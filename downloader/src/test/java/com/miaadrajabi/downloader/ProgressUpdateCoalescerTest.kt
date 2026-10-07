package com.miaadrajabi.downloader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressUpdateCoalescerTest {

    private val handle = DownloadHandle(id = "dl-1", source = "https://example.com/file.bin")

    @Test
    fun intervalIsOneSecond() {
        assertEquals(1_000L, ProgressUpdateCoalescer.PROGRESS_NOTIFY_INTERVAL_MILLIS)
    }

    @Test
    fun firstProgressIsDeliveredAndBurstKeepsLatest() {
        var now = 5_000L
        val scheduled = mutableListOf<Pair<Long, Int>>()
        val emitted = mutableListOf<Long>()
        val coalescer = newCoalescer(
            clock = { now },
            scheduled = scheduled,
            emitted = emitted
        )

        coalescer.submit(handle, progress(10))
        coalescer.submit(handle, progress(20))
        coalescer.submit(handle, progress(40))
        coalescer.submit(handle, progress(90))

        assertEquals(listOf(10L), emitted)
        assertEquals(1, scheduled.size)
        assertEquals(1_000L, scheduled.single().first)

        now += 1_000L
        coalescer.onScheduled(scheduled.single().second)

        assertEquals(listOf(10L, 90L), emitted)
    }

    @Test
    fun progressAfterTheIntervalIsDeliveredImmediately() {
        var now = 0L
        val emitted = mutableListOf<Long>()
        val coalescer = newCoalescer(
            clock = { now },
            scheduled = mutableListOf(),
            emitted = emitted
        )

        coalescer.submit(handle, progress(10))
        now += ProgressUpdateCoalescer.PROGRESS_NOTIFY_INTERVAL_MILLIS
        coalescer.submit(handle, progress(25))

        assertEquals(listOf(10L, 25L), emitted)
    }

    @Test
    fun clearDropsPendingProgressAndIgnoresTheOldToken() {
        var now = 2_000L
        val scheduled = mutableListOf<Pair<Long, Int>>()
        val emitted = mutableListOf<Long>()
        var cancelled = false
        val coalescer = ProgressUpdateCoalescer(
            clockMillis = { now },
            schedule = { delay, token -> scheduled.add(delay to token) },
            cancelScheduled = { cancelled = true },
            emit = { _, progress -> emitted.add(progress.bytesDownloaded) }
        )

        coalescer.submit(handle, progress(10))
        coalescer.submit(handle, progress(50))
        val staleToken = scheduled.single().second
        coalescer.clear()
        coalescer.onScheduled(staleToken)

        assertTrue(cancelled)
        assertEquals(listOf(10L), emitted)
    }

    @Test
    fun newerScheduleSurvivesAStaleCallback() {
        var now = 8_000L
        val scheduled = mutableListOf<Pair<Long, Int>>()
        val emitted = mutableListOf<Long>()
        val coalescer = newCoalescer(
            clock = { now },
            scheduled = scheduled,
            emitted = emitted
        )

        coalescer.submit(handle, progress(10))
        coalescer.submit(handle, progress(15))
        val staleToken = scheduled.single().second
        coalescer.clear()
        scheduled.clear()

        coalescer.submit(handle, progress(70))
        val freshToken = scheduled.single().second
        coalescer.onScheduled(staleToken)
        now += 1_000L
        coalescer.onScheduled(freshToken)

        assertEquals(listOf(10L, 70L), emitted)
    }

    @Test
    fun hundredProgressEventsInOneWindowExposeOnlyTheLatest() {
        var now = 1_000L
        val scheduled = mutableListOf<Pair<Long, Int>>()
        val emitted = mutableListOf<Long>()
        val coalescer = newCoalescer(
            clock = { now },
            scheduled = scheduled,
            emitted = emitted
        )

        for (bytes in 1L..100L) {
            coalescer.submit(handle, progress(bytes))
        }

        assertEquals(listOf(1L), emitted)
        assertEquals(1, scheduled.size)
        now += scheduled.single().first
        coalescer.onScheduled(scheduled.single().second)
        assertEquals(listOf(1L, 100L), emitted)
    }

    private fun newCoalescer(
        clock: () -> Long,
        scheduled: MutableList<Pair<Long, Int>>,
        emitted: MutableList<Long>
    ): ProgressUpdateCoalescer {
        return ProgressUpdateCoalescer(
            clockMillis = clock,
            schedule = { delay, token -> scheduled.add(delay to token) },
            cancelScheduled = { },
            emit = { _, progress -> emitted.add(progress.bytesDownloaded) }
        )
    }

    private fun progress(bytes: Long): DownloadProgress {
        return DownloadProgress(
            bytesDownloaded = bytes,
            totalBytes = 100L,
            percent = bytes.toInt()
        )
    }
}
