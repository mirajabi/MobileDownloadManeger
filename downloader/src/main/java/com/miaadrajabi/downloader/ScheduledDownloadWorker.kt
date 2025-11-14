package com.miaadrajabi.downloader

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 1. WorkManager worker that recreates the manager and enqueues the request.
 */
class ScheduledDownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val request = DownloadRequestAdapter.fromData(inputData) ?: return@withContext Result.failure()
        val config = DownloadConfigStore.load(applicationContext) ?: DownloadConfig()
        val manager = MobileDownloadManager.create(applicationContext, config)
        manager.enqueue(request)
        Result.success()
    }

    companion object {
        const val UNIQUE_WORK_PREFIX = "scheduled-download-"
    }
}

