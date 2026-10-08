package com.miaadrajabi.downloader

import android.content.Context
import android.util.Log
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * Durable queue for downloads that should continue after process death or reboot.
 * Manual pause stays paused. A failed or cancelled record is abandoned and is not
 * started again until a new request replaces it from byte zero.
 */
internal object DownloadRecoveryStore {

    private const val TAG = "DownloadRecoveryStore"

    fun save(context: Context, record: DownloadRecoveryRecord) {
        val file = recordFile(context, record.id)
        val payload = record.toJson().toString()
        if (!writeAtomically(file, payload)) {
            Log.e(TAG, "Failed to persist recovery record ${record.id}")
        }
    }

    fun load(context: Context, id: String): DownloadRecoveryRecord? {
        val file = recordFile(context, id)
        if (!file.exists()) return null
        return runCatching { JSONObject(file.readText()).toRecoveryRecord() }
            .onFailure { error -> Log.e(TAG, "Ignoring unreadable recovery record $id", error) }
            .getOrNull()
    }

    fun loadAll(context: Context): List<DownloadRecoveryRecord> {
        val dir = recoveryDir(context)
        if (!dir.exists()) return emptyList()
        return dir.listFiles()?.mapNotNull { file ->
            runCatching { JSONObject(file.readText()).toRecoveryRecord() }
                .onFailure { error -> Log.e(TAG, "Ignoring unreadable recovery record ${file.name}", error) }
                .getOrNull()
        } ?: emptyList()
    }

    fun hasRecoverable(context: Context): Boolean {
        return loadAll(context).any { it.state in DownloadRecovery.RESUMABLE_AFTER_RESTART }
    }

    fun delete(context: Context, id: String) {
        recordFile(context, id).delete()
    }

    private fun recoveryDir(context: Context): File {
        return File(context.filesDir, "download_recovery")
    }

    private fun recordFile(context: Context, id: String): File {
        return File(recoveryDir(context), "$id.json")
    }

    private fun writeAtomically(target: File, payload: String): Boolean {
        return runCatching {
            target.parentFile?.mkdirs()
            val temp = File(target.parentFile, "${target.name}.tmp")
            temp.writeText(payload)
            if (!temp.renameTo(target)) {
                target.writeText(payload)
                temp.delete()
            }
            true
        }.getOrDefault(false)
    }
}

internal data class DownloadRecoveryRecord(
    val id: String,
    val request: DownloadRequest,
    val resolution: StorageResolution,
    val state: String,
    val completedBytes: Long,
    val chunkStates: List<ChunkStateData>,
    val strongEtag: String?,
    val lastModified: String?,
    val totalBytes: Long?,
    val generation: Long,
    val canonicalPath: String
) {
    fun validators(): ArtifactValidators {
        return ArtifactValidators(strongEtag, lastModified, totalBytes)
    }
}

private fun DownloadRecoveryRecord.toJson(): JSONObject {
    return JSONObject().apply {
        put("version", 1)
        put("id", id)
        put("request", request.toRecoveryJson())
        put("resolution", resolution.toRecoveryJson())
        put("state", state)
        put("completedBytes", completedBytes)
        put("chunkStates", JSONArray().apply {
            chunkStates.forEach { state -> put(state.toJson()) }
        })
        put("strongEtag", strongEtag ?: JSONObject.NULL)
        put("lastModified", lastModified ?: JSONObject.NULL)
        put("totalBytes", totalBytes ?: JSONObject.NULL)
        put("generation", generation)
        put("canonicalPath", canonicalPath)
    }
}

private fun JSONObject.toRecoveryRecord(): DownloadRecoveryRecord {
    val checksum = if (has("request")) {
        getJSONObject("request").optString("expectedChecksum", "")
    } else {
        ""
    }
    return DownloadRecoveryRecord(
        id = getString("id"),
        request = getJSONObject("request").toRecoveryRequest(),
        resolution = getJSONObject("resolution").toRecoveryResolution(),
        state = getString("state"),
        completedBytes = optLong("completedBytes", 0L),
        chunkStates = optJSONArray("chunkStates")?.toChunkStateList() ?: emptyList(),
        strongEtag = optStringOrNull("strongEtag"),
        lastModified = optStringOrNull("lastModified"),
        totalBytes = if (has("totalBytes") && !isNull("totalBytes")) getLong("totalBytes") else null,
        generation = optLong("generation", 0L),
        canonicalPath = optString("canonicalPath", "")
    ).also {
        if (checksum.isNotEmpty()) {
            validatedChecksum(it.request.expectedChecksum, it.request.checksumAlgorithm)
        }
    }
}

private fun DownloadRequest.toRecoveryJson(): JSONObject {
    return JSONObject().apply {
        put("id", id)
        put("url", url)
        put("fileName", fileName)
        put("destination", destination.toRecoveryJson())
        put("headers", JSONObject(headers))
        if (expectedChecksum != null) put("expectedChecksum", expectedChecksum)
        put("checksumAlgorithm", checksumAlgorithm.name)
    }
}

private fun JSONObject.toRecoveryRequest(): DownloadRequest {
    val destinationJson = getJSONObject("destination")
    val destination = when (destinationJson.getString("type")) {
        "custom" -> DownloadDestination.Custom(destinationJson.getString("path"))
        "scoped" -> DownloadDestination.Scoped(destinationJson.getString("path"))
        else -> DownloadDestination.Auto
    }
    val headersJson = optJSONObject("headers") ?: JSONObject()
    val headers = mutableMapOf<String, String>()
    headersJson.keys().forEach { key -> headers[key] = headersJson.getString(key) }
    val checksum = optStringOrNull("expectedChecksum")
    val algorithm = parseChecksumAlgorithm(optStringOrNull("checksumAlgorithm"), checksum != null)
    return DownloadRequest(
        id = getString("id"),
        url = getString("url"),
        fileName = getString("fileName"),
        destination = destination,
        headers = headers,
        expectedChecksum = checksum?.let { validatedChecksum(it, algorithm) },
        checksumAlgorithm = algorithm
    )
}

private fun DownloadDestination.toRecoveryJson(): JSONObject {
    val obj = JSONObject()
    when (this) {
        is DownloadDestination.Auto -> obj.put("type", "auto")
        is DownloadDestination.Custom -> {
            obj.put("type", "custom")
            obj.put("path", absolutePath)
        }
        is DownloadDestination.Scoped -> {
            obj.put("type", "scoped")
            obj.put("path", relativePath)
        }
    }
    return obj
}

private fun StorageResolution.toRecoveryJson(): JSONObject {
    return JSONObject().apply {
        put("directory", directory.absolutePath)
        put("file", file.absolutePath)
        put("overwroteExisting", overwroteExisting)
    }
}

private fun JSONObject.toRecoveryResolution(): StorageResolution {
    return StorageResolution(
        directory = File(getString("directory")),
        file = File(getString("file")),
        overwroteExisting = optBoolean("overwroteExisting", false)
    )
}

private fun JSONObject.optStringOrNull(key: String): String? {
    if (!has(key) || isNull(key)) return null
    val value = optString(key, "")
    return if (value.isEmpty()) null else value
}
