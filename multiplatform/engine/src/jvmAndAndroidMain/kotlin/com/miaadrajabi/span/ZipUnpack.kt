package com.miaadrajabi.span

import java.io.File
import java.util.zip.ZipFile

object ZipUnpack {
    fun names(file: File): List<String> {
        if (!file.isFile) return emptyList()
        return ZipFile(file).use { archive ->
            ZipPicker.listed(archive.entries().toList().map { it.name })
        }
    }

    fun unpack(file: File, chosen: Collection<String>): Int {
        if (!file.isFile) return 0
        val folderName = file.name.substringBeforeLast('.', file.name)
        val root = File(file.parentFile, folderName)
        if (!root.exists() && !root.mkdirs()) return 0
        val base = root.canonicalFile
        var saved = 0
        ZipFile(file).use { archive ->
            chosen.forEach { name ->
                if (!ZipEntryGuard.allowed(name)) return@forEach
                val entry = archive.getEntry(name) ?: return@forEach
                if (entry.isDirectory) return@forEach
                val target = File(root, name.replace('\\', '/')).canonicalFile
                val prefix = base.path + File.separator
                if (!target.path.startsWith(prefix) && target != base) return@forEach
                target.parentFile?.mkdirs()
                archive.getInputStream(entry).use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                saved += 1
            }
        }
        return saved
    }
}
