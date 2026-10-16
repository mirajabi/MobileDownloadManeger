package com.miaadrajabi.span

import java.io.File
import java.net.URLDecoder
import java.util.Properties

data class DeskSettings(
    val theme: String = "light",
    val threads: Int = 8,
    val speedLimitKb: Int = 0,
    val maxConcurrent: Int = 3,
    val hostCap: Int = 8,
    val clipboard: Boolean = true,
    val port: Int = 0,
    val userAgent: String = "Fetch",
    val proxyHost: String = "",
    val proxyPort: Int = 0,
    val onComplete: String = "notify",
    val folder: String = File(System.getProperty("user.home"), "Fetch").absolutePath,
    val hostRules: String = "",
    val parallel: Boolean = true,
    val minChunkKb: Int = 256,
    val retryAttempts: Int = 5,
    val retryDelaySeconds: Int = 3,
    val backoffTenths: Int = 20,
    val channelName: String = "Fetch",
    val iconKey: String = "arrow",
    val showProgress: Boolean = true,
    val persistent: Boolean = true,
    val destination: String = "auto",
    val publicDownloads: Boolean = false,
    val publicFolder: String = "",
    val overwrite: Boolean = true,
    val checkSpace: Boolean = true,
    val minFreeMb: Int = 10,
    val promptOpen: Boolean = false,
    val verifySize: Boolean = true,
    val verifyChecksum: Boolean = true,
    val verifyApk: Boolean = true,
    val verifyType: Boolean = false,
    val verifySignature: Boolean = false,
    val useAlarm: Boolean = false,
    val floatBubble: Boolean = false,
    val windowWidth: Int = DEFAULT_WINDOW_WIDTH,
    val windowHeight: Int = DEFAULT_WINDOW_HEIGHT,
    val viewCards: Boolean = true,
    val comfortable: Boolean = true,
    val alwaysOnTop: Boolean = false,
    val writePolicy: String = "Auto",
    val pauseOnBattery: Boolean = false,
    val adaptiveConnections: Boolean = false,
    val localToken: String = "",
    val networkProfile: String = "Unmetered",
    val latencyAware: Boolean = false,
    val quotaMb: Int = 0,
    val nameColumnDp: Int = 160,
    val sidebarCollapsed: Boolean = false,
    val sidebarWidth: Int = 216,
)

data class DeskRule(val start: String, val nextStep: String)

data class DeskQueue(
    val name: String,
    val maxAtOnce: Int = 3,
    val fromHour: Int = -1,
    val toHour: Int = -1,
)

data class DeskCategory(
    val name: String,
    val extensions: String,
    val subfolder: String,
)

data class StoredJob(
    val id: Long,
    val url: String,
    val fileName: String,
    val state: String,
    val addedAt: Long,
    val queue: String,
    val connections: Int,
    val speedLimit: Long,
    val checksum: String,
    val algorithm: String,
    val path: String,
    val message: String,
    val mediaPage: String = "",
    val mediaFormat: String = "",
)

object DeskLibrary {
    private val root = File(System.getProperty("user.home"), "Span")
    private val settingsFile = File(root, "settings.properties")
    private val queueFile = File(root, "queues.txt")
    private val rulesFile = File(root, "rules.txt")
    private val categoryFile = File(root, "categories.txt")
    private val jobFile = File(root, "downloads.txt")

    fun loadSettings(): DeskSettings {
        if (!settingsFile.exists()) return DeskSettings()
        val properties = Properties()
        settingsFile.inputStream().use { properties.load(it) }
        val base = DeskSettings()
        return base.copy(
            theme = properties.getProperty("theme", base.theme),
            threads = properties.getProperty("threads")?.toIntOrNull() ?: base.threads,
            speedLimitKb = properties.getProperty("speedLimitKb")?.toIntOrNull() ?: base.speedLimitKb,
            maxConcurrent = properties.getProperty("maxConcurrent")?.toIntOrNull() ?: base.maxConcurrent,
            hostCap = properties.getProperty("hostCap")?.toIntOrNull() ?: base.hostCap,
            clipboard = properties.getProperty("clipboard", "true") == "true",
            port = properties.getProperty("port")?.toIntOrNull() ?: base.port,
            userAgent = properties.getProperty("userAgent", base.userAgent),
            proxyHost = properties.getProperty("proxyHost", base.proxyHost),
            proxyPort = properties.getProperty("proxyPort")?.toIntOrNull() ?: base.proxyPort,
            onComplete = properties.getProperty("onComplete", base.onComplete),
            folder = properties.getProperty("folder", base.folder),
            hostRules = properties.getProperty("hostRules", base.hostRules),
            parallel = properties.getProperty("parallel", "true") == "true",
            minChunkKb = properties.getProperty("minChunkKb")?.toIntOrNull() ?: base.minChunkKb,
            retryAttempts = properties.getProperty("retryAttempts")?.toIntOrNull() ?: base.retryAttempts,
            retryDelaySeconds = properties.getProperty("retryDelaySeconds")?.toIntOrNull() ?: base.retryDelaySeconds,
            backoffTenths = properties.getProperty("backoffTenths")?.toIntOrNull() ?: base.backoffTenths,
            channelName = properties.getProperty("channelName", base.channelName),
            iconKey = properties.getProperty("iconKey", base.iconKey),
            showProgress = properties.getProperty("showProgress", "true") == "true",
            persistent = properties.getProperty("persistent", "true") == "true",
            destination = properties.getProperty("destination", base.destination),
            publicDownloads = properties.getProperty("publicDownloads") == "true",
            publicFolder = properties.getProperty("publicFolder", base.publicFolder),
            overwrite = properties.getProperty("overwrite", "true") == "true",
            checkSpace = properties.getProperty("checkSpace", "true") == "true",
            minFreeMb = properties.getProperty("minFreeMb")?.toIntOrNull() ?: base.minFreeMb,
            promptOpen = properties.getProperty("promptOpen") == "true",
            verifySize = properties.getProperty("verifySize", "true") == "true",
            verifyChecksum = properties.getProperty("verifyChecksum", "true") == "true",
            verifyApk = properties.getProperty("verifyApk", "true") == "true",
            verifyType = properties.getProperty("verifyType") == "true",
            verifySignature = properties.getProperty("verifySignature") == "true",
            useAlarm = properties.getProperty("useAlarm") == "true",
            floatBubble = properties.getProperty("floatBubble") == "true",
            windowWidth = properties.getProperty("windowWidth")?.toIntOrNull()?.coerceIn(360, 4000) ?: base.windowWidth,
            windowHeight = properties.getProperty("windowHeight")?.toIntOrNull()?.coerceIn(280, 2500) ?: base.windowHeight,
            viewCards = properties.getProperty("viewCards", "true") == "true",
            comfortable = properties.getProperty("comfortable", "true") == "true",
            alwaysOnTop = properties.getProperty("alwaysOnTop") == "true",
            writePolicy = properties.getProperty("writePolicy", base.writePolicy).takeIf { it in setOf("Auto", "Local", "Removable", "Network") } ?: base.writePolicy,
            pauseOnBattery = properties.getProperty("pauseOnBattery") == "true",
            adaptiveConnections = properties.getProperty("adaptiveConnections") == "true",
            localToken = properties.getProperty("localToken", base.localToken),
            networkProfile = properties.getProperty("networkProfile", base.networkProfile).takeIf { it == "Metered" } ?: "Unmetered",
            latencyAware = properties.getProperty("latencyAware") == "true",
            quotaMb = properties.getProperty("quotaMb")?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
            nameColumnDp = properties.getProperty("nameColumnDp")?.toIntOrNull()?.coerceIn(80, 420) ?: base.nameColumnDp,
            sidebarCollapsed = properties.getProperty("sidebarCollapsed") == "true",
            sidebarWidth = properties.getProperty("sidebarWidth")?.toIntOrNull()?.coerceIn(184, 280) ?: base.sidebarWidth,
        )
    }

    fun saveSettings(settings: DeskSettings) {
        root.mkdirs()
        val properties = Properties()
        properties["theme"] = settings.theme
        properties["threads"] = settings.threads.toString()
        properties["speedLimitKb"] = settings.speedLimitKb.toString()
        properties["maxConcurrent"] = settings.maxConcurrent.toString()
        properties["hostCap"] = settings.hostCap.toString()
        properties["clipboard"] = settings.clipboard.toString()
        properties["port"] = settings.port.toString()
        properties["userAgent"] = settings.userAgent
        properties["proxyHost"] = settings.proxyHost
        properties["proxyPort"] = settings.proxyPort.toString()
        properties["onComplete"] = settings.onComplete
        properties["folder"] = settings.folder
        properties["hostRules"] = settings.hostRules
        properties["parallel"] = settings.parallel.toString()
        properties["minChunkKb"] = settings.minChunkKb.toString()
        properties["retryAttempts"] = settings.retryAttempts.toString()
        properties["retryDelaySeconds"] = settings.retryDelaySeconds.toString()
        properties["backoffTenths"] = settings.backoffTenths.toString()
        properties["channelName"] = settings.channelName
        properties["iconKey"] = settings.iconKey
        properties["showProgress"] = settings.showProgress.toString()
        properties["persistent"] = settings.persistent.toString()
        properties["destination"] = settings.destination
        properties["publicDownloads"] = settings.publicDownloads.toString()
        properties["publicFolder"] = settings.publicFolder
        properties["overwrite"] = settings.overwrite.toString()
        properties["checkSpace"] = settings.checkSpace.toString()
        properties["minFreeMb"] = settings.minFreeMb.toString()
        properties["promptOpen"] = settings.promptOpen.toString()
        properties["verifySize"] = settings.verifySize.toString()
        properties["verifyChecksum"] = settings.verifyChecksum.toString()
        properties["verifyApk"] = settings.verifyApk.toString()
        properties["verifyType"] = settings.verifyType.toString()
        properties["verifySignature"] = settings.verifySignature.toString()
        properties["useAlarm"] = settings.useAlarm.toString()
        properties["floatBubble"] = settings.floatBubble.toString()
        properties["windowWidth"] = settings.windowWidth.toString()
        properties["windowHeight"] = settings.windowHeight.toString()
        properties["viewCards"] = settings.viewCards.toString()
        properties["comfortable"] = settings.comfortable.toString()
        properties["alwaysOnTop"] = settings.alwaysOnTop.toString()
        properties["writePolicy"] = settings.writePolicy
        properties["pauseOnBattery"] = settings.pauseOnBattery.toString()
        properties["adaptiveConnections"] = settings.adaptiveConnections.toString()
        properties["localToken"] = settings.localToken
        properties["networkProfile"] = settings.networkProfile
        properties["latencyAware"] = settings.latencyAware.toString()
        properties["quotaMb"] = settings.quotaMb.toString()
        properties["nameColumnDp"] = settings.nameColumnDp.toString()
        properties["sidebarCollapsed"] = settings.sidebarCollapsed.toString()
        properties["sidebarWidth"] = settings.sidebarWidth.toString()
        settingsFile.outputStream().use { properties.store(it, "Span desktop settings") }
    }

    fun loadQueues(): List<DeskQueue> {
        if (!queueFile.exists()) return listOf(DeskQueue("Main"))
        return queueFile.readLines().mapNotNull { line ->
            val bits = line.split('\t')
            if (bits.size < 4) return@mapNotNull null
            DeskQueue(bits[0], bits[1].toIntOrNull() ?: 3, bits[2].toIntOrNull() ?: -1, bits[3].toIntOrNull() ?: -1)
        }.ifEmpty { listOf(DeskQueue("Main")) }
    }

    fun saveQueues(queues: List<DeskQueue>) {
        root.mkdirs()
        queueFile.writeText(queues.joinToString("\n") { "${it.name}\t${it.maxAtOnce}\t${it.fromHour}\t${it.toHour}" })
    }

    fun loadRules(): List<DeskRule> {
        if (!rulesFile.exists()) return emptyList()
        return rulesFile.readLines().mapNotNull { line ->
            val bits = line.split('\t')
            if (bits.size < 2) return@mapNotNull null
            val start = decode(bits[0]).trim()
            val next = decode(bits[1]).trim()
            if (!RuleBook.canSave(start, next)) return@mapNotNull null
            DeskRule(start, next)
        }
    }

    fun saveRules(rules: List<DeskRule>) {
        root.mkdirs()
        rulesFile.writeText(rules.filter { RuleBook.canSave(it.start, it.nextStep) }.joinToString("\n") { "${encode(it.start)}\t${encode(it.nextStep)}" })
    }

    fun loadCategories(): List<DeskCategory> {
        if (!categoryFile.exists()) return defaultCategories()
        return categoryFile.readLines().mapNotNull { line ->
            val bits = line.split('\t')
            if (bits.size < 3) return@mapNotNull null
            DeskCategory(decode(bits[0]), decode(bits[1]), decode(bits[2]))
        }.ifEmpty { defaultCategories() }
    }

    fun saveCategories(categories: List<DeskCategory>) {
        root.mkdirs()
        categoryFile.writeText(categories.joinToString("\n") { "${encode(it.name)}\t${encode(it.extensions)}\t${encode(it.subfolder)}" })
    }

    fun loadJobs(): List<StoredJob> {
        if (!jobFile.exists()) return emptyList()
        return jobFile.readLines().mapNotNull { line ->
            val bits = line.split('\t')
            if (bits.size < 12) return@mapNotNull null
            StoredJob(
                id = bits[0].toLongOrNull() ?: return@mapNotNull null,
                url = decode(bits[1]),
                fileName = decode(bits[2]),
                state = bits[3],
                addedAt = bits[4].toLongOrNull() ?: 0L,
                queue = decode(bits[5]),
                connections = bits[6].toIntOrNull() ?: 8,
                speedLimit = bits[7].toLongOrNull() ?: 0L,
                checksum = decode(bits[8]),
                algorithm = bits[9],
                path = decode(bits[10]),
                message = decode(bits[11]),
                mediaPage = decode(bits.getOrElse(12) { "" }),
                mediaFormat = decode(bits.getOrElse(13) { "" }),
            )
        }
    }

    fun saveJobs(jobs: List<StoredJob>) {
        root.mkdirs()
        jobFile.writeText(
            jobs.joinToString("\n") {
                listOf(
                    it.id.toString(),
                    encode(it.url),
                    encode(it.fileName),
                    it.state,
                    it.addedAt.toString(),
                    encode(it.queue),
                    it.connections.toString(),
                    it.speedLimit.toString(),
                    encode(it.checksum),
                    it.algorithm,
                    encode(it.path),
                    encode(it.message),
                    encode(it.mediaPage),
                    encode(it.mediaFormat),
                ).joinToString("\t")
            },
        )
    }

    fun defaultCategories() = listOf(
        DeskCategory("Image", "png,jpg,jpeg,gif,webp", "Images"),
        DeskCategory("Music", "mp3,flac,wav,m4a,aac", "Music"),
        DeskCategory("Video", "mp4,mkv,mov,webm", "Video"),
        DeskCategory("Compressed", "zip,rar,7z,tar,gz", "Compressed"),
        DeskCategory("Other", "", "Other"),
    )

    fun folderFor(settings: DeskSettings, categories: List<DeskCategory>, fileName: String): File {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val match = categories.firstOrNull { category ->
            category.extensions.split(',').map { it.trim().lowercase() }.contains(ext)
        } ?: categories.lastOrNull()
        val sub = match?.subfolder?.ifBlank { null }
        return if (sub == null) File(settings.folder) else File(settings.folder, sub)
    }

    fun resolveFolder(settings: DeskSettings, categories: List<DeskCategory>, fileName: String, requested: String = ""): String {
        if (requested.isNotBlank() && requested != settings.folder) return requested
        if (settings.destination == "custom") return settings.folder
        if (settings.publicDownloads && settings.destination == "auto") {
            val root = File(System.getProperty("user.home"), "Downloads")
            val parts = settings.publicFolder.trim().split('/', '\\').filter { it.isNotBlank() && it != "." && it != ".." }
            return parts.fold(root) { dir, part -> File(dir, part) }.absolutePath
        }
        if (settings.destination == "scoped") return File(settings.folder).absolutePath
        return folderFor(settings, categories, fileName).absolutePath
    }

    fun queueOpen(queue: DeskQueue, hour: Int): Boolean {
        if (queue.fromHour < 0 || queue.toHour < 0) return true
        return if (queue.fromHour <= queue.toHour) {
            hour in queue.fromHour until queue.toHour
        } else {
            hour >= queue.fromHour || hour < queue.toHour
        }
    }

    fun hostOf(url: String): String {
        val raw = url.substringAfter("://").substringBefore('/').substringBefore('?')
        return raw.substringAfter('@').substringBefore(':').lowercase()
    }

    fun hostLimit(settings: DeskSettings, host: String): Pair<Int, Long>? {
        settings.hostRules.lineSequence().forEach { line ->
            val bits = line.trim().split(Regex("\\s+"))
            if (bits.size >= 2 && bits[0].equals(host, ignoreCase = true)) {
                val connections = bits[1].toIntOrNull() ?: return null
                val speedKb = bits.getOrNull(2)?.toLongOrNull() ?: 0L
                return connections.coerceIn(1, 32) to speedKb * 1024L
            }
        }
        return null
    }

    fun decodeUrl(value: String): String = URLDecoder.decode(value, "UTF-8")

    fun exportText(): String {
        return SettingsDocument.export(settingsMap())
    }

    fun importText(text: String): Boolean {
        val current = settingsMap()
        val next = SettingsDocument.import(current, text)
        if (next == current) return false
        val properties = Properties()
        next.forEach { (key, value) -> properties.setProperty(key, value) }
        root.mkdirs()
        settingsFile.outputStream().use { properties.store(it, "Span desktop settings") }
        return true
    }

    private fun settingsMap(): Map<String, String> {
        if (!settingsFile.exists()) return emptyMap()
        val properties = Properties()
        settingsFile.inputStream().use { properties.load(it) }
        return properties.stringPropertyNames().associateWith { properties.getProperty(it) }
    }

    private fun encode(value: String): String {
        return value.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")
    }

    private fun decode(value: String): String {
        val out = StringBuilder()
        var escaped = false
        value.forEach { char ->
            if (escaped) {
                out.append(if (char == 't') '\t' else if (char == 'n') '\n' else char)
                escaped = false
            } else if (char == '\\') {
                escaped = true
            } else {
                out.append(char)
            }
        }
        return out.toString()
    }
}
