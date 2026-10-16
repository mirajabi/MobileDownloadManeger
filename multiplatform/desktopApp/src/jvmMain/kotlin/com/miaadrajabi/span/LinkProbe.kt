package com.miaadrajabi.span

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.Base64
import java.util.concurrent.TimeUnit

data class ProbeReport(
    val code: Int,
    val bytes: Long?,
    val fileName: String,
    val contentType: String,
    val authKind: String,
    val freeBytes: Long,
    val message: String,
) {
    val reachable: Boolean get() = code in 200..299
    val fits: Boolean get() = bytes == null || freeBytes <= 0L || bytes <= freeBytes
}

object LinkProbe {
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun authorization(user: String, password: String, token: String): String = when {
        token.isNotBlank() -> {
            val value = token.trim()
            if (value.startsWith("Bearer ", true) || value.startsWith("Basic ", true)) value else "Bearer $value"
        }
        user.isNotBlank() -> "Basic " + Base64.getEncoder().encodeToString("$user:$password".toByteArray(Charsets.UTF_8))
        else -> ""
    }

    fun inspect(url: String, folder: String, user: String, password: String, token: String): ProbeReport {
        val free = freeBytes(folder)
        val header = authorization(user, password, token)
        return try {
            val response = open(url, header)
            response.use { body ->
                val kind = authKind(body.header("WWW-Authenticate").orEmpty(), body.code)
                val size = totalBytes(body.code, body.header("Content-Range"), body.header("Content-Length"))
                val name = fileName(body.header("Content-Disposition"), url)
                val type = body.header("Content-Type").orEmpty().substringBefore(";").trim()
                val message = when {
                    kind == "basic" -> "This site asks for a username and password."
                    kind == "bearer" -> "This site asks for a token."
                    kind == "custom" -> "This site asks for a sign-in. Add the header it expects, then check again."
                    body.code !in 200..299 -> "The link answered ${body.code}."
                    size != null && free > 0L && size > free -> "The file is larger than the free space in this folder."
                    size != null -> "The link is reachable."
                    else -> "The link is reachable. The server did not say how large the file is."
                }
                ProbeReport(body.code, size, name, type, kind, free, message)
            }
        } catch (_: Exception) {
            ProbeReport(0, null, "", "", "", free, "The link could not be opened.")
        }
    }

    private fun open(url: String, authorization: String) = client.newCall(request(url, authorization, head = true)).execute().let { head ->
        if (head.code == 405 || head.code == 501) {
            head.close()
            client.newCall(request(url, authorization, head = false)).execute()
        } else {
            head
        }
    }

    private fun request(url: String, authorization: String, head: Boolean): Request {
        val builder = Request.Builder().url(url).header("User-Agent", "Fetch").header("Range", "bytes=0-0")
        if (authorization.isNotBlank()) builder.header("Authorization", authorization)
        return if (head) builder.head().build() else builder.get().build()
    }

    private fun authKind(challenge: String, code: Int): String {
        if (code != 401 && code != 403) return ""
        val text = challenge.lowercase()
        return when {
            text.contains("basic") -> "basic"
            text.contains("bearer") -> "bearer"
            challenge.isBlank() -> "basic"
            else -> "custom"
        }
    }

    private fun totalBytes(code: Int, range: String?, length: String?): Long? {
        val total = range?.substringAfter("/", "")?.substringBefore(" ")?.toLongOrNull()
        if (total != null && total > 0L) return total
        val bytes = length?.toLongOrNull()
        return if (code == 200 && bytes != null && bytes > 0L) bytes else null
    }

    private fun fileName(disposition: String?, url: String): String {
        val named = disposition?.substringAfter("filename=", "")?.substringBefore(";")?.trim()?.trim('"')
        if (!named.isNullOrBlank()) return SpanRules.safeFileName(named)
        val tail = url.substringBefore('?').substringAfterLast('/').ifBlank { "download.bin" }
        return SpanRules.safeFileName(tail)
    }

    private fun freeBytes(folder: String): Long {
        val file = File(folder)
        val dir = when {
            file.isDirectory -> file
            file.parentFile != null -> file.parentFile
            else -> File(System.getProperty("user.home"))
        }
        return dir.usableSpace
    }
}
