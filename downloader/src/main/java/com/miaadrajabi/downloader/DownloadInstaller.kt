package com.miaadrajabi.downloader

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale

internal object DownloadInstaller {

    private const val TAG = "DownloadInstaller"

    fun maybePromptInstall(context: Context, file: File, config: InstallerConfig) {
        if (!config.promptOnCompletion) return
        if (!file.exists()) return
        val mimeType = if (config.autoDetectMimeType) {
            detectMimeType(file.name) ?: config.fallbackMimeType
        } else {
            config.fallbackMimeType
        }
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.downloader.provider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (error: ActivityNotFoundException) {
            Log.w(TAG, "No activity to handle installer intent", error)
        } catch (error: IllegalArgumentException) {
            Log.w(TAG, "Unable to generate uri for installer", error)
        }
    }

    private fun detectMimeType(fileName: String): String? {
        val lower = fileName.toLowerCase(Locale.US)
        return when {
            lower.endsWith(".apk") -> "application/vnd.android.package-archive"
            lower.endsWith(".apks") -> "application/vnd.android.package-archive"
            else -> null
        }
    }
}

