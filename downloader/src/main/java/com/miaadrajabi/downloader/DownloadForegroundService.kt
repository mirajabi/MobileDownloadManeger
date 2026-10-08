package com.miaadrajabi.downloader

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Foreground service that owns the MobileDownloadManager lifecycle and processes commands.
 *
 * startForegroundService must be followed by startForeground before any disk or network work.
 * The first notification is a local placeholder; download progress replaces it later.
 */
class DownloadForegroundService : Service() {

    private lateinit var manager: MobileDownloadManager
    private val uiListeners get() = Companion.uiListeners
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    @Volatile
    private var foregroundStarted = false
    @Volatile
    private var destroyed = false

    override fun onCreate() {
        super.onCreate()
        runningInstance = this
        // Satisfy the foreground-service contract before config, files, or the network.
        promoteToForegroundImmediately()
        manager = createManagerFromConfig()
        Log.d(TAG, "Download manager initialized from stored configuration")
    }

    /**
     * Creates the MobileDownloadManager by loading configuration from DownloadConfigStore.
     * If no configuration exists, throws an exception to prevent the service from running
     * without proper setup. Users must call configureService() before starting the service.
     */
    private fun createManagerFromConfig(): MobileDownloadManager {
        val savedConfig = DownloadConfigStore.load(applicationContext) ?: DownloadConfig().also {
            Log.w(TAG, "Saved configuration is missing or corrupt; using defaults")
        }

        return MobileDownloadManager.createRecovering(this) {
            // Apply chunking configuration
            chunkCount(savedConfig.chunking.chunkCount)
            chunkParallel(savedConfig.chunking.preferParallel)
            chunkMinSize(savedConfig.chunking.minChunkSizeBytes)

            // Apply retry policy
            retryPolicy(
                maxAttempts = savedConfig.retryPolicy.maxAttempts,
                initialDelayMillis = savedConfig.retryPolicy.initialDelayMillis,
                backoffMultiplier = savedConfig.retryPolicy.backoffMultiplier
            )

            // Apply notification configuration
            notificationChannel(
                id = savedConfig.notification.channelId,
                name = savedConfig.notification.channelName,
                description = savedConfig.notification.channelDescription
            )
            notificationShowProgress(savedConfig.notification.showProgressPercentage)
            notificationPersistent(savedConfig.notification.persistent)
            savedConfig.notification.smallIconRes?.let { notificationIcon(it) }
                ?: notificationIcon(notificationIconRes ?: android.R.drawable.stat_sys_download)

            // Apply scheduler configuration
            val scheduleTime = savedConfig.scheduler.exactStartTime
            if (scheduleTime != null) {
                if (scheduleTime.year != null && scheduleTime.month != null && scheduleTime.dayOfMonth != null) {
                    exactScheduleDate(
                        year = scheduleTime.year,
                        month = scheduleTime.month,
                        dayOfMonth = scheduleTime.dayOfMonth,
                        hour = scheduleTime.hour,
                        minute = scheduleTime.minute,
                        allowWhileIdle = savedConfig.scheduler.allowWhileIdle
                    )
                } else {
                    exactSchedule(
                        hour = scheduleTime.hour,
                        minute = scheduleTime.minute,
                        weekday = scheduleTime.weekday,
                        allowWhileIdle = savedConfig.scheduler.allowWhileIdle
                    )
                }
            } else {
                savedConfig.scheduler.periodicIntervalMinutes?.let { periodicSchedule(intervalMinutes = it) }
            }
            schedulerUseAlarmManager(savedConfig.scheduler.useAlarmManager)

            // Apply storage configuration
            storageDestinations(savedConfig.storage.downloadDirs)
            storageOverwrite(savedConfig.storage.overwriteExisting)
            storageValidateFreeSpace(
                savedConfig.storage.validateFreeSpace,
                savedConfig.storage.minFreeSpaceBytes
            )
            storageUsePublicDownloads(savedConfig.storage.preferExternalPublic)

            // Apply installer configuration
            installerPromptOnCompletion(
                savedConfig.installer.promptOnCompletion,
                savedConfig.installer.fallbackMimeType
            )
            integrityValidation(
                verifyFileSize = savedConfig.integrity.verifyFileSize,
                verifyChecksum = savedConfig.integrity.verifyChecksum,
                verifyApkStructure = savedConfig.integrity.verifyApkStructure,
                verifyContentType = savedConfig.integrity.verifyContentType,
                verifyApkSignature = savedConfig.integrity.verifyApkSignature
            )

            // Add relay listener for UI communication
            addListener(object : DownloadListener {
                override fun onQueued(handle: DownloadHandle) = relay { it.onQueued(handle) }
                override fun onStarted(handle: DownloadHandle) = relay { it.onStarted(handle) }
                override fun onProgress(handle: DownloadHandle, progress: DownloadProgress) =
                    relay { it.onProgress(handle, progress) }
                override fun onPaused(handle: DownloadHandle) = relay { it.onPaused(handle) }
                override fun onResumed(handle: DownloadHandle) = relay { it.onResumed(handle) }
                override fun onCompleted(handle: DownloadHandle) = relay { it.onCompleted(handle) }
                override fun onFailed(handle: DownloadHandle, error: Throwable?) =
                    relay { it.onFailed(handle, error) }
                override fun onRetry(handle: DownloadHandle, attempt: Int) =
                    relay { it.onRetry(handle, attempt) }
                override fun onCancelled(handle: DownloadHandle) = relay { it.onCancelled(handle) }
            })
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val accepted = handleCommand(intent)
        val running = if (::manager.isInitialized) manager.hasRunningDownloads() else false
        return when (
            foregroundContinuation(
                action = intent?.action,
                commandAccepted = accepted,
                hasRunningDownloads = running
            )
        ) {
            ForegroundContinuation.StopIdle -> {
                Log.d(TAG, "No active download for action=${intent?.action}; stopping foreground service")
                leaveForeground(removeNotification = true)
                stopSelf(startId)
                START_NOT_STICKY
            }
            ForegroundContinuation.AlreadyStopping -> START_NOT_STICKY
            ForegroundContinuation.KeepRunning -> START_STICKY
        }
    }

    override fun onDestroy() {
        destroyed = true
        if (::manager.isInitialized) {
            manager.clearPendingNotificationUpdates()
        }
        if (runningInstance === this) {
            runningInstance = null
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun handleCommand(intent: Intent?): Boolean {
        if (intent == null || !::manager.isInitialized) {
            return false
        }
        return when (intent.action) {
            ACTION_UPDATE_NOTIFICATION -> {
                val notification: Notification? = intent.getParcelableExtra(EXTRA_NOTIFICATION)
                if (notification == null) {
                    Log.w(TAG, "Foreground notification extra was missing; keeping the startup notification")
                    true
                } else {
                    updateForeground(notification)
                    true
                }
            }
            ACTION_ENQUEUE -> {
                val request = DownloadRequestAdapter.fromIntent(intent) ?: return false
                Log.d(TAG, "Enqueue request ${request.id}")
                manager.enqueue(request)
                true
            }
            ACTION_PAUSE -> {
                val id = intent.getStringExtra(EXTRA_HANDLE_ID) ?: return false
                Log.d(TAG, "Pause request $id")
                manager.pause(id)
            }
            ACTION_RESUME -> {
                val id = intent.getStringExtra(EXTRA_HANDLE_ID) ?: return false
                Log.d(TAG, "Resume request $id")
                manager.resume(id)
            }
            ACTION_STOP -> {
                val id = intent.getStringExtra(EXTRA_HANDLE_ID) ?: return false
                Log.d(TAG, "Stop request $id")
                val stopped = manager.stop(id)
                manager.cancelScheduled(id)
                stopped
            }
            ACTION_SCHEDULE -> {
                val request = DownloadRequestAdapter.fromIntent(intent) ?: return false
                val schedule = intent.readScheduleTime() ?: return false
                Log.d(TAG, "Schedule request ${request.id} for $schedule")
                manager.schedule(request, schedule)
                true
            }
            else -> false
        }
    }

    private fun relay(block: (DownloadListener) -> Unit) {
        uiListeners.forEach { listener ->
            try {
                block(listener)
            } catch (_: Throwable) {
                // Ignore listener failures to avoid breaking service callbacks
            }
        }
    }

    private fun promoteToForegroundImmediately() {
        enterForeground(
            DownloadNotificationHelper.buildStartupNotification(
                this,
                android.R.drawable.stat_sys_download
            )
        )
        Log.d(TAG, "Entered foreground before download manager initialization")
    }

    private fun updateForeground(notification: Notification) {
        if (!foregroundStarted) {
            enterForeground(notification)
        } else {
            notificationManager.notify(
                DownloadNotificationHelper.FOREGROUND_NOTIFICATION_ID,
                notification
            )
        }
    }

    private fun enterForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                DownloadNotificationHelper.FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(
                DownloadNotificationHelper.FOREGROUND_NOTIFICATION_ID,
                notification
            )
        }
        foregroundStarted = true
    }

    private fun leaveForeground(removeNotification: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val flags = if (removeNotification) {
                STOP_FOREGROUND_REMOVE
            } else {
                STOP_FOREGROUND_DETACH
            }
            stopForeground(flags)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(removeNotification)
        }
    }

    companion object {
        private const val TAG = "DownloadService"
        const val EXTRA_NOTIFICATION = "extra_notification"
        const val EXTRA_HANDLE_ID = "extra_handle_id"
        const val ACTION_UPDATE_NOTIFICATION = "com.miaadrajabi.downloader.action.UPDATE_NOTIFICATION"
        const val ACTION_ENQUEUE = "com.miaadrajabi.downloader.action.ENQUEUE"
        const val ACTION_PAUSE = "com.miaadrajabi.downloader.action.PAUSE"
        const val ACTION_RESUME = "com.miaadrajabi.downloader.action.RESUME"
        const val ACTION_STOP = "com.miaadrajabi.downloader.action.STOP"
        const val ACTION_SCHEDULE = "com.miaadrajabi.downloader.action.SCHEDULE"
        const val ACTION_RECOVER = "com.miaadrajabi.downloader.action.RECOVER"

        private var notificationIconRes: Int? = null
        private val uiListeners = CopyOnWriteArrayList<DownloadListener>()

        @Volatile
        private var runningInstance: DownloadForegroundService? = null

        /**
         * Updates the already-foreground notification in-process.
         * Returns false only when the service is not running, so the caller can start it.
         * A destroyed instance returns true to avoid restarting the service from a late callback.
         */
        internal fun postNotification(notification: Notification): Boolean {
            val service = runningInstance ?: return false
            if (service.destroyed) {
                return true
            }
            service.notificationManager.notify(
                DownloadNotificationHelper.FOREGROUND_NOTIFICATION_ID,
                notification
            )
            return true
        }

        /**
         * Configures the download service with the specified settings.
         * This must be called before starting the service to define how downloads should behave.
         * The configuration is persisted and will be used when the service starts.
         *
         * Example usage:
         * ```
         * DownloadForegroundService.configureService(context) {
         *     chunkCount(4)
         *     chunkParallel(true)
         *     retryPolicy(maxAttempts = 5)
         *     notificationChannel("downloads", "Downloads", "Download notifications")
         *     storageDestinations(listOf(DownloadDestination.Downloads))
         * }
         * ```
         */
        @JvmStatic
        fun configureService(context: Context, configure: DownloadManagerBuilder.() -> Unit) {
            val previousManager = DownloadManagerRegistry.manager
            val previousHelper = DownloadNotificationRegistry.helper
            val tempManager = MobileDownloadManager.create(context, configure)
            tempManager.discardKeeping(previousManager, previousHelper)
        }

        @JvmStatic
        fun setNotificationIcon(resId: Int) {
            notificationIconRes = resId
        }

        @JvmStatic
        fun registerListener(listener: DownloadListener) {
            uiListeners += listener
        }

        @JvmStatic
        fun unregisterListener(listener: DownloadListener) {
            uiListeners -= listener
        }

        @JvmStatic
        fun recoverDownloads(context: Context) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_RECOVER
            }
            ContextCompat.startForegroundService(context, intent)
        }

        @JvmStatic
        fun enqueueDownload(context: Context, request: DownloadRequest) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_ENQUEUE
                DownloadRequestAdapter.putExtras(this, request)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        @JvmStatic
        fun pauseDownload(context: Context, handleId: String) {
            val manager = DownloadManagerRegistry.manager
            if (manager != null && manager.pause(handleId)) {
                return
            }
            ContextCompat.startForegroundService(context, Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_PAUSE
                putExtra(EXTRA_HANDLE_ID, handleId)
            })
        }

        @JvmStatic
        fun resumeDownload(context: Context, handleId: String) {
            val manager = DownloadManagerRegistry.manager
            if (manager != null && manager.resume(handleId)) {
                return
            }
            ContextCompat.startForegroundService(context, Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_RESUME
                putExtra(EXTRA_HANDLE_ID, handleId)
            })
        }

        @JvmStatic
        fun scheduleDownload(context: Context, request: DownloadRequest, scheduleTime: ScheduleTime) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_SCHEDULE
                DownloadRequestAdapter.putExtras(this, request)
                putScheduleExtras(scheduleTime)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        @JvmStatic
        fun stopDownload(context: Context, handleId: String) {
            val manager = DownloadManagerRegistry.manager
            if (manager != null && manager.stop(handleId)) {
                return
            }
            ContextCompat.startForegroundService(context, Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_STOP
                putExtra(EXTRA_HANDLE_ID, handleId)
            })
        }

        @JvmStatic
        fun stopService(context: Context) {
            val service = runningInstance
            if (service != null && !service.destroyed) {
                service.leaveForeground(removeNotification = false)
                service.stopSelf()
            } else {
                context.stopService(Intent(context, DownloadForegroundService::class.java))
            }
        }
    }
}

private fun Intent.putScheduleExtras(scheduleTime: ScheduleTime) {
    putExtra("sch_hour", scheduleTime.hour)
    putExtra("sch_minute", scheduleTime.minute)
    scheduleTime.year?.let { putExtra("sch_year", it) }
    scheduleTime.month?.let { putExtra("sch_month", it) }
    scheduleTime.dayOfMonth?.let { putExtra("sch_day", it) }
    scheduleTime.weekday?.let { putExtra("sch_weekday", it.name) }
}

private fun Intent.readScheduleTime(): ScheduleTime? {
    val hour = getIntExtra("sch_hour", -1)
    val minute = getIntExtra("sch_minute", -1)
    if (hour == -1 || minute == -1) return null
    val weekdayName = getStringExtra("sch_weekday")
    val weekday = weekdayName?.let { Weekday.valueOf(it) }
    val year = if (hasExtra("sch_year")) getIntExtra("sch_year", 0) else null
    val month = if (hasExtra("sch_month")) getIntExtra("sch_month", 0) else null
    val day = if (hasExtra("sch_day")) getIntExtra("sch_day", 0) else null
    return ScheduleTime(
        hour = hour,
        minute = minute,
        weekday = weekday,
        year = year,
        month = month,
        dayOfMonth = day
    )
}

private fun defaultDownloadPath(context: Context): String {
    val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        ?: context.filesDir
    if (!dir.exists()) dir.mkdirs()
    return dir.absolutePath
}
