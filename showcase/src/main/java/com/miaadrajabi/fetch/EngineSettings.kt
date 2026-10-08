package com.miaadrajabi.fetch

import android.content.Context
import com.miaadrajabi.downloader.DownloadDestination

/**
 * Engine-wide choices. They are written with configureService, so the foreground
 * service reads them the next time it starts.
 */
data class EngineSettings(
    val chunkCount: Int = 4,
    val parallel: Boolean = true,
    val minChunkKb: Int = 256,
    val retryAttempts: Int = 5,
    val retryDelaySeconds: Int = 3,
    val backoffTenths: Int = 20,
    val channelName: String = "Fetch",
    val iconKey: String = ICON_ARROW,
    val showProgress: Boolean = true,
    val persistent: Boolean = true,
    val destination: String = DEST_AUTO,
    val folder: String = "Fetch",
    val publicDownloads: Boolean = false,
    val publicFolder: String = "",
    val overwrite: Boolean = true,
    val checkSpace: Boolean = true,
    val minFreeMb: Int = 10,
    val promptInstaller: Boolean = false,
    val verifySize: Boolean = true,
    val verifyChecksum: Boolean = true,
    val verifyApk: Boolean = true,
    val verifyType: Boolean = false,
    val verifySignature: Boolean = false,
    val useAlarm: Boolean = false,
    val watchClipboard: Boolean = false,
    val floatBubble: Boolean = false
) {
    fun notificationIcon(): Int {
        return when (iconKey) {
            ICON_TRAY -> R.drawable.ic_stat_tray
            ICON_BOLT -> R.drawable.ic_stat_bolt
            ICON_LAYERS -> R.drawable.ic_stat_layers
            else -> R.drawable.ic_stat_arrow
        }
    }

    fun destinations(): List<DownloadDestination> {
        val trimmed = folder.trim()
        return when (destination) {
            DEST_SCOPED -> listOf(DownloadDestination.Scoped(if (trimmed.isBlank()) "Fetch" else trimmed))
            DEST_CUSTOM -> {
                // A bare name such as "Fetch" is not a folder. File("Fetch") becomes /Fetch.
                if (trimmed.startsWith("/")) listOf(DownloadDestination.Custom(trimmed))
                else listOf(DownloadDestination.Auto)
            }
            else -> listOf(DownloadDestination.Auto)
        }
    }

    fun save(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_CHUNKS, chunkCount)
            .putBoolean(KEY_PARALLEL, parallel)
            .putInt(KEY_MIN_CHUNK, minChunkKb)
            .putInt(KEY_ATTEMPTS, retryAttempts)
            .putInt(KEY_DELAY, retryDelaySeconds)
            .putInt(KEY_BACKOFF, backoffTenths)
            .putString(KEY_CHANNEL, channelName)
            .putString(KEY_ICON, iconKey)
            .putBoolean(KEY_PROGRESS, showProgress)
            .putBoolean(KEY_PERSISTENT, persistent)
            .putString(KEY_DESTINATION, destination)
            .putString(KEY_FOLDER, folder)
            .putBoolean(KEY_PUBLIC, publicDownloads)
            .putString(KEY_PUBLIC_FOLDER, publicFolder)
            .putBoolean(KEY_OVERWRITE, overwrite)
            .putBoolean(KEY_SPACE, checkSpace)
            .putInt(KEY_FREE, minFreeMb)
            .putBoolean(KEY_INSTALLER, promptInstaller)
            .putBoolean(KEY_SIZE, verifySize)
            .putBoolean(KEY_CHECKSUM, verifyChecksum)
            .putBoolean(KEY_APK, verifyApk)
            .putBoolean(KEY_TYPE, verifyType)
            .putBoolean(KEY_SIGNATURE, verifySignature)
            .putBoolean(KEY_ALARM, useAlarm)
            .putBoolean(KEY_WATCH, watchClipboard)
            .putBoolean(KEY_BUBBLE, floatBubble)
            .apply()
    }

    companion object {
        const val DEST_AUTO = "auto"
        const val DEST_SCOPED = "scoped"
        const val DEST_CUSTOM = "custom"
        const val ICON_ARROW = "arrow"
        const val ICON_TRAY = "tray"
        const val ICON_BOLT = "bolt"
        const val ICON_LAYERS = "layers"

        private const val PREFS = "fetch_engine"
        private const val KEY_CHUNKS = "chunks"
        private const val KEY_PARALLEL = "parallel"
        private const val KEY_MIN_CHUNK = "minChunk"
        private const val KEY_ATTEMPTS = "attempts"
        private const val KEY_DELAY = "delay"
        private const val KEY_BACKOFF = "backoff"
        private const val KEY_CHANNEL = "channel"
        private const val KEY_ICON = "icon"
        private const val KEY_PROGRESS = "progress"
        private const val KEY_PERSISTENT = "persistent"
        private const val KEY_DESTINATION = "destination"
        private const val KEY_FOLDER = "folder"
        private const val KEY_PUBLIC = "public"
        private const val KEY_PUBLIC_FOLDER = "publicFolder"
        private const val KEY_OVERWRITE = "overwrite"
        private const val KEY_SPACE = "space"
        private const val KEY_FREE = "free"
        private const val KEY_INSTALLER = "installer"
        private const val KEY_SIZE = "size"
        private const val KEY_CHECKSUM = "checksum"
        private const val KEY_APK = "apk"
        private const val KEY_TYPE = "type"
        private const val KEY_SIGNATURE = "signature"
        private const val KEY_ALARM = "alarm"
        private const val KEY_WATCH = "watchClipboard"
        private const val KEY_BUBBLE = "floatBubble"

        fun load(context: Context): EngineSettings {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return EngineSettings(
                chunkCount = prefs.getInt(KEY_CHUNKS, 4).coerceIn(1, 8),
                parallel = prefs.getBoolean(KEY_PARALLEL, true),
                minChunkKb = prefs.getInt(KEY_MIN_CHUNK, 256).coerceAtLeast(64),
                retryAttempts = prefs.getInt(KEY_ATTEMPTS, 5).coerceIn(1, 8),
                retryDelaySeconds = prefs.getInt(KEY_DELAY, 3).coerceIn(1, 30),
                backoffTenths = prefs.getInt(KEY_BACKOFF, 20).coerceIn(10, 30),
                channelName = prefs.getString(KEY_CHANNEL, "Fetch") ?: "Fetch",
                iconKey = prefs.getString(KEY_ICON, ICON_ARROW) ?: ICON_ARROW,
                showProgress = prefs.getBoolean(KEY_PROGRESS, true),
                persistent = prefs.getBoolean(KEY_PERSISTENT, true),
                destination = prefs.getString(KEY_DESTINATION, DEST_AUTO) ?: DEST_AUTO,
                folder = prefs.getString(KEY_FOLDER, "Fetch") ?: "Fetch",
                publicDownloads = prefs.getBoolean(KEY_PUBLIC, false),
                publicFolder = prefs.getString(KEY_PUBLIC_FOLDER, "") ?: "",
                overwrite = prefs.getBoolean(KEY_OVERWRITE, true),
                checkSpace = prefs.getBoolean(KEY_SPACE, true),
                minFreeMb = prefs.getInt(KEY_FREE, 10).coerceAtLeast(1),
                promptInstaller = prefs.getBoolean(KEY_INSTALLER, false),
                verifySize = prefs.getBoolean(KEY_SIZE, true),
                verifyChecksum = prefs.getBoolean(KEY_CHECKSUM, true),
                verifyApk = prefs.getBoolean(KEY_APK, true),
                verifyType = prefs.getBoolean(KEY_TYPE, false),
                verifySignature = prefs.getBoolean(KEY_SIGNATURE, false),
                useAlarm = prefs.getBoolean(KEY_ALARM, false),
                watchClipboard = prefs.getBoolean(KEY_WATCH, false),
                floatBubble = prefs.getBoolean(KEY_BUBBLE, false)
            )
        }
    }
}
