package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.HardwareSnapshot
import com.example.core.RedMagicPerfMode
import com.example.core.RedMagicState

sealed interface MagicSidebarAction {
    data object TurboBoostNow : MagicSidebarAction
    data object NetworkBoostNow : MagicSidebarAction
    data object TakeScreenshot : MagicSidebarAction
    data object ToggleScreenRecording : MagicSidebarAction
    data object CycleFilter : MagicSidebarAction
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
}

@Composable
fun CompactSwipeUpSidebar(
    rmState: RedMagicState,
    hw: HardwareSnapshot,
    boostReadout: String?,
    onClose: () -> Unit,
    onAction: (MagicSidebarAction) -> Unit
) {
    val bgCard = Color(0xF20B0B0E)
    val tileBg = Color(0xFF15151C)
    val accentOrange = Color(0xFFFF551A)
    val accentGreen = Color(0xFF00E676)
    val textPrimary = Color(0xFFF5F5F7)
    val textMuted = Color(0xFF9494A3)

    Column(
        modifier = Modifier
            .width(284.dp)
            .fillMaxHeight(0.92f)
            .padding(start = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bgCard)
            .border(1.2.dp, accentOrange.copy(alpha = 0.7f), RoundedCornerShape(18.dp))
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accentOrange)
                )
                Text(
                    text = "GAME MAGIC PRO",
                    color = textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(tileBg)
                    .clickable { onClose() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("HIDE", color = textMuted, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(tileBg)
                .padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatBadge("FPS", "${hw.fps}", accentOrange)
            StatBadge("PING", "${hw.pingMs}ms", accentGreen)
            StatBadge("CPU", "${hw.cpuPercent}%", textPrimary)
            StatBadge("GPU", "${hw.gpuPercent}%", textPrimary)
        }

        if (!boostReadout.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentOrange.copy(alpha = 0.18f))
                    .border(0.8.dp, accentOrange, RoundedCornerShape(8.dp))
                    .padding(vertical = 5.dp, horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(boostReadout, color = accentOrange, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        Text("PERFORMANCE MODE", color = textMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            RedMagicPerfMode.entries.forEach { mode ->
                val selected = rmState.perfMode == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) accentOrange else tileBg)
                        .clickable { onAction(MagicSidebarAction.SetPerfMode(mode)) }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mode.label,
                        color = if (selected) Color.White else textMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Text("PRO GAMING TOOLS", color = textMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ToggleTile(
                modifier = Modifier.weight(1f),
                title = "Block Alerts",
                subtitle = if (rmState.blockAlerts) "DND ON" else "OFF",
                active = rmState.blockAlerts
            ) { onAction(MagicSidebarAction.ToggleBlockAlerts(!rmState.blockAlerts)) }

            ToggleTile(
                modifier = Modifier.weight(1f),
                title = "Lock Shade",
                subtitle = if (rmState.lockControlCenter) "LOCKED" else "OFF",
                active = rmState.lockControlCenter
            ) { onAction(MagicSidebarAction.ToggleLockControlCenter(!rmState.lockControlCenter)) }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ToggleTile(
                modifier = Modifier.weight(1f),
                title = "Call Banner",
                subtitle = if (rmState.smallCallBanner) "MINI" else "OFF",
                active = rmState.smallCallBanner
            ) { onAction(MagicSidebarAction.ToggleSmallCallBanner(!rmState.smallCallBanner)) }

            ToggleTile(
                modifier = Modifier.weight(1f),
                title = "Bright Lock",
                subtitle = if (rmState.brightnessLock) "FIXED" else "AUTO",
                active = rmState.brightnessLock
            ) { onAction(MagicSidebarAction.ToggleBrightnessLock(!rmState.brightnessLock)) }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ToggleTile(
                modifier = Modifier.weight(1f),
                title = "Clean RAM",
                subtitle = "BOOST NOW",
                active = false
            ) { onAction(MagicSidebarAction.TurboBoostNow) }

            ToggleTile(
                modifier = Modifier.weight(1f),
                title = "Net Boost",
                subtitle = "${hw.pingMs} ms",
                active = false
            ) { onAction(MagicSidebarAction.NetworkBoostNow) }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ToggleTile(
                modifier = Modifier.weight(1f),
                title = "Screenshot",
                subtitle = "CAPTURE",
                active = false
            ) { onAction(MagicSidebarAction.TakeScreenshot) }

            ToggleTile(
                modifier = Modifier.weight(1f),
                title = "Record",
                subtitle = if (rmState.isRecording) "REC ${rmState.recordingSeconds}s" else "MP4",
                active = rmState.isRecording
            ) { onAction(MagicSidebarAction.ToggleScreenRecording) }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ToggleTile(
                modifier = Modifier.weight(1f),
                title = "Voice FX",
                subtitle = if (rmState.voiceChanger) "ACTIVE" else "OFF",
                active = rmState.voiceChanger
            ) { onAction(MagicSidebarAction.ToggleVoiceChanger(!rmState.voiceChanger)) }

            ToggleTile(
                modifier = Modifier.weight(1f),
                title = "Game Filter",
                subtitle = rmState.filter.label,
                active = rmState.filter.ordinal != 0
            ) { onAction(MagicSidebarAction.CycleFilter) }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ToggleTile(
                modifier = Modifier.weight(1f),
                title = "AI Isolation",
                subtitle = if (rmState.aiIsolation) "ON" else "OFF",
                active = rmState.aiIsolation
            ) { onAction(MagicSidebarAction.ToggleAiIsolation(!rmState.aiIsolation)) }

            ToggleTile(
                modifier = Modifier.weight(1f),
                title = "FPS Pill",
                subtitle = if (rmState.showMiniFpsCounter) "SHOWN" else "HIDDEN",
                active = rmState.showMiniFpsCounter
            ) { onAction(MagicSidebarAction.ToggleMiniFpsCounter(!rmState.showMiniFpsCounter)) }
        }

        Text("QUICK FLOATING APPS", color = textMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            QuickAppChip(Modifier.weight(1f), "WhatsApp") {
                onAction(MagicSidebarAction.LaunchFloatingApp("com.whatsapp"))
            }
            QuickAppChip(Modifier.weight(1f), "YouTube") {
                onAction(MagicSidebarAction.LaunchFloatingApp("com.google.android.youtube"))
            }
            QuickAppChip(Modifier.weight(1f), "Telegram") {
                onAction(MagicSidebarAction.LaunchFloatingApp("org.telegram.messenger"))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(tileBg)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Touch Sensitivity", color = textPrimary, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
                Text("${(rmState.touchSensitivity * 100).toInt()}%", color = accentOrange, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = rmState.touchSensitivity,
                onValueChange = { onAction(MagicSidebarAction.SetTouchSensitivity(it)) },
                colors = SliderDefaults.colors(thumbColor = accentOrange, activeTrackColor = accentOrange)
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Touch Response", color = textPrimary, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
                Text("${(rmState.touchResponse * 100).toInt()}%", color = accentOrange, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = rmState.touchResponse,
                onValueChange = { onAction(MagicSidebarAction.SetTouchResponse(it)) },
                colors = SliderDefaults.colors(thumbColor = accentOrange, activeTrackColor = accentOrange)
            )
        }
    }
}

@Composable
private fun StatBadge(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = valueColor, fontSize = 11.sp, fontWeight = FontWeight.Black)
        Text(label, color = Color(0xFF9494A3), fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ToggleTile(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    active: Boolean,
    onClick: () -> Unit
) {
    val accent = Color(0xFFFF551A)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) accent.copy(alpha = 0.2f) else Color(0xFF15151C))
            .border(
                width = 1.dp,
                color = if (active) accent else Color(0xFF252530),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 7.dp)
    ) {
        Text(title, color = Color(0xFFF5F5F7), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(
            subtitle,
            color = if (active) accent else Color(0xFF9494A3),
            fontSize = 8.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun QuickAppChip(
    modifier: Modifier = Modifier,
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF15151C))
            .border(0.8.dp, Color(0xFF2B2B38), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color(0xFFF5F5F7), fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
    }
}
