package com.miaadrajabi.fetch

import android.app.PendingIntent
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import java.security.MessageDigest

/**
 * Reads http links from the clipboard and remembers the last text that was offered,
 * so the same copy is not asked about twice.
 */
object ClipOffer {

    fun present(activity: android.app.Activity) {
        if (!EngineSettings.load(activity).watchClipboard) return
        val raw = rawText(activity)
        val links = LinkParser.parse(raw)
        if (links.isEmpty() || !isNew(activity, raw)) return
        remember(activity, raw)
        activity.startActivity(offerIntent(activity, links))
    }

    fun readLinks(context: Context): List<String> {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = try {
            manager.primaryClip
        } catch (error: SecurityException) {
            null
        } ?: return emptyList()
        if (clip.itemCount <= 0) return emptyList()
        val text = clip.getItemAt(0).coerceToText(context)?.toString().orEmpty()
        return LinkParser.parse(text)
    }

    fun rawText(context: Context): String {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = try {
            manager.primaryClip
        } catch (error: SecurityException) {
            null
        } ?: return ""
        if (clip.itemCount <= 0) return ""
        return clip.getItemAt(0).coerceToText(context)?.toString().orEmpty()
    }

    fun isNew(context: Context, raw: String): Boolean {
        if (raw.isBlank()) return false
        val seen = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "")
        return seen != digest(raw)
    }

    fun remember(context: Context, raw: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, digest(raw))
            .apply()
    }

    fun offerIntent(context: Context, links: List<String>): Intent {
        return Intent(context, OfferActivity::class.java)
            .putStringArrayListExtra(OfferActivity.EXTRA_LINKS, ArrayList(links))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }

    fun contentIntent(context: Context, links: List<String>): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or immutable()
        return PendingIntent.getActivity(context, 8, offerIntent(context, links), flags)
    }

    private fun digest(raw: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        val builder = StringBuilder(bytes.size * 2)
        for (byte in bytes) {
            val value = byte.toInt() and 0xff
            if (value < 16) builder.append('0')
            builder.append(Integer.toHexString(value))
        }
        return builder.toString()
    }

    private fun immutable(): Int {
        return if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
    }

    private const val PREFS = "fetch_clip"
    private const val KEY = "last"
}
