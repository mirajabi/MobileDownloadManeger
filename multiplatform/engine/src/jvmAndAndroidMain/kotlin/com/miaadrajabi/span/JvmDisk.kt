package com.miaadrajabi.span

import java.io.File
import java.nio.file.Files

object JvmDisk {
    fun stamp(directory: File): DiskStamp {
        val store = Files.getFileStore(directory.toPath())
        val device = runCatching { store.getAttribute("unix:dev")?.toString() }.getOrNull()
        val id = listOfNotNull(device, store.name(), store.type()).joinToString("|").ifBlank { "unknown" }
        val type = store.type().lowercase()
        val kind = when {
            listOf("nfs", "smb", "cifs", "afp", "webdav", "ftp").any { type.contains(it) } -> DiskKind.Network
            runCatching { store.getAttribute("volume:isRemovable") == true }.getOrDefault(false) -> DiskKind.Removable
            else -> DiskKind.Local
        }
        return DiskStamp(id, kind)
    }
}
