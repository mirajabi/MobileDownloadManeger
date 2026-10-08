package com.miaadrajabi.fetch

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.miaadrajabi.downloader.DownloadForegroundService
import com.miaadrajabi.fetch.databinding.ActivityQueueBinding
import com.miaadrajabi.fetch.databinding.DialogEditTransferBinding
import java.io.File

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
        onRemove = { item -> store.remove(item.id) },
        onCopy = { item -> export(item, move = false) },
        onMove = { item -> export(item, move = true) },
        onEdit = { item -> edit(item) },
        onFolder = { item -> openFolder(item) }
    )

    private var pendingId: String? = null
    private var pendingMove = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQueueBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.queue.layoutManager = LinearLayoutManager(this)
        binding.queue.adapter = adapter
        (binding.queue.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        if (savedInstanceState != null) {
            pendingId = savedInstanceState.getString(STATE_ID)
            pendingMove = savedInstanceState.getBoolean(STATE_MOVE)
        }
        binding.settings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.add.setOnClickListener { openComposer() }
        binding.emptyAdd.setOnClickListener { openComposer() }
    }

    override fun onStart() {
        super.onStart()
        store.locateFiles(this)
        store.watch(onQueue)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_ID, pendingId)
        outState.putBoolean(STATE_MOVE, pendingMove)
    }

    override fun onStop() {
        store.unwatch(onQueue)
        super.onStop()
    }

    private fun openComposer() {
        startActivity(Intent(this, ComposerActivity::class.java))
    }

    private fun export(item: Transfer, move: Boolean) {
        val file = File(item.localPath)
        if (!file.isFile) {
            toast("That file is not in the download folder anymore.")
            return
        }
        pendingId = item.id
        pendingMove = move
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = SavedFiles.mime(file.name)
        intent.putExtra(Intent.EXTRA_TITLE, file.name)
        intent.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        )
        startActivityForResult(intent, REQUEST_EXPORT)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_EXPORT || resultCode != Activity.RESULT_OK) return
        val destination = data?.data ?: return
        val id = pendingId ?: return
        val item = store.snapshot().firstOrNull { it.id == id } ?: return
        val file = File(item.localPath)
        if (!file.isFile) {
            toast("That file is not in the download folder anymore.")
            return
        }
        toast(if (pendingMove) "Moving…" else "Copying…")
        val move = pendingMove
        val shown = item.copy(message = if (move) "Moving…" else "Copying…")
        store.save(shown)
        store.refresh()
        try {
            contentResolver.takePersistableUriPermission(
                destination,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (error: SecurityException) {
            // The provider may only grant a one-shot write.
        }
        SavedFiles.export(this, file, destination, move) { message ->
            toast(message)
            val latest = store.snapshot().firstOrNull { it.id == id } ?: shown
            if (move && message == "Moved") {
                val placed = SavedFiles.filePath(destination)
                store.save(
                    latest.copy(
                        localPath = placed ?: "",
                        locationUri = destination.toString(),
                        message = "Moved"
                    )
                )
            } else {
                store.save(latest.copy(message = if (message == "Copied") "Saved" else message))
            }
            store.refresh()
        }
    }

    private fun openFolder(item: Transfer) {
        val file = File(item.localPath)
        val dir = file.parentFile
        val canList = dir != null && dir.isDirectory && dir.list() != null
        if (canList) {
            showFolder(dir!!, file.name)
            return
        }
        val view = folderViewUri(item)
        if (view != null && openSystemFolder(view)) return
        if (dir != null) {
            showFolder(dir, file.name)
            return
        }
        toast("The download folder is not available.")
    }

    private fun showFolder(dir: File, focus: String) {
        startActivity(
            Intent(this, FolderActivity::class.java)
                .putExtra(FolderActivity.EXTRA_DIR, dir.absolutePath)
                .putExtra(FolderActivity.EXTRA_FOCUS, focus)
        )
    }

    private fun openSystemFolder(uri: Uri): Boolean {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return try {
            startActivity(intent)
            true
        } catch (error: ActivityNotFoundException) {
            false
        }
    }

    private fun folderViewUri(item: Transfer): Uri? {
        if (item.locationUri.isNotBlank()) {
            val parent = SavedFiles.folderUri(Uri.parse(item.locationUri))
            if (parent != null) return parent
        }
        val path = item.localPath
        val root = Environment.getExternalStorageDirectory().absolutePath
        if (!path.startsWith(root)) return null
        val relative = path.removePrefix(root).trimStart('/')
        val folder = relative.substringBeforeLast('/', "")
        if (folder.isEmpty()) return null
        return DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents",
            "primary:$folder"
        )
    }

    private fun edit(item: Transfer) {
        val form = DialogEditTransferBinding.inflate(layoutInflater)
        form.titleInput.setText(item.title.ifBlank { item.fileName })
        form.fileInput.setText(item.fileName)
        form.urlInput.setText(item.url)
        val busy = isBusy(item)
        form.fileInput.isEnabled = !busy
        if (busy) {
            form.fileLayout.helperText = "The file name can change after the download stops."
        }
        form.copyLink.setOnClickListener {
            val link = form.urlInput.text?.toString().orEmpty()
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("download", link))
            toast("Link copied")
        }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Edit")
            .setView(form.root)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val saved = applyEdit(item, form) ?: return@setOnClickListener
                store.save(saved)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun applyEdit(item: Transfer, form: DialogEditTransferBinding): Transfer? {
        val title = form.titleInput.text?.toString()?.trim().orEmpty()
        val url = form.urlInput.text?.toString()?.trim().orEmpty()
        val lower = url.toLowerCase(java.util.Locale.US)
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            form.urlLayout.error = "The link still has to start with http:// or https://."
            return null
        }
        if (isBusy(item)) {
            return item.copy(title = title, url = url)
        }
        val fileName = LinkParser.sanitizeFileName(form.fileInput.text?.toString().orEmpty())
        if (fileName.isBlank()) {
            form.fileLayout.error = "Use a file name with letters or numbers."
            return null
        }
        var path = item.localPath
        if (fileName != item.fileName && path.isNotBlank()) {
            val source = File(path)
            if (source.isFile) {
                val parent = source.parentFile
                val target = if (parent == null) File(fileName) else File(parent, fileName)
                if (target.exists() && target.absolutePath != source.absolutePath) {
                    form.fileLayout.error = "A file with that name is already in the folder."
                    return null
                }
                if (!source.renameTo(target)) {
                    form.fileLayout.error = "The file could not be renamed."
                    return null
                }
                path = target.absolutePath
            }
        }
        return item.copy(title = title, url = url, fileName = fileName, localPath = path)
    }

    private fun isBusy(item: Transfer): Boolean {
        return item.status == TransferStore.STATUS_RUNNING ||
            item.status == TransferStore.STATUS_QUEUED ||
            item.status == TransferStore.STATUS_RETRYING ||
            item.status == TransferStore.STATUS_PAUSED ||
            item.status == TransferStore.STATUS_SCHEDULED
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val REQUEST_EXPORT = 41
        private const val STATE_ID = "pendingId"
        private const val STATE_MOVE = "pendingMove"
    }
}
