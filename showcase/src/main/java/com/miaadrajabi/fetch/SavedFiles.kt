package com.miaadrajabi.fetch

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.Locale

/**
 * Finds a finished download in the folders Fetch is allowed to read.
 */
object SavedFiles {

    fun find(context: Context, fileName: String): File? {
        if (fileName.isBlank()) return null
        val dirs = directories(context)
        for (dir in dirs) {
            val file = File(dir, fileName)
            if (file.isFile) return file
        }
        return null
    }

    fun directories(context: Context): List<File> {
        val dirs = ArrayList<File>()
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { dirs.add(it) }
        context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)?.let { dirs.add(it) }
        context.getExternalFilesDir(null)?.let { root ->
            dirs.add(root)
            val children = try {
                root.listFiles()
            } catch (error: SecurityException) {
                null
            }
            if (children != null) {
                for (child in children) {
                    if (child.isDirectory) dirs.add(child)
                }
            }
        }
        dirs.add(File(context.filesDir, "downloads"))
        val settings = EngineSettings.load(context)
        if (settings.destination == EngineSettings.DEST_CUSTOM && settings.folder.startsWith("/")) {
            dirs.add(File(settings.folder))
        }
        val pub = try {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        } catch (error: Exception) {
            null
        }
        if (pub != null) {
            val folder = settings.publicFolder.trim().trim('/')
            if (folder.isNotEmpty()) dirs.add(File(pub, folder))
            dirs.add(pub)
            val nested = try {
                pub.listFiles()
            } catch (error: SecurityException) {
                null
            }
            if (nested != null) {
                for (child in nested) {
                    if (child.isDirectory) dirs.add(child)
                }
            }
        }
        return dirs
    }

    fun mime(name: String): String {
        val ext = name.substringAfterLast('.', "").toLowerCase(Locale.US)
        return when (ext) {
            "apk" -> "application/vnd.android.package-archive"
            "zip" -> "application/zip"
            "pdf" -> "application/pdf"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            else -> "application/octet-stream"
        }
    }

    fun export(activity: Activity, source: File, destination: Uri, move: Boolean, onDone: (String) -> Unit) {
        val main = Handler(Looper.getMainLooper())
        Thread {
            val message = try {
                val output = activity.contentResolver.openOutputStream(destination)
                    ?: throw IOException("The destination could not be opened")
                output.use { out ->
                    FileInputStream(source).use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            out.write(buffer, 0, count)
                        }
                    }
                }
                if (!move) {
                    "Copied"
                } else if (source.delete()) {
                    "Moved"
                } else {
                    "Copied, but the original file is still in the download folder"
                }
            } catch (error: Exception) {
                error.message ?: "Could not write the file"
            }
            main.post { onDone(message) }
        }.start()
    }

    fun filePath(uri: Uri): String? {
        val docId = try {
            DocumentsContract.getDocumentId(uri)
        } catch (error: Exception) {
            return null
        }
        val colon = docId.indexOf(':')
        if (colon < 0) return null
        val volume = docId.substring(0, colon)
        val relative = docId.substring(colon + 1)
        if (relative.isEmpty()) return null
        val root = if (volume == "primary") {
            Environment.getExternalStorageDirectory()
        } else {
            File("/storage/$volume")
        }
        return File(root, relative).absolutePath
    }

    fun folderUri(fileUri: Uri): Uri? {
        val docId = try {
            DocumentsContract.getDocumentId(fileUri)
        } catch (error: Exception) {
            return null
        }
        if (!docId.contains("/")) return null
        val authority = fileUri.authority ?: return null
        return DocumentsContract.buildDocumentUri(authority, docId.substringBeforeLast("/"))
    }
}
