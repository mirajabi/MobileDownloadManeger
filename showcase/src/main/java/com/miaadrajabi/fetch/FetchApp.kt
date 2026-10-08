package com.miaadrajabi.fetch

import android.app.Application
import com.miaadrajabi.downloader.DownloadForegroundService

class FetchApp : Application() {

    lateinit var store: TransferStore
        private set

    override fun onCreate() {
        super.onCreate()
        store = TransferStore(this)
        val settings = EngineSettings.load(this)
        DownloadDesk.apply(this, settings)
        DownloadForegroundService.registerListener(store.bridge)
        if (settings.watchClipboard) {
            ClipWatchService.start(this)
        }
    }
}
