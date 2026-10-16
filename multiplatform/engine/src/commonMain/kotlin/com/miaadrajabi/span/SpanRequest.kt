package com.miaadrajabi.span

import kotlin.concurrent.Volatile

enum class ChecksumAlgorithm {
    MD5,
    SHA256,
    SHA512,
}

data class SpanRequest(
    val url: String,
    val fileName: String,
    val rangeStart: Long? = null,
    val rangeEndInclusive: Long? = null,
    val headers: Map<String, String> = emptyMap(),
    val expectedChecksum: String? = null,
    val checksumAlgorithm: ChecksumAlgorithm = ChecksumAlgorithm.SHA256,
    val connections: Int = 1,
    val speedLimitBytesPerSec: Long = 0L,
    val minPartBytes: Long = 256L * 1024L,
    val writePolicy: WritePolicy = WritePolicy.Auto,
    val latencyAware: Boolean = false,
)

data class ByteWindow(
    val start: Long,
    val endInclusive: Long?,
)

data class SpanPart(
    val index: Int,
    val downloaded: Long,
    val size: Long,
    val reserved: Long = downloaded,
)

enum class TransferPhase {
    Receiving,
    Finalize,
    Verify,
    Ready,
}

data class SpanProgress(
    val bytes: Long,
    val total: Long?,
    val parts: List<SpanPart> = emptyList(),
    val reservedBytes: Long = bytes,
    val phase: TransferPhase = TransferPhase.Receiving,
)

enum class StopReason {
    USER,
    NETWORK,
    HTTP,
}

enum class ResumeAction {
    KEEP_PARTIAL,
    DISCARD_PARTIAL,
}

sealed class SpanOutcome {
    data class Saved(val path: String, val bytes: Long) : SpanOutcome()
    data class Failed(val message: String, val retryAfterMs: Long? = null) : SpanOutcome()
    data object Cancelled : SpanOutcome()
}

@OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
class SpanCancel {
    private data class State(val cancelled: Boolean = false, val hooks: List<() -> Unit> = emptyList())
    private val state = kotlin.concurrent.atomics.AtomicReference(State())
    val cancelled: Boolean get() = state.load().cancelled

    fun listen(block: () -> Unit) {
        while (true) {
            val before = state.load()
            if (before.cancelled) { invoke(block); return }
            if (state.compareAndSet(before, before.copy(hooks = before.hooks + block))) return
        }
    }

    fun cancel() {
        while (true) {
            val before = state.load()
            if (before.cancelled) return
            if (state.compareAndSet(before, State(cancelled = true))) {
                before.hooks.forEach(::invoke)
                return
            }
        }
    }

    private fun invoke(hook: () -> Unit) {
        try { hook() } catch (_: Exception) { }
    }
}

interface SpanDownloader {
    suspend fun download(
        request: SpanRequest,
        directory: String,
        cancel: SpanCancel = SpanCancel(),
        onProgress: (SpanProgress) -> Unit = {},
    ): SpanOutcome
}
