package com.miaadrajabi.span

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JvmSpanDownloaderTest {
    @Test
    fun concurrentCancellationRunsEveryListenerExactlyOnce() {
        val cancel = SpanCancel()
        val count = java.util.concurrent.atomic.AtomicInteger()
        val gate = java.util.concurrent.CountDownLatch(1)
        val pool = java.util.concurrent.Executors.newFixedThreadPool(12)
        try {
            repeat(80) { pool.submit { gate.await(); cancel.listen { count.incrementAndGet() } } }
            pool.submit { gate.await(); cancel.cancel() }
            gate.countDown(); pool.shutdown()
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
            cancel.cancel(); cancel.cancel()
            assertEquals(80, count.get())
        } finally { pool.shutdownNow() }
    }

    @Test
    fun pauseDrainsParallelWritersAndResumesTheExactFile() {
        val payload = ByteArray(1024 * 1024) { (it % 251).toByte() }
        val ranges = CopyOnWriteArrayList<String>()
        val server = rangedServer(payload, ranges)
        val dir = tempDir()
        try {
            val request = SpanRequest(server.url("/file").toString(), "pause.bin", connections = 8, minPartBytes = 65536, speedLimitBytesPerSec = 8192)
            val cancel = SpanCancel()
            val downloader = OkHttpSpanDownloader()
            val paused = kotlinx.coroutines.runBlocking {
                downloader.download(request, dir.absolutePath, cancel) {
                    if (it.reservedBytes > 0) cancel.cancel()
                }
            }
            assertEquals(SpanOutcome.Cancelled, paused)
            assertTrue(File(dir, "pause.bin.span-plan").isFile)
            val resumed = kotlinx.coroutines.runBlocking {
                downloader.download(request.copy(speedLimitBytesPerSec = 0), dir.absolutePath)
            }
            assertTrue(resumed is SpanOutcome.Saved, resumed.toString())
            assertTrue(File(dir, "pause.bin").readBytes().contentEquals(payload))
            assertFalse(File(dir, "pause.bin.span-plan").exists())
        } finally { server.shutdown(); dir.deleteRecursively() }
    }

    @Test
    fun pauseWithOneConnectionKeepsItsResumeValidator() {
        val payload = ByteArray(128 * 1024) { (it % 251).toByte() }
        val server = rangedServer(payload, CopyOnWriteArrayList())
        val dir = tempDir()
        try {
            val request = SpanRequest(server.url("/file").toString(), "single.bin", connections = 1)
            val cancel = SpanCancel()
            val paused = kotlinx.coroutines.runBlocking {
                OkHttpSpanDownloader().download(request, dir.absolutePath, cancel) { if (it.reservedBytes > 0) cancel.cancel() }
            }
            assertEquals(SpanOutcome.Cancelled, paused)
            assertTrue(File(dir, "single.bin.span").isFile)
            val resumed = kotlinx.coroutines.runBlocking { OkHttpSpanDownloader().download(request, dir.absolutePath) }
            assertTrue(resumed is SpanOutcome.Saved, resumed.toString())
            assertTrue(File(dir, "single.bin").readBytes().contentEquals(payload))
        } finally { server.shutdown(); dir.deleteRecursively() }
    }

    @Test
    fun redirectLoopEnds() {
        val server = server { _ ->
            MockResponse().setResponseCode(302).setHeader("Location", "/again")
        }
        val dir = tempDir()
        val outcome = download(server, dir, SpanRequest(server.url("/again").toString(), "note.txt"))
        assertTrue(outcome is SpanOutcome.Failed, outcome.toString())
        assertEquals("Redirect loop", outcome.message)
        assertTrue(!File(dir, "note.txt").exists())
        server.shutdown()
    }

    @Test
    fun clearingHistoryLeavesTheFile() {
        val dir = tempDir()
        val saved = File(dir, "movie.bin")
        saved.writeText("keep-me")
        val history = FetchHistory(File(dir, "history.txt"))
        history.add("movie.bin", saved.absolutePath)
        history.clear()
        assertTrue(history.entries().isEmpty())
        assertEquals("keep-me", saved.readText())
    }

    @Test
    fun retryAfterIsReportedOnce() {
        val server = server { _ ->
            MockResponse().setResponseCode(429).setHeader("Retry-After", "2")
        }
        val dir = tempDir()
        val outcome = download(server, dir, SpanRequest(server.url("/a").toString(), "note.txt"))
        assertTrue(outcome is SpanOutcome.Failed, outcome.toString())
        assertEquals(2_000L, outcome.retryAfterMs)
        assertEquals("The server asked to wait.", outcome.message)
        server.shutdown()
    }

    @Test
    fun aSlowReplyCutsOnlyThisDownloadsLimit() {
        val server = server { _ ->
            MockResponse().setHeadersDelay(1, TimeUnit.SECONDS).setBody("hello-span")
        }
        val dir = tempDir()
        val client = OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build()
        val downloader = OkHttpSpanDownloader(client)
        val outcome = kotlinx.coroutines.runBlocking {
            downloader.download(
                SpanRequest(
                    server.url("/a").toString(),
                    "note.txt",
                    speedLimitBytesPerSec = 100_000L,
                    latencyAware = true,
                ),
                dir.absolutePath,
            )
        }
        assertTrue(outcome is SpanOutcome.Saved, outcome.toString())
        assertEquals(50_000L, downloader.appliedSpeedLimit)
        server.shutdown()
    }

    @Test
    fun fullGetWritesTheBody() {
        val server = server { request ->
            assertTrue(request.getHeader("Range") == null)
            MockResponse().setBody("hello-span")
        }
        val dir = tempDir()
        val outcome = download(server, dir, SpanRequest(server.url("/a").toString(), "note.txt"))
        assertTrue(outcome is SpanOutcome.Saved)
        assertEquals("hello-span", File(dir, "note.txt").readText())
        server.shutdown()
    }

    @Test
    fun partialIsAppendedOn206() {
        val server = server { request ->
            assertEquals("bytes=5-", request.getHeader("Range"))
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes 5-10/11")
                .setHeader("ETag", "W/\"weak\"")
                .setBody("-span")
        }
        val dir = tempDir()
        File(dir, "note.txt").writeText("hello")
        val outcome = download(server, dir, SpanRequest(server.url("/a").toString(), "note.txt"))
        assertTrue(outcome is SpanOutcome.Saved, outcome.toString())
        assertEquals("hello-span", File(dir, "note.txt").readText())
        server.shutdown()
    }

    @Test
    fun fullBodyDoesNotLandOnAPartial() {
        var calls = 0
        val server = server { _ ->
            calls += 1
            MockResponse().setBody(if (calls == 1) "IGNORE" else "fresh")
        }
        val dir = tempDir()
        File(dir, "note.txt").writeText("old")
        val outcome = download(server, dir, SpanRequest(server.url("/a").toString(), "note.txt"))
        assertTrue(outcome is SpanOutcome.Saved, outcome.toString())
        assertEquals("fresh", File(dir, "note.txt").readText())
        assertEquals(2, calls)
        server.shutdown()
    }

    @Test
    fun ignoredWindowIsNotSaved() {
        val server = server { _ -> MockResponse().setBody("entire-file") }
        val dir = tempDir()
        val outcome = download(
            server,
            dir,
            SpanRequest(server.url("/a").toString(), "slice.bin", rangeStart = 2, rangeEndInclusive = 4),
        )
        assertTrue(outcome is SpanOutcome.Failed)
        assertTrue(!File(dir, "slice.bin").exists())
        server.shutdown()
    }

    @Test
    fun matching416IsComplete() {
        val server = server { _ -> MockResponse().setResponseCode(416) }
        val dir = tempDir()
        File(dir, "slice.bin").writeBytes(byteArrayOf(1, 2, 3))
        val outcome = download(
            server,
            dir,
            SpanRequest(server.url("/a").toString(), "slice.bin", rangeStart = 0, rangeEndInclusive = 2),
        )
        assertTrue(outcome is SpanOutcome.Saved)
        assertEquals(3, outcome.bytes)
        server.shutdown()
    }

    @Test
    fun partsLandInPlaceAndReplaceThePreviousFile() {
        val payload = ByteArray(256 * 1024) { index -> (index % 251).toByte() }
        val ranges = CopyOnWriteArrayList<String>()
        val server = rangedServer(payload, ranges)
        val dir = tempDir()
        File(dir, "movie.bin").writeText("keep-until-publish")
        val outcome = download(
            server,
            dir,
            SpanRequest(server.url("/file").toString(), "movie.bin", connections = 4, minPartBytes = 1024),
        )
        assertTrue(outcome is SpanOutcome.Saved, outcome.toString())
        assertTrue(File(dir, "movie.bin").readBytes().contentEquals(payload))
        assertTrue(!File(dir, "movie.bin.span-data").exists())
        assertTrue(dir.list().orEmpty().none { it.contains(".span-part-") })
        assertTrue(ranges.none { it == "bytes=0-" })
        server.shutdown()
    }

    @Test
    fun resumeIgnoresBytesWrittenAfterThePlan() {
        val payload = ByteArray(256 * 1024) { index -> (index % 251).toByte() }
        val ranges = CopyOnWriteArrayList<String>()
        val server = rangedServer(payload, ranges)
        val dir = tempDir()
        val data = File(dir, "movie.bin.span-data")
        java.io.RandomAccessFile(data, "rw").use { out ->
            out.write(payload, 0, 200)
        }
        File(dir, "movie.bin.span-plan").writeText("v2\n262144\n4\n\n100\n0\n0\n0\n")
        val outcome = download(
            server,
            dir,
            SpanRequest(server.url("/file").toString(), "movie.bin", connections = 4, minPartBytes = 1024),
        )
        assertTrue(outcome is SpanOutcome.Saved, outcome.toString())
        assertTrue(File(dir, "movie.bin").readBytes().contentEquals(payload))
        assertTrue(ranges.contains("bytes=100-65535"), ranges.toString())
        assertTrue(ranges.none { it.startsWith("bytes=200-") })
        server.shutdown()
    }

    @Test
    fun reservedBytesStayAheadOfTheCheckpoint() {
        val payload = ByteArray(256 * 1024) { index -> (index % 251).toByte() }
        val server = rangedServer(payload, CopyOnWriteArrayList())
        val dir = tempDir()
        val client = OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build()
        val sawGap = java.util.concurrent.atomic.AtomicBoolean(false)
        val outcome = kotlinx.coroutines.runBlocking {
            OkHttpSpanDownloader(client).download(
                SpanRequest(server.url("/file").toString(), "movie.bin", connections = 4, minPartBytes = 1024),
                dir.absolutePath,
            ) { progress ->
                if (progress.reservedBytes > progress.bytes) sawGap.set(true)
            }
        }
        assertTrue(outcome is SpanOutcome.Saved, outcome.toString())
        assertTrue(sawGap.get())
        server.shutdown()
    }

    @Test
    fun failureLogDropsTheTokenAndRotates() {
        val dir = tempDir()
        val logDir = tempDir()
        val server = server { _ -> MockResponse().setResponseCode(500) }
        val client = OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build()
        val log = SpanFileLog(logDir, maxBytes = 400)
        val outcome = kotlinx.coroutines.runBlocking {
            OkHttpSpanDownloader(client, log = log).download(
                SpanRequest(server.url("/a").toString() + "?token=super-secret", "note.txt"),
                dir.absolutePath,
            )
        }
        assertTrue(outcome is SpanOutcome.Failed, outcome.toString())
        log.write("x".repeat(400))
        val combined = logDir.listFiles().orEmpty().joinToString("\n") { it.readText() }
        assertFalse(combined.contains("super-secret"), combined)
        assertTrue(combined.contains("token=[redacted]"))
        assertTrue(File(logDir, "fetch.log.1").exists())
        server.shutdown()
    }

    @Test
    fun resumeStartsAtTheSavedPartOffset() {
        val payload = ByteArray(256 * 1024) { index -> (index % 251).toByte() }
        val ranges = CopyOnWriteArrayList<String>()
        val server = rangedServer(payload, ranges)
        val dir = tempDir()
        val data = File(dir, "movie.bin.span-data")
        java.io.RandomAccessFile(data, "rw").use { out ->
            out.write(payload, 0, 100)
        }
        File(dir, "movie.bin.span-plan").writeText("v2\n262144\n4\n\n100\n0\n0\n0\n")
        val outcome = download(
            server,
            dir,
            SpanRequest(server.url("/file").toString(), "movie.bin", connections = 4, minPartBytes = 1024),
        )
        assertTrue(outcome is SpanOutcome.Saved, outcome.toString())
        assertTrue(File(dir, "movie.bin").readBytes().contentEquals(payload))
        assertTrue(ranges.contains("bytes=100-65535"), ranges.toString())
        assertTrue(ranges.none { it == "bytes=0-65535" })
        server.shutdown()
    }

    @Test
    fun verifyStartsOnlyAfterTheBytesArrive() {
        val payload = "span-stage".toByteArray()
        val server = server { _ -> MockResponse().setBody(Buffer().write(payload)) }
        val dir = tempDir()
        val phases = CopyOnWriteArrayList<TransferPhase>()
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }
        val outcome = download(
            server,
            dir,
            SpanRequest(server.url("/a").toString(), "note.txt", expectedChecksum = digest),
        ) { progress -> phases += progress.phase }
        assertTrue(outcome is SpanOutcome.Saved, outcome.toString())
        val verifyAt = phases.indexOf(TransferPhase.Verify)
        val readyAt = phases.indexOf(TransferPhase.Ready)
        assertTrue(verifyAt >= 0, phases.toString())
        assertTrue(readyAt > verifyAt, phases.toString())
        server.shutdown()
    }

    @Test
    fun aFileWithoutAChecksumIsNotMarkedVerifying() {
        val server = server { _ -> MockResponse().setBody("plain") }
        val dir = tempDir()
        val phases = CopyOnWriteArrayList<TransferPhase>()
        val outcome = download(
            server,
            dir,
            SpanRequest(server.url("/a").toString(), "note.txt"),
        ) { progress -> phases += progress.phase }
        assertTrue(outcome is SpanOutcome.Saved, outcome.toString())
        assertTrue(phases.contains(TransferPhase.Finalize), phases.toString())
        assertTrue(phases.none { it == TransferPhase.Verify }, phases.toString())
        server.shutdown()
    }

    @Test
    fun badChecksumKeepsThePreviousFile() {
        val payload = ByteArray(256 * 1024) { index -> (index % 251).toByte() }
        val server = rangedServer(payload, CopyOnWriteArrayList())
        val dir = tempDir()
        File(dir, "movie.bin").writeText("keep-me")
        val wrong = "ab".repeat(32)
        val outcome = download(
            server,
            dir,
            SpanRequest(
                server.url("/file").toString(),
                "movie.bin",
                expectedChecksum = wrong,
                connections = 4,
                minPartBytes = 1024,
            ),
        )
        assertTrue(outcome is SpanOutcome.Failed, outcome.toString())
        assertEquals("keep-me", File(dir, "movie.bin").readText())
        assertTrue(!File(dir, "movie.bin.span-data").exists())
        server.shutdown()
    }

    @Test
    fun diskFullBeforePartsKeepsThePreviousFile() {
        val payload = ByteArray(256 * 1024) { index -> (index % 251).toByte() }
        val server = rangedServer(payload, CopyOnWriteArrayList())
        val dir = tempDir()
        File(dir, "movie.bin").writeText("keep-me")
        val outcome = download(
            server,
            dir,
            SpanRequest(server.url("/file").toString(), "movie.bin", connections = 4, minPartBytes = 1024),
            freeSpace = { 0L },
        )
        assertTrue(outcome is SpanOutcome.Failed, outcome.toString())
        assertEquals("Not enough free space.", outcome.message)
        assertEquals("keep-me", File(dir, "movie.bin").readText())
        assertTrue(!File(dir, "movie.bin.span-data").exists())
        server.shutdown()
    }

    @Test
    fun unknownSizeRefusesWhenTheDiskIsFull() {
        val server = server { _ -> MockResponse().setBody("hello-span") }
        val dir = tempDir()
        val outcome = download(
            server,
            dir,
            SpanRequest(server.url("/a").toString(), "note.txt"),
            freeSpace = { 0L },
        )
        assertTrue(outcome is SpanOutcome.Failed, outcome.toString())
        assertEquals("Not enough free space.", outcome.message)
        assertTrue(!File(dir, "note.txt").exists())
        server.shutdown()
    }

    @Test
    fun aDifferentDiskDoesNotReplaceTheFile() {
        val payload = ByteArray(256 * 1024) { index -> (index % 251).toByte() }
        val server = rangedServer(payload, CopyOnWriteArrayList())
        val dir = tempDir()
        File(dir, "movie.bin").writeText("keep-me")
        val seen = java.util.concurrent.atomic.AtomicInteger()
        val outcome = download(
            server,
            dir,
            SpanRequest(server.url("/file").toString(), "movie.bin", connections = 4, minPartBytes = 1024),
            disk = {
                if (seen.incrementAndGet() == 1) DiskStamp("stick-a", DiskKind.Removable) else DiskStamp("stick-b", DiskKind.Removable)
            },
        )
        assertTrue(outcome is SpanOutcome.Failed, outcome.toString())
        assertEquals("The destination disk changed.", outcome.message)
        assertEquals("keep-me", File(dir, "movie.bin").readText())
        server.shutdown()
    }

    @Test
    fun aMissingDiskStopsBeforeWriting() {
        val server = server { _ -> MockResponse().setBody("hello-span") }
        val dir = tempDir()
        val outcome = download(
            server,
            dir,
            SpanRequest(server.url("/a").toString(), "note.txt"),
            disk = { throw IllegalStateException("ejected") },
        )
        assertTrue(outcome is SpanOutcome.Failed, outcome.toString())
        assertEquals("The destination disconnected.", outcome.message)
        assertTrue(!File(dir, "note.txt").exists())
        server.shutdown()
    }

    @Test
    fun aZipPathThatLeavesTheFolderIsNotExtracted() {
        val dir = tempDir()
        val zip = File(dir, "bundle.zip")
        java.util.zip.ZipOutputStream(zip.outputStream()).use { out ->
            out.putNextEntry(java.util.zip.ZipEntry("docs/a.txt"))
            out.write("inside".toByteArray())
            out.closeEntry()
            out.putNextEntry(java.util.zip.ZipEntry("../escape.txt"))
            out.write("outside".toByteArray())
            out.closeEntry()
        }
        assertEquals(listOf("docs/a.txt"), ZipUnpack.names(zip))
        assertEquals(1, ZipUnpack.unpack(zip, ZipUnpack.names(zip) + "../escape.txt"))
        assertEquals("inside", File(dir, "bundle/docs/a.txt").readText())
        assertTrue(!File(dir, "escape.txt").exists())
    }

    private fun rangedServer(payload: ByteArray, ranges: CopyOnWriteArrayList<String>): MockWebServer {
        return server { request ->
            val header = request.getHeader("Range") ?: return@server MockResponse().setResponseCode(200).setBody(Buffer().write(payload))
            ranges += header
            if (header == "bytes=0-0") {
                return@server MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Range", "bytes 0-0/${payload.size}")
                    .setBody(Buffer().write(byteArrayOf(payload[0])))
            }
            if (header.isEmpty()) return@server MockResponse().setBody(Buffer().write(payload))
            val spec = header.removePrefix("bytes=")
            val start = spec.substringBefore('-').toInt()
            val end = spec.substringAfter('-').toIntOrNull() ?: payload.lastIndex
            val slice = payload.copyOfRange(start, end + 1)
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes $start-$end/${payload.size}")
                .setBody(Buffer().write(slice))
        }
    }

    private fun download(
        server: MockWebServer,
        dir: File,
        request: SpanRequest,
        freeSpace: (File) -> Long = { it.usableSpace },
        disk: (File) -> DiskStamp = { JvmDisk.stamp(it) },
        onProgress: (SpanProgress) -> Unit = {},
    ): SpanOutcome {
        val client = OkHttpClient.Builder()
            .callTimeout(5, TimeUnit.SECONDS)
            .build()
        return kotlinx.coroutines.runBlocking {
            OkHttpSpanDownloader(client, freeSpace, disk = disk).download(request, dir.absolutePath, onProgress = onProgress)
        }
    }

    private fun server(handler: (RecordedRequest) -> MockResponse): MockWebServer {
        val mock = MockWebServer()
        mock.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = handler(request)
        }
        mock.start()
        return mock
    }

    private fun tempDir(): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "span-test-" + System.nanoTime())
        dir.mkdirs()
        return dir
    }
}
