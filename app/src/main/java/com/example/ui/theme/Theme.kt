package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val ObsidianGreenBg = Color(0xFF070709)
val RedMagicOrange = Color(0xFFFF551A)
val RedMagicCrimson = Color(0xFFFF2A3C)

// Legacy color aliases so all existing UI components compile cleanly
val EmeraldGlow = Color(0xFFFF551A)
val EmeraldDeepGlow = Color(0xFFCC3D0E)
val EmeraldWireframe = Color(0xFFFF551A)
val HeavyPurpleGlow = Color(0xFFFF2A3C)
val MagicTextPrimary = Color(0xFFF5F5F7)
val MagicTextSecondary = Color(0xFF9494A3)
val DarkCardSurface = Color(0xFF101015)

private val RedMagicDarkColors = darkColorScheme(
    primary = RedMagicOrange,
    secondary = RedMagicCrimson,
    background = ObsidianGreenBg,
    surface = DarkCardSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = MagicTextPrimary,
    onSurface = MagicTextPrimary
)

@Composable
fun GamingTurboTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RedMagicDarkColors,
        content = content
    )
}
