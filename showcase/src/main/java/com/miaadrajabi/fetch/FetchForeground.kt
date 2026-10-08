package com.miaadrajabi.fetch

/**
 * How many Fetch screens are visible. Clipboard offers use this to decide
 * between a dialog and a notification.
 */
object FetchForeground {
    private var depth = 0

    val open: Boolean
        get() = depth > 0

    fun enter() {
        depth += 1
    }

    fun leave() {
        if (depth > 0) depth -= 1
    }
}
