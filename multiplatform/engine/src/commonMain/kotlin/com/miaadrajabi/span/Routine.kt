package com.miaadrajabi.span

enum class StallKind {
    Moving,
    SlowDisk,
    Stalled,
}

data class FairCandidate(val id: Long, val priority: Int, val waits: Int)

object DropIntake {
    private val link = Regex("https?://[^\\s<>\"]+")
    private val executable = setOf("command", "sh", "bash", "zsh", "bat", "cmd", "exe", "app", "dmg", "pkg", "js", "jar", "ps1")

    fun links(text: String): List<String> {
        return link.findAll(text).map { it.value.trimEnd('.', ',', ';') }.distinct().toList()
    }

    fun executable(name: String): Boolean {
        return name.substringAfterLast('.', "").lowercase() in executable
    }

    fun fromFile(name: String, text: String): List<String> {
        if (executable(name)) return emptyList()
        return links(text)
    }
}

object FairShare {
    fun pick(ready: List<FairCandidate>, slots: Int): List<Long> {
        if (slots <= 0 || ready.isEmpty()) return emptyList()
        val ordered = ready.sortedWith(compareByDescending<FairCandidate> { it.priority }.thenByDescending { it.waits })
        val top = ordered.first().priority
        val high = ordered.filter { it.priority == top }
        val patient = ordered.filter { it.priority < top }.maxByOrNull { it.waits }
        val chosen = mutableListOf<Long>()
        if (patient != null && patient.waits > 0 && high.size >= slots) {
            chosen += high.take((slots - 1).coerceAtLeast(0)).map { it.id }
            if (chosen.size < slots) chosen += patient.id
        } else {
            chosen += high.take(slots).map { it.id }
        }
        if (chosen.size < slots) {
            chosen += ordered.map { it.id }.filter { it !in chosen }.take(slots - chosen.size)
        }
        return chosen.distinct()
    }
}

object StallWatch {
    fun judge(
        bytesThen: Long,
        bytesNow: Long,
        reservedThen: Long,
        reservedNow: Long,
        elapsedMs: Long,
        limitMs: Long = 20_000L,
    ): StallKind {
        if (bytesNow > bytesThen) return StallKind.Moving
        if (reservedNow > reservedThen) return StallKind.SlowDisk
        if (elapsedMs >= limitMs) return StallKind.Stalled
        return StallKind.Moving
    }
}

object ReconnectPlan {
    fun resume(state: String): Boolean = state == "Queued" || state == "Running"
}

object WakePlan {
    fun shouldStart(state: String): Boolean = state == "Queued"
}

object Redirects {
    fun refuse(seen: List<String>, next: String, max: Int = 8): String? {
        if (next in seen) return "Redirect loop"
        if (seen.size >= max) return "Too many redirects"
        return null
    }
}

object SettingsDocument {
    const val SCHEMA = 1
    private val secret = Regex("(?i)password|passwd|token|secret|authorization")

    fun export(entries: Map<String, String>): String {
        val body = entries
            .filterKeys { !secret.containsMatchIn(it) }
            .toSortedMap()
            .entries
            .joinToString("\n") { "${it.key}=${it.value.replace('\n', ' ')}" }
        return "schema=$SCHEMA\n$body\n"
    }

    fun import(current: Map<String, String>, text: String): Map<String, String> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        val schema = lines.firstOrNull { it.startsWith("schema=") }?.substringAfter("=")?.toIntOrNull()
        if (schema != SCHEMA) return current
        val parsed = linkedMapOf<String, String>()
        lines.filterNot { it.startsWith("schema=") }.forEach { line ->
            val key = line.substringBefore("=", "")
            if (key.isBlank() || !line.contains("=") || secret.containsMatchIn(key)) return@forEach
            parsed[key] = line.substringAfter("=")
        }
        if (parsed.isEmpty()) return current
        return current + parsed
    }
}

object ScanLabel {
    const val NOT_SCANNED = "Not scanned"
}

object RetryAfter {
    fun millis(header: String?): Long? {
        val seconds = header?.trim()?.toLongOrNull() ?: return null
        return seconds.coerceIn(0L, 3_600L) * 1000L
    }
}

object BatteryPlan {
    const val PAUSED = "Paused for battery"

    fun nextState(policyOn: Boolean, onBattery: Boolean?, state: String, batteryHold: Boolean): String {
        if (!policyOn) return if (batteryHold && state == "Paused") "Queued" else state
        if (onBattery == null) return state
        if (onBattery && (state == "Running" || state == "Queued")) return "Paused"
        if (!onBattery && batteryHold && state == "Paused") return "Queued"
        return state
    }
}

object UpdatePlan {
    fun decision(active: Int, available: Boolean, signatureValid: Boolean): String {
        if (!available) return "No update is available."
        if (active > 0) return "An update waits until downloads finish."
        if (!signatureValid) return "The update signature was not accepted."
        return "Ready to update."
    }
}

object Admission {
    fun ready(
        queued: Boolean,
        notBefore: Long,
        now: Long,
        queueAllows: Boolean,
        runningInQueue: Int,
        queueCap: Int,
        runningOnHost: Int,
        hostCap: Int,
    ): Boolean {
        if (!queued || notBefore > now) return false
        if (!queueAllows) return false
        if (runningInQueue >= queueCap.coerceAtLeast(1)) return false
        if (runningOnHost >= hostCap.coerceAtLeast(1)) return false
        return true
    }
}

object CompletionPlan {
    fun proceed(cancelled: Boolean, active: Boolean): Boolean = !cancelled && !active
}

object RuleBook {
    fun canSave(start: String, nextStep: String): Boolean {
        if (start.isBlank() || nextStep.isBlank()) return false
        if (start.contains('\t') || nextStep.contains('\t')) return false
        return "Loop stopped" !in RulePreview.steps(start, mapOf(start to nextStep))
    }

    fun queueOf(start: String, nextStep: String): String? {
        if (!canSave(start, nextStep)) return null
        val named = RulePreview.steps(start, mapOf(start to nextStep))
            .firstOrNull { it.startsWith("queue ") }
            ?.removePrefix("queue ")
            ?.trim()
        return named?.ifBlank { null }
    }
}

/** Daily hour window in the computer's local hour. A negative start means the queue is always open. */
data class BatchLine(val text: String, val accepted: Boolean, val note: String)

object BatchIntake {
    fun review(raw: String): List<BatchLine> {
        return raw.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
            if (!line.startsWith("http://") && !line.startsWith("https://")) {
                BatchLine(line, false, "Only http and https links are accepted.")
            } else {
                val name = SpanRules.safeFileName(line.substringBefore('?').substringAfterLast('/').ifBlank { "download.bin" })
                val error = SpanRules.validationError(SpanRequest(url = line, fileName = name))
                if (error != null) BatchLine(line, false, error) else BatchLine(line, true, "Will start")
            }
        }.toList()
    }

    fun keep(raw: String): List<String> = review(raw).filter { it.accepted }.map { it.text }
}

object ActivityLog {
    fun next(previous: List<String>, label: String): List<String> {
        val clean = label.trim()
        if (clean.isBlank()) return previous
        if (previous.lastOrNull() == clean) return previous
        return (previous + clean).takeLast(12)
    }
}

object ScheduleWindow {
    fun describe(fromHour: Int, toHour: Int, nowHour: Int): String {
        if (fromHour < 0) return "Always open"
        val from = fromHour.coerceIn(0, 23)
        val to = toHour.coerceIn(0, 23)
        val now = nowHour.mod(24)
        if (from == to) return "Next run at $from:00"
        val inside = if (from < to) now in from until to else now >= from || now < to
        return if (inside) "Open now, until $to:00" else "Next run at $from:00"
    }
}
