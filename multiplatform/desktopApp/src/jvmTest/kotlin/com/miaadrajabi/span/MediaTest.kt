package com.miaadrajabi.span

import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Rule
import org.junit.Test
import kotlin.test.*
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File

class MediaTest {
    @get:Rule val rule = createComposeRule()
    @Test fun platformsUseExactHostBoundaries() {
        assertTrue(WatchPage.isWatch("https://www.youtube.com/watch?v=abc"))
        assertTrue(WatchPage.isInstagram("https://instagram.com/reel/abc/"))
        assertFalse(WatchPage.isWatch("https://notyoutube.com/watch?v=abc"))
        assertFalse(WatchPage.isWatch("https://youtube.com.example.org/"))
        assertFalse(WatchPage.isInstagram("https://notinstagram.com/"))
    }
    @Test fun extractionPrefersCombinedFilesAndRejectsManifestsAndDrm() {
        val info = parseMediaInfo("""{"title":"A sample","uploader":"Artist","duration":30,"http_headers":{"User-Agent":"Fetch-Test","Cookie":"private"},"formats":[
          {"format_id":"v","url":"https://cdn.example/video","protocol":"https","vcodec":"avc1","acodec":"none","height":1080,"ext":"mp4"},
          {"format_id":"both","url":"https://cdn.example/both","protocol":"https","vcodec":"avc1","acodec":"aac","height":360,"ext":"mp4"},
          {"format_id":"hls","url":"https://cdn.example/list.m3u8","protocol":"m3u8_native","vcodec":"avc1","acodec":"aac"},
          {"format_id":"drm","url":"https://cdn.example/protected","has_drm":true,"vcodec":"avc1","acodec":"aac"},
          {"format_id":"fragment","url":"https://cdn.example/parts","fragments":[],"vcodec":"avc1","acodec":"aac"}
        ]}""", "https://youtube.com/watch?v=sample")
        assertEquals(listOf("both", "v"), info.streams.map { it.formatId })
        assertEquals(StreamKind.Both, info.streams.first().kind)
        assertEquals("Fetch-Test", info.streams.first().headers["User-Agent"])
        assertFalse(info.streams.first().headers.containsKey("Cookie"))
        assertTrue(info.streams.last().label.contains("Video only"))
    }
    @Test fun splitTracksOfferACompleteMp4WithSound() {
        val info = parseMediaInfo("""{"title":"Sample","formats":[
          {"format_id":"v","url":"https://cdn.example/v","protocol":"https","vcodec":"avc1","acodec":"none","height":1080,"ext":"mp4","filesize":1000},
          {"format_id":"a","url":"https://cdn.example/a","protocol":"https","vcodec":"none","acodec":"aac","ext":"m4a","filesize":200}
        ]}""", "https://youtube.com/watch?v=sample")
        val combined = info.streams.first()
        assertEquals(StreamKind.Both, combined.kind)
        assertEquals("v+a", combined.formatId)
        assertEquals("a", combined.audio?.formatId)
        assertEquals(1200L, combined.bytes)
        assertTrue(combined.canPreview)
    }
    @Test fun relayPreservesRangeAndHeadersAndRejectsUnknownPaths() {
        MockWebServer().use { upstream ->
            upstream.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 2-5/10").setHeader("Content-Type", "video/mp4").setBody("2345"))
            val stream = WatchStream("Original", upstream.url("/video").toString(), "mp4", StreamKind.Both, headers = mapOf("Referer" to "https://www.instagram.com/"))
            StreamGate(stream).use { gate ->
                gate.start()
                val http = OkHttpClient()
                http.newCall(Request.Builder().url(gate.localUrl).header("Range", "bytes=2-5").build()).execute().use {
                    assertEquals(206, it.code); assertEquals("2345", it.body!!.string()); assertEquals("bytes 2-5/10", it.header("Content-Range"))
                }
                val received = upstream.takeRequest()
                assertEquals("bytes=2-5", received.getHeader("Range")); assertEquals("https://www.instagram.com/", received.getHeader("Referer"))
                http.newCall(Request.Builder().url(gate.localUrl.substringBeforeLast('/') + "/other").build()).execute().use { assertEquals(404, it.code) }
                assertEquals(1, upstream.requestCount)
            }
        }
    }
    @Test fun verificationRejectsHtmlAndFollowsMediaRedirects() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody("login"))
            val file = WatchStream("File", server.url("/video").toString(), "mp4", StreamKind.Both)
            assertFailsWith<IllegalStateException> { WatchPage.verify(file) }
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/real"))
            server.enqueue(MockResponse().setHeader("Content-Type", "video/mp4").setBody("media"))
            assertEquals(server.url("/real").toString(), WatchPage.verify(file).url)
        }
    }
    @Test fun mediaStudioRequiresVerifiedMediaAndInvalidatesOldLinks() {
        val verified = CompletableDeferred<WatchStream>()
        val stream = WatchStream("720p · MP4 · Video + audio", "https://cdn.example/video.mp4", "mp4", StreamKind.Both)
        var saved: WatchStream? = null
        rule.setContent {
            val ink = inkOf("dark")
            FetchTheme(ink) {
                Box(Modifier.size(740.dp, 780.dp)) {
                    MediaStudio(ink, "", "YouTube", DeskSettings(), listOf("Main"), {}, { file, _, _, _, _ -> saved = file },
                        resolve = { WatchPreview(it, "A beautiful journey", null, listOf(stream), "Choose a quality, preview it, then download.", "Fetch Studio", 92.0) },
                        verify = { verified.await() }, player = { _, modifier, _ -> Box(modifier) { Text("Preview playing") } })
                }
            }
        }
        rule.onNodeWithTag("download-media").assertIsNotEnabled()
        rule.onNodeWithTag("media-url").performTextInput("https://youtube.com/watch?v=sample")
        rule.onNodeWithTag("preview-media").performClick()
        rule.onNodeWithTag("download-media").assertIsNotEnabled()
        rule.runOnIdle { verified.complete(stream) }
        rule.waitForIdle()
        rule.onNodeWithTag("download-media").assertIsEnabled().performClick()
        rule.runOnIdle { assertEquals(stream, saved) }
        val dir = File("build/ui-review").apply { mkdirs() }
        File(dir, "media-studio.png").writeBytes(Image.makeFromBitmap(rule.onRoot().captureToImage().asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        rule.onNodeWithTag("media-url").performTextReplacement("https://instagram.com/reel/other")
        rule.onNodeWithTag("download-media").assertIsNotEnabled()
        rule.onNodeWithText("A beautiful journey").assertDoesNotExist()
    }
}
