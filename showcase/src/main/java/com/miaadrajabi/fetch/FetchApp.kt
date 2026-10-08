package com.miaadrajabi.fetch

import android.app.Application
import com.miaadrajabi.downloader.DownloadForegroundService

class FetchApp : Application() {

    lateinit var store: TransferStore
        private set

    override fun onCreate() {
        super.onCreate()
        store = TransferStore(this)
        DownloadDesk.apply(this, EngineSettings.load(this))
        DownloadForegroundService.registerListener(store.bridge)
    }
}
