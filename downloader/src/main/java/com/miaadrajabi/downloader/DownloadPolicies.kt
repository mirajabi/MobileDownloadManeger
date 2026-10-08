package com.miaadrajabi.downloader

import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * Decisions that keep a partial download from being mixed with a different artifact.
 * A healthy in-progress file is resumed. A failed checksum or a changed strong
 * validator throws that partial file away and starts again at byte zero.
 */
internal object DownloadRecovery {
    const val QUEUED = "queued"
    const val RUNNING = "running"
    const val RETRY_WAIT = "retry_wait"
    const val PAUSED = "paused"
    const val FAILED = "failed"
    const val CANCELLED = "cancelled"
    const val COMPLETED = "completed"

    val RESUMABLE_AFTER_RESTART = setOf(QUEUED, RUNNING, RETRY_WAIT)
}

internal enum class DownloadStability {
    Healthy,
    Abandoned,
    Finished
}

internal fun stabilityOf(state: String): DownloadStability {
    return when (state) {
        DownloadRecovery.QUEUED,
        DownloadRecovery.RUNNING,
        DownloadRecovery.RETRY_WAIT,
        DownloadRecovery.PAUSED -> DownloadStability.Healthy
        DownloadRecovery.FAILED,
        DownloadRecovery.CANCELLED -> DownloadStability.Abandoned
        else -> DownloadStability.Finished
    }
}

internal enum class Admission {
    ReuseActive,
    RejectBusy,
    ReplaceFromZero,
    StartFresh
}

/**
 * Same id and same artifact keeps the running download.
 * A second id is rejected while that owner is healthy.
 * An abandoned owner is dropped so the new request can start from byte zero.
 */
internal fun admitDownload(
    sameIdState: String?,
    sameIdSameArtifact: Boolean,
    pathOwnerState: String?,
    pathOwnerIsSameId: Boolean
): Admission {
    if (sameIdState != null) {
        when (stabilityOf(sameIdState)) {
            DownloadStability.Healthy -> {
                return if (sameIdSameArtifact) Admission.ReuseActive else Admission.RejectBusy
            }
            DownloadStability.Abandoned -> return Admission.ReplaceFromZero
            DownloadStability.Finished -> Unit
        }
    }
    if (pathOwnerState != null && !pathOwnerIsSameId) {
        return when (stabilityOf(pathOwnerState)) {
            DownloadStability.Healthy -> Admission.RejectBusy
            DownloadStability.Abandoned -> Admission.ReplaceFromZero
            DownloadStability.Finished -> Admission.StartFresh
        }
    }
    return Admission.StartFresh
}

internal fun sameArtifact(existing: DownloadRequest, incoming: DownloadRequest): Boolean {
    return existing.url == incoming.url &&
        existing.fileName == incoming.fileName &&
        existing.expectedChecksum == incoming.expectedChecksum &&
        existing.checksumAlgorithm == incoming.checksumAlgorithm &&
        existing.rangeStart == incoming.rangeStart &&
        existing.rangeEndInclusive == incoming.rangeEndInclusive
}

/**
 * The remote byte window a request keeps. Both ends are inclusive.
 * A null start is byte zero. A null end reads through the remote file.
 */
internal data class ByteWindow(
    val start: Long,
    val endInclusive: Long?
) {
    fun isWholeFile(): Boolean = start == 0L && endInclusive == null

    fun remoteStart(localOffset: Long): Long = start + localOffset

    fun remoteEnd(localEndInclusive: Long?): Long? = localEndInclusive?.let { start + it }

    fun sliceLength(remoteTotal: Long?): Long? {
        if (endInclusive != null) {
            val span = endInclusive - start
            if (span == Long.MAX_VALUE) {
                throw IllegalArgumentException("Requested range is too large")
            }
            return span + 1L
        }
        if (remoteTotal == null || start == 0L) return remoteTotal
        if (remoteTotal <= start) return 0L
        return remoteTotal - start
    }
}

internal fun byteWindowOf(start: Long?, endInclusive: Long?): ByteWindow {
    val origin = start ?: 0L
    if (origin < 0L) {
        throw IllegalArgumentException("rangeStart must be zero or greater")
    }
    if (endInclusive != null && endInclusive < origin) {
        throw IllegalArgumentException("rangeEndInclusive must be greater than or equal to rangeStart")
    }
    return ByteWindow(origin, endInclusive)
}

internal fun canonicalPath(file: File): String {
    return try {
        file.canonicalPath
    } catch (error: IOException) {
        file.absolutePath
    }
}

internal data class ArtifactValidators(
    val strongEtag: String? = null,
    val lastModified: String? = null,
    val totalBytes: Long? = null
) {
    fun ifRangeValue(): String? = strongEtag ?: lastModified
}

/**
 * Weak validators are not byte identity. A missing validator does not forbid resume;
 * the final checksum is what rejects a mixed file.
 */
internal fun strongEtag(header: String?): String? {
    if (header == null) return null
    val trimmed = header.trim()
    if (trimmed.isEmpty()) return null
    if (trimmed.startsWith("W/") || trimmed.startsWith("w/")) return null
    return trimmed
}

internal enum class ValidatorDecision {
    ContinuePartial,
    RestartFromZero
}

internal fun validatorDecision(
    saved: ArtifactValidators?,
    observedStrongEtag: String?,
    observedTotal: Long?
): ValidatorDecision {
    if (saved == null) return ValidatorDecision.ContinuePartial
    if (saved.strongEtag != null && observedStrongEtag != null && saved.strongEtag != observedStrongEtag) {
        return ValidatorDecision.RestartFromZero
    }
    if (saved.totalBytes != null && observedTotal != null && saved.totalBytes != observedTotal && observedTotal > 0L) {
        return ValidatorDecision.RestartFromZero
    }
    return ValidatorDecision.ContinuePartial
}

internal data class ContentRangeBytes(
    val start: Long,
    val endInclusive: Long,
    val total: Long?
)

internal fun parseContentRange(header: String?): ContentRangeBytes? {
    if (header == null) return null
    val trimmed = header.trim()
    if (!trimmed.startsWith("bytes ")) return null
    val spec = trimmed.substring("bytes ".length)
    val slash = spec.indexOf('/')
    if (slash <= 0) return null
    val bounds = spec.substring(0, slash)
    val totalPart = spec.substring(slash + 1)
    val dash = bounds.indexOf('-')
    if (dash <= 0) return null
    val start = bounds.substring(0, dash).toLongOrNull() ?: return null
    val end = bounds.substring(dash + 1).toLongOrNull() ?: return null
    if (end < start) return null
    val total = if (totalPart == "*") {
        null
    } else {
        totalPart.toLongOrNull() ?: return null
    }
    if (total != null && total < end + 1) return null
    return ContentRangeBytes(start, end, total)
}

internal enum class RangeOutcome {
    WriteAtOffset,
    RestartFromZero,
    AlreadyComplete
}

/**
 * A ranged response is written only when status and Content-Range describe that
 * exact interval. Anything else restarts a single full download at byte zero.
 */
internal fun classifyRangeResponse(
    requestedStart: Long,
    requestedEndInclusive: Long?,
    statusCode: Int,
    contentRangeHeader: String?,
    knownTotal: Long?,
    localFileLength: Long
): RangeOutcome {
    if (statusCode == 416) {
        if (knownTotal != null && knownTotal > 0L && localFileLength == knownTotal && requestedStart > 0L) {
            return RangeOutcome.AlreadyComplete
        }
        return RangeOutcome.RestartFromZero
    }
    if (statusCode == 206) {
        val range = parseContentRange(contentRangeHeader) ?: return RangeOutcome.RestartFromZero
        if (range.start != requestedStart) return RangeOutcome.RestartFromZero
        if (requestedEndInclusive != null && range.endInclusive != requestedEndInclusive) {
            return RangeOutcome.RestartFromZero
        }
        if (knownTotal != null && range.total != null && range.total != knownTotal) {
            return RangeOutcome.RestartFromZero
        }
        return RangeOutcome.WriteAtOffset
    }
    if (statusCode == 200) {
        return RangeOutcome.RestartFromZero
    }
    return RangeOutcome.RestartFromZero
}

internal class SingleStreamRestartException(message: String) : IOException(message)

internal class LocalArtifactCompleteException : IOException("Local file already matches the remote length")

internal fun chunkBytesAreComplete(state: ChunkStateData): Boolean {
    val end = state.endInclusive ?: return false
    return state.nextOffset == end + 1 && state.nextOffset >= state.start
}

/**
 * Complete means every saved interval is contiguous from byte zero.
 * File length is not used, because a sparse file can look full while it still has holes.
 */
internal fun coverageIsComplete(chunks: List<ChunkStateData>, totalBytes: Long?): Boolean {
    if (chunks.isEmpty()) return false
    val sorted = chunks.sortedBy { it.start }
    if (sorted.first().start != 0L) return false
    var cursor = 0L
    for (chunk in sorted) {
        if (chunk.start != cursor) return false
        val end = chunk.endInclusive ?: return false
        if (chunk.nextOffset != end + 1) return false
        if (chunk.start > end) return false
        cursor = end + 1
    }
    if (totalBytes != null && cursor != totalBytes) return false
    return true
}

internal fun advanceWritePosition(position: Long, wrote: Int): Long {
    if (wrote <= 0) {
        throw IOException("File write made no progress")
    }
    return position + wrote
}

internal fun validatedChecksum(checksum: String?, algorithm: ChecksumAlgorithm): String? {
    if (checksum == null) return null
    val hex = checksum.trim().toLowerCase(Locale.US)
    if (hex.isEmpty()) return null
    val expectedLength = when (algorithm) {
        ChecksumAlgorithm.MD5 -> 32
        ChecksumAlgorithm.SHA256 -> 64
        ChecksumAlgorithm.SHA512 -> 128
    }
    val malformed = hex.length != expectedLength || hex.any { char ->
        char !in '0'..'9' && char !in 'a'..'f'
    }
    if (malformed) {
        throw IllegalArgumentException("Checksum is not a valid ${algorithm.name} hex string")
    }
    return hex
}

internal fun parseChecksumAlgorithm(name: String?, checksumPresent: Boolean): ChecksumAlgorithm {
    if (name == null || name.isEmpty()) return ChecksumAlgorithm.SHA256
    val parsed = runCatching { ChecksumAlgorithm.valueOf(name) }.getOrNull()
    if (parsed == null) {
        if (checksumPresent) {
            throw IllegalArgumentException("Unknown checksum algorithm: $name")
        }
        return ChecksumAlgorithm.SHA256
    }
    return parsed
}
