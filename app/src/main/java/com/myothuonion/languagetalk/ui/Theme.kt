package com.myothuonion.languagetalk.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

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
    primary = Color(0xFF9CD2CC), onPrimary = Color(0xFF073F3C),
    primaryContainer = Color(0xFF234C4A), onPrimaryContainer = Color(0xFFD0EEEA),
    secondary = Color(0xFFBDCCC9), onSecondary = Color(0xFF253B38),
    secondaryContainer = Color(0xFF3D5350), onSecondaryContainer = Color(0xFFD9E7E3),
    tertiary = Color(0xFFE8C58D), onTertiary = Color(0xFF49371A),
    tertiaryContainer = Color(0xFF5F4624), onTertiaryContainer = Color(0xFFFFE1B2),
    background = Color(0xFF161E24), onBackground = Color(0xFFE8EEF2),
    surface = Color(0xFF1D282F), onSurface = Color(0xFFE8EEF2),
    surfaceVariant = Color(0xFF39484F), onSurfaceVariant = Color(0xFFBCC9CE),
    surfaceDim = Color(0xFF161E24), surfaceBright = Color(0xFF3A454B),
    surfaceContainerLowest = Color(0xFF10181D), surfaceContainerLow = Color(0xFF1A252B),
    surfaceContainer = Color(0xFF233038), surfaceContainerHigh = Color(0xFF2C3941),
    surfaceContainerHighest = Color(0xFF35424A), outline = Color(0xFF8B9BA2),
    outlineVariant = Color(0xFF425158), inverseSurface = Color(0xFFE1EBEB),
    inverseOnSurface = Color(0xFF243238), inversePrimary = Color(0xFF285D58),
    surfaceTint = Color(0xFF9CD2CC), error = Color(0xFFFFB4AB)
)
private val LightColors = lightColorScheme(
    primary = Color(0xFF245A56), onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEEE9), onPrimaryContainer = Color(0xFF123C38),
    secondary = Color(0xFF506560), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3EBE5), onSecondaryContainer = Color(0xFF2D433C),
    tertiary = Color(0xFF805E2D), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF9E5C1), onTertiaryContainer = Color(0xFF4C3716),
    background = Color(0xFFF7F8F7), onBackground = Color(0xFF172B36),
    surface = Color(0xFFFFFEFB), onSurface = Color(0xFF172B36),
    surfaceVariant = Color(0xFFE5ECEA), onSurfaceVariant = Color(0xFF56666D),
    surfaceDim = Color(0xFFD9E3E0), surfaceBright = Color(0xFFFFFEFB),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF1F5F2),
    surfaceContainer = Color(0xFFECF1EF), surfaceContainerHigh = Color(0xFFE6EDE9),
    surfaceContainerHighest = Color(0xFFE0E7E3), outline = Color(0xFF78888A),
    outlineVariant = Color(0xFFD8E2DD), inverseSurface = Color(0xFF293A40),
    inverseOnSurface = Color(0xFFF1F6F2), inversePrimary = Color(0xFF9CD2CC),
    surfaceTint = Color(0xFF245A56)
)

@Composable
fun LanguageTalkTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    val activity = LocalActivity.current
    val view = LocalView.current
    if (!view.isInEditMode && activity != null) SideEffect {
        val window = activity.window
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = colors.background.toArgb()
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
    }
    MaterialTheme(colorScheme = colors, content = content)
}
