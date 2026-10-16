package com.miaadrajabi.span

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ShellMatrixTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun clientSizesKeepTheAddControlInside() {
        val shots = listOf(
            Shot("1440x900", 1440, 900, 1f, 1f, false),
            Shot("1280x800", 1280, 800, 1f, 1f, false),
            Shot("1040x680", 1040, 680, 1f, 1f, false),
            Shot("1024x768", 1024, 768, 1f, 1f, false),
            Shot("960x600", 960, 600, 1f, 1f, false),
            Shot("800x600", 800, 600, 1f, 1f, false),
            Shot("640x480", 640, 480, 1f, 1f, false),
            Shot("599x480", 599, 480, 1f, 1f, false),
            Shot("600x360", 600, 360, 1f, 1f, false),
            Shot("900x359", 900, 359, 1f, 1f, false),
            Shot("380x240", 380, 240, 1f, 1f, false),
            Shot("320x200", 320, 200, 1f, 1f, false),
            Shot("679x419", 679, 419, 1f, 1f, false),
            Shot("680x420", 680, 420, 1f, 1f, false),
            Shot("800x600-text200", 800, 600, 2f, 1f, false),
            Shot("1024x768-dpi125", 1024, 768, 1f, 1.25f, false),
            Shot("1024x768-dpi150", 1024, 768, 1f, 1.5f, false),
            Shot("800x600-dpi200", 800, 600, 1f, 2f, false),
            Shot("1024x768-rtl", 1024, 768, 1f, 1f, true),
            Shot("800x600-rtl-text200", 800, 600, 2f, 1f, true),
        )
        val dir = shotDir()
        shots.forEach { shot ->
            val mode = resolveShell(ShellMode.Wide, false, shot.width.toFloat(), shot.height.toFloat())
            rule.setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(density = shot.density, fontScale = shot.fontScale),
                    LocalLayoutDirection provides if (shot.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                ) {
                    ProofChrome(shot.width, shot.height, mode)
                }
            }
            rule.waitForIdle()
            val root = rule.onRoot().getUnclippedBoundsInRoot()
            val add = rule.onNodeWithTag("add-download")
            add.assertIsDisplayed()
            val bounds = add.getUnclippedBoundsInRoot()
            assertTrue(bounds.left >= root.left, "${shot.name} add starts outside")
            assertTrue(bounds.top >= root.top, "${shot.name} add starts above")
            assertTrue(bounds.right <= root.right + 1.dp, "${shot.name} add crosses the right edge")
            assertTrue(bounds.bottom <= root.bottom + 1.dp, "${shot.name} add crosses the bottom edge")
            val bitmap = rule.onRoot().captureToImage()
            val png = Image.makeFromBitmap(bitmap.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)
            checkNotNull(png)
            File(dir, "${shot.name}.png").writeBytes(png.bytes)
        }
    }

    @Test
    fun resizingDoesNotReplaceTheRunningDownload() {
        val job = proofJob(7).also { it.state = "Running" }
        val token = job.cancel
        val size = mutableStateOf(DpSize(1280.dp, 800.dp))
        var starts = 0
        rule.setContent {
            val current = size.value
            val mode = resolveShell(ShellMode.Wide, false, current.width.value, current.height.value)
            ProofChrome(current.width.value.toInt(), current.height.value.toInt(), mode, listOf(job)) {
                starts += 1
            }
        }
        rule.waitForIdle()
        size.value = DpSize(640.dp, 480.dp)
        rule.waitForIdle()
        size.value = DpSize(380.dp, 240.dp)
        rule.waitForIdle()
        assertSame(token, job.cancel)
        assertEquals("Running", job.state)
        assertEquals(0, starts)
    }

    @Test
    fun tenThousandRowsFilterAndScroll() {
        val jobs = List(10_000) { proofJob(it) }
        val started = System.nanoTime()
        val found = libraryRows(jobs, emptyList(), "All downloads", "All", "file-9000.bin", "name", true)
        val millis = (System.nanoTime() - started) / 1_000_000L
        assertEquals(listOf(9000L), found.map { it.id })
        assertTrue(millis < 500L, "filter took ${millis}ms")
        val listState = LazyListState()
        rule.setContent {
            val rows = remember { jobs }
            LazyColumn(Modifier.size(800.dp, 600.dp), listState) {
                items(rows, key = { it.id }) { job ->
                    DownloadCard(
                        job, inkOf("light"), false, false,
                        onSelect = {},
                        onOpenFile = {},
                        onPause = {},
                        onStart = {},
                        onEdit = {},
                        onOpen = {},
                        onRemove = {},
                        onExtract = {},
                    )
                }
            }
        }
        val scrollStarted = System.nanoTime()
        rule.runOnIdle { listState.requestScrollToItem(9000) }
        rule.waitForIdle()
        val scrollMillis = (System.nanoTime() - scrollStarted) / 1_000_000L
        rule.onNodeWithTag("row-9000").assertIsDisplayed()
        assertTrue(scrollMillis < 2_000L, "scroll took ${scrollMillis}ms")
        File(shotDir(), "list-benchmark.txt").writeText("filterMs=$millis\nscrollTo9000Ms=$scrollMillis\nrows=10000\n")
    }

    @Test
    fun primaryControlsAreNamedForAScreenReader() {
        rule.setContent { ProofChrome(1280, 800, ShellMode.Wide) }
        rule.onNodeWithContentDescription("Add download").assertIsDisplayed()
        rule.onNodeWithContentDescription("Pause all").assertIsDisplayed()
        rule.onNodeWithContentDescription("Search downloads").assertIsDisplayed()
        rule.setContent {
            UndoBar(inkOf("light"), 1, {}, {})
        }
        rule.onNodeWithContentDescription("Undo remove").assertIsDisplayed()
    }

    @Test
    fun undoPutsTheRowBackAndADeletedFileDoesNot() {
        val job = proofJob(3)
        assertTrue(keptForUndo(false, listOf(job)).contains(job))
        assertTrue(keptForUndo(true, listOf(job)).isEmpty())
        var restored = false
        rule.setContent { UndoBar(inkOf("light"), 1, { restored = true }, {}) }
        rule.onNodeWithContentDescription("Undo remove").performClick()
        assertTrue(restored)
    }

    @Test
    fun movingAQueueUpChangesTheOrder() {
        val queues = androidx.compose.runtime.mutableStateListOf(DeskQueue("A"), DeskQueue("B"))
        rule.setContent {
            Box(Modifier.size(420.dp, 640.dp)) {
                AppNav(
                    inkOf("light"), "All downloads", "All", emptyList(), emptyList(), queues, false,
                    {}, {}, {}, { from, to ->
                        val next = placeQueue(queues.toList(), from, to)
                        queues.clear()
                        queues.addAll(next)
                    }, {}, {},
                )
            }
        }
        rule.onNodeWithContentDescription("Queue actions for B").performClick()
        rule.onNodeWithText("Move B up").performClick()
        assertEquals(listOf("B", "A"), queues.map { it.name })
    }

    @Test
    fun theCalendarMarksTodayAndDoesNotInventEvents() {
        rule.setContent {
            MonthBoard(inkOf("light"), -1, -1, 15, 11, 5, 1, 31)
        }
        rule.onNodeWithText("Always open").assertIsDisplayed()
        rule.onNodeWithText("No daily window is set.").assertIsDisplayed()
        rule.onNodeWithText("11").assertIsDisplayed()
    }

    @Test
    fun aLiveDownloadSurvivesAResize() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBodyDelay(1500, TimeUnit.MILLISECONDS).setBody("still-the-same-socket"))
        server.start()
        val dir = java.nio.file.Files.createTempDirectory("fetch-resize").toFile()
        val token = com.miaadrajabi.span.SpanCancel()
        val size = mutableStateOf(DpSize(1280.dp, 800.dp))
        val outcome = AtomicReference<SpanOutcome?>(null)
        rule.setContent {
            val current = size.value
            val mode = resolveShell(ShellMode.Wide, false, current.width.value, current.height.value)
            ProofChrome(current.width.value.toInt(), current.height.value.toInt(), mode)
        }
        rule.onNodeWithContentDescription("Add download").assertIsDisplayed()
        val worker = Thread {
            val downloader = OkHttpSpanDownloader(OkHttpClient())
            outcome.set(
                kotlinx.coroutines.runBlocking {
                    downloader.download(
                        SpanRequest(server.url("/live").toString(), "live.bin", connections = 1),
                        dir.absolutePath,
                        token,
                    ) { }
                },
            )
        }
        worker.start()
        val seen = server.takeRequest(5, TimeUnit.SECONDS)
        checkNotNull(seen)
        size.value = DpSize(380.dp, 240.dp)
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Add").assertIsDisplayed()
        worker.join(8_000)
        assertTrue(!worker.isAlive)
        assertEquals(1, server.requestCount)
        assertTrue(outcome.get() is SpanOutcome.Saved, outcome.get().toString())
        assertEquals("still-the-same-socket", File(dir, "live.bin").readText())
        server.shutdown()
        dir.deleteRecursively()
    }
}

private data class Shot(
    val name: String,
    val width: Int,
    val height: Int,
    val fontScale: Float,
    val density: Float,
    val rtl: Boolean,
)

@androidx.compose.runtime.Composable
private fun ProofChrome(
    width: Int,
    height: Int,
    mode: ShellMode,
    jobs: List<DeskJob> = listOf(proofJob(1).also { it.state = "Running"; it.bytes = 20; it.total = 100 }),
    onStart: () -> Unit = {},
) {
    val ink = inkOf("light")
    val settings = DeskSettings(folder = System.getProperty("java.io.tmpdir"))
    FetchTheme(ink) {
    Box(Modifier.size(width.dp, height.dp)) {
        FrostedBackdrop(ink)
        if (mode == ShellMode.Widget) {
            WidgetFace(
                ink, jobs, jobs.first(), null, DpSize(width.dp, height.dp),
                onPin = {},
                onPauseAll = {},
                onResume = {},
                onAdd = {},
                onExpand = {},
                onOpen = {},
            )
        } else {
            Column(Modifier.fillMaxSize()) {
                CommandBar(
                    ink, "", {}, jobs.map { it.fileName }, true, mode,
                    androidx.compose.ui.focus.FocusRequester(),
                    onAdd = {},
                    onToggleRun = {},
                    onMenu = {},
                    onSettings = {},
                    onWidget = {},
                    library = "All downloads",
                    onLibrary = {},
                )
                Row(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    if (mode == ShellMode.Wide || mode == ShellMode.Standard) {
                        AppNav(
                            ink, "All downloads", "All", jobs, emptyList(), listOf(DeskQueue("Main")),
                            mode == ShellMode.Standard, {}, {}, {}, { _, _ -> }, {}, {},
                        )
                    }
                    LazyColumn(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        items(jobs.take(8), key = { it.id }) { job ->
                            DownloadCard(job, ink, false, mode != ShellMode.Compact, {}, {}, {}, onStart, {}, {}, {}, {})
                        }
                    }
                }
                StatusLine(ink, jobs, settings, null)
            }
        }
    }
}

}

private fun proofJob(index: Int) = DeskJob(
    id = index.toLong(),
    url = "https://example.com/file-$index.bin",
    fileName = "file-$index.bin",
    state = "Queued",
    addedAt = index.toLong(),
    queue = "Main",
    connections = 1,
    speedLimit = 0L,
    checksum = "",
    algorithm = "SHA256",
    path = System.getProperty("java.io.tmpdir"),
    message = "",
).also { it.total = 1_000L }

private fun shotDir(): File {
    val dir = listOf(File("docs/shots"), File("../docs/shots")).first { it.parentFile.exists() }
    dir.mkdirs()
    return dir
}
