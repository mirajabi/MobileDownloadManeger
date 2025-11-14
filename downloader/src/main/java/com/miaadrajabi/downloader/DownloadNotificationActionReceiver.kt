package com.miaadrajabi.downloader

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 1. Handles notification action buttons (pause/resume/stop).
 */
class DownloadNotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val handleId = intent.getStringExtra(EXTRA_HANDLE_ID) ?: return
        when (intent.action) {
            ACTION_PAUSE -> DownloadForegroundService.pauseDownload(context, handleId)
            ACTION_RESUME -> DownloadForegroundService.resumeDownload(context, handleId)
            ACTION_STOP -> DownloadForegroundService.stopDownload(context, handleId)
        }
    }

    companion object {
        const val ACTION_PAUSE = "com.miaadrajabi.downloader.action.PAUSE"
        const val ACTION_RESUME = "com.miaadrajabi.downloader.action.RESUME"
        const val ACTION_STOP = "com.miaadrajabi.downloader.action.STOP"
        const val EXTRA_HANDLE_ID = "extra_handle_id"
    }
}

