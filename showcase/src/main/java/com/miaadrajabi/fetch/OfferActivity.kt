package com.miaadrajabi.fetch

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Asks whether a copied link should start now.
 */
class OfferActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val links = intent.getStringArrayListExtra(EXTRA_LINKS) ?: arrayListOf()
        if (links.isEmpty()) {
            finish()
            return
        }
        val names = links.joinToString("\n") { LinkParser.fileNameFrom(it) }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(if (links.size == 1) "Start this download?" else "Start these downloads?")
            .setMessage(names)
            .setPositiveButton("Start") { _, _ ->
                startAll(links)
                finish()
            }
            .setNegativeButton("Not now") { _, _ -> finish() }
            .create()
        dialog.setOnDismissListener { if (!isFinishing) finish() }
        dialog.show()
    }

    private fun startAll(links: List<String>) {
        val settings = EngineSettings.load(this)
        val names = LinkParser.uniqueNames(links, "")
        val store = (application as FetchApp).store
        for (index in links.indices) {
            val request = DownloadDesk.newRequest(
                url = links[index],
                fileName = names[index],
                headers = emptyMap(),
                checksum = null,
                algorithm = com.miaadrajabi.downloader.ChecksumAlgorithm.SHA256,
                rangeStart = null,
                rangeEndInclusive = null
            )
            store.insert(
                Transfer(
                    id = request.id,
                    url = request.url,
                    fileName = request.fileName,
                    status = TransferStore.STATUS_QUEUED,
                    message = "Waiting to start"
                )
            )
            DownloadDesk.enqueue(this, settings, request)
        }
    }

    companion object {
        const val EXTRA_LINKS = "links"
    }
}
