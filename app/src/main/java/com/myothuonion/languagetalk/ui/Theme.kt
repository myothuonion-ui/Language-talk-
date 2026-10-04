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
val Violet = Color(0xFF2A624A)
val VioletLight = Color(0xFFA7D6B9)
val Mint = Color(0xFF22D3A7)
val Coral = Color(0xFFFF7E79)
val TextPrimary = Color(0xFFF7F4FF)
val TextMuted = Color(0xFFA9A2B8)

private val DarkColors = darkColorScheme(
    primary = VioletLight,
    onPrimary = Color(0xFF173B29),
    primaryContainer = Color(0xFF294437),
    onPrimaryContainer = Color(0xFFDEEEDD),
    secondary = Mint,
    onSecondary = Color(0xFF00382D),
    background = Color(0xFF171E19),
    onBackground = TextPrimary,
    surface = Color(0xFF202923),
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFF303C33),
    onSurfaceVariant = Color(0xFFB3C1B7),
    error = Coral
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2A624A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4EDE2),
    onPrimaryContainer = Color(0xFF203C2C),
    secondary = Color(0xFF496451),
    background = Color(0xFFF8FAF6),
    onBackground = Color(0xFF20372B),
    surface = Color(0xFFFFFEFA),
    onSurface = Color(0xFF20372B),
    surfaceVariant = Color(0xFFECF0E8),
    onSurfaceVariant = Color(0xFF58665D),
    outlineVariant = Color(0xFFDCE4DB)
)

@Composable
fun LanguageTalkTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
