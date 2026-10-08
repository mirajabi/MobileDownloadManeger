package com.miaadrajabi.fetch

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.miaadrajabi.downloader.DownloadHandle
import com.miaadrajabi.downloader.DownloadListener
import com.miaadrajabi.downloader.DownloadProgress
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The queue the screen draws. The library does not publish a list of jobs,
 * so Fetch remembers the ones this app started and updates them from the listener.
 */
class TransferStore(context: Context) {

    private val appContext = context.applicationContext ?: context
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())
    private val watchers = CopyOnWriteArrayList<(List<Transfer>) -> Unit>()
    private val lock = Any()
    private var items: MutableList<Transfer> = read()

    val bridge: DownloadListener = object : DownloadListener {
        override fun onQueued(handle: DownloadHandle) {
            touch(handle, STATUS_QUEUED, "Waiting to start")
        }

        override fun onStarted(handle: DownloadHandle) {
            touch(handle, STATUS_RUNNING, "Connected")
        }

        override fun onProgress(handle: DownloadHandle, progress: DownloadProgress) {
            val percent = progress.percent ?: -1
            val message = etaLabel(progress)
            updateActive(handle.id) {
                it.copy(
                    status = if (it.status == STATUS_RETRYING) STATUS_RETRYING else STATUS_RUNNING,
                    bytes = progress.bytesDownloaded,
                    total = progress.totalBytes ?: it.total,
                    speed = progress.bytesPerSecond ?: 0L,
                    percent = percent,
                    message = message
                )
            }
        }

        override fun onPaused(handle: DownloadHandle) {
            touch(handle, STATUS_PAUSED, "Paused")
        }

        override fun onResumed(handle: DownloadHandle) {
            touch(handle, STATUS_RUNNING, "Resuming")
        }

        override fun onCompleted(handle: DownloadHandle) {
            val known = snapshot().firstOrNull { it.id == handle.id }
            val bytes = when {
                known == null -> 0L
                known.total > 0L -> known.total
                else -> known.bytes
            }
            Haul.record(appContext, handle.id, bytes)
            updateActive(handle.id, allowTerminal = true) {
                it.copy(status = STATUS_DONE, percent = 100, speed = 0L, message = "Saved")
            }
        }

        override fun onFailed(handle: DownloadHandle, error: Throwable?) {
            val reason = error?.message?.take(160) ?: "The download failed"
            updateActive(handle.id, allowTerminal = true) {
                it.copy(status = STATUS_FAILED, speed = 0L, message = reason)
            }
        }

        override fun onRetry(handle: DownloadHandle, attempt: Int) {
            touch(handle, STATUS_RETRYING, "Trying again, attempt $attempt")
        }

        override fun onCancelled(handle: DownloadHandle) {
            updateActive(handle.id, allowTerminal = true) {
                it.copy(status = STATUS_STOPPED, speed = 0L, message = "Stopped")
            }
        }
    }

    fun watch(watcher: (List<Transfer>) -> Unit) {
        watchers.add(watcher)
        watcher(snapshot())
    }

    fun unwatch(watcher: (List<Transfer>) -> Unit) {
        watchers.remove(watcher)
    }

    fun insert(item: Transfer) {
        synchronized(lock) {
            items.removeAll { it.id == item.id }
            items.add(0, item)
            persistLocked()
        }
        publish()
    }

    fun remove(id: String) {
        synchronized(lock) {
            items.removeAll { it.id == id }
            persistLocked()
        }
        publish()
    }

    fun mark(id: String, status: String, message: String) {
        updateActive(id, allowTerminal = true) {
            it.copy(status = status, message = message, speed = 0L)
        }
    }

    fun save(item: Transfer) {
        updateActive(item.id, allowTerminal = true) { item }
    }

    fun locateFiles(context: Context) {
        synchronized(lock) {
            var changed = false
            for (index in items.indices) {
                val item = items[index]
                if (item.localPath.isNotBlank() && java.io.File(item.localPath).isFile) continue
                val found = SavedFiles.find(context, item.fileName) ?: continue
                items[index] = item.copy(localPath = found.absolutePath)
                changed = true
            }
            if (changed) persistLocked()
        }
    }

    fun refresh() {
        publish()
    }

    fun snapshot(): List<Transfer> {
        synchronized(lock) {
            return ArrayList(items)
        }
    }

    private fun touch(handle: DownloadHandle, status: String, message: String) {
        synchronized(lock) {
            val index = items.indexOfFirst { it.id == handle.id }
            if (index < 0) {
                items.add(
                    0,
                    Transfer(
                        id = handle.id,
                        url = handle.source,
                        fileName = LinkParser.fileNameFrom(handle.source),
                        status = status,
                        message = message
                    )
                )
            } else {
                val current = items[index]
                items[index] = current.copy(status = status, message = message)
            }
            persistLocked()
        }
        publish()
    }

    private fun updateActive(id: String, allowTerminal: Boolean = false, block: (Transfer) -> Transfer) {
        synchronized(lock) {
            val index = items.indexOfFirst { it.id == id }
            if (index < 0) return
            val current = items[index]
            val terminal = current.status == STATUS_DONE ||
                current.status == STATUS_FAILED ||
                current.status == STATUS_STOPPED
            if (terminal && !allowTerminal) return
            items[index] = block(current)
            persistLocked()
        }
        publish()
    }

    private fun publish() {
        val copy = snapshot()
        main.post {
            for (watcher in watchers) {
                watcher(copy)
            }
            FetchWidget.push(appContext, copy)
        }
    }

    private fun persistLocked() {
        val array = JSONArray()
        for (item in items) {
            array.put(item.toJson())
        }
        prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    private fun read(): MutableList<Transfer> {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return ArrayList()
        val array = try {
            JSONArray(raw)
        } catch (error: Exception) {
            return ArrayList()
        }
        val loaded = ArrayList<Transfer>(array.length())
        for (index in 0 until array.length()) {
            val objectAt = array.optJSONObject(index) ?: continue
            loaded.add(objectAt.toTransfer())
        }
        return loaded
    }

    private fun etaLabel(progress: DownloadProgress): String {
        val remaining = progress.remainingBytes
        val speed = progress.bytesPerSecond
        if (remaining == null || remaining <= 0L) return "Downloading"
        if (speed == null || speed <= 0L) return "Downloading"
        val seconds = remaining / speed
        return when {
            seconds < 60L -> "About a minute left"
            seconds < 3600L -> "About ${seconds / 60L} min left"
            else -> "About ${seconds / 3600L} hr left"
        }
    }

    private fun Transfer.toJson(): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("url", url)
            .put("fileName", fileName)
            .put("status", status)
            .put("bytes", bytes)
            .put("total", total)
            .put("speed", speed)
            .put("percent", percent)
            .put("message", message)
            .put("whenLabel", whenLabel)
            .put("title", title)
            .put("localPath", localPath)
            .put("locationUri", locationUri)
    }

    private fun JSONObject.toTransfer(): Transfer {
        return Transfer(
            id = optString("id"),
            url = optString("url"),
            fileName = optString("fileName"),
            status = optString("status", STATUS_QUEUED),
            bytes = optLong("bytes"),
            total = optLong("total"),
            speed = optLong("speed"),
            percent = optInt("percent", -1),
            message = optString("message"),
            whenLabel = optString("whenLabel"),
            title = optString("title"),
            localPath = optString("localPath"),
            locationUri = optString("locationUri")
        )
    }

    companion object {
        const val STATUS_QUEUED = "queued"
        const val STATUS_RUNNING = "running"
        const val STATUS_PAUSED = "paused"
        const val STATUS_SCHEDULED = "scheduled"
        const val STATUS_DONE = "done"
        const val STATUS_FAILED = "failed"
        const val STATUS_STOPPED = "stopped"
        const val STATUS_RETRYING = "retrying"

        private const val PREFS = "fetch_queue"
        private const val KEY_ITEMS = "items"
    }
}
