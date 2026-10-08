package com.miaadrajabi.fetch

import java.util.Locale

/**
 * Pulls http and https addresses out of a pasted block.
 * People drop several links at once, one per line or separated by spaces.
 */
object LinkParser {

    fun parse(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        val found = ArrayList<String>()
        val tokens = raw.split(Regex("\\s+"))
        for (token in tokens) {
            val cleaned = token.trim().trimEnd(',', ';', ')', ']')
            val lower = cleaned.toLowerCase(Locale.US)
            val isWeb = lower.startsWith("http://") || lower.startsWith("https://")
            if (isWeb && cleaned.length > "http://".length && !found.contains(cleaned)) {
                found.add(cleaned)
            }
        }
        return found
    }

    fun fileNameFrom(url: String): String {
        val path = url.substringBefore('?').substringBefore('#')
        val raw = path.substringAfterLast('/')
        val cleaned = raw.replace(Regex("[^A-Za-z0-9._-]"), "_").trim('_')
        if (cleaned.isBlank() || !cleaned.contains('.')) {
            val host = path.substringAfter("://").substringBefore('/').substringBefore(':')
            val hostName = host.replace(Regex("[^A-Za-z0-9._-]"), "")
            if (hostName.isNotBlank()) return "$hostName.bin"
            return "download.bin"
        }
        return cleaned
    }

    fun uniqueNames(urls: List<String>, preferredSingleName: String): List<String> {
        if (urls.size == 1) {
            val preferred = sanitizeFileName(preferredSingleName)
            if (preferred.isNotBlank()) return listOf(preferred)
        }
        val used = HashSet<String>()
        val names = ArrayList<String>(urls.size)
        for (url in urls) {
            val base = fileNameFrom(url)
            var candidate = base
            var suffix = 2
            while (!used.add(candidate.toLowerCase(Locale.US))) {
                val dot = base.lastIndexOf('.')
                candidate = if (dot > 0) {
                    base.substring(0, dot) + "-" + suffix + base.substring(dot)
                } else {
                    base + "-" + suffix
                }
                suffix += 1
            }
            names.add(candidate)
        }
        return names
    }

    fun sanitizeFileName(raw: String): String {
        return raw.trim().replace(Regex("[\\\\/]+"), "_").replace(Regex("\\s+"), "_")
    }
}
