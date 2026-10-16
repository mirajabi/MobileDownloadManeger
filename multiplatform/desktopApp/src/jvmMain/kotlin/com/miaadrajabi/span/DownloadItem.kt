package com.miaadrajabi.span

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Checkbox
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import kotlin.math.roundToInt

internal data class TransferActions(
    val select: () -> Unit, val openFile: () -> Unit, val pause: () -> Unit, val start: () -> Unit,
    val edit: () -> Unit, val openFolder: () -> Unit, val remove: () -> Unit,
    val extract: (() -> Unit)? = null, val stop: (() -> Unit)? = null,
    val preview: (() -> Unit)? = null, val inspect: (() -> Unit)? = null,
)

@Composable
internal fun DownloadCard(
    job: DeskJob, ink: Ink, selected: Boolean, comfortable: Boolean,
    onSelect: () -> Unit, onOpenFile: () -> Unit, onPause: () -> Unit, onStart: () -> Unit,
    onEdit: () -> Unit, onOpen: () -> Unit, onRemove: () -> Unit, onExtract: () -> Unit,
    onStop: (() -> Unit)? = null, onInspect: (() -> Unit)? = null,
) = TransferTile(job, ink, selected, comfortable, false,
    TransferActions(onSelect, onOpenFile, onPause, onStart, onEdit, onOpen, onRemove, onExtract, onStop, inspect = onInspect))

@Composable
internal fun DownloadRow(
    job: DeskJob, ink: Ink, selected: Boolean, showProgress: Boolean, nameWidth: Int = 160,
    onClick: () -> Unit, onEdit: () -> Unit, onPause: () -> Unit, onStop: () -> Unit,
    onStart: () -> Unit, onOpen: () -> Unit, onRemove: () -> Unit, onPreview: () -> Unit,
    onInspect: (() -> Unit)? = null,
) = TransferTile(job, ink, selected, false, true,
    TransferActions(onClick, onOpen, onPause, onStart, onEdit, onOpen, onRemove, stop = onStop, preview = onPreview, inspect = onInspect),
    showProgress, nameWidth)

internal data class TransferMenuItem(val label: String, val glyph: String, val enabled: Boolean = true, val destructive: Boolean = false, val run: () -> Unit)

internal fun transferMenu(job: DeskJob, actions: TransferActions): List<TransferMenuItem> = buildList {
    when (job.state) {
        "Running" -> add(TransferMenuItem("Pause download", "pause", run = actions.pause))
        "Saved" -> add(TransferMenuItem("Open file", "doc", run = actions.openFile))
        "Queued" -> actions.stop?.let { add(TransferMenuItem("Stop download", "stop", run = it)) }
        else -> add(TransferMenuItem("Resume download", "play", run = actions.start))
    }
    add(TransferMenuItem(if (job.expanded) "Collapse details" else "Expand details", "chevron") { job.expanded = !job.expanded })
    actions.inspect?.let { add(TransferMenuItem("Open details panel", "info", run = it)) }
    add(TransferMenuItem("Open folder", "folder", run = actions.openFolder))
    add(TransferMenuItem("Copy download link", "copy") { copyText(job.url) })
    add(TransferMenuItem("Edit download", "pencil", enabled = job.state != "Running", run = actions.edit))
    if (job.state != "Saved") {
        add(TransferMenuItem(if (job.priority > 0) "Normal priority" else "Higher priority", "bars") { job.priority = if (job.priority > 0) 0 else 1; job.waits = 0 })
        add(TransferMenuItem("Boost for 10 minutes", "bolt", enabled = job.state == "Running") { job.boost = 5; job.boostUntil = System.currentTimeMillis() + 600_000L })
    }
    if (job.state == "Saved" && job.fileName.endsWith(".zip", true)) actions.extract?.let { add(TransferMenuItem("Extract ZIP", "box", run = it)) }
    if (WatchPage.isMedia(job.url)) actions.preview?.let { add(TransferMenuItem("Preview media", "video", run = it)) }
    if (job.state == "Running") actions.stop?.let { add(TransferMenuItem("Stop download", "stop", run = it)) }
    add(TransferMenuItem("Remove from list", "trash", destructive = true, run = actions.remove))
}

internal fun copyText(value: String) {
    val selection = StringSelection(value)
    runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection) }
}

@OptIn(ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun TransferTile(job: DeskJob, ink: Ink, selected: Boolean, comfortable: Boolean, table: Boolean, actions: TransferActions, showProgress: Boolean = true, nameWidth: Int = 160) {
    var menuAnchor by remember { mutableStateOf<Offset?>(null) }
    var bounds by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val rotation by animateFloatAsState(if (job.expanded) 90f else 0f, tween(220, easing = FastOutSlowInEasing))
    val fraction = job.total?.takeIf { it > 0 }?.let { (job.bytes.toFloat() / it).coerceIn(0f, 1f) } ?: 0f
    val percent = job.total?.takeIf { it > 0 }?.let { "${(fraction * 100).toInt()}%" } ?: "—"
    val status = when (job.state) { "Running" -> ink.accent; "Saved" -> ink.success; "Failed" -> ink.danger; "Paused" -> ink.warning; else -> ink.muted }
    val glyph = when (job.fileName.substringAfterLast('.').lowercase()) { "mp4", "mkv", "webm" -> "video"; "mp3", "wav", "flac" -> "audio"; "png", "jpg", "webp" -> "image"; "zip", "rar", "7z" -> "box"; else -> "doc" }
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("row-${job.id}").onSizeChanged { bounds = it }
        .onPointerEvent(PointerEventType.Press, PointerEventPass.Initial) { event ->
            if (event.button == PointerButton.Secondary) {
                menuAnchor = event.changes.firstOrNull()?.position ?: Offset.Zero
                event.changes.forEach { it.consume() }
            }
        }) {
        val fileWidth = nameWidth.dp.coerceAtMost((maxWidth - 340.dp).coerceAtLeast(80.dp))
        Column(Modifier.fillMaxWidth().glassPanel(ink, if (table) 14.dp else 18.dp, selected)
            .background(if (hovered && !selected) ink.accent.copy(alpha = 0.035f) else Color.Transparent)
            .hoverable(interaction).combinedClickable(onClick = actions.select, onDoubleClick = actions.openFile)
            .padding(if (comfortable) 16.dp else 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Checkbox(job.picked, { job.picked = it }, Modifier.size(30.dp).semantics { contentDescription = "Select ${job.fileName}" })
                Box(Modifier.size(if (table) 34.dp else 42.dp).clip(RoundedCornerShape(12.dp)).background(ink.primaryContainer), contentAlignment = Alignment.Center) {
                    FetchGlyph(glyph, ink.onPrimaryContainer, if (table) 18.dp else 22.dp)
                }
                Column(if (table) Modifier.width(fileWidth) else Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(job.fileName, color = ink.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${siteOf(job.url)} · ${job.total?.let { bytesOf(it) } ?: "Unknown size"}", color = ink.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (table) Spacer(Modifier.weight(1f))
                Text(stateWord(job), color = status, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 118.dp).clip(RoundedCornerShape(6.dp)).background(status.copy(alpha = 0.10f)).padding(horizontal = 8.dp, vertical = 5.dp))
                QuietIcon(when (job.state) { "Running" -> "pause"; "Saved" -> "folder"; else -> "play" }, ink,
                    when (job.state) { "Running" -> "Pause download"; "Saved" -> "Open file"; else -> "Resume download" },
                    when (job.state) { "Running" -> actions.pause; "Saved" -> actions.openFile; else -> actions.start })
                Box(Modifier.graphicsLayer { rotationZ = rotation }) {
                    QuietIcon("chevron", ink, if (job.expanded) "Collapse ${job.fileName}" else "Expand ${job.fileName}") { job.expanded = !job.expanded }
                }
                QuietIcon("more", ink, "Actions for ${job.fileName}") { menuAnchor = Offset(bounds.width - with(density) { 44.dp.toPx() }, with(density) { 46.dp.toPx() }) }
            }
            if (showProgress) {
                val progress by animateFloatAsState(fraction, tween(320))
                Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(4.dp)).background(ink.line.copy(alpha = 0.45f))) {
                    Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(if (job.state == "Saved") ink.success else ink.accent))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(percent, color = ink.text, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                Text("${bytesOf(job.bytes)} downloaded", color = ink.muted, fontSize = 11.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(cardPace(job, if (job.state == "Running") job.speed else job.heldSpeed), color = if (job.state == "Running") ink.accent else ink.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 250.dp))
            }
            AnimatedVisibility(job.expanded, enter = expandVertically(tween(240)) + fadeIn(tween(180)), exit = shrinkVertically(tween(200)) + fadeOut(tween(120))) {
                TransferDetails(job, ink)
            }
        }
        menuAnchor?.let { anchor ->
            GlassTransferMenu(ink, job.fileName, anchor, transferMenu(job, actions)) { menuAnchor = null }
        }
    }
}

@Composable
internal fun TransferDetails(job: DeskJob, ink: Ink) {
    Column(Modifier.fillMaxWidth().testTag("details-${job.id}").clip(RoundedCornerShape(12.dp)).background(ink.secondaryContainer.copy(alpha = 0.36f)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            DetailMetric("TRANSFERRED", bytesOf(job.bytes), ink, Modifier.weight(1f))
            DetailMetric("CONNECTIONS", job.parts.size.takeIf { it > 0 }?.toString() ?: job.connections.toString(), ink, Modifier.weight(1f))
            DetailMetric("QUEUE", job.queue, ink, Modifier.weight(1f))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(ink.line.copy(alpha = 0.5f)))
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailMetric("SOURCE", job.url, ink)
                DetailMetric("SAVE TO", File(job.path, job.fileName).absolutePath, ink)
            }
        }
        if (job.checksum.isNotBlank()) DetailMetric(job.algorithm, job.checksum, ink)
        if (job.message.isNotBlank()) Text(job.message, color = if (job.state == "Failed") ink.danger else ink.muted, fontSize = 12.sp)
        if (job.parts.isNotEmpty()) {
            val scroll = rememberScrollState()
            Box(Modifier.fillMaxWidth().heightIn(max = 160.dp)) {
                Column(Modifier.fillMaxWidth().verticalScroll(scroll).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    job.parts.forEach { part ->
                        Row { Text("Connection ${part.index + 1}", color = ink.muted, fontSize = 11.sp, modifier = Modifier.weight(1f)); Text("${bytesOf(part.downloaded)} / ${bytesOf(part.size)}", color = ink.text, fontSize = 11.sp) }
                        SegmentTrack(part.downloaded, part.reserved, part.size, job.phase, "Connection ${part.index + 1}", Modifier.fillMaxWidth())
                    }
                }
                FetchScrollbar(scroll, ink, Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
        }
    }
}

@Composable
internal fun DetailMetric(label: String, value: String, ink: Ink, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = ink.muted, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.7.sp)
        Text(value, color = ink.text, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

internal class CursorMenuPosition(private val cursor: Offset) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = anchorBounds.left + cursor.x.roundToInt()
        val y = anchorBounds.top + cursor.y.roundToInt()
        return IntOffset(x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)), y.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)))
    }
}

@Composable
internal fun GlassTransferMenu(ink: Ink, title: String, anchor: Offset, items: List<TransferMenuItem>, onDismiss: () -> Unit) {
    val viewport = LocalWindowInfo.current.containerSize
    val menuHeight = with(LocalDensity.current) { (viewport.height.toDp() - 24.dp).coerceIn(120.dp, 460.dp) }
    Popup(popupPositionProvider = CursorMenuPosition(anchor), onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        val scroll = rememberScrollState()
        Column(Modifier.width(244.dp).heightIn(max = menuHeight).shadow(14.dp, RoundedCornerShape(18.dp))
            .background(ink.panel.copy(alpha = 0.98f), RoundedCornerShape(18.dp)).glassPanel(ink, 18.dp)
            .testTag("transfer-context-menu").padding(6.dp).verticalScroll(scroll)) {
            Text(title, color = ink.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(10.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(ink.line.copy(alpha = 0.5f)))
            items.forEach { item ->
                val interaction = remember { MutableInteractionSource() }
                val hovered by interaction.collectIsHoveredAsState()
                val tint = if (!item.enabled) ink.muted.copy(alpha = 0.45f) else if (item.destructive) ink.danger else ink.text
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (hovered && item.enabled) ink.primaryContainer.copy(alpha = 0.65f) else Color.Transparent)
                    .hoverable(interaction).clickable(enabled = item.enabled, role = Role.Button) { onDismiss(); item.run() }.padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FetchGlyph(item.glyph, tint, 16.dp)
                    Text(item.label, color = tint, fontSize = 12.sp)
                }
            }
        }
    }
}
