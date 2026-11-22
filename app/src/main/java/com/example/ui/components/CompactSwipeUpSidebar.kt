package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.*

private val RmBg = Color(0xF50B0B0E)
private val RmCard = Color(0xFF141419)
private val RmOrange = Color(0xFFFF551A)
private val RmCrimson = Color(0xFFFF2A3C)
private val RmText = Color(0xFFF5F5F7)
private val RmMuted = Color(0xFF9898A6)

enum class GameVisualFilter(val label: String, val tintColor: Color, val tintAlpha: Float) {
    ORIGINAL("Original", Color.Transparent, 0f),
    PRO_CLARITY("Pro Clarity", Color(0xFF00E5FF), 0.05f),
    NIGHT_VISION("Night Vision", Color(0xFF76FF03), 0.08f),
    VIVID_BOOST("Vivid Boost", Color(0xFFFF551A), 0.07f),
    EYE_CARE("Eye Care", Color(0xFFFF9E00), 0.12f),
    COOL_ICE("Cool Ice", Color(0xFF2979FF), 0.08f)
}

sealed interface MagicSidebarAction {
    data object TurboBoostNow : MagicSidebarAction
    data object NetworkBoostNow : MagicSidebarAction
    data object TakeScreenshot : MagicSidebarAction
    data object ToggleScreenRecording : MagicSidebarAction
    data class SetPerfMode(val mode: RedMagicPerfMode) : MagicSidebarAction
    data class ToggleBlockAlerts(val enabled: Boolean) : MagicSidebarAction
    data class ToggleLockControlCenter(val enabled: Boolean) : MagicSidebarAction
    data class ToggleSmallCallBanner(val enabled: Boolean) : MagicSidebarAction
    data class ToggleBrightnessLock(val enabled: Boolean) : MagicSidebarAction
    data class ToggleVoiceChanger(val enabled: Boolean) : MagicSidebarAction
    data class ToggleAiIsolation(val enabled: Boolean) : MagicSidebarAction
    data class ToggleMiniFpsCounter(val enabled: Boolean) : MagicSidebarAction
    data class LaunchFloatingApp(val pkg: String) : MagicSidebarAction
    data object CycleFilter : MagicSidebarAction
    data class SetTouchSensitivity(val value: Float) : MagicSidebarAction
    data class SetTouchResponse(val value: Float) : MagicSidebarAction
}

fun resolveModeAccent(s: MagicSettings): Color = RmOrange

fun Modifier.darkNeonGlow(
    glowColor: Color,
    cornerRadius: androidx.compose.ui.unit.Dp = 12.dp,
    glowAlpha: Float = 0.4f,
    strokeWidth: androidx.compose.ui.unit.Dp = 1.dp
): Modifier = this.border(strokeWidth, glowColor.copy(alpha = 0.85f), RoundedCornerShape(cornerRadius))

@Composable
fun CompactSwipeUpSidebar(
    rmState: RedMagicState,
    hw: HardwareSnapshot,
    boostReadout: String?,
    onClose: () -> Unit,
    onAction: (MagicSidebarAction) -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    var dragX by remember { mutableFloatStateOf(0f) }
    var showQuickAppsRow by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 14.dp)
            .width(236.dp)
            .wrapContentHeight()
            .heightIn(max = 530.dp)
            .clip(shape)
            .background(RmBg)
            .border(1.3.dp, RmOrange, shape)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (kotlin.math.abs(dragX) > 36f) onClose()
                        dragX = 0f
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        dragX += amount
                    }
                )
            }
            .padding(10.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        // Header + Touch-to-Back Close button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(Icons.Rounded.Bolt, contentDescription = null, tint = RmOrange, modifier = Modifier.size(16.dp))
                Text("GAME TURBO", color = RmOrange, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp)
            }
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(RmCard)
                    .border(0.8.dp, RmOrange.copy(alpha = 0.6f), RoundedCornerShape(7.dp))
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Close", tint = RmText, modifier = Modifier.size(14.dp))
            }
        }

        // Top Row: Live FPS, Ping, CPU, GPU
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            MiniStatPill("FPS", "${hw.fps}", RmOrange, Modifier.weight(1f))
            MiniStatPill("PING", "${hw.pingMs}ms", Color(0xFF00E676), Modifier.weight(1f))
            MiniStatPill("CPU", "${hw.cpuPercent}%", RmText, Modifier.weight(1f))
            MiniStatPill("GPU", "${hw.gpuPercent}%", RmText, Modifier.weight(1f))
        }

        if (boostReadout != null) {
            Text(
                text = "⚡ $boostReadout",
                color = Color(0xFF00E676),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x2200E676))
                    .padding(vertical = 4.dp, horizontal = 8.dp)
            )
        }

        // Quick Tiles (All screenshot features with clean Enable/Disable states)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            QuickTile(
                icon = Icons.Rounded.NotificationsOff,
                label = "Block alerts",
                active = rmState.blockAlerts,
                modifier = Modifier.weight(1f),
                onClick = { onAction(MagicSidebarAction.ToggleBlockAlerts(!rmState.blockAlerts)) }
            )
            QuickTile(
                icon = Icons.Rounded.Lock,
                label = "Lock shade",
                active = rmState.lockControlCenter,
                modifier = Modifier.weight(1f),
                onClick = { onAction(MagicSidebarAction.ToggleLockControlCenter(!rmState.lockControlCenter)) }
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            QuickTile(
                icon = Icons.Rounded.PhoneInTalk,
                label = "Call banner",
                active = rmState.smallCallBanner,
                modifier = Modifier.weight(1f),
                onClick = { onAction(MagicSidebarAction.ToggleSmallCallBanner(!rmState.smallCallBanner)) }
            )
            QuickTile(
                icon = Icons.Rounded.BrightnessMedium,
                label = "Bright lock",
                active = rmState.brightnessLock,
                modifier = Modifier.weight(1f),
                onClick = { onAction(MagicSidebarAction.ToggleBrightnessLock(!rmState.brightnessLock)) }
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            QuickTile(
                icon = Icons.Rounded.Memory,
                label = "Clean RAM",
                active = false,
                modifier = Modifier.weight(1f),
                onClick = { onAction(MagicSidebarAction.TurboBoostNow) }
            )
            QuickTile(
                icon = Icons.Rounded.Speed,
                label = "Net boost",
                active = false,
                modifier = Modifier.weight(1f),
                onClick = { onAction(MagicSidebarAction.NetworkBoostNow) }
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            QuickTile(
                icon = Icons.Rounded.Screenshot,
                label = "Screenshot",
                active = false,
                modifier = Modifier.weight(1f),
                onClick = { onAction(MagicSidebarAction.TakeScreenshot) }
            )
            QuickTile(
                icon = Icons.Rounded.FiberManualRecord,
                label = if (rmState.isRecording) "REC ${rmState.recordingSeconds}s" else "Record",
                active = rmState.isRecording,
                activeColor = RmCrimson,
                modifier = Modifier.weight(1f),
                onClick = { onAction(MagicSidebarAction.ToggleScreenRecording) }
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            QuickTile(
                icon = Icons.Rounded.RecordVoiceOver,
                label = "Voice FX",
                active = rmState.voiceChanger,
                modifier = Modifier.weight(1f),
                onClick = { onAction(MagicSidebarAction.ToggleVoiceChanger(!rmState.voiceChanger)) }
            )
            QuickTile(
                icon = Icons.Rounded.Palette,
                label = rmState.filter.label,
                active = rmState.filter != RedMagicFilter.ORIGINAL,
                modifier = Modifier.weight(1f),
                onClick = { onAction(MagicSidebarAction.CycleFilter) }
            )
        }

        // New Screenshot Tiles: AI Isolation + Quick Apps + Mini FPS Counter
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            QuickTile(
                icon = Icons.Rounded.Shield,
                label = "AI isolation",
                active = rmState.aiIsolation,
                modifier = Modifier.weight(1f),
                onClick = { onAction(MagicSidebarAction.ToggleAiIsolation(!rmState.aiIsolation)) }
            )
            QuickTile(
                icon = Icons.Rounded.Apps,
                label = "Quick apps",
                active = showQuickAppsRow,
                modifier = Modifier.weight(1f),
                onClick = { showQuickAppsRow = !showQuickAppsRow }
            )
        }

        if (showQuickAppsRow) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                QuickAppChip("WhatsApp", Modifier.weight(1f)) { onAction(MagicSidebarAction.LaunchFloatingApp("com.whatsapp")) }
                QuickAppChip("YouTube", Modifier.weight(1f)) { onAction(MagicSidebarAction.LaunchFloatingApp("com.google.android.youtube")) }
                QuickAppChip("Telegram", Modifier.weight(1f)) { onAction(MagicSidebarAction.LaunchFloatingApp("org.telegram.messenger")) }
            }
        }

        QuickTile(
            icon = Icons.Rounded.Speed,
            label = if (rmState.showMiniFpsCounter) "Over-game FPS/Ping: ON" else "Over-game FPS/Ping: OFF",
            active = rmState.showMiniFpsCounter,
            modifier = Modifier.fillMaxWidth(),
            onClick = { onAction(MagicSidebarAction.ToggleMiniFpsCounter(!rmState.showMiniFpsCounter)) }
        )

        // Touch Sensitivity & Touch Response Sliders
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(RmCard)
                .border(0.7.dp, RmOrange.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Touch sensitivity", color = RmMuted, fontSize = 9.sp)
                Text("${(rmState.touchSensitivity * 100).toInt()}%", color = RmOrange, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = rmState.touchSensitivity,
                onValueChange = { onAction(MagicSidebarAction.SetTouchSensitivity(it)) },
                modifier = Modifier.height(22.dp),
                colors = SliderDefaults.colors(thumbColor = RmOrange, activeTrackColor = RmOrange)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Touch response", color = RmMuted, fontSize = 9.sp)
                Text("${(rmState.touchResponse * 100).toInt()}%", color = RmOrange, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = rmState.touchResponse,
                onValueChange = { onAction(MagicSidebarAction.SetTouchResponse(it)) },
                modifier = Modifier.height(22.dp),
                colors = SliderDefaults.colors(thumbColor = RmOrange, activeTrackColor = RmOrange)
            )
        }

        // 4 Performance Modes
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            RedMagicPerfMode.entries.forEach { mode ->
                val selected = rmState.perfMode == mode
                val pillColor = if (mode == RedMagicPerfMode.LAG_FREE) RmCrimson else RmOrange
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) pillColor.copy(alpha = 0.22f) else RmCard)
                        .border(
                            width = if (selected) 1.dp else 0.6.dp,
                            color = if (selected) pillColor else RmMuted.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onAction(MagicSidebarAction.SetPerfMode(mode)) }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mode.label,
                        color = if (selected) RmText else RmMuted,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickAppChip(name: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(RmCard)
            .border(0.8.dp, RmOrange.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(name, color = RmText, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun MiniStatPill(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(RmCard)
            .border(0.7.dp, RmOrange.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = RmMuted, fontSize = 7.5.sp, fontWeight = FontWeight.Bold)
        Text(value, color = color, fontSize = 10.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun QuickTile(
    icon: ImageVector,
    label: String,
    active: Boolean,
    activeColor: Color = RmOrange,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (active) activeColor.copy(alpha = 0.20f) else RmCard)
            .border(
                width = if (active) 1.dp else 0.6.dp,
                color = if (active) activeColor else RmMuted.copy(alpha = 0.28f),
                shape = RoundedCornerShape(9.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 7.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(icon, contentDescription = null, tint = if (active) activeColor else RmMuted, modifier = Modifier.size(13.dp))
        Text(
            text = label,
            color = if (active) RmText else RmMuted,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
