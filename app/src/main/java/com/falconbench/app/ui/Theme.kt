package com.falconbench.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Terminal-adjacent bench palette — not SaaS chatbot pastels
private val Ink = Color(0xFF0E1114)
private val Panel = Color(0xFF161A1F)
private val Line = Color(0xFF2A3038)
private val Accent = Color(0xFF7C9CFF)
private val AccentDim = Color(0xFF4A6AD8)
private val TextPri = Color(0xFFE8ECF1)
private val TextSec = Color(0xFF9AA3AD)
private val Warn = Color(0xFFFFB454)

private val Dark = darkColorScheme(
    primary = Accent,
    onPrimary = Ink,
    secondary = AccentDim,
    background = Ink,
    surface = Panel,
    onBackground = TextPri,
    onSurface = TextPri,
    onSurfaceVariant = TextSec,
    outline = Line,
    error = Warn
)

private val Light = lightColorScheme(
    primary = AccentDim,
    background = Color(0xFFF4F5F7),
    surface = Color.White,
    onBackground = Color(0xFF121418),
    onSurface = Color(0xFF121418)
)

@Composable
fun FalconBenchTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        content = content
    )
}
