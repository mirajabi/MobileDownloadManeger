package com.miaadrajabi.downloader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DownloadInterruptTest {

    @Test
    fun pausedDownloadIsNotRetried() {
        assertEquals(
            DownloadInterrupt.KeepPaused,
            classifyDownloadInterrupt(
                coroutineActive = true,
                isPaused = true,
                sessionTracked = true
            )
        )
    }

    @Test
    fun stoppedDownloadIsNotRetriedEvenIfTheCoroutineHasNotEnded() {
        assertEquals(
            DownloadInterrupt.StoppedByUser,
            classifyDownloadInterrupt(
                coroutineActive = true,
                isPaused = false,
                sessionTracked = false
            )
        )
    }

    @Test
    fun cancelledCoroutineIsNotRetried() {
        assertEquals(
            DownloadInterrupt.StoppedByUser,
            classifyDownloadInterrupt(
                coroutineActive = false,
                isPaused = false,
                sessionTracked = true
            )
        )
    }

    @Test
    fun pauseWinsOverAMissingSession() {
        assertEquals(
            DownloadInterrupt.KeepPaused,
            classifyDownloadInterrupt(
                coroutineActive = false,
                isPaused = true,
                sessionTracked = false
            )
        )
    }

    @Test
    fun liveNetworkFailureStillRetries() {
        assertEquals(
            DownloadInterrupt.ContinueRetry,
            classifyDownloadInterrupt(
                coroutineActive = true,
                isPaused = false,
                sessionTracked = true
            )
        )
    }

    @Test
    fun ordinaryFailuresAreNotForegroundStartBlocks() {
        assertFalse(isForegroundStartBlocked(IllegalStateException("network")))
        assertFalse(isForegroundStartBlocked(IllegalStateException("blocked", IllegalStateException("io"))))
    }
}
