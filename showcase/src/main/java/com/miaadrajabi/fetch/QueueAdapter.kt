package com.miaadrajabi.fetch

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.miaadrajabi.fetch.databinding.ItemTransferBinding
import java.util.Locale

class QueueAdapter(
    private val onPause: (Transfer) -> Unit,
    private val onResume: (Transfer) -> Unit,
    private val onStop: (Transfer) -> Unit,
    private val onRemove: (Transfer) -> Unit,
    private val onCopy: (Transfer) -> Unit,
    private val onMove: (Transfer) -> Unit,
    private val onEdit: (Transfer) -> Unit,
    private val onFolder: (Transfer) -> Unit
) : ListAdapter<Transfer, QueueAdapter.Holder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemTransferBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class Holder(private val binding: ItemTransferBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Transfer) {
            val context = binding.root.context
            binding.name.text = item.title.ifBlank { item.fileName }
            val named = item.title.isNotBlank() && item.title != item.fileName
            binding.host.text = if (named) item.fileName + "  ·  " + item.host() else item.host()
            binding.status.text = statusLabel(item.status)
            binding.status.setTextColor(context.getColor(statusColor(item.status)))
            binding.detail.text = detail(item)
            val known = item.percent >= 0
            binding.percent.text = if (known) "${item.percent}%" else ""
            binding.progress.isIndeterminate = !known &&
                (item.status == TransferStore.STATUS_RUNNING || item.status == TransferStore.STATUS_QUEUED)
            binding.progress.progress = if (known) item.percent else 0
            val active = item.status == TransferStore.STATUS_RUNNING ||
                item.status == TransferStore.STATUS_QUEUED ||
                item.status == TransferStore.STATUS_RETRYING
            val finished = item.status == TransferStore.STATUS_DONE ||
                item.status == TransferStore.STATUS_FAILED ||
                item.status == TransferStore.STATUS_STOPPED
            binding.pause.visibility = if (active) View.VISIBLE else View.GONE
            binding.resume.visibility = if (item.status == TransferStore.STATUS_PAUSED) View.VISIBLE else View.GONE
            binding.stop.visibility = if (finished) View.GONE else View.VISIBLE
            binding.remove.visibility = if (finished) View.VISIBLE else View.GONE
            val fileReady = item.localPath.isNotBlank() && java.io.File(item.localPath).isFile
            val idle = item.status == TransferStore.STATUS_DONE ||
                item.status == TransferStore.STATUS_FAILED ||
                item.status == TransferStore.STATUS_STOPPED
            val placed = item.localPath.isNotBlank() || item.locationUri.isNotBlank()
            binding.copy.visibility = if (fileReady) View.VISIBLE else View.GONE
            binding.move.visibility = if (fileReady && idle) View.VISIBLE else View.GONE
            binding.folder.visibility = if (placed) View.VISIBLE else View.GONE
            binding.pause.setOnClickListener { onPause(item) }
            binding.resume.setOnClickListener { onResume(item) }
            binding.stop.setOnClickListener { onStop(item) }
            binding.remove.setOnClickListener { onRemove(item) }
            binding.copy.setOnClickListener { onCopy(item) }
            binding.move.setOnClickListener { onMove(item) }
            binding.edit.setOnClickListener { onEdit(item) }
            binding.folder.setOnClickListener { onFolder(item) }
        }
    }

    private fun detail(item: Transfer): String {
        val size = if (item.total > 0L) {
            item.bytes.formatSize() + " / " + item.total.formatSize()
        } else if (item.bytes > 0L) {
            item.bytes.formatSize()
        } else {
            ""
        }
        val speed = if (item.speed > 0L) item.speed.formatSize() + "/s" else ""
        val bits = ArrayList<String>()
        if (item.message.isNotBlank()) bits.add(item.message)
        if (size.isNotBlank()) bits.add(size)
        if (speed.isNotBlank()) bits.add(speed)
        if (bits.isEmpty() && item.whenLabel.isNotBlank()) return item.whenLabel
        if (item.status == TransferStore.STATUS_SCHEDULED && item.whenLabel.isNotBlank()) {
            return item.whenLabel
        }
        return bits.joinToString("  ·  ")
    }

    private fun statusLabel(status: String): String {
        return when (status) {
            TransferStore.STATUS_RUNNING -> "Downloading"
            TransferStore.STATUS_PAUSED -> "Paused"
            TransferStore.STATUS_SCHEDULED -> "Scheduled"
            TransferStore.STATUS_DONE -> "Saved"
            TransferStore.STATUS_FAILED -> "Failed"
            TransferStore.STATUS_STOPPED -> "Stopped"
            TransferStore.STATUS_RETRYING -> "Retrying"
            else -> "Queued"
        }
    }

    private fun statusColor(status: String): Int {
        return when (status) {
            TransferStore.STATUS_RUNNING, TransferStore.STATUS_RETRYING -> R.color.copper
            TransferStore.STATUS_PAUSED, TransferStore.STATUS_SCHEDULED -> R.color.amber
            TransferStore.STATUS_DONE -> R.color.green
            TransferStore.STATUS_FAILED -> R.color.rose
            else -> R.color.muted
        }
    }

    private fun Long.formatSize(): String {
        if (this <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        var value = toDouble()
        var index = 0
        while (value >= 1024.0 && index < units.lastIndex) {
            value /= 1024.0
            index += 1
        }
        return String.format(Locale.US, "%.1f %s", value, units[index])
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Transfer>() {
            override fun areItemsTheSame(oldItem: Transfer, newItem: Transfer): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: Transfer, newItem: Transfer): Boolean {
                return oldItem == newItem
            }
        }
    }
}
