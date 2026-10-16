package com.miaadrajabi.span

import androidx.compose.ui.input.pointer.pointerHoverIcon

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Checkbox
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPainter
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.jetbrains.skia.Image as SkiaImage
import java.awt.Desktop
import java.awt.datatransfer.DataFlavor
import java.awt.image.BufferedImage
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.util.Calendar
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

internal class DeskJob(
    val id: Long,
    val url: String,
    fileName: String,
    state: String,
    val addedAt: Long,
    queue: String,
    connections: Int,
    speedLimit: Long,
    checksum: String,
    algorithm: String,
    path: String,
    message: String,
) {
    var fileName by mutableStateOf(fileName)
    var state by mutableStateOf(state)
    var queue by mutableStateOf(queue)
    var connections by mutableStateOf(connections)
    var speedLimit by mutableStateOf(speedLimit)
    var checksum by mutableStateOf(checksum)
    var algorithm by mutableStateOf(algorithm)
    var path by mutableStateOf(path)
    var message by mutableStateOf(message)
    var bytes by mutableStateOf(0L)
    var reserved by mutableStateOf(0L)
    var total by mutableStateOf<Long?>(null)
    var speed by mutableStateOf(0L)
    var heldSpeed by mutableStateOf(0L)
    var picked by mutableStateOf(false)
    var authorization by mutableStateOf("")
    var parts by mutableStateOf<List<SpanPart>>(emptyList())
    var expanded by mutableStateOf(false)
    var tries by mutableStateOf(0)
    var notBefore by mutableStateOf(0L)
    var cancel = SpanCancel()
    var worker: kotlinx.coroutines.Job? = null
    var mediaPage = ""
    var mediaFormat = ""
    var mediaStream: WatchStream? = null
    var priority by mutableStateOf(0)
    var waits by mutableStateOf(0)
    var stallBytes by mutableStateOf(0L)
    var stallReserved by mutableStateOf(0L)
    var stallAt by mutableStateOf(0L)
    var batteryHold by mutableStateOf(false)
    var boost by mutableStateOf<Int?>(null)
    var boostUntil by mutableStateOf(0L)
    var checkpoints by mutableStateOf(0)
    var paceRing by mutableStateOf<List<Pair<Long, Long>>>(emptyList())
    var phase by mutableStateOf(TransferPhase.Receiving)
    var notes by mutableStateOf(listOf("Added"))
    private var sample = 0L
    private var stamp = 0L

    fun apply(progress: SpanProgress) {
        if (state != "Running") return
        val now = System.currentTimeMillis()
        if (stamp > 0L && now - stamp > 350L) {
            speed = ((progress.bytes - sample) * 1000L / (now - stamp)).coerceAtLeast(0L)
            if (speed > 0L) heldSpeed = speed
            paceRing = recentPace(paceRing + (now to speed), now).takeLast(180)
            sample = progress.bytes
            stamp = now
        } else if (stamp == 0L) {
            sample = progress.bytes
            stamp = now
        }
        if (progress.bytes > bytes) checkpoints += 1
        bytes = progress.bytes
        reserved = progress.reservedBytes
        total = progress.total
        parts = progress.parts
        phase = progress.phase
        notes = ActivityLog.next(notes, TransferStage.label(state, phase, bytes, total, message))
    }

    fun mark(extra: String? = null) {
        notes = ActivityLog.next(notes, extra ?: TransferStage.label(state, phase, bytes, total, message))
    }

    fun snapshot() = StoredJob(
        id, url, fileName, state, addedAt, queue, connections, speedLimit, checksum, algorithm, path, message, mediaPage, mediaFormat,
    )
}

private val startupLinks = mutableListOf<String>()
private val bridgeLinks = java.util.concurrent.ConcurrentLinkedQueue<String>()

fun main(args: Array<String>) {
    startupLinks += args.filter { it.startsWith("http://") || it.startsWith("https://") }
    if (args.contains("--native-messaging")) listenForBrowser()
    application {
        val tray = rememberTrayState()
        var shown by remember { mutableStateOf(true) }
        SpanWindow(tray, { shown = true }, { shown = false }, { exitApplication() }, shown)
    }
}

@Composable
private fun androidx.compose.ui.window.ApplicationScope.SpanWindow(
    tray: androidx.compose.ui.window.TrayState,
    onShow: () -> Unit,
    onHide: () -> Unit,
    onExit: () -> Unit,
    shown: Boolean,
) {
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf(DeskLibrary.loadSettings()) }
    val jobs = remember {
        mutableStateListOf<DeskJob>().apply {
            addAll(
                DeskLibrary.loadJobs().map { stored ->
                    DeskJob(
                        stored.id,
                        stored.url,
                        stored.fileName,
                        if (ReconnectPlan.resume(stored.state)) "Queued" else stored.state,
                        stored.addedAt,
                        stored.queue,
                        stored.connections,
                        stored.speedLimit,
                        stored.checksum,
                        stored.algorithm,
                        stored.path,
                        stored.message,
                    ).also { it.batteryHold = stored.message == BatteryPlan.PAUSED; it.mediaPage = stored.mediaPage; it.mediaFormat = stored.mediaFormat }
                },
            )
        }
    }
    val queues = remember { mutableStateListOf<DeskQueue>().apply { addAll(DeskLibrary.loadQueues()) } }
    val categories = remember { mutableStateListOf<DeskCategory>().apply { addAll(DeskLibrary.loadCategories()) } }
    var search by remember { mutableStateOf("") }
    var library by remember { mutableStateOf("All downloads") }
    var shell by remember { mutableStateOf(resolveShell(ShellMode.Standard, false, settings.windowWidth.toFloat(), settings.windowHeight.toFloat())) }
    var manualWidget by remember { mutableStateOf(false) }
    var cards by remember { mutableStateOf(settings.viewCards) }
    var comfortable by remember { mutableStateOf(settings.comfortable) }
    var power by remember { mutableStateOf<Boolean?>(null) }
    var palette by remember { mutableStateOf(false) }
    var paletteQuery by remember { mutableStateOf("") }
    val searchFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val listState = rememberLazyListState()
    val tableState = rememberLazyListState()
    var inspectorOpen by remember { mutableStateOf(false) }
    var inspectorTab by remember { mutableStateOf("Overview") }
    var pinnedId by remember { mutableStateOf<Long?>(null) }
    var toldBackground by remember { mutableStateOf(false) }
    val initialSize = remember {
        val screen = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
        val fitted = clampSize(settings.windowWidth.toFloat(), settings.windowHeight.toFloat(), screen.width.toFloat(), screen.height.toFloat())
        DpSize(fitted.first.dp, fitted.second.dp)
    }
    val windowState = rememberWindowState(size = initialSize, position = WindowPosition(Alignment.Center))
    var fullSize by remember { mutableStateOf(initialSize) }
    var queueFilter by remember { mutableStateOf("All") }
    var sort by remember { mutableStateOf("added") }
    var ascending by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var addUrl by remember { mutableStateOf("") }
    var showBatch by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    var calendarQueue by remember { mutableStateOf<DeskQueue?>(null) }
    var showCategory by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    var deleteFile by remember { mutableStateOf(false) }
    var undoJobs by remember { mutableStateOf<List<DeskJob>>(emptyList()) }
    var nameColumn by remember { mutableStateOf(settings.nameColumnDp.coerceIn(80, 420)) }
    var sleepAt by remember { mutableStateOf(0L) }
    var hadWork by remember { mutableStateOf(false) }
    var previewUrl by remember { mutableStateOf<String?>(null) }
    var mediaPlatform by remember { mutableStateOf("YouTube") }
    var extractFile by remember { mutableStateOf<File?>(null) }
    var noticeAt by remember { mutableStateOf(0L) }
    val ink = inkOf(settings.theme)
    val selected = jobs.firstOrNull { it.id == selectedId }

    fun persist() {
        DeskLibrary.saveJobs(jobs.map { it.snapshot() })
    }

    fun enqueue(
        url: String,
        fileName: String = "",
        folder: String = "",
        queue: String = queues.firstOrNull()?.name ?: "Main",
        threads: Int = settings.threads,
        speedKb: Int = settings.speedLimitKb,
        checksum: String = "",
        algorithm: String = "SHA256",
        authorization: String = "",
        force: Boolean = false,
        mediaPage: String = "",
        mediaStream: WatchStream? = null,
    ) {
        val clean = url.trim()
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) return
        if (!force && WatchPage.isMedia(clean)) {
            previewUrl = clean
            return
        }
        if (jobs.any { it.url == clean && it.state != "Saved" && it.state != "Cancelled" && it.state != "Stopped" }) return
        val guessed = clean.substringBefore('?').substringAfterLast('/').ifBlank { "download.bin" }
        val name = SpanRules.safeFileName(fileName.ifBlank { guessed })
        val defaultQueue = queues.firstOrNull()?.name ?: "Main"
        val extension = name.substringAfterLast('.', "")
        val ruled = DeskLibrary.loadRules().firstNotNullOfOrNull { rule ->
            if (!rule.start.equals(extension, true)) null
            else RuleBook.queueOf(rule.start, rule.nextStep)?.takeIf { wanted -> queues.any { it.name == wanted } }
        }
        val chosenQueue = if (queue == defaultQueue && ruled != null) ruled else queue
        val dir = DeskLibrary.resolveFolder(settings, categories, name, folder)
        jobs.add(
            DeskJob(
                id = System.nanoTime(),
                url = clean,
                fileName = name,
                state = "Queued",
                addedAt = System.currentTimeMillis(),
                queue = chosenQueue,
                connections = threads.coerceIn(1, 32),
                speedLimit = speedKb.coerceAtLeast(0).toLong() * 1024L,
                checksum = checksum.trim(),
                algorithm = algorithm,
                path = dir,
                message = "",
            ).also { it.authorization = authorization; it.mediaPage = mediaPage; it.mediaFormat = mediaStream?.formatId.orEmpty(); it.mediaStream = mediaStream },
        )
        persist()
    }

    fun pause(job: DeskJob) {
        if (job.speed > 0L) job.heldSpeed = job.speed
        job.state = "Paused"
        job.speed = 0L
        job.cancel.cancel()
        job.mark()
        persist()
    }

    fun stop(job: DeskJob) {
        job.cancel.cancel()
        job.state = "Stopped"
        job.speed = 0L
        job.mark()
        persist()
    }

    fun pauseRunning() {
        val chosen = jobs.filter { it.picked && it.state == "Running" }
        val targets = if (jobs.none { it.picked }) jobs.filter { it.state == "Running" } else chosen
        targets.forEach { pause(it) }
    }

    fun resumeEligible() {
        val chosen = jobs.filter { it.picked && it.state == "Paused" }
        val targets = if (jobs.none { it.picked }) jobs.filter { it.state == "Paused" } else chosen
        targets.forEach {
            it.notBefore = 0L
            it.state = "Queued"
        }
        persist()
    }

    fun rememberRemoval(job: DeskJob) {
        val safe = HandoffBundle.pack(
            mapOf(
                "name" to job.fileName,
                "path" to File(job.path, job.fileName).absolutePath,
                "authorization" to job.authorization,
            ),
        )
        FetchHistory(File(System.getProperty("user.home"), "Span")).add(safe["name"] ?: job.fileName, safe["path"].orEmpty())
    }

    fun dropFromList(targets: List<DeskJob>) {
        val kept = mutableListOf<DeskJob>()
        targets.forEach { job ->
            job.cancel.cancel()
            if (deleteFile) erase(job) else kept += job
            rememberRemoval(job)
            jobs.remove(job)
            if (selectedId == job.id) selectedId = null
        }
        undoJobs = keptForUndo(deleteFile, kept)
        persist()
    }

    fun reorderQueues(from: Int, to: Int) {
        val next = placeQueue(queues.toList(), from, to)
        if (next == queues.toList()) return
        queues.clear()
        queues.addAll(next)
        DeskLibrary.saveQueues(queues)
    }

    fun removePicked() {
        val chosen = jobs.filter { it.picked }
        val targets = if (chosen.isEmpty()) listOfNotNull(selected) else chosen
        dropFromList(targets)
    }

    fun start(job: DeskJob) {
        if (!WakePlan.shouldStart(job.state) || job.worker?.isActive == true) return
        if (settings.quotaMb > 0) {
            val useful = jobs.sumOf { it.bytes }
            if (QuotaUse.left(settings.quotaMb.toLong() * 1024L * 1024L, useful) == 0L) {
                job.message = "Quota reached. Retries are not included."
                persist()
                return
            }
        }
        val stalled = job.message.startsWith("Stalled")
        val target = File(job.path)
        target.mkdirs()
        if (settings.checkSpace && target.usableSpace < settings.minFreeMb.coerceAtLeast(1) * 1024L * 1024L) {
            job.state = "Failed"
            job.message = "Not enough free space."
            persist()
            return
        }
        val existing = File(job.path, job.fileName)
        val partial = File(job.path, job.fileName + ".span").exists() || File(job.path, job.fileName + ".span-plan").exists() || File(job.path, job.fileName + ".span-media").isDirectory
        if (existing.exists() && !partial && job.bytes == 0L) {
            if (settings.overwrite) existing.delete() else job.fileName = uniqueName(job.fileName, job.path)
        }
        job.state = "Running"
        job.message = ""
        job.cancel = SpanCancel()
        val token = job.cancel
        val hostRule = DeskLibrary.hostLimit(settings, DeskLibrary.hostOf(job.url))
        val requested = NetworkProfile.connectionCap(
            settings.networkProfile,
            when {
                !settings.parallel -> 1
                hostRule != null -> hostRule.first
                else -> job.connections
            },
        )
        val connections = AdaptiveConnections.choose(
            settings.adaptiveConnections,
            requested,
            settings.threads.coerceAtLeast(1),
            stalled,
        )
        val request = SpanRequest(
            url = job.url,
            fileName = job.fileName,
            headers = buildMap {
                val agent = if (job.url.contains("googlevideo.com")) WatchPage.agentFor(job.url) else settings.userAgent.ifBlank { "Fetch" }
                put("User-Agent", agent)
                put("Referer", WatchPage.refererFor(job.url))
                if (job.authorization.isNotBlank()) put("Authorization", job.authorization)
            },
            expectedChecksum = if (settings.verifyChecksum) job.checksum.ifBlank { null } else null,
            checksumAlgorithm = when (job.algorithm.uppercase()) {
                "MD5" -> ChecksumAlgorithm.MD5
                "SHA512" -> ChecksumAlgorithm.SHA512
                else -> ChecksumAlgorithm.SHA256
            },
            connections = connections,
            speedLimitBytesPerSec = hostRule?.second?.takeIf { it > 0L } ?: job.speedLimit,
            minPartBytes = settings.minChunkKb.coerceAtLeast(64) * 1024L,
            writePolicy = when (settings.writePolicy) {
                "Local" -> WritePolicy.Local
                "Removable" -> WritePolicy.Removable
                "Network" -> WritePolicy.Network
                else -> WritePolicy.Auto
            },
            latencyAware = settings.latencyAware,
        )
        val client = clientFor(settings)
        val downloader = OkHttpSpanDownloader(client, log = SpanFileLog(File(System.getProperty("user.home"), "Span")))
        job.worker = scope.launch(Dispatchers.IO) {
            val outcome = try {
                val media = if (job.mediaPage.isNotBlank() && WatchPage.isMedia(job.mediaPage)) {
                    job.mediaStream?.takeIf { job.tries == 0 } ?: WatchPage.load(job.mediaPage, token).streams.firstOrNull { it.formatId == job.mediaFormat }
                        ?.also { job.mediaStream = it } ?: error("This quality is no longer available. Open the video in Media Studio again.")
                } else job.mediaStream
                val report: (SpanProgress) -> Unit = { progress -> javax.swing.SwingUtilities.invokeLater { if (job.cancel === token) job.apply(progress) } }
                if (media == null) downloader.download(request, job.path, token, report)
                else downloadMedia(media, request, job.path, token, downloader, report)
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException && !token.cancelled) throw error
                if (token.cancelled) SpanOutcome.Cancelled else SpanOutcome.Failed(error.message ?: "The transfer could not finish.")
            }
            withContext(Dispatchers.Swing) {
                if (job.cancel !== token || job !in jobs) return@withContext
                when (outcome) {
                    is SpanOutcome.Saved -> {
                        val problem = savedProblem(File(outcome.path), job, settings, if (job.mediaStream?.audio != null) "video/mp4" else downloader.lastContentType)
                        if (problem != null) {
                            File(outcome.path).delete()
                            job.state = "Failed"
                            job.message = problem
                            job.mark(problem)
                        } else {
                            job.state = "Saved"
                            job.phase = TransferPhase.Ready
                            job.bytes = outcome.bytes
                            job.reserved = outcome.bytes
                            job.speed = 0L
                            job.message = ""
                            job.tries = 0
                            job.mark()
                            tray.sendNotification(Notification(settings.channelName.ifBlank { "Fetch" }, "${job.fileName} finished"))
                            if (settings.promptOpen) openFolder(File(outcome.path))
                        }
                    }
                    is SpanOutcome.Failed -> {
                        val next = job.tries + 1
                        job.tries = next
                        if (next < settings.retryAttempts.coerceAtLeast(1)) {
                            val factor = Math.pow(settings.backoffTenths.coerceIn(10, 30) / 10.0, (next - 1).toDouble())
                            val backoff = (settings.retryDelaySeconds.coerceAtLeast(1) * 1000.0 * factor).toLong()
                            val wait = maxOf(backoff, outcome.retryAfterMs ?: 0L)
                            job.notBefore = System.currentTimeMillis() + wait
                            job.state = "Queued"
                            job.message = if (outcome.retryAfterMs != null) "Waiting ${wait / 1000}s for the server" else "Retry $next"
                            job.mark(job.message)
                        } else {
                            job.state = "Failed"
                            job.message = outcome.message
                            job.mark(outcome.message)
                        }
                    }
                    SpanOutcome.Cancelled -> {
                        if (job.state == "Running") job.state = "Paused"
                        if (job.state != "Paused") job.speed = 0L
                        job.mark()
                    }
                }
                persist()
            }
        }
    }

    LaunchedEffect(Unit) {
        startupLinks.forEach { enqueue(it) }
        startupLinks.clear()
        while (isActive) {
            while (true) {
                val link = bridgeLinks.poll() ?: break
                enqueue(link)
            }
            delay(400)
            power = if (settings.pauseOnBattery) MacBattery.onBattery() else null
            jobs.forEach { job ->
                val next = BatteryPlan.nextState(settings.pauseOnBattery, power, job.state, job.batteryHold)
                if (next == "Paused" && job.state != "Paused") {
                    job.batteryHold = true
                    job.cancel.cancel()
                    job.state = "Paused"
                    job.message = BatteryPlan.PAUSED
                    job.mark(BatteryPlan.PAUSED)
                } else if (next == "Queued" && job.batteryHold) {
                    job.batteryHold = false
                    job.message = ""
                    job.notBefore = 0L
                    job.state = "Queued"
                }
            }
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            if (jobs.any { it.state == "Running" || it.state == "Queued" }) hadWork = true
            if (hadWork && jobs.none { it.state == "Running" || it.state == "Queued" }) {
                hadWork = false
                if (settings.onComplete == "sleep" || settings.onComplete == "shutdown") {
                    sleepAt = System.currentTimeMillis() + 10_000L
                } else tray.sendNotification(Notification(settings.channelName.ifBlank { "Fetch" }, "The queue is empty"))
            }
            if (settings.persistent && settings.showProgress) {
                val now = System.currentTimeMillis()
                val running = jobs.filter { it.state == "Running" }
                if (running.isNotEmpty() && now - noticeAt > 8000L) {
                    noticeAt = now
                    val line = running.joinToString { job ->
                        val pct = job.total?.takeIf { it > 0L }?.let { (job.bytes * 100L / it).toInt() }
                        if (pct == null) job.fileName else "${job.fileName} $pct%"
                    }
                    tray.sendNotification(Notification(settings.channelName.ifBlank { "Fetch" }, line))
                }
            }
            jobs.filter { it.state == "Running" }.forEach { job ->
                val now = System.currentTimeMillis()
                if (job.stallAt == 0L) {
                    job.stallBytes = job.bytes
                    job.stallReserved = job.reserved
                    job.stallAt = now
                } else {
                    val kind = StallWatch.judge(job.stallBytes, job.bytes, job.stallReserved, job.reserved, now - job.stallAt)
                    if (kind == StallKind.Stalled && !job.message.startsWith("Stalled")) {
                        job.message = "Stalled. The connection is idle."
                        job.mark(job.message)
                    }
                    if (kind != StallKind.Stalled && job.message.startsWith("Stalled")) job.message = ""
                    if (job.bytes != job.stallBytes || job.reserved != job.stallReserved) {
                        job.stallBytes = job.bytes
                        job.stallReserved = job.reserved
                        job.stallAt = now
                    }
                }
            }
            val room = settings.maxConcurrent.coerceAtLeast(1) - jobs.count { it.state == "Running" }
            val ready = jobs.mapNotNull { job ->
                val queue = queues.firstOrNull { it.name == job.queue }
                val host = DeskLibrary.hostOf(job.url)
                val cap = DeskLibrary.hostLimit(settings, host)?.first ?: settings.hostCap.coerceAtLeast(1)
                val allowed = Admission.ready(
                    queued = job.state == "Queued",
                    notBefore = job.notBefore,
                    now = System.currentTimeMillis(),
                    queueAllows = queue == null || !settings.useAlarm || DeskLibrary.queueOpen(queue, hour),
                    runningInQueue = if (queue == null) 0 else jobs.count { it.state == "Running" && it.queue == queue.name },
                    queueCap = queue?.maxAtOnce ?: Int.MAX_VALUE,
                    runningOnHost = jobs.count { it.state == "Running" && DeskLibrary.hostOf(it.url) == host },
                    hostCap = cap,
                )
                if (!allowed) return@mapNotNull null
                FairCandidate(job.id, TemporaryPriority.effective(job.priority, job.boost, job.boostUntil, System.currentTimeMillis()), job.waits)
            }
            val picked = FairShare.pick(ready, room).toSet()
            val readyIds = ready.map { it.id }.toSet()
            jobs.forEach { job ->
                if (job.id in readyIds && job.id !in picked) job.waits += 1
                if (job.id in picked) job.waits = 0
            }
            picked.forEach { id -> jobs.firstOrNull { it.id == id }?.let { start(it) } }
        }
    }

    LaunchedEffect(settings.clipboard) {
        if (!settings.clipboard) return@LaunchedEffect
        var last = ""
        while (isActive) {
            delay(1200)
            val text = readClipboard()?.trim().orEmpty()
            if (text == last) continue
            last = text
            if (text.startsWith("http://") || text.startsWith("https://")) previewUrl = text
        }
    }

    LaunchedEffect(settings.port) {
        val port = settings.port
        if (port !in 1..65535) return@LaunchedEffect
        val server = withContext(Dispatchers.IO) { ServerSocket(port) }
        try {
            while (isActive) {
                val socket = withContext(Dispatchers.IO) { server.accept() }
                scope.launch(Dispatchers.IO) {
                    socket.use { client ->
                        val line = client.getInputStream().bufferedReader().readLine().orEmpty()
                        val raw = line.substringAfter("url=", "").substringBefore(" ").substringBefore("&")
                        val token = line.substringAfter("token=", "").substringBefore(" ").substringBefore("&")
                        val allowed = LocalGate.allow("127.0.0.1", runCatching { DeskLibrary.decodeUrl(token) }.getOrDefault(""), settings.localToken)
                        val url = DeskLibrary.decodeUrl(raw)
                        if (allowed && (url.startsWith("http://") || url.startsWith("https://"))) {
                            withContext(Dispatchers.Swing) { enqueue(url) }
                        }
                        val body = if (allowed) "ok" else "A local token is required."
                        val status = if (allowed) "200 OK" else "403 Forbidden"
                        val head = "HTTP/1.1 $status\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n"
                        client.getOutputStream().write((head + body).toByteArray())
                    }
                }
            }
        } finally {
            server.close()
        }
    }

    LaunchedEffect(sleepAt) {
        val mark = sleepAt
        if (mark == 0L) return@LaunchedEffect
        delay(10_000)
        val cancelled = sleepAt != mark
        val active = jobs.any { it.state == "Running" || it.state == "Queued" }
        if (!CompletionPlan.proceed(cancelled, active)) return@LaunchedEffect
        sleepAt = 0L
        if (System.getProperty("os.name").contains("Mac")) {
            val script = if (settings.onComplete == "shutdown") {
                "tell application \"System Events\" to shut down"
            } else {
                "tell application \"System Events\" to sleep"
            }
            Runtime.getRuntime().exec(arrayOf("osascript", "-e", script))
        } else {
            tray.sendNotification(Notification("Fetch", "Sleep and shut down after the queue are available on macOS"))
        }
    }

    val visible = libraryRows(jobs, categories, library, queueFilter, search, sort, ascending)

    LaunchedEffect(settings.sidebarCollapsed, settings.sidebarWidth) {
        delay(250)
        DeskLibrary.saveSettings(settings)
    }

    LaunchedEffect(windowState.size, manualWidget, cards, comfortable) {
        val width = windowState.size.width
        val height = windowState.size.height
        delay(120)
        if (windowState.size.width != width || windowState.size.height != height) return@LaunchedEffect
        val next = resolveShell(shell, manualWidget, width.value, height.value)
        if (shell != ShellMode.Widget && next != ShellMode.Widget) {
            fullSize = windowState.size
            val screen = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
            val fitted = clampSize(width.value, height.value, screen.width.toFloat(), screen.height.toFloat())
            settings = settings.copy(windowWidth = fitted.first.toInt(), windowHeight = fitted.second.toInt(), viewCards = cards, comfortable = comfortable)
            DeskLibrary.saveSettings(settings)
        }
        shell = next
    }

    LaunchedEffect(showAdd, showBatch, showSettings) {
        if ((showAdd || showBatch || showSettings) && (manualWidget || shell == ShellMode.Widget)) {
            manualWidget = false
            windowState.size = DpSize(maxOf(fullSize.width, 680.dp), maxOf(fullSize.height, 480.dp))
        }
    }

    val activeCount = jobs.count { it.state == "Running" }
    val pausedCount = jobs.count { it.state == "Paused" }
    val queuedCount = jobs.count { it.state == "Queued" }
    val failedCount = jobs.count { it.state == "Failed" }
    val liveSpeed = jobs.filter { it.state == "Running" }.sumOf { it.speed }
    val trayIcon = remember(settings.iconKey, activeCount > 0, pausedCount > 0) { trayPainter(settings.iconKey, activeCount > 0, pausedCount > 0) }
    Tray(
        state = tray, icon = trayIcon,
        tooltip = "Fetch · $activeCount active · ${bytesOf(liveSpeed)}/s",
        onAction = onShow,
        menu = {
            Item("$activeCount active · $queuedCount queued · ${bytesOf(liveSpeed)}/s", enabled = false, onClick = {})
            Separator()
            Item("Open Fetch", onClick = onShow)
            Item("Add download…", onClick = { addUrl = ""; showAdd = true; onShow() })
            Item("Paste download link…", onClick = { addUrl = readClipboard()?.trim().orEmpty(); showAdd = true; onShow() })
            Item("Add batch…", onClick = { showBatch = true; onShow() })
            Separator()
            Item("Pause all downloads", enabled = activeCount + queuedCount > 0, onClick = {
                jobs.filter { it.state == "Running" || it.state == "Queued" }.forEach { pause(it) }
            })
            Item("Resume all paused ($pausedCount)", enabled = pausedCount > 0, onClick = {
                jobs.filter { it.state == "Paused" }.forEach { it.notBefore = 0L; it.state = "Queued" }; persist()
            })
            Item("Retry failed ($failedCount)", enabled = failedCount > 0, onClick = {
                jobs.filter { it.state == "Failed" }.forEach { it.notBefore = 0L; it.state = "Queued" }; persist()
            })
            Item("Open downloads folder", onClick = { openFolder(File(settings.folder)) })
            Menu("Recent downloads", enabled = jobs.any { it.state == "Saved" }) {
                jobs.filter { it.state == "Saved" }.sortedByDescending { it.addedAt }.take(5).forEach { job ->
                    Item(job.fileName, onClick = {
                        val file = File(job.path, job.fileName)
                        if (file.isFile) runCatching { Desktop.getDesktop().open(file) }
                        else { selectedId = job.id; inspectorOpen = true; onShow() }
                    })
                }
            }
            Separator()
            CheckboxItem("Always on top", checked = settings.alwaysOnTop, onCheckedChange = {
                settings = settings.copy(alwaysOnTop = it); DeskLibrary.saveSettings(settings)
            })
            CheckboxItem("Watch clipboard", checked = settings.clipboard, onCheckedChange = {
                settings = settings.copy(clipboard = it); DeskLibrary.saveSettings(settings)
            })
            Item(if (manualWidget) "Expand window" else "Mini window", onClick = {
                if (manualWidget) { manualWidget = false; windowState.size = fullSize }
                else { fullSize = windowState.size; manualWidget = true; windowState.size = DpSize(380.dp, 280.dp) }
                onShow()
            })
            Item("Settings…", onClick = { showSettings = true; onShow() })
            Separator()
            Item("Quit Fetch", onClick = {
                if (activeCount + queuedCount > 0) { confirmExit = true; onShow() } else onExit()
            })
        },
    )

    FetchTheme(ink) {
        Window(
            onCloseRequest = {
                onHide()
                if (!toldBackground) {
                    tray.sendNotification(Notification("Fetch", "Fetch is still running in the menu bar. Quit there to exit."))
                    toldBackground = true
                }
            },
            title = "Fetch",
            icon = remember { fetchIcon() },
            state = windowState,
            visible = shown,
            alwaysOnTop = settings.alwaysOnTop,
            onPreviewKeyEvent = { event ->
                val command = event.isCtrlPressed || event.isMetaPressed
                if (event.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@Window false
                if (event.isAltPressed && !command) {
                    val index = queues.indexOfFirst { it.name == queueFilter }
                    if (index < 0) return@Window false
                    when (event.key) {
                        androidx.compose.ui.input.key.Key.DirectionUp -> {
                            reorderQueues(index, index - 1)
                            true
                        }
                        androidx.compose.ui.input.key.Key.DirectionDown -> {
                            reorderQueues(index, index + 1)
                            true
                        }
                        else -> false
                    }
                } else if (!command) {
                    false
                } else when (event.key) {
                    androidx.compose.ui.input.key.Key.N -> {
                        showAdd = true
                        true
                    }
                    androidx.compose.ui.input.key.Key.F -> {
                        if (shell != ShellMode.Widget) searchFocus.requestFocus()
                        true
                    }
                    androidx.compose.ui.input.key.Key.K -> {
                        palette = true
                        paletteQuery = ""
                        true
                    }
                    androidx.compose.ui.input.key.Key.M -> if (event.isShiftPressed) {
                        if (shell == ShellMode.Widget && manualWidget) {
                            manualWidget = false
                            windowState.size = fullSize
                        } else {
                            fullSize = windowState.size
                            manualWidget = true
                            windowState.size = DpSize(380.dp, 240.dp)
                        }
                        true
                    } else {
                        false
                    }
                    else -> false
                }
            },
        ) {
            MenuBar {
                Menu("File") {
                    Item("Add URL", onClick = { showAdd = true })
                    Item("Add batch", onClick = { showBatch = true })
                    Item("Settings", onClick = { showSettings = true })
                    Item("Open folder", onClick = { openFolder(File(selected?.path ?: settings.folder)) })
                    Item("Quit", onClick = {
                        if (jobs.any { it.state == "Running" || it.state == "Queued" }) confirmExit = true else onExit()
                    })
                }
                Menu("Tasks") {
                    Item("Start queue", onClick = {
                        val chosen = if (queueFilter == "All") queues.firstOrNull()?.name else queueFilter
                        jobs.filter { it.queue == chosen && (it.state == "Paused" || it.state == "Failed") }
                            .forEach { it.state = "Queued" }
                        persist()
                    })
                    Item("Stop queue", onClick = {
                        val chosen = if (queueFilter == "All") null else queueFilter
                        jobs.filter { (chosen == null || it.queue == chosen) && (it.state == "Running" || it.state == "Queued") }
                            .forEach { stop(it) }
                        persist()
                    })
                    Item("Pause", onClick = { selected?.let { pause(it) } })
                    Item("Stop", onClick = { selected?.let { stop(it) } })
                    Item("Stop all", onClick = {
                        jobs.forEach { if (it.state == "Running" || it.state == "Queued") stop(it) }
                        persist()
                    })
                    Item("Resume failed", onClick = {
                        jobs.filter { it.state == "Failed" }.forEach { it.state = "Queued" }
                        persist()
                    })
                }
                Menu("View") {
                    Item("Reset window size", onClick = {
                        manualWidget = false
                        windowState.placement = androidx.compose.ui.window.WindowPlacement.Floating
                        val screen = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
                        val fitted = clampSize(DEFAULT_WINDOW_WIDTH.toFloat(), DEFAULT_WINDOW_HEIGHT.toFloat(), screen.width.toFloat(), screen.height.toFloat())
                        windowState.size = DpSize(fitted.first.dp, fitted.second.dp)
                        windowState.position = WindowPosition(Alignment.Center)
                    })
                }
                Menu("Tools") {
                    Item("Watch clipboard", onClick = {
                        settings = settings.copy(clipboard = !settings.clipboard)
                        DeskLibrary.saveSettings(settings)
                    })
                    Item("New queue", onClick = { showQueue = true })
                    Item("Rules", onClick = { showRules = true })
                    Item("New category", onClick = { showCategory = true })
                    Item("Export settings", onClick = {
                        val chooser = javax.swing.JFileChooser()
                        chooser.dialogTitle = "Export settings"
                        if (chooser.showSaveDialog(null) != javax.swing.JFileChooser.APPROVE_OPTION) return@Item
                        chooser.selectedFile.writeText(DeskLibrary.exportText())
                    })
                    Item("Import settings", onClick = {
                        val chooser = javax.swing.JFileChooser()
                        chooser.dialogTitle = "Import settings"
                        if (chooser.showOpenDialog(null) != javax.swing.JFileChooser.APPROVE_OPTION) return@Item
                        val text = chooser.selectedFile.readText()
                        if (DeskLibrary.importText(text)) settings = DeskLibrary.loadSettings()
                    })
                    Item("Clear history", onClick = {
                        FetchHistory(File(System.getProperty("user.home"), "Span")).clear()
                    })
                }
                Menu("Help") { Item("About", onClick = { showAbout = true }) }
            }
            LaunchedEffect(Unit) {
                window.minimumSize = java.awt.Dimension(360, 280)
                window.dropTarget = java.awt.dnd.DropTarget(window, object : java.awt.dnd.DropTargetAdapter() {
                    override fun drop(event: java.awt.dnd.DropTargetDropEvent) {
                        try {
                            event.acceptDrop(java.awt.dnd.DnDConstants.ACTION_COPY)
                            val data = event.transferable
                            val links = mutableListOf<String>()
                            if (data.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.javaFileListFlavor)) {
                                @Suppress("UNCHECKED_CAST")
                                val files = data.getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor) as List<File>
                                files.forEach { file ->
                                    if (DropIntake.executable(file.name)) return@forEach
                                    if (file.isFile && file.length() < 1_000_000L) links += DropIntake.fromFile(file.name, runCatching { file.readText() }.getOrDefault(""))
                                }
                            } else if (data.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.stringFlavor)) {
                                links += DropIntake.links(data.getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor) as String)
                            }
                            javax.swing.SwingUtilities.invokeLater { links.distinct().forEach { enqueue(it) } }
                            event.dropComplete(true)
                        } catch (_: Exception) {
                            event.dropComplete(false)
                        }
                    }
                })
            }
            Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                FrostedBackdrop(ink)
                if (shell == ShellMode.Widget) {
                    WidgetFace(
                        ink, jobs, selected, pinnedId, windowState.size,
                        onPin = { pinnedId = it },
                        onPauseAll = { pauseRunning() },
                        onResume = { resumeEligible() },
                        onAdd = { showAdd = true },
                        onExpand = {
                            manualWidget = false
                            val screen = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
                            val fitted = clampSize(fullSize.width.value, fullSize.height.value, screen.width.toFloat(), screen.height.toFloat())
                            windowState.size = DpSize(fitted.first.dp, fitted.second.dp)
                        },
                        onOpen = { job -> openFolder(File(job.path)) },
                    )
                } else {
                    val runningNow = jobs.any { it.state == "Running" }
                    Column(Modifier.fillMaxSize()) {
                        CommandBar(
                            ink, search, { search = it }, visible.map { it.fileName }, runningNow, shell, searchFocus,
                            onAdd = { showAdd = true },
                            onToggleRun = { if (runningNow) pauseRunning() else resumeEligible() },
                            onMenu = { showBatch = true },
                            onSettings = { showSettings = true },
                            onWidget = {
                                fullSize = windowState.size
                                manualWidget = true
                                windowState.size = DpSize(380.dp, 240.dp)
                            },
                            library = library,
                            onLibrary = { library = it },
                            onMedia = { mediaPlatform = it; previewUrl = "" },
                        )
                        Row(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (windowState.size.width >= 760.dp) {
                                AppNav(
                                    ink, library, queueFilter, jobs, categories, queues, settings.sidebarCollapsed,
                                    { library = it }, { queueFilter = it }, { showQueue = true }, { from, to -> reorderQueues(from, to) }, { calendarQueue = it }, { showSettings = true },
                                    onToggle = { settings = settings.copy(sidebarCollapsed = !settings.sidebarCollapsed) },
                                    expandedWidth = settings.sidebarWidth,
                                    onWidthChange = { settings = settings.copy(sidebarWidth = it.coerceIn(184, 280)) },
                                )
                            }
                            Column(Modifier.weight(1f).fillMaxHeight().padding(vertical = 8.dp)) {
                                ListHead(ink, library, visible.size, cards, comfortable, windowState.size.height, jobs, settings, { cards = it }, { comfortable = it })
                                if (jobs.any { it.picked }) {
                                    val pickedJobs = jobs.filter { it.picked }
                                    SelectionBar(
                                        ink,
                                        pickedJobs.size,
                                        pickedJobs.count { it.state == "Running" },
                                        pickedJobs.count { it.state == "Paused" },
                                        deleteFile,
                                        { jobs.forEach { it.picked = false } },
                                        { pauseRunning() },
                                        { resumeEligible() },
                                        { removePicked() },
                                        { deleteFile = it },
                                    )
                                }
                                if (undoJobs.isNotEmpty()) {
                                    UndoBar(ink, undoJobs.size, {
                                        undoJobs.forEach { job -> if (jobs.none { it.id == job.id }) jobs.add(job) }
                                        undoJobs = emptyList()
                                        persist()
                                    }) { undoJobs = emptyList() }
                                }
                                if (visible.isEmpty()) {
                                    EmptyLibrary(ink, search.isNotBlank(), { search = "" }, { showAdd = true })
                                } else if (cards || shell == ShellMode.Compact) {
                                    DownloadList(ink, listState, Modifier.weight(1f)) {
                                        val grouped = if (library == "Needs attention") visible.groupBy { attentionCause(it) } else mapOf("" to visible)
                                        grouped.forEach { (cause, rows) ->
                                            if (cause.isNotEmpty()) {
                                                item(key = "cause-$cause") {
                                                    Text(cause, color = ink.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                                                }
                                            }
                                            items(rows, key = { it.id }) { job ->
                                            DownloadCard(
                                                job, ink, job.id == selectedId, comfortable && shell != ShellMode.Compact,
                                                onSelect = { selectedId = job.id },
                                                onInspect = { selectedId = job.id; inspectorOpen = true },
                                                onStop = { stop(job) },
                                                onOpenFile = {
                                                    val file = File(job.path, job.fileName)
                                                    if (job.state == "Saved" && file.exists()) java.awt.Desktop.getDesktop().open(file)
                                                },
                                                onPause = { pause(job) },
                                                onStart = { job.notBefore = 0L; job.state = "Queued"; persist() },
                                                onEdit = { selectedId = job.id; if (job.state != "Running") showEdit = true },
                                                onOpen = { openFolder(File(job.path)) },
                                                onRemove = { dropFromList(listOf(job)) },
                                                onExtract = {
                                                    val file = File(job.path, job.fileName)
                                                    if (job.state == "Saved" && file.name.endsWith(".zip", true)) extractFile = file
                                                },
                                            )
                                            }
                                        }
                                    }
                                } else {
                                    TableHead(ink, sort, ascending, visible.isNotEmpty() && visible.all { it.picked }, nameColumn, { mark -> visible.forEach { it.picked = mark } }, { delta ->
                                        val next = columnWidth(nameColumn, delta)
                                        nameColumn = next
                                        settings = settings.copy(nameColumnDp = next)
                                        DeskLibrary.saveSettings(settings)
                                    }) { column ->
                                        if (sort == column) ascending = !ascending else { sort = column; ascending = true }
                                    }
                                    DownloadList(ink, tableState, Modifier.weight(1f)) {
                                        items(visible, key = { it.id }) { job ->
                                            DownloadRow(
                                                job, ink, job.id == selectedId, settings.showProgress, nameColumn,
                                                onClick = { selectedId = job.id },
                                                onInspect = { selectedId = job.id; inspectorOpen = true },
                                                onEdit = { selectedId = job.id; if (job.state != "Running") showEdit = true },
                                                onPause = { pause(job) },
                                                onStop = { stop(job) },
                                                onStart = { job.notBefore = 0L; job.state = "Queued"; persist() },
                                                onOpen = { openFolder(File(job.path)) },
                                                onRemove = { dropFromList(listOf(job)) },
                                                onPreview = { if (WatchPage.isMedia(job.url)) previewUrl = job.url },
                                            )
                                        }
                                    }
                                }
                            }
                            if (shell == ShellMode.Wide && inspectorOpen && selected != null) {
                                Inspector(ink, selected, settings, inspectorTab, { inspectorTab = it }, { inspectorOpen = false }, { openFolder(File(selected.path)) }, {
                                    val file = File(selected.path, selected.fileName)
                                    if (selected.state == "Saved" && file.name.endsWith(".zip", true)) extractFile = file
                                }, { count ->
                                    selected.connections = count
                                    if (selected.state == "Running") {
                                        pause(selected)
                                        selected.state = "Queued"
                                        selected.notBefore = 0L
                                    }
                                    persist()
                                })
                            }
                        }
                        StatusLine(ink, jobs, settings, power)
                    }
                    if (shell != ShellMode.Wide && inspectorOpen && selected != null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
                            Box(Modifier.fillMaxSize().background(ink.night.copy(alpha = 0.60f)).clickable(
                                interactionSource = remember { MutableInteractionSource() }, indication = null,
                            ) { inspectorOpen = false })
                            Inspector(ink, selected, settings, inspectorTab, { inspectorTab = it }, { inspectorOpen = false }, { openFolder(File(selected.path)) }, {
                                val file = File(selected.path, selected.fileName)
                                if (selected.state == "Saved" && file.name.endsWith(".zip", true)) extractFile = file
                            }, { count ->
                                selected.connections = count
                                if (selected.state == "Running") {
                                    pause(selected)
                                    selected.state = "Queued"
                                    selected.notBefore = 0L
                                }
                                persist()
                            })
                        }
                    }
                }
            }
            if (palette) {
                val commands = listOf(
                    "Add download" to { showAdd = true },
                    "Pause all" to { pauseRunning() },
                    "Resume eligible" to { resumeEligible() },
                    "Needs attention" to { library = "Needs attention" },
                    "Settings" to { showSettings = true },
                    "Widget" to {
                        fullSize = windowState.size
                        manualWidget = true
                        windowState.size = DpSize(380.dp, 240.dp)
                    },
                ).filter { it.first.contains(paletteQuery, ignoreCase = true) }
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)).clickable { palette = false })
                    Column(
                        Modifier.align(Alignment.TopCenter).padding(top = 72.dp).width(440.dp).clip(RoundedCornerShape(12.dp)).background(ink.panel).border(1.dp, ink.line, RoundedCornerShape(12.dp)).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        androidx.compose.foundation.text.BasicTextField(
                            paletteQuery,
                            { paletteQuery = it },
                            Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(color = ink.text, fontSize = 14.sp),
                            decorationBox = { inner ->
                                if (paletteQuery.isEmpty()) Text("Search commands…", color = ink.muted, fontSize = 14.sp)
                                inner()
                            },
                        )
                        if (commands.isEmpty()) Text("No matching command.", color = ink.muted, fontSize = 13.sp)
                        commands.forEach { (label, run) ->
                            Text(label, color = ink.text, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { palette = false; run() }.padding(vertical = 8.dp))
                        }
                    }
                }
            }
            if (showAdd) AddDialog(ink, queues.map { it.name }, settings, addUrl) { url, name, folder, queue, threads, speed, checksum, algorithm, authorization ->
                showAdd = false
                addUrl = ""
                if (url != null) enqueue(url, name, folder, queue, threads, speed, checksum, algorithm, authorization)
            }
            if (showBatch) BatchDialog(ink) { urls ->
                showBatch = false
                urls.forEach { enqueue(it) }
            }
            extractFile?.let { archive ->
                ZipExtractDialog(ink, archive) { extractFile = null }
            }
            if (showSettings) SettingsDialog(ink, settings, onTheme = { picked ->
                settings = settings.copy(theme = picked)
                DeskLibrary.saveSettings(settings)
            }) { next ->
                showSettings = false
                if (next != null) {
                    settings = next
                    DeskLibrary.saveSettings(next)
                }
            }
            if (showInfo && selected != null) InfoDialog(ink, selected) { showInfo = false }
            if (showEdit && selected != null) EditDialog(ink, selected, queues.map { it.name }) { name, folder, queue, threads, speed, checksum, algorithm ->
                showEdit = false
                if (name != null) {
                    selected.fileName = SpanRules.safeFileName(name)
                    selected.path = folder.ifBlank { selected.path }
                    selected.queue = queue
                    selected.connections = threads
                    selected.speedLimit = speed * 1024L
                    selected.checksum = checksum
                    selected.algorithm = algorithm
                    persist()
                }
            }
            if (showAbout) AboutDialog(ink) { showAbout = false }
            previewUrl?.let { page ->
                MediaStudioWindow(ink, page, mediaPlatform, settings, queues.map { it.name }, { previewUrl = null }) { stream, title, source, folder, queue ->
                    previewUrl = null
                    enqueue(stream.url, SpanRules.safeFileName(title.ifBlank { "video" } + "." + stream.extension), folder = folder, queue = queue, force = true, mediaPage = source, mediaStream = stream)
                }
            }
            if (settings.floatBubble) FloatingOrb(ink) { previewUrl = it }
            calendarQueue?.let { queue ->
                ScheduleDialog(ink, queue) { calendarQueue = null }
            }
            if (showRules) RuleDialog(ink, DeskLibrary.loadRules()) { saved ->
                showRules = false
                if (saved != null) DeskLibrary.saveRules(saved)
            }
            if (showQueue) QueueDialog(ink) { queue ->
                showQueue = false
                if (queue != null && queues.none { it.name == queue.name }) {
                    queues.add(queue)
                    DeskLibrary.saveQueues(queues)
                }
            }
            if (showCategory) CategoryDialog(ink) { category ->
                showCategory = false
                if (category != null && categories.none { it.name == category.name }) {
                    categories.add(category)
                    DeskLibrary.saveCategories(categories)
                }
            }
            if (confirmExit) ConfirmDialog(ink, "Downloads are still active. Quit anyway?") {
                confirmExit = false
                if (it) onExit()
            }
            if (sleepAt != 0L) ConfirmDialog(
                ink,
                if (settings.onComplete == "shutdown") {
                    "The queue is empty. The Mac will shut down unless you cancel."
                            } else {
                    "The queue is empty. The Mac will sleep unless you cancel."
                },
            ) { proceed ->
                if (!proceed) sleepAt = 0L
            }
        }
        }
    }
}

@Composable
internal fun CommandBar(
    ink: Ink,
    search: String,
    onSearch: (String) -> Unit,
    names: List<String>,
    running: Boolean,
    shell: ShellMode,
    searchFocus: androidx.compose.ui.focus.FocusRequester,
    onAdd: () -> Unit,
    onToggleRun: () -> Unit,
    onMenu: () -> Unit,
    onSettings: () -> Unit,
    onWidget: () -> Unit,
    library: String,
    onLibrary: (String) -> Unit,
    onMedia: (String) -> Unit = {},
) {
    var menu by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    val suggestions = if (search.isBlank()) emptyList() else names.filter { it.contains(search, true) }.distinct().take(6)
    val runLabel = when {
        shell == ShellMode.Compact && running -> "Pause"
        shell == ShellMode.Compact -> "Resume"
        running -> "Pause all"
        else -> "Resume eligible"
    }
    val addLabel = if (shell == ShellMode.Compact) "Add" else "Add download"
    Column {
    Row(
        Modifier.fillMaxWidth().padding(12.dp).glassPanel(ink).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (shell == ShellMode.Compact) {
            Box {
                QuietIcon("grid", ink) { menu = true }
                DropdownMenu(menu, { menu = false }) {
                    listOf("All downloads", "Active", "Queued", "Completed", "Needs attention").forEach { name ->
                        DropdownMenuItem(onClick = { onLibrary(name); menu = false }) { Text(name) }
                    }
                }
            }
        } else {
            Row(Modifier.width(if (shell == ShellMode.Wide) 204.dp else 112.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Image(remember { fetchIcon() }, "Fetch", Modifier.size(34.dp))
                Text("Fetch", color = ink.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Box(Modifier.weight(1f)) {
            Row(
                Modifier.fillMaxWidth().height(40.dp).glassPanel(ink, 10.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FetchGlyph("search", ink.muted, 18.dp)
                Spacer(Modifier.width(8.dp))
                androidx.compose.foundation.text.BasicTextField(
                    search,
                    onSearch,
                    Modifier.weight(1f).focusRequester(searchFocus).semantics { contentDescription = "Search downloads" },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = ink.text, fontSize = 14.sp),
                    decorationBox = { inner ->
                        if (search.isEmpty()) Text("Search downloads…", color = ink.muted, fontSize = 14.sp)
                        inner()
                    },
                )
            }
            if (suggestions.isNotEmpty()) {
                Column(Modifier.padding(top = 44.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(ink.panel).border(1.dp, ink.line, RoundedCornerShape(8.dp))) {
                    suggestions.forEach { name ->
                        Text(name, color = ink.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().clickable { onSearch(name) }.padding(horizontal = 12.dp, vertical = 8.dp))
                    }
                }
            }
        }
        ShellButton(runLabel, false, ink, onToggleRun)
        ShellButton(addLabel, true, ink, onAdd, Modifier.testTag("add-download"))
        Box {
            QuietIcon("gear", ink) { more = true }
            DropdownMenu(more, { more = false }) {
                DropdownMenuItem(onClick = { more = false; onMenu() }) { Text("Add batch") }
                DropdownMenuItem(onClick = { more = false; onSettings() }) { Text("Settings") }
                DropdownMenuItem(onClick = { more = false; onWidget() }) { Text("Widget") }
                if (shell == ShellMode.Compact && library.isNotBlank()) {
                    DropdownMenuItem(onClick = { more = false }) { Text(library) }
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        MediaShortcut("YouTube", ink) { onMedia("YouTube") }
        MediaShortcut("Instagram", ink) { onMedia("Instagram") }
        if (shell != ShellMode.Compact) Text("Preview & download videos", color = ink.muted, fontSize = 11.sp)
    }
    }
}

@Composable
private fun DownloadList(ink: Ink, state: LazyListState, modifier: Modifier, content: LazyListScope.() -> Unit) {
    Box(modifier.fillMaxWidth()) {
        LazyColumn(Modifier.fillMaxSize().padding(end = 10.dp), state, verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        if (state.canScrollForward || state.canScrollBackward) {
            VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(5.dp),
                style = ScrollbarStyle(24.dp, 5.dp, RoundedCornerShape(4.dp), 200, ink.muted.copy(alpha = 0.28f), ink.accent.copy(alpha = 0.70f)))
        }
    }
}

@Composable
internal fun FetchScrollbar(state: androidx.compose.foundation.ScrollState, ink: Ink, modifier: Modifier = Modifier) {
    if (state.maxValue <= 0) return
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(state), modifier = modifier.width(5.dp),
        style = ScrollbarStyle(24.dp, 5.dp, RoundedCornerShape(4.dp), 200,
            ink.muted.copy(alpha = 0.28f), ink.accent.copy(alpha = 0.70f)),
    )
}

@Composable
private fun ShellButton(label: String, primary: Boolean, ink: Ink, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val background = if (primary) ink.accent else if (hovered) ink.raised else ink.panel.copy(alpha = 0.65f)
    val foreground = if (primary) ink.onAccent else ink.text
    Row(
        modifier.semantics { contentDescription = label }.heightIn(min = 40.dp).clip(RoundedCornerShape(10.dp)).background(background).border(1.dp, if (primary) ink.accent else ink.line.copy(alpha = 0.6f), RoundedCornerShape(10.dp)).hoverable(interaction).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (primary) FetchGlyph("add", foreground, 18.dp)
        Text(label, color = foreground, fontSize = 14.sp, fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
    }
}

@Composable
internal fun AppNav(
    ink: Ink,
    library: String,
    queueFilter: String,
    jobs: List<DeskJob>,
    categories: List<DeskCategory>,
    queues: List<DeskQueue>,
    rail: Boolean,
    onLibrary: (String) -> Unit,
    onQueue: (String) -> Unit,
    onNewQueue: () -> Unit,
    onReorder: (Int, Int) -> Unit,
    onCalendar: (DeskQueue) -> Unit,
    onSettings: () -> Unit,
    onToggle: (() -> Unit)? = null,
    expandedWidth: Int = 216,
    onWidthChange: ((Int) -> Unit)? = null,
) {
    val scroll = rememberScrollState()
    var localCollapsed by remember(rail) { mutableStateOf(rail) }
    val collapsed = if (onToggle == null) localCollapsed else rail
    val width by animateDpAsState(if (collapsed) 68.dp else expandedWidth.coerceIn(184, 280).dp, tween(260, easing = FastOutSlowInEasing))
    val density = LocalDensity.current
    val currentWidth by androidx.compose.runtime.rememberUpdatedState(expandedWidth)
    val resize by androidx.compose.runtime.rememberUpdatedState(onWidthChange)
    Box(Modifier.width(width).fillMaxHeight().padding(vertical = 8.dp).testTag("sidebar")) {
    Column(
        Modifier.fillMaxSize().glassPanel(ink, 20.dp).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (!collapsed) Text("WORKSPACE", color = ink.muted, fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f).padding(start = 10.dp))
            Box(Modifier.graphicsLayer { rotationZ = if (collapsed) 0f else 180f }) {
                QuietIcon("chevron", ink, if (collapsed) "Expand sidebar" else "Collapse sidebar") {
                    if (onToggle != null) onToggle() else localCollapsed = !localCollapsed
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.fillMaxSize().testTag("navigation-scroll").verticalScroll(scroll).padding(end = if (collapsed) 0.dp else 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!collapsed) Text("LIBRARY", color = ink.muted, fontSize = 10.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(12.dp))
                listOf("All downloads", "Active", "Queued", "Completed", "Needs attention").forEach { name ->
                    NavRow(name, library == name, ink, collapsed, countOf(name, jobs), onClick = { onLibrary(name) })
                }
                Spacer(Modifier.height(12.dp))
                if (!collapsed) Text("Queues", color = ink.muted, fontSize = 12.sp, modifier = Modifier.padding(start = 12.dp, bottom = 4.dp))
                NavRow("All queues", queueFilter == "All", ink, collapsed, 0) { onQueue("All") }
                queues.forEachIndexed { index, queue ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            NavRow(queue.name, queueFilter == queue.name, ink, collapsed, jobs.count { it.queue == queue.name }) { onQueue(queue.name) }
                            if (!collapsed && queue.fromHour >= 0) {
                                val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                                Text(ScheduleWindow.describe(queue.fromHour, queue.toHour, hour), color = ink.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp))
                            }
                        }
                        if (!collapsed) {
                            val density = LocalDensity.current
                            Box {
                                var queueMenu by remember(queue.name) { mutableStateOf(false) }
                                QuietIcon("more", ink, "Queue actions for ${queue.name}") { queueMenu = true }
                                DropdownMenu(queueMenu, { queueMenu = false }) {
                                    DropdownMenuItem(enabled = index > 0, onClick = { queueMenu = false; onReorder(index, index - 1) }) { Text("Move ${queue.name} up") }
                                    DropdownMenuItem(enabled = index < queues.lastIndex, onClick = { queueMenu = false; onReorder(index, index + 1) }) { Text("Move ${queue.name} down") }
                                    DropdownMenuItem(onClick = { queueMenu = false; onCalendar(queue) }) { Text("Schedule") }
                                }
                            }
                            Box(
                                Modifier.padding(start = 4.dp).size(16.dp).semantics { contentDescription = "Drag ${queue.name}" }.pointerInput(index, queues.size) {
                                    var total = 0f
                                    detectDragGestures(
                                        onDragCancel = { total = 0f },
                                        onDragEnd = {
                                            val to = queueDestination(queues.size, index, total / density.density)
                                            onReorder(index, to)
                                            total = 0f
                                        },
                                    ) { _, amount -> total += amount.y }
                                },
                            ) { FetchGlyph("grid", ink.muted, 14.dp) }
                        }
                    }
                }
                NavRow("New queue", false, ink, collapsed, 0, onNewQueue)
                if (!collapsed) {
                    Spacer(Modifier.height(8.dp))
                    Text("Categories", color = ink.muted, fontSize = 12.sp, modifier = Modifier.padding(start = 12.dp, bottom = 4.dp))
                }
                categories.forEach { category ->
                    NavRow(category.name, library == category.name, ink, collapsed, 0) { onLibrary(category.name) }
                }
            }

        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(ink.line.copy(alpha = 0.4f)))
        NavRow("Settings", false, ink, collapsed, 0, onSettings)
    }
        if (!collapsed && onWidthChange != null) {
            Box(Modifier.align(Alignment.CenterEnd).width(10.dp).fillMaxHeight().pointerHoverIcon(androidx.compose.ui.input.pointer.PointerIcon(java.awt.Cursor(java.awt.Cursor.E_RESIZE_CURSOR))).semantics { contentDescription = "Resize sidebar" }
                .pointerInput(Unit) {
                    var proposed = 216f
                    detectHorizontalDragGestures(onDragStart = { proposed = currentWidth.toFloat() }) { change, amount ->
                        change.consume()
                        proposed = (proposed + amount / density.density).coerceIn(184f, 280f)
                        resize?.invoke(proposed.toInt())
                    }
                }, contentAlignment = Alignment.Center) {
                Box(Modifier.width(3.dp).height(38.dp).clip(RoundedCornerShape(3.dp)).background(ink.muted.copy(alpha = 0.45f)))
            }
        }
    }

}

@Composable
private fun NavRow(label: String, selected: Boolean, ink: Ink, rail: Boolean, count: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp).semantics { contentDescription = label; this.selected = selected }.clip(RoundedCornerShape(9.dp)).background(if (selected) ink.accent.copy(alpha = 0.14f) else Color.Transparent).clickable(role = Role.Tab, onClick = onClick).padding(horizontal = if (rail) 0.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (rail) Arrangement.Center else Arrangement.spacedBy(12.dp),
    ) {
        FetchGlyph(glyphFor(label), if (selected) ink.accent else ink.muted, 18.dp)
        if (!rail) {
            Text(label, color = if (selected) ink.accent else ink.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (count > 0) Text(count.toString(), color = ink.muted, fontSize = 12.sp)
        }
    }
}

private fun countOf(label: String, jobs: List<DeskJob>): Int = when (label) {
    "All downloads" -> jobs.size
    "Active" -> jobs.count { it.state == "Running" }
    "Queued" -> jobs.count { it.state == "Queued" }
    "Completed" -> jobs.count { it.state == "Saved" }
    "Needs attention" -> jobs.count { it.state == "Failed" }
    else -> 0
}

@Composable
internal fun ListHead(
    ink: Ink,
    library: String,
    count: Int,
    cards: Boolean,
    comfortable: Boolean,
    height: androidx.compose.ui.unit.Dp,
    jobs: List<DeskJob>,
    settings: DeskSettings,
    onCards: (Boolean) -> Unit,
    onDensity: (Boolean) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(library, color = ink.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$count shown", color = ink.muted, fontSize = 12.sp)
            }
            Row(Modifier.glassPanel(ink, 9.dp).padding(3.dp)) {
                listOf("Cards" to true, "Table" to false).forEach { (label, value) ->
                    Text(label, color = if (cards == value) ink.onAccent else ink.muted, fontSize = 12.sp,
                        modifier = Modifier.semantics { selected = cards == value }.clip(RoundedCornerShape(6.dp))
                            .background(if (cards == value) ink.accent else Color.Transparent).clickable(role = Role.Tab) { onCards(value) }.padding(horizontal = 10.dp, vertical = 7.dp))
                }
            }
            Text(if (comfortable) "Comfortable" else "Compact", color = ink.muted, fontSize = 13.sp, modifier = Modifier.clickable { onDensity(!comfortable) }.padding(8.dp))
        }
        if (height >= 560.dp) {
            val speed = jobs.filter { it.state == "Running" }.sumOf { it.speed }
            Text(
                "${jobs.count { it.state == "Running" }} active · ${jobs.count { it.state == "Queued" }} queued · ${if (speed > 0L) "${bytesOf(speed)}/s" else "idle"} · ${settings.folder}",
                color = ink.muted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SelectionBar(
    ink: Ink,
    count: Int,
    canPause: Int,
    canResume: Int,
    deleteFile: Boolean,
    onClear: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRemove: () -> Unit,
    onDeleteFile: (Boolean) -> Unit,
) {
    FlowRow(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("$count selected", color = ink.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text("Clear", color = ink.accent, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onClear))
        Text("Pause $canPause of $count", color = if (canPause > 0) ink.text else ink.muted, fontSize = 13.sp, modifier = Modifier.clickable(enabled = canPause > 0, onClick = onPause).padding(8.dp))
        Text("Resume $canResume of $count", color = if (canResume > 0) ink.text else ink.muted, fontSize = 13.sp, modifier = Modifier.clickable(enabled = canResume > 0, onClick = onResume).padding(8.dp))
        Text("Remove from list", color = ink.danger, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onRemove).padding(8.dp))
        Checkbox(deleteFile, onDeleteFile)
        Text(if (deleteFile) "Also delete the file" else "Keep the file", color = ink.muted, fontSize = 12.sp)
    }
}

private fun attentionCause(job: DeskJob): String = when {
    job.message.contains("free space", ignoreCase = true) || job.message.contains("storage", ignoreCase = true) -> "Storage"
    job.message.contains("auth", ignoreCase = true) || job.message.contains("401", ignoreCase = true) || job.message.contains("sign", ignoreCase = true) -> "Authentication"
    job.message.contains("checksum", ignoreCase = true) || job.message.contains("integrity", ignoreCase = true) -> "Integrity"
    job.message.contains("expired", ignoreCase = true) || job.message.contains("416") -> "Expired link"
    job.message.contains("changed", ignoreCase = true) -> "File changed"
    else -> "Network"
}

@Composable
private fun EmptyLibrary(ink: Ink, filtered: Boolean, onClear: () -> Unit, onAdd: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).glassPanel(ink, 22.dp), contentAlignment = Alignment.Center) {
            FetchGlyph(if (filtered) "search" else "down", ink.accent, 32.dp)
        }
        Spacer(Modifier.height(20.dp))
        Text(if (filtered) "Nothing matches this search." else "No downloads yet.", color = ink.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(if (filtered) "The library is unchanged." else "Your files, in one calm workspace.", color = ink.muted, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        if (filtered) ShellButton("Clear search", false, ink, onClear) else ShellButton("Add download", true, ink, onAdd)
    }
}

internal fun stateWord(job: DeskJob): String =
    TransferStage.label(job.state, job.phase, job.bytes, job.total, job.message)

internal fun cardPace(job: DeskJob, pace: Long): String {
    if (job.total == null) return if (pace > 0L) "${bytesOf(pace)}/s" else stateWord(job)
    if (job.state == "Running" && pace <= 0L) return "Calculating…"
    if (pace <= 0L) return stateWord(job)
    if (job.state == "Paused") return "Last speed ${bytesOf(pace)}/s"
    return "${bytesOf(pace)}/s · ~${timeLeft(job, pace)} remaining"
}

@Composable
internal fun Inspector(
    ink: Ink,
    job: DeskJob,
    settings: DeskSettings,
    tab: String,
    onTab: (String) -> Unit,
    onClose: () -> Unit,
    onOpen: () -> Unit,
    onExtract: () -> Unit,
    onFewerConnections: (Int) -> Unit,
) {
    val scroll = rememberScrollState()
    LaunchedEffect(job.id, tab) { scroll.scrollTo(0) }
    Column(
        Modifier.width(320.dp).fillMaxHeight().padding(vertical = 8.dp).background(ink.night, RoundedCornerShape(18.dp)).glassPanel(ink, 18.dp).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(job.fileName, color = ink.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            QuietIcon("close", ink, onClose)
        }
        Text(stateWord(job), color = ink.muted, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Open folder", color = ink.accent, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onOpen))
            Text("Copy path", color = ink.accent, fontSize = 13.sp, modifier = Modifier.clickable {
                val path = File(job.path, job.fileName).absolutePath
                val selection = java.awt.datatransfer.StringSelection(path)
                java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
            })
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Overview", "Connections", "Integrity", "Activity").forEach { name ->
                Text(name, color = if (tab == name) ink.accent else ink.muted, fontSize = 11.sp,
                    modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(if (tab == name) ink.accent.copy(alpha = 0.14f) else Color.Transparent)
                        .semantics { selected = tab == name }.clickable(role = Role.Tab) { onTab(name) }.padding(horizontal = 7.dp, vertical = 8.dp))
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.fillMaxSize().testTag("inspector-scroll").verticalScroll(scroll).padding(end = 12.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (tab) {
                    "Connections" -> {
                        Text("${job.connections} requested · host cap ${settings.hostCap}", color = ink.text, fontSize = 13.sp)
                        if (job.parts.isEmpty()) Text("No live ranges yet.", color = ink.muted, fontSize = 12.sp)
                        job.parts.forEach { part ->
                            Text(
                                SegmentMap.describe("Part ${part.index + 1}", part.downloaded, part.reserved, part.size, job.phase),
                                color = ink.muted,
                                fontSize = 12.sp,
                            )
                            SegmentTrack(part.downloaded, part.reserved, part.size, job.phase, "Part ${part.index + 1}", Modifier.fillMaxWidth())
                        }
                    }
                    "Integrity" -> {
                        if (job.checksum.isBlank()) Text("No checksum provided.", color = ink.muted, fontSize = 13.sp)
                        else Text("${job.algorithm} is set. It is checked when the file is saved, not while bytes are still arriving.", color = ink.text, fontSize = 13.sp)
                        Text(ScanLabel.NOT_SCANNED, color = ink.muted, fontSize = 12.sp)
                        if (job.state == "Saved" && job.fileName.endsWith(".zip", true)) {
                            Text(
                                "Choose ZIP entries",
                                color = ink.accent,
                                fontSize = 13.sp,
                                modifier = Modifier.semantics { contentDescription = "Choose ZIP entries" }.clickable(onClick = onExtract),
                            )
                        }
                    }
                    "Activity" -> {
                        Text("Added ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(java.util.Date(job.addedAt))}", color = ink.text, fontSize = 13.sp)
                        job.notes.forEach { label ->
                            Text(label, color = ink.muted, fontSize = 12.sp)
                        }
                    }
                    else -> {
                        Text("Downloaded ${bytesOf(job.bytes)} of ${job.total?.let { bytesOf(it) } ?: "unknown size"}", color = ink.text, fontSize = 14.sp)
                        if (job.reserved > job.bytes) Text("Reserved ${bytesOf(job.reserved)}. This is not finished data.", color = ink.muted, fontSize = 12.sp)
                        Text(cardPace(job, if (job.state == "Running") job.speed else job.heldSpeed), color = ink.muted, fontSize = 13.sp)
                        PaceChart(job.paceRing, System.currentTimeMillis(), ink)
                        Text(siteOf(job.url), color = ink.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(job.path, color = ink.muted, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text("Queue ${job.queue}", color = ink.text, fontSize = 13.sp)
                        val note = BottleneckJudge.note(job.bytes > job.stallBytes, job.reserved > job.stallReserved, job.message.startsWith("Stalled"))
                        Text("${note.cause}. ${note.evidence} Confidence ${note.confidence}.", color = ink.muted, fontSize = 12.sp)
                        val suggestion = SpeedCause.suggest(note.cause, job.connections, job.state == "Running")
                        val fewer = suggestion.connections
                        if (fewer != null) {
                            Text(
                                suggestion.label,
                                color = ink.accent,
                                fontSize = 13.sp,
                                modifier = Modifier.semantics { contentDescription = suggestion.label }.clickable { onFewerConnections(fewer) },
                            )
                        } else {
                            Text(suggestion.label, color = ink.muted, fontSize = 12.sp)
                        }
                        Text(TransferCounters(job.bytes, job.reserved, job.tries, job.checkpoints).label(), color = ink.muted, fontSize = 12.sp)
                        Text(PostStep.describe(false), color = ink.muted, fontSize = 12.sp)
                        Text(NetworkProfile.explain(settings.networkProfile, settings.networkProfile), color = ink.muted, fontSize = 12.sp)
                        Text(InterfaceBind.refusal(true, false).orEmpty(), color = ink.muted, fontSize = 12.sp)
                        val stamp = runCatching { JvmDisk.stamp(File(job.path)) }.getOrNull()
                        val policy = when (settings.writePolicy) {
                            "Local" -> WritePolicy.Local
                            "Removable" -> WritePolicy.Removable
                            "Network" -> WritePolicy.Network
                            else -> WritePolicy.Auto
                        }
                        Text(
                            "Write policy ${DiskPolicy.describe(policy, stamp?.kind ?: DiskKind.Unknown)}",
                            color = ink.muted,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
            FetchScrollbar(scroll, ink, Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    }
}

@Composable
internal fun StatusLine(ink: Ink, jobs: List<DeskJob>, settings: DeskSettings, power: Boolean?) {
    val speed = jobs.filter { it.state == "Running" }.sumOf { it.speed }
    val free = runCatching { File(settings.folder).usableSpace }.getOrDefault(0L)
    Row(Modifier.fillMaxWidth().background(ink.panel.copy(alpha = 0.5f)).heightIn(min = 32.dp).padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${jobs.count { it.state == "Running" }} active · ${jobs.count { it.state == "Queued" }} queued · ${if (speed > 0L) "${bytesOf(speed)}/s" else "idle"}",
            color = ink.muted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        val battery = when {
            !settings.pauseOnBattery -> ""
            power == true -> " · On battery"
            power == false -> " · On power"
            else -> " · Battery N/A"
        }
        Text(
            "${bytesOf(free)} free · Health ${DiskHealth.read { null }}$battery · ${settings.networkProfile}",
            color = ink.muted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun WidgetFace(
    ink: Ink,
    jobs: List<DeskJob>,
    selected: DeskJob?,
    pinnedId: Long?,
    size: androidx.compose.ui.unit.DpSize,
    onPin: (Long?) -> Unit,
    onPauseAll: () -> Unit,
    onResume: () -> Unit,
    onAdd: () -> Unit,
    onExpand: () -> Unit,
    onOpen: (DeskJob) -> Unit,
) {
    val pinned = jobs.firstOrNull { it.id == pinnedId }
    val focus = pinned ?: selected?.takeIf { it.state == "Running" || it.state == "Paused" || it.state == "Saved" }
        ?: jobs.firstOrNull { it.state == "Running" }
        ?: jobs.firstOrNull { it.state == "Paused" }
    val running = jobs.any { it.state == "Running" }
    val extended = size.width >= 400.dp && size.height >= 280.dp
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FetchGlyph("down", ink.accent, 16.dp)
            Text("Fetch", color = ink.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
            Text("Expand", color = ink.accent, fontSize = 12.sp, maxLines = 1, modifier = Modifier.semantics { contentDescription = "Expand" }.clickable(onClick = onExpand))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (focus == null) {
                Text("No active downloads", color = ink.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            } else {
                Text(focus.fileName, color = ink.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val pace = if (focus.state == "Running") focus.speed else 0L
                Text(
                    if (pace > 0L) "${bytesOf(pace)}/s · ${stateWord(focus)}" else stateWord(focus),
                    color = ink.text,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                val fraction = focus.total?.takeIf { it > 0L }?.let { (focus.bytes.toFloat() / it).coerceIn(0f, 1f) } ?: 0f
                GlowBar(fraction, focus.state == "Running", ink.success)
                val percent = focus.total?.takeIf { it > 0L }?.let { (focus.bytes * 100L / it).toInt() }
                Text(
                    buildString {
                        append(if (percent != null) "$percent%" else bytesOf(focus.bytes))
                        append(" · ")
                        append(focus.total?.let { bytesOf(it) } ?: "size unknown")
                        if (pace > 0L && focus.total != null) append(" · about ${timeLeft(focus, pace)} left")
                    },
                    color = ink.muted,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (focus.state == "Saved") Text("Open folder", color = ink.accent, fontSize = 13.sp, modifier = Modifier.clickable { onOpen(focus) })
                Text(if (pinnedId == focus.id) "Pinned" else "Pin", color = ink.accent, fontSize = 12.sp, modifier = Modifier.clickable { onPin(if (pinnedId == focus.id) null else focus.id) })
                if (extended) {
                    jobs.filter { it.id != focus.id && (it.state == "Running" || it.state == "Paused") }.take(2).forEach { other ->
                        Text("${other.fileName} · ${stateWord(other)}", color = ink.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShellButton(if (running) if (size.width < 420.dp) "Pause" else "Pause all" else "Resume", false, ink, if (running) onPauseAll else onResume)
            ShellButton("Add", true, ink, onAdd, Modifier.testTag("add-download"))
        }
    }
}

@Composable
internal fun PaceChart(samples: List<Pair<Long, Long>>, now: Long, ink: Ink) {
    val points = recentPace(samples, now)
    if (points.size < 2) {
        Text("Not enough samples for a speed chart.", color = ink.muted, fontSize = 12.sp)
        return
    }
    val peak = points.maxOf { it.second }.coerceAtLeast(1L)
    val span = ((points.last().first - points.first().first) / 1000L).coerceAtLeast(0L)
    Text("${bytesOf(points.last().second)}/s now · ${bytesOf(peak)}/s peak · ${span}s of samples", color = ink.muted, fontSize = 12.sp)
    Canvas(Modifier.fillMaxWidth().height(48.dp)) {
        val step = size.width / (points.size - 1).coerceAtLeast(1)
        var previous: androidx.compose.ui.geometry.Offset? = null
        points.forEachIndexed { index, sample ->
            val point = androidx.compose.ui.geometry.Offset(
                index * step,
                size.height - (sample.second.toFloat() / peak.toFloat()) * size.height,
            )
            previous?.let { drawLine(ink.accent, it, point, strokeWidth = 2.dp.toPx()) }
            previous = point
        }
    }
}

internal fun siteOf(url: String): String = runCatching { java.net.URI(url).host ?: url }.getOrDefault(url)

@Composable
internal fun QuietIcon(name: String, ink: Ink, onClick: () -> Unit) = QuietIcon(name, ink, null, onClick)

@Composable
internal fun QuietIcon(name: String, ink: Ink, description: String?, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val label = description ?: when (name) {
        "gear" -> "Settings and tools"
        "more" -> "Download actions"
        "close" -> "Close"
        "grid" -> "Browse library"
        "pause" -> "Pause download"
        "play" -> "Resume download"
        else -> name.replaceFirstChar { it.uppercase() }
    }
    Box(
        Modifier.size(34.dp).semantics { contentDescription = label }.clip(RoundedCornerShape(9.dp))
            .background(if (hovered) ink.accent.copy(alpha = 0.14f) else ink.raised.copy(alpha = 0.55f))
            .hoverable(interaction).clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { FetchGlyph(name, if (hovered) ink.accent else ink.muted, 16.dp) }
}

@Composable
internal fun Action(label: String, primary: Boolean, onClick: () -> Unit) {
    val glyph = when {
        label.contains("Pause") -> "pause"
        label.contains("Stop") -> "stop"
        label.contains("Start") || label.contains("Play") || label == "Download" -> "play"
        label.contains("Folder") || label.contains("Open") -> "folder"
        label.contains("Settings") || label.contains("Edit") -> "gear"
        label.contains("Add") || label.contains("Batch") -> "add"
        label.startsWith("Audio +") -> "both"
        label.startsWith("Audio") -> "audio"
        label.startsWith("Video") -> "video"
        else -> null
    }
    val ink = LocalFetchInk.current
    val background = if (primary) ink.accent else ink.raised
    val foreground = if (primary) ink.onAccent else ink.text
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        elevation = ButtonDefaults.elevation(0.dp, 1.dp, 0.dp, 1.dp, 0.dp),
        colors = ButtonDefaults.buttonColors(backgroundColor = background, contentColor = foreground),
    ) {
        if (glyph != null) {
            FetchGlyph(glyph, foreground, 14.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(label, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TableHead(
    ink: Ink,
    sort: String,
    ascending: Boolean,
    allPicked: Boolean,
    nameWidth: Int,
    onPickAll: (Boolean) -> Unit,
    onNameDelta: (Float) -> Unit,
    onSort: (String) -> Unit,
) {
    var sortMenu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Checkbox(allPicked, onPickAll)
        Spacer(Modifier.width(36.dp))
        HeadCell("File", "name", Modifier.width(nameWidth.dp), ink, sort, ascending, onSort, onNameDelta)
        Text("Transfer", color = ink.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Box {
            Text("Sort ${if (ascending) "↑" else "↓"}", color = ink.accent, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { sortMenu = true }.padding(8.dp))
            DropdownMenu(sortMenu, { sortMenu = false }) {
                listOf("Name" to "name", "Size" to "size", "Status" to "status", "Speed" to "speed", "Time left" to "left", "Added" to "added").forEach { (label, key) ->
                    DropdownMenuItem(onClick = { onSort(key); sortMenu = false }) { Text(label, color = if (sort == key) ink.accent else ink.text) }
                }
            }
        }
    }
}

@Composable
private fun HeadCell(
    label: String,
    key: String,
    modifier: Modifier,
    ink: Ink,
    sort: String,
    ascending: Boolean,
    onSort: (String) -> Unit,
    onResize: ((Float) -> Unit)?,
) {
    val mark = if (sort == key) if (ascending) " ↑" else " ↓" else ""
    if (onResize == null) {
        Text(label + mark, color = ink.muted, fontSize = 12.sp, modifier = modifier.clickable { onSort(key) }.padding(vertical = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    } else {
        val density = LocalDensity.current
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            Text(label + mark, color = ink.muted, fontSize = 12.sp, modifier = Modifier.weight(1f).clickable { onSort(key) }.padding(vertical = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(
                Modifier.width(8.dp).height(24.dp).semantics { contentDescription = "Resize name column" }.pointerInput(Unit) {
                    detectHorizontalDragGestures { _, drag ->
                        onResize(with(density) { drag.toDp().value })
                    }
                },
            )
        }
    }
}

@Composable
private fun GlowBar(fraction: Float, moving: Boolean, tint: Color) {
    val animated by animateFloatAsState(fraction, tween(if (moving) 520 else 280, easing = FastOutSlowInEasing))
    Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(8.dp)).background(LocalFetchInk.current.line.copy(alpha = 0.50f))) {
        Box(Modifier.fillMaxWidth(animated.coerceIn(0f, 1f)).height(5.dp).clip(RoundedCornerShape(8.dp)).background(tint))
    }
}

@Composable
private fun ConnectionRail(parts: List<SpanPart>, phase: TransferPhase) {
    Row(Modifier.fillMaxWidth().height(5.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        parts.forEach { part ->
            SegmentTrack(
                part.downloaded,
                part.reserved,
                part.size,
                phase,
                "Part ${part.index + 1}",
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
internal fun SegmentTrack(
    downloaded: Long,
    reserved: Long,
    size: Long,
    phase: TransferPhase,
    name: String,
    modifier: Modifier,
) {
    val spoken = SegmentMap.describe(name, downloaded, reserved, size, phase)
    val rows = SegmentMap.bands(downloaded, reserved, size, phase)
    fun amount(kind: SegmentKind) = rows.filter { it.kind == kind }.sumOf { it.bytes }
    val stored = amount(SegmentKind.Stored)
    val pending = amount(SegmentKind.Pending)
    val check = amount(SegmentKind.NeedsCheck)
    val known = if (size > 0L) size else stored + pending + check
    Box(
        modifier.height(5.dp).clip(RoundedCornerShape(4.dp)).background(LocalFetchInk.current.line.copy(alpha = 0.50f)).semantics { contentDescription = spoken },
    ) {
        if (known > 0L && stored + pending + check > 0L) {
            Row(Modifier.fillMaxSize()) {
                if (stored > 0L) Box(Modifier.fillMaxHeight().weight(stored.toFloat()).background(LocalFetchInk.current.success))
                if (check > 0L) Box(Modifier.fillMaxHeight().weight(check.toFloat()).background(LocalFetchInk.current.warning))
                if (pending > 0L) Box(Modifier.fillMaxHeight().weight(pending.toFloat()).background(LocalFetchInk.current.accent))
                val rest = (known - stored - pending - check).coerceAtLeast(0L)
                if (rest > 0L) Spacer(Modifier.fillMaxHeight().weight(rest.toFloat()))
            }
        }
    }
}

private data class KindTile(val glyph: String, val tint: Color, val tile: Color)

private fun fileKind(name: String, ink: Ink = inkOf("light")): KindTile {
    val glyph = when (typeName(name)) {
        "Video" -> "video"
        "Image" -> "image"
        "Document" -> "doc"
        else -> "box"
    }
    return KindTile(glyph, ink.accent, ink.accent.copy(alpha = 0.12f))
}

private fun typeName(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
    "mp4", "mkv", "mov", "webm", "avi", "m4v" -> "Video"
    "jpg", "jpeg", "png", "gif", "webp", "heic", "bmp" -> "Image"
    "pdf", "doc", "docx", "txt", "rtf", "xls", "xlsx", "ppt", "pptx", "csv" -> "Document"
    else -> "Other"
}

@Composable
private fun BatchDialog(ink: Ink, onClose: (List<String>) -> Unit) {
    var text by remember { mutableStateOf("") }
    val rows = BatchIntake.review(text)
    Scrim {
        DialogCard(ink, "Batch URLs", scroll = false) {
            OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().height(180.dp), label = { Text("One URL per line") })
            Column(Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (rows.isEmpty()) Text("Each line is checked on its own. A refused line does not stop the rest.", color = ink.muted, fontSize = 12.sp)
                rows.forEach { row ->
                    Text(
                        "${if (row.accepted) "Keep" else "Skip"} · ${row.note}",
                        color = if (row.accepted) ink.success else ink.danger,
                        fontSize = 12.sp,
                    )
                    Text(row.text, color = ink.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Action("Add", rows.any { it.accepted }) { onClose(BatchIntake.keep(text)) }
                Action("Cancel", false) { onClose(emptyList()) }
            }
        }
    }
}

@Composable
private fun ZipExtractDialog(ink: Ink, archive: File, onClose: () -> Unit) {
    val names = remember(archive) { ZipUnpack.names(archive) }
    var picked by remember { mutableStateOf(names.toSet()) }
    var result by remember { mutableStateOf<String?>(null) }
    Scrim {
        DialogCard(ink, "ZIP entries", scroll = false) {
            if (!archive.isFile) Text("The archive is not on disk.", color = ink.danger, fontSize = 13.sp)
            else if (names.isEmpty()) Text("This archive has no files that stay inside the folder.", color = ink.muted, fontSize = 13.sp)
            else Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                names.forEach { name ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(name in picked, { on -> picked = if (on) picked + name else picked - name })
                        Text(name, color = ink.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            result?.let { Text(it, color = ink.muted, fontSize = 12.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Action("Extract", true) {
                    if (picked.isEmpty()) {
                        result = "Nothing was extracted."
                        return@Action
                    }
                    val saved = ZipUnpack.unpack(archive, picked)
                    result = if (saved == 0) "Nothing was extracted." else "Saved $saved ${if (saved == 1) "file" else "files"} next to the archive."
                }
                Action("Close", false, onClose)
            }
        }
    }
}

@Composable
internal fun SettingsDialog(ink: Ink, current: DeskSettings, onTheme: (String) -> Unit, onClose: (DeskSettings?) -> Unit) {
    var theme by remember { mutableStateOf(current.theme) }
    var threads by remember { mutableStateOf(current.threads.toString()) }
    var parallel by remember { mutableStateOf(current.parallel) }
    var minChunk by remember { mutableStateOf(current.minChunkKb.toString()) }
    var attempts by remember { mutableStateOf(current.retryAttempts.toString()) }
    var delaySec by remember { mutableStateOf(current.retryDelaySeconds.toString()) }
    var backoff by remember { mutableStateOf(current.backoffTenths.toString()) }
    var channel by remember { mutableStateOf(current.channelName) }
    var icon by remember { mutableStateOf(current.iconKey) }
    var showProgress by remember { mutableStateOf(current.showProgress) }
    var persistent by remember { mutableStateOf(current.persistent) }
    var destination by remember { mutableStateOf(current.destination) }
    var folder by remember { mutableStateOf(current.folder) }
    var publicDownloads by remember { mutableStateOf(current.publicDownloads) }
    var publicFolder by remember { mutableStateOf(current.publicFolder) }
    var overwrite by remember { mutableStateOf(current.overwrite) }
    var checkSpace by remember { mutableStateOf(current.checkSpace) }
    var minFree by remember { mutableStateOf(current.minFreeMb.toString()) }
    var writePolicy by remember { mutableStateOf(current.writePolicy) }
    var useAlarm by remember { mutableStateOf(current.useAlarm) }
    var promptOpen by remember { mutableStateOf(current.promptOpen) }
    var verifySize by remember { mutableStateOf(current.verifySize) }
    var verifyChecksum by remember { mutableStateOf(current.verifyChecksum) }
    var verifyApk by remember { mutableStateOf(current.verifyApk) }
    var verifyType by remember { mutableStateOf(current.verifyType) }
    var verifySignature by remember { mutableStateOf(current.verifySignature) }
    var clipboard by remember { mutableStateOf(current.clipboard) }
    var floatBubble by remember { mutableStateOf(current.floatBubble) }
    var alwaysOnTop by remember { mutableStateOf(current.alwaysOnTop) }
    var pauseOnBattery by remember { mutableStateOf(current.pauseOnBattery) }
    var adaptiveConnections by remember { mutableStateOf(current.adaptiveConnections) }
    var localToken by remember { mutableStateOf(current.localToken) }
    var networkProfile by remember { mutableStateOf(current.networkProfile) }
    var latencyAware by remember { mutableStateOf(current.latencyAware) }
    var quotaMb by remember { mutableStateOf(current.quotaMb.toString()) }
    var speed by remember { mutableStateOf(current.speedLimitKb.toString()) }
    var concurrent by remember { mutableStateOf(current.maxConcurrent.toString()) }
    var host by remember { mutableStateOf(current.hostCap.toString()) }
    var hostRules by remember { mutableStateOf(current.hostRules) }
    var port by remember { mutableStateOf(current.port.toString()) }
    var agent by remember { mutableStateOf(current.userAgent) }
    var proxy by remember { mutableStateOf(current.proxyHost) }
    var proxyPort by remember { mutableStateOf(current.proxyPort.toString()) }
    var complete by remember { mutableStateOf(current.onComplete) }
    Scrim {
        BoxWithConstraints {
            val settingsScroll = rememberScrollState()
            DialogCard(ink, "Settings", 680.dp, scroll = false) {
                Box(Modifier.height((maxHeight - 190.dp).coerceIn(100.dp, 540.dp)).fillMaxWidth()) {
                    Column(Modifier.fillMaxSize().verticalScroll(settingsScroll).padding(end = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Section("Appearance", ink)
                        Hint("Choose a palette. Appearance changes are applied immediately.", ink)
                        ThemePicker(theme) { key -> theme = key; onTheme(key) }
                        Section("Engine", ink)
                        Hint("The next start picks these up. A transfer that is already running keeps the settings it started with.", ink)
                        Field("Connections", threads) { threads = it }
                        Toggle("Run those connections together", parallel, ink) { parallel = it }
                        Field("Smallest piece, KB", minChunk) { minChunk = it }
                        Hint("The library will not go under 64 KB.", ink)
                        Section("If the network drops", ink)
                        Field("Attempts", attempts) { attempts = it }
                        Field("First wait, seconds", delaySec) { delaySec = it }
                        Field("Backoff, tenths", backoff) { backoff = it }
                        Hint("Each later try waits this many times longer than the one before it. 20 means twice as long.", ink)
                        Section("Notification", ink)
                        Field("Name", channel) { channel = it }
                        Hint("The tray title. Pause, Stop, and Start sit on each download.", ink)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("fetch", "tray", "bolt", "layers").forEach { item -> Action(item, icon == item) { icon = item } }
                        }
                        Toggle("Show the percentage", showProgress, ink) { showProgress = it }
                        Toggle("Keep a notification while it runs", persistent, ink) { persistent = it }
                        Section("Where files go", ink)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Action("Let the library choose", destination == "auto") {
                                destination = "auto"
                            }
                            Action("App folder", destination == "scoped") {
                                destination = "scoped"
                                publicDownloads = false
                            }
                            Action("Absolute path", destination == "custom") {
                                destination = "custom"
                                publicDownloads = false
                            }
                        }
                        Field(if (destination == "custom") "Absolute path" else "App folder", folder) { folder = it }
                        Toggle("Prefer the public Downloads folder", publicDownloads, ink) {
                            publicDownloads = it
                            if (it) destination = "auto"
                        }
                        Hint("Turn this on only with Let the library choose. Absolute path and App folder turn it off.", ink)
                        Field("Folder inside Downloads", publicFolder) { publicFolder = it }
                        Toggle("Replace a file that already has this name", overwrite, ink) { overwrite = it }
                        Toggle("Check free space first", checkSpace, ink) { checkSpace = it }
                        Field("Minimum free MB", minFree) { minFree = it }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("Auto", "Local", "Removable", "Network").forEach { item ->
                                Action(item, writePolicy == item) { writePolicy = item }
                            }
                        }
                        Hint("Auto follows the volume. Removable and network flush sooner. This does not guess hard disk versus SSD.", ink)
                        Section("Default clock", ink)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Action("WorkManager", !useAlarm) { useAlarm = false }
                            Action("AlarmManager", useAlarm) { useAlarm = true }
                        }
                        Hint("WorkManager starts while Fetch is open. AlarmManager waits for the queue's hour window. A repeat still uses the open app.", ink)
                        Section("After the file is saved", ink)
                        Toggle("Offer to open the file", promptOpen, ink) { promptOpen = it }
                        Hint("On the phone this offers the installer. Here it opens the saved file. A failed check never opens it.", ink)
                        Section("Checking the file", ink)
                        Toggle("Size matches what the server promised", verifySize, ink) { verifySize = it }
                        Toggle("Checksum, when you provided one", verifyChecksum, ink) { verifyChecksum = it }
                        Toggle("APK structure, for .apk and .apks", verifyApk, ink) { verifyApk = it }
                        Toggle("Content-Type header", verifyType, ink) { verifyType = it }
                        Toggle("APK signature can be read", verifySignature, ink) { verifySignature = it }
                        Hint("Content-Type is off by default because many servers send the wrong one. Signature only checks that a signature exists.", ink)
                        Section("Clipboard", ink)
                        Toggle("Offer a copied link", clipboard, ink) { clipboard = it }
                        Section("Battery", ink)
                        Toggle("Pause downloads on battery", pauseOnBattery, ink) { pauseOnBattery = it }
                        Hint("Off unless you turn it on. A Mac can read the power source. If it cannot, nothing is paused. A download you paused stays paused.", ink)
                        Toggle("Lower connections after a stall", adaptiveConnections, ink) { adaptiveConnections = it }
                        Hint("Off unless you turn it on. It never raises the connection cap, and it only applies to this download.", ink)
                        Field("Local API token", localToken) { localToken = it }
                        Hint("The browser port stays closed to other machines. A blank token refuses every request.", ink)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Action("Unmetered", networkProfile != "Metered") { networkProfile = "Unmetered" }
                            Action("Metered", networkProfile == "Metered") { networkProfile = "Metered" }
                        }
                        Hint("Metered keeps a new download to two connections. This does not change other programs.", ink)
                        Toggle("Slow the speed cap when the reply is slow", latencyAware, ink) { latencyAware = it }
                        Hint("Off unless you turn it on. It only cuts a speed cap that is already set, and only for this download.", ink)
                        Field("Useful quota MB (0 = off)", quotaMb) { quotaMb = it }
                        Hint("Retries are not added to this count.", ink)
                        Section("Window", ink)
                        Toggle("Keep this window above others", alwaysOnTop, ink) { alwaysOnTop = it }
                        Hint("Off unless you turn it on. Closing the window leaves Fetch in the menu bar.", ink)
                        Section("Floating orb", ink)
                        Toggle("Keep Fetch on top", floatBubble, ink) { floatBubble = it }
                        Hint("A small window stays above other apps. Paste a link there to start it.", ink)
                        Section("Speed and queues", ink)
                        Field("Default speed KB/s", speed) { speed = it }
                        Field("Downloads at once", concurrent) { concurrent = it }
                        Field("Connections per host", host) { host = it }
                        OutlinedTextField(hostRules, { hostRules = it }, Modifier.fillMaxWidth().height(80.dp), label = { Text("Per host: example.com 4 512") })
                        Field("Browser integration port (0 = off)", port) { port = it }
                        Field("User agent", agent) { agent = it }
                        Field("Proxy host", proxy) { proxy = it }
                        Field("Proxy port", proxyPort) { proxyPort = it }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Action("Notify when empty", complete == "notify") { complete = "notify" }
                            Action("Sleep when empty", complete == "sleep") { complete = "sleep" }
                            Action("Shut down when empty", complete == "shutdown") { complete = "shutdown" }
                        }
                        Section("About", ink)
                        Text("Miaad Rajabi", color = ink.text)
                        Text("miaad.rajabi@gmail.com", color = ink.muted, fontSize = 13.sp)
                        Text("Download desk and the library underneath it.", color = ink.muted, fontSize = 12.sp)
                    }
                    FetchScrollbar(settingsScroll, ink, Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                }
                val savedDraft = SettingDraft(
                    current.writePolicy,
                    current.pauseOnBattery,
                    current.quotaMb,
                    if (current.networkProfile == "Metered") "Metered" else "Unmetered",
                    current.latencyAware,
                    current.useAlarm,
                    current.localToken,
                    current.speedLimitKb,
                )
                val nextDraft = SettingDraft(
                    writePolicy,
                    pauseOnBattery,
                    quotaMb.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                    if (networkProfile == "Metered") "Metered" else "Unmetered",
                    latencyAware,
                    useAlarm,
                    localToken,
                    speed.toIntOrNull() ?: 0,
                )
                Text(
                    SettingPreview.pending(savedDraft, nextDraft).joinToString("\n"),
                    color = ink.muted,
                    fontSize = 12.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { contentDescription = "Settings preview" },
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Action("Save", true) {
                        onClose(
                            current.copy(
                                theme = theme,
                                threads = threads.toIntOrNull()?.coerceIn(1, 32) ?: current.threads,
                                speedLimitKb = speed.toIntOrNull() ?: 0,
                                maxConcurrent = concurrent.toIntOrNull() ?: 3,
                                hostCap = host.toIntOrNull() ?: 8,
                                clipboard = clipboard,
                                port = port.toIntOrNull() ?: 0,
                                userAgent = agent,
                                proxyHost = proxy,
                                proxyPort = proxyPort.toIntOrNull() ?: 0,
                                onComplete = complete,
                                folder = folder,
                                hostRules = hostRules,
                                parallel = parallel,
                                minChunkKb = minChunk.toIntOrNull()?.coerceAtLeast(64) ?: 256,
                                retryAttempts = attempts.toIntOrNull()?.coerceIn(1, 8) ?: 5,
                                retryDelaySeconds = delaySec.toIntOrNull()?.coerceIn(1, 30) ?: 3,
                                backoffTenths = backoff.toIntOrNull()?.coerceIn(10, 30) ?: 20,
                                channelName = channel.ifBlank { "Fetch" },
                                iconKey = icon,
                                showProgress = showProgress,
                                persistent = persistent,
                                destination = destination,
                                publicDownloads = publicDownloads && destination == "auto",
                                publicFolder = publicFolder,
                                overwrite = overwrite,
                                checkSpace = checkSpace,
                                minFreeMb = minFree.toIntOrNull()?.coerceAtLeast(1) ?: 10,
                                writePolicy = writePolicy,
                                promptOpen = promptOpen,
                                verifySize = verifySize,
                                verifyChecksum = verifyChecksum,
                                verifyApk = verifyApk,
                                verifyType = verifyType,
                                verifySignature = verifySignature,
                                useAlarm = useAlarm,
                                floatBubble = floatBubble,
                                alwaysOnTop = alwaysOnTop,
                                pauseOnBattery = pauseOnBattery,
                                adaptiveConnections = adaptiveConnections,
                                localToken = localToken,
                                networkProfile = if (networkProfile == "Metered") "Metered" else "Unmetered",
                                latencyAware = latencyAware,
                                quotaMb = quotaMb.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                            ),
                        )
                    }
                    Action("Cancel", false) { onClose(null) }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, ink: Ink) {
    Text(title, color = ink.text, fontSize = 15.sp)
}

@Composable
private fun Hint(text: String, ink: Ink) {
    Text(text, color = ink.muted, fontSize = 12.sp)
}

@Composable
private fun Toggle(label: String, value: Boolean, ink: Ink, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(value, onChange)
        Text(label, color = ink.text, fontSize = 13.sp)
    }
}

@Composable
private fun InfoDialog(ink: Ink, job: DeskJob, onClose: () -> Unit) {
    Scrim {
        DialogCard(ink, job.fileName) {
            Text(job.state, color = ink.text)
            Text(job.url, color = ink.muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("Downloaded ${bytesOf(job.bytes)} of ${job.total?.let { bytesOf(it) } ?: "unknown"}", color = ink.text, fontSize = 13.sp)
            Text("${bytesOf(job.speed)}/s · ${timeLeft(job)} · ${job.connections} connections", color = ink.muted, fontSize = 12.sp)
            if (job.message.isNotBlank()) Text(job.message, color = ink.warning, fontSize = 12.sp)
            job.parts.forEach { part ->
                Text("Part ${part.index + 1}: ${bytesOf(part.downloaded)} / ${bytesOf(part.size)}", color = ink.muted, fontSize = 12.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Action("Open file", true) { openFolder(File(job.path, job.fileName)) }
                Action("Close", false, onClose)
            }
        }
    }
}

@Composable
private fun EditDialog(
    ink: Ink,
    job: DeskJob,
    queues: List<String>,
    onClose: (String?, String, String, Int, Int, String, String) -> Unit,
) {
    var name by remember { mutableStateOf(job.fileName) }
    var folder by remember { mutableStateOf(job.path) }
    var queue by remember { mutableStateOf(job.queue) }
    var threads by remember { mutableStateOf(job.connections.toString()) }
    var speed by remember { mutableStateOf((job.speedLimit / 1024L).toString()) }
    var checksum by remember { mutableStateOf(job.checksum) }
    var algorithm by remember { mutableStateOf(job.algorithm) }
    Scrim {
        DialogCard(ink, "Edit download") {
            Text(job.url, color = ink.muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Field("File name", name) { name = it }
            Field("Folder", folder) { folder = it }
            Field("Queue (${queues.joinToString()})", queue) { queue = it }
            Field("Connections", threads) { threads = it }
            Field("Speed KB/s", speed) { speed = it }
            Field("Checksum", checksum) { checksum = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("MD5", "SHA256", "SHA512").forEach { item -> Action(item, algorithm == item) { algorithm = item } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Action("Save", true) {
                    onClose(name, folder, queue, threads.toIntOrNull() ?: job.connections, speed.toIntOrNull() ?: 0, checksum, algorithm)
                }
                Action("Cancel", false) { onClose(null, "", "", 1, 0, "", "SHA256") }
            }
        }
    }
}

@Composable
private fun AboutDialog(ink: Ink, onClose: () -> Unit) {
    Scrim {
        DialogCard(ink, "Fetch 1.0.0") {
            Text("Miaad Rajabi", color = ink.text)
            Text("miaad.rajabi@gmail.com", color = ink.muted, fontSize = 13.sp)
            Text("Desktop downloads for macOS, Windows, and Linux. Built with Compose and OkHttp.", color = ink.muted, fontSize = 13.sp)
            Text("Version check: this build has no update server. ${UpdatePlan.decision(0, available = false, signatureValid = false)}", color = ink.muted, fontSize = 12.sp)
            Action("Close", true, onClose)
        }
    }
}

@Composable
private fun RuleDialog(ink: Ink, current: List<DeskRule>, onClose: (List<DeskRule>?) -> Unit) {
    var rules by remember { mutableStateOf(current) }
    var start by remember { mutableStateOf("pdf") }
    var next by remember { mutableStateOf("queue Night") }
    val preview = RulePreview.steps(start, mapOf(start to next))
    val allowed = RuleBook.canSave(start, next)
    Scrim {
        DialogCard(ink, "Rules") {
            Text("A saved rule can send one extension to a queue. Scripts are not run.", color = ink.muted, fontSize = 12.sp)
            rules.forEach { rule ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${rule.start} → ${rule.nextStep}", color = ink.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text("Remove", color = ink.danger, fontSize = 12.sp, modifier = Modifier.clickable { rules = rules.filterNot { it.start == rule.start } })
                }
            }
            Field("Extension", start) { start = it.trim() }
            Field("Next step", next) { next = it }
            Text(preview.joinToString(" → "), color = ink.text, fontSize = 13.sp)
            if (!allowed) Text("This chain is not saved.", color = ink.danger, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Action("Add step", allowed) {
                    if (!RuleBook.canSave(start, next)) return@Action
                    rules = rules.filterNot { it.start.equals(start, true) } + DeskRule(start, next)
                }
                Action("Save", true) { onClose(rules) }
                Action("Cancel", false) { onClose(null) }
            }
        }
    }
}

@Composable
internal fun MonthBoard(
    ink: Ink,
    fromHour: Int,
    toHour: Int,
    hour: Int,
    today: Int,
    startWeekday: Int,
    firstDayOfWeek: Int,
    daysInMonth: Int,
) {
    Text(ScheduleWindow.describe(fromHour, toHour, hour), color = ink.text, fontSize = 13.sp)
    Text(
        if (fromHour < 0) "No daily window is set." else "The same hour window applies every day. Today is marked.",
        color = ink.muted,
        fontSize = 12.sp,
    )
    val cells = monthCells(startWeekday, firstDayOfWeek, daysInMonth)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            weekdayLabels(firstDayOfWeek).forEach { name ->
                Text(name, color = ink.muted, fontSize = 11.sp, maxLines = 1, modifier = Modifier.weight(1f))
            }
        }
        cells.chunked(7).forEach { week ->
            Row {
                week.forEach { day ->
                    Box(Modifier.weight(1f).height(28.dp), contentAlignment = Alignment.Center) {
                        if (day > 0) {
                            Text(
                                day.toString(),
                                color = if (day == today) ink.accent else ink.text,
                                fontSize = 12.sp,
                                fontWeight = if (day == today) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ScheduleDialog(ink: Ink, queue: DeskQueue, onClose: () -> Unit) {
    val facts = remember { monthFacts(java.util.Calendar.getInstance()) }
    Scrim {
        DialogCard(ink, queue.name) {
            MonthBoard(ink, queue.fromHour, queue.toHour, facts.hour, facts.today, facts.startWeekday, facts.firstDayOfWeek, facts.days)
            Action("Close", true, onClose)
        }
    }
}

@Composable
private fun QueueDialog(ink: Ink, onClose: (DeskQueue?) -> Unit) {
    var name by remember { mutableStateOf("Night") }
    var max by remember { mutableStateOf("2") }
    var from by remember { mutableStateOf("-1") }
    var to by remember { mutableStateOf("-1") }
    Scrim {
        DialogCard(ink, "New queue") {
            Field("Name", name) { name = it }
            Field("At once", max) { max = it }
            Field("From hour (-1 = always)", from) { from = it }
            Field("To hour", to) { to = it }
            val facts = remember { monthFacts(java.util.Calendar.getInstance()) }
            MonthBoard(ink, from.toIntOrNull() ?: -1, to.toIntOrNull() ?: -1, facts.hour, facts.today, facts.startWeekday, facts.firstDayOfWeek, facts.days)
            Hint("The next run uses this computer's local hour, including daylight saving. It does not start a download by itself.", ink)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Action("Add", true) {
                    onClose(DeskQueue(name.ifBlank { "Queue" }, max.toIntOrNull() ?: 2, from.toIntOrNull() ?: -1, to.toIntOrNull() ?: -1))
                }
                Action("Cancel", false) { onClose(null) }
            }
        }
    }
}

@Composable
private fun CategoryDialog(ink: Ink, onClose: (DeskCategory?) -> Unit) {
    var name by remember { mutableStateOf("Documents") }
    var extensions by remember { mutableStateOf("pdf,doc,docx") }
    var folder by remember { mutableStateOf("Documents") }
    Scrim {
        DialogCard(ink, "New category") {
            Field("Name", name) { name = it }
            Field("Extensions", extensions) { extensions = it }
            Field("Subfolder", folder) { folder = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Action("Add", true) { onClose(DeskCategory(name.ifBlank { "Other" }, extensions, folder)) }
                Action("Cancel", false) { onClose(null) }
            }
        }
    }
}

@Composable
private fun ConfirmDialog(ink: Ink, message: String, onClose: (Boolean) -> Unit) {
    Scrim {
        DialogCard(ink, "Fetch") {
            Text(message, color = ink.text)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Action("Continue", true) { onClose(true) }
                Action("Cancel", false) { onClose(false) }
            }
        }
    }
}

@Composable
internal fun Scrim(content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val scale by animateFloatAsState(if (shown) 1f else 0.9f, tween(280, easing = FastOutSlowInEasing))
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(220))
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.40f * alpha)).padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }) { content() }
    }
}

@Composable
private fun DialogCard(ink: Ink, title: String, width: androidx.compose.ui.unit.Dp = 520.dp, scroll: Boolean = true, content: @Composable () -> Unit) {
    val state = rememberScrollState()
    Column(
        Modifier.widthIn(max = width).fillMaxWidth().heightIn(max = 720.dp)
            .background(ink.panel, RoundedCornerShape(18.dp)).glassPanel(ink, 18.dp).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, color = ink.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Box(Modifier.weight(1f, fill = false).fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().then(if (scroll) Modifier.verticalScroll(state).padding(end = 12.dp) else Modifier), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
            if (scroll) FetchScrollbar(state, ink, Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    }
}

@Composable
internal fun Field(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(value, onValue, Modifier.fillMaxWidth(), singleLine = true, label = { Text(label) })
}

private fun matchesCategory(category: DeskCategory, categories: List<DeskCategory>, fileName: String): Boolean {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    val own = category.extensions.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
    if (own.isNotEmpty()) return own.contains(ext)
    return categories.none { other ->
        other.name != category.name && other.extensions.split(',').map { it.trim().lowercase() }.contains(ext)
    }
}

internal fun keptForUndo(deleteFile: Boolean, jobs: List<DeskJob>): List<DeskJob> = if (deleteFile) emptyList() else jobs

@Composable
internal fun UndoBar(ink: Ink, count: Int, onUndo: () -> Unit, onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Removed $count from the list. The file was kept.", color = ink.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text("Undo", color = ink.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { contentDescription = "Undo remove" }.clickable(onClick = onUndo))
        Text("Dismiss", color = ink.muted, fontSize = 13.sp, modifier = Modifier.semantics { contentDescription = "Dismiss undo" }.clickable(onClick = onDismiss))
    }
}

internal fun libraryRows(
    jobs: List<DeskJob>,
    categories: List<DeskCategory>,
    library: String,
    queueFilter: String,
    search: String,
    sort: String,
    ascending: Boolean,
): List<DeskJob> {
    return jobs.filter { job ->
        val category = categories.firstOrNull { it.name == library }
        val categoryOk = category == null || matchesCategory(category, categories, job.fileName)
        val statusOk = when (library) {
            "Unfinished" -> job.state != "Saved"
            "Finished", "Completed" -> job.state == "Saved"
            "Active" -> job.state == "Running"
            "Queued" -> job.state == "Queued"
            "Needs attention" -> job.state == "Failed"
            else -> true
        }
        val queueOk = queueFilter == "All" || job.queue == queueFilter
        val textOk = search.isBlank() || job.fileName.contains(search, true) || job.url.contains(search, true)
        categoryOk && statusOk && queueOk && textOk
    }.sortedWith(compare(sort, ascending))
}

private fun compare(sort: String, ascending: Boolean): Comparator<DeskJob> {
    val base = when (sort) {
        "name" -> compareBy<DeskJob> { it.fileName.lowercase() }
        "size" -> compareBy { it.total ?: -1L }
        "status" -> compareBy { it.state }
        "speed" -> compareBy { it.speed }
        "left" -> compareBy { etaSeconds(it) }
        else -> compareBy { it.addedAt }
    }
    return if (ascending) base else base.reversed()
}

private fun etaSeconds(job: DeskJob): Long {
    val total = job.total ?: return Long.MAX_VALUE
    if (job.speed <= 0L) return Long.MAX_VALUE
    return ((total - job.bytes).coerceAtLeast(0L) / job.speed)
}

internal fun timeLeft(job: DeskJob, pace: Long = job.speed): String {
    val total = job.total ?: return "—"
    if (pace <= 0L) return "—"
    val seconds = ((total - job.bytes).coerceAtLeast(0L) / pace)
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val rest = seconds % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m ${rest}s"
}

internal fun bytesOf(value: Long): String {
    if (value < 1024L) return "$value B"
    val units = listOf("KB", "MB", "GB", "TB")
    var size = value.toDouble()
    var index = -1
    while (size >= 1024 && index < units.lastIndex) {
        size /= 1024
        index++
    }
    val shown = (size * 10).toLong() / 10.0
    return "$shown ${units[index]}"
}

private fun openFolder(file: File) {
    val target = if (file.isDirectory) file else file.parentFile ?: file
    target.mkdirs()
    if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(if (file.exists()) file else target)
}

private fun erase(job: DeskJob) {
    File(job.path, job.fileName + ".span-media").deleteRecursively()
    File(job.path, job.fileName).delete()
    File(job.path, job.fileName + ".span").delete()
    File(job.path, job.fileName + ".span-plan").delete()
    repeat(32) { index -> File(job.path, "${job.fileName}.span-part-$index").delete() }
}

internal fun readClipboard(): String? {
    return try {
        val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
        clipboard.getData(DataFlavor.stringFlavor) as? String
    } catch (_: Exception) {
        null
    }
}

private object MacBattery {
    fun onBattery(): Boolean? {
        if (!System.getProperty("os.name").contains("Mac", ignoreCase = true)) return null
        return runCatching {
            val text = ProcessBuilder("pmset", "-g", "batt").redirectErrorStream(true).start().inputStream.bufferedReader().readText()
            when {
                text.contains("AC Power") -> false
                text.contains("Battery Power") -> true
                else -> null
            }
        }.getOrNull()
    }
}

private fun clientFor(settings: DeskSettings): OkHttpClient {
    val builder = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(0, TimeUnit.SECONDS)
    if (settings.proxyHost.isNotBlank() && settings.proxyPort in 1..65535) {
        builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(settings.proxyHost, settings.proxyPort)))
    }
    return builder.build()
}

private fun listenForBrowser() {
    Thread {
        val input = System.`in`
        while (true) {
            val header = ByteArray(4)
            if (input.read(header) != 4) return@Thread
            val length = (header[0].toInt() and 0xFF) or
                ((header[1].toInt() and 0xFF) shl 8) or
                ((header[2].toInt() and 0xFF) shl 16) or
                ((header[3].toInt() and 0xFF) shl 24)
            if (length !in 1..1_000_000) return@Thread
            val body = ByteArray(length)
            var got = 0
            while (got < length) {
                val read = input.read(body, got, length - got)
                if (read < 0) return@Thread
                got += read
            }
            val text = body.toString(Charsets.UTF_8)
            val url = text.substringAfter("\"url\":\"", "").substringBefore("\"").replace("\\/", "/")
            if (url.startsWith("http://") || url.startsWith("https://")) bridgeLinks.add(url)
        }
    }.apply {
        isDaemon = true
        name = "span-native-messaging"
        start()
    }
}

private fun savedProblem(file: File, job: DeskJob, settings: DeskSettings, contentType: String?): String? {
    if (settings.verifySize && job.total != null && job.total!! > 0L && file.length() != job.total) {
        return "Size does not match what the server promised."
    }
    val apk = file.name.endsWith(".apk", true) || file.name.endsWith(".apks", true)
    if (settings.verifyApk && apk) {
        val head = file.inputStream().use { it.readNBytes(4) }
        val zip = head.size >= 4 && head[0] == 0x50.toByte() && head[1] == 0x4B.toByte()
        if (!zip) return "The APK structure is not a package."
    }
    if (settings.verifyType && !contentType.isNullOrBlank()) {
        val mime = contentType.substringBefore(';').lowercase()
        val ext = file.name.substringAfterLast('.', "").lowercase()
        val expected = when (ext) {
            "png", "jpg", "jpeg", "gif", "webp" -> "image/"
            "mp4", "mkv", "webm", "mov" -> "video/"
            "mp3", "m4a", "flac", "wav" -> "audio/"
            "apk", "zip" -> "application/"
            else -> ""
        }
        if (expected.isNotBlank() && mime != "application/octet-stream" && !mime.startsWith(expected)) {
            return "Content-Type was $mime."
        }
    }
    if (settings.verifySignature && file.name.endsWith(".apk", true)) {
        val signed = try {
            ZipFile(file).use { zip ->
                zip.entries().asSequence().any { entry ->
                    val name = entry.name.uppercase()
                    name.startsWith("META-INF/") && (name.endsWith(".SF") || name.endsWith(".RSA") || name.endsWith(".EC"))
                }
            }
        } catch (_: Exception) {
            false
        }
        if (!signed) return "The APK signature could not be read."
    }
    return null
}

private fun uniqueName(name: String, dir: String): String {
    val dot = name.lastIndexOf('.')
    val base = if (dot > 0) name.substring(0, dot) else name
    val ext = if (dot > 0) name.substring(dot) else ""
    var index = 1
    var candidate = name
    while (File(dir, candidate).exists()) {
        candidate = SpanRules.safeFileName("$base-$index$ext")
        index++
    }
    return candidate
}

@Composable
private fun FloatingOrb(ink: Ink, onLink: (String) -> Unit) {
    var link by remember { mutableStateOf("") }
    Window(
        onCloseRequest = {},
        state = rememberWindowState(position = WindowPosition(Alignment.CenterEnd), size = DpSize(280.dp, 148.dp)),
        title = "Fetch",
        alwaysOnTop = true,
        resizable = false,
    ) {
        Column(Modifier.fillMaxSize().background(ink.panel).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Fetch", color = ink.text)
            OutlinedTextField(link, { link = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Paste a link") })
            Action("Download", true) {
                val value = link.trim()
                if (value.startsWith("http://") || value.startsWith("https://")) {
                    onLink(value)
                    link = ""
                }
            }
        }
    }
}

internal fun fetchIcon(): Painter {
    val stream = Thread.currentThread().contextClassLoader.getResourceAsStream("fetch.svg") ?: return trayPainter()
    return stream.use { androidx.compose.ui.res.loadSvgPainter(it, androidx.compose.ui.unit.Density(1f)) }
}

internal fun trayPainter(iconKey: String = "fetch", active: Boolean = false, paused: Boolean = false): Painter {
    val image = BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
    g.scale(1.25, 1.25)
    val mark = java.awt.geom.Path2D.Float().apply {
        when (iconKey) {
            "bolt" -> { moveTo(14.0, 2.0); lineTo(5.0, 13.0); lineTo(11.0, 13.0); lineTo(9.0, 23.0); lineTo(20.0, 10.0); lineTo(14.0, 10.0); closePath() }
            "layers" -> { moveTo(3.0, 5.0); lineTo(21.0, 5.0); lineTo(21.0, 10.0); lineTo(3.0, 10.0); closePath(); moveTo(3.0, 14.0); lineTo(21.0, 14.0); lineTo(21.0, 19.0); lineTo(3.0, 19.0); closePath() }
            "tray" -> { moveTo(10.0, 2.0); lineTo(14.0, 2.0); lineTo(14.0, 11.0); lineTo(19.0, 11.0); lineTo(12.0, 18.0); lineTo(5.0, 11.0); lineTo(10.0, 11.0); closePath() }
            else -> { moveTo(4.0, 3.0); lineTo(20.0, 3.0); lineTo(20.0, 7.0); lineTo(8.0, 7.0); lineTo(8.0, 10.0); lineTo(17.0, 10.0); lineTo(17.0, 15.0); lineTo(21.0, 15.0); lineTo(14.0, 22.0); lineTo(7.0, 15.0); lineTo(12.0, 15.0); lineTo(12.0, 14.0); lineTo(8.0, 14.0); lineTo(8.0, 20.0); lineTo(4.0, 20.0); closePath() }
        }
    }
    // A fine light keyline preserves the mark on either light or dark native menu bars.
    g.stroke = java.awt.BasicStroke(1.2f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND)
    g.color = java.awt.Color(255, 255, 255, 210); g.draw(mark)
    g.color = java.awt.Color(30, 42, 60); g.fill(mark)
    if (active || paused) {
        g.color = java.awt.Color.WHITE; g.fillOval(17, 17, 8, 8)
        g.color = if (active) java.awt.Color(40, 142, 105) else java.awt.Color(190, 124, 35)
        g.fillOval(18, 18, 6, 6)
    }
    g.dispose()
    return image.toPainter()
}
