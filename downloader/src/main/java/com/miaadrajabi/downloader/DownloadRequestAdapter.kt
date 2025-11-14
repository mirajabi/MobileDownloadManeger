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
            headers = headers
        )
    }

    fun putExtras(intent: Intent, request: DownloadRequest) {
        intent.putExtra(KEY_URL, request.url)
        intent.putExtra(KEY_FILE_NAME, request.fileName)
        intent.putExtra(KEY_DESTINATION, destinationToJson(request.destination).toString())
        intent.putExtra(KEY_HEADERS, JSONObject(request.headers).toString())
        intent.putExtra(KEY_ID, request.id)
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
            headers = headers
        )
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
}

