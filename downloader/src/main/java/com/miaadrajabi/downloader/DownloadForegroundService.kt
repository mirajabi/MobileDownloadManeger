package com.miaadrajabi.downloader

import android.app.Notification
import android.app.Service
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Foreground service that owns the MobileDownloadManager lifecycle and processes commands.
 */
class DownloadForegroundService : Service() {

    private lateinit var manager: MobileDownloadManager
    private val uiListeners get() = Companion.uiListeners
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    private var foregroundStarted = false

    override fun onCreate() {
        super.onCreate()
        manager = MobileDownloadManager.create(this) {
            chunkCount(4)
            chunkParallel(true)
            chunkMinSize(256 * 1024L)
            retryPolicy(maxAttempts = 5, initialDelayMillis = 3_000L, backoffMultiplier = 1.5f)
            notificationChannel(
                id = "sample_downloads",
                name = "Sample Downloads",
                description = "Foreground sample channel"
            )
            notificationShowProgress(true)
            notificationPersistent(true)
            periodicSchedule(intervalMinutes = 60)
            storageDestinations(listOf(DownloadDestination.Custom(defaultDownloadPath(applicationContext))))
            storageOverwrite(true)
            storageValidateFreeSpace(true)
            installerPromptOnCompletion(true)
            notificationIcon(notificationIconRes ?: android.R.drawable.stat_sys_download)
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
                override fun onCancelled(handle: DownloadHandle) = relay { it.onCancelled(handle) }
                override fun onRetry(handle: DownloadHandle, attempt: Int) =
                    relay { it.onRetry(handle, attempt) }
            })
        }
        Log.d(TAG, "Download manager initialized inside service")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY
        when (intent.action) {
            ACTION_UPDATE_NOTIFICATION -> {
                val notification: Notification = intent.getParcelableExtra(EXTRA_NOTIFICATION)
                    ?: return START_NOT_STICKY
                updateForeground(notification)
            }
            ACTION_ENQUEUE -> {
                val request = DownloadRequestAdapter.fromIntent(intent)
                if (request != null) {
                    Log.d(TAG, "Enqueue request ${request.id}")
                    manager.enqueue(request)
                }
            }
            ACTION_PAUSE -> {
                val id = intent.getStringExtra(EXTRA_HANDLE_ID)
                if (id != null) {
                    Log.d(TAG, "Pause request $id")
                    manager.pause(id)
                }
            }
            ACTION_RESUME -> {
                val id = intent.getStringExtra(EXTRA_HANDLE_ID)
                if (id != null) {
                    Log.d(TAG, "Resume request $id")
                    manager.resume(id)
                }
            }
            ACTION_STOP -> {
                val id = intent.getStringExtra(EXTRA_HANDLE_ID)
                if (id != null) {
                    Log.d(TAG, "Stop request $id")
                    manager.stop(id)
                    manager.cancelScheduled(id)
                }
            }
            ACTION_SCHEDULE -> {
                val request = DownloadRequestAdapter.fromIntent(intent)
                val schedule = intent.readScheduleTime()
                if (request != null && schedule != null) {
                    Log.d(TAG, "Schedule request ${request.id} for $schedule")
                    manager.schedule(request, schedule)
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun relay(block: (DownloadListener) -> Unit) {
        uiListeners.forEach { listener ->
            try {
                block(listener)
            } catch (_: Throwable) {
                // Ignore listener failures to avoid breaking service callbacks
            }
        }
    }

    private fun updateForeground(notification: Notification) {
        if (!foregroundStarted) {
            startForeground(DownloadNotificationHelper.FOREGROUND_NOTIFICATION_ID, notification)
            foregroundStarted = true
        } else {
            notificationManager.notify(DownloadNotificationHelper.FOREGROUND_NOTIFICATION_ID, notification)
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

        private var notificationIconRes: Int? = null
        private val uiListeners = CopyOnWriteArrayList<DownloadListener>()

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
        fun enqueueDownload(context: Context, request: DownloadRequest) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_ENQUEUE
                DownloadRequestAdapter.putExtras(this, request)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        @JvmStatic
        fun pauseDownload(context: Context, handleId: String) {
            ContextCompat.startForegroundService(context, Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_PAUSE
                putExtra(EXTRA_HANDLE_ID, handleId)
            })
        }

        @JvmStatic
        fun resumeDownload(context: Context, handleId: String) {
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
            ContextCompat.startForegroundService(context, Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_STOP
                putExtra(EXTRA_HANDLE_ID, handleId)
            })
        }

        @JvmStatic
        fun stopService(context: Context) {
            context.stopService(Intent(context, DownloadForegroundService::class.java))
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

