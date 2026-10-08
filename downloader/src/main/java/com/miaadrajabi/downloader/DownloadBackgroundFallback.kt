package com.miaadrajabi.downloader

import android.content.Context

/**
 * Runs one download on a short-lived manager when the process is not allowed to
 * start a foreground service. Restores whatever manager was already registered.
 */
internal object DownloadBackgroundFallback {

    suspend fun run(context: Context, request: DownloadRequest): Boolean {
        val config = DownloadConfigStore.load(context) ?: DownloadConfig()
        val previousManager = DownloadManagerRegistry.manager
        val previousHelper = DownloadNotificationRegistry.helper
        val manager = MobileDownloadManager.create(context, config)
        return try {
            manager.enqueueAndAwait(request)
        } finally {
            manager.discardKeeping(previousManager, previousHelper)
        }
    }
}
