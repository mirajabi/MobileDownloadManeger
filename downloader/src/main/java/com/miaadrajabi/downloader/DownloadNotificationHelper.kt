package com.miaadrajabi.downloader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * 1. Builds and dispatches user-visible notifications for each download.
 */
internal class DownloadNotificationHelper(
    private val context: Context,
    private val config: NotificationConfig
) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val iconRes = config.smallIconRes ?: DEFAULT_ICON

    init {
        ensureChannel()
    }

    /**
     * 2. Listener hooked into the download pipeline to mirror state changes.
     */
    val listener: DownloadListener = object : DownloadListener {
        override fun onQueued(handle: DownloadHandle) {
            showStatus(
                handle,
                title = "Queued download",
                text = handle.source,
                indeterminate = true,
                withActions = true
            )
        }

        override fun onStarted(handle: DownloadHandle) {
            showStatus(
                handle,
                title = "Starting download",
                text = handle.source,
                indeterminate = true,
                withActions = true
            )
        }

        override fun onProgress(handle: DownloadHandle, progress: DownloadProgress) {
            showProgress(handle, progress)
        }

        override fun onPaused(handle: DownloadHandle) {
            showStatus(
                handle,
                title = "Download paused",
                text = handle.source,
                indeterminate = false,
                withActions = true,
                isPaused = true
            )
        }

        override fun onResumed(handle: DownloadHandle) {
            showStatus(
                handle,
                title = "Resuming download",
                text = handle.source,
                indeterminate = true,
                withActions = true,
                isPaused = false
            )
        }

        override fun onCompleted(handle: DownloadHandle) {
            showStatus(
                handle,
                title = "Download complete",
                text = handle.source,
                indeterminate = false,
                ongoing = false
            )
        }

        override fun onFailed(handle: DownloadHandle, error: Throwable?) {
            showStatus(
                handle,
                title = "Download failed",
                text = error?.localizedMessage ?: "Unknown error",
                indeterminate = false,
                ongoing = false
            )
        }

        override fun onCancelled(handle: DownloadHandle) {
            cancel()
        }
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
        isPaused: Boolean = false
    ) {
        val builder = baseBuilder(ongoing)
            .setContentTitle(title)
            .setContentText(text)
            .setProgress(0, 0, indeterminate)
        if (withActions && ongoing) {
            addControlActions(builder, handle, isPaused)
        }
        startForegroundWith(builder.build())
    }

    private fun showProgress(handle: DownloadHandle, progress: DownloadProgress) {
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
        startForegroundWith(builder.build())
    }

    fun buildForegroundNotification(title: String, text: String): Notification {
        return baseBuilder()
            .setContentTitle(title)
            .setContentText(text)
            .setProgress(0, 0, true)
            .build()
    }

    fun notifyForeground(notification: Notification) {
        notificationManager.notify(FOREGROUND_NOTIFICATION_ID, notification)
    }

    fun cancel() {
        notificationManager.cancel(FOREGROUND_NOTIFICATION_ID)
    }

    private fun startForegroundWith(notification: Notification) {
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

    private fun actionPendingIntent(action: String, handle: DownloadHandle) =
        PendingIntent.getBroadcast(
            context,
            (action + handle.id).hashCode(),
            Intent(context, DownloadNotificationActionReceiver::class.java).apply {
                this.action = action
                putExtra(DownloadNotificationActionReceiver.EXTRA_HANDLE_ID, handle.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag()
        )

    private fun mutableFlag(): Int = 0

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
        const val FOREGROUND_NOTIFICATION_ID = 7001
        private const val DEFAULT_ICON = android.R.drawable.stat_sys_download
    }
}

