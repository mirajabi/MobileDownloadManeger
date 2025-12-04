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
    val integrity: IntegrityConfig = IntegrityConfig(),
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
    val minFreeSpaceBytes: Long = 10 * 1024 * 1024L,
    val preferExternalPublic: Boolean = false
)

data class InstallerConfig(
    val promptOnCompletion: Boolean = false,
    val autoDetectMimeType: Boolean = true,
    val fallbackMimeType: String = "application/vnd.android.package-archive"
)

/**
 * 9. Integrity validation configuration for downloaded files.
 * All validations are optional and can be enabled/disabled independently.
 */
data class IntegrityConfig(
    /**
     * If true, verifies that downloaded file size matches Content-Length header.
     * Recommended: true (prevents incomplete downloads)
     */
    val verifyFileSize: Boolean = true,
    
    /**
     * If true, verifies file checksum/hash if provided in DownloadRequest.
     * Recommended: true for APK files (ensures file integrity)
     */
    val verifyChecksum: Boolean = true,
    
    /**
     * If true, validates APK file structure (magic number, ZIP format).
     * Only applies to .apk/.apks files.
     * Recommended: true for APK downloads (catches corrupted files early)
     */
    val verifyApkStructure: Boolean = true,
    
    /**
     * If true, validates Content-Type header matches expected MIME type.
     * 
     * **Why check Content-Type?**
     * - Prevents downloading wrong file types (e.g., HTML error page instead of APK)
     * - Security: Detects if server returns unexpected content
     * - Early detection of server errors or redirects
     * 
     * **Why default is false?**
     * - Many servers/CDNs don't send correct Content-Type headers
     * - Some proxies modify Content-Type headers
     * - May cause false positives (valid file with wrong header)
     * - APK structure validation is more reliable for detecting actual file type
     * 
     * **When to enable:**
     * - You control the server and know it sends correct Content-Type
     * - High-security environments where any mismatch is suspicious
     * - You want early detection of server-side errors
     * 
     * Recommended: false (APK structure validation is more reliable)
     */
    val verifyContentType: Boolean = false,
    
    /**
     * If true, attempts to verify APK signature (requires PackageManager).
     * This is expensive and may fail for unsigned APKs.
     * Recommended: false (optional, only if signature verification is critical)
     */
    val verifyApkSignature: Boolean = false
)

/**
 * Checksum algorithm types for file integrity verification.
 */
enum class ChecksumAlgorithm {
    /**
     * MD5 - Fast but less secure (128-bit).
     * Not recommended for security-critical files.
     */
    MD5,
    
    /**
     * SHA-256 - Recommended default (256-bit).
     * Good balance of security and performance.
     */
    SHA256,
    
    /**
     * SHA-512 - Most secure but slower (512-bit).
     * Use for large files or high-security requirements.
     */
    SHA512
}

/**
 * 8. Request model describing what and where to download.
 */
data class DownloadRequest(
    val url: String,
    val fileName: String,
    val destination: DownloadDestination = DownloadDestination.Auto,
    val id: String = UUID.randomUUID().toString(),
    val headers: Map<String, String> = emptyMap(),
    
    /**
     * Expected checksum/hash of the file for integrity verification.
     * If provided and IntegrityConfig.verifyChecksum is true, the downloaded file
     * will be validated against this checksum.
     * Format: hex string (e.g., "a1b2c3d4e5f6..." for SHA256)
     */
    val expectedChecksum: String? = null,
    
    /**
     * Algorithm used to calculate the expectedChecksum.
     * Default: SHA256 (recommended)
     */
    val checksumAlgorithm: ChecksumAlgorithm = ChecksumAlgorithm.SHA256
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

