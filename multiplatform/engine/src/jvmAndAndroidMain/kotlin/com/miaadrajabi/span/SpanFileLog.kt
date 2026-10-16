package com.miaadrajabi.span

import java.io.File

class SpanFileLog(
    private val directory: File,
    private val maxBytes: Long = 256L * 1024L,
) {
    fun write(line: String) {
        if (!directory.exists() && !directory.mkdirs()) return
        val current = File(directory, "fetch.log")
        val safe = SpanRedaction.redact(line).replace('\n', ' ').replace('\r', ' ')
        current.appendText(safe + "\n")
        if (current.length() <= maxBytes) return
        val previous = File(directory, "fetch.log.1")
        if (previous.exists()) previous.delete()
        if (!current.renameTo(previous)) current.writeText("")
    }
}
