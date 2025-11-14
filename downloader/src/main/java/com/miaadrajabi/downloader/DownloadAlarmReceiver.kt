package com.miaadrajabi.downloader

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 1. Receives AlarmManager intents and enqueues the targeted download.
 */
class DownloadAlarmReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val request = DownloadRequestAdapter.fromIntent(intent) ?: return
        scope.launch {
            val config = DownloadConfigStore.load(context) ?: DownloadConfig()
            val manager = MobileDownloadManager.create(context, config)
            manager.enqueue(request)
        }
    }
}

