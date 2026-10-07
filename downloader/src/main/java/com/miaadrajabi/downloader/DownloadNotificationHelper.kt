package com.miaadrajabi.downloader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Builds and dispatches user-visible notifications for each download.
 * Progress posts are coalesced. Completion, failure, pause, and cancel stay immediate.
 */
internal class DownloadNotificationHelper(
    private val context: Context,
    private val config: NotificationConfig
) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val iconRes = config.smallIconRes ?: DEFAULT_ICON
    private val mainHandler = Handler(Looper.getMainLooper())
    private val renderLock = Any()
    private val callbackLock = Any()
    private val progressSuppressed = AtomicBoolean(false)
    private var scheduledRunnable: Runnable? = null
    private lateinit var progressCoalescer: ProgressUpdateCoalescer

    init {
        progressCoalescer = ProgressUpdateCoalescer(
            clockMillis = { SystemClock.elapsedRealtime() },
            schedule = { delayMillis: Long, token: Int ->
                synchronized(callbackLock) {
                    val runnable = Runnable { progressCoalescer.onScheduled(token) }
                    scheduledRunnable = runnable
                    mainHandler.postDelayed(runnable, delayMillis)
                }
            },
            cancelScheduled = {
                synchronized(callbackLock) {
                    scheduledRunnable?.let { mainHandler.removeCallbacks(it) }
                    scheduledRunnable = null
                }
            },
            emit = { handle: DownloadHandle, progress: DownloadProgress ->
                showProgressNow(handle, progress)
            }
        )
        ensureChannel()
    }

    /**
     * Listener hooked into the download pipeline to mirror state changes.
     * Host [DownloadListener] callbacks are separate and are not coalesced here.
     */
    val listener: DownloadListener = object : DownloadListener {
        override fun onQueued(handle: DownloadHandle) {
            showStatus(
                handle,
                title = "Queued download",
                text = handle.source,
                indeterminate = true,
                withActions = true,
                suppressProgress = false
            )
        }

        override fun onStarted(handle: DownloadHandle) {
            showStatus(
                handle,
                title = "Starting download",
                text = handle.source,
                indeterminate = true,
                withActions = true,
                suppressProgress = false
            )
        }

        override fun onProgress(handle: DownloadHandle, progress: DownloadProgress) {
            if (progressSuppressed.get()) return
            progressCoalescer.submit(handle, progress)
        }

        override fun onPaused(handle: DownloadHandle) {
            showStatus(
                handle,
                title = "Download paused",
                text = handle.source,
                indeterminate = false,
                withActions = true,
                isPaused = true,
                suppressProgress = true
            )
        }

        override fun onResumed(handle: DownloadHandle) {
            showStatus(
                handle,
                title = "Resuming download",
                text = handle.source,
                indeterminate = true,
                withActions = true,
                isPaused = false,
                suppressProgress = false
            )
        }

        override fun onCompleted(handle: DownloadHandle) {
            showStatus(
                handle,
                title = "Download complete",
                text = handle.source,
                indeterminate = false,
                ongoing = false,
                suppressProgress = true
            )
        }

        override fun onFailed(handle: DownloadHandle, error: Throwable?) {
            showStatus(
                handle,
                title = "Download failed",
                text = error?.localizedMessage ?: "Unknown error",
                indeterminate = false,
                ongoing = false,
                suppressProgress = true
            )
        }

        override fun onCancelled(handle: DownloadHandle) {
            cancel()
        }
    }

    fun clearPendingUpdates() {
        synchronized(renderLock) {
            progressSuppressed.set(true)
            progressCoalescer.clear()
        }
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                config.channelId,
                config.channelName,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = config.channelDescription
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showStatus(
        handle: DownloadHandle,
        title: String,
        text: String,
        indeterminate: Boolean,
        ongoing: Boolean = config.persistent,
        withActions: Boolean = false,
        isPaused: Boolean = false,
        suppressProgress: Boolean
    ) {
        val builder = baseBuilder(ongoing)
            .setContentTitle(title)
            .setContentText(text)
            .setProgress(0, 0, indeterminate)
        if (withActions && ongoing) {
            addControlActions(builder, handle, isPaused)
        }
        synchronized(renderLock) {
            progressSuppressed.set(suppressProgress)
            progressCoalescer.clear()
            dispatchNotification(builder.build())
        }
    }

    private fun showProgressNow(handle: DownloadHandle, progress: DownloadProgress) {
        val total = progress.totalBytes
        val detailText = buildProgressText(progress)
        val builder = baseBuilder()
            .setContentTitle("Downloading")
            .setContentText(detailText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(buildSecondaryText(progress)))

        if (total != null && total > 0) {
            val max = total.toIntSafe()
            val current = progress.bytesDownloaded.toIntSafe()
            builder.setProgress(max, current, false)
        } else {
            builder.setProgress(0, 0, true)
        }

        addControlActions(builder, handle, isPaused = false)
        synchronized(renderLock) {
            if (progressSuppressed.get()) return
            dispatchNotification(builder.build())
        }
    }

    fun buildForegroundNotification(title: String, text: String): Notification {
        return baseBuilder()
            .setContentTitle(title)
            .setContentText(text)
            .setProgress(0, 0, true)
            .build()
    }

    fun notifyForeground(notification: Notification) {
        synchronized(renderLock) {
            dispatchNotification(notification)
        }
    }

    fun cancel() {
        synchronized(renderLock) {
            progressSuppressed.set(true)
            progressCoalescer.clear()
            notificationManager.cancel(FOREGROUND_NOTIFICATION_ID)
        }
    }

    private fun dispatchNotification(notification: Notification) {
        if (DownloadForegroundService.postNotification(notification)) {
            return
        }
        val intent = Intent(context, DownloadForegroundService::class.java).apply {
            action = DownloadForegroundService.ACTION_UPDATE_NOTIFICATION
            putExtra(DownloadForegroundService.EXTRA_NOTIFICATION, notification)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    private fun baseBuilder(isOngoing: Boolean = config.persistent): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, config.channelId)
            .setSmallIcon(iconRes)
            .setOngoing(isOngoing)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
    }

    private fun addControlActions(
        builder: NotificationCompat.Builder,
        handle: DownloadHandle,
        isPaused: Boolean
    ) {
        if (isPaused) {
            builder.addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_media_play,
                    "Resume",
                    actionPendingIntent(DownloadNotificationActionReceiver.ACTION_RESUME, handle)
                ).build()
            )
        } else {
            builder.addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_media_pause,
                    "Pause",
                    actionPendingIntent(DownloadNotificationActionReceiver.ACTION_PAUSE, handle)
                ).build()
            )
        }
        builder.addAction(
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                actionPendingIntent(DownloadNotificationActionReceiver.ACTION_STOP, handle)
            ).build()
        )
    }

    private fun actionPendingIntent(action: String, handle: DownloadHandle): PendingIntent {
        val flags = if (Build.VERSION.SDK_INT >= ANDROID_12) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(
            context,
            (action + handle.id).hashCode(),
            Intent(context, DownloadNotificationActionReceiver::class.java).apply {
                this.action = action
                putExtra(DownloadNotificationActionReceiver.EXTRA_HANDLE_ID, handle.id)
            },
            flags
        )
    }

    private fun buildProgressText(progress: DownloadProgress): String {
        val builder = StringBuilder()
        builder.append("Downloaded: ").append(progress.bytesDownloaded.toHumanReadable())
        progress.totalBytes?.let {
            builder.append(" / ").append(it.toHumanReadable())
        }
        progress.percent?.let {
            builder.append(" (").append(it).append("%)")
        }
        return builder.toString()
    }

    private fun buildSecondaryText(progress: DownloadProgress): String {
        val builder = StringBuilder()
        progress.bytesPerSecond?.takeIf { it > 0 }?.let {
            builder.append("Speed: ").append(it.toHumanReadable()).append("/s")
        }
        progress.remainingBytes?.let {
            if (builder.isNotEmpty()) builder.append("\n")
            builder.append("Remaining: ").append(it.toHumanReadable())
        }
        if (builder.isEmpty()) {
            builder.append("Transferring…")
        }
        return builder.toString()
    }

    private fun Long.toHumanReadable(): String {
        if (this <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = this.toDouble()
        var unitIndex = 0
        while (value >= 1024 && unitIndex < units.lastIndex) {
            value /= 1024
            unitIndex++
        }
        return String.format("%.1f %s", value, units[unitIndex])
    }

    private fun Long.toIntSafe(): Int {
        return when {
            this > Int.MAX_VALUE -> Int.MAX_VALUE
            this < Int.MIN_VALUE -> Int.MIN_VALUE
            else -> this.toInt()
        }
    }

    companion object {
        private const val ANDROID_12 = 31
        const val FOREGROUND_NOTIFICATION_ID = 7001
        const val STARTUP_CHANNEL_ID = "com.miaadrajabi.downloader.foreground"
        private const val DEFAULT_ICON = android.R.drawable.stat_sys_download

        /**
         * Notification used only to enter the foreground. It does not read stored config,
         * download callbacks, or progress.
         */
        fun buildStartupNotification(context: Context, smallIconRes: Int): Notification {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    STARTUP_CHANNEL_ID,
                    "Active downloads",
                    NotificationManager.IMPORTANCE_LOW
                )
                channel.description = "Shown while a download is starting"
                notificationManager.createNotificationChannel(channel)
            }
            val icon = if (smallIconRes != 0) smallIconRes else DEFAULT_ICON
            return NotificationCompat.Builder(context, STARTUP_CHANNEL_ID)
                .setSmallIcon(icon)
                .setContentTitle("Download manager")
                .setContentText("Preparing download")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .build()
        }
    }
}
