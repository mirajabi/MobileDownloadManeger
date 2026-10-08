package com.miaadrajabi.downloader

/**
 * Distinguishes a real network failure from pause or stop.
 * OkHttp reports call cancellation as [java.io.IOException], so the retry loop must not
 * treat that as a transport error.
 */
internal enum class DownloadInterrupt {
    ContinueRetry,
    KeepPaused,
    StoppedByUser
}

internal fun classifyDownloadInterrupt(
    coroutineActive: Boolean,
    isPaused: Boolean,
    sessionTracked: Boolean
): DownloadInterrupt {
    if (isPaused) return DownloadInterrupt.KeepPaused
    if (!coroutineActive || !sessionTracked) return DownloadInterrupt.StoppedByUser
    return DownloadInterrupt.ContinueRetry
}

internal fun isForegroundStartBlocked(error: Throwable): Boolean {
    var current: Throwable? = error
    while (current != null) {
        if (current.javaClass.name == "android.app.ForegroundServiceStartNotAllowedException") {
            return true
        }
        current = current.cause
    }
    return false
}
