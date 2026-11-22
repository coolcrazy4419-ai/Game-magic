package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.*
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class GameVisualFilter(
    val id: Int,
    val label: String,
    val tintColor: Color,
    val tintAlpha: Float,
    val accentColor: Color
) {
    ORIGINAL(0, "Original", Color.Transparent, 0f, Color(0xFF90A4AE)),
    HDR_VIVID(1, "HDR Vivid", Color(0xFF00E5FF), 0.08f, Color(0xFF00E5FF)),
    ULTRA_CRISP(2, "Ultra Crisp", Color(0xFF76FF03), 0.07f, Color(0xFF76FF03)),
    ENEMY_SPOTTER(3, "Spotter", Color(0xFFFFEA00), 0.09f, Color(0xFFFFEA00)),
    NIGHT_VISION(4, "Night Vis", Color(0xFF00E676), 0.12f, Color(0xFF00E676)),
    COOL_PRO(5, "Cool Pro", Color(0xFF2979FF), 0.09f, Color(0xFF448AFF)),
    WARM_BOOST(6, "Warm Boost", Color(0xFFFF6D00), 0.09f, Color(0xFFFF9100)),
    SOFT_EYE(7, "Soft Eye", Color(0xFFFFB300), 0.11f, Color(0xFFFFCA28)),
    NEON_CONTRAST(8, "Neon Pop", Color(0xFFD500F9), 0.08f, Color(0xFFE040FB)),
    AMOLED_DARK(9, "AMOLED", Color(0xFF000000), 0.16f, Color(0xFF80CBC4))
}

fun resolveModeAccent(s: MagicSettings): Color = when {
    s.iceCoolingEnabled -> IceBlueGlow
    s.performanceMode == PerformanceMode.ULTRA -> UltraCrimsonGlow
    s.performanceMode == PerformanceMode.HEAVY -> HeavyPurpleGlow
    else -> BalancedCyanGlow
}

fun resolveModeFill(s: MagicSettings): Color = when {
    s.iceCoolingEnabled -> IceFillDark
    s.performanceMode == PerformanceMode.ULTRA -> UltraFillDark
    s.performanceMode == PerformanceMode.HEAVY -> HeavyFillDark
    else -> BalancedFillDark
}

sealed interface MagicSidebarAction {
    data object TurboBoostNow : MagicSidebarAction
    data class SetPerformanceMode(val mode: PerformanceMode) : MagicSidebarAction
    data class ToggleSustainedPerformance(val enabled: Boolean) : MagicSidebarAction
    data class Toggle24HourClock(val enabled: Boolean) : MagicSidebarAction
    data class ToggleVoiceChanger(val enabled: Boolean) : MagicSidebarAction
    data class SelectVoicePreset(val preset: VoicePreset) : MagicSidebarAction
    data class ToggleLiveMicMonitor(val enabled: Boolean) : MagicSidebarAction
    data class ToggleCrosshair(val enabled: Boolean) : MagicSidebarAction
    data object CycleCrosshairStyle : MagicSidebarAction
    data class SetCrosshairColor(val hex: Long) : MagicSidebarAction
    data class AdjustCrosshairSize(val delta: Int) : MagicSidebarAction
    data class ToggleDnd(val enabled: Boolean) : MagicSidebarAction
    data class ToggleTouchGuard(val enabled: Boolean) : MagicSidebarAction
    data object TakeScreenshot : MagicSidebarAction
    data object ToggleScreenRecording : MagicSidebarAction
    data class SelectVisualFilter(val filter: GameVisualFilter) : MagicSidebarAction
    data class ToggleFloatingCall(val enabled: Boolean) : MagicSidebarAction
    data class ToggleSmartVoiceMic(val enabled: Boolean) : MagicSidebarAction
    data class ToggleIceCooling(val enabled: Boolean) : MagicSidebarAction
    data class SetOverheatThreshold(val tempC: Float) : MagicSidebarAction
    data class SetLowBatteryThreshold(val pct: Int) : MagicSidebarAction
    data class TogglePingShield(val enabled: Boolean) : MagicSidebarAction
    data class SetPingThreshold(val ms: Int) : MagicSidebarAction
    data class SetEdgeOnLeft(val left: Boolean) : MagicSidebarAction
    data class SetEdgeSize(val scale: Float) : MagicSidebarAction
    data class SetEdgeOpacity(val opacity: Float) : MagicSidebarAction
    data class ToggleSoundEffects(val enabled: Boolean) : MagicSidebarAction
    data class ToggleFastAnimations(val enabled: Boolean) : MagicSidebarAction
}

/**
 * Upgraded Pro Game Magic Floating Drawer (`228.dp` ~42% screen width, max `76%` height).
 * Features:
 *  - Enhanced multi-ring Neon Glow border
 *  - Icon-tile grid (tap icon to toggle ON/OFF, label underneath—no side switches)
 *  - 10 In-Game Visual & Smoothness Filters
 *  - Screen Recorder, Clear Memory, Floating Call Attend, Smart Voice-Chat Mic, DND Swipe Blocker
 *  - Expandable `More ▼` section preserving 100% of original tools (Ping Shield, Heat/Battery alerts, Voice Presets, Crosshair, Sidebar Setup)
 */
@Composable
fun CompactSwipeUpSidebar(
    settings: MagicSettings,
    sysStats: SystemTelemetry,
    netStats: NetworkTelemetry,
    boostShockwaveTick: Int,
    boostReadout: String?,
    activeFilter: GameVisualFilter = GameVisualFilter.ORIGINAL,
    isRecordingScreen: Boolean = false,
    recordingSeconds: Int = 0,
    floatingCallEnabled: Boolean = true,
    smartVoiceChatMicOnly: Boolean = true,
    isVoiceChatDetected: Boolean = false,
    onClose: () -> Unit,
    onAction: (MagicSidebarAction) -> Unit
) {
    var clockText by remember { mutableStateOf("") }
    var showMoreSection by remember { mutableStateOf(false) }
    var showFilterTray by remember { mutableStateOf(false) }

    LaunchedEffect(settings.use24HourClock) {
        val pattern = if (settings.use24HourClock) "HH:mm:ss" else "hh:mm:ss a"
        val fmt = SimpleDateFormat(pattern, Locale.getDefault())
        while (true) {
            clockText = fmt.format(Date())
            delay(1000L)
        }
    }

    val shockwave = remember { Animatable(0f) }
    LaunchedEffect(boostShockwaveTick) {
        if (boostShockwaveTick > 0) {
            shockwave.snapTo(0f)
            shockwave.animateTo(1f, tween(durationMillis = 560, easing = FastOutSlowInEasing))
        }
    }

    val accent by animateColorAsState(
        targetValue = resolveModeAccent(settings),
        animationSpec = tween(220),
        label = "drawerAccent"
    )
    val deepFill by animateColorAsState(
        targetValue = resolveModeFill(settings),
        animationSpec = tween(220),
        label = "drawerFill"
    )

    val panelShape = if (settings.edgeOnLeft) {
        RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp)
    } else {
        RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp)
    }

    val crosshairColors = remember {
        listOf(0xFF00E676L, 0xFF00E5FFL, 0xFFFF1744L, 0xFFAA00FFL, 0xFFFFAB00L)
    }

    Surface(
        shape = panelShape,
        color = Color.Transparent,
        modifier = Modifier
            .width(228.dp)
            .fillMaxHeight(0.76f)
            .darkNeonGlow(accent, cornerRadius = 18.dp, glowAlpha = 0.52f, strokeWidth = 1.4.dp)
            .border(
                width = 1.2.dp,
                brush = Brush.verticalGradient(
                    listOf(accent, EmeraldGlow.copy(alpha = 0.8f), HeavyPurpleGlow.copy(alpha = 0.85f), accent)
                ),
                shape = panelShape
            )
            .testTag("compact_magic_drawer")
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            deepFill.copy(alpha = 0.96f),
                            ObsidianGreenBg.copy(alpha = 0.98f),
                            ObsidianSurface.copy(alpha = 0.98f)
                        )
                    )
                )
        ) {
            // Dual-Ring Emerald & Royal Violet Shockwave Animation
            if (shockwave.value in 0.01f..0.99f) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val p = shockwave.value
                    val maxRadius = size.maxDimension * 0.95f
                    val origin = Offset(size.width / 2f, size.height * 0.22f)
                    val alpha = (1f - p).coerceIn(0f, 1f)

                    drawCircle(
                        color = EmeraldGlow.copy(alpha = alpha * 0.28f),
                        radius = p * maxRadius,
                        center = origin,
                        style = Stroke(width = 8.dp.toPx())
                    )
                    drawCircle(
                        color = EmeraldWireframe.copy(alpha = alpha * 0.9f),
                        radius = p * maxRadius,
                        center = origin,
                        style = Stroke(width = 2.4.dp.toPx())
                    )
                    val p2 = ((p - 0.14f) / 0.86f).coerceIn(0f, 1f)
                    if (p2 > 0f) {
                        drawCircle(
                            color = HeavyPurpleGlow.copy(alpha = (1f - p2) * 0.82f),
                            radius = p2 * maxRadius * 0.76f,
                            center = origin,
                            style = Stroke(width = 2.2.dp.toPx())
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 9.dp, vertical = 9.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // ===== 1. LIVE TELEMETRY HEADER =====
                TelemetryHeaderBlock(
                    settings = settings,
                    clockText = clockText,
                    sysStats = sysStats,
                    netStats = netStats,
                    accent = accent,
                    onClose = onClose
                )

                // ===== 2. 4-THEME PERFORMANCE MODE PILL SELECTOR =====
                PerformanceModePillGrid(
                    settings = settings,
                    onSelectMode = { mode -> onAction(MagicSidebarAction.SetPerformanceMode(mode)) },
                    onToggleIceCooling = { onAction(MagicSidebarAction.ToggleIceCooling(!settings.iceCoolingEnabled)) }
                )

                // ===== 3. ICON-GRID GAMING CONTROLS (TAP ICON TO TOGGLE ON/OFF, NAME UNDERNEATH) =====
                Text(
                    text = "PRO GAMING CONTROLS",
                    color = accent,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp
                )

                // Row 1: Clear Memory | Screen Rec | Game Filters
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconFeatureTile(
                        icon = Icons.Rounded.CleaningServices,
                        label = boostReadout ?: "Clear RAM",
                        active = boostReadout != null || shockwave.value in 0.01f..0.6f,
                        glowColor = EmeraldGlow,
                        modifier = Modifier.weight(1f),
                        onClick = { onAction(MagicSidebarAction.TurboBoostNow) }
                    )
                    val recMins = recordingSeconds / 60
                    val recSecs = recordingSeconds % 60
                    IconFeatureTile(
                        icon = if (isRecordingScreen) Icons.Rounded.StopCircle else Icons.Rounded.Videocam,
                        label = if (isRecordingScreen) String.format("%02d:%02d", recMins, recSecs) else "Screen Rec",
                        active = isRecordingScreen,
                        glowColor = UltraCrimsonGlow,
                        modifier = Modifier.weight(1f),
                        onClick = { onAction(MagicSidebarAction.ToggleScreenRecording) }
                    )
                    IconFeatureTile(
                        icon = Icons.Rounded.AutoFixHigh,
                        label = if (activeFilter == GameVisualFilter.ORIGINAL) "10 Filters" else activeFilter.label,
                        active = activeFilter != GameVisualFilter.ORIGINAL || showFilterTray,
                        glowColor = activeFilter.accentColor,
                        modifier = Modifier.weight(1f),
                        onClick = { showFilterTray = !showFilterTray }
                    )
                }

                // Expandable 10 In-Game Visual & Smoothness Filters Grid
                AnimatedVisibility(
                    visible = showFilterTray,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(ObsidianCard.copy(alpha = 0.94f))
                            .border(0.85.dp, BalancedCyanGlow.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                            .padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = "IN-GAME GRAPHICS & SMOOTH FILTERS (10)",
                            color = BalancedCyanGlow,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.6.sp
                        )
                        GameVisualFilter.entries.chunked(2).forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                pair.forEach { filter ->
                                    val picked = activeFilter == filter
                                    Surface(
                                        color = if (picked) filter.accentColor.copy(alpha = 0.22f) else ObsidianGreenBg,
                                        shape = RoundedCornerShape(7.dp),
                                        border = BorderStroke(
                                            width = if (picked) 1.dp else 0.65.dp,
                                            color = if (picked) filter.accentColor else ObsidianDivider
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { onAction(MagicSidebarAction.SelectVisualFilter(filter)) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(filter.accentColor)
                                            )
                                            Text(
                                                text = filter.label,
                                                color = if (picked) Color.White else MagicTextSecondary,
                                                fontSize = 8.5.sp,
                                                fontWeight = if (picked) FontWeight.Bold else FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Row 2: DND + Blocker | In-Game Call | Smart Voice Mic
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconFeatureTile(
                        icon = Icons.Rounded.DoNotDisturbOn,
                        label = "DND Lock",
                        active = settings.dndEnabled,
                        glowColor = UltraCrimsonGlow,
                        modifier = Modifier.weight(1f),
                        onClick = { onAction(MagicSidebarAction.ToggleDnd(!settings.dndEnabled)) }
                    )
                    IconFeatureTile(
                        icon = Icons.Rounded.PhoneInTalk,
                        label = "In-Game Call",
                        active = floatingCallEnabled,
                        glowColor = BalancedCyanGlow,
                        modifier = Modifier.weight(1f),
                        onClick = { onAction(MagicSidebarAction.ToggleFloatingCall(!floatingCallEnabled)) }
                    )
                    IconFeatureTile(
                        icon = Icons.Rounded.RecordVoiceOver,
                        label = if (settings.voiceChangerEnabled) {
                            if (smartVoiceChatMicOnly && !isVoiceChatDetected) "Mic Standby" else "Voice FX"
                        } else {
                            "Voice FX"
                        },
                        active = settings.voiceChangerEnabled,
                        glowColor = HeavyPurpleGlow,
                        modifier = Modifier.weight(1f),
                        onClick = { onAction(MagicSidebarAction.ToggleVoiceChanger(!settings.voiceChangerEnabled)) }
                    )
                }

                // Row 3: Crosshair | Screenshot | Touch Guard
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconFeatureTile(
                        icon = Icons.Rounded.GpsFixed,
                        label = "Crosshair",
                        active = settings.crosshairEnabled,
                        glowColor = EmeraldGlow
