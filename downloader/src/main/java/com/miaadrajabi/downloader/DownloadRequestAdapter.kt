package com.miaadrajabi.downloader

import android.content.Intent
import androidx.work.Data
import org.json.JSONObject

/**
 * 1. Serializes DownloadRequest between WorkManager Data and AlarmManager intents.
 */
internal object DownloadRequestAdapter {

    fun toData(request: DownloadRequest): Data {
        return Data.Builder()
            .putString(KEY_URL, request.url)
            .putString(KEY_FILE_NAME, request.fileName)
            .putString(KEY_DESTINATION, destinationToJson(request.destination).toString())
            .putString(KEY_HEADERS, JSONObject(request.headers).toString())
            .putString(KEY_ID, request.id)
            .putString(KEY_CHECKSUM_ALGORITHM, request.checksumAlgorithm.name)
            .apply {
                request.expectedChecksum?.let { putString(KEY_CHECKSUM, it) }
                request.rangeStart?.let { putString(KEY_RANGE_START, it.toString()) }
                request.rangeEndInclusive?.let { putString(KEY_RANGE_END, it.toString()) }
            }
            .build()
    }

    fun fromData(data: Data): DownloadRequest? {
        val url = data.getString(KEY_URL) ?: return null
        val fileName = data.getString(KEY_FILE_NAME) ?: return null
        val destination = data.getString(KEY_DESTINATION)?.let { destinationFromJson(JSONObject(it)) }
            ?: DownloadDestination.Auto
        val headers = data.getString(KEY_HEADERS)?.let { jsonToMap(JSONObject(it)) } ?: emptyMap()
        val id = data.getString(KEY_ID) ?: java.util.UUID.randomUUID().toString()
        return DownloadRequest(
            url = url,
            fileName = fileName,
            destination = destination,
            id = id,
            headers = headers,
            expectedChecksum = data.getString(KEY_CHECKSUM),
            checksumAlgorithm = checksumAlgorithm(
                data.getString(KEY_CHECKSUM_ALGORITHM),
                data.getString(KEY_CHECKSUM)
            ),
            rangeStart = data.getString(KEY_RANGE_START)?.toLongOrNull(),
            rangeEndInclusive = data.getString(KEY_RANGE_END)?.toLongOrNull()
        )
    }

    fun putExtras(intent: Intent, request: DownloadRequest) {
        intent.putExtra(KEY_URL, request.url)
        intent.putExtra(KEY_FILE_NAME, request.fileName)
        intent.putExtra(KEY_DESTINATION, destinationToJson(request.destination).toString())
        intent.putExtra(KEY_HEADERS, JSONObject(request.headers).toString())
        intent.putExtra(KEY_ID, request.id)
        request.expectedChecksum?.let { intent.putExtra(KEY_CHECKSUM, it) }
        intent.putExtra(KEY_CHECKSUM_ALGORITHM, request.checksumAlgorithm.name)
        request.rangeStart?.let { intent.putExtra(KEY_RANGE_START, it) }
        request.rangeEndInclusive?.let { intent.putExtra(KEY_RANGE_END, it) }
    }

    fun fromIntent(intent: Intent): DownloadRequest? {
        val url = intent.getStringExtra(KEY_URL) ?: return null
        val fileName = intent.getStringExtra(KEY_FILE_NAME) ?: return null
        val destination = intent.getStringExtra(KEY_DESTINATION)
            ?.let { destinationFromJson(JSONObject(it)) } ?: DownloadDestination.Auto
        val headers = intent.getStringExtra(KEY_HEADERS)?.let { jsonToMap(JSONObject(it)) } ?: emptyMap()
        val id = intent.getStringExtra(KEY_ID) ?: java.util.UUID.randomUUID().toString()
        return DownloadRequest(
            url = url,
            fileName = fileName,
            destination = destination,
            id = id,
            headers = headers,
            expectedChecksum = intent.getStringExtra(KEY_CHECKSUM),
            checksumAlgorithm = checksumAlgorithm(
                intent.getStringExtra(KEY_CHECKSUM_ALGORITHM),
                intent.getStringExtra(KEY_CHECKSUM)
            ),
            rangeStart = if (intent.hasExtra(KEY_RANGE_START)) intent.getLongExtra(KEY_RANGE_START, 0L) else null,
            rangeEndInclusive = if (intent.hasExtra(KEY_RANGE_END)) intent.getLongExtra(KEY_RANGE_END, 0L) else null
        )
    }

    private fun checksumAlgorithm(name: String?, checksum: String?): ChecksumAlgorithm {
        return parseChecksumAlgorithm(name, !checksum.isNullOrEmpty())
    }

    private fun destinationToJson(destination: DownloadDestination): JSONObject {
        val obj = JSONObject()
        when (destination) {
            is DownloadDestination.Auto -> obj.put("type", "auto")
            is DownloadDestination.Custom -> {
                obj.put("type", "custom")
                obj.put("path", destination.absolutePath)
            }
            is DownloadDestination.Scoped -> {
                obj.put("type", "scoped")
                obj.put("path", destination.relativePath)
            }
        }
        return obj
    }

    private fun destinationFromJson(json: JSONObject): DownloadDestination {
        return when (json.getString("type")) {
            "custom" -> DownloadDestination.Custom(json.getString("path"))
            "scoped" -> DownloadDestination.Scoped(json.getString("path"))
            else -> DownloadDestination.Auto
        }
    }

    private fun jsonToMap(json: JSONObject): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            map[key] = json.getString(key)
        }
        return map
    }

    private const val KEY_URL = "download_url"
    private const val KEY_FILE_NAME = "download_file_name"
    private const val KEY_DESTINATION = "download_destination"
    private const val KEY_HEADERS = "download_headers"
    private const val KEY_ID = "download_id"
    private const val KEY_CHECKSUM = "download_checksum"
    private const val KEY_CHECKSUM_ALGORITHM = "download_checksum_algorithm"
    private const val KEY_RANGE_START = "download_range_start"
    private const val KEY_RANGE_END = "download_range_end"
}

