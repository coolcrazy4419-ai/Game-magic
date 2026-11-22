package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val GameMagicColorScheme = darkColorScheme(
    primary = EmeraldGlow,
    onPrimary = ObsidianGreenBg,
    primaryContainer = BalancedFillDark,
    onPrimaryContainer = MagicTextPrimary,
    secondary = HeavyPurpleGlow,
    onSecondary = Color.White,
    tertiary = IceBlueGlow,
    onTertiary = ObsidianGreenBg,
    background = ObsidianGreenBg,
    onBackground = MagicTextPrimary,
    surface = ObsidianSurface,
    onSurface = MagicTextPrimary,
    surfaceVariant = ObsidianCard,
    onSurfaceVariant = MagicTextSecondary,
    outline = ObsidianDivider,
    error = UltraCrimsonGlow,
    onError = Color.White
)

@Composable
fun GamingTurboTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity
            activity?.window?.let { window ->
                @Suppress("DEPRECATION")
                window.statusBarColor = Color.Transparent.toArgb()
                @Suppress("DEPRECATION")
                window.navigationBarColor = ObsidianGreenBg.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = GameMagicColorScheme,
        typography = CyberTypography,
        content = content
    )
}
