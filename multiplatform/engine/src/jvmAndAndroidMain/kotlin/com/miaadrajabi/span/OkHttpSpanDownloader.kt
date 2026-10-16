package com.miaadrajabi.span

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock

class OkHttpSpanDownloader(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
    private val freeSpace: (File) -> Long = { it.usableSpace },
    private val log: SpanFileLog? = null,
    private val disk: (File) -> DiskStamp = { JvmDisk.stamp(it) },
) : SpanDownloader {
    private val buffers = BufferLoan(4)
    private val calls = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
    @Volatile
    var lastContentType: String? = null
        private set

    @Volatile
    var appliedSpeedLimit: Long = 0L
        private set

    @Volatile
    private var headerMs: Long = 0L
    override suspend fun download(
        request: SpanRequest,
        directory: String,
        cancel: SpanCancel,
        onProgress: (SpanProgress) -> Unit,
    ): SpanOutcome {
        SpanRules.validationError(request)?.let { return noteFailure(request, it) }
        val dir = File(directory)
        if (!dir.exists() && !dir.mkdirs()) {
            return noteFailure(request, "Could not create $directory")
        }
        val file = File(dir, SpanRules.safeFileName(request.fileName))
        val note = File(dir, file.name + ".span")
        val window = SpanRules.window(request)
        val saved = readNote(note, request, window)
        if (!saved.matches) {
            file.delete()
            note.delete()
        }
        val outcome = try { if (request.connections > 1 && request.rangeStart == null && request.rangeEndInclusive == null) {
            downloadInParts(request, file, cancel, onProgress) ?: transfer(request, window, file, note, cancel, onProgress, restartLeft = true)
        } else {
            transfer(request, window, file, note, cancel, onProgress, restartLeft = true)
        } } catch (closed: Exception) {
            if (closed is kotlinx.coroutines.CancellationException) throw closed
            if (cancel.cancelled) SpanOutcome.Cancelled else SpanOutcome.Failed(closed.message ?: "The transfer could not finish.")
        }
        return if (outcome is SpanOutcome.Failed) noteFailure(request, outcome.message, outcome.retryAfterMs) else outcome
    }

    private fun noteFailure(request: SpanRequest, message: String, retryAfterMs: Long? = null): SpanOutcome.Failed {
        log?.write("failed file=${request.fileName} url=${request.url} reason=$message")
        return SpanOutcome.Failed(message, retryAfterMs)
    }

    private fun transfer(
        request: SpanRequest,
        window: ByteWindow,
        file: File,
        note: File,
        cancel: SpanCancel,
        onProgress: (SpanProgress) -> Unit,
        restartLeft: Boolean,
    ): SpanOutcome {
        if (cancel.cancelled) return SpanOutcome.Cancelled
        var local = if (file.exists()) file.length() else 0L
        if (SpanRules.isComplete(window, local)) {
            return finish(request, file, local, onProgress)
        }
        val meta = readNote(note, request, window)
        val range = SpanRules.remoteRangeHeader(window, local)
        val response = try {
            open(buildRequest(request, range), cancel)
        } catch (closed: Exception) {
            if (cancel.cancelled) return SpanOutcome.Cancelled
            return SpanOutcome.Failed(closed.message ?: "Network error")
        }
        response.use { body ->
            if (cancel.cancelled) return SpanOutcome.Cancelled
            val code = body.code
            val etag = body.header("ETag")
            lastContentType = body.header("Content-Type")
            val remoteTotal = contentLengthOf(body)
            if (local > 0L && SpanRules.resumeAction(meta.etag, etag, meta.total, remoteTotal) == ResumeAction.DISCARD_PARTIAL) {
                file.delete()
                note.delete()
                if (!restartLeft) return SpanOutcome.Failed("The file on the server changed.")
                return transfer(request, window, file, note, cancel, onProgress, restartLeft = false)
            }
            when (code) {
                200 -> {
                    if (local > 0L || window.start > 0L) {
                        body.close()
                        file.delete()
                        note.delete()
                        if (window.start > 0L) {
                            return SpanOutcome.Failed("The server ignored the requested byte window.")
                        }
                        if (!restartLeft) return SpanOutcome.Failed("The server ignored the resume request.")
                        return transfer(request, window, file, note, cancel, onProgress, restartLeft = false)
                    }
                    return writeBody(limited(request), window, file, note, body, append = false, etag, remoteTotal, cancel, onProgress)
                }
                206 -> {
                    val announced = parseContentRangeStart(body.header("Content-Range"))
                    val expected = window.start + local
                    if (announced != null && announced != expected) {
                        return SpanOutcome.Failed("Content-Range did not match the requested window.")
                    }
                    return writeBody(limited(request), window, file, note, body, append = true, etag, remoteTotal, cancel, onProgress)
                }
                416 -> {
                    val expected = SpanRules.windowLength(window) ?: meta.total
                    if (expected != null && local == expected) return finish(request, file, local, onProgress)
                    file.delete()
                    note.delete()
                    return SpanOutcome.Failed("The server rejected the byte range.")
                }
                429 -> {
                    val wait = RetryAfter.millis(body.header("Retry-After"))
                    return SpanOutcome.Failed("The server asked to wait.", wait)
                }
                else -> return SpanOutcome.Failed("HTTP $code")
            }
        }
    }

    private fun writeBody(
        request: SpanRequest,
        window: ByteWindow,
        file: File,
        note: File,
        response: Response,
        append: Boolean,
        etag: String?,
        remoteTotal: Long?,
        cancel: SpanCancel,
        onProgress: (SpanProgress) -> Unit,
    ): SpanOutcome {
        val source = response.body ?: return SpanOutcome.Failed("Empty response.")
        val sliceTotal = SpanRules.windowLength(window)
        val parent = file.parentFile ?: return SpanOutcome.Failed("The destination disconnected.")
        val already = if (append && file.exists()) file.length() else 0L
        val knownNeed = (sliceTotal ?: remoteTotal)?.minus(already)
        val reserve = if (knownNeed != null) knownNeed else 1024L * 1024L
        if (reserve > 0L && !fits(parent, reserve)) {
            return SpanOutcome.Failed("Not enough free space.")
        }
        val stamp = openedDisk(parent) ?: return SpanOutcome.Failed("The destination disconnected.")
        val every = DiskPolicy.interval(request.writePolicy, stamp.kind)
        val started = System.nanoTime()
        // Keep the validator even when the transfer is paused before its last byte.
        writeNote(note, request, window, etag, remoteTotal)
        var session = 0L
        var synced = already
        val buffer = buffers.borrow(cancel)
        try {
            RandomAccessFile(file, "rw").use { out ->
                if (append) out.seek(out.length()) else out.setLength(0L)
                val stream = source.byteStream()
                while (true) {
                    if (cancel.cancelled) {
                        stream.close()
                        return SpanOutcome.Cancelled
                    }
                    val read = try {
                        stream.read(buffer)
                    } catch (closed: Exception) {
                        if (cancel.cancelled) return SpanOutcome.Cancelled
                        return SpanOutcome.Failed(closed.message ?: "Network error")
                    }
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    session += read
                    val known = sliceTotal ?: remoteTotal?.let { it - window.start }
                    if (session % every < read) {
                        val problem = diskProblem(parent, stamp)
                        if (problem != null) return SpanOutcome.Failed(problem)
                        out.fd.sync()
                        synced = out.length()
                        val remain = (sliceTotal ?: remoteTotal)?.minus(synced)
                        val need = if (remain != null) remain else 1024L * 1024L
                        if (need > 0L && !fits(parent, need)) {
                            return SpanOutcome.Failed("Not enough free space.")
                        }
                    }
                    pace(request.speedLimitBytesPerSec, started, session, cancel)
                    onProgress(SpanProgress(synced, known, reservedBytes = out.length()))
                }
            }
        } finally {
            buffers.recycle(buffer)
        }
        val bytes = file.length()
        writeNote(note, request, window, etag, remoteTotal)
        if (sliceTotal != null && bytes != sliceTotal) {
            return SpanOutcome.Failed("Saved $bytes bytes, expected $sliceTotal.")
        }
        return finish(request, file, bytes, onProgress)
    }

    private fun downloadInParts(
        request: SpanRequest,
        file: File,
        cancel: SpanCancel,
        onProgress: (SpanProgress) -> Unit,
    ): SpanOutcome? {
        val total = try {
            probeLength(request, cancel)
        } catch (closed: Exception) {
            if (cancel.cancelled) return SpanOutcome.Cancelled
            return null
        } ?: return null
        if (total < request.minPartBytes.coerceAtLeast(64L * 1024L)) return null
        val bounds = SpanRules.partBounds(total, request.connections)
        if (bounds.size < 2) return null
        val plan = File(file.parentFile, file.name + ".span-plan")
        val data = File(file.parentFile, file.name + ".span-data")
        val flushed = loadPlan(plan, data, file, bounds, total)
        val live = flushed.copyOf()
        val already = flushed.sum()
        val parent = file.parentFile ?: return SpanOutcome.Failed("Not enough free space.")
        if (!fits(parent, total - already)) return SpanOutcome.Failed("Not enough free space.")
        val stamp = openedDisk(parent) ?: return SpanOutcome.Failed("The destination disconnected.")
        val every = DiskPolicy.interval(request.writePolicy, stamp.kind)
        val executor = Executors.newFixedThreadPool(bounds.size)
        val failure = AtomicLong(0)
        val gate = Any()
        fun pictured(phase: TransferPhase) = SpanProgress(
            flushed.sum(),
            total,
            bounds.mapIndexed { index, window ->
                SpanPart(index, flushed[index], SpanRules.windowLength(window) ?: 0L, live[index])
            },
            live.sum(),
            phase,
        )
        fun remember(channel: FileChannel, index: Int): String? {
            synchronized(gate) {
                val problem = diskProblem(parent, stamp)
                if (problem != null) {
                    failure.set(if (problem == "The destination disk changed.") 3L else 4L)
                    return problem
                }
                channel.force(false)
                flushed[index] = live[index]
                writePlan(plan, total, bounds.size, flushed)
                return null
            }
        }
        FileChannel.open(data.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
            fun report() {
                onProgress(pictured(TransferPhase.Receiving))
            }
            report()
            bounds.forEachIndexed { index, window ->
                executor.submit {
                    try {
                        downloadPart(limited(request), window, channel, parent, flushed[index], every, cancel, { got ->
                            synchronized(gate) { live[index] = got }
                            report()
                        }) {
                            remember(channel, index)?.let { throw IllegalStateException(it) }
                        }
                    } catch (closed: Exception) {
                        if (!cancel.cancelled && failure.get() == 0L) {
                            failure.set(when (closed.message) {
                                "Not enough free space." -> 2L
                                "The destination disk changed." -> 3L
                                "The destination disconnected." -> 4L
                                else -> 1L
                            })
                        }
                    } finally {
                        try { remember(channel, index) } catch (_: Exception) { failure.compareAndSet(0, 4) }
                    }
                }
            }
            executor.shutdown()
            while (!executor.isTerminated) {
                // Never interrupt a worker sharing this channel. FileChannel closes the
                // entire channel on interruption, including the other writers and flush.
                // SpanCancel closes network calls; pacing also checks it every 50 ms.
                executor.awaitTermination(200, TimeUnit.MILLISECONDS)
            }
            if (cancel.cancelled) return SpanOutcome.Cancelled
            if (failure.get() == 2L) return SpanOutcome.Failed("Not enough free space.")
            if (failure.get() == 3L) return SpanOutcome.Failed("The destination disk changed.")
            if (failure.get() == 4L) return SpanOutcome.Failed("The destination disconnected.")
            if (failure.get() != 0L) return SpanOutcome.Failed("A download part failed.")
            if (flushed.sum() != total) return SpanOutcome.Failed("The parts did not cover the file.")
            channel.force(false)
        }
        diskProblem(parent, stamp)?.let { return SpanOutcome.Failed(it) }
        onProgress(pictured(TransferPhase.Finalize))
        val checksum = request.expectedChecksum?.trim().orEmpty()
        if (checksum.isNotEmpty()) onProgress(pictured(TransferPhase.Verify))
        val mismatch = checksumMismatch(request, data)
        if (mismatch != null) {
            data.delete()
            plan.delete()
            return SpanOutcome.Failed(mismatch)
        }
        if (!publish(data, file)) return SpanOutcome.Failed("Could not finish the file on this volume.")
        plan.delete()
        onProgress(SpanProgress(total, total, emptyList(), total, TransferPhase.Ready))
        return SpanOutcome.Saved(file.absolutePath, total)
    }

    private fun downloadPart(
        request: SpanRequest,
        window: ByteWindow,
        channel: FileChannel,
        directory: File,
        already: Long,
        flushEvery: Long,
        cancel: SpanCancel,
        onPart: (Long) -> Unit,
        onFlushed: () -> Unit,
    ) {
        val length = SpanRules.windowLength(window) ?: return
        var have = already.coerceAtLeast(0L)
        if (have >= length) return
        val buffer = buffers.borrow(cancel)
        try {
            open(buildRequest(request, SpanRules.remoteRangeHeader(window, have)), cancel).use { response ->
                if (response.code != 206 && response.code != 200) {
                    throw IllegalStateException("HTTP ${response.code}")
                }
                if (response.code == 200) throw IllegalStateException("The server ignored the byte range.")
                val body = response.body ?: throw IllegalStateException("Empty part")
                val started = System.nanoTime()
                var session = 0L
                var sinceFlush = 0L
                val stream = body.byteStream()
                while (true) {
                    if (cancel.cancelled) {
                        stream.close()
                        return
                    }
                    val read = stream.read(buffer)
                    if (read < 0) break
                    writeAt(channel, window.start + have, buffer, read)
                    have += read
                    session += read
                    sinceFlush += read
                    onPart(have)
                    if (sinceFlush >= flushEvery) {
                        onFlushed()
                        sinceFlush = 0L
                        val remain = length - have
                        if (remain > 0L && !fits(directory, remain)) {
                            throw IllegalStateException("Not enough free space.")
                        }
                    }
                    pace(request.speedLimitBytesPerSec, started, session, cancel)
                }
            }
        } finally {
            buffers.recycle(buffer)
        }
    }

    private fun writeAt(channel: FileChannel, position: Long, bytes: ByteArray, length: Int) {
        var offset = 0
        var cursor = position
        while (offset < length) {
            val wrote = channel.write(ByteBuffer.wrap(bytes, offset, length - offset), cursor)
            if (wrote <= 0) throw IllegalStateException("Could not write the download.")
            offset += wrote
            cursor += wrote
        }
    }

    private fun probeLength(request: SpanRequest, cancel: SpanCancel): Long? {
        open(buildRequest(request, "bytes=0-0"), cancel).use { response ->
            if (response.code != 206) return null
            val total = response.header("Content-Range")?.substringAfter('/', "")?.toLongOrNull()
            return total?.takeIf { it > 0L }
        }
    }

    private fun partFile(file: File, index: Int): File {
        return File(file.parentFile, file.name + ".span-part-$index")
    }

    private fun open(request: Request, cancel: SpanCancel): Response {
        val seen = mutableListOf<String>()
        var current = request
        val began = System.nanoTime()
        var timed = false
        while (true) {
            val call = calls.newCall(current)
            cancel.listen { call.cancel() }
            val response = call.execute()
            if (!timed) {
                headerMs = (System.nanoTime() - began) / 1_000_000L
                timed = true
            }
            if (response.code !in 300..399) return response
            val location = response.header("Location")
            val next = if (location.isNullOrBlank()) null else current.url.resolve(location)
            response.close()
            if (next == null) throw java.io.IOException("Redirect had no location")
            val refusal = Redirects.refuse(seen, next.toString())
            if (refusal != null) throw java.io.IOException(refusal)
            seen += next.toString()
            current = current.newBuilder().url(next).build()
        }
    }

    private fun openedDisk(directory: File): DiskStamp? {
        return try {
            disk(directory)
        } catch (_: Exception) {
            null
        }
    }

    private fun diskProblem(directory: File, stamp: DiskStamp): String? {
        val now = openedDisk(directory) ?: return "The destination disconnected."
        return if (now.id != stamp.id) "The destination disk changed." else null
    }

    private fun fits(directory: File, need: Long): Boolean {
        if (need <= 0L) return true
        val free = freeSpace(directory)
        if (free < 0L) return true
        return free >= need
    }

    private fun loadPlan(plan: File, data: File, file: File, bounds: List<ByteWindow>, total: Long): LongArray {
        val count = bounds.size
        val blank = LongArray(count)
        if (!plan.exists() || !PartialImport.known(plan.readLines().firstOrNull())) {
            bounds.indices.forEach { index ->
                val legacy = partFile(file, index)
                if (!legacy.exists()) return@forEach
                val length = SpanRules.windowLength(bounds[index]) ?: 0L
                val have = legacy.length().coerceAtMost(length)
                if (have > 0L) {
                    FileChannel.open(data.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
                        legacy.inputStream().use { input ->
                            val buffer = ByteArray(64 * 1024)
                            var placed = 0L
                            while (placed < have) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                val take = minOf(read.toLong(), have - placed).toInt()
                                writeAt(channel, bounds[index].start + placed, buffer, take)
                                placed += take
                            }
                        }
                    }
                    blank[index] = have
                }
                legacy.delete()
            }
            if (blank.any { it > 0L }) writePlan(plan, total, count, blank)
            return blank
        }
        val lines = plan.readLines()
        val savedTotal = lines.getOrNull(1)?.toLongOrNull()
        val savedCount = lines.getOrNull(2)?.toIntOrNull()
        if (savedTotal != total || savedCount != count) {
            data.delete()
            bounds.indices.forEach { partFile(file, it).delete() }
            return blank
        }
        return LongArray(count) { index ->
            lines.getOrNull(4 + index)?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        }
    }

    private fun writePlan(plan: File, total: Long, count: Int, durable: LongArray) {
        val body = buildString {
            append("v2\n")
            append(total)
            append('\n')
            append(count)
            append("\n\n")
            durable.forEach { append(it).append('\n') }
        }
        val temp = File(plan.parentFile, plan.name + ".tmp")
        temp.writeText(body)
        if (!temp.renameTo(plan)) {
            plan.writeText(body)
            temp.delete()
        }
    }

    private fun checksumMismatch(request: SpanRequest, file: File): String? {
        val expected = request.expectedChecksum?.trim().orEmpty()
        if (expected.isEmpty()) return null
        val actual = digest(file, request.checksumAlgorithm)
        return if (actual.equals(expected, ignoreCase = true)) null else "Checksum did not match."
    }

    private fun publish(data: File, destination: File): Boolean {
        val backup = File(destination.parentFile, destination.name + ".span-old")
        if (destination.exists() && !destination.renameTo(backup)) return false
        if (!data.renameTo(destination)) {
            if (backup.exists()) backup.renameTo(destination)
            return false
        }
        if (backup.exists()) backup.delete()
        return true
    }

    private fun limited(request: SpanRequest): SpanRequest {
        val cut = LatencyLimit.apply(request.latencyAware, request.speedLimitBytesPerSec, headerMs)
        appliedSpeedLimit = cut
        return if (cut == request.speedLimitBytesPerSec) request else request.copy(speedLimitBytesPerSec = cut)
    }

    private fun pace(limit: Long, started: Long, written: Long, cancel: SpanCancel) {
        if (limit <= 0L || written <= 0L) return
        val allowed = written * 1_000_000_000L / limit
        while (!cancel.cancelled) {
            val wait = (allowed - (System.nanoTime() - started)) / 1_000_000L
            if (wait <= 2L) return
            Thread.sleep(minOf(wait, 50L))
        }
    }

    private fun finish(
        request: SpanRequest,
        file: File,
        bytes: Long,
        onProgress: (SpanProgress) -> Unit,
    ): SpanOutcome {
        val expected = request.expectedChecksum?.trim().orEmpty()
        val known = bytes.takeIf { it > 0L }
        if (expected.isEmpty()) {
            onProgress(SpanProgress(bytes, known, phase = TransferPhase.Finalize))
            return SpanOutcome.Saved(file.absolutePath, bytes)
        }
        onProgress(SpanProgress(bytes, known ?: bytes, phase = TransferPhase.Verify))
        val actual = digest(file, request.checksumAlgorithm)
        if (!actual.equals(expected, ignoreCase = true)) {
            file.delete()
            return SpanOutcome.Failed("Checksum did not match.")
        }
        onProgress(SpanProgress(bytes, known ?: bytes, phase = TransferPhase.Ready))
        return SpanOutcome.Saved(file.absolutePath, bytes)
    }

    private fun buildRequest(request: SpanRequest, range: String?): Request {
        val builder = Request.Builder().url(request.url.trim()).get()
        SpanRules.strippedHeaders(request.headers).forEach { (name, value) ->
            builder.header(name, value)
        }
        if (range != null) builder.header("Range", range)
        return builder.build()
    }

    private fun contentLengthOf(response: Response): Long? {
        val raw = response.header("Content-Range")
        if (raw != null) {
            val total = raw.substringAfter('/', "")
            total.toLongOrNull()?.let { if (it >= 0L) return it }
        }
        val length = response.body?.contentLength() ?: -1L
        return if (length >= 0L) length else null
    }

    private fun parseContentRangeStart(header: String?): Long? {
        if (header == null) return null
        val bytes = header.substringAfter("bytes ", "").substringBefore('-')
        return bytes.toLongOrNull()
    }

    private fun digest(file: File, algorithm: ChecksumAlgorithm): String {
        val name = when (algorithm) {
            ChecksumAlgorithm.MD5 -> "MD5"
            ChecksumAlgorithm.SHA256 -> "SHA-256"
            ChecksumAlgorithm.SHA512 -> "SHA-512"
        }
        val digest = MessageDigest.getInstance(name)
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private data class Note(val matches: Boolean, val etag: String?, val total: Long?)

    private fun readNote(note: File, request: SpanRequest, window: ByteWindow): Note {
        if (!note.exists()) return Note(matches = true, etag = null, total = null)
        val lines = note.readLines()
        fun value(key: String) = lines.firstOrNull { it.startsWith("$key=") }?.substringAfter("=")
        val sameUrl = value("url") == request.url.trim()
        val sameStart = value("start") == window.start.toString()
        val sameEnd = value("end") == (window.endInclusive?.toString() ?: "")
        return Note(
            matches = sameUrl && sameStart && sameEnd,
            etag = value("etag")?.takeIf { it.isNotEmpty() },
            total = value("total")?.toLongOrNull(),
        )
    }

    private fun writeNote(
        note: File,
        request: SpanRequest,
        window: ByteWindow,
        etag: String?,
        total: Long?,
    ) {
        note.writeText(
            listOf(
                "url=${request.url.trim()}",
                "start=${window.start}",
                "end=${window.endInclusive ?: ""}",
                "etag=${etag.orEmpty()}",
                "total=${total ?: ""}",
            ).joinToString("\n"),
        )
    }
}

private class BufferLoan(count: Int) {
    private val lock = ReentrantLock()
    private val ready = lock.newCondition()
    private val free = ArrayDeque<ByteArray>()

    init {
        repeat(count.coerceIn(1, 8)) { free.add(ByteArray(64 * 1024)) }
    }

    fun borrow(cancel: SpanCancel): ByteArray {
        lock.lock()
        try {
            while (free.isEmpty()) {
                if (cancel.cancelled) throw IllegalStateException("Cancelled")
                ready.await(200, TimeUnit.MILLISECONDS)
            }
            return free.removeFirst()
        } finally {
            lock.unlock()
        }
    }

    fun recycle(buffer: ByteArray) {
        lock.lock()
        try {
            free.addLast(buffer)
            ready.signalAll()
        } finally {
            lock.unlock()
        }
    }
}
