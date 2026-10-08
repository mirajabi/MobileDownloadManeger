package com.miaadrajabi.downloader

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.runBlocking

/**
 * 1. Receives AlarmManager intents and enqueues the targeted download.
 * The receiver stays alive until the hand-off finishes. A blocked foreground start
 * falls back to one owned manager and waits for that download.
 */
class DownloadAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val request = try {
            DownloadRequestAdapter.fromIntent(intent)
        } catch (error: IllegalArgumentException) {
            Log.e(TAG, "Scheduled download has a malformed checksum", error)
            return
        } ?: return
        val pendingResult = goAsync()
        Thread {
            try {
                if (DownloadConfigStore.load(context) == null) {
                    Log.w(TAG, "Scheduled download is using default configuration")
                }
                try {
                    DownloadForegroundService.enqueueDownload(context, request)
                } catch (error: RuntimeException) {
                    if (!isForegroundStartBlocked(error)) {
                        throw error
                    }
                    Log.w(TAG, "Foreground start was blocked; finishing the download in this receiver", error)
                    runBlocking {
                        DownloadBackgroundFallback.run(context, request)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    private companion object {
        private const val TAG = "DownloadAlarmReceiver"
    }
}
