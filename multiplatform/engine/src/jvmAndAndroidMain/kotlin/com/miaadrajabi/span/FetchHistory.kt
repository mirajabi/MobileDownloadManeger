package com.miaadrajabi.span

import java.io.File

class FetchHistory(private val file: File) {
    fun add(name: String, path: String) {
        file.parentFile?.mkdirs()
        val row = name.replace('\n', ' ') + "\t" + path.replace('\n', ' ')
        file.appendText(row + "\n")
    }

    fun entries(): List<String> {
        if (!file.exists()) return emptyList()
        return file.readLines().filter { it.isNotBlank() }
    }

    fun clear() {
        file.delete()
    }
}
