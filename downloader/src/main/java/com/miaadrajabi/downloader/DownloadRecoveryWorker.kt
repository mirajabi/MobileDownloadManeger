package com.miaadrajabi.downloader

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Restarts interrupted downloads after process death or reboot.
 * WorkManager keeps this work until the durable queue is empty, so a reboot
 * can bring the foreground service back without a boot receiver.
 */
class DownloadRecoveryWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!DownloadRecoveryStore.hasRecoverable(applicationContext)) {
            return@withContext Result.success()
        }
        try {
            DownloadForegroundService.recoverDownloads(applicationContext)
        } catch (error: RuntimeException) {
            if (!isForegroundStartBlocked(error)) {
                throw error
            }
            Log.w(TAG, "Foreground start was blocked; resuming interrupted downloads here", error)
            val config = DownloadConfigStore.load(applicationContext) ?: DownloadConfig()
            val previousManager = DownloadManagerRegistry.manager
            val previousHelper = DownloadNotificationRegistry.helper
            val manager = MobileDownloadManager.createRecovering(applicationContext, config)
            try {
                manager.awaitRecovered()
            } finally {
                manager.discardKeeping(previousManager, previousHelper)
            }
        }
        if (DownloadRecoveryStore.hasRecoverable(applicationContext)) {
            Result.retry()
        } else {
            Result.success()
        }
    }

    private companion object {
        private const val TAG = "DownloadRecoveryWorker"
    }
}
