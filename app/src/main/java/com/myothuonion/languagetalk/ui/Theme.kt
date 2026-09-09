package com.myothuonion.languagetalk.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF0B0912)
val SurfaceDark = Color(0xFF15131F)
val SurfaceHigh = Color(0xFF211E2E)
val Violet = Color(0xFF8B5CF6)
val VioletLight = Color(0xFFBFA5FF)
val Mint = Color(0xFF22D3A7)
val Coral = Color(0xFFFF7E79)
val TextPrimary = Color(0xFFF7F4FF)
val TextMuted = Color(0xFFA9A2B8)

private val DarkColors = darkColorScheme(
    primary = VioletLight,
    onPrimary = Color(0xFF24104A),
    primaryContainer = Color(0xFF382067),
    onPrimaryContainer = Color(0xFFE7D9FF),
    secondary = Mint,
    onSecondary = Color(0xFF00382D),
    background = Ink,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = TextMuted,
    error = Coral
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF6941C6),
    secondary = Color(0xFF00866C),
    background = Color(0xFFF8F6FC),
    surface = Color.White,
    surfaceVariant = Color(0xFFEEEAF5),
    onSurfaceVariant = Color(0xFF625B70)
)

@Composable
fun LanguageTalkTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
