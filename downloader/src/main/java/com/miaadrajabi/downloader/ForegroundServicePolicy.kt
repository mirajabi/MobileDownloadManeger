package com.miaadrajabi.downloader

/**
 * Decides whether a started foreground service still has work to keep alive.
 * Enqueue/pause/resume/stop/schedule must not stay up only to wait for progress.
 */
internal enum class ForegroundContinuation {
    KeepRunning,
    AlreadyStopping,
    StopIdle
}

internal fun foregroundContinuation(
    action: String?,
    commandAccepted: Boolean,
    hasRunningDownloads: Boolean
): ForegroundContinuation {
    // The download thread can finish before onStartCommand returns. Do not mark the
    // service sticky again, and do not remove the completion notification.
    if ((action == DownloadForegroundService.ACTION_STOP ||
            action == DownloadForegroundService.ACTION_ENQUEUE) &&
        commandAccepted &&
        !hasRunningDownloads
    ) {
        return ForegroundContinuation.AlreadyStopping
    }
    val keep = when (action) {
        DownloadForegroundService.ACTION_UPDATE_NOTIFICATION -> true
        DownloadForegroundService.ACTION_SCHEDULE -> hasRunningDownloads
        DownloadForegroundService.ACTION_ENQUEUE,
        DownloadForegroundService.ACTION_PAUSE,
        DownloadForegroundService.ACTION_RESUME,
        DownloadForegroundService.ACTION_STOP -> commandAccepted || hasRunningDownloads
        else -> hasRunningDownloads
    }
    return if (keep) ForegroundContinuation.KeepRunning else ForegroundContinuation.StopIdle
}
