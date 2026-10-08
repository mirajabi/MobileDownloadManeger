package com.miaadrajabi.fetch

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.miaadrajabi.fetch.databinding.ActivityFolderBinding
import com.miaadrajabi.fetch.databinding.ItemFolderFileBinding
import java.io.File
import java.util.Locale

class FolderActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityFolderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.close.setOnClickListener { finish() }
        val dir = File(intent.getStringExtra(EXTRA_DIR).orEmpty())
        val focus = intent.getStringExtra(EXTRA_FOCUS).orEmpty()
        binding.path.text = dir.absolutePath
        val files = if (dir.isDirectory) {
            dir.listFiles()?.sortedBy { it.name.toLowerCase(Locale.US) } ?: emptyList()
        } else {
            emptyList()
        }
        binding.empty.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
        binding.files.layoutManager = LinearLayoutManager(this)
        binding.files.adapter = FileAdapter(files, focus)
    }

    override fun onStart() {
        super.onStart()
        FetchForeground.enter()
    }

    override fun onStop() {
        FetchForeground.leave()
        super.onStop()
    }

    private class FileAdapter(
        private val files: List<File>,
        private val focus: String
    ) : RecyclerView.Adapter<FileAdapter.Holder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val binding = ItemFolderFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return Holder(binding)
        }

        override fun getItemCount(): Int = files.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.bind(files[position], focus)
        }

        class Holder(private val binding: ItemFolderFileBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(file: File, focus: String) {
                val context = binding.root.context
                val focused = file.name == focus
                binding.fileName.text = file.name
                binding.fileName.setTextColor(context.getColor(if (focused) R.color.copper else R.color.paper))
                val kind = if (file.isDirectory) "Folder" else file.length().formatSize()
                binding.fileSize.text = kind
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
        }
    }

    companion object {
        const val EXTRA_DIR = "dir"
        const val EXTRA_FOCUS = "focus"
    }
}
