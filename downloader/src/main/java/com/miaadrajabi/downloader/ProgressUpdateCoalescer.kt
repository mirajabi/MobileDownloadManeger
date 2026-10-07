package com.miaadrajabi.downloader

/**
 * Keeps the newest progress event and emits it at most once per interval.
 * Terminal notification paths call [clear] so a delayed progress cannot overwrite them.
 */
internal class ProgressUpdateCoalescer(
    private val intervalMillis: Long = PROGRESS_NOTIFY_INTERVAL_MILLIS,
    private val clockMillis: () -> Long,
    private val schedule: (delayMillis: Long, token: Int) -> Unit,
    private val cancelScheduled: () -> Unit,
    private val emit: (DownloadHandle, DownloadProgress) -> Unit
) {
    private val lock = Any()
    private var pending: Pending? = null
    private var lastEmitAtMillis = Long.MIN_VALUE
    private var scheduled = false
    private var generation = 0

    fun submit(handle: DownloadHandle, progress: DownloadProgress) {
        val immediate: Pending?
        var scheduleDelay: Long? = null
        var scheduleToken = 0
        synchronized(lock) {
            pending = Pending(handle, progress)
            val now = clockMillis()
            val due = lastEmitAtMillis == Long.MIN_VALUE ||
                now - lastEmitAtMillis >= intervalMillis
            if (!scheduled && due) {
                immediate = pollPending(now)
            } else {
                immediate = null
                if (!scheduled) {
                    scheduled = true
                    scheduleToken = generation
                    scheduleDelay = (intervalMillis - (now - lastEmitAtMillis)).coerceAtLeast(0L)
                }
            }
        }
        val delay = scheduleDelay
        if (delay != null) {
            schedule(delay, scheduleToken)
        }
        if (immediate != null) {
            emit(immediate.handle, immediate.progress)
        }
    }

    fun onScheduled(token: Int) {
        val item = synchronized(lock) {
            if (token != generation) {
                null
            } else {
                scheduled = false
                pollPending(clockMillis())
            }
        } ?: return
        emit(item.handle, item.progress)
    }

    fun clear() {
        synchronized(lock) {
            generation++
            pending = null
            scheduled = false
        }
        cancelScheduled()
    }

    private fun pollPending(now: Long): Pending? {
        val current = pending ?: return null
        pending = null
        lastEmitAtMillis = now
        return current
    }

    private data class Pending(
        val handle: DownloadHandle,
        val progress: DownloadProgress
    )

    companion object {
        const val PROGRESS_NOTIFY_INTERVAL_MILLIS = 1_000L
    }
}
