package com.miaadrajabi.span

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import kotlinx.coroutines.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.skia.Image as SkiaImage
import java.io.File
import java.util.concurrent.TimeUnit

@Composable
internal fun MediaShortcut(platform: String, ink: Ink, compact: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(10.dp)).background(ink.raised.copy(alpha = 0.8f)).border(1.dp, ink.line, RoundedCornerShape(10.dp))
        .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PlatformMark(platform, 20.dp)
        if (!compact) Text(platform, color = ink.text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun PlatformMark(platform: String, size: Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        if (platform == "Instagram") {
            drawRoundRect(Brush.linearGradient(listOf(Color(0xFFFDA44B), Color(0xFFE93F78), Color(0xFF8D4DE8))), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .27f))
            drawRoundRect(Color.White, topLeft = androidx.compose.ui.geometry.Offset(w*.22f,h*.22f), size = androidx.compose.ui.geometry.Size(w*.56f,h*.56f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w*.16f), style = androidx.compose.ui.graphics.drawscope.Stroke(w*.07f))
            drawCircle(Color.White, radius = w*.14f, style = androidx.compose.ui.graphics.drawscope.Stroke(w*.065f))
            drawCircle(Color.White, radius = w*.045f, center = androidx.compose.ui.geometry.Offset(w*.69f,h*.31f))
        } else {
            drawRoundRect(Color(0xFFEF3C49), topLeft = androidx.compose.ui.geometry.Offset(0f,h*.12f), size = androidx.compose.ui.geometry.Size(w,h*.76f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w*.22f))
            drawPath(Path().apply { moveTo(w*.41f,h*.31f); lineTo(w*.70f,h*.50f); lineTo(w*.41f,h*.69f); close() }, Color.White)
        }
    }
}

@Composable
internal fun MediaStudioWindow(ink: Ink, initialUrl: String, platform: String, settings: DeskSettings, queues: List<String>, onClose: () -> Unit,
    onSave: (WatchStream, String, String, String, String) -> Unit) {
    Window(onCloseRequest = onClose, title = "Fetch · Media Studio", icon = remember { fetchIcon() },
        state = rememberWindowState(position = WindowPosition(Alignment.Center), size = DpSize(740.dp, 780.dp))) {
        LaunchedEffect(Unit) { window.minimumSize = java.awt.Dimension(500, 530) }
        FetchTheme(ink) { MediaStudio(ink, initialUrl, platform, settings, queues, onClose, onSave) }
    }
}

@Composable
internal fun MediaStudio(ink: Ink, initialUrl: String, initialPlatform: String, settings: DeskSettings, queues: List<String>, onClose: () -> Unit,
    onSave: (WatchStream, String, String, String, String) -> Unit,
    resolve: suspend (String) -> WatchPreview = { WatchPage.load(it) },
    verify: suspend (WatchStream) -> WatchStream = { WatchPage.verify(it) },
    player: @Composable (WatchStream, Modifier, (String) -> Unit) -> Unit = { stream, modifier, error -> FetchStage(stream, ink, modifier, error) },
) {
    val scope = rememberCoroutineScope()
    var platform by remember { mutableStateOf(initialPlatform) }
    var url by remember { mutableStateOf(initialUrl) }
    var preview by remember { mutableStateOf<WatchPreview?>(null) }
    var selected by remember { mutableStateOf<WatchStream?>(null) }
    var verified by remember { mutableStateOf<WatchStream?>(null) }
    var thumbnail by remember { mutableStateOf<ImageBitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var lookup by remember { mutableStateOf<Job?>(null) }
    var qualityMenu by remember { mutableStateOf(false) }
    var queueMenu by remember { mutableStateOf(false) }
    var folder by remember { mutableStateOf(settings.folder) }
    var queue by remember { mutableStateOf(queues.firstOrNull() ?: "Main") }
    var generation by remember { mutableStateOf(0) }
    val latestUrl by rememberUpdatedState(url)
    fun clear() { generation++; lookup?.cancel(); preview = null; selected = null; verified = null; thumbnail = null; busy = false; checking = false; error = null; playerError = null }
    fun inspect() {
        if (url.toHttpUrlOrNull() == null) return
        clear()
        val ticket = generation
        val link = url.trim()
        busy = true
        lookup = scope.launch {
            try {
                val result = resolve(link)
                if (ticket == generation && latestUrl.trim() == link) {
                    preview = result
                    selected = result.streams.firstOrNull()
                    platform = if (WatchPage.isInstagram(link)) "Instagram" else if (WatchPage.isWatch(link)) "YouTube" else platform
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (problem: Exception) { if (ticket == generation) error = problem.message ?: "The video could not be loaded." }
            finally { if (ticket == generation) busy = false }
        }
    }
    LaunchedEffect(Unit) { if (initialUrl.isNotBlank()) inspect() }
    LaunchedEffect(selected, generation) {
        verified = null; playerError = null
        val choice = selected ?: return@LaunchedEffect
        checking = true
        try { verified = verify(choice) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (problem: Exception) { error = problem.message ?: "The file could not be opened." }
        finally { checking = false }
    }
    LaunchedEffect(preview?.thumbnail) {
        thumbnail = null
        val address = preview?.thumbnail ?: return@LaunchedEffect
        thumbnail = withContext(Dispatchers.IO) { runCatching {
            val client = OkHttpClient.Builder().callTimeout(12, TimeUnit.SECONDS).build()
            client.newCall(Request.Builder().url(address).build()).execute().use { response ->
                check(response.isSuccessful)
                val data = response.body!!.byteStream().use { it.readNBytes(4_000_001) }
                check(data.size <= 4_000_000)
                SkiaImage.makeFromEncoded(data).toComposeImageBitmap()
            }
        }.getOrNull() }
    }
    Box(Modifier.fillMaxSize().testTag("media-studio")) {
        FrostedBackdrop(ink)
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(ink.primaryContainer), contentAlignment = Alignment.Center) { FetchGlyph("video", ink.onPrimaryContainer, 24.dp) }
                Column(Modifier.weight(1f)) {
                    Text("Media Studio", color = ink.text, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                    Text("Preview it. Pick a quality. Keep the original.", color = ink.muted, fontSize = 12.sp)
                }
                QuietIcon("close", ink, "Close media studio", onClose)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("YouTube", "Instagram").forEach { name -> MediaShortcut(name, ink) { platform = name; url = ""; clear() } }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(url, { url = it; clear() }, Modifier.weight(1f).testTag("media-url"), singleLine = true, shape = RoundedCornerShape(12.dp),
                    placeholder = { Text(if (platform == "Instagram") "Paste a post or reel link" else "Paste a YouTube video link", fontSize = 12.sp) })
                QuietIcon("copy", ink, "Paste media link") { readClipboard()?.let { url = it.trim(); clear() } }
                Button(onClick = { inspect() }, enabled = !busy && url.toHttpUrlOrNull() != null, elevation = null, modifier = Modifier.height(48.dp).testTag("preview-media")) { Text(if (busy) "Loading…" else "Preview", fontSize = 12.sp) }
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFF0C111C)), contentAlignment = Alignment.Center) {
                    val stream = verified
                    if (stream != null && stream.canPreview && playerError == null) player(stream, Modifier.fillMaxSize()) { playerError = it }
                    else {
                        thumbnail?.let { Image(it, preview?.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                        Column(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = .35f)).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (busy || checking) CircularProgressIndicator(color = ink.accent, strokeWidth = 2.dp, modifier = Modifier.size(30.dp))
                            else PlatformMark(platform, 48.dp)
                            Text(when { busy -> "Finding the video and available qualities…"; checking -> "Opening the original media…"; playerError != null -> "Preview is unavailable for this codec. The file is ready to download."; stream != null -> "This format can be downloaded. Choose MP4 or M4A for an inline preview."; else -> "Your next video starts here" }, color = Color.White, fontSize = 13.sp)
                            if (preview == null && !busy) Text("Paste a link above to see its thumbnail and available files.", color = Color(0xFFA6B4CA), fontSize = 11.sp)
                        }
                    }
                }
                error?.let { Text(it, color = ink.danger, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().glassPanel(ink, 12.dp).padding(12.dp).testTag("media-error")) }
                preview?.let { info ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(info.title, color = ink.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(listOf(info.author, mediaTime(info.duration.toFloat())).filter { it.isNotBlank() }.joinToString(" · "), color = ink.muted, fontSize = 11.sp)
                        Text(info.note, color = ink.muted, fontSize = 12.sp)
                    }
                    Box {
                        Row(Modifier.fillMaxWidth().glassPanel(ink, 12.dp).clickable { qualityMenu = true }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("QUALITY & FORMAT", color = ink.accent, fontSize = 10.sp, letterSpacing = 1.sp)
                                Text(selected?.label.orEmpty(), color = ink.text, fontSize = 13.sp)
                            }
                            selected?.bytes?.let { Text(bytesOf(it), color = ink.muted, fontSize = 11.sp) }
                            FetchGlyph("chevron", ink.muted, 16.dp)
                        }
                        DropdownMenu(qualityMenu, { qualityMenu = false }, Modifier.heightIn(max = 280.dp)) {
                            info.streams.forEach { stream -> DropdownMenuItem(onClick = { error = null; selected = stream; qualityMenu = false }) { Text(stream.label, fontSize = 12.sp) } }
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().glassPanel(ink, 16.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuietIcon("folder", ink, "Choose media destination") {
                        val chooser = javax.swing.JFileChooser(File(folder)).apply { fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY }
                        if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) folder = chooser.selectedFile.absolutePath
                    }
                    Text(folder, color = ink.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Box {
                        TextButton(onClick = { queueMenu = true }) { Text(queue, fontSize = 11.sp) }
                        DropdownMenu(queueMenu, { queueMenu = false }) { queues.forEach { name -> DropdownMenuItem(onClick = { queue = name; queueMenu = false }) { Text(name) } } }
                    }
                }
                Button(onClick = { val file = verified; val info = preview; if (file != null && info != null) onSave(file, info.title, info.pageUrl, folder, queue) }, enabled = verified != null && !checking && !busy,
                    modifier = Modifier.fillMaxWidth().testTag("download-media"), elevation = null) {
                    FetchGlyph("down", if (verified != null) ink.onAccent else ink.muted, 18.dp)
                    Spacer(Modifier.width(8.dp)); Text("Add to downloads", fontSize = 13.sp)
                }
            }
        }
    }
}
