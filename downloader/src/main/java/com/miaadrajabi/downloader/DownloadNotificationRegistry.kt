package com.miaadrajabi.downloader

/**
 * 1. Shared holder so the foreground service and manager reuse the same helper instance.
 */
internal object DownloadNotificationRegistry {
    @Volatile
    var helper: DownloadNotificationHelper? = null
}

