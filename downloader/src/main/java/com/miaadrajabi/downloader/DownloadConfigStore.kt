package com.miaadrajabi.downloader

import android.content.Context
import android.os.Environment
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * 1. Persists DownloadConfig so scheduled jobs can recreate the manager after process death.
 */
internal object DownloadConfigStore {

    fun save(context: Context, config: DownloadConfig) {
        runCatching {
            val file = configFile(context)
            file.parentFile?.mkdirs()
            file.writeText(config.toJson().toString())
        }
    }

    fun load(context: Context): DownloadConfig? {
        val file = configFile(context)
        if (!file.exists()) return null
        return runCatching { downloadConfigFromJson(JSONObject(file.readText())) }.getOrNull()
    }

    private fun DownloadConfig.toJson(): JSONObject = JSONObject().apply {
        put("chunking", chunking.toJson())
        put("retryPolicy", retryPolicy.toJson())
        put("enforceForegroundService", enforceForegroundService)
        put("notification", notification.toJson())
        put("scheduler", scheduler.toJson())
        put("storage", storage.toJson())
    }

    private fun ChunkingConfig.toJson(): JSONObject = JSONObject().apply {
        put("chunkCount", chunkCount)
        put("minChunkSizeBytes", minChunkSizeBytes)
        put("preferParallel", preferParallel)
    }

    private fun RetryPolicy.toJson(): JSONObject = JSONObject().apply {
        put("maxAttempts", maxAttempts)
        put("initialDelayMillis", initialDelayMillis)
        put("backoffMultiplier", backoffMultiplier)
    }

    private fun NotificationConfig.toJson(): JSONObject = JSONObject().apply {
        put("channelId", channelId)
        put("channelName", channelName)
        put("channelDescription", channelDescription)
        put("showProgressPercentage", showProgressPercentage)
        put("persistent", persistent)
        put("smallIconRes", smallIconRes ?: JSONObject.NULL)
    }

    private fun SchedulerConfig.toJson(): JSONObject = JSONObject().apply {
        put("periodicIntervalMinutes", periodicIntervalMinutes ?: JSONObject.NULL)
        put("exactStartTime", exactStartTime?.toJson() ?: JSONObject.NULL)
        put("allowWhileIdle", allowWhileIdle)
        put("useAlarmManager", useAlarmManager)
    }

    private fun ScheduleTime.toJson(): JSONObject = JSONObject().apply {
        put("hour", hour)
        put("minute", minute)
        put("weekday", weekday?.name ?: JSONObject.NULL)
        put("year", year ?: JSONObject.NULL)
        put("month", month ?: JSONObject.NULL)
        put("dayOfMonth", dayOfMonth ?: JSONObject.NULL)
    }

    private fun StorageConfig.toJson(): JSONObject = JSONObject().apply {
        put("downloadDirs", JSONArray().apply {
            downloadDirs.forEach { destination ->
                put(destination.toJson())
            }
        })
        put("overwriteExisting", overwriteExisting)
        put("validateFreeSpace", validateFreeSpace)
        put("minFreeSpaceBytes", minFreeSpaceBytes)
    }

    private fun DownloadDestination.toJson(): JSONObject {
        val obj = JSONObject()
        when (this) {
            is DownloadDestination.Auto -> {
                obj.put("type", "auto")
            }
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

    private fun JSONObject.toChunkingConfig() = ChunkingConfig(
        chunkCount = getInt("chunkCount"),
        minChunkSizeBytes = getLong("minChunkSizeBytes"),
        preferParallel = getBoolean("preferParallel")
    )

    private fun JSONObject.toRetryPolicy() = RetryPolicy(
        maxAttempts = getInt("maxAttempts"),
        initialDelayMillis = getLong("initialDelayMillis"),
        backoffMultiplier = getDouble("backoffMultiplier").toFloat()
    )

    private fun JSONObject.toNotificationConfig() = NotificationConfig(
        channelId = getString("channelId"),
        channelName = getString("channelName"),
        channelDescription = getString("channelDescription"),
        showProgressPercentage = getBoolean("showProgressPercentage"),
        persistent = getBoolean("persistent"),
        smallIconRes = optIntNullable("smallIconRes")
    )

    private fun JSONObject.toSchedulerConfig() = SchedulerConfig(
        periodicIntervalMinutes = optLongNullable("periodicIntervalMinutes"),
        exactStartTime = optJSONObject("exactStartTime")?.toScheduleTime(),
        allowWhileIdle = getBoolean("allowWhileIdle"),
        useAlarmManager = getBoolean("useAlarmManager")
    )

    private fun JSONObject.toScheduleTime() = ScheduleTime(
        hour = getInt("hour"),
        minute = getInt("minute"),
        weekday = optString("weekday").takeIf { it.isNotEmpty() }?.let { Weekday.valueOf(it) },
        year = optIntNullable("year"),
        month = optIntNullable("month"),
        dayOfMonth = optIntNullable("dayOfMonth")
    )

    private fun JSONObject.toStorageConfig(): StorageConfig {
        val dirs = mutableListOf<DownloadDestination>()
        val array = getJSONArray("downloadDirs")
        for (i in 0 until array.length()) {
            val entry = array.getJSONObject(i)
            val destination = when (entry.getString("type")) {
                "custom" -> DownloadDestination.Custom(entry.getString("path"))
                "scoped" -> DownloadDestination.Scoped(entry.getString("path"))
                else -> DownloadDestination.Auto
            }
            dirs += destination
        }
        return StorageConfig(
            downloadDirs = dirs,
            overwriteExisting = getBoolean("overwriteExisting"),
            validateFreeSpace = getBoolean("validateFreeSpace"),
            minFreeSpaceBytes = getLong("minFreeSpaceBytes")
        )
    }

    private fun downloadConfigFromJson(json: JSONObject): DownloadConfig {
        return DownloadConfig(
            chunking = json.getJSONObject("chunking").toChunkingConfig(),
            retryPolicy = json.getJSONObject("retryPolicy").toRetryPolicy(),
            enforceForegroundService = json.getBoolean("enforceForegroundService"),
            notification = json.getJSONObject("notification").toNotificationConfig(),
            scheduler = json.getJSONObject("scheduler").toSchedulerConfig(),
            storage = json.getJSONObject("storage").toStorageConfig(),
            listeners = emptyList()
        )
    }

    private fun configFile(context: Context): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        return File(dir, "mobile_downloader_config.json")
    }

    // === Pause State Persistence ===

    fun savePausedState(
        context: Context,
        handleId: String,
        request: DownloadRequest,
        resolution: StorageResolution,
        completedBytes: Long,
        chunkStates: List<ChunkStateData> = emptyList()
    ) {
        runCatching {
            val file = pausedStateFile(context, handleId)
            file.parentFile?.mkdirs()
            val json = JSONObject().apply {
                put("handleId", handleId)
                put("request", request.toJson())
                put("resolution", resolution.toJson())
                put("completedBytes", completedBytes)
                put("chunkStates", JSONArray().apply {
                    chunkStates.forEach { state ->
                        put(state.toJson())
                    }
                })
            }
            file.writeText(json.toString())
        }
    }

    fun loadPausedState(context: Context, handleId: String): PausedStateData? {
        val file = pausedStateFile(context, handleId)
        if (!file.exists()) return null
        return runCatching {
            val json = JSONObject(file.readText())
            PausedStateData(
                handleId = json.getString("handleId"),
                request = json.getJSONObject("request").toDownloadRequest(),
                resolution = json.getJSONObject("resolution").toStorageResolution(),
                completedBytes = json.getLong("completedBytes"),
                chunkStates = json.optJSONArray("chunkStates")?.toChunkStateList() ?: emptyList()
            )
        }.getOrNull()
    }

    fun loadAllPausedStates(context: Context): List<PausedStateData> {
        val dir = pausedStatesDir(context)
        if (!dir.exists()) return emptyList()
        return dir.listFiles()?.mapNotNull { file ->
            runCatching {
                val json = JSONObject(file.readText())
                PausedStateData(
                    handleId = json.getString("handleId"),
                    request = json.getJSONObject("request").toDownloadRequest(),
                    resolution = json.getJSONObject("resolution").toStorageResolution(),
                    completedBytes = json.getLong("completedBytes"),
                    chunkStates = json.optJSONArray("chunkStates")?.toChunkStateList() ?: emptyList()
                )
            }.getOrNull()
        } ?: emptyList()
    }

    fun removePausedState(context: Context, handleId: String) {
        pausedStateFile(context, handleId).delete()
    }

    private fun pausedStatesDir(context: Context): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        return File(dir, "paused_states")
    }

    private fun pausedStateFile(context: Context, handleId: String): File {
        return File(pausedStatesDir(context), "$handleId.json")
    }

    private fun DownloadRequest.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("url", url)
        put("fileName", fileName)
        put("destination", destination.toJson())
        put("headers", JSONObject(headers))
    }

    private fun JSONObject.toDownloadRequest(): DownloadRequest {
        val destJson = getJSONObject("destination")
        val destination = when (destJson.getString("type")) {
            "custom" -> DownloadDestination.Custom(destJson.getString("path"))
            "scoped" -> DownloadDestination.Scoped(destJson.getString("path"))
            else -> DownloadDestination.Auto
        }
        val headersJson = getJSONObject("headers")
        val headers = mutableMapOf<String, String>()
        headersJson.keys().forEach { key ->
            headers[key] = headersJson.getString(key)
        }
        return DownloadRequest(
            id = getString("id"),
            url = getString("url"),
            fileName = getString("fileName"),
            destination = destination,
            headers = headers
        )
    }

    private fun StorageResolution.toJson(): JSONObject = JSONObject().apply {
        put("directory", directory.absolutePath)
        put("file", file.absolutePath)
        put("overwroteExisting", overwroteExisting)
    }

    private fun JSONObject.toStorageResolution(): StorageResolution {
        return StorageResolution(
            directory = File(getString("directory")),
            file = File(getString("file")),
            overwroteExisting = getBoolean("overwroteExisting")
        )
    }
}

data class PausedStateData(
    val handleId: String,
    val request: DownloadRequest,
    val resolution: StorageResolution,
    val completedBytes: Long,
    val chunkStates: List<ChunkStateData> = emptyList()
)

data class ChunkStateData(
    val index: Int,
    val start: Long,
    val endInclusive: Long?,
    val nextOffset: Long
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("index", index)
        put("start", start)
        put("endInclusive", endInclusive ?: JSONObject.NULL)
        put("nextOffset", nextOffset)
    }
}

private fun JSONArray.toChunkStateList(): List<ChunkStateData> {
    val list = mutableListOf<ChunkStateData>()
    for (i in 0 until length()) {
        val obj = getJSONObject(i)
        list += ChunkStateData(
            index = obj.getInt("index"),
            start = obj.getLong("start"),
            endInclusive = obj.optLongNullable("endInclusive"),
            nextOffset = obj.getLong("nextOffset")
        )
    }
    return list
}

private fun JSONObject.optIntNullable(key: String): Int? =
    if (isNull(key)) null else getInt(key)

private fun JSONObject.optLongNullable(key: String): Long? =
    if (isNull(key)) null else getLong(key)

