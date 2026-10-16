package com.miaadrajabi.span

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val FetchCopper = Color(0xFFE2A15A)
val FetchEmber = Color(0xFFF3D2A4)
val FetchInk = Color(0xFF12161C)
val FetchRose = Color(0xFFE07A6A)
val FetchOk = Color(0xFF7DCEA0)

@Composable
fun FetchGlyph(name: String, tint: Color, box: Dp = 16.dp) {
    Canvas(Modifier.size(box)) {
        val w = size.width
        val h = size.height
        val line = Stroke(width = 1.8f, cap = StrokeCap.Round)
        when (name) {
            "add" -> {
                drawLine(tint, Offset(w * 0.5f, h * 0.18f), Offset(w * 0.5f, h * 0.82f), strokeWidth = 1.8f, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.18f, h * 0.5f), Offset(w * 0.82f, h * 0.5f), strokeWidth = 1.8f, cap = StrokeCap.Round)
            }
            "close" -> {
                drawLine(tint, Offset(w * 0.28f, h * 0.28f), Offset(w * 0.72f, h * 0.72f), strokeWidth = 1.8f, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.72f, h * 0.28f), Offset(w * 0.28f, h * 0.72f), strokeWidth = 1.8f, cap = StrokeCap.Round)
            }
            "grid" -> {
                val s = w * 0.22f
                listOf(0.18f to 0.18f, 0.56f to 0.18f, 0.18f to 0.56f, 0.56f to 0.56f).forEach { (x, y) ->
                    drawRoundRect(tint, Offset(w * x, h * y), Size(s, s), CornerRadius(2f, 2f), style = line)
                }
            }
            "down" -> {
                drawLine(tint, Offset(w * 0.5f, h * 0.16f), Offset(w * 0.5f, h * 0.62f), strokeWidth = 1.8f, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.28f, h * 0.48f), Offset(w * 0.5f, h * 0.70f), strokeWidth = 1.8f, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.72f, h * 0.48f), Offset(w * 0.5f, h * 0.70f), strokeWidth = 1.8f, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.24f, h * 0.82f), Offset(w * 0.76f, h * 0.82f), strokeWidth = 1.8f, cap = StrokeCap.Round)
            }
            "bars" -> {
                drawLine(tint, Offset(w * 0.22f, h * 0.78f), Offset(w * 0.22f, h * 0.42f), strokeWidth = 1.8f, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.5f, h * 0.78f), Offset(w * 0.5f, h * 0.22f), strokeWidth = 1.8f, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.78f, h * 0.78f), Offset(w * 0.78f, h * 0.50f), strokeWidth = 1.8f, cap = StrokeCap.Round)
            }
            "doc" -> {
                drawRoundRect(tint, Offset(w * 0.24f, h * 0.14f), Size(w * 0.46f, h * 0.72f), CornerRadius(2f, 2f), style = line)
                drawLine(tint, Offset(w * 0.34f, h * 0.36f), Offset(w * 0.60f, h * 0.36f), strokeWidth = 1.6f, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.34f, h * 0.50f), Offset(w * 0.60f, h * 0.50f), strokeWidth = 1.6f, cap = StrokeCap.Round)
            }
            "pause" -> {
                drawRoundRect(tint, Offset(w * 0.22f, h * 0.18f), Size(w * 0.16f, h * 0.64f), CornerRadius(2f, 2f))
                drawRoundRect(tint, Offset(w * 0.62f, h * 0.18f), Size(w * 0.16f, h * 0.64f), CornerRadius(2f, 2f))
            }
            "stop" -> drawRoundRect(tint, Offset(w * 0.22f, h * 0.22f), Size(w * 0.56f, h * 0.56f), CornerRadius(3f, 3f))
            "play" -> {
                val path = Path().apply {
                    moveTo(w * 0.28f, h * 0.16f)
                    lineTo(w * 0.82f, h * 0.5f)
                    lineTo(w * 0.28f, h * 0.84f)
                    close()
                }
                drawPath(path, tint)
            }
            "folder" -> {
                drawPath(Path().apply {
                    moveTo(w * 0.14f, h * 0.78f); lineTo(w * 0.14f, h * 0.24f)
                    lineTo(w * 0.40f, h * 0.24f); lineTo(w * 0.50f, h * 0.36f)
                    lineTo(w * 0.86f, h * 0.36f); lineTo(w * 0.86f, h * 0.78f); close()
                }, tint, style = line)
            }
            "copy" -> {
                drawRoundRect(tint, Offset(w * 0.32f, h * 0.30f), Size(w * 0.46f, h * 0.52f), CornerRadius(2f, 2f), style = line)
                drawPath(Path().apply { moveTo(w * 0.56f, h * 0.16f); lineTo(w * 0.18f, h * 0.16f); lineTo(w * 0.18f, h * 0.64f) }, tint, style = line)
            }
            "bolt" -> drawPath(Path().apply {
                moveTo(w * 0.58f, h * 0.10f); lineTo(w * 0.22f, h * 0.54f); lineTo(w * 0.48f, h * 0.54f)
                lineTo(w * 0.40f, h * 0.90f); lineTo(w * 0.80f, h * 0.42f); lineTo(w * 0.54f, h * 0.42f); close()
            }, tint, style = line)
            "gear" -> {
                drawCircle(tint, radius = w * 0.25f, style = line)
                drawCircle(tint, radius = w * 0.08f, style = line)
                repeat(8) { index ->
                    val angle = index * Math.PI / 4
                    val x = kotlin.math.cos(angle).toFloat()
                    val y = kotlin.math.sin(angle).toFloat()
                    drawLine(tint, Offset(w * (0.5f + x * 0.25f), h * (0.5f + y * 0.25f)),
                        Offset(w * (0.5f + x * 0.36f), h * (0.5f + y * 0.36f)), strokeWidth = w * 0.10f, cap = StrokeCap.Round)
                }
            }
            "more" -> repeat(3) { index ->
                drawCircle(tint, radius = w * 0.07f, center = Offset(w * (0.25f + index * 0.25f), h * 0.5f))
            }
            "audio" -> {
                drawCircle(tint, radius = w * 0.16f, center = Offset(w * 0.38f, h * 0.62f))
                drawLine(tint, Offset(w * 0.54f, h * 0.62f), Offset(w * 0.54f, h * 0.22f), strokeWidth = 2f)
                drawLine(tint, Offset(w * 0.54f, h * 0.22f), Offset(w * 0.78f, h * 0.32f), strokeWidth = 2f)
            }
            "video" -> {
                drawRoundRect(tint, Offset(w * 0.12f, h * 0.22f), Size(w * 0.76f, h * 0.56f), CornerRadius(3f, 3f), style = line)
                drawPath(Path().apply { moveTo(w * 0.42f, h * 0.36f); lineTo(w * 0.65f, h * 0.5f); lineTo(w * 0.42f, h * 0.64f); close() }, tint)
            }
            "both" -> {
                drawCircle(tint, radius = w * 0.12f, center = Offset(w * 0.32f, h * 0.62f))
                drawRoundRect(tint, Offset(w * 0.48f, h * 0.28f), Size(w * 0.36f, h * 0.28f), CornerRadius(2f, 2f), style = Stroke(1.8f))
            }
            "check" -> {
                drawLine(tint, Offset(w * 0.18f, h * 0.52f), Offset(w * 0.40f, h * 0.74f), strokeWidth = 1.8f, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.40f, h * 0.74f), Offset(w * 0.82f, h * 0.28f), strokeWidth = 1.8f, cap = StrokeCap.Round)
            }
            "chevron" -> {
                drawLine(tint, Offset(w * 0.34f, h * 0.24f), Offset(w * 0.66f, h * 0.5f), strokeWidth = 1.8f, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.66f, h * 0.5f), Offset(w * 0.34f, h * 0.76f), strokeWidth = 1.8f, cap = StrokeCap.Round)
            }
            "pencil" -> {
                drawLine(tint, Offset(w * 0.22f, h * 0.78f), Offset(w * 0.72f, h * 0.22f), strokeWidth = 2.2f)
                drawLine(tint, Offset(w * 0.62f, h * 0.22f), Offset(w * 0.78f, h * 0.38f), strokeWidth = 2.2f)
            }
            "trash" -> {
                drawLine(tint, Offset(w * 0.28f, h * 0.28f), Offset(w * 0.72f, h * 0.28f), strokeWidth = 2f)
                drawRoundRect(tint, Offset(w * 0.32f, h * 0.34f), Size(w * 0.36f, h * 0.46f), CornerRadius(2f, 2f), style = Stroke(2f))
            }
            "info" -> {
                drawCircle(tint, radius = w * 0.34f, style = Stroke(2f))
                drawCircle(tint, radius = w * 0.05f, center = Offset(w * 0.5f, h * 0.34f))
                drawLine(tint, Offset(w * 0.5f, h * 0.48f), Offset(w * 0.5f, h * 0.72f), strokeWidth = 2f)
            }
            "search" -> {
                drawCircle(tint, radius = w * 0.22f, center = Offset(w * 0.42f, h * 0.42f), style = line)
                drawLine(tint, Offset(w * 0.58f, h * 0.58f), Offset(w * 0.80f, h * 0.80f), strokeWidth = 1.8f, cap = StrokeCap.Round)
            }
            "clock" -> {
                drawCircle(tint, radius = w * 0.30f, style = line)
                drawLine(tint, Offset(w * 0.5f, h * 0.32f), Offset(w * 0.5f, h * 0.52f), strokeWidth = 1.6f, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.5f, h * 0.52f), Offset(w * 0.66f, h * 0.62f), strokeWidth = 1.6f, cap = StrokeCap.Round)
            }
            "image" -> drawRoundRect(tint, Offset(w * 0.16f, h * 0.22f), Size(w * 0.68f, h * 0.56f), CornerRadius(3f, 3f), style = line)
            "box" -> drawRoundRect(tint, Offset(w * 0.2f, h * 0.2f), Size(w * 0.6f, h * 0.6f), CornerRadius(3f, 3f), style = line)
            "all" -> drawCircle(tint, radius = w * 0.22f, center = Offset(w * 0.5f, h * 0.5f))
            else -> drawCircle(tint, radius = w * 0.16f, center = Offset(w * 0.5f, h * 0.5f))
        }
    }
}

fun glyphFor(label: String): String = when {
    label == "All" || label == "All downloads" || label.startsWith("All queues") -> "grid"
    label == "Active" -> "play"
    label == "Queued" || label == "Unfinished" -> "clock"
    label == "Completed" || label == "Finished" -> "check"
    label == "Needs attention" -> "info"
    label.startsWith("Image") -> "image"
    label.startsWith("Music") -> "audio"
    label.startsWith("Video") -> "video"
    label.startsWith("Compressed") -> "box"
    label.startsWith("Document") -> "doc"
    label.startsWith("New") -> "add"
    label == "Settings" -> "gear"
    label.contains("·") -> "clock"
    else -> "doc"
}
