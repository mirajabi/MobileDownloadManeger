@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.miaadrajabi.span

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import platform.CoreCrypto.CC_MD5
import platform.CoreCrypto.CC_MD5_DIGEST_LENGTH
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreCrypto.CC_SHA512
import platform.CoreCrypto.CC_SHA512_DIGEST_LENGTH
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSNumber
import platform.Foundation.NSObject
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequestReloadIgnoringLocalCacheData
import platform.Foundation.NSURLResponse
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDownloadDelegateProtocol
import platform.Foundation.NSURLSessionDownloadTask
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSUTF8StringEncoding
import platform.darwin.DISPATCH_TIME_FOREVER
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_wait

@OptIn(ExperimentalForeignApi::class)
class IosSpanDownloader : SpanDownloader {
    override suspend fun download(
        request: SpanRequest,
        directory: String,
        cancel: SpanCancel,
        onProgress: (SpanProgress) -> Unit,
    ): SpanOutcome {
        SpanRules.validationError(request)?.let { return SpanOutcome.Failed(it) }
        val manager = NSFileManager.defaultManager
        if (!manager.createDirectoryAtPath(directory, true, null, null) &&
            !manager.fileExistsAtPath(directory)
        ) {
            return SpanOutcome.Failed("Could not create $directory")
        }
        val file = "$directory/${SpanRules.safeFileName(request.fileName)}"
        val note = "$file.span"
        val window = SpanRules.window(request)
        val saved = readNote(note, request, window)
        if (!saved.matches) {
            remove(file)
            remove(note)
        }
        return transfer(request, window, file, note, cancel, onProgress, restartLeft = true)
    }

    private fun transfer(
        request: SpanRequest,
        window: ByteWindow,
        file: String,
        note: String,
        cancel: SpanCancel,
        onProgress: (SpanProgress) -> Unit,
        restartLeft: Boolean,
    ): SpanOutcome {
        if (cancel.cancelled) return SpanOutcome.Cancelled
        val local = length(file)
        if (SpanRules.isComplete(window, local)) return finish(request, file, local)
        val meta = readNote(note, request, window)
        val range = SpanRules.remoteRangeHeader(window, local)
        val fetched = try {
            fetch(request, range, local, window, cancel, onProgress)
        } catch (closed: Exception) {
            if (cancel.cancelled) return SpanOutcome.Cancelled
            return SpanOutcome.Failed(closed.message ?: "Network error")
        }
        if (cancel.cancelled) return SpanOutcome.Cancelled
        fetched.error?.let { return SpanOutcome.Failed(it) }
        val code = fetched.code
        val etag = header(fetched.headers, "ETag")
        val remoteTotal = remoteTotal(fetched.headers, fetched.bodyBytes)
        if (local > 0L && SpanRules.resumeAction(meta.etag, etag, meta.total, remoteTotal) == ResumeAction.DISCARD_PARTIAL) {
            remove(file)
            remove(note)
            remove(fetched.tempPath)
            if (!restartLeft) return SpanOutcome.Failed("The file on the server changed.")
            return transfer(request, window, file, note, cancel, onProgress, restartLeft = false)
        }
        when (code) {
            200 -> {
                if (local > 0L || window.start > 0L) {
                    remove(fetched.tempPath)
                    remove(file)
                    remove(note)
                    if (window.start > 0L) return SpanOutcome.Failed("The server ignored the requested byte window.")
                    if (!restartLeft) return SpanOutcome.Failed("The server ignored the resume request.")
                    return transfer(request, window, file, note, cancel, onProgress, restartLeft = false)
                }
                if (!takeBody(fetched.tempPath, file, append = false)) {
                    return SpanOutcome.Failed("Empty response.")
                }
            }
            206 -> {
                val announced = parseContentRangeStart(header(fetched.headers, "Content-Range"))
                val expected = window.start + local
                if (announced != null && announced != expected) {
                    remove(fetched.tempPath)
                    return SpanOutcome.Failed("Content-Range did not match the requested window.")
                }
                if (!takeBody(fetched.tempPath, file, append = true)) {
                    return SpanOutcome.Failed("Empty response.")
                }
            }
            416 -> {
                remove(fetched.tempPath)
                val expected = SpanRules.windowLength(window) ?: meta.total
                if (expected != null && local == expected) return finish(request, file, local)
                remove(file)
                remove(note)
                return SpanOutcome.Failed("The server rejected the byte range.")
            }
            else -> {
                remove(fetched.tempPath)
                return SpanOutcome.Failed("HTTP $code")
            }
        }
        val bytes = length(file)
        writeNote(note, request, window, etag, remoteTotal)
        val sliceTotal = SpanRules.windowLength(window)
        val known = sliceTotal ?: remoteTotal?.let { it - window.start }
        onProgress(SpanProgress(bytes, known))
        if (sliceTotal != null && bytes != sliceTotal) {
            return SpanOutcome.Failed("Saved $bytes bytes, expected $sliceTotal.")
        }
        return finish(request, file, bytes)
    }

    private fun finish(request: SpanRequest, file: String, bytes: Long): SpanOutcome {
        val expected = request.expectedChecksum?.trim().orEmpty()
        if (expected.isEmpty()) return SpanOutcome.Saved(file, bytes)
        val actual = digest(file, request.checksumAlgorithm)
        if (!actual.equals(expected, ignoreCase = true)) {
            remove(file)
            return SpanOutcome.Failed("Checksum did not match.")
        }
        return SpanOutcome.Saved(file, bytes)
    }

    private data class Fetched(
        val code: Int,
        val headers: Map<String, String>,
        val tempPath: String?,
        val bodyBytes: Long,
        val error: String?,
    )

    private fun fetch(
        request: SpanRequest,
        range: String?,
        local: Long,
        window: ByteWindow,
        cancel: SpanCancel,
        onProgress: (SpanProgress) -> Unit,
    ): Fetched {
        val url = NSURL.URLWithString(request.url.trim()) ?: return Fetched(0, emptyMap(), null, 0L, "Bad link.")
        val call = NSMutableURLRequest.requestWithURL(url)
        call.setCachePolicy(NSURLRequestReloadIgnoringLocalCacheData)
        call.setTimeoutInterval(60.0)
        call.setHTTPMethod("GET")
        SpanRules.strippedHeaders(request.headers).forEach { (name, value) ->
            call.setValue(value, forHTTPHeaderField = name)
        }
        if (range != null) call.setValue(range, forHTTPHeaderField = "Range")
        val gate = dispatch_semaphore_create(0)
        var tempPath: String? = null
        var failure: String? = null
        var response: NSURLResponse? = null
        val delegate = object : NSObject(), NSURLSessionDownloadDelegateProtocol {
            override fun URLSession(
                session: NSURLSession,
                downloadTask: NSURLSessionDownloadTask,
                didFinishDownloadingToURL: NSURL,
            ) {
                val source = didFinishDownloadingToURL.path ?: return
                val stable = "${platform.Foundation.NSTemporaryDirectory()}span-${platform.Foundation.NSUUID.UUID().UUIDString}.part"
                if (NSFileManager.defaultManager.copyItemAtPath(source, stable, null)) {
                    tempPath = stable
                }
            }

            override fun URLSession(
                session: NSURLSession,
                downloadTask: NSURLSessionDownloadTask,
                didWriteData: Long,
                totalBytesWritten: Long,
                totalBytesExpectedToWrite: Long,
            ) {
                val slice = SpanRules.windowLength(window)
                val known = when {
                    slice != null -> slice
                    totalBytesExpectedToWrite >= 0L -> local + totalBytesExpectedToWrite
                    else -> null
                }
                onProgress(SpanProgress(local + totalBytesWritten, known))
            }

            override fun URLSession(
                session: NSURLSession,
                task: NSURLSessionTask,
                didCompleteWithError: NSError?,
            ) {
                response = task.response
                failure = didCompleteWithError?.localizedDescription
                dispatch_semaphore_signal(gate)
            }
        }
        val queue = NSOperationQueue()
        val session = NSURLSession.sessionWithConfiguration(
            NSURLSessionConfiguration.defaultSessionConfiguration(),
            delegate,
            queue,
        )
        val task = session.downloadTaskWithRequest(call)
        if (cancel.cancelled) {
            session.invalidateAndCancel()
            return Fetched(0, emptyMap(), null, 0L, null)
        }
        task.resume()
        dispatch_semaphore_wait(gate, DISPATCH_TIME_FOREVER)
        session.finishTasksAndInvalidate()
        val http = response as? NSHTTPURLResponse
        val headers = mutableMapOf<String, String>()
        http?.allHeaderFields?.forEach { key, value ->
            headers[key.toString()] = value.toString()
        }
        return Fetched(
            code = http?.statusCode?.toInt() ?: 0,
            headers = headers,
            tempPath = tempPath,
            bodyBytes = tempPath?.let { length(it) } ?: 0L,
            error = failure,
        )
    }

    private fun takeBody(tempPath: String?, destination: String, append: Boolean): Boolean {
        val source = tempPath ?: return false
        val manager = NSFileManager.defaultManager
        if (!manager.fileExistsAtPath(source)) return false
        val data = NSData.dataWithContentsOfFile(source) ?: return false
        val wrote = if (!append || !manager.fileExistsAtPath(destination)) {
            if (manager.fileExistsAtPath(destination)) manager.removeItemAtPath(destination, null)
            data.writeToFile(destination, true)
        } else {
            val handle = platform.Foundation.NSFileHandle.fileHandleForWritingAtPath(destination)
            if (handle == null) {
                false
            } else {
                handle.seekToEndOfFile()
                handle.writeData(data)
                handle.closeFile()
                true
            }
        }
        remove(source)
        return wrote
    }

    private fun digest(path: String, algorithm: ChecksumAlgorithm): String {
        val data = NSData.dataWithContentsOfFile(path) ?: return ""
        val size = when (algorithm) {
            ChecksumAlgorithm.MD5 -> CC_MD5_DIGEST_LENGTH
            ChecksumAlgorithm.SHA256 -> CC_SHA256_DIGEST_LENGTH
            ChecksumAlgorithm.SHA512 -> CC_SHA512_DIGEST_LENGTH
        }
        return memScoped {
            val out = allocArray<UByteVar>(size.toInt())
            when (algorithm) {
                ChecksumAlgorithm.MD5 -> CC_MD5(data.bytes, data.length.convert(), out)
                ChecksumAlgorithm.SHA256 -> CC_SHA256(data.bytes, data.length.convert(), out)
                ChecksumAlgorithm.SHA512 -> CC_SHA512(data.bytes, data.length.convert(), out)
            }
            buildString(size.toInt() * 2) {
                for (index in 0 until size.toInt()) {
                    append(out[index].toUByte().toString(16).padStart(2, '0'))
                }
            }
        }
    }

    private fun remoteTotal(headers: Map<String, String>, bodyBytes: Long): Long? {
        val raw = header(headers, "Content-Range")
        if (raw != null) {
            raw.substringAfter('/', "").toLongOrNull()?.let { if (it >= 0L) return it }
        }
        val length = header(headers, "Content-Length")?.toLongOrNull()
        return length ?: bodyBytes.takeIf { it >= 0L }
    }

    private fun parseContentRangeStart(header: String?): Long? {
        if (header == null) return null
        return header.substringAfter("bytes ", "").substringBefore('-').toLongOrNull()
    }

    private fun header(headers: Map<String, String>, name: String): String? {
        return headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
    }

    private fun length(path: String): Long {
        val manager = NSFileManager.defaultManager
        if (!manager.fileExistsAtPath(path)) return 0L
        val attrs = manager.attributesOfItemAtPath(path, null) ?: return 0L
        val number = attrs[NSFileSize] as? NSNumber ?: return 0L
        return number.longLongValue
    }

    private fun remove(path: String?) {
        if (path == null) return
        val manager = NSFileManager.defaultManager
        if (manager.fileExistsAtPath(path)) manager.removeItemAtPath(path, null)
    }

    private data class Note(val matches: Boolean, val etag: String?, val total: Long?)

    private fun readNote(note: String, request: SpanRequest, window: ByteWindow): Note {
        if (!NSFileManager.defaultManager.fileExistsAtPath(note)) {
            return Note(matches = true, etag = null, total = null)
        }
        val text = NSString.stringWithContentsOfFile(note, NSUTF8StringEncoding, null) ?: ""
        val lines = text.lines()
        fun value(key: String) = lines.firstOrNull { it.startsWith("$key=") }?.substringAfter("=")
        return Note(
            matches = value("url") == request.url.trim() &&
                value("start") == window.start.toString() &&
                value("end") == (window.endInclusive?.toString() ?: ""),
            etag = value("etag")?.takeIf { it.isNotEmpty() },
            total = value("total")?.toLongOrNull(),
        )
    }

    private fun writeNote(
        note: String,
        request: SpanRequest,
        window: ByteWindow,
        etag: String?,
        total: Long?,
    ) {
        val text = listOf(
            "url=${request.url.trim()}",
            "start=${window.start}",
            "end=${window.endInclusive ?: ""}",
            "etag=${etag.orEmpty()}",
            "total=${total ?: ""}",
        ).joinToString("\n")
        (text as NSString).writeToFile(note, true, NSUTF8StringEncoding, null)
    }
}
