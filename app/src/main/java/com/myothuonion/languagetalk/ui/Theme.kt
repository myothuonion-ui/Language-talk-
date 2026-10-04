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
    secondaryContainer = Color(0xFF304C3C),
    onSecondaryContainer = Color(0xFFDEEEDD),
    tertiary = Color(0xFFBDD1AD),
    onTertiary = Color(0xFF263D21),
    tertiaryContainer = Color(0xFF3C5433),
    onTertiaryContainer = Color(0xFFDCEED0),
    background = Color(0xFF171E19),
    onBackground = TextPrimary,
    surface = Color(0xFF202923),
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFF303C33),
    onSurfaceVariant = Color(0xFFB3C1B7),
    surfaceDim = Color(0xFF171E19),
    surfaceBright = Color(0xFF354138),
    surfaceContainerLowest = Color(0xFF111812),
    surfaceContainerLow = Color(0xFF1C261F),
    surfaceContainer = Color(0xFF263228),
    surfaceContainerHigh = Color(0xFF2E3A30),
    surfaceContainerHighest = Color(0xFF344135),
    outline = Color(0xFF89998D),
    outlineVariant = Color(0xFF455348),
    inverseSurface = Color(0xFFE8F0E5),
    inverseOnSurface = Color(0xFF243329),
    inversePrimary = Color(0xFF2A624A),
    surfaceTint = VioletLight,
    error = Coral
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2A624A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4EDE2),
    onPrimaryContainer = Color(0xFF203C2C),
    secondary = Color(0xFF496451),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE9D9),
    onSecondaryContainer = Color(0xFF213C29),
    tertiary = Color(0xFF4C6840),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD8E8CA),
    onTertiaryContainer = Color(0xFF263D21),
    background = Color(0xFFF8FAF6),
    onBackground = Color(0xFF20372B),
    surface = Color(0xFFFFFEFA),
    onSurface = Color(0xFF20372B),
    surfaceVariant = Color(0xFFECF0E8),
    onSurfaceVariant = Color(0xFF58665D),
    surfaceDim = Color(0xFFD8E1D5),
    surfaceBright = Color(0xFFFCFDF7),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF2F6EE),
    surfaceContainer = Color(0xFFEDF2E8),
    surfaceContainerHigh = Color(0xFFE7EDDF),
    surfaceContainerHighest = Color(0xFFE0E7DC),
    outline = Color(0xFF758174),
    outlineVariant = Color(0xFFDCE4DB),
    inverseSurface = Color(0xFF2A382A),
    inverseOnSurface = Color(0xFFEDF4E8),
    inversePrimary = VioletLight,
    surfaceTint = Violet
)

@Composable
fun LanguageTalkTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
