package com.miaadrajabi.span

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** A downloadable file, optionally paired with an audio track for lossless merging. */
enum class StreamKind { Audio, Video, Both }
data class WatchStream(
    val label: String, val url: String, val extension: String, val kind: StreamKind,
    val formatId: String = "", val headers: Map<String, String> = emptyMap(),
    val bytes: Long? = null, val height: Int = 0, val codec: String = "", val audio: WatchStream? = null,
) {
    val canPreview get() = extension in setOf("m4a", "mp3", "wav") || (extension == "mp4" && (codec.isBlank() || codec.startsWith("avc")))
}
data class WatchPreview(
    val pageUrl: String, val title: String, val thumbnail: String?, val streams: List<WatchStream>,
    val note: String, val author: String = "", val duration: Double = 0.0,
)

object WatchPage {
    private fun onHost(url: String, domain: String): Boolean {
        val host = url.toHttpUrlOrNull()?.host ?: return false
        return host == domain || host.endsWith(".$domain")
    }
    fun isWatch(url: String) = onHost(url, "youtube.com") || onHost(url, "youtu.be")
    fun isInstagram(url: String) = onHost(url, "instagram.com")
    fun isMedia(url: String) = isWatch(url) || isInstagram(url)
    fun refererFor(url: String) = when {
        isWatch(url) || onHost(url, "googlevideo.com") -> "https://www.youtube.com/"
        isInstagram(url) || onHost(url, "cdninstagram.com") -> "https://www.instagram.com/"
        else -> url.toHttpUrlOrNull()?.let { "${it.scheme}://${it.host}/" }.orEmpty()
    }
    fun agentFor(@Suppress("UNUSED_PARAMETER") url: String) = "Mozilla/5.0"

    suspend fun load(url: String, cancel: SpanCancel? = null): WatchPreview = withContext(Dispatchers.IO) {
        require(url.toHttpUrlOrNull() != null) { "Enter a complete HTTP or HTTPS link." }
        if (!isMedia(url)) {
            val ext = url.substringBefore('?').substringAfterLast('.', "mp4").lowercase()
            return@withContext WatchPreview(url, "Direct media", null,
                listOf(WatchStream("Original file", url, ext, if (ext in setOf("mp3", "m4a", "wav")) StreamKind.Audio else StreamKind.Both)), "Preview the file, then add it to your downloads.")
        }
        val extractor = MediaTools.find("yt-dlp") ?: error("The video engine is missing. Install a complete Fetch build, or place yt-dlp on PATH.")
        val command = mutableListOf(extractor.absolutePath, "--ignore-config", "--no-plugin-dirs", "--no-playlist", "--no-warnings", "--no-progress", "--no-cache-dir", "--socket-timeout", "20", "--retries", "1", "--skip-download", "--dump-single-json")
        MediaTools.find("deno")?.let { command += listOf("--js-runtimes", "deno:${it.absolutePath}") }
        command += listOf("--", url)
        parseMediaInfo(runMediaProcess(command, cancel), url)
    }

    suspend fun verify(stream: WatchStream): WatchStream {
      val verified = runInterruptible(Dispatchers.IO) {
        val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
        val request = Request.Builder().url(stream.url).apply { stream.headers.forEach { (k, v) -> header(k, v) } }.header("Range", "bytes=0-1").build()
        client.newCall(request).execute().use { response ->
            check(response.code == 200 || response.code == 206) { "The media server returned HTTP ${response.code}. Refresh the link and try again." }
            val type = response.header("Content-Type").orEmpty().lowercase()
            check(!type.contains("text/html") && !type.contains("json") && !type.contains("mpegurl")) { "This link does not point to a downloadable media file." }
            check(response.body?.byteStream()?.read()?.let { it >= 0 } == true) { "The media file is empty." }
            stream.copy(url = response.request.url.toString())
        }
      }
      return if (stream.audio == null) verified else verified.copy(audio = verify(stream.audio))
    }
}

internal object MediaTools {
    fun find(name: String): File? {
        val exe = name + if (System.getProperty("os.name").startsWith("Windows")) ".exe" else ""
        val roots = listOfNotNull(System.getProperty("fetch.media.tools"), System.getProperty("compose.application.resources.dir")?.let { "$it/media-tools" }) +
            listOf("desktopApp/build/media-resources/common/media-tools", "build/media-resources/common/media-tools", "/opt/homebrew/bin", "/usr/local/bin", "/usr/bin") +
            System.getenv("PATH").orEmpty().split(File.pathSeparator)
        return roots.map { File(it, exe) }.firstOrNull { it.isFile && it.canExecute() }
    }
}

/** No shell, browser cookies, plugins or user config; terminate on cancellation and cap output. */
internal suspend fun runMediaProcess(command: List<String>, cancel: SpanCancel? = null, timeoutSeconds: Long = 90): String = withContext(Dispatchers.IO) {
    val output = File.createTempFile("fetch-media-", ".json")
    val errors = File.createTempFile("fetch-media-", ".log")
    var process: Process? = null
    try {
        process = ProcessBuilder(command).redirectOutput(output).redirectError(errors).start()
        val running = process
        cancel?.listen { running.destroy() }
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
        while (running.isAlive) {
            currentCoroutineContext().ensureActive()
            if (cancel?.cancelled == true) throw CancellationException("Media lookup paused")
            check(System.nanoTime() < deadline) { "The site took too long to respond. Try again." }
            check(output.length() < 16_000_000 && errors.length() < 1_000_000) { "The site returned too much information." }
            delay(80)
        }
        if (cancel?.cancelled == true) throw CancellationException("Media lookup paused")
        check(running.exitValue() == 0) {
            val message = errors.readText().lineSequence().lastOrNull { it.startsWith("ERROR:") }?.removePrefix("ERROR:")?.trim()
            message?.replace(Regex("https?://\\S+"), "[link]")?.take(400) ?: "This video is unavailable. Check the link or try a public video."
        }
        output.readText()
    } finally {
        process?.let { if (it.isAlive) { it.destroy(); if (!it.waitFor(500, TimeUnit.MILLISECONDS)) it.destroyForcibly() } }
        output.delete(); errors.delete()
    }
}

internal fun parseMediaInfo(text: String, page: String): WatchPreview {
    val info = Json.parseToJsonElement(text).jsonObject
    fun JsonObject.string(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
    fun JsonObject.number(key: String) = (this[key] as? JsonPrimitive)?.doubleOrNull ?: 0.0
    fun JsonObject.headers(): Map<String, String> = (this["http_headers"] as? JsonObject)?.mapNotNull { (k, v) ->
        if (k.lowercase() in setOf("user-agent", "referer", "origin", "accept", "accept-language")) k to v.jsonPrimitive.content else null
    }?.toMap().orEmpty()
    val formats = (info["formats"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty().ifEmpty { listOf(info) }
    val streams = formats.mapNotNull { format ->
        val url = format.string("url")
        val protocol = format.string("protocol")
        if (url.toHttpUrlOrNull() == null || (protocol.isNotBlank() && protocol !in setOf("http", "https")) || format["has_drm"]?.jsonPrimitive?.booleanOrNull == true || format.containsKey("fragments")) return@mapNotNull null
        val video = format.string("vcodec").let { it.isNotBlank() && it != "none" }
        val audio = format.string("acodec").let { it.isNotBlank() && it != "none" }
        if (!video && !audio) return@mapNotNull null
        val kind = if (video && audio) StreamKind.Both else if (video) StreamKind.Video else StreamKind.Audio
        val height = format.number("height").toInt()
        val ext = format.string("ext").ifBlank { if (video) "mp4" else "m4a" }
        val quality = if (video) (if (height > 0) "${height}p" else "Original") else "${format.number("abr").toInt()} kbps"
        val track = when (kind) { StreamKind.Both -> "Video + audio"; StreamKind.Video -> "Video only"; StreamKind.Audio -> "Audio only" }
        WatchStream("$quality · ${ext.uppercase()} · $track", url, ext, kind, format.string("format_id"), info.headers() + format.headers(),
            (format.number("filesize").takeIf { it > 0 } ?: format.number("filesize_approx")).toLong().takeIf { it > 0 }, height, format.string("vcodec"))
    }.distinctBy { it.formatId to it.url }.sortedWith(compareBy<WatchStream> { if (it.kind == StreamKind.Both) 0 else if (it.kind == StreamKind.Video) 1 else 2 }.thenBy { !it.canPreview }.thenByDescending { it.height })
    check(streams.isNotEmpty()) { "No direct media files are available for this link. The site may require sign-in or only offer segmented streams." }
    val audio = streams.filter { it.kind == StreamKind.Audio && it.extension == "m4a" }.maxByOrNull { it.bytes ?: 0 }
    val paired = if (audio == null) emptyList() else streams.filter { it.kind == StreamKind.Video && it.extension == "mp4" }.map { video ->
        video.copy(label = "${video.height}p · MP4 · Video + audio", kind = StreamKind.Both, formatId = "${video.formatId}+${audio.formatId}",
            bytes = if (video.bytes != null && audio.bytes != null) video.bytes + audio.bytes else null, audio = audio)
    }
    val choices = (paired + streams).sortedWith(compareBy<WatchStream> { if (it.kind == StreamKind.Both) 0 else if (it.kind == StreamKind.Video) 1 else 2 }.thenBy { !it.canPreview }.thenByDescending { it.height })
    return WatchPreview(page, info.string("title").ifBlank { "Video" }, info.string("thumbnail").takeIf { it.toHttpUrlOrNull() != null }, choices,
        if (choices.any { it.kind == StreamKind.Both }) "Choose a quality, preview it, then download the original file."
        else "This video offers separate tracks. Video-only files do not include sound; audio can be downloaded separately.",
        info.string("uploader"), info.number("duration"))
}
