package com.miaadrajabi.span

object SpanRules {
    fun validationError(request: SpanRequest): String? {
        val url = request.url.trim()
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            return "Only http and https links are accepted."
        }
        val start = request.rangeStart
        val end = request.rangeEndInclusive
        if (start != null && start < 0L) {
            return "Range start cannot be negative."
        }
        if (end != null && end < 0L) {
            return "Range end cannot be negative."
        }
        val windowStart = start ?: 0L
        if (end != null && end < windowStart) {
            return "Range end is before the start."
        }
        return checksumError(request.expectedChecksum, request.checksumAlgorithm)
    }

    fun window(request: SpanRequest): ByteWindow {
        return ByteWindow(
            start = request.rangeStart ?: 0L,
            endInclusive = request.rangeEndInclusive,
        )
    }

    fun windowLength(window: ByteWindow): Long? {
        val end = window.endInclusive ?: return null
        return end - window.start + 1L
    }

    /**
     * Remote Range for the bytes still missing from the local window.
     * A null result means a full GET from the start of the artifact.
     */
    fun partBounds(total: Long, connections: Int): List<ByteWindow> {
        if (total <= 0L) return listOf(ByteWindow(0L, null))
        val count = connections.coerceIn(1, 32)
        if (count == 1) return listOf(ByteWindow(0L, total - 1L))
        val size = total / count
        if (size <= 0L) return listOf(ByteWindow(0L, total - 1L))
        return (0 until count).map { index ->
            val start = index * size
            val end = if (index == count - 1) total - 1L else start + size - 1L
            ByteWindow(start, end)
        }
    }

    fun remoteRangeHeader(window: ByteWindow, localBytes: Long): String? {
        val remoteStart = window.start + localBytes
        val remoteEnd = window.endInclusive
        if (remoteStart == 0L && remoteEnd == null) return null
        if (remoteEnd == null) return "bytes=$remoteStart-"
        return "bytes=$remoteStart-$remoteEnd"
    }

    fun isComplete(window: ByteWindow, localBytes: Long): Boolean {
        val length = windowLength(window) ?: return false
        return localBytes >= length && length >= 0L
    }

    fun safeFileName(raw: String): String {
        val cleaned = raw.trim().replace(Regex("[^A-Za-z0-9._-]"), "_").trim('_', '.')
        if (cleaned.isEmpty()) return "download.bin"
        return if (cleaned.length <= 80) cleaned else cleaned.take(80)
    }

    fun strippedHeaders(headers: Map<String, String>): Map<String, String> {
        return headers.filterKeys { name ->
            !name.equals("Range", ignoreCase = true) &&
                !name.equals("Content-Range", ignoreCase = true)
        }
    }

    fun checksumError(raw: String?, algorithm: ChecksumAlgorithm): String? {
        if (raw == null) return null
        val hex = raw.trim()
        if (hex.isEmpty()) return null
        val expected = when (algorithm) {
            ChecksumAlgorithm.MD5 -> 32
            ChecksumAlgorithm.SHA256 -> 64
            ChecksumAlgorithm.SHA512 -> 128
        }
        if (hex.length != expected || !hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
            return "Checksum must be $expected hexadecimal characters."
        }
        return null
    }

    fun isWeakEtag(value: String): Boolean {
        return value.trim().startsWith("W/", ignoreCase = true)
    }

    fun strongEtag(value: String?): String? {
        if (value == null) return null
        val trimmed = value.trim()
        if (trimmed.isEmpty() || isWeakEtag(trimmed)) return null
        return trimmed
    }

    fun resumeAction(
        savedEtag: String?,
        responseEtag: String?,
        savedTotal: Long?,
        responseTotal: Long?,
    ): ResumeAction {
        val saved = strongEtag(savedEtag)
        val fresh = strongEtag(responseEtag)
        if (saved != null && fresh != null && !saved.equals(fresh, ignoreCase = true)) {
            return ResumeAction.DISCARD_PARTIAL
        }
        if (
            savedTotal != null && responseTotal != null &&
            savedTotal > 0L && responseTotal > 0L &&
            savedTotal != responseTotal
        ) {
            return ResumeAction.DISCARD_PARTIAL
        }
        return ResumeAction.KEEP_PARTIAL
    }

    fun shouldRetry(reason: StopReason): Boolean {
        return reason != StopReason.USER
    }
}
