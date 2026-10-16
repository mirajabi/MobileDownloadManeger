package com.miaadrajabi.span

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeskFrameTest {
    @Test
    fun wideAndStandardAndCompactStayOutOfTheWidget() {
        assertEquals(ShellMode.Wide, resolveShell(ShellMode.Wide, false, 1440f, 900f))
        assertEquals(ShellMode.Wide, resolveShell(ShellMode.Wide, false, 1280f, 800f))
        assertEquals(ShellMode.Standard, resolveShell(ShellMode.Wide, false, 1024f, 768f))
        assertEquals(ShellMode.Standard, resolveShell(ShellMode.Wide, false, 960f, 600f))
        assertEquals(ShellMode.Compact, resolveShell(ShellMode.Wide, false, 800f, 600f))
        assertEquals(ShellMode.Compact, resolveShell(ShellMode.Wide, false, 640f, 480f))
        assertEquals(ShellMode.Compact, resolveShell(ShellMode.Wide, false, 600f, 360f))
    }

    @Test
    fun smallWidthOrHeightEntersTheWidget() {
        assertEquals(ShellMode.Widget, resolveShell(ShellMode.Compact, false, 599f, 480f))
        assertEquals(ShellMode.Widget, resolveShell(ShellMode.Wide, false, 900f, 359f))
    }

    @Test
    fun widgetStaysUntilBothThresholdsClear() {
        assertEquals(ShellMode.Widget, resolveShell(ShellMode.Widget, false, 679f, 419f))
        assertEquals(ShellMode.Widget, resolveShell(ShellMode.Widget, false, 620f, 480f))
        assertEquals(ShellMode.Compact, resolveShell(ShellMode.Widget, false, 680f, 420f))
        assertEquals(ShellMode.Standard, resolveShell(ShellMode.Widget, false, 1000f, 700f))
    }

    @Test
    fun manualWidgetIgnoresSize() {
        assertEquals(ShellMode.Widget, resolveShell(ShellMode.Wide, true, 1440f, 900f))
    }

    @Test
    fun restoredSizeStaysInsideTheWorkArea() {
        assertEquals(360f to 280f, clampSize(100f, 100f, 1440f, 900f))
        assertEquals(1280f to 800f, clampSize(1280f, 800f, 1440f, 900f))
        assertEquals(1440f to 900f, clampSize(2000f, 2000f, 1440f, 900f))
    }

    @Test
    fun aDraggedNameColumnStaysReadable() {
        assertEquals(200, columnWidth(160, 40f))
        assertEquals(80, columnWidth(90, -40f))
        assertEquals(420, columnWidth(400, 80f))
    }

    @Test
    fun aQueueMovesByDragAndByOneRow() {
        assertEquals(listOf("B", "A", "C"), placeQueue(listOf("A", "B", "C"), 1, 0))
        assertEquals(listOf("A", "B"), placeQueue(listOf("A", "B"), 0, -1))
        assertEquals(2, queueDestination(3, 0, 80f))
        assertEquals(0, queueDestination(3, 0, -40f))
    }

    @Test
    fun oldPaceSamplesAreDropped() {
        val now = 100_000L
        val kept = recentPace(listOf(now - 61_000L to 9L, now - 1_000L to 4L), now)
        assertEquals(listOf(4L), kept.map { it.second })
        assertEquals(emptyList(), recentPace(emptyList(), now))
    }

    @Test
    fun aClosedHourStaysClosed() {
        assertTrue(DeskLibrary.queueOpen(DeskQueue("Night", 1, 22, 6), 23))
        assertFalse(DeskLibrary.queueOpen(DeskQueue("Night", 1, 22, 6), 10))
        assertFalse(DeskLibrary.queueOpen(DeskQueue("Day", 1, 9, 17), 18))
        assertTrue(DeskLibrary.queueOpen(DeskQueue("Main"), 3))
    }

    @Test
    fun aMonthStartsOnTheRightWeekday() {
        assertEquals(listOf(0, 0, 0, 0) + (1..31).toList(), monthCells(5, 1, 31))
        assertEquals((1..28).toList(), monthCells(2, 2, 28))
        assertEquals("Sun", weekdayLabels(1).first())
        assertEquals("Mon", weekdayLabels(2).first())
        val calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        calendar.set(2026, java.util.Calendar.OCTOBER, 11, 15, 0, 0)
        val facts = monthFacts(calendar)
        assertEquals(11, facts.today)
        assertEquals(15, facts.hour)
        assertEquals(31, facts.days)
        assertEquals(java.util.Calendar.THURSDAY, facts.startWeekday)
        assertEquals(11, calendar.get(java.util.Calendar.DAY_OF_MONTH))
    }
}
