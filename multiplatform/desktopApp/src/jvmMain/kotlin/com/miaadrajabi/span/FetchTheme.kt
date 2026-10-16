package com.miaadrajabi.span

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Shapes
import androidx.compose.material.Text
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class Ink(
    val night: Color,
    val panel: Color,
    val raised: Color,
    val line: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val glow: Color,
    val isLight: Boolean = false,
    val secondary: Color = accent,
    val tertiary: Color = accent,
    val primaryContainer: Color = raised,
    val onPrimaryContainer: Color = text,
    val secondaryContainer: Color = raised,
    val onSecondaryContainer: Color = text,
    val tones: List<Color> = emptyList(),
)

internal data class DeskTheme(val key: String, val name: String, val description: String)

internal val deskThemes = listOf(
    DeskTheme("light", "Cobalt", "Material · blue"),
    DeskTheme("dark", "Tidal", "Material · ocean"),
    DeskTheme("black", "Amber", "Material · gold"),
    DeskTheme("forest", "Forest", "Material · jade"),
    DeskTheme("orchid", "Orchid", "Material · violet"),
    DeskTheme("rose", "Rose", "Material · berry"),
)

// Material 3 color roles mapped to our desktop components. The palettes are generated in HCT.
internal fun inkOf(theme: String): Ink {
    val palette = materialPalettes[theme] ?: materialPalettes.getValue("light")
    val light = theme == "light" || theme == "rose" || theme !in materialPalettes
    val foregroundTone = if (light) 40 else 80
    return Ink(
        night = palette.neutral.getValue(if (light) 98 else 6),
        panel = palette.neutral.getValue(if (light) 96 else 12),
        raised = palette.neutral.getValue(if (light) 94 else 20),
        line = palette.variant.getValue(if (light) 80 else 30),
        text = palette.neutral.getValue(if (light) 10 else 90),
        muted = palette.variant.getValue(if (light) 30 else 80),
        accent = palette.primary.getValue(foregroundTone),
        onAccent = palette.primary.getValue(if (light) 100 else 20),
        success = palette.success.getValue(foregroundTone),
        warning = palette.warning.getValue(foregroundTone),
        danger = palette.error.getValue(foregroundTone),
        glow = palette.primary.getValue(if (light) 80 else 40),
        isLight = light,
        secondary = palette.secondary.getValue(foregroundTone),
        tertiary = palette.tertiary.getValue(foregroundTone),
        primaryContainer = palette.primary.getValue(if (light) 90 else 30),
        onPrimaryContainer = palette.primary.getValue(if (light) 10 else 90),
        secondaryContainer = palette.secondary.getValue(if (light) 90 else 30),
        onSecondaryContainer = palette.secondary.getValue(if (light) 10 else 90),
        tones = listOf(20, 40, 60, 80, 90).map { palette.primary.getValue(it) },
    )
}

internal val LocalFetchInk = staticCompositionLocalOf { inkOf("light") }

@Composable
internal fun FetchTheme(ink: Ink, content: @Composable () -> Unit) {
    val colors = if (ink.isLight) lightColors() else darkColors()
    CompositionLocalProvider(LocalFetchInk provides ink, androidx.compose.material.LocalContentColor provides ink.text) {
        MaterialTheme(
            colors = colors.copy(
                primary = ink.accent, primaryVariant = ink.primaryContainer, secondary = ink.secondary,
                secondaryVariant = ink.secondaryContainer, background = ink.night, surface = ink.panel,
                onBackground = ink.text, onSurface = ink.text, onPrimary = ink.onAccent,
                onSecondary = if (ink.isLight) Color.White else ink.night, error = ink.danger,
            ),
            shapes = Shapes(RoundedCornerShape(8.dp), RoundedCornerShape(12.dp), RoundedCornerShape(18.dp)),
            content = content,
        )
    }
}

/** A frosted surface over the soft, static backdrop. Foreground text is never blurred. */
internal fun Modifier.glassPanel(ink: Ink, radius: Dp = 16.dp, selected: Boolean = false): Modifier {
    val shape = RoundedCornerShape(radius)
    return clip(shape)
        .background(Brush.linearGradient(listOf(
            ink.panel.copy(alpha = if (ink.isLight) 0.88f else 0.94f),
            ink.panel.copy(alpha = if (ink.isLight) 0.64f else 0.76f),
        )))
        .then(if (selected) Modifier.background(ink.accent.copy(alpha = 0.10f)) else Modifier)
        .border(1.dp, Brush.linearGradient(listOf(
            if (selected) ink.accent.copy(alpha = 0.85f) else if (ink.isLight) Color.White.copy(alpha = 0.95f) else ink.line,
            if (selected) ink.accent.copy(alpha = 0.35f) else ink.line.copy(alpha = 0.45f),
        )), shape)
}

@Composable
internal fun FrostedBackdrop(ink: Ink) {
    Canvas(Modifier.fillMaxSize().background(ink.night)) {
        // Broad radial gradients form an already-diffuse backdrop, without a per-frame blur pass.
        drawRect(Brush.radialGradient(
            listOf(ink.glow.copy(alpha = if (ink.isLight) 0.28f else 0.22f), Color.Transparent),
            center = Offset(size.width * 0.12f, size.height * 0.08f), radius = size.width * 0.75f,
        ))
        drawRect(Brush.radialGradient(
            listOf(ink.accent.copy(alpha = 0.10f), Color.Transparent),
            center = Offset(size.width * 0.95f, size.height * 0.95f), radius = size.width * 0.65f,
        ))
    }
}

@Composable
internal fun ThemePicker(selectedKey: String, onSelect: (String) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 540.dp) 3 else if (maxWidth >= 340.dp) 2 else 1
        val cardWidth = (maxWidth - 10.dp * (columns - 1)) / columns
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            deskThemes.forEach { theme ->
                val ink = inkOf(theme.key)
                val chosen = theme.key == selectedKey
                Column(
                    Modifier.width(cardWidth).semantics { contentDescription = "${theme.name} theme"; selected = chosen }
                        .clip(RoundedCornerShape(12.dp)).background(ink.night)
                        .border(if (chosen) 2.dp else 1.dp, if (chosen) ink.accent else ink.line, RoundedCornerShape(12.dp))
                        .clickable(role = Role.RadioButton) { onSelect(theme.key) }.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.width(24.dp).height(40.dp).glassPanel(ink, 5.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.fillMaxWidth().height(18.dp).glassPanel(ink, 4.dp))
                            Box(Modifier.fillMaxWidth(0.72f).height(18.dp).glassPanel(ink, 4.dp))
                        }
                        Box(Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).background(ink.accent), contentAlignment = Alignment.Center) {
                            if (chosen) FetchGlyph("check", ink.onAccent, 14.dp)
                        }
                    }
                    Text(theme.name, color = ink.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(theme.description, color = ink.muted, fontSize = 11.sp)
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))) {
                        ink.tones.forEach { tone -> Box(Modifier.weight(1f).height(7.dp).background(tone)) }
                    }
                }
            }
        }
    }
}
