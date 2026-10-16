package com.miaadrajabi.span

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.*
import kotlinx.coroutines.CompletableDeferred
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.test.*

class InteractionTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun bothDownloadViewsExpandAndCollapseWithoutSelectingOrRestarting() {
        for (table in listOf(false, true)) {
            val job = sample()
            var selected = false
            var started = false
            rule.setContent {
                val ink = inkOf("dark")
                FetchTheme(ink) {
                    Box(Modifier.size(760.dp, 600.dp)) {
                        FrostedBackdrop(ink)
                        if (table) DownloadRow(job, ink, false, true, 180, { selected = true }, {}, {}, {}, { started = true }, {}, {}, {})
                        else DownloadCard(job, ink, false, true, { selected = true }, {}, {}, { started = true }, {}, {}, {}, {})
                    }
                }
            }
            rule.onNodeWithContentDescription("Expand ${job.fileName}").performClick()
            rule.onNodeWithTag("details-${job.id}", useUnmergedTree = true).assertIsDisplayed()
            rule.runOnIdle { assertTrue(job.expanded); assertFalse(selected); assertFalse(started) }
            capture(if (table) "table-expanded" else "card-expanded")
            rule.onNodeWithContentDescription("Collapse ${job.fileName}").performClick()
            rule.onNodeWithTag("details-${job.id}", useUnmergedTree = true).assertDoesNotExist()
        }
    }

    @Test
    fun rightClickWorksOnTheCardAndItsChildrenInBothViews() {
        for (table in listOf(false, true)) {
            val job = sample()
            rule.setContent {
                val ink = inkOf("orchid")
                FetchTheme(ink) {
                    Box(Modifier.size(760.dp, 600.dp).padding(20.dp)) {
                        if (table) DownloadRow(job, ink, false, true, 180, {}, {}, {}, {}, {}, {}, {}, {})
                        else DownloadCard(job, ink, false, true, {}, {}, {}, {}, {}, {}, {}, {})
                    }
                }
            }
            rule.onNodeWithText(job.fileName, useUnmergedTree = true).performMouseInput { click(button = MouseButton.Secondary) }
            rule.onNodeWithTag("transfer-context-menu").assertIsDisplayed()
            capture("context-${if (table) "table" else "card"}", "transfer-context-menu")
            rule.onNodeWithText("Expand details").performClick()
            rule.onNodeWithTag("details-${job.id}", useUnmergedTree = true).assertIsDisplayed()
            rule.onNodeWithTag("row-${job.id}").performMouseInput { click(button = MouseButton.Secondary, position = Offset(12f, 12f)) }
            rule.onNodeWithText("Collapse details").assertIsDisplayed().performClick()
            rule.onNodeWithTag("details-${job.id}", useUnmergedTree = true).assertDoesNotExist()
        }
    }

    @Test
    fun cursorMenuStaysAtTheClickAndFitsNearWindowEdges() {
        val bounds = IntRect(80, 100, 700, 500)
        assertEquals(IntOffset(120, 130), CursorMenuPosition(Offset(40f, 30f)).calculatePosition(bounds, IntSize(900, 700), LayoutDirection.Ltr, IntSize(244, 400)))
        assertEquals(IntOffset(356, 100), CursorMenuPosition(Offset(590f, 380f)).calculatePosition(bounds, IntSize(600, 500), LayoutDirection.Rtl, IntSize(244, 400)))
    }

    @Test
    fun sidebarAnimatesToARailAndExpandsAgain() {
        rule.setContent {
            FetchTheme(inkOf("dark")) {
                Box(Modifier.size(360.dp, 600.dp)) {
                    AppNav(inkOf("dark"), "All downloads", "All", emptyList(), emptyList(), listOf(DeskQueue("Main")), false, {}, {}, {}, { _, _ -> }, {}, {})
                }
            }
        }
        rule.onNodeWithContentDescription("Collapse sidebar").performClick()
        rule.waitForIdle()
        val narrow = rule.onNodeWithTag("sidebar").getUnclippedBoundsInRoot().width
        assertEquals(68.dp, narrow)
        rule.onNodeWithContentDescription("All downloads").assertIsDisplayed()
        rule.onNodeWithContentDescription("Settings").assertIsDisplayed()
        capture("sidebar-collapsed")
        rule.onNodeWithContentDescription("Expand sidebar").performClick()
        rule.waitForIdle()
        assertEquals(216.dp, rule.onNodeWithTag("sidebar").getUnclippedBoundsInRoot().width)
    }

    @Test
    fun sidebarCanBeResizedByDraggingItsHandle() {
        rule.setContent {
            var width by remember { mutableStateOf(216) }
            FetchTheme(inkOf("dark")) {
                Box(Modifier.size(400.dp, 500.dp)) {
                    AppNav(inkOf("dark"), "All downloads", "All", emptyList(), emptyList(), listOf(DeskQueue("Main")), false, {}, {}, {}, { _, _ -> }, {}, {}, expandedWidth = width, onWidthChange = { width = it })
                }
            }
        }
        rule.onNodeWithContentDescription("Resize sidebar").performMouseInput { moveTo(center); press(); moveBy(Offset(30f, 0f)); moveBy(Offset(30f, 0f)); release() }
        rule.waitForIdle()
        val width = rule.onNodeWithTag("sidebar").getUnclippedBoundsInRoot().width
        assertTrue(width > 216.dp && width <= 280.dp, "Resized width: $width")
        capture("sidebar-resized")
    }

    @Test
    fun editingTheUrlInvalidatesAnInFlightLinkCheck() {
        val first = CompletableDeferred<ProbeReport>()
        val second = CompletableDeferred<ProbeReport>()
        var checks = 0
        var submitted: String? = null
        val report = ProbeReport(200, 1024, "download.zip", "application/zip", "", 100000, "Ready to download")
        rule.setContent {
            FetchTheme(inkOf("light")) {
                Box(Modifier.size(640.dp, 500.dp)) {
                    LinkOfferForm(inkOf("light"), listOf("Main"), DeskSettings(), "", 350.dp,
                        onClose = { url, _, _, _, _, _, _, _, _ -> submitted = url },
                        inspect = { if (checks++ == 0) first.await() else second.await() })
                }
            }
        }
        val urlField = rule.onNodeWithTag("download-url")
        urlField.performTextInput("https://example.com/first.zip")
        rule.onNodeWithText("Check link").performClick()
        urlField.performTextReplacement("https://example.com/second.zip")
        rule.runOnIdle { first.complete(report) }
        rule.onNodeWithTag("start-download").assertIsNotEnabled()
        rule.onNodeWithText("Check link").performClick()
        rule.runOnIdle { second.complete(report) }
        rule.waitForIdle()
        rule.onNodeWithTag("start-download").assertIsEnabled().performClick()
        rule.runOnIdle { assertEquals("https://example.com/second.zip", submitted) }
    }

    @Test
    fun addDownloadKeepsItsActionsVisibleAtCompactSizes() {
        for (size in listOf(DpSize(760.dp, 620.dp), DpSize(640.dp, 480.dp), DpSize(380.dp, 480.dp))) {
            rule.setContent {
                val ink = inkOf("dark")
                FetchTheme(ink) {
                    Box(Modifier.size(size)) {
                        FrostedBackdrop(ink)
                        AddDialog(ink, listOf("Main", "Overnight"), DeskSettings()) { _, _, _, _, _, _, _, _, _ -> }
                    }
                }
            }
            rule.onNodeWithContentDescription("Close add download").assertIsDisplayed()
            rule.onNodeWithTag("start-download").assertIsDisplayed().assertIsNotEnabled()
            rule.onNodeWithTag("download-url").performTextInput("https://example.com/design-resources.zip")
            rule.onNodeWithText("Check link").assertIsEnabled()
            capture("add-download-${size.width.value.toInt()}")
        }
    }

    private fun sample() = DeskJob(81L, "https://example.com/design-resources.zip", "Design resources.zip", "Running", 0L, "Main", 8, 0L, "", "SHA256", "/tmp/Fetch", "").also {
        it.bytes = 360_000_000; it.total = 900_000_000; it.speed = 8_000_000
        it.parts = List(4) { index -> SpanPart(index, 90_000_000L, 225_000_000L) }
    }
    private fun capture(name: String, tag: String? = null) {
        rule.waitForIdle()
        val dir = File("build/ui-review").apply { mkdirs() }
        val node = if (tag == null) rule.onRoot() else rule.onNodeWithTag(tag)
        val bitmap = node.captureToImage().asSkiaBitmap()
        File(dir, "$name.png").writeBytes(Image.makeFromBitmap(bitmap).encodeToData(EncodedImageFormat.PNG)!!.bytes)
    }
}
