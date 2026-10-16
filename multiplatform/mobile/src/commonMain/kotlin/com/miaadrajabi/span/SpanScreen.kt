package com.miaadrajabi.span

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Ink = Color(0xFF12161C)
private val Copper = Color(0xFFE2A15A)
private val Paper = Color(0xFFF4EFE6)

@Composable
fun SpanScreen(directory: String, downloader: SpanDownloader) {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf("download.bin") }
    var status by remember { mutableStateOf("Paste a link. The file stays in this app's private Span folder.") }
    var fraction by remember { mutableStateOf(0f) }
    var busy by remember { mutableStateOf(false) }

    MaterialTheme(colors = darkColors(primary = Copper, background = Ink, surface = Ink)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Span", color = Copper, style = MaterialTheme.typography.h4)
            Text("Same screen for Android and iOS.", color = Paper)
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Link") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = fileName,
                onValueChange = { fileName = it },
                label = { Text("File name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Button(
                onClick = {
                    if (busy) return@Button
                    busy = true
                    fraction = 0f
                    status = "Starting"
                    scope.launch {
                        val outcome = withContext(Dispatchers.IO) {
                            downloader.download(
                                SpanRequest(url = url, fileName = fileName),
                                directory,
                            ) { progress ->
                                val total = progress.total
                                val next = if (total != null && total > 0L) {
                                    (progress.bytes.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                                } else {
                                    0f
                                }
                                scope.launch(Dispatchers.Main) { fraction = next }
                            }
                        }
                        status = when (outcome) {
                            is SpanOutcome.Saved -> "Saved ${outcome.bytes} bytes to ${outcome.path}"
                            is SpanOutcome.Failed -> outcome.message
                            SpanOutcome.Cancelled -> "Cancelled"
                        }
                        if (outcome is SpanOutcome.Saved) fraction = 1f
                        busy = false
                    }
                },
                enabled = !busy,
            ) {
                Text(if (busy) "Downloading" else "Start")
            }
            if (busy || fraction > 0f) {
                LinearProgressIndicator(progress = fraction, modifier = Modifier.fillMaxWidth(), color = Copper)
            }
            Spacer(Modifier.height(4.dp))
            Text(status, color = Paper)
        }
    }
}
