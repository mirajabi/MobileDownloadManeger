package com.miaadrajabi.mobiledownloadmaneger

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.miaadrajabi.downloader.ChunkingConfig
import com.miaadrajabi.downloader.DownloadConfig
import com.miaadrajabi.downloader.DownloadDestination
import com.miaadrajabi.downloader.DownloadForegroundService
import com.miaadrajabi.downloader.DownloadHandle
import com.miaadrajabi.downloader.DownloadListener
import com.miaadrajabi.downloader.DownloadProgress
import com.miaadrajabi.downloader.DownloadRequest
import com.miaadrajabi.downloader.InstallerConfig
import com.miaadrajabi.downloader.NotificationConfig
import com.miaadrajabi.downloader.RetryPolicy
import com.miaadrajabi.downloader.ScheduleTime
import com.miaadrajabi.downloader.SchedulerConfig
import com.miaadrajabi.downloader.StorageConfig
import com.miaadrajabi.downloader.StorageResolver
import com.miaadrajabi.downloader.StorageResolutionException
import com.miaadrajabi.downloader.Weekday
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * 1. Simple activity used for quick smoke tests of the downloader module.
 * 2. Demonstrates how to build a MobileDownloadManager with the fluent DSL.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var summaryView: TextView
    private lateinit var instructionsView: TextView
    private lateinit var statusView: TextView
    private lateinit var scheduleStatusView: TextView
    private lateinit var pauseButton: Button
    private lateinit var resumeButton: Button
    private var currentHandleId: String? = null
    private var lastUiBytes: Long = 0L
    private var lastUiTimestamp: Long = 0L
    private var pendingPermissionAction: (() -> Unit)? = null
    private val loggingListener = object : DownloadListener {
        override fun onQueued(handle: DownloadHandle) {
            Log.d(TAG, "Listener queued ${handle.id}")
            currentHandleId = handle.id
            updateStatus("Queued: ${handle.id}")
            setPauseResumeState(canPause = true, canResume = false)
        }

        override fun onStarted(handle: DownloadHandle) {
            Log.d(TAG, "Listener started ${handle.id}")
            updateStatus("Started: ${handle.id}")
        }

        override fun onProgress(handle: DownloadHandle, progress: DownloadProgress) {
            val now = System.currentTimeMillis()
            val deltaBytes = progress.bytesDownloaded - lastUiBytes
            val deltaTime = now - lastUiTimestamp
            if (deltaBytes < MIN_UI_BYTES_STEP && deltaTime < MIN_UI_INTERVAL_MS) return
            lastUiBytes = progress.bytesDownloaded
            lastUiTimestamp = now

            val total = progress.totalBytes?.let { "/${it.toHumanReadable()}" } ?: ""
            val speed = progress.bytesPerSecond?.let { " • ${it.toHumanReadable()}/s" } ?: ""
            updateStatus("Downloading: ${progress.bytesDownloaded.toHumanReadable()}$total$speed")
        }

        override fun onPaused(handle: DownloadHandle) {
            Log.d(TAG, "Listener paused ${handle.id}")
            updateStatus("Paused: ${handle.id}")
            setPauseResumeState(canPause = false, canResume = true)
        }

        override fun onResumed(handle: DownloadHandle) {
            Log.d(TAG, "Listener resumed ${handle.id}")
            updateStatus("Resuming: ${handle.id}")
            setPauseResumeState(canPause = true, canResume = false)
        }

        override fun onCompleted(handle: DownloadHandle) {
            Log.d(TAG, "Listener completed ${handle.id}")
            updateStatus("Completed: ${handle.id}")
            currentHandleId = null
            setPauseResumeState(canPause = false, canResume = false)
        }

        override fun onFailed(handle: DownloadHandle, error: Throwable?) {
            Log.w(TAG, "Listener failed ${handle.id}", error)
            updateStatus("Failed: ${error?.message ?: "unknown error"}")
            currentHandleId = null
            setPauseResumeState(canPause = false, canResume = false)
        }
    }

    private fun extractBaseName(url: String): String {
        val cleanUrl = url.substringBefore('?')
        val raw = cleanUrl.substringAfterLast('/')
        return if (raw.isNotBlank()) raw else "download.bin"
    }

    private fun formattedTimestamp(): String {
        val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        return formatter.format(Date())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Configure the download service before using it
        configureDownloadService()

        DownloadForegroundService.setNotificationIcon(R.mipmap.ic_launcher)

        summaryView = findViewById(R.id.tvBuilderSummary)
        instructionsView = findViewById(R.id.tvInstructions)
        statusView = findViewById(R.id.tvDownloadStatus)
        scheduleStatusView = findViewById(R.id.tvScheduleStatus)
        val startButton: Button = findViewById(R.id.btnStartDownload)
        val scheduleButton: Button = findViewById(R.id.btnScheduleDownload)
        val exactButton: Button = findViewById(R.id.btnScheduleExact)
        pauseButton = findViewById(R.id.btnPauseDownload)
        resumeButton = findViewById(R.id.btnResumeDownload)

        val previewConfig = buildPreviewConfig()
        summaryView.text = buildSummary(previewConfig)
        instructionsView.text = buildStoragePreview(previewConfig)
        statusView.text = "Idle — tap the button to start a download."
        scheduleStatusView.text = getString(R.string.sample_schedule_hint)
        setPauseResumeState(canPause = false, canResume = false)
        DownloadForegroundService.registerListener(loggingListener)

        startButton.setOnClickListener {
            ensureStoragePermission {
                val request = createSampleRequest()
                currentHandleId = request.id
                DownloadForegroundService.enqueueDownload(this, request)
                updateStatus("Queued: ${request.id}")
                setPauseResumeState(canPause = true, canResume = false)
            }
        }
        pauseButton.setOnClickListener {
            val handleId = currentHandleId
            if (handleId != null) {
                DownloadForegroundService.pauseDownload(this, handleId)
                updateStatus("Paused: $handleId")
                setPauseResumeState(canPause = false, canResume = true)
            } else {
                updateStatus("No active download to pause.")
            }
        }

        resumeButton.setOnClickListener {
            val handleId = currentHandleId
            if (handleId != null) {
                ensureStoragePermission {
                    DownloadForegroundService.resumeDownload(this, handleId)
                    updateStatus("Resuming: $handleId")
                    setPauseResumeState(canPause = true, canResume = false)
                }
            } else {
                updateStatus("No paused download to resume.")
            }
        }

        scheduleButton.setOnClickListener {
            ensureStoragePermission {
                val scheduleTime = ScheduleTime(hour = 0, minute = 30, weekday = Weekday.TUESDAY)
                val scheduledRequest = createScheduledRequest("weekday")
                DownloadForegroundService.scheduleDownload(this, scheduledRequest, scheduleTime)
                scheduleStatusView.text = "Scheduled ${scheduledRequest.fileName} for ${describeSchedule(scheduleTime)}"
            }
        }

        exactButton.setOnClickListener {
            ensureStoragePermission {
                val scheduleTime = createExactScheduleTime()
                val scheduledRequest = createScheduledRequest("exact")
                DownloadForegroundService.scheduleDownload(this, scheduledRequest, scheduleTime)
                scheduleStatusView.text = "Scheduled ${scheduledRequest.fileName} for ${describeSchedule(scheduleTime)}"
            }
        }
    }

    override fun onDestroy() {
        DownloadForegroundService.unregisterListener(loggingListener)
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_STORAGE_PERMISSIONS) {
            val granted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (granted) {
                pendingPermissionAction?.invoke()
            } else {
                updateStatus("Storage permission is required to write into Downloads folder.")
            }
            pendingPermissionAction = null
        }
    }

    /**
     * 3. Formats the active configuration so testers can quickly inspect knobs.
     */
    private fun buildSummary(config: DownloadConfig): String = buildString {
        val destinationNames = config.storage.downloadDirs
            .map { it::class.simpleName ?: "Auto" }
            .joinToString()
        appendLine("Chunk count: ${config.chunking.chunkCount}")
        appendLine("Chunk min size: ${config.chunking.minChunkSizeBytes / 1024} KB")
        appendLine("Parallel enabled: ${config.chunking.preferParallel}")
        appendLine("Retry attempts: ${config.retryPolicy.maxAttempts}")
        appendLine("Retry delay: ${config.retryPolicy.initialDelayMillis} ms")
        appendLine("Retry backoff: ${config.retryPolicy.backoffMultiplier}")
        appendLine("Foreground required: ${config.enforceForegroundService}")
        appendLine("Notification channel: ${config.notification.channelName}")
        appendLine("Scheduler periodic: ${config.scheduler.periodicIntervalMinutes ?: "-"} min")
        appendLine("Exact start: ${config.scheduler.exactStartTime ?: "-"}")
        appendLine("Destinations: $destinationNames")
        appendLine("Overwrite existing: ${config.storage.overwriteExisting}")
        appendLine("Validate free space: ${config.storage.validateFreeSpace}")
        appendLine("Listeners attached: ${config.listeners.size}")
    }

    /**
     * 4. Runs the storage resolver in dry-run mode for a sample request and prints the outcome.
     */
    private fun buildStoragePreview(config: DownloadConfig): String {
        val resolver = StorageResolver(applicationContext, config.storage)
        val request = createSampleRequest()

        return try {
            val resolution = resolver.resolve(request, dryRun = true)
            """
                Next storage target:
                • Directory: ${resolution.directory.absolutePath}
                • File: ${resolution.file.absolutePath}
                • Would overwrite existing: ${resolution.overwroteExisting}
            """.trimIndent()
        } catch (error: StorageResolutionException) {
            "Storage resolver error: ${error.message}"
        }
    }

    private fun createSampleRequest(): DownloadRequest = buildDownloadRequest("TMS-SADAD")

    private fun createScheduledRequest(prefix: String = "TMS-SADAD-SCH"): DownloadRequest =
        buildDownloadRequest(prefix)

    private fun buildDownloadRequest(prefix: String): DownloadRequest {
        val baseName = extractBaseName(SAMPLE_URL)
        val stamp = formattedTimestamp()
        val fileName = "${prefix}_${stamp}_${baseName}"
        return DownloadRequest(
            id = UUID.randomUUID().toString(),
            url = SAMPLE_URL,
            fileName = fileName,
            destination = DownloadDestination.Auto
        )
    }

    private fun createExactScheduleTime(): ScheduleTime {
        val calendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return ScheduleTime(
            hour = calendar.get(Calendar.HOUR_OF_DAY),
            minute = calendar.get(Calendar.MINUTE),
            year = calendar.get(Calendar.YEAR),
            month = calendar.get(Calendar.MONTH) + 1,
            dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    private fun updateStatus(message: String) {
        runOnUiThread {
            statusView.text = message
        }
    }

    private fun setPauseResumeState(canPause: Boolean, canResume: Boolean) {
        runOnUiThread {
            pauseButton.isEnabled = canPause
            resumeButton.isEnabled = canResume
        }
    }

    /**
     * 5. Produces a friendly string for the configured weekly schedule.
     */
    private fun describeSchedule(scheduleTime: ScheduleTime): String {
        val hour = scheduleTime.hour.toString().padStart(2, '0')
        val minute = scheduleTime.minute.toString().padStart(2, '0')
        val datePart = if (
            scheduleTime.year != null &&
            scheduleTime.month != null &&
            scheduleTime.dayOfMonth != null
        ) {
            val month = scheduleTime.month.toString().padStart(2, '0')
            val day = scheduleTime.dayOfMonth.toString().padStart(2, '0')
            "${scheduleTime.year}-$month-$day"
        } else {
            scheduleTime.weekday?.name?.let { prettifyWeekday(it) } ?: "Daily"
        }
        return "$datePart at $hour:$minute"
    }

    private fun prettifyWeekday(raw: String): String {
        if (raw.isEmpty()) return raw
        val lower = raw.toLowerCase(Locale.US)
        return lower.substring(0, 1).toUpperCase(Locale.US) + lower.substring(1)
    }

    private fun Long.toHumanReadable(): String {
        if (this <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = this.toDouble()
        var idx = 0
        while (value >= 1024 && idx < units.lastIndex) {
            value /= 1024
            idx++
        }
        return String.format(Locale.US, "%.1f %s", value, units[idx])
    }

    private fun ensureStoragePermission(onGranted: () -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            onGranted()
            return
        }
        val missing = REQUIRED_PERMISSIONS.any {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (!missing) {
            onGranted()
            return
        }
        pendingPermissionAction = onGranted
        requestPermissions(REQUIRED_PERMISSIONS, REQUEST_STORAGE_PERMISSIONS)
    }

    /**
     * Example function showing how to configure the DownloadForegroundService.
     * This configuration is persisted and will be used whenever the service starts.
     * 
     * Call this method once during app initialization (e.g., in onCreate) before
     * using any download functionality.
     */
    private fun configureDownloadService() {
        DownloadForegroundService.configureService(this) {
            // Chunking configuration
            chunkCount(4)
            chunkParallel(true)
            chunkMinSize(256 * 1024L)

            // Retry policy
            retryPolicy(
                maxAttempts = 5,
                initialDelayMillis = 3_000L,
                backoffMultiplier = 1.5f
            )

            // Notification configuration
            notificationChannel(
                id = "sample_downloads",
                name = "Sample Downloads",
                description = "Foreground sample channel"
            )
            notificationShowProgress(true)
            notificationPersistent(true)

            // Scheduler configuration (optional)
            periodicSchedule(intervalMinutes = 60)

            // Storage configuration
            storageDestinations(listOf(DownloadDestination.Custom(getDefaultDownloadPath())))
            storageOverwrite(true)
            storageValidateFreeSpace(true)

            // Installer configuration
            installerPromptOnCompletion(true)
        }

        Log.d(TAG, "Download service configured successfully")
    }

    /**
     * Helper function to get the default download path.
     */
    private fun getDefaultDownloadPath(): String {
        val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
        if (!dir.exists()) dir.mkdirs()
        return dir.absolutePath
    }

    private fun buildPreviewConfig(): DownloadConfig {
        return DownloadConfig(
            chunking = ChunkingConfig(
                chunkCount = 4,
                minChunkSizeBytes = 256 * 1024L,
                preferParallel = true
            ),
            retryPolicy = RetryPolicy(
                maxAttempts = 5,
                initialDelayMillis = 3_000L,
                backoffMultiplier = 1.5f
            ),
            notification = NotificationConfig(
                channelId = "sample_downloads",
                channelName = "Sample Downloads",
                channelDescription = "Foreground sample channel",
                showProgressPercentage = true,
                persistent = true,
                smallIconRes = R.mipmap.ic_launcher
            ),
            scheduler = SchedulerConfig(
                periodicIntervalMinutes = 60,
                exactStartTime = null,
                allowWhileIdle = true,
                useAlarmManager = false
            ),
            storage = StorageConfig(
                overwriteExisting = true,
                validateFreeSpace = true,
                preferExternalPublic = true
            ),
            installer = InstallerConfig(
                promptOnCompletion = true
            )
        )
    }

    companion object {
        private const val MIN_UI_INTERVAL_MS = 750L
        private const val MIN_UI_BYTES_STEP = 128 * 1024L
        private const val TAG = "MainActivity"
        private const val SAMPLE_URL = "https://www.dl.farsroid.com/ap/Duolingo-Unlocked-6.56.3(FarsRoid.Com).apk"
        private const val REQUEST_STORAGE_PERMISSIONS = 100
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
    }
}