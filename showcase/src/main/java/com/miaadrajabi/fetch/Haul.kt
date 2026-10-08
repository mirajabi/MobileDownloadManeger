package com.miaadrajabi.fetch

import android.content.Context
import java.util.Locale

/**
 * A small score for finished downloads. The floating orb shows the rank.
 * The same id is counted once.
 */
object Haul {

    data class Rank(val name: String, val need: Int)

    data class Snapshot(
        val files: Int,
        val bytes: Long,
        val rank: String,
        val toward: String
    )

    fun record(context: Context, id: String, bytes: Long) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val seen = HashSet(prefs.getStringSet(KEY_IDS, emptySet()) ?: emptySet())
        if (!seen.add(id)) return
        if (seen.size > 400) {
            val trimmed = HashSet<String>()
            var kept = 0
            for (item in seen) {
                trimmed.add(item)
                kept += 1
                if (kept >= 400) break
            }
            seen.clear()
            seen.addAll(trimmed)
        }
        prefs.edit()
            .putStringSet(KEY_IDS, seen)
            .putInt(KEY_FILES, prefs.getInt(KEY_FILES, 0) + 1)
            .putLong(KEY_BYTES, prefs.getLong(KEY_BYTES, 0L) + bytes.coerceAtLeast(0L))
            .putBoolean(KEY_POP, true)
            .apply()
    }

    fun snapshot(context: Context): Snapshot {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val files = prefs.getInt(KEY_FILES, 0)
        val rank = rankFor(files)
        val next = RANKS.firstOrNull { it.need > files }
        val toward = if (next == null) {
            "Captain is the top rank"
        } else {
            val left = next.need - files
            val word = if (left == 1) "file" else "files"
            "$left $word to ${next.name}"
        }
        return Snapshot(files, prefs.getLong(KEY_BYTES, 0L), rank.name, toward)
    }

    fun takePop(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_POP, false)) return false
        prefs.edit().putBoolean(KEY_POP, false).apply()
        return true
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        var value = bytes.toDouble()
        var index = 0
        while (value >= 1024.0 && index < units.lastIndex) {
            value /= 1024.0
            index += 1
        }
        return String.format(Locale.US, "%.1f %s", value, units[index])
    }

    private fun rankFor(files: Int): Rank {
        var current = RANKS[0]
        for (rank in RANKS) {
            if (files >= rank.need) current = rank
        }
        return current
    }

    private val RANKS = listOf(
        Rank("Scout", 0),
        Rank("Runner", 3),
        Rank("Courier", 10),
        Rank("Pilot", 25),
        Rank("Captain", 50)
    )

    private const val PREFS = "fetch_haul"
    private const val KEY_IDS = "ids"
    private const val KEY_FILES = "files"
    private const val KEY_BYTES = "bytes"
    private const val KEY_POP = "pop"
}
