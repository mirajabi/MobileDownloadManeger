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
                DownloadDesk.startPlain(this, links)
                finish()
            }
            .setNegativeButton("Not now") { _, _ -> finish() }
            .create()
        dialog.setOnDismissListener { if (!isFinishing) finish() }
        dialog.show()
    }

    companion object {
        const val EXTRA_LINKS = "links"
    }
}
