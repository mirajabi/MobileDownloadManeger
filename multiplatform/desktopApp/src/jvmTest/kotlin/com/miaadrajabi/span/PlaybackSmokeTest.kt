package com.miaadrajabi.span

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.*

/** Optional native codec smoke test: provide -Dfetch.player.sample=/path/to/a/small.mp4. */
class PlaybackSmokeTest {
    @Test fun nativePlayerStartsPausesAndSeeks() {
        val sample = System.getProperty("fetch.player.sample")?.let(::File)
        assumeTrue("A local MP4 fixture is required for the native codec smoke test", sample?.isFile == true)
        val bytes = sample!!.readBytes()
        MockWebServer().use { server ->
            server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
                override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                    val range = request.getHeader("Range")?.removePrefix("bytes=")
                    val start = range?.substringBefore('-')?.toIntOrNull() ?: 0
                    val end = range?.substringAfter('-')?.toIntOrNull()?.coerceAtMost(bytes.lastIndex) ?: bytes.lastIndex
                    return MockResponse().setResponseCode(if (range != null) 206 else 200).setHeader("Content-Type", "video/mp4")
                        .setHeader("Accept-Ranges", "bytes").apply { if (range != null) setHeader("Content-Range", "bytes $start-$end/${bytes.size}") }
                        .setBody(Buffer().write(bytes.copyOfRange(start, end + 1)))
                }
            }
            StreamGate(WatchStream("Sample", server.url("/video.mp4").toString(), "mp4", StreamKind.Both)).use { gate ->
                gate.start()
                javax.swing.SwingUtilities.invokeAndWait { JFXPanel() }
                Platform.setImplicitExit(false)
                val started = CountDownLatch(1); val paused = CountDownLatch(1); val failure = AtomicReference<String>()
                val handle = AtomicReference<MediaPlayer>()
                Platform.runLater {
                    try {
                        val p = MediaPlayer(Media(gate.localUrl)); handle.set(p)
                        p.setOnError { failure.set(p.error.toString()); started.countDown() }
                        p.setOnPlaying { started.countDown() }; p.setOnPaused { paused.countDown() }; p.play()
                    } catch (e: Exception) { failure.set(e.toString()); started.countDown() }
                }
                try {
                    assertTrue(started.await(20, TimeUnit.SECONDS), "The native player did not start")
                    assertNull(failure.get())
                    Platform.runLater { handle.get().pause(); handle.get().seek(javafx.util.Duration.seconds(1.0)) }
                    assertTrue(paused.await(5, TimeUnit.SECONDS), "Pause did not reach the native player")
                } finally { Platform.runLater { handle.get()?.dispose() } }
            }
        }
    }
    @Test fun pausedPairedDownloadResumesAndProducesBothTracks(): Unit = kotlinx.coroutines.runBlocking {
        val sample = System.getProperty("fetch.player.sample")?.let(::File)
        val ffmpeg = MediaTools.find("ffmpeg")
        assumeTrue("Local MP4 and merger are required", sample?.isFile == true && ffmpeg != null)
        val work = java.nio.file.Files.createTempDirectory("fetch-merge-smoke").toFile()
        try {
            val video = File(work, "fixture.mp4")
            val audio = File(work, "fixture.m4a")
            runMediaProcess(listOf(ffmpeg!!.absolutePath, "-nostdin", "-hide_banner", "-loglevel", "error", "-y", "-i", sample!!.absolutePath, "-t", "1", "-an", "-c:v", "copy", video.absolutePath))
            runMediaProcess(listOf(ffmpeg.absolutePath, "-nostdin", "-hide_banner", "-loglevel", "error", "-y", "-f", "lavfi", "-i", "sine=frequency=440:duration=1", "-c:a", "aac", audio.absolutePath))
            MockWebServer().use { server ->
                server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
                    override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                        val bytes = (if (request.path == "/audio") audio else video).readBytes()
                        val range = request.getHeader("Range")?.removePrefix("bytes=")
                        val start = range?.substringBefore('-')?.toIntOrNull() ?: 0
                        val end = range?.substringAfter('-')?.toIntOrNull()?.coerceAtMost(bytes.lastIndex) ?: bytes.lastIndex
                        if (start >= bytes.size) return MockResponse().setResponseCode(416).setHeader("Content-Range", "bytes */${bytes.size}")
                        return MockResponse().setResponseCode(if (range != null) 206 else 200).setHeader("Content-Type", "video/mp4")
                            .apply { if (range != null) setHeader("Content-Range", "bytes $start-$end/${bytes.size}") }
                            .setBody(Buffer().write(bytes.copyOfRange(start, end + 1)))
                    }
                }
                val audioTrack = WatchStream("Audio", server.url("/audio").toString(), "m4a", StreamKind.Audio, "a", bytes = audio.length())
                val stream = WatchStream("Video + audio", server.url("/video").toString(), "mp4", StreamKind.Both, "v+a", audio = audioTrack)
                val request = SpanRequest(stream.url, "complete.mp4", connections = 1)
                val cancel = SpanCancel()
                val paused = downloadMedia(stream, request, work.absolutePath, cancel, OkHttpSpanDownloader()) { if (it.reservedBytes > 0) cancel.cancel() }
                assertEquals(SpanOutcome.Cancelled, paused)
                assertTrue(File(work, "complete.mp4.span-media").isDirectory)
                val saved = downloadMedia(stream, request, work.absolutePath, SpanCancel(), OkHttpSpanDownloader()) {}
                assertTrue(saved is SpanOutcome.Saved, saved.toString())
                assertFalse(File(work, "complete.mp4.span-media").exists())
                runMediaProcess(listOf(ffmpeg.absolutePath, "-nostdin", "-hide_banner", "-loglevel", "error", "-i", File(work, "complete.mp4").absolutePath, "-map", "0:v:0", "-map", "0:a:0", "-t", "0.1", "-f", "null", "-"))
            }
        } finally { work.deleteRecursively() }
        Unit
    }

    @Test fun suppliedYoutubeLinkResolvesACombinedDownload() {
        val fixture = System.getProperty("fetch.youtube.sample")?.let(::File)
        assumeTrue("A live extraction result is required", fixture?.isFile == true)
        val result = parseMediaInfo(fixture!!.readText(), "https://www.youtube.com/watch?v=DsXxgD_7kDg")
        assertTrue(result.streams.isNotEmpty())
        assertTrue(result.streams.any { it.kind == StreamKind.Both })
        val verified = kotlinx.coroutines.runBlocking { WatchPage.verify(result.streams.first()) }
        assertTrue(verified.url.startsWith("https://"))
    }
}
