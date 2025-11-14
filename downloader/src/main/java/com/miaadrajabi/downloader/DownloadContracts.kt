package com.miaadrajabi.downloader

import java.util.UUID

/**
 * 1. Aggregates every configurable aspect of the download manager.
 */
data class DownloadConfig(
    val chunking: ChunkingConfig = ChunkingConfig(),
    val retryPolicy: RetryPolicy = RetryPolicy(),
    val enforceForegroundService: Boolean = true,
    val notification: NotificationConfig = NotificationConfig(),
    val scheduler: SchedulerConfig = SchedulerConfig(),
    val storage: StorageConfig = StorageConfig(),
    val installer: InstallerConfig = InstallerConfig(),
    val listeners: List<DownloadListener> = emptyList()
)

/**
 * 2. Controls how files split across parallel requests.
 */
data class ChunkingConfig(
    val chunkCount: Int = 3,
    val minChunkSizeBytes: Long = 512 * 1024L,
    val preferParallel: Boolean = true
)

/**
 * 3. Retry policy definition for failed download attempts.
 */
data class RetryPolicy(
    val maxAttempts: Int = 3,
    val initialDelayMillis: Long = 2_000L,
    val backoffMultiplier: Float = 2f
)

/**
 * 4. Notification channel and foreground UI preferences.
 */
data class NotificationConfig(
    val channelId: String = "mobile_downloader",
    val channelName: String = "Mobile Downloader",
    val channelDescription: String = "Background downloads",
    val showProgressPercentage: Boolean = true,
    val persistent: Boolean = true,
    val smallIconRes: Int? = null
)

/**
 * 5. Scheduler knobs for periodic or exact triggers.
 */
data class SchedulerConfig(
    val periodicIntervalMinutes: Long? = null,
    val exactStartTime: ScheduleTime? = null,
    val allowWhileIdle: Boolean = true,
    val useAlarmManager: Boolean = false
)

/**
 * 6. Represents an exact hour/minute trigger.
 */
data class ScheduleTime(
    val hour: Int,
    val minute: Int,
    val weekday: Weekday? = null,
    val year: Int? = null,
    val month: Int? = null,
    val dayOfMonth: Int? = null
)

/**
 * 7. Enumerates days of week for exact schedules.
 */
enum class Weekday(val calendarValue: Int) {
    SUNDAY(java.util.Calendar.SUNDAY),
    MONDAY(java.util.Calendar.MONDAY),
    TUESDAY(java.util.Calendar.TUESDAY),
    WEDNESDAY(java.util.Calendar.WEDNESDAY),
    THURSDAY(java.util.Calendar.THURSDAY),
    FRIDAY(java.util.Calendar.FRIDAY),
    SATURDAY(java.util.Calendar.SATURDAY)
}

/**
 * 7. Storage directives and housekeeping rules.
 */
data class StorageConfig(
    val downloadDirs: List<DownloadDestination> = listOf(DownloadDestination.Auto),
    val overwriteExisting: Boolean = true,
    val validateFreeSpace: Boolean = true,
    val minFreeSpaceBytes: Long = 10 * 1024 * 1024L
)

data class InstallerConfig(
    val promptOnCompletion: Boolean = false,
    val autoDetectMimeType: Boolean = true,
    val fallbackMimeType: String = "application/vnd.android.package-archive"
)

/**
 * 8. Request model describing what and where to download.
 */
data class DownloadRequest(
    val url: String,
    val fileName: String,
    val destination: DownloadDestination = DownloadDestination.Auto,
    val id: String = UUID.randomUUID().toString(),
    val headers: Map<String, String> = emptyMap()
)

/**
 * 9. Handle returned to callers to track enqueued downloads.
 */
data class DownloadHandle(
    val id: String,
    val source: String
)

/**
 * 10. Result of preparing storage before a download starts.
 */
data class StorageResolution(
    val directory: java.io.File,
    val file: java.io.File,
    val overwroteExisting: Boolean
)

/**
 * 10. Progress payload delivered to observers.
 */
data class DownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long?,
    val chunkIndex: Int? = null,
    val bytesPerSecond: Long? = null,
    val remainingBytes: Long? = null,
    val percent: Int? = null
)

/**
 * 11. Normalized download lifecycle states.
 */
sealed class DownloadStatus {
    object Queued : DownloadStatus()
    data class Running(val progress: DownloadProgress) : DownloadStatus()
    data class Completed(val filePath: String) : DownloadStatus()
    data class Failed(val error: Throwable?) : DownloadStatus()
    object Cancelled : DownloadStatus()
}

/**
 * 12. Destination abstraction that controls where files land.
 */
sealed class DownloadDestination {
    /**
     * 13. Relies on the library default directories.
     */
    object Auto : DownloadDestination()

    /**
     * 14. Points to a user-provided absolute path.
     */
    data class Custom(val absolutePath: String) : DownloadDestination()

    /**
     * 15. Targets SAF or MediaStore friendly relative paths.
     */
    data class Scoped(val relativePath: String) : DownloadDestination()
}

