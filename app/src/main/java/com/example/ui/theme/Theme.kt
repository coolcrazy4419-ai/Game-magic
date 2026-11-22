package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val ObsidianGreenBg = Color(0xFF070709)
val RedMagicOrange = Color(0xFFFF551A)
val RedMagicCrimson = Color(0xFFFF2A3C)

private val RedMagicDarkColors = darkColorScheme(
    primary = RedMagicOrange,
    secondary = RedMagicCrimson,
    background = ObsidianGreenBg,
    surface = Color(0xFF101015),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFFF5F5F7),
    onSurface = Color(0xFFF5F5F7)
)

@Composable
fun GamingTurboTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RedMagicDarkColors,
        content = content
    )
}
