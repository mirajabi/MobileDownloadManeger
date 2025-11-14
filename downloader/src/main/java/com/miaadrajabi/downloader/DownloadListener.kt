package com.miaadrajabi.downloader

/**
 * 1. Default listener contract exposed to host applications.
 */
interface DownloadListener {
    /**
     * 2. Invoked when a request is accepted into the queue.
     */
    fun onQueued(handle: DownloadHandle) {}

    /**
     * 3. Invoked when the first byte transfer begins.
     */
    fun onStarted(handle: DownloadHandle) {}

    /**
     * 4. Emits progress snapshots for UI updates.
     */
    fun onProgress(handle: DownloadHandle, progress: DownloadProgress) {}

    /**
     * 4.1 Fired when a running download is paused by the user/system.
     */
    fun onPaused(handle: DownloadHandle) {}

    /**
     * 4.2 Fired when a paused download resumes transferring bytes.
     */
    fun onResumed(handle: DownloadHandle) {}

    /**
     * 5. Signals a successful download along with completion metadata.
     */
    fun onCompleted(handle: DownloadHandle) {}

    /**
     * 6. Signals an unrecoverable failure and the root cause if available.
     */
    fun onFailed(handle: DownloadHandle, error: Throwable?) {}

    /**
     * 7. Notifies consumers that a retry attempt is about to happen.
     */
    fun onRetry(handle: DownloadHandle, attempt: Int) {}

    /**
     * 8. Fired when a queued/download in-progress job is cancelled.
     */
    fun onCancelled(handle: DownloadHandle) {}
}

