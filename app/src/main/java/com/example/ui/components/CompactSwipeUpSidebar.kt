package com.example.ui.components

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.HardwareSnapshot
import com.example.core.RedMagicPerfMode
import com.example.core.RedMagicState

sealed interface ProSidebarAction {
    data object FixLagNow : ProSidebarAction
    data object TurboBoostNow : ProSidebarAction
    data object NetworkBoostNow : ProSidebarAction
    data object TakeScreenshot : ProSidebarAction
    data object ToggleScreenRecording : ProSidebarAction
    data object CycleFilter : ProSidebarAction
    data object ToggleCrosshair : ProSidebarAction
    data object CycleCrosshairStyle : ProSidebarAction
    data class SetFilterIntensity(val value: Float) : ProSidebarAction
    data class SetPerfMode(val mode: RedMagicPerfMode) : ProSidebarAction
    data class ToggleBlockAlerts(val enabled: Boolean) : ProSidebarAction
    data class ToggleLockControlCenter(val enabled: Boolean) : ProSidebarAction
    data class ToggleSmallCallBanner(val enabled: Boolean) : ProSidebarAction
    data class ToggleBrightnessLock(val enabled: Boolean) : ProSidebarAction
    data class ToggleAiIsolation(val enabled: Boolean) : ProSidebarAction
    data class ToggleAiFpsGuard(val enabled: Boolean) : ProSidebarAction
    data class ToggleMiniFpsCounter(val enabled: Boolean) : ProSidebarAction
    data class ToggleVoiceChanger(val enabled: Boolean) : ProSidebarAction
    data class LaunchFloatingApp(val pkg: String) : ProSidebarAction
    data class SetTouchSensitivity(val value: Float) : ProSidebarAction
    data class SetTouchResponse(val value: Float) : ProSidebarAction
}

object CyberSoundPlayer {
    fun playModeSound(modeIndex: Int) {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            val tone = when (modeIndex) {
                0 -> ToneGenerator.TONE_PROP_ACK
                1 -> ToneGenerator.TONE_PROP_BEEP2
                2 -> ToneGenerator.TONE_CDMA_HIGH_L
                else -> ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK
            }
            tg.startTone(tone, 180)
        } catch (_: Exception) {
        }
    }

    fun playLaunchTurbineSound() {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            tg.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 260)
        } catch (_: Exception) {
        }
    }

    fun playAppStartupSound() {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 200)
        } catch (_: Exception) {
        }
    }
}

@Composable
fun CompactSwipeUpSidebar(
    rmState: RedMagicState,
    hw: HardwareSnapshot,
    liveRealFps: Int,
    boostReadout: String?,
    filterIntensity: Float,
    crosshairEnabled: Boolean,
    aiFpsGuardEnabled: Boolean,
    onClose: () -> Unit,
    onAction: (ProSidebarAction) -> Unit
) {
    val bgCard = Color(0xF6060911)
    val tileBg = Color(0xFF0F1524)
    val neonCyan = Color(0xFF00E5FF)
    val neonOrange = Color(0xFFFF551A)
    val neonGreen = Color(0xFF00E676)
    val neonPurple = Color(0xFFD500F9)
    val neonGold = Color(0xFFFFD600)
    val textPrimary = Color(0xFFF8FAFC)
    val textMuted = Color(0xFF94A3B8)

    val displayFps = if (liveRealFps > 0) liveRealFps else hw.fps
    val modeColors = listOf(neonGreen, neonCyan, neonOrange, neonPurple)
    val activeModeColor by animateColorAsState(
        targetValue = modeColors[rmState.perfMode.ordinal % modeColors.size],
        animationSpec = tween(220),
        label = "modeColor"
    )

    val futuristicBorder = Brush.verticalGradient(
        colors = listOf(neonCyan, activeModeColor, neonPurple)
    )

    Column(
        modifier = Modifier
            .width(300.dp)
            .fillMaxHeight(0.95f)
            .clip(RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp))
            .background(bgCard)
            .border(1.6.dp, futuristicBorder, RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp))
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(activeModeColor)
                )
                Text(
                    text = "GAME MAGIC • AI CORE",
                    color = textPrimary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Black
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(tileBg)
                    .border(0.9.dp, neonOrange.copy(alpha = 0.75f), RoundedCornerShape(8.dp))
                    .clickable { onClose() }
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                Text("HIDE", color = neonOrange, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(tileBg)
                .border(0.9.dp, neonCyan.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                .padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatBadge("REAL FPS", "$displayFps", neonCyan)
            StatBadge("PING", "${hw.pingMs}ms", neonGreen)
            StatBadge("CPU", "${hw.cpuPercent}%", neonGold)
            StatBadge("GPU", "${hw.gpuPercent}%", neonPurple)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(neonCyan.copy(alpha = 0.25f), neonGreen.copy(alpha = 0.25f))
                    )
                )
                .border(1.2.dp, neonCyan, RoundedCornerShape(10.dp))
                .clickable { onAction(ProSidebarAction.FixLagNow) }
                .padding(vertical = 8.dp, horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "⚡ FIX LAG & CLEAR GAME CACHE",
                color = neonCyan,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Black
            )
        }

        if (!boostReadout.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(neonGreen.copy(alpha = 0.18f))
                    .border(1.dp, neonGreen, RoundedCornerShape(8.dp))
                    .padding(vertical = 5.dp, horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(boostReadout, color = neonGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        Text("PERFORMANCE ENGINE & POWER GRAPH", color = textMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            RedMagicPerfMode.entries.forEachIndexed { idx, mode ->
                val selected = rmState.perfMode == mode
                val mColor = modeColors[idx % modeColors.size]
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) mColor.copy(alpha = 0.28f) else tileBg)
                        .border(
                            width = if (selected) 1.3.dp else 0.7.dp,
                            color = if (selected) mColor else Color(0xFF252B3D),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable {
                            CyberSoundPlayer.playModeSound(idx)
                            onAction(ProSidebarAction.SetPerfMode(mode))
                        }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mode.label,
                        color = if (selected) mColor else textMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        ModePowerGraphCard(
            modeIndex = rmState.perfMode.ordinal,
            modeName = rmState.perfMode.label,
            accentColor = activeModeColor
        )

        Text("AI SMART GUARD", color = textMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ToggleTile(
                modifier = Modifier.weight(1f),
                title = "AI FPS Guard",
                subtitle = if (aiFpsGuardEnabled) "AUTO-BOOST" else "OFF",
                active = aiFpsGuardEnabled,
                accent = neonCyan
            ) { onAction(ProSidebarAction.ToggleAiFpsGuard(!aiFpsGuardEnabled)) }

            ToggleTile(
                modifier = Modifier.weight(1f),
                title = "AI Isolation",
                subtitle = if (rmState.aiIsolation) "LOCKED" else "OFF",
                active = rmState.aiIsolation,
                accent = neonPurple
            ) { onAction(ProSidebarAction.ToggleAiIsolation(!rmState.aiIsolation)) }
        }

        Text("VISUAL FILTER & INTENSITY ADJUST", color = textMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(tileBg)
                .border(0.9.dp, neonGold.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Filter: ${rmState.filter.label}",
                    color = neonGold,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(neonGold.copy(alpha = 0.2f))
                        .border(0.8.dp, neonGold, RoundedCornerShape(6.dp))
                        .clickable { onAction(ProSidebarAction.CycleFilter) }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text("CHANGE FILTER", color = neonGold, fontSize = 8.5.sp, fontWeight = FontWeight.Black)
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Filter Adjust", color = textPrimary, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                Text("${(filterIntensity * 100).toInt()}%", color = neonGold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = filterIntensity,
                onValueChange = { onAction(ProSidebarAction.SetFilterIntensity(it)) },
                colors = SliderDefaults.colors(thumbColor = neonGold, activeTrackColor = neonGold)
            )
        }

        Text("PRO GAMING TOOLS", color = textMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ToggleTile(
                modifier = Modifier.weight(1f),
                title = "Crosshair",
                subtitle = if (crosshairEnabled) "AIM ON" else "OFF",
                active = crosshairEnabled,
                accent = neonOrange
            ) { onAction(ProSidebarAction.ToggleCrosshair) }

            ToggleTile(
                modifier = Modifier.weight(1f),
                title = "Aim Style",
                subtitle = "SWITCH",
                active = crosshairEnabled,
                accent = neonCyan
            ) { onAction(ProSidebarAction.CycleCrosshairStyle) }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ToggleTile(
                modifier = Modifier.weight(1f),
                title = "Block Alerts",
                subtitle = if (rmState.blockAlerts) "DND ON" else "OFF",
                active = rmState.blockAlerts,
                accent = neonOrange
            ) { onAction(ProSidebarAction.ToggleBlockAlerts(!rmState.blockAlerts)) }

            ToggleTile(
                modifier = Modifier.weight(1f),
                title = "Lock Shade",
                subtitle = if (rmState.lockControlCenter) "LOCKED" else "OFF",
                active = rmState.lockControlCenter,
                accent = neonCyan
            ) { onAction(ProSidebarAction.ToggleLockControlCenter(!rmState.lockControlCenter)) }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ToggleTile(
                modifier = Modifier.weight(1f),
                title = "Call Banner",
                subtitle = if (rmState.smallCallBanner) "MINI" else "OFF",
                active = rmState.smallCallBanner,
                accent = neonGreen
            ) { onAction(ProSidebarAction.ToggleSmallCallBanner(!rmState.smallCallBanner)) }

            ToggleTile(
                modifier = Modifier.weight(1f),
                title = "Bright Lock",
                subtitle = if (rmState.brightnessLock) "FIXED" else "AUTO",
                active = rmState.brightnessLock,
                accent = neonGold
            ) { onAction(ProSidebarAction.ToggleBrightnessLock(!rmState.brightnessLock)) }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ToggleTile(
                modifier = Modifier.weight(1f),
                title = "Clean RAM",
                subtitle = "BOOST NOW",
                active = true,
                accent = neonCyan
            ) { onAction(ProSidebarAction.TurboBoostNow) }

            ToggleTile(
                modifier = Modifier.weight(1f),
                title = "Net Boost",
                subtitle = "${hw.pingMs} ms",
                active = true,
                accent = neonGreen
            ) { onAction(ProSidebarAction.NetworkBoostNow) }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ToggleTile(
                modifier = Modifier.weight(1f),
                title = "Screenshot",
                subtitle = "CAPTURE",
                active = false,
                accent = neonPurple
            ) { onAction(ProSidebarAction.TakeScreenshot) }

            ToggleTile(
                modifier = Modifier.weight(1f),
                title = "Record",
                subtitle = if (rmState.isRecording) "REC ${rmState.recordingSeconds}s" else "MP4",
                active = rmState.isRecording,
                accent = neonOrange
            ) { onAction(ProSidebarAction.ToggleScreenRecording) }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ToggleTile(
                modifier = Modifier.weight(1f),
                title = "Voice FX",
                subtitle = if (rmState.voiceChanger) "ACTIVE" else "OFF",
                active = rmState.voiceChanger,
                accent = neonPurple
            ) { onAction(ProSidebarAction.ToggleVoiceChanger(!rmState.voiceChanger)) }

            ToggleTile(
                modifier = Modifier.weight(1f),
                title = "FPS Pill",
                subtitle = if (rmState.showMiniFpsCounter) "SHOWN" else "HIDDEN",
                active = rmState.showMiniFpsCounter,
                accent = neonGreen
            ) { onAction(ProSidebarAction.ToggleMiniFpsCounter(!rmState.showMiniFpsCounter)) }
        }

        Text("QUICK FLOATING APPS", color = textMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            QuickAppChip(Modifier.weight(1f), "WhatsApp", neonGreen) {
                onAction(ProSidebarAction.LaunchFloatingApp("com.whatsapp"))
            }
            QuickAppChip(Modifier.weight(1f), "YouTube", neonOrange) {
                onAction(ProSidebarAction.LaunchFloatingApp("com.google.android.youtube"))
            }
            QuickAppChip(Modifier.weight(1f), "Telegram", neonCyan) {
                onAction(ProSidebarAction.LaunchFloatingApp("org.telegram.messenger"))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(tileBg)
                .border(0.8.dp, neonPurple.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Touch Sensitivity", color = textPrimary, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
                Text("${(rmState.touchSensitivity * 100).toInt()}%", color = neonCyan, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = rmState.touchSensitivity,
                onValueChange = { onAction(ProSidebarAction.SetTouchSensitivity(it)) },
                colors = SliderDefaults.colors(thumbColor = neonCyan, activeTrackColor = neonCyan)
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Touch Response", color = textPrimary, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
                Text("${(rmState.touchResponse * 100).toInt()}%", color = neonOrange, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = rmState.touchResponse,
                onValueChange = { onAction(ProSidebarAction.SetTouchResponse(it)) },
                colors = SliderDefaults.colors(thumbColor = neonOrange, activeTrackColor = neonOrange)
            )
        }
    }
}

@Composable
private fun ModePowerGraphCard(
    modeIndex: Int,
    modeName: String,
    accentColor: Color
) {
    val targetPower by animateFloatAsState(
        targetValue = when (modeIndex) {
            0 -> 0.42f
            1 -> 0.68f
            2 -> 0.88f
            else -> 1.0f
        },
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "powerLevel"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0A0F1C))
            .border(1.dp, accentColor.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "BOOST CURVE • ${modeName.uppercase()}",
                color = accentColor,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "OUTPUT ${(targetPower * 100).toInt()}%",
                color = Color(0xFFF8FAFC),
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
        ) {
            val w = size.width
            val h = size.height

            drawLine(
                color = Color.White.copy(alpha = 0.08f),
                start = Offset(0f, h * 0.5f),
                end = Offset(w, h * 0.5f),
                strokeWidth = 1f
            )

            val points = listOf(0.25f, 0.45f, 0.38f, 0.72f, 0.65f, 0.92f, 1.0f)
            val path = Path()
            val fillPath = Path()

            points.forEachIndexed { index, factor ->
                val x = (w / (points.size - 1)) * index
                val y = h - (h * 0.85f * factor * targetPower) - 4f
                if (index == 0) {
                    path.moveTo(x, y)
                    fillPath.moveTo(x, h)
                    fillPath.lineTo(x, y)
                } else {
                    path.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
                if (index == points.lastIndex) {
                    fillPath.lineTo(x, h)
                    fillPath.close()
                }
            }

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    listOf(accentColor.copy(alpha = 0.35f), Color.Transparent)
                )
            )
            drawPath(
                path = path,
                color = accentColor,
                style = Stroke(width = 3.2f, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
private fun StatBadge(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = valueColor, fontSize = 11.sp, fontWeight = FontWeight.Black)
        Text(label, color = Color(0xFF94A3B8), fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ToggleTile(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    active: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) accent.copy(alpha = 0.18f) else Color(0xFF0F1524))
            .border(
