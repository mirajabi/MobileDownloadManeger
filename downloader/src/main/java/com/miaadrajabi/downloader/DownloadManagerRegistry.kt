package com.miaadrajabi.downloader

/**
 * 1. Keeps a reference to the most recent MobileDownloadManager for notification actions.
 */
internal object DownloadManagerRegistry {
    @Volatile
    var manager: MobileDownloadManager? = null
}

