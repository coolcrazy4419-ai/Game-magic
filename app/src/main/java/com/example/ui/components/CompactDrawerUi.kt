package com.example.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import com.example.core.HardwareSnapshot
import com.example.core.RedMagicPerfMode
import com.example.core.RedMagicState

sealed interface MagicSidebarAction {
    data object TurboBoostNow : MagicSidebarAction
    data object NetworkBoostNow : MagicSidebarAction
    data object TakeScreenshot : MagicSidebarAction
    data object ToggleScreenRecording : MagicSidebarAction
    data object CycleFilter : MagicSidebarAction
    data object CycleCrosshairStyle : MagicSidebarAction
    data class SetPerfMode(val mode: RedMagicPerfMode) : MagicSidebarAction
    data class ToggleBlockAlerts(val enabled: Boolean) : MagicSidebarAction
    data class ToggleLockControlCenter(val enabled: Boolean) : MagicSidebarAction
    data class ToggleSmallCallBanner(val enabled: Boolean) : MagicSidebarAction
    data class ToggleBrightnessLock(val enabled: Boolean) : MagicSidebarAction
    data class ToggleAiIsolation(val enabled: Boolean) : MagicSidebarAction
    data class ToggleMiniFpsCounter(val enabled: Boolean) : MagicSidebarAction
    data class ToggleVoiceChanger(val enabled: Boolean) : MagicSidebarAction
    data class LaunchFloatingApp(val pkg: String) : MagicSidebarAction
    data class SetTouchSensitivity(val value: Float) : MagicSidebarAction
    data class SetTouchResponse(val value: Float) : MagicSidebarAction
    data class AdjustCrosshairSize(val value: Float) : MagicSidebarAction
    data class SelectVisualFilter(val index: Int) : MagicSidebarAction
    data class SelectVoicePreset(val index: Int) : MagicSidebarAction
    data class SetCrosshairColor(val colorHex: Long) : MagicSidebarAction
}

@Composable
fun CompactDrawerUi(
    rmState: RedMagicState,
    hw: HardwareSnapshot,
    boostReadout: String?,
    onClose: () -> Unit,
    onAction: (MagicSidebarAction) -> Unit
) {
    Box {}
}
