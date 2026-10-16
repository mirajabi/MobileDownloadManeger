package com.miaadrajabi.span

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.test.assertTrue

class AppearanceTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun paletteTextAndPrimaryActionsHaveReadableContrast() {
        fun contrast(a: Color, b: Color): Float {
            val x = a.luminance()
            val y = b.luminance()
            return (maxOf(x, y) + 0.05f) / (minOf(x, y) + 0.05f)
        }
        deskThemes.forEach { theme ->
            val ink = inkOf(theme.key)
            listOf(ink.night, ink.panel, ink.raised).forEach { surface ->
                assertTrue(contrast(ink.text, surface) >= 4.5f, "${theme.name}: body text")
                assertTrue(contrast(ink.muted, surface) >= 4.5f, "${theme.name}: secondary text")
                assertTrue(contrast(ink.accent, surface) >= 4.5f, "${theme.name}: link text")
            }
            assertTrue(contrast(ink.onAccent, ink.accent) >= 4.5f, "${theme.name}: primary action")
        }
    }

    @Test
    fun allConnectionDetailsCanBeReachedInAShortInspector() {
        val job = sample(1).also {
            it.parts = List(32) { index -> SpanPart(index, 1024L, 4096L) }
        }
        rule.setContent {
            FetchTheme(inkOf("forest")) {
                Box(Modifier.size(320.dp, 360.dp)) {
                    Inspector(inkOf("forest"), job, DeskSettings(), "Connections", {}, {}, {}, {}, {})
                }
            }
        }
        rule.onNodeWithTag("inspector-scroll").performScrollToNode(hasText("Part 32", substring = true))
        rule.onNodeWithText("Part 32", substring = true).assertIsDisplayed()
        rule.onNodeWithContentDescription("Close").assertIsDisplayed()
        capture("short-inspector")
    }

    @Test
    fun longNavigationScrollsWhileSettingsStayVisible() {
        val queues = List(20) { DeskQueue("Queue ${it + 1}") }
        rule.setContent {
            FetchTheme(inkOf("dark")) {
                Box(Modifier.size(216.dp, 360.dp)) {
                    AppNav(inkOf("dark"), "All downloads", "All", emptyList(), emptyList(), queues, false,
                        {}, {}, {}, { _, _ -> }, {}, {})
                }
            }
        }
        rule.onNodeWithContentDescription("Settings").assertIsDisplayed()
        rule.onNodeWithTag("navigation-scroll").performScrollToNode(hasContentDescription("Queue 20"))
        rule.onNodeWithContentDescription("Queue 20").assertIsDisplayed()
        rule.onNodeWithContentDescription("Settings").assertIsDisplayed()
    }

    @Test
    fun settingsFitASmallWindowAndAllowThemeSelection() {
        var chosen by mutableStateOf("light")
        rule.setContent {
            val ink = inkOf(chosen)
            FetchTheme(ink) {
                Box(Modifier.size(640.dp, 480.dp)) {
                    SettingsDialog(ink, DeskSettings(theme = chosen), { chosen = it }, {})
                }
            }
        }
        rule.onNodeWithText("Save", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithContentDescription("Forest theme").performScrollTo().performClick()
        rule.runOnIdle { assertTrue(chosen == "forest") }
        rule.onNodeWithText("Save", useUnmergedTree = true).assertIsDisplayed()
        capture("settings-640x480")
    }

    @Test
    fun aCompletedCardOpensTheFileWithoutStartingAnotherTransfer() {
        var opened = false
        var restarted = false
        val job = sample(1).also { it.state = "Saved" }
        rule.setContent {
            FetchTheme(inkOf("light")) {
                Box(Modifier.width(700.dp)) {
                    DownloadCard(job, inkOf("light"), false, true, {}, { opened = true }, {}, { restarted = true }, {}, {}, {}, {})
                }
            }
        }
        rule.onNodeWithContentDescription("Open file").performClick()
        rule.runOnIdle { assertTrue(opened && !restarted) }
    }

    @Test
    fun renderEveryThemeAtTheDefaultSize() {
        val jobs = listOf(sample(1), sample(2).also { it.state = "Paused" }, sample(3).also { it.state = "Saved"; it.bytes = it.total!! })
        deskThemes.forEach { theme ->
            rule.setContent {
                val ink = inkOf(theme.key)
                FetchTheme(ink) {
                    Box(Modifier.size(1040.dp, 680.dp)) {
                        FrostedBackdrop(ink)
                        Column(Modifier.fillMaxSize()) {
                            CommandBar(ink, "", {}, emptyList(), true, ShellMode.Standard, remember { FocusRequester() }, {}, {}, {}, {}, {}, "All downloads", {})
                            Row(Modifier.weight(1f).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                AppNav(ink, "All downloads", "All", jobs, listOf(DeskCategory("Video", "mp4", ""), DeskCategory("Document", "pdf", "")), listOf(DeskQueue("Main"), DeskQueue("Overnight", 2, 22, 6)), false, {}, {}, {}, { _, _ -> }, {}, {})
                                Column(Modifier.weight(1f).padding(top = 8.dp)) {
                                    ListHead(ink, "All downloads", jobs.size, true, true, 680.dp, jobs, DeskSettings(), {}, {})
                                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(jobs) { job -> DownloadCard(job, ink, job.id == 1L, true, {}, {}, {}, {}, {}, {}, {}, {}) }
                                    }
                                }
                            }
                            StatusLine(ink, jobs, DeskSettings(), null)
                        }
                    }
                }
            }
            rule.onNodeWithContentDescription("Add download").assertIsDisplayed()
            capture("theme-${theme.key}")
        }
    }

    private fun capture(name: String) {
        rule.waitForIdle()
        val dir = File("build/ui-review").apply { mkdirs() }
        val bitmap = rule.onRoot().captureToImage().asSkiaBitmap()
        File(dir, "$name.png").writeBytes(Image.makeFromBitmap(bitmap).encodeToData(EncodedImageFormat.PNG)!!.bytes)
    }

    private fun sample(index: Int) = DeskJob(
        index.toLong(), "https://downloads.example.com/file", when (index) {
            1 -> "Design resources.zip"
            2 -> "Product walkthrough.mp4"
            else -> "Project brief.pdf"
        }, "Running", 0L, "Main", 8, 0L, "", "SHA256", System.getProperty("java.io.tmpdir"), "",
    ).also { it.bytes = 360_000_000L; it.total = 900_000_000L; it.speed = 8_400_000L }
}
