package com.miaadrajabi.span

internal const val DEFAULT_WINDOW_WIDTH = 1040
internal const val DEFAULT_WINDOW_HEIGHT = 680

enum class ShellMode {
    Wide,
    Standard,
    Compact,
    Widget,
}

/**
 * Picks the desktop shell from the logical client size.
 * Widget entry and exit use different thresholds so a resize on the boundary does not flicker.
 * A manual widget stays a widget until the user expands it.
 */
fun resolveShell(current: ShellMode, manualWidget: Boolean, widthDp: Float, heightDp: Float): ShellMode {
    if (manualWidget) return ShellMode.Widget
    val widget = current == ShellMode.Widget
    if (!widget && (widthDp < 600f || heightDp < 360f)) return ShellMode.Widget
    if (widget && (widthDp < 680f || heightDp < 420f)) return ShellMode.Widget
    return when {
        widthDp >= 1280f && heightDp >= 360f -> ShellMode.Wide
        widthDp >= 960f && heightDp >= 360f -> ShellMode.Standard
        else -> ShellMode.Compact
    }
}

/** Keeps a restored window inside the usable screen, without going under the platform minimum. */
fun clampSize(width: Float, height: Float, screenWidth: Float, screenHeight: Float): Pair<Float, Float> {
    val maxWidth = screenWidth.coerceAtLeast(360f)
    val maxHeight = screenHeight.coerceAtLeast(280f)
    return width.coerceIn(360f, maxWidth) to height.coerceIn(280f, maxHeight)
}

/** Keeps a dragged table column wide enough to read and narrow enough to leave room for the rest. */
fun columnWidth(current: Int, deltaDp: Float): Int = (current + deltaDp).toInt().coerceIn(80, 420)

fun <T> placeQueue(items: List<T>, from: Int, to: Int): List<T> {
    if (from !in items.indices || to !in items.indices || from == to) return items
    val copy = items.toMutableList()
    val item = copy.removeAt(from)
    copy.add(to, item)
    return copy
}

/** Turns a vertical drag into a row index. One row is 36 dp. */
fun queueDestination(count: Int, from: Int, dragDp: Float, rowDp: Float = 36f): Int {
    if (count <= 0 || from !in 0 until count || rowDp <= 0f) return from
    return (from + (dragDp / rowDp).toInt()).coerceIn(0, count - 1)
}

/** Drops pace samples older than 60 seconds. It does not invent missing points. */
fun recentPace(samples: List<Pair<Long, Long>>, now: Long): List<Pair<Long, Long>> {
    return samples.filter { now - it.first in 0..60_000L }
}

/** Blank cells lead the month so day 1 sits on its weekday. Zero means an empty cell. */
fun monthCells(startWeekday: Int, firstDayOfWeek: Int, daysInMonth: Int): List<Int> {
    val days = daysInMonth.coerceIn(1, 31)
    val lead = ((startWeekday - firstDayOfWeek) % 7 + 7) % 7
    return List(lead) { 0 } + (1..days).toList()
}

fun weekdayLabels(firstDayOfWeek: Int): List<String> {
    val names = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    val start = (firstDayOfWeek - 1).mod(7)
    return List(7) { names[(start + it) % 7] }
}

data class MonthFacts(
    val hour: Int,
    val today: Int,
    val startWeekday: Int,
    val firstDayOfWeek: Int,
    val days: Int,
)

fun monthFacts(calendar: java.util.Calendar): MonthFacts {
    val copy = calendar.clone() as java.util.Calendar
    val hour = copy.get(java.util.Calendar.HOUR_OF_DAY)
    val today = copy.get(java.util.Calendar.DAY_OF_MONTH)
    val first = copy.firstDayOfWeek
    copy.set(java.util.Calendar.DAY_OF_MONTH, 1)
    return MonthFacts(
        hour,
        today,
        copy.get(java.util.Calendar.DAY_OF_WEEK),
        first,
        copy.getActualMaximum(java.util.Calendar.DAY_OF_MONTH),
    )
}
