package com.miaadrajabi.span

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File

internal data class ProbeInput(val url: String, val folder: String, val user: String, val password: String, val token: String) {
    val validUrl: Boolean get() = url.toHttpUrlOrNull() != null
}

@Composable
internal fun AddDialog(
    ink: Ink, queues: List<String>, settings: DeskSettings, initialUrl: String = "",
    onClose: (String?, String, String, String, Int, Int, String, String, String) -> Unit,
) {
    Scrim {
        BoxWithConstraints {
            val bodyHeight = (maxHeight - (if (maxWidth < 480.dp) 300.dp else 225.dp)).coerceIn(70.dp, 460.dp)
            Column(Modifier.widthIn(max = 680.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(ink.panel).glassPanel(ink, 24.dp).padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(ink.primaryContainer), contentAlignment = Alignment.Center) {
                        FetchGlyph("down", ink.onPrimaryContainer, 26.dp)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Add a download", color = ink.text, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                        Text("A link. A destination. Ready to go.", color = ink.muted, fontSize = 12.sp)
                    }
                    QuietIcon("close", ink, "Close add download") { onClose(null, "", "", "", 1, 0, "", "SHA256", "") }
                }
                LinkOfferForm(ink, queues, settings, initialUrl, bodyHeight, onClose)
            }
        }
    }
}

@Composable
internal fun LinkOfferForm(
    ink: Ink, queues: List<String>, settings: DeskSettings, initialUrl: String,
    fieldHeight: Dp = 280.dp,
    onClose: (String?, String, String, String, Int, Int, String, String, String) -> Unit,
    inspect: suspend (ProbeInput) -> ProbeReport = { input -> withContext(Dispatchers.IO) { LinkProbe.inspect(input.url, input.folder, input.user, input.password, input.token) } },
) {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(initialUrl) }
    var name by remember { mutableStateOf("") }
    var folder by remember { mutableStateOf(settings.folder) }
    var queue by remember { mutableStateOf(queues.firstOrNull() ?: "Main") }
    var threads by remember { mutableStateOf(settings.threads.toString()) }
    var speed by remember { mutableStateOf(settings.speedLimitKb.toString()) }
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var checksum by remember { mutableStateOf("") }
    var options by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<ProbeReport?>(null) }
    var checkedInput by remember { mutableStateOf<ProbeInput?>(null) }
    var probeJob by remember { mutableStateOf<Job?>(null) }
    var queueMenu by remember { mutableStateOf(false) }
    val input = ProbeInput(url.trim(), folder, user, password, token)
    val currentInput by rememberUpdatedState(input)
    val latestInspect by rememberUpdatedState(inspect)
    val scroll = rememberScrollState()
    val turn by animateFloatAsState(if (options) 90f else 0f, tween(200))
    val ready = report?.let { it.reachable && it.fits } == true && checkedInput == input && !checking &&
        threads.toIntOrNull() in 1..32 && (speed.toIntOrNull() ?: -1) >= 0 && folder.isNotBlank()

    fun check() {
        if (checking || !input.validUrl) return
        val snapshot = input
        checking = true
        probeJob = scope.launch {
            try {
                val next = latestInspect(snapshot)
                if (currentInput == snapshot) {
                    report = next
                    checkedInput = snapshot
                    if (name.isBlank() && next.fileName.isNotBlank()) name = next.fileName
                    if (next.authKind.isNotBlank()) options = true
                }
            } finally {
                if (currentInput == snapshot) checking = false
            }
        }
    }
    LaunchedEffect(input) {
        if (checkedInput != input) report = null
        if (checking) { probeJob?.cancel(); checking = false }
    }
    LaunchedEffect(Unit) { if (initialUrl.toHttpUrlOrNull() != null) check() }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth().heightIn(max = fieldHeight)) {
            Column(Modifier.fillMaxWidth().verticalScroll(scroll).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(ink.primaryContainer.copy(alpha = 0.30f)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("DOWNLOAD LINK", color = ink.accent, fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("Paste link", color = ink.accent, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(7.dp)).clickable(role = Role.Button) { readClipboard()?.trim()?.let { url = it } }.padding(6.dp))
                    }
                    OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth().testTag("download-url"), singleLine = true,
                        placeholder = { Text("https://example.com/file.zip", fontSize = 13.sp) },
                        isError = url.isNotBlank() && !input.validUrl, shape = RoundedCornerShape(12.dp))
                    Text(if (url.isNotBlank() && !input.validUrl) "Enter a complete HTTP or HTTPS link." else "Check the link to see its name, size and availability.", color = if (url.isNotBlank() && !input.validUrl) ink.danger else ink.muted, fontSize = 11.sp)
                }
                Field("File name (automatic when checked)", name) { name = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { Field("Save to", folder) { folder = it } }
                    QuietIcon("folder", ink, "Choose destination folder") {
                        val chooser = javax.swing.JFileChooser(File(folder).let { if (it.isDirectory) it else it.parentFile })
                        chooser.fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
                        chooser.dialogTitle = "Choose a download destination"
                        if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) folder = chooser.selectedFile.absolutePath
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        Row(Modifier.clip(RoundedCornerShape(10.dp)).background(ink.secondaryContainer).clickable { queueMenu = true }.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FetchGlyph("clock", ink.onSecondaryContainer, 16.dp)
                            Text("Queue · $queue", color = ink.onSecondaryContainer, fontSize = 12.sp)
                        }
                        DropdownMenu(queueMenu, { queueMenu = false }) {
                            queues.ifEmpty { listOf("Main") }.forEach { entry -> DropdownMenuItem(onClick = { queue = entry; queueMenu = false }) { Text(entry) } }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Row(Modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) { options = !options }.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.graphicsLayer { rotationZ = turn }) { FetchGlyph("chevron", ink.accent, 12.dp) }
                        Text("Advanced", color = ink.accent, fontSize = 12.sp)
                    }
                }
                AnimatedVisibility(options) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(ink.raised.copy(alpha = 0.5f)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.weight(1f)) { Field("Connections · 1–32", threads) { threads = it } }
                            Box(Modifier.weight(1f)) { Field("Limit · KB/s, 0 = unlimited", speed) { speed = it } }
                        }
                        Field("SHA-256 checksum (optional)", checksum) { checksum = it }
                        Text("AUTHENTICATION", color = ink.muted, fontSize = 10.sp, letterSpacing = 0.8.sp)
                        Field("Username", user) { user = it }
                        OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation())
                        OutlinedTextField(token, { token = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Authorization token") }, visualTransformation = PasswordVisualTransformation())
                    }
                }
                report?.takeIf { checkedInput == input }?.let { result ->
                    val tone = if (result.reachable && result.fits) ink.success else ink.danger
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(tone.copy(alpha = 0.08f)).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FetchGlyph(if (result.reachable && result.fits) "check" else "info", tone, 20.dp)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(result.message, color = tone, fontSize = 12.sp)
                            Text("${result.bytes?.let { bytesOf(it) } ?: "Unknown size"} · ${bytesOf(result.freeBytes)} available", color = ink.muted, fontSize = 11.sp)
                        }
                    }
                }
            }
            FetchScrollbar(scroll, ink, Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(ink.line.copy(alpha = 0.5f)))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { check() }, enabled = input.validUrl && !checking, colors = ButtonDefaults.buttonColors(backgroundColor = ink.secondaryContainer, contentColor = ink.onSecondaryContainer), elevation = null) {
                Text(if (checking) "Checking…" else "Check link", fontSize = 12.sp)
            }
            Button(onClick = {
                if (ready) onClose(input.url, name, folder, queue, threads.toInt(), speed.toInt(), checksum.trim(), "SHA256", LinkProbe.authorization(user, password, token))
            }, enabled = ready, modifier = Modifier.testTag("start-download"), elevation = null) {
                FetchGlyph("down", if (ready) ink.onAccent else ink.muted, 16.dp)
                Spacer(Modifier.width(8.dp))
                Text("Start download", fontSize = 12.sp)
            }
            TextButton(onClick = { onClose(null, "", "", "", 1, 0, "", "SHA256", "") }) { Text("Cancel", color = ink.muted, fontSize = 12.sp) }
        }
    }
}
