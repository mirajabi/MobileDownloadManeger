package com.miaadrajabi.downloader

import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong
import kotlin.jvm.Volatile
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * 1. Executes HTTP downloads with optional chunked range requests and progress callbacks.
 */
internal class ChunkedDownloader(
    private val client: OkHttpClient
) {

    suspend fun download(
        request: DownloadRequest,
        resolution: StorageResolution,
        handle: DownloadHandle,
        config: DownloadConfig,
        listeners: List<DownloadListener>,
        startOffset: Long = 0L,
        callTracker: CallTracker? = null
    ) = withContext(Dispatchers.IO) {
        val totalBytes = fetchContentLength(request, callTracker)
        val chunkRanges = ChunkPlanner.plan(totalBytes, config.chunking, startOffset)
        RandomAccessFile(resolution.file, "rw").use { raf ->
            val dispatcher = ProgressDispatcher(listeners, handle, totalBytes, startOffset)
            val channel = raf.channel
            if (config.chunking.preferParallel && chunkRanges.size > 1) {
                coroutineScope {
                    val parallelism = min(config.chunking.chunkCount, chunkRanges.size)
                    val semaphore = Semaphore(parallelism)
                    val jobs = chunkRanges.map { range ->
                        launch {
                            semaphore.withPermit {
                                downloadChunk(request, range, channel, dispatcher, callTracker)
                            }
                        }
                    }
                    jobs.joinAll()
                }
            } else {
                chunkRanges.forEach { range ->
                    downloadChunk(request, range, channel, dispatcher, callTracker)
                }
            }
        }
    }

    private fun fetchContentLength(request: DownloadRequest, callTracker: CallTracker?): Long? {
        val headRequest = baseRequestBuilder(request).head().build()
        return try {
            val call = client.newCall(headRequest)
            callTracker?.register(call)
            call.execute().use { response ->
                when {
                    response.isSuccessful -> response.header("Content-Length")?.toLongOrNull()
                    response.code == 405 || response.code == 501 -> null
                    else -> throw IOException("HEAD failed with code ${response.code}")
                }
            }
        } catch (_: IOException) {
            null
        }
    }

    private fun downloadChunk(
        request: DownloadRequest,
        range: ChunkRange,
        channel: java.nio.channels.FileChannel,
        dispatcher: ProgressDispatcher,
        callTracker: CallTracker?
    ) {
        val builder = baseRequestBuilder(request)
        if (range.endInclusive != null) {
            builder.addHeader("Range", "bytes=${range.start}-${range.endInclusive}")
        } else if (range.start > 0) {
            builder.addHeader("Range", "bytes=${range.start}-")
        }
        val httpRequest = builder.get().build()
        val call = client.newCall(httpRequest)
        callTracker?.register(call)
        call.execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Download failed for chunk ${range.index} with code ${response.code}")
            }
            dispatcher.updateTotalIfAbsent(extractTotalBytes(response, range))
            val body = response.body ?: throw IOException("Empty response body for chunk ${range.index}")
            body.byteStream().use { source ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var position = range.start
                var read = source.read(buffer)
                while (read != -1) {
                    val byteBuffer = ByteBuffer.wrap(buffer, 0, read)
                    while (byteBuffer.hasRemaining()) {
                        channel.write(byteBuffer, position)
                    }
                    dispatcher.onBytes(range.index, read.toLong())
                    position += read
                    read = source.read(buffer)
                }
            }
        }
    }

    private fun baseRequestBuilder(request: DownloadRequest): Request.Builder {
        val builder = Request.Builder().url(request.url)
        request.headers.forEach { (key, value) ->
            builder.addHeader(key, value)
        }
        return builder
    }

    private data class ChunkRange(
        val index: Int,
        val start: Long,
        val endInclusive: Long?
    )

    private object ChunkPlanner {
        fun plan(totalBytes: Long?, config: ChunkingConfig, startOffset: Long = 0L): List<ChunkRange> {
            if (totalBytes == null || totalBytes <= 0L) {
                val adjustedStart = max(0L, startOffset)
                return listOf(ChunkRange(index = 0, start = adjustedStart, endInclusive = null))
            }

            val minChunk = max(config.minChunkSizeBytes, 64 * 1024L)
            val idealChunkSize = max(minChunk, totalBytes / config.chunkCount)
            val estimatedCount = max(1, min(config.chunkCount, ceil(totalBytes / idealChunkSize.toDouble()).toInt()))

            val ranges = mutableListOf<ChunkRange>()
            var cursor = 0L
            for (index in 0 until estimatedCount) {
                if (cursor >= totalBytes) break
                val remaining = totalBytes - cursor
                val size = if (index == estimatedCount - 1) {
                    remaining
                } else {
                    min(idealChunkSize, remaining)
                }
                val start = cursor
                val end = if (index == estimatedCount - 1) totalBytes - 1 else (start + size - 1)
                ranges += ChunkRange(index = index, start = start, endInclusive = end)
                cursor = end + 1
            }

            if (startOffset <= 0) {
                return ranges
            }
            val filtered = mutableListOf<ChunkRange>()
            var adjustedIndex = 0
            for (range in ranges) {
                val offset = startOffset
                if (range.endInclusive != null && offset > range.endInclusive) {
                    continue
                }
                val newStart = max(range.start, offset)
                filtered += ChunkRange(
                    index = adjustedIndex++,
                    start = newStart,
                    endInclusive = range.endInclusive
                )
            }
            if (filtered.isEmpty()) {
                filtered += ChunkRange(
                    index = 0,
                    start = min(startOffset, totalBytes - 1),
                    endInclusive = totalBytes - 1
                )
            }
            return filtered
        }
    }

    private class ProgressDispatcher(
        private val listeners: List<DownloadListener>,
        private val handle: DownloadHandle,
        totalBytes: Long?,
        startOffset: Long
    ) {
        private val downloaded = AtomicLong(startOffset.coerceAtLeast(0L))
        private var lastTimestamp = System.currentTimeMillis()
        private var lastBytes = downloaded.get()
        private var smoothedSpeed = 0.0
        @Volatile private var totalBytesSnapshot: Long? = totalBytes
        private var lastEmissionTime = System.currentTimeMillis()

        fun updateTotalIfAbsent(value: Long?) {
            if (value == null || value <= 0) return
            val current = totalBytesSnapshot
            if (current == null || current <= 0) {
                synchronized(this) {
                    val inner = totalBytesSnapshot
                    if (inner == null || inner <= 0) {
                        totalBytesSnapshot = value
                    }
                }
            }
        }

        fun onBytes(chunkIndex: Int, delta: Long) {
            val total = downloaded.addAndGet(delta)
            val now = System.currentTimeMillis()
            val elapsedMs = (now - lastTimestamp).coerceAtLeast(1)
            val bytesDelta = total - lastBytes
            val rawSpeed = (bytesDelta * 1000.0) / elapsedMs
            smoothedSpeed = if (smoothedSpeed <= 0.0) rawSpeed else (SMOOTHING_ALPHA * rawSpeed) + ((1 - SMOOTHING_ALPHA) * smoothedSpeed)
            lastTimestamp = now
            lastBytes = total

            val totalBytes = totalBytesSnapshot
            val remaining = totalBytes?.let { max(0L, it - total) }
            val percent = totalBytes?.takeIf { it > 0L }?.let { ((total * 100) / it).toInt().coerceIn(0, 100) }

            val shouldEmit = now - lastEmissionTime >= MIN_INTERVAL_MS || bytesDelta >= MIN_BYTES_STEP || percent == 100 || totalBytes == null
            if (!shouldEmit) {
                return
            }
            lastEmissionTime = now

            val progress = DownloadProgress(
                bytesDownloaded = total,
                totalBytes = totalBytes,
                chunkIndex = chunkIndex,
                bytesPerSecond = smoothedSpeed.toLong().takeIf { it > 0 },
                remainingBytes = remaining,
                percent = percent
            )
            listeners.forEach { it.onProgress(handle, progress) }
        }

        companion object {
            private const val SMOOTHING_ALPHA = 0.55
            private const val MIN_INTERVAL_MS = 250L
            private const val MIN_BYTES_STEP = 32 * 1024L
        }
    }

    private fun extractTotalBytes(response: Response, range: ChunkRange): Long? {
        response.header("Content-Range")?.let { header ->
            val slashIndex = header.lastIndexOf('/')
            if (slashIndex != -1 && slashIndex + 1 < header.length) {
                header.substring(slashIndex + 1).toLongOrNull()?.let { return it }
            }
        }
        if (range.start == 0L) {
            val length = response.body?.contentLength() ?: -1
            if (length > 0) return length
        }
        return null
    }

    private companion object {
        private const val DEFAULT_BUFFER_SIZE = 16 * 1024
    }
}

