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
import android.util.Log

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
        callTracker: CallTracker? = null,
        existingChunkStates: List<ChunkStateData> = emptyList(),
        chunkStateUpdater: ((ChunkStateData) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        val totalBytes = fetchContentLength(request, callTracker)
        
        // Validate startOffset against actual file size
        val actualFileSize = resolution.file.length()
        val validatedOffset = if (startOffset > 0 && actualFileSize < startOffset) {
            // File is smaller than expected, restart from actual file size
            Log.w(TAG, "Resume offset ($startOffset) > file size ($actualFileSize), using file size")
            actualFileSize
        } else {
            startOffset
        }
        
        Log.d(TAG, "Download: totalBytes=$totalBytes, startOffset=$startOffset, actualFileSize=$actualFileSize, validatedOffset=$validatedOffset")
        val chunkPlans = ChunkPlanner.plan(totalBytes, config.chunking, validatedOffset, existingChunkStates)
        if (chunkPlans.isEmpty()) {
            Log.d(TAG, "No chunk plans generated; nothing to download.")
            return@withContext
        }
        Log.d(TAG, "Chunk plans: ${chunkPlans.map { "${it.index}:${it.start}-${it.endInclusive} resume=${it.resumeOffset}" }}")
        chunkStateUpdater?.let { updater ->
            chunkPlans.forEach { plan ->
                updater(plan.toState())
            }
        }
        RandomAccessFile(resolution.file, "rw").use { raf ->
            val dispatcher = ProgressDispatcher(listeners, handle, totalBytes, validatedOffset)
            val channel = raf.channel
            if (config.chunking.preferParallel && chunkPlans.size > 1) {
                coroutineScope {
                    val parallelism = min(config.chunking.chunkCount, chunkPlans.size)
                    val semaphore = Semaphore(parallelism)
                    val jobs = chunkPlans.map { plan ->
                        launch {
                            semaphore.withPermit {
                                downloadChunk(
                                    request,
                                    plan,
                                    channel,
                                    dispatcher,
                                    callTracker,
                                    chunkStateUpdater
                                )
                            }
                        }
                    }
                    jobs.joinAll()
                }
            } else {
                chunkPlans.forEach { plan ->
                    downloadChunk(
                        request,
                        plan,
                        channel,
                        dispatcher,
                        callTracker,
                        chunkStateUpdater
                    )
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
        plan: ChunkPlan,
        channel: java.nio.channels.FileChannel,
        dispatcher: ProgressDispatcher,
        callTracker: CallTracker?,
        chunkStateUpdater: ((ChunkStateData) -> Unit)?
    ) {
        val builder = baseRequestBuilder(request)
        val rangeStart = plan.resumeOffset
        if (plan.endInclusive != null) {
            builder.addHeader("Range", "bytes=${rangeStart}-${plan.endInclusive}")
        } else if (rangeStart > 0) {
            builder.addHeader("Range", "bytes=${rangeStart}-")
        }
        val httpRequest = builder.get().build()
        val call = client.newCall(httpRequest)
        callTracker?.register(call)
        call.execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Download failed for chunk ${plan.index} with code ${response.code}")
            }
            dispatcher.updateTotalIfAbsent(extractTotalBytes(response, plan))
            val body = response.body ?: throw IOException("Empty response body for chunk ${plan.index}")
            body.byteStream().use { source ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var position = rangeStart
                chunkStateUpdater?.invoke(plan.toState(position))
                var read = source.read(buffer)
                while (read != -1) {
                    val byteBuffer = ByteBuffer.wrap(buffer, 0, read)
                    while (byteBuffer.hasRemaining()) {
                        channel.write(byteBuffer, position)
                    }
                    dispatcher.onBytes(plan.index, read.toLong())
                    position += read
                    chunkStateUpdater?.invoke(plan.toState(position))
                    read = source.read(buffer)
                }
                val completionOffset = plan.endInclusive?.let { end ->
                    if (end == Long.MAX_VALUE) Long.MAX_VALUE else end + 1
                } ?: position
                chunkStateUpdater?.invoke(plan.toState(completionOffset))
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

    private data class ChunkPlan(
        val index: Int,
        val start: Long,
        val endInclusive: Long?,
        val resumeOffset: Long
    ) {
        fun toState(nextOffset: Long = resumeOffset): ChunkStateData =
            ChunkStateData(index, start, endInclusive, nextOffset)
    }

    private object ChunkPlanner {
        fun plan(
            totalBytes: Long?,
            config: ChunkingConfig,
            startOffset: Long = 0L,
            existingStates: List<ChunkStateData> = emptyList()
        ): List<ChunkPlan> {
            if (totalBytes == null || totalBytes <= 0L) {
                val adjustedStart = max(0L, startOffset)
                return listOf(ChunkPlan(index = 0, start = adjustedStart, endInclusive = null, resumeOffset = adjustedStart))
            }

            val ranges = buildRanges(totalBytes, config)
            return if (existingStates.isNotEmpty()) {
                planFromStates(ranges, existingStates)
            } else {
                planFromOffset(ranges, startOffset, totalBytes)
            }
        }

        private fun buildRanges(totalBytes: Long, config: ChunkingConfig): List<ChunkRange> {
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
            return ranges
        }

        private fun planFromOffset(
            ranges: List<ChunkRange>,
            startOffset: Long,
            totalBytes: Long
        ): List<ChunkPlan> {
            if (ranges.isEmpty()) return emptyList()
            if (startOffset <= 0) {
                return ranges.map { range ->
                    ChunkPlan(range.index, range.start, range.endInclusive, range.start)
                }
            }
            val plans = mutableListOf<ChunkPlan>()
            for (range in ranges) {
                val offset = startOffset
                if (range.endInclusive != null && offset > range.endInclusive) {
                    continue
                }
                val resume = if (offset >= range.start && (range.endInclusive == null || offset <= range.endInclusive)) {
                    offset
                } else {
                    range.start
                }
                plans += ChunkPlan(range.index, range.start, range.endInclusive, resume)
            }
            if (plans.isEmpty()) {
                val last = ranges.last()
                val fallbackStart = min(startOffset, totalBytes - 1)
                plans += ChunkPlan(last.index, fallbackStart, last.endInclusive, fallbackStart)
            }
            return plans
        }

        private fun planFromStates(
            ranges: List<ChunkRange>,
            existingStates: List<ChunkStateData>
        ): List<ChunkPlan> {
            val stateMap = existingStates.associateBy { it.index }
            val plans = mutableListOf<ChunkPlan>()
            ranges.forEach { range ->
                val state = stateMap[range.index]
                val resume = state?.nextOffset ?: range.start
                val endExclusive = range.endInclusive?.let { end ->
                    if (end == Long.MAX_VALUE) Long.MAX_VALUE else end + 1
                } ?: Long.MAX_VALUE
                val clamped = resume.coerceIn(range.start, endExclusive)
                if (clamped >= endExclusive) {
                    return@forEach
                }
                plans += ChunkPlan(range.index, range.start, range.endInclusive, clamped)
            }
            return plans
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
            private const val SMOOTHING_ALPHA = 0.65
            private const val MIN_INTERVAL_MS = 150L
            private const val MIN_BYTES_STEP = 16 * 1024L
        }
    }

    private fun extractTotalBytes(response: Response, range: ChunkPlan): Long? {
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
        private const val TAG = "ChunkedDownloader"
    }
}

