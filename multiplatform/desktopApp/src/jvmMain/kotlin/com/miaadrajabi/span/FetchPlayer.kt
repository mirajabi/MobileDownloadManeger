package com.miaadrajabi.span

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.*
import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.layout.StackPane
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.scene.media.MediaView
import javafx.util.Duration
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities

/** The native player cannot set request headers. Relay just this file over loopback. */
internal class StreamGate(private val stream: WatchStream) : Closeable {
    private val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
    private val path = "/${UUID.randomUUID()}/media.${stream.extension}"
    val localUrl = "http://127.0.0.1:${server.localPort}$path"
    private val open = AtomicBoolean(true)
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    private val workers = Executors.newFixedThreadPool(4) { task -> Thread(task, "fetch-media-relay").apply { isDaemon = true } }
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    fun start() {
        Thread({
            while (open.get()) {
                val socket = try { server.accept() } catch (_: Exception) { break }
                sockets += socket
                try { workers.execute { serve(socket) } } catch (_: Exception) { sockets.remove(socket); socket.close() }
            }
        }, "fetch-media-listener").apply { isDaemon = true; start() }
    }
    private fun serve(socket: Socket) {
        try { socket.use {
            socket.soTimeout = 15000
            val input = socket.getInputStream()
            val head = StringBuilder()
            while (head.length < 8192 && !head.endsWith("\r\n\r\n")) {
                val b = input.read(); if (b < 0) return; head.append(b.toChar())
            }
            val lines = head.toString().split("\r\n")
            val parts = lines.first().split(' ')
            val output = socket.getOutputStream()
            if (parts.size < 2 || parts[1] != path || parts[0] !in setOf("GET", "HEAD")) {
                output.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray()); return
            }
            val range = lines.firstOrNull { it.startsWith("Range:", true) }?.substringAfter(':')?.trim()
            val request = Request.Builder().url(stream.url).apply {
                stream.headers.forEach { (key, value) -> header(key, value) }
                range?.let { header("Range", it) }
                if (parts[0] == "HEAD") head()
            }.build()
            http.newCall(request).execute().use { response ->
                val header = buildString {
                    append("HTTP/1.1 ${response.code} ${response.message}\r\n")
                    listOf("Content-Type", "Content-Length", "Content-Range", "Accept-Ranges").forEach { key -> response.header(key)?.let { append("$key: $it\r\n") } }
                    append("Connection: close\r\n\r\n")
                }
                output.write(header.toByteArray(Charsets.ISO_8859_1))
                if (parts[0] == "GET") response.body?.byteStream()?.use { it.copyTo(output, 64 * 1024) }
                output.flush()
            }
        } } catch (_: Exception) { /* Seeking or closing the player cancels old range requests. */ }
        finally { sockets.remove(socket) }
    }
    override fun close() {
        if (!open.compareAndSet(true, false)) return
        server.close()
        http.dispatcher.cancelAll()
        sockets.forEach { runCatching { it.close() } }
        workers.shutdownNow()
        http.connectionPool.evictAll()
    }
}

internal class PlayerController {
    var playing by mutableStateOf(false)
    var ready by mutableStateOf(false)
    var position by mutableStateOf(0f)
    var duration by mutableStateOf(0f)
    var volume by mutableStateOf(0.8f)
    var error by mutableStateOf<String?>(null)
    var player: MediaPlayer? = null
    var audio: MediaPlayer? = null
    val closed = AtomicBoolean(false)
    fun toggle() { Platform.runLater { player?.let { if (it.status == MediaPlayer.Status.PLAYING) { it.pause(); audio?.pause() } else { audio?.seek(it.currentTime); it.play(); audio?.play() } } } }
    fun seek(seconds: Float) { Platform.runLater { player?.seek(Duration.seconds(seconds.toDouble())); audio?.seek(Duration.seconds(seconds.toDouble())) } }
    fun volume(value: Float) { volume = value; Platform.runLater { player?.volume = value.toDouble(); audio?.volume = value.toDouble() } }
    fun close() { closed.set(true); runCatching { Platform.runLater { player?.dispose(); audio?.dispose(); player = null; audio = null } } }
}

@Composable
internal fun FetchStage(stream: WatchStream, ink: Ink, modifier: Modifier = Modifier, onError: (String) -> Unit = {}) {
    key(stream.url) {
        val controller = remember { PlayerController() }
        val gate = remember { StreamGate(stream).also { it.start() } }
        val audioGate = remember { stream.audio?.let { StreamGate(it).also { gate -> gate.start() } } }
        val latestError by rememberUpdatedState(onError)
        DisposableEffect(Unit) { onDispose { controller.close(); gate.close(); audioGate?.close() } }
        Column(modifier.background(Color(0xFF0C111C))) {
            SwingPanel(modifier = Modifier.fillMaxWidth().weight(1f), background = Color(0xFF0C111C), factory = {
                val panel = JFXPanel()
                Platform.setImplicitExit(false)
                Platform.runLater {
                    if (!controller.closed.get()) {
                        fun fail(message: String) { SwingUtilities.invokeLater { if (!controller.closed.get()) { controller.error = message; latestError(message) } } }
                        try {
                            val media = Media(gate.localUrl)
                            val player = MediaPlayer(media)
                            controller.player = player
                            val audio = audioGate?.let { MediaPlayer(Media(it.localUrl)) }; controller.audio = audio
                            audio?.volume = controller.volume.toDouble()
                            player.volume = controller.volume.toDouble()
                            val view = MediaView(player)
                            view.isPreserveRatio = true
                            val root = StackPane(view).apply { style = "-fx-background-color: #0c111c;" }
                            view.fitWidthProperty().bind(root.widthProperty())
                            view.fitHeightProperty().bind(root.heightProperty())
                            panel.scene = Scene(root, 640.0, 300.0, javafx.scene.paint.Color.web("#0c111c"))
                            media.setOnError { fail(media.error?.message ?: "This format cannot be previewed here.") }
                            player.setOnError { fail(player.error?.message ?: "Playback could not start.") }
                            var videoReady = false
                            var audioReady = audio == null
                            fun begin() {
                                if (!videoReady || !audioReady || controller.closed.get()) return
                                val duration = player.totalDuration.toSeconds().toFloat().takeIf { it.isFinite() } ?: 0f
                                SwingUtilities.invokeLater { if (!controller.closed.get()) { controller.ready = true; controller.duration = duration } }
                                player.play(); audio?.play()
                            }
                            player.setOnReady { videoReady = true; begin() }
                            audio?.setOnReady { audioReady = true; begin() }
                            audio?.setOnError { player.pause(); fail(audio.error?.message ?: "The audio track could not start.") }
                            player.setOnPlaying { audio?.seek(player.currentTime); audio?.play(); SwingUtilities.invokeLater { if (!controller.closed.get()) controller.playing = true } }
                            player.setOnStalled { audio?.pause() }
                            player.setOnPaused { audio?.pause(); SwingUtilities.invokeLater { if (!controller.closed.get()) controller.playing = false } }
                            player.setOnEndOfMedia { player.pause(); audio?.pause(); player.seek(Duration.ZERO); audio?.seek(Duration.ZERO) }
                            player.currentTimeProperty().addListener { _, _, time ->
                                if (audio != null && player.status == MediaPlayer.Status.PLAYING && kotlin.math.abs(audio.currentTime.toSeconds() - time.toSeconds()) > 0.7) audio.seek(time)
                                SwingUtilities.invokeLater { if (!controller.closed.get()) controller.position = time.toSeconds().toFloat() }
                            }
                        } catch (error: Exception) { fail(error.message ?: "The player could not start.") }
                    }
                }
                panel
            })
            Row(Modifier.fillMaxWidth().background(ink.raised).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuietIcon(if (controller.playing) "pause" else "play", ink, if (controller.playing) "Pause preview" else "Play preview") { if (controller.ready) controller.toggle() }
                Text(mediaTime(controller.position), color = ink.text, fontSize = 11.sp)
                Slider(controller.position.coerceIn(0f, controller.duration.coerceAtLeast(1f)), { controller.seek(it) }, Modifier.weight(1f), enabled = controller.ready && controller.duration > 0, valueRange = 0f..controller.duration.coerceAtLeast(1f))
                Text(mediaTime(controller.duration), color = ink.muted, fontSize = 11.sp)
                Text("Vol", color = ink.muted, fontSize = 10.sp)
                Slider(controller.volume, { controller.volume(it) }, Modifier.width(62.dp), enabled = controller.ready)
            }
        }
    }
}
internal fun mediaTime(seconds: Float): String {
    val total = seconds.takeIf { it.isFinite() }?.toInt()?.coerceAtLeast(0) ?: 0
    return "%d:%02d".format(total / 60, total % 60)
}
