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
            .width(212.dp)
            .fillMaxHeight(0.72f)
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
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                TelemetryHeaderBlock(
                    settings = settings,
                    clockText = clockText,
                    sysStats = sysStats,
                    netStats = netStats,
                    accent = accent,
                    onClose = onClose
                )

                PerformanceModePillGrid(
                    settings = settings,
                    onSelectMode = { mode -> onAction(MagicSidebarAction.SetPerformanceMode(mode)) },
                    onToggleIceCooling = { onAction(MagicSidebarAction.ToggleIceCooling(!settings.iceCoolingEnabled)) }
                )

                Text(
                    text = "PRO GAMING CONTROLS",
                    color = accent,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    IconFeatureTile(
                        icon = Icons.Rounded.GpsFixed,
                        label = "Crosshair",
                        active = settings.crosshairEnabled,
                        glowColor = EmeraldGlow,
                        modifier = Modifier.weight(1f),
                        onClick = { onAction(MagicSidebarAction.ToggleCrosshair(!settings.crosshairEnabled)) }
                    )
                    IconFeatureTile(
                        icon = Icons.Rounded.Screenshot,
                        label = "Screenshot",
                        active = false,
                        glowColor = BalancedCyanGlow,
                        modifier = Modifier.weight(1f),
                        onClick = { onAction(MagicSidebarAction.TakeScreenshot) }
                    )
                    IconFeatureTile(
                        icon = Icons.Rounded.SwipeVertical,
                        label = "Swipe Lock",
                        active = settings.touchGuardEnabled,
                        glowColor = IceBlueGlow,
                        modifier = Modifier.weight(1f),
                        onClick = { onAction(MagicSidebarAction.ToggleTouchGuard(!settings.touchGuardEnabled)) }
                    )
                }

                Surface(
                    color = ObsidianCard.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(9.dp),
                    border = BorderStroke(0.85.dp, accent.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showMoreSection = !showMoreSection }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (showMoreSection) "LESS OPTIONS" else "MORE OPTIONS",
                            color = accent,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = if (showMoreSection) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                AnimatedVisibility(
                    visible = showMoreSection,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            IconFeatureTile(
                                icon = Icons.Rounded.Speed,
                                label = "Smooth FPS",
                                active = settings.sustainedPerformanceEnabled,
                                glowColor = EmeraldGlow,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    onAction(MagicSidebarAction.ToggleSustainedPerformance(!settings.sustainedPerformanceEnabled))
                                }
                            )
                            IconFeatureTile(
                                icon = Icons.Rounded.Schedule,
                                label = "24h Clock",
                                active = settings.use24HourClock,
                                glowColor = BalancedCyanGlow,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    onAction(MagicSidebarAction.Toggle24HourClock(!settings.use24HourClock))
                                }
                            )
                            IconFeatureTile(
                                icon = Icons.Rounded.Hearing,
                                label = "Mic Monitor",
                                active = settings.liveMicMonitorEnabled,
                                glowColor = HeavyVioletAccent,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    onAction(MagicSidebarAction.ToggleLiveMicMonitor(!settings.liveMicMonitorEnabled))
                                }
                            )
                        }

                        CompactSectionCard(title = "VOICE CHAT & MIC DSP", accent = HeavyPurpleGlow) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                MiniPillButton(
                                    label = if (smartVoiceChatMicOnly) "Voice Chat Only: ON" else "Always On Mic",
                                    tint = if (smartVoiceChatMicOnly) EmeraldGlow else WarningAmberGlow,
                                    highlighted = smartVoiceChatMicOnly
                                ) {
                                    onAction(MagicSidebarAction.ToggleSmartVoiceMic(!smartVoiceChatMicOnly))
                                }
                            }
                            VoicePreset.entries.chunked(2).forEach { pair ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    pair.forEach { preset ->
                                        val selected = settings.voicePreset == preset
                                        Surface(
                                            color = if (selected) HeavyFillDeep else ObsidianGreenBg,
                                            shape = RoundedCornerShape(6.dp),
                                            border = BorderStroke(0.75.dp, if (selected) HeavyPurpleGlow else ObsidianDivider),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { onAction(MagicSidebarAction.SelectVoicePreset(preset)) }
                                        ) {
                                            Box(Modifier.padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = preset.label,
                                                    fontSize = 9.sp,
                                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (selected) Color.White else MagicTextSecondary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (settings.crosshairEnabled) {
                            CompactSectionCard(title = "CROSSHAIR SETUP", accent = EmeraldGlow) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    MiniPillButton(settings.crosshairStyle.label, EmeraldGlow, true) {
                                        onAction(MagicSidebarAction.CycleCrosshairStyle)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        MiniPillButton("-", MagicTextSecondary) { onAction(MagicSidebarAction.AdjustCrosshairSize(-2)) }
                                        MiniPillButton("+", MagicTextSecondary) { onAction(MagicSidebarAction.AdjustCrosshairSize(2)) }
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    crosshairColors.forEach { hex ->
                                        val picked = settings.crosshairColorHex == hex
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(Color(hex))
                                                .border(if (picked) 1.6.dp else 0.6.dp, if (picked) Color.White else ObsidianDivider, CircleShape)
                                                .clickable { onAction(MagicSidebarAction.SetCrosshairColor(hex)) }
                                        )
                                    }
                                }
                            }
                        }

                        CompactSectionCard(title = "THERMAL & BATTERY ALERTS", accent = IceBlueGlow) {
                            if (settings.iceCoolingEnabled && settings.coolingBaselineTempC > 0f) {
                                val drop = (settings.coolingBaselineTempC - sysStats.batteryTempC).coerceAtLeast(0f)
                                Text(
                                    text = if (drop > 0.05f) "Ice-Cooling Active (-${"%.1f".format(drop)}°C)" else "Ice-Cooling Throttling Active",
                                    fontSize = 9.sp,
                                    color = IceBlueGlow,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Heat Alert", fontSize = 9.sp, color = MagicTextSecondary)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf(41f to "41°C", 43f to "43°C").forEach { (temp, lbl) ->
                                        val active = settings.overheatThresholdC == temp
                                        MiniPillButton(lbl, if (active) UltraCrimsonGlow else MagicTextSecondary, active) {
                                            onAction(MagicSidebarAction.SetOverheatThreshold(temp))
                                        }
                                    }
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Low Bat", fontSize = 9.sp, color = MagicTextSecondary)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf(15 to "15%", 20 to "20%").forEach { (pct, lbl) ->
                                        val active = settings.lowBatteryThresholdPct == pct
                                        MiniPillButton(lbl, if (active) WarningAmberGlow else MagicTextSecondary, active) {
                                            onAction(MagicSidebarAction.SetLowBatteryThreshold(pct))
                                        }
                                    }
                                }
                            }
                        }

                        CompactSectionCard(title = "PING SHIELD", accent = BalancedCyanGlow) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${netStats.transport.label} • ${netStats.signalBars}/4 (${netStats.rssiDbm}dBm)",
                                    fontSize = 8.5.sp,
                                    color = if (netStats.isWarningActive) WarningAmberGlow else EmeraldGlow
                                )
                                MiniPillButton(
                                    label = if (settings.pingShieldEnabled) "Alert ON" else "Alert OFF",
                                    tint = if (settings.pingShieldEnabled) BalancedCyanGlow else MagicTextSecondary,
                                    highlighted = settings.pingShieldEnabled
                                ) {
                                    onAction(MagicSidebarAction.TogglePingShield(!settings.pingShieldEnabled))
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(80, 120, 180).forEach { limit ->
                                    val active = settings.pingAlertThresholdMs == limit
                                    Surface(
                                        color = if (active) BalancedFillDeep else ObsidianGreenBg,
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(0.7.dp, if (active) BalancedCyanGlow else ObsidianDivider),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { onAction(MagicSidebarAction.SetPingThreshold(limit)) }
                                    ) {
                                        Box(Modifier.padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                                            Text("${limit}ms", fontSize = 8.sp, color = if (active) Color.White else MagicTextSecondary, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        CompactSectionCard(title = "SIDEBAR SETUP", accent = EmeraldGlow) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Edge Side", fontSize = 9.sp, color = MagicTextSecondary)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    MiniPillButton("L", if (settings.edgeOnLeft) EmeraldGlow else MagicTextSecondary, settings.edgeOnLeft) {
                                        onAction(MagicSidebarAction.SetEdgeOnLeft(true))
                                    }
                                    MiniPillButton("R", if (!settings.edgeOnLeft) EmeraldGlow else MagicTextSecondary, !settings.edgeOnLeft) {
                                        onAction(MagicSidebarAction.SetEdgeOnLeft(false))
                                    }
                                }
                            }
                            Text("Icon Size (${"%.1f".format(settings.edgeSizeScale)}x)", fontSize = 9.sp, color = MagicTextSecondary)
                            Slider(
                                value = settings.edgeSizeScale,
                                onValueChange = { onAction(MagicSidebarAction.SetEdgeSize(it)) },
                                valueRange = 0.8f..1.4f,
                                colors = SliderDefaults.colors(thumbColor = EmeraldGlow, activeTrackColor = EmeraldGlow),
                                modifier = Modifier.height(22.dp)
                            )
                            Text("Opacity (${(settings.edgeOpacity * 100).toInt()}%)", fontSize = 9.sp, color = MagicTextSecondary)
                            Slider(
                                value = settings.edgeOpacity,
                                onValueChange = { onAction(MagicSidebarAction.SetEdgeOpacity(it)) },
                                valueRange = 0.35f..1.0f,
                                colors = SliderDefaults.colors(thumbColor = EmeraldGlow, activeTrackColor = EmeraldGlow),
                                modifier = Modifier.height(22.dp)
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                MiniPillButton(
                                    label = if (settings.soundEffectsEnabled) "Sounds: ON" else "Sounds: OFF",
                                    tint = if (settings.soundEffectsEnabled) EmeraldGlow else MagicTextSecondary,
                                    highlighted = settings.soundEffectsEnabled
                                ) {
                                    onAction(MagicSidebarAction.ToggleSoundEffects(!settings.soundEffectsEnabled))
                                }
                                MiniPillButton(
                                    label = if (settings.fastAnimationsEnabled) "Fast Anim: ON" else "Fast Anim: OFF",
                                    tint = if (settings.fastAnimationsEnabled) BalancedCyanGlow else MagicTextSecondary,
                                    highlighted = settings.fastAnimationsEnabled
                                ) {
                                    onAction(MagicSidebarAction.ToggleFastAnimations(!settings.fastAnimationsEnabled))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun IconFeatureTile(
    icon: ImageVector,
    label: String,
    active: Boolean,
    glowColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val tileModifier = if (active) {
        modifier.darkNeonGlow(glowColor, cornerRadius = 11.dp, glowAlpha = 0.42f, strokeWidth = 1.dp)
    } else {
        modifier
    }

    Surface(
        color = if (active) glowColor.copy(alpha = 0.18f) else ObsidianCard.copy(alpha = 0.9f),
        shape = RoundedCornerShape(11.dp),
        border = BorderStroke(
            width = if (active) 1.dp else 0.7.dp,
            color = if (active) glowColor else ObsidianDivider
        ),
        modifier = tileModifier.clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 7.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(27.dp)
                    .clip(CircleShape)
                    .background(if (active) glowColor.copy(alpha = 0.25f) else ObsidianGreenBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (active) glowColor else MagicTextSecondary,
                    modifier = Modifier.size(15.dp)
                )
            }
            Text(
                text = label,
                color = if (active) Color.White else MagicTextSecondary,
                fontSize = 8.5.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun TelemetryHeaderBlock(
    settings: MagicSettings,
    clockText: String,
    sysStats: SystemTelemetry,
    netStats: NetworkTelemetry,
    accent: Color,
    onClose: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = when {
                        settings.iceCoolingEnabled -> "ICE-COOLING PRO"
                        settings.performanceMode == PerformanceMode.ULTRA -> "ULTRA MAGIC PRO"
                        settings.performanceMode == PerformanceMode.HEAVY -> "HEAVY MAGIC PRO"
                        else -> "GAME MAGIC PRO"
                    },
                    color = accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.9.sp
                )
                Text(
                    text = clockText.ifEmpty { "--:--:--" },
                    color = MagicTextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Box(
                modifier = Modifier
                    .size(23.dp)
                    .clip(CircleShape)
                    .background(ObsidianCard)
                    .border(0.75.dp, accent.copy(alpha = 0.5f), CircleShape)
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Close Drawer", tint = MagicTextSecondary, modifier = Modifier.size(13.dp))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(ObsidianGreenBg.copy(alpha = 0.9f))
                .border(0.85.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                .padding(horizontal = 7.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MiniStat("${sysStats.liveFps} FPS", EmeraldGlow)
                MiniStat("RAM ${sysStats.ramUsagePercent}%", BalancedCyanGlow)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val tempColor = if (sysStats.batteryTempC >= settings.overheatThresholdC) UltraCrimsonGlow else IceBlueGlow
                MiniStat("${"%.1f".format(sysStats.batteryTempC)}°C • ${sysStats.batteryPercent}%", tempColor)
                val pingColor = when {
                    netStats.pingMs < 0 -> MagicTextSecondary
                    netStats.pingMs < settings.pingAlertThresholdMs -> EmeraldGlow
                    else -> UltraCrimsonGlow
                }
                MiniStat(if (netStats.pingMs >= 0) "${netStats.pingMs}ms" else "--", pingColor)
            }
        }
    }
}

@Composable
private fun PerformanceModePillGrid(
    settings: MagicSettings,
    onSelectMode: (PerformanceMode) -> Unit,
    onToggleIceCooling: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            ModeThemePill(
                label = "Balanced",
                icon = Icons.Rounded.Eco,
                selected = !settings.iceCoolingEnabled && settings.performanceMode == PerformanceMode.BALANCED,
                fillColor = BalancedFillDark,
                glowColor = BalancedCyanGlow,
                modifier = Modifier.weight(1f),
                onClick = { onSelectMode(PerformanceMode.BALANCED) }
            )
            ModeThemePill(
                label = "Heavy",
                icon = Icons.Rounded.LocalFireDepartment,
                selected = !settings.iceCoolingEnabled && settings.performanceMode == PerformanceMode.HEAVY,
                fillColor = HeavyFillDark,
                glowColor = HeavyPurpleGlow,
                modifier = Modifier.weight(1f),
                onClick = { onSelectMode(PerformanceMode.HEAVY) }
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            ModeThemePill(
                label = "Ultra",
                icon = Icons.Rounded.Whatshot,
                selected = !settings.iceCoolingEnabled && settings.performanceMode == PerformanceMode.ULTRA,
                fillColor = UltraFillDark,
                glowColor = UltraCrimsonGlow,
                modifier = Modifier.weight(1f),
                onClick = { onSelectMode(PerformanceMode.ULTRA) }
            )
            ModeThemePill(
                label = "Ice-Cool",
                icon = Icons.Rounded.AcUnit,
                selected = settings.iceCoolingEnabled,
                fillColor = IceFillDark,
                glowColor = IceBlueGlow,
                modifier = Modifier.weight(1f),
                onClick = onToggleIceCooling
            )
        }
    }
}

@Composable
private fun ModeThemePill(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    fillColor: Color,
    glowColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val pillMod = if (selected) {
        modifier.darkNeonGlow(glowColor, cornerRadius = 8.dp, glowAlpha = 0.38f, strokeWidth = 0.95.dp)
    } else {
        modifier
    }

    Surface(
        color = if (selected) fillColor else ObsidianCard,
        shape = RoundedCornerShape(8.dp),
        border = if (!selected) BorderStroke(0.65.dp, ObsidianDivider) else null,
        modifier = pillMod
            .testTag("drawer_mode_${label.lowercase()}")
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) glowColor else MagicTextSecondary,
                modifier = Modifier.size(12.dp)
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = label,
                color = if (selected) glowColor else MagicTextSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MiniStat(text: String, color: Color) {
    Text(text = text, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
}

@Composable
private fun CompactSectionCard(
    title: String,
    accent: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(ObsidianCard.copy(alpha = 0.92f))
            .border(0.75.dp, accent.copy(alpha = 0.42f), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = title,
            color = accent,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.7.sp
        )
        content()
    }
}

@Composable
private fun MiniPillButton(
    label: String,
    tint: Color,
    highlighted: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        color = if (highlighted) tint.copy(alpha = 0.22f) else ObsidianGreenBg,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(0.75.dp, if (highlighted) tint else ObsidianDivider),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            color = tint,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
        )
    }
} 
