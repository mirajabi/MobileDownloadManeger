package com.miaadrajabi.downloader

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the startForegroundService paths that used to return without startForeground:
 * cold enqueue, null-intent recovery, and pause/stop/schedule with no active download.
 */
class ForegroundServicePolicyTest {

    @Test
    fun nullIntentWithNoWorkStopsTheIdleService() {
        assertEquals(
            ForegroundContinuation.StopIdle,
            foregroundContinuation(
                action = null,
                commandAccepted = false,
                hasRunningDownloads = false
            )
        )
    }

    @Test
    fun nullIntentKeepsServiceWhenADownloadIsStillRunning() {
        assertEquals(
            ForegroundContinuation.KeepRunning,
            foregroundContinuation(
                action = null,
                commandAccepted = false,
                hasRunningDownloads = true
            )
        )
    }

    @Test
    fun fastEnqueueThatAlreadyFinishedDoesNotEraseTheResultNotification() {
        assertEquals(
            ForegroundContinuation.AlreadyStopping,
            foregroundContinuation(
                action = DownloadForegroundService.ACTION_ENQUEUE,
                commandAccepted = true,
                hasRunningDownloads = false
            )
        )
    }

    @Test
    fun enqueueStaysUpWithoutWaitingForProgress() {
        assertEquals(
            ForegroundContinuation.KeepRunning,
            foregroundContinuation(
                action = DownloadForegroundService.ACTION_ENQUEUE,
                commandAccepted = true,
                hasRunningDownloads = true
            )
        )
    }

    @Test
    fun enqueueOfAnEmptyCommandStopsWhenNothingIsRunning() {
        assertEquals(
            ForegroundContinuation.StopIdle,
            foregroundContinuation(
                action = DownloadForegroundService.ACTION_ENQUEUE,
                commandAccepted = false,
                hasRunningDownloads = false
            )
        )
    }

    @Test
    fun pauseStopAndResumeWithoutWorkDoNotWaitForProgress() {
        listOf(
            DownloadForegroundService.ACTION_PAUSE,
            DownloadForegroundService.ACTION_RESUME,
            DownloadForegroundService.ACTION_STOP
        ).forEach { action ->
            assertEquals(
                action,
                ForegroundContinuation.StopIdle,
                foregroundContinuation(
                    action = action,
                    commandAccepted = false,
                    hasRunningDownloads = false
                )
            )
        }
    }

    @Test
    fun pauseOrResumeOfRealWorkKeepsTheService() {
        assertEquals(
            ForegroundContinuation.KeepRunning,
            foregroundContinuation(
                action = DownloadForegroundService.ACTION_PAUSE,
                commandAccepted = true,
                hasRunningDownloads = true
            )
        )
        assertEquals(
            ForegroundContinuation.KeepRunning,
            foregroundContinuation(
                action = DownloadForegroundService.ACTION_RESUME,
                commandAccepted = true,
                hasRunningDownloads = true
            )
        )
    }

    @Test
    fun rejectedPauseDoesNotStopAnotherActiveDownload() {
        assertEquals(
            ForegroundContinuation.KeepRunning,
            foregroundContinuation(
                action = DownloadForegroundService.ACTION_PAUSE,
                commandAccepted = false,
                hasRunningDownloads = true
            )
        )
    }

    @Test
    fun scheduleWithoutAnActiveDownloadStopsAfterTheJobIsRegistered() {
        assertEquals(
            ForegroundContinuation.StopIdle,
            foregroundContinuation(
                action = DownloadForegroundService.ACTION_SCHEDULE,
                commandAccepted = true,
                hasRunningDownloads = false
            )
        )
    }

    @Test
    fun scheduleDuringAnActiveDownloadKeepsTheService() {
        assertEquals(
            ForegroundContinuation.KeepRunning,
            foregroundContinuation(
                action = DownloadForegroundService.ACTION_SCHEDULE,
                commandAccepted = true,
                hasRunningDownloads = true
            )
        )
    }

    @Test
    fun notificationUpdateNeverDropsTheForegroundService() {
        assertEquals(
            ForegroundContinuation.KeepRunning,
            foregroundContinuation(
                action = DownloadForegroundService.ACTION_UPDATE_NOTIFICATION,
                commandAccepted = false,
                hasRunningDownloads = false
            )
        )
    }

    @Test
    fun stopOfTheLastPausedDownloadDoesNotEraseTheTerminalNotification() {
        assertEquals(
            ForegroundContinuation.AlreadyStopping,
            foregroundContinuation(
                action = DownloadForegroundService.ACTION_STOP,
                commandAccepted = true,
                hasRunningDownloads = false
            )
        )
    }

    @Test
    fun stopOfAnActiveDownloadStaysUntilTheJobFinishes() {
        assertEquals(
            ForegroundContinuation.KeepRunning,
            foregroundContinuation(
                action = DownloadForegroundService.ACTION_STOP,
                commandAccepted = true,
                hasRunningDownloads = true
            )
        )
    }
}
