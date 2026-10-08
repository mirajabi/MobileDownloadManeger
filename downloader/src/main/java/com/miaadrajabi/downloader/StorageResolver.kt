package com.miaadrajabi.downloader

import android.content.Context
import android.os.StatFs
import java.io.File
import android.os.Environment

/**
 * 1. Resolves destination directories and files based on StorageConfig rules.
 */
class StorageResolver(
    context: Context,
    private val storageConfig: StorageConfig
) {

    private val appContext = context.applicationContext ?: context
    private val defaultLocations: List<File>
    private val publicDownloadDir: File? = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

    init {
        val internalDefaults = listOfNotNull(
            appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            appContext.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            File(appContext.filesDir, "downloads")
        )
        val publicRoot = if (storageConfig.preferExternalPublic && publicDownloadDir != null) {
            childOf(publicDownloadDir, storageConfig.publicFolder)
        } else {
            null
        }
        defaultLocations = if (publicRoot != null) {
            listOf(publicRoot) + internalDefaults
        } else {
            internalDefaults
        }
    }

    /**
     * 2. Computes the final file that should receive the download payload.
     * 3. When dryRun is true, existing files are not deleted but validation still happens.
     */
    fun resolve(request: DownloadRequest, dryRun: Boolean = false): StorageResolution {
        val candidateDirs = toCandidateDirectories(storageConfig.downloadDirs).ifEmpty { defaultLocations }
        val writableDir = candidateDirs.firstOrNull { ensureDirectory(it) }
            ?: throw StorageResolutionException(
                "No writable directory found for ${request.fileName}. Tried: " +
                    candidateDirs.joinToString { it.absolutePath }
            )

        val targetFile = File(writableDir, request.fileName)

        val overwrote = handleExistingFile(targetFile, dryRun)

        if (storageConfig.validateFreeSpace) {
            validateFreeSpace(writableDir)
        }

        if (!dryRun && !targetFile.exists()) {
            targetFile.parentFile?.let { ensureDirectory(it) }
            if (!targetFile.createNewFile()) {
                throw StorageResolutionException("Unable to create target file: ${targetFile.absolutePath}")
            }
        }

        return StorageResolution(
            directory = writableDir,
            file = targetFile,
            overwroteExisting = overwrote
        )
    }

    private fun toCandidateDirectories(destinations: List<DownloadDestination>): List<File> {
        if (destinations.isEmpty()) return defaultLocations
        return destinations.flatMap { destination ->
            when (destination) {
                is DownloadDestination.Custom -> listOf(File(destination.absolutePath))
                is DownloadDestination.Scoped -> {
                    val base = appContext.getExternalFilesDir(null) ?: appContext.filesDir
                    listOf(File(base, destination.relativePath))
                }
                DownloadDestination.Auto -> defaultLocations
            }
        }
    }

    private fun childOf(root: File, relative: String): File {
        val cleaned = relative.trim().replace('\\', '/').trim('/')
        if (cleaned.isEmpty()) return root
        val safe = StringBuilder()
        val parts = cleaned.split('/')
        for (part in parts) {
            if (part.isEmpty() || part == "." || part == "..") continue
            val piece = part.replace(Regex("[^A-Za-z0-9._ -]"), "_").trim('_', ' ', '.')
            if (piece.isEmpty()) continue
            if (safe.isNotEmpty()) safe.append('/')
            safe.append(piece)
        }
        if (safe.isEmpty()) return root
        return File(root, safe.toString())
    }

    private fun ensureDirectory(directory: File): Boolean {
        if (!directory.exists() && !directory.mkdirs()) {
            return false
        }
        if (!directory.isDirectory) return false
        // canWrite() is false on app-specific external storage even when the app can create files.
        if (directory.canWrite()) return true
        return probeWritable(directory)
    }

    private fun probeWritable(directory: File): Boolean {
        val probe = File(directory, ".mdm-write-probe")
        return try {
            if (!probe.exists() && !probe.createNewFile()) {
                false
            } else {
                probe.delete()
                true
            }
        } catch (error: Exception) {
            false
        }
    }

    private fun handleExistingFile(target: File, dryRun: Boolean): Boolean {
        if (!target.exists()) return false
        if (!storageConfig.overwriteExisting) {
            throw StorageResolutionException("File already exists and overwrite is disabled: ${target.absolutePath}")
        }
        if (dryRun) {
            return false
        }
        if (!target.delete()) {
            throw StorageResolutionException("Unable to delete existing file: ${target.absolutePath}")
        }
        return true
    }

    private fun validateFreeSpace(directory: File) {
        val statFs = StatFs(directory.absolutePath)
        val availableBytes = statFs.availableBytes
        if (availableBytes < storageConfig.minFreeSpaceBytes) {
            val minMb = storageConfig.minFreeSpaceBytes / (1024 * 1024)
            throw StorageResolutionException(
                "Insufficient free space. Requires at least ${minMb} MB but found ${(availableBytes / (1024 * 1024))} MB."
            )
        }
    }
}

/**
 * 4. Dedicated exception for storage-specific failures.
 */
class StorageResolutionException(message: String) : IllegalStateException(message)

