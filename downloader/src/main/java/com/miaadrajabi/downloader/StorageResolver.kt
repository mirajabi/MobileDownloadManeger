package com.miaadrajabi.downloader

import android.content.Context
import android.os.Environment
import android.os.StatFs
import java.io.File

/**
 * 1. Resolves destination directories and files based on StorageConfig rules.
 */
class StorageResolver(
    context: Context,
    private val storageConfig: StorageConfig
) {

    private val appContext = context.applicationContext ?: context
    private val defaultLocations: List<File> = listOfNotNull(
        appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
        appContext.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
        File(appContext.filesDir, "downloads")
    )

    /**
     * 2. Computes the final file that should receive the download payload.
     * 3. When dryRun is true, existing files are not deleted but validation still happens.
     */
    fun resolve(request: DownloadRequest, dryRun: Boolean = false): StorageResolution {
        val candidateDirs = toCandidateDirectories(storageConfig.downloadDirs).ifEmpty { defaultLocations }
        val writableDir = candidateDirs.firstOrNull { ensureDirectory(it) }
            ?: throw StorageResolutionException("No writable directory found for ${request.fileName}")

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

    private fun ensureDirectory(directory: File): Boolean {
        if (directory.exists()) {
            return directory.isDirectory && directory.canWrite()
        }
        return directory.mkdirs()
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

