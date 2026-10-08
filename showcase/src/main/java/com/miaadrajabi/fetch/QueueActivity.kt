package com.miaadrajabi.fetch

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.miaadrajabi.downloader.DownloadForegroundService
import com.miaadrajabi.fetch.databinding.ActivityQueueBinding

class QueueActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQueueBinding
    private val store by lazy { (application as FetchApp).store }
    private val onQueue: (List<Transfer>) -> Unit = { items ->
        adapter.submitList(items)
        val empty = items.isEmpty()
        binding.emptyGroup.visibility = if (empty) View.VISIBLE else View.GONE
        binding.queue.visibility = if (empty) View.GONE else View.VISIBLE
        binding.add.visibility = if (empty) View.GONE else View.VISIBLE
    }
    private val adapter = QueueAdapter(
        onPause = { item ->
            DownloadForegroundService.pauseDownload(this, item.id)
            store.mark(item.id, TransferStore.STATUS_PAUSED, "Paused")
        },
        onResume = { item ->
            DownloadForegroundService.resumeDownload(this, item.id)
            store.mark(item.id, TransferStore.STATUS_RUNNING, "Resuming")
        },
        onStop = { item ->
            DownloadForegroundService.stopDownload(this, item.id)
            store.mark(item.id, TransferStore.STATUS_STOPPED, "Stopped")
        },
        onRemove = { item -> store.remove(item.id) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQueueBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.queue.layoutManager = LinearLayoutManager(this)
        binding.queue.adapter = adapter
        (binding.queue.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        binding.settings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.add.setOnClickListener { openComposer() }
        binding.emptyAdd.setOnClickListener { openComposer() }
    }

    override fun onStart() {
        super.onStart()
        store.watch(onQueue)
    }

    override fun onStop() {
        store.unwatch(onQueue)
        super.onStop()
    }

    private fun openComposer() {
        startActivity(Intent(this, ComposerActivity::class.java))
    }
}
