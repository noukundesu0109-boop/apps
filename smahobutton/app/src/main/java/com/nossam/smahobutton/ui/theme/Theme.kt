package com.nossam.smahobutton.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Coral = Color(0xFFC2412D)
private val CoralDark = Color(0xFF7A2E22)
private val Paper = Color(0xFFF6F3EE)
private val Ink = Color(0xFF1C1B19)

private val LightColors = lightColorScheme(
    primary = Coral,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD3),
    onPrimaryContainer = Color(0xFF3D0803),
    secondary = Color(0xFF4E6070),
    background = Paper,
    surface = Color(0xFFFFFCF8),
    surfaceVariant = Color(0xFFE7E1D8),
    onBackground = Ink,
    onSurface = Ink,
    onSurfaceVariant = Color(0xFF564F48),
    outline = Color(0xFF8A8178),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB4A6),
    onPrimary = Color(0xFF5F150B),
    primaryContainer = CoralDark,
    onPrimaryContainer = Color(0xFFFFDAD3),
    background = Color(0xFF141311),
    surface = Color(0xFF1E1C1A),
    surfaceVariant = Color(0xFF3A342F),
    onBackground = Color(0xFFF4EFE8),
    onSurface = Color(0xFFF4EFE8),
    onSurfaceVariant = Color(0xFFD4CBC2),
    outline = Color(0xFF9A9188),
)

@Composable
fun SmahoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}

fun argbColor(value: Long): Color {
    val color = value.toInt()
    return Color(
        alpha = ((color ushr 24) and 0xFF) / 255f,
        red = ((color ushr 16) and 0xFF) / 255f,
        green = ((color ushr 8) and 0xFF) / 255f,
        blue = (color and 0xFF) / 255f,
    )
}
