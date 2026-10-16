package com.miaadrajabi.span

object NetworkProfile {
    fun explain(previous: String, next: String): String {
        val safeNext = if (next == "Metered") "Metered" else "Unmetered"
        val safePrevious = if (previous == "Metered") "Metered" else "Unmetered"
        if (safePrevious == safeNext) return "The network profile stays $safeNext. This does not change other programs."
        return "The network profile changed from $safePrevious to $safeNext. This does not change other programs."
    }

    fun connectionCap(profile: String, requested: Int): Int {
        val bounded = requested.coerceAtLeast(1)
        return if (profile == "Metered") bounded.coerceAtMost(2) else bounded
    }
}

data class BottleneckNote(val cause: String, val evidence: String, val confidence: String)

data class SpeedAction(val label: String, val connections: Int?)

object SpeedCause {
    fun suggest(cause: String, connections: Int, running: Boolean): SpeedAction {
        if (cause == "Network" && connections > 1) {
            val next = connections - 1
            val label = if (running) "Restart with $next connections" else "Use $next connections next time"
            return SpeedAction(label, next)
        }
        val label = when (cause) {
            "Network" -> "Already using one connection"
            "Disk" -> "Leave this download running. The disk is still writing."
            "None" -> "No change. Bytes are still arriving."
            else -> "No change. There is not enough evidence."
        }
        return SpeedAction(label, null)
    }
}

object BottleneckJudge {
    fun note(bytesMoved: Boolean, diskMoved: Boolean, quiet: Boolean): BottleneckNote {
        if (bytesMoved) return BottleneckNote("None", "Bytes are still arriving.", "Observed")
        if (diskMoved) return BottleneckNote("Disk", "Bytes are still being written, but the checkpoint has not moved.", "Observed")
        if (quiet) return BottleneckNote("Network", "No new bytes arrived and nothing new was written.", "Observed")
        return BottleneckNote("Unknown", "There is not enough evidence.", "Low")
    }
}

object AdaptiveConnections {
    fun choose(enabled: Boolean, requested: Int, cap: Int, reduce: Boolean): Int {
        val bounded = requested.coerceIn(1, cap.coerceAtLeast(1))
        if (!enabled || !reduce) return bounded
        return (bounded - 1).coerceAtLeast(1)
    }
}

object LatencyLimit {
    fun apply(enabled: Boolean, currentBytesPerSecond: Long, latencyMs: Long, thresholdMs: Long = 400L): Long {
        if (!enabled || currentBytesPerSecond <= 0L || latencyMs <= thresholdMs) return currentBytesPerSecond
        return (currentBytesPerSecond / 2L).coerceAtLeast(1024L)
    }
}

object TemporaryPriority {
    fun effective(base: Int, boost: Int?, untilMs: Long, nowMs: Long): Int {
        if (boost == null || nowMs >= untilMs) return base
        return boost
    }
}

object DeadlineGuess {
    fun phrase(remainingBytes: Long, bytesPerSecond: Long): String {
        if (remainingBytes <= 0L) return "The received total is already at the known size."
        if (bytesPerSecond <= 0L) return "Not enough evidence for a finish time."
        val seconds = remainingBytes / bytesPerSecond
        return "About ${seconds}s if the recent pace holds. This is not a promise."
    }
}

object QuotaUse {
    fun useful(downloaded: Long, retryBytes: Long): Long = downloaded.coerceAtLeast(0L)

    fun left(limit: Long, useful: Long): Long = (limit - useful).coerceAtLeast(0L)

    fun label(useful: Long, retryBytes: Long): String {
        return "Useful $useful. Retries $retryBytes are not part of the useful count."
    }
}

object LinkIdentity {
    fun refusal(oldTag: String?, newTag: String?, oldSize: Long?, newSize: Long?): String? {
        if (oldTag.isNullOrBlank() || newTag.isNullOrBlank()) {
            return "The file identity is not known, so the link was not replaced."
        }
        if (!oldTag.equals(newTag, ignoreCase = true)) return "The new link is a different file."
        if (oldSize != null && newSize != null && oldSize != newSize) return "The new link is a different file."
        return null
    }
}

object ProviderRefresh {
    fun allowed(provider: String): Boolean = provider.equals("youtube", ignoreCase = true)
}

object Mirrors {
    fun firstReachable(candidates: List<Pair<String, Boolean>>): String? {
        return candidates.firstOrNull { it.second }?.first
    }
}

object SameFile {
    fun proven(identities: List<String>): Boolean {
        return identities.isNotEmpty() && identities.distinct().size == 1
    }
}

object PieceRepair {
    fun badRange(expectedHash: String?, actualHash: String?): Boolean {
        if (expectedHash.isNullOrBlank() || actualHash.isNullOrBlank()) return false
        return !expectedHash.equals(actualHash, ignoreCase = true)
    }
}

object PartialImport {
    fun known(header: String?): Boolean = header == "v2"
}

object HandoffBundle {
    private val secret = Regex("(?i)password|passwd|token|secret|authorization|cookie")

    fun pack(entries: Map<String, String>): Map<String, String> {
        return entries.filterKeys { !secret.containsMatchIn(it) }
    }
}

object ZipEntryGuard {
    fun allowed(name: String): Boolean {
        val normalized = name.replace('\\', '/')
        if (normalized.startsWith("/") || normalized.startsWith("../") || normalized.contains("/../") || normalized == "..") {
            return false
        }
        return normalized.isNotBlank()
    }
}

object ZipPicker {
    fun listed(names: List<String>): List<String> {
        return names.map { it.replace('\\', '/') }.filter { name ->
            ZipEntryGuard.allowed(name) && !name.endsWith("/")
        }.distinct()
    }
}

object InterfaceBind {
    fun refusal(requested: Boolean, supported: Boolean): String? {
        if (!requested) return null
        if (!supported) return "This system cannot bind the download to one network."
        return null
    }
}

object DeltaUpdate {
    fun refusal(manifestValid: Boolean): String? {
        return if (manifestValid) null else "A delta update needs a valid manifest. Nothing was patched."
    }
}

object RulePreview {
    fun steps(start: String, nextOf: Map<String, String>, limit: Int = 8): List<String> {
        val seen = mutableListOf<String>()
        var current = start
        repeat(limit) {
            if (current in seen) return seen + "Loop stopped"
            seen += current
            current = nextOf[current] ?: return seen
        }
        return seen + "Stopped at the step limit"
    }
}

object PostStep {
    fun describe(enabled: Boolean): String {
        return if (enabled) "This build does not run scripts." else "Post-download scripts are off."
    }
}

object ProjectPath {
    fun inside(relative: String): Boolean {
        val normalized = relative.replace('\\', '/')
        return normalized.isNotBlank() && !normalized.startsWith("/") && !normalized.startsWith("../") && !normalized.contains("/../")
    }
}

class DeviceAccess {
    private val paired = mutableSetOf<String>()

    fun pair(token: String) {
        if (token.isNotBlank()) paired += token
    }

    fun revoke(token: String) {
        paired -= token
    }

    fun allowed(token: String): Boolean = token.isNotBlank() && token in paired
}

object LocalGate {
    fun allow(host: String, token: String, expected: String): Boolean {
        val local = host == "127.0.0.1" || host.equals("localhost", ignoreCase = true)
        return local && expected.isNotBlank() && token == expected
    }
}

data class TransferCounters(val useful: Long, val received: Long, val retries: Int, val checkpoints: Int) {
    fun label(): String {
        return "Useful $useful. Received $received. Retries $retries. Checkpoints $checkpoints."
    }
}

enum class SegmentKind { Stored, Pending, NeedsCheck }

data class SegmentBand(val kind: SegmentKind, val bytes: Long)

object SegmentMap {
    fun bands(downloaded: Long, reserved: Long, size: Long, phase: TransferPhase): List<SegmentBand> {
        val flushed = downloaded.coerceAtLeast(0L)
        val ahead = (reserved - flushed).coerceAtLeast(0L)
        val storedCap = if (size > 0L) flushed.coerceAtMost(size) else flushed
        val pending = if (size > 0L) ahead.coerceAtMost((size - storedCap).coerceAtLeast(0L)) else ahead
        val (stored, needsCheck) = when (phase) {
            TransferPhase.Verify -> 0L to storedCap
            else -> storedCap to 0L
        }
        return listOf(
            SegmentBand(SegmentKind.Stored, stored),
            SegmentBand(SegmentKind.Pending, pending),
            SegmentBand(SegmentKind.NeedsCheck, needsCheck),
        ).filter { it.bytes > 0L }
    }

    fun describe(name: String, downloaded: Long, reserved: Long, size: Long, phase: TransferPhase): String {
        val rows = bands(downloaded, reserved, size, phase)
        fun amount(kind: SegmentKind) = rows.filter { it.kind == kind }.sumOf { it.bytes }
        return "$name stored ${amount(SegmentKind.Stored)} pending ${amount(SegmentKind.Pending)} needs check ${amount(SegmentKind.NeedsCheck)}"
    }
}

data class SettingDraft(
    val writePolicy: String,
    val pauseOnBattery: Boolean,
    val quotaMb: Int,
    val networkProfile: String,
    val latencyAware: Boolean,
    val useAlarm: Boolean,
    val localToken: String,
    val speedLimitKb: Int,
)

object SettingPreview {
    fun pending(saved: SettingDraft, draft: SettingDraft): List<String> {
        if (saved == draft) return listOf("Save keeps the current settings.")
        val lines = mutableListOf("Not saved yet. A running download keeps the settings it started with.")
        if (saved.writePolicy != draft.writePolicy) lines += writeLine(draft.writePolicy)
        if (saved.pauseOnBattery != draft.pauseOnBattery) lines += batteryLine(draft.pauseOnBattery)
        if (saved.quotaMb != draft.quotaMb) lines += quotaLine(draft.quotaMb)
        if (saved.networkProfile != draft.networkProfile) lines += networkLine(draft.networkProfile)
        if (saved.latencyAware != draft.latencyAware || saved.speedLimitKb != draft.speedLimitKb) lines += latencyLine(draft.latencyAware, draft.speedLimitKb)
        if (saved.useAlarm != draft.useAlarm) lines += clockLine(draft.useAlarm)
        if (saved.localToken != draft.localToken) lines += tokenLine(draft.localToken.isBlank(), saved.localToken.isBlank())
        return lines
    }

    private fun writeLine(policy: String): String = when (policy) {
        "Local" -> "Local disks flush about every 1 MB. This does not guess hard disk versus SSD."
        "Removable" -> "Removable disks flush about every 256 KB."
        "Network" -> "Network disks flush about every 256 KB."
        else -> "Auto flushes a local disk about every 1 MB and a removable or network disk about every 256 KB. This does not guess hard disk versus SSD."
    }

    private fun batteryLine(pause: Boolean): String = if (pause) {
        "On battery, a running download pauses. A download you paused stays paused."
    } else {
        "Battery is ignored. Nothing pauses because of the power source."
    }

    private fun quotaLine(quotaMb: Int): String = if (quotaMb <= 0) {
        "The useful-byte quota is off."
    } else {
        "A new start stops after $quotaMb MB of useful bytes. Retries are not included."
    }

    private fun networkLine(profile: String): String = if (profile == "Metered") {
        "A new download uses at most 2 connections. Other programs stay as they are."
    } else {
        "The network profile is unmetered. The connection cap is the one you set."
    }

    private fun latencyLine(aware: Boolean, speedLimitKb: Int): String = when {
        !aware -> "A slow reply does not change the speed cap."
        speedLimitKb <= 0 -> "Slow replies are watched, but this download has no speed cap to cut."
        else -> "A slow reply can cut this download's speed cap in half."
    }

    private fun clockLine(useAlarm: Boolean): String = if (useAlarm) {
        "Queues open only inside their hour window."
    } else {
        "The hour window is not applied. Queues can start while Fetch is open."
    }

    private fun tokenLine(draftBlank: Boolean, savedBlank: Boolean): String = when {
        draftBlank -> "The local port will refuse every request."
        savedBlank -> "The local port will accept only this machine with the new token. The token is not shown here."
        else -> "The local token will change. It is not shown here."
    }
}

object TransferStage {
    fun label(state: String, phase: TransferPhase, bytes: Long, total: Long?, message: String): String {
        if (message.contains("free space", ignoreCase = true)) return "Waiting for storage"
        if (state == "Saved") return "Ready"
        val complete = total != null && total > 0L && bytes >= total
        if (state == "Running" && complete) {
            return when (phase) {
                TransferPhase.Verify -> "Verifying"
                TransferPhase.Ready -> "Ready"
                else -> "Finalizing"
            }
        }
        return when (state) {
            "Running" -> "Receiving"
            "Paused" -> "Paused"
            "Queued" -> "Queued"
            "Failed" -> "Failed"
            "Stopped" -> "Stopped"
            else -> state
        }
    }
}
