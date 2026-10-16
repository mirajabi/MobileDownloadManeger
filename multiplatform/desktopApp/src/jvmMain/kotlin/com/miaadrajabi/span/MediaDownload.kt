package com.miaadrajabi.span

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Uses the regular resumable engine for both tracks, then remuxes without re-encoding. */
internal suspend fun downloadMedia(
    stream: WatchStream, request: SpanRequest, directory: String, cancel: SpanCancel,
    downloader: OkHttpSpanDownloader, onProgress: (SpanProgress) -> Unit,
): SpanOutcome {
    val audio = stream.audio ?: return downloader.download(request.copy(url = stream.url, headers = request.headers + stream.headers), directory, cancel, onProgress)
    val ffmpeg = MediaTools.find("ffmpeg") ?: return SpanOutcome.Failed("The audio/video merger is missing. Install a complete Fetch build.")
    val name = SpanRules.safeFileName(request.fileName)
    val work = File(directory, "$name.span-media").apply { mkdirs() }
    val videoFile = File(work, "video.${stream.extension}")
    val audioFile = File(work, "audio.${audio.extension}")
    suspend fun track(track: WatchStream, file: File, offset: Long, otherBytes: Long?): SpanOutcome {
        if (cancel.cancelled) return SpanOutcome.Cancelled
        val marker = File(work, file.name + ".complete")
        if (file.isFile && marker.isFile && marker.readText() == "${track.formatId}:${file.length()}") return SpanOutcome.Saved(file.absolutePath, file.length())
        val partRequest = request.copy(url = track.url, fileName = file.name, headers = request.headers + track.headers, expectedChecksum = null)
        val result = downloader.download(partRequest, work.absolutePath, cancel) { progress ->
            onProgress(progress.copy(bytes = offset + progress.bytes, reservedBytes = offset + progress.reservedBytes,
                total = progress.total?.let { if (otherBytes == null) null else it + otherBytes }, parts = emptyList(), phase = TransferPhase.Receiving))
        }
        if (result is SpanOutcome.Saved) marker.writeText("${track.formatId}:${file.length()}")
        return result
    }
    val videoResult = track(stream, videoFile, 0, audio.bytes)
    if (videoResult !is SpanOutcome.Saved) return videoResult
    val audioResult = track(audio, audioFile, videoResult.bytes, videoResult.bytes)
    if (audioResult !is SpanOutcome.Saved) return audioResult
    if (cancel.cancelled) return SpanOutcome.Cancelled
    val total = videoResult.bytes + audioResult.bytes
    if (work.usableSpace < total + 1024 * 1024) return SpanOutcome.Failed("Not enough free space to combine the video and audio.")
    onProgress(SpanProgress(total, total, phase = TransferPhase.Finalize))
    val merged = File(work, "merged.mp4")
    try {
        runMediaProcess(listOf(ffmpeg.absolutePath, "-nostdin", "-hide_banner", "-loglevel", "error", "-y",
            "-i", videoFile.absolutePath, "-i", audioFile.absolutePath,
            "-map", "0:v:0", "-map", "1:a:0", "-c", "copy", "-movflags", "+faststart", merged.absolutePath), cancel, timeoutSeconds = 600)
        if (cancel.cancelled) return SpanOutcome.Cancelled
        check(merged.isFile && merged.length() > 0) { "The merger did not produce a file." }
        request.expectedChecksum?.takeIf { it.isNotBlank() }?.let { expected ->
            onProgress(SpanProgress(total, total, phase = TransferPhase.Verify))
            val digest = java.security.MessageDigest.getInstance(when (request.checksumAlgorithm) { ChecksumAlgorithm.MD5 -> "MD5"; ChecksumAlgorithm.SHA512 -> "SHA-512"; else -> "SHA-256" })
            merged.inputStream().use { input -> val buffer = ByteArray(65536); while (true) { val got = input.read(buffer); if (got < 0) break; digest.update(buffer, 0, got) } }
            check(digest.digest().joinToString("") { "%02x".format(it) }.equals(expected.trim(), true)) { "Checksum did not match." }
        }
        val destination = File(directory, name)
        try { Files.move(merged.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
        catch (_: java.nio.file.AtomicMoveNotSupportedException) { Files.move(merged.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING) }
        work.deleteRecursively()
        onProgress(SpanProgress(destination.length(), destination.length(), phase = TransferPhase.Ready))
        return SpanOutcome.Saved(destination.absolutePath, destination.length())
    } finally { merged.delete() }
}
