package com.miaadrajabi.downloader

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 1. Hands a scheduled download to the foreground service.
 * The worker owns the transfer only when the platform refuses a foreground start.
 */
class ScheduledDownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val request = DownloadRequestAdapter.fromData(inputData) ?: return@withContext Result.failure()
        if (DownloadConfigStore.load(applicationContext) == null) {
            Log.e(TAG, "Scheduled download failed because configureService() was not called")
            return@withContext Result.failure()
        }
        try {
            DownloadForegroundService.enqueueDownload(applicationContext, request)
            Result.success()
        } catch (error: RuntimeException) {
            if (!isForegroundStartBlocked(error)) {
                throw error
            }
            Log.w(TAG, "Foreground start was blocked; finishing the download in this worker", error)
            if (DownloadBackgroundFallback.run(applicationContext, request)) {
                Result.success()
            } else {
                Result.failure()
            }
        }
    }

    companion object {
        const val UNIQUE_WORK_PREFIX = "scheduled-download-"
        private const val TAG = "ScheduledDownloadWorker"
    }
}
