package com.miaadrajabi.span

enum class DiskKind {
    Local,
    Removable,
    Network,
    Unknown,
}

enum class WritePolicy {
    Auto,
    Local,
    Removable,
    Network,
}

data class DiskStamp(val id: String, val kind: DiskKind)

object DiskPolicy {
    fun interval(policy: WritePolicy, kind: DiskKind): Long {
        return when (policy) {
            WritePolicy.Local -> 1024L * 1024L
            WritePolicy.Removable, WritePolicy.Network -> 256L * 1024L
            WritePolicy.Auto -> when (kind) {
                DiskKind.Removable, DiskKind.Network -> 256L * 1024L
                DiskKind.Local, DiskKind.Unknown -> 1024L * 1024L
            }
        }
    }

    fun describe(policy: WritePolicy, kind: DiskKind): String {
        val chosen = when (policy) {
            WritePolicy.Auto -> kind
            WritePolicy.Local -> DiskKind.Local
            WritePolicy.Removable -> DiskKind.Removable
            WritePolicy.Network -> DiskKind.Network
        }
        return when (chosen) {
            DiskKind.Removable -> "Removable"
            DiskKind.Network -> "Network"
            else -> "Local"
        }
    }
}

object DiskHealth {
    fun read(probe: () -> String?): String {
        return try {
            probe()?.takeIf { it.isNotBlank() } ?: "N/A"
        } catch (_: Exception) {
            "N/A"
        }
    }
}
