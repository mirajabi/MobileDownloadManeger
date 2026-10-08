package com.miaadrajabi.fetch

import android.content.Context
import com.miaadrajabi.downloader.ChecksumAlgorithm
import com.miaadrajabi.downloader.DownloadDestination
import com.miaadrajabi.downloader.DownloadForegroundService
import com.miaadrajabi.downloader.DownloadManagerBuilder
import com.miaadrajabi.downloader.DownloadRequest
import com.miaadrajabi.downloader.MobileDownloadManager
import com.miaadrajabi.downloader.ScheduleTime
import com.miaadrajabi.downloader.StorageResolver
import com.miaadrajabi.downloader.StorageResolutionException
import java.util.UUID

/**
 * Turns the screen's choices into library calls.
 * The download module itself is not modified. Fetch only uses the public API.
 */
object DownloadDesk {

    fun apply(context: Context, settings: EngineSettings) {
        // Custom notification icon, read before the service promotes itself.
        DownloadForegroundService.setNotificationIcon(settings.notificationIcon())
        // Chunks, retry, notification, storage, installer, and integrity.
        DownloadForegroundService.configureService(context) {
            fill(settings)
            schedulerUseAlarmManager(settings.useAlarm)
        }
    }

    fun enqueue(context: Context, settings: EngineSettings, request: DownloadRequest) {
        apply(context, settings)
        DownloadForegroundService.enqueueDownload(context, request)
    }

    fun schedule(
        context: Context,
        settings: EngineSettings,
        request: DownloadRequest,
        whenToRun: ScheduleTime
    ) {
        // Exact clock. AlarmManager or a one-time WorkManager job, from the saved flag.
        apply(context, settings.copy(useAlarm = settings.useAlarm))
        DownloadForegroundService.scheduleDownload(context, request, whenToRun)
    }

    fun repeat(context: Context, settings: EngineSettings, request: DownloadRequest, minutes: Long) {
        // scheduleDownload always takes a clock time, so a repeat uses schedule(request, null)
        // after periodicSchedule. That is the WorkManager path. AlarmManager does not repeat here.
        val manager = MobileDownloadManager.create(context) {
            fill(settings)
            periodicSchedule(minutes)
            schedulerUseAlarmManager(false)
        }
        manager.schedule(request, null)
        apply(context, settings)
    }

    fun previewPath(context: Context, settings: EngineSettings, fileName: String): String {
        val config = MobileDownloadManager.builder(context).apply { fill(settings) }.buildConfig()
        val request = DownloadRequest(
            url = "https://downloads.example.com/preview.bin",
            fileName = fileName.ifBlank { "download.bin" },
            destination = DownloadDestination.Auto
        )
        return try {
            val resolution = StorageResolver(context, config.storage).resolve(request, true)
            resolution.file.absolutePath
        } catch (error: StorageResolutionException) {
            error.message ?: "No writable folder yet"
        }
    }

    fun startPlain(context: Context, links: List<String>) {
        if (links.isEmpty()) return
        val settings = EngineSettings.load(context)
        val names = LinkParser.uniqueNames(links, "")
        val store = (context.applicationContext as FetchApp).store
        for (index in links.indices) {
            val request = newRequest(
                url = links[index],
                fileName = names[index],
                headers = emptyMap(),
                checksum = null,
                algorithm = ChecksumAlgorithm.SHA256,
                rangeStart = null,
                rangeEndInclusive = null
            )
            store.insert(
                Transfer(
                    id = request.id,
                    url = request.url,
                    fileName = request.fileName,
                    status = TransferStore.STATUS_QUEUED,
                    message = "Waiting to start"
                )
            )
            enqueue(context, settings, request)
        }
    }

    fun newRequest(
        url: String,
        fileName: String,
        headers: Map<String, String>,
        checksum: String?,
        algorithm: ChecksumAlgorithm,
        rangeStart: Long?,
        rangeEndInclusive: Long?
    ): DownloadRequest {
        return DownloadRequest(
            url = url,
            fileName = fileName,
            destination = DownloadDestination.Auto,
            id = UUID.randomUUID().toString(),
            headers = headers,
            expectedChecksum = checksum,
            checksumAlgorithm = algorithm,
            rangeStart = rangeStart,
            rangeEndInclusive = rangeEndInclusive
        )
    }

    private fun DownloadManagerBuilder.fill(settings: EngineSettings) {
        chunkCount(settings.chunkCount)
        chunkParallel(settings.parallel)
        chunkMinSize(settings.minChunkKb * 1024L)
        retryPolicy(
            maxAttempts = settings.retryAttempts,
            initialDelayMillis = settings.retryDelaySeconds * 1000L,
            backoffMultiplier = settings.backoffTenths / 10f
        )
        enforceForeground(true)
        notificationChannel(
            id = "fetch_downloads",
            name = settings.channelName.ifBlank { "Fetch" },
            description = "Progress for files Fetch is downloading"
        )
        notificationIcon(settings.notificationIcon())
        notificationShowProgress(settings.showProgress)
        notificationPersistent(settings.persistent)
        storageDestinations(settings.destinations())
        storageOverwrite(settings.overwrite)
        storageValidateFreeSpace(settings.checkSpace, settings.minFreeMb * 1024L * 1024L)
        storageUsePublicDownloads(settings.publicDownloads && settings.destination == EngineSettings.DEST_AUTO)
        storagePublicDownloadsFolder(settings.publicFolder)
        installerPromptOnCompletion(settings.promptInstaller)
        integrityValidation(
            verifyFileSize = settings.verifySize,
            verifyChecksum = settings.verifyChecksum,
            verifyApkStructure = settings.verifyApk,
            verifyContentType = settings.verifyType,
            verifyApkSignature = settings.verifySignature
        )
    }
}
