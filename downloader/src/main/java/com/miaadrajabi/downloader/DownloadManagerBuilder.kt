package com.miaadrajabi.downloader

import android.content.Context
import kotlin.math.max

/**
 * 1. Fluent builder that helps compose complex DownloadConfig instances.
 */
class DownloadManagerBuilder internal constructor(
    private val context: Context
) {

    private var chunking: ChunkingConfig = ChunkingConfig()
    private var retryPolicy: RetryPolicy = RetryPolicy()
    private var enforceForegroundService: Boolean = true
    private var notification: NotificationConfig = NotificationConfig()
    private var scheduler: SchedulerConfig = SchedulerConfig()
    private var storage: StorageConfig = StorageConfig()
    private var installer: InstallerConfig = InstallerConfig()
    private var integrity: IntegrityConfig = IntegrityConfig()
    private val listeners = mutableListOf<DownloadListener>()

    /**
     * 2. Sets the number of parallel chunks.
     */
    fun chunkCount(count: Int) = apply {
        chunking = chunking.copy(chunkCount = max(1, count))
    }

    /**
     * 3. Enables or disables parallel chunk execution.
     */
    fun chunkParallel(enable: Boolean) = apply {
        chunking = chunking.copy(preferParallel = enable)
    }

    /**
     * 4. Controls the minimum size for generated chunks.
     */
    fun chunkMinSize(bytes: Long) = apply {
        chunking = chunking.copy(minChunkSizeBytes = max(64 * 1024L, bytes))
    }

    /**
     * 5. Adjusts retry policy parameters.
     */
    fun retryPolicy(
        maxAttempts: Int? = null,
        initialDelayMillis: Long? = null,
        backoffMultiplier: Float? = null
    ) = apply {
        retryPolicy = retryPolicy.copy(
            maxAttempts = maxAttempts ?: retryPolicy.maxAttempts,
            initialDelayMillis = initialDelayMillis ?: retryPolicy.initialDelayMillis,
            backoffMultiplier = backoffMultiplier ?: retryPolicy.backoffMultiplier
        )
    }

    /**
     * 6. Declares whether a foreground service is mandatory.
     */
    fun enforceForeground(required: Boolean) = apply {
        enforceForegroundService = required
    }

    /**
     * 7. Configures the notification channel properties.
     */
    fun notificationChannel(id: String, name: String, description: String = notification.channelDescription) = apply {
        notification = notification.copy(
            channelId = id,
            channelName = name,
            channelDescription = description
        )
    }

    /**
     * 8. Sets the small icon resource for notifications.
     */
    fun notificationIcon(iconRes: Int?) = apply {
        notification = notification.copy(smallIconRes = iconRes)
    }

    /**
     * 9. Toggles whether progress percentage is shown.
     */
    fun notificationShowProgress(show: Boolean) = apply {
        notification = notification.copy(showProgressPercentage = show)
    }

    /**
     * 10. Controls whether the notification is ongoing/persistent.
     */
    fun notificationPersistent(persistent: Boolean) = apply {
        notification = notification.copy(persistent = persistent)
    }

    /**
     * 11. Configures periodic scheduling via WorkManager.
     */
    fun periodicSchedule(intervalMinutes: Long) = apply {
        scheduler = scheduler.copy(
            periodicIntervalMinutes = intervalMinutes,
            exactStartTime = null
        )
    }

    /**
     * 12. Configures an exact alarm-style trigger.
     */
    fun exactSchedule(
        hour: Int,
        minute: Int,
        weekday: Weekday? = null,
        allowWhileIdle: Boolean = scheduler.allowWhileIdle
    ) = apply {
        scheduler = scheduler.copy(
            exactStartTime = ScheduleTime(hour = hour, minute = minute, weekday = weekday),
            periodicIntervalMinutes = null,
            allowWhileIdle = allowWhileIdle
        )
    }

    /**
     * 13. Configures a one-off exact date (year/month/day) trigger.
     */
    fun exactScheduleDate(
        year: Int,
        month: Int,
        dayOfMonth: Int,
        hour: Int,
        minute: Int,
        allowWhileIdle: Boolean = scheduler.allowWhileIdle
    ) = apply {
        scheduler = scheduler.copy(
            exactStartTime = ScheduleTime(
                hour = hour,
                minute = minute,
                year = year,
                month = month,
                dayOfMonth = dayOfMonth
            ),
            periodicIntervalMinutes = null,
            allowWhileIdle = allowWhileIdle
        )
    }

    /**
     * 14. Switches between AlarmManager and WorkManager scheduling.
     */
    fun schedulerUseAlarmManager(useAlarmManager: Boolean) = apply {
        scheduler = scheduler.copy(useAlarmManager = useAlarmManager)
    }

    /**
     * 14. Overrides default storage destinations.
     */
    fun storageDestinations(destinations: List<DownloadDestination>) = apply {
        if (destinations.isNotEmpty()) {
            storage = storage.copy(downloadDirs = destinations)
        }
    }

    /**
     * 15. Controls overwrite behavior for existing files.
     */
    fun storageOverwrite(overwrite: Boolean) = apply {
        storage = storage.copy(overwriteExisting = overwrite)
    }

    /**
     * 16. Enables or disables free-space validation.
     */
    fun storageValidateFreeSpace(validate: Boolean, minBytes: Long? = null) = apply {
        storage = storage.copy(
            validateFreeSpace = validate,
            minFreeSpaceBytes = minBytes ?: storage.minFreeSpaceBytes
        )
    }

    fun storageUsePublicDownloads(enable: Boolean = true) = apply {
        storage = storage.copy(preferExternalPublic = enable)
    }

    fun installerPromptOnCompletion(
        enabled: Boolean = true,
        fallbackMimeType: String = installer.fallbackMimeType
    ) = apply {
        installer = installer.copy(
            promptOnCompletion = enabled,
            fallbackMimeType = fallbackMimeType
        )
    }

    /**
     * 17. Configures file integrity validation.
     * Recommended: Enable file size and checksum validation for APK downloads.
     * 
     * @param verifyFileSize If true, verifies downloaded file size matches Content-Length (recommended: true)
     * @param verifyChecksum If true, verifies file checksum if provided in DownloadRequest (recommended: true for APKs)
     * @param verifyApkStructure If true, validates APK structure for .apk/.apks files (recommended: true)
     * @param verifyContentType If true, validates Content-Type header (recommended: false)
     * @param verifyApkSignature If true, verifies APK signature (expensive, recommended: false)
     */
    fun integrityValidation(
        verifyFileSize: Boolean? = null,
        verifyChecksum: Boolean? = null,
        verifyApkStructure: Boolean? = null,
        verifyContentType: Boolean? = null,
        verifyApkSignature: Boolean? = null
    ) = apply {
        integrity = integrity.copy(
            verifyFileSize = verifyFileSize ?: integrity.verifyFileSize,
            verifyChecksum = verifyChecksum ?: integrity.verifyChecksum,
            verifyApkStructure = verifyApkStructure ?: integrity.verifyApkStructure,
            verifyContentType = verifyContentType ?: integrity.verifyContentType,
            verifyApkSignature = verifyApkSignature ?: integrity.verifyApkSignature
        )
    }

    /**
     * 18. Enables recommended integrity validation for APK downloads.
     * This enables: file size, checksum, and APK structure validation.
     */
    fun integrityValidationForApk() = apply {
        integrity = IntegrityConfig(
            verifyFileSize = true,
            verifyChecksum = true,
            verifyApkStructure = true,
            verifyContentType = false,
            verifyApkSignature = false
        )
    }

    /**
     * 19. Adds a listener that receives download lifecycle callbacks.
     */
    fun addListener(listener: DownloadListener) = apply {
        listeners += listener
    }

    /**
     * 20. Clears all listeners accumulated so far.
     */
    fun clearListeners() = apply {
        listeners.clear()
    }

    /**
     * 21. Builds the immutable config representation.
     */
    fun buildConfig(): DownloadConfig = DownloadConfig(
        chunking = chunking,
        retryPolicy = retryPolicy,
        enforceForegroundService = enforceForegroundService,
        notification = notification,
        scheduler = scheduler,
        storage = storage,
        installer = installer,
        integrity = integrity,
        listeners = listeners.toList()
    )

    /**
     * 20. Produces a MobileDownloadManager with the assembled config.
     */
    fun build(): MobileDownloadManager = MobileDownloadManager.create(context, buildConfig())
}

