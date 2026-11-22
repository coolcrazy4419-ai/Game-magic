package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.*
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
 * Ultra-Compact Game Magic Floating Drawer UI (`180.dp` width, max `64%` screen height).
 * Vertical scrollable column featuring:
 *  - Live Telemetry Header (Clock, FPS, RAM %, Temp °C & Bat %, Ping ms)
 *  - 1-Tap Turbo Boost button with expanding emerald/violet shockwave animation
 *  - 4-Theme Performance Mode Pill Selector (Balanced / Heavy / Ultra / Ice-Cooling)
 *  - Vertical swipe-up drawer with all independent ON/OFF gaming tools & sidebar setup.
 */
@Composable
fun CompactSwipeUpSidebar(
    settings: MagicSettings,
    sysStats: SystemTelemetry,
    netStats: NetworkTelemetry,
    boostShockwaveTick: Int,
    boostReadout: String?,
    onClose: () -> Unit,
    onAction: (MagicSidebarAction) -> Unit
) {
    var clockText by remember { mutableStateOf("") }
    LaunchedEffect(settings.use24HourClock) {
        val pattern = if (settings.use24HourClock) "HH:mm:ss" else "hh:mm:ss a"
        val fmt = SimpleDateFormat(pattern, Locale.getDefault())
        while (true) {
            clockText = fmt.format(Date())
            delay(1000L)
        }
    }

    // Smooth multi-ring shockwave animation triggered by 1-Tap Turbo Boost
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
        RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
    }

    val crosshairColors = remember {
        listOf(0xFF00E676L, 0xFF00E5FFL, 0xFFFF1744L, 0xFFAA00FFL, 0xFFFFAB00L)
    }

    Surface(
        shape = panelShape,
        color = Color.Transparent,
        modifier = Modifier
            .width(180.dp)
            .fillMaxHeight(0.64f)
            .darkNeonGlow(accent, cornerRadius = 16.dp, glowAlpha = 0.36f, strokeWidth = 0.95.dp)
            .testTag("compact_magic_drawer")
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {} // Consume inside taps so backdrop only collapses on outside taps
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            deepFill.copy(alpha = 0.95f),
                            ObsidianGreenBg.copy(alpha = 0.97f),
                            ObsidianSurface.copy(alpha = 0.97f)
                        )
                    )
                )
        ) {
            // Dual-Ring Emerald & Royal Violet Shockwave Animation across the compact drawer
            if (shockwave.value in 0.01f..0.99f) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val p = shockwave.value
                    val maxRadius = size.maxDimension * 0.95f
                    val origin = Offset(size.width / 2f, size.height * 0.24f)
                    val alpha = (1f - p).coerceIn(0f, 1f)

                    // Outer Emerald Shockwave
                    drawCircle(
                        color = EmeraldGlow.copy(alpha = alpha * 0.24f),
                        radius = p * maxRadius,
                        center = origin,
                        style = Stroke(width = 7.dp.toPx())
                    )
                    drawCircle(
                        color = EmeraldWireframe.copy(alpha = alpha * 0.85f),
                        radius = p * maxRadius,
                        center = origin,
                        style = Stroke(width = 2.2.dp.toPx())
                    )
                    // Inner Deep-Violet Secondary Wave
                    val p2 = ((p - 0.14f) / 0.86f).coerceIn(0f, 1f)
                    if (p2 > 0f) {
                        drawCircle(
                            color = HeavyPurpleGlow.copy(alpha = (1f - p2) * 0.78f),
                            radius = p2 * maxRadius * 0.76f,
                            center = origin,
                            style = Stroke(width = 2.dp.toPx())
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
                // ===== 1. LIVE TELEMETRY HEADER =====
                TelemetryHeaderBlock(
                    settings = settings,
                    clockText = clockText,
                    sysStats = sysStats,
                    netStats = netStats,
                    accent = accent,
                    onClose = onClose
                )

                // ===== 2. 1-TAP TURBO BOOST BUTTON =====
                TurboBoostActionButton(
                    boostReadout = boostReadout,
                    isFlashing = shockwave.value in 0.01f..0.45f,
                    onBoost = { onAction(MagicSidebarAction.TurboBoostNow) }
                )

                // ===== 3. 4-THEME PERFORMANCE MODE PILL SELECTOR (Balanced / Heavy / Ultra / Ice-Cooling) =====
                PerformanceModePillGrid(
                    settings = settings,
                    onSelectMode = { mode -> onAction(MagicSidebarAction.SetPerformanceMode(mode)) },
                    onToggleIceCooling = { onAction(MagicSidebarAction.ToggleIceCooling(!settings.iceCoolingEnabled)) }
                )

                // Subtle Vertical Scroll Cue
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = null, tint = accent.copy(alpha = 0.72f), modifier = Modifier.size(13.dp))
                    Text("SWIPE UP FOR TOOLS", fontSize = 8.sp, color = MagicTextSecondary, letterSpacing = 0.8.sp)
                    Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = null, tint = accent.copy(alpha = 0.72f), modifier = Modifier.size(13.dp))
                }

                // ===== 4. SWIPE-UP SECTION: LAG-FREE ENGINE & CLOCK =====
                CompactSectionCard(title = "LAG-FREE ENGINE", accent = EmeraldGlow) {
                    CompactToggleRow("Sustained Perf", settings.sustainedPerformanceEnabled, EmeraldGlow) {
                        onAction(MagicSidebarAction.ToggleSustainedPerformance(it))
                    }
                    CompactToggleRow("24h Clock", settings.use24HourClock, BalancedCyanGlow) {
                        onAction(MagicSidebarAction.Toggle24HourClock(it))
                    }
                }

                // ===== 5. SWIPE-UP SECTION: REAL-TIME VOICE / MIC CHANGER =====
                CompactSectionCard(title = "VOICE CHANGER DSP", accent = HeavyPurpleGlow) {
                    CompactToggleRow("Voice FX", settings.voiceChangerEnabled, HeavyPurpleGlow) {
                        onAction(MagicSidebarAction.ToggleVoiceChanger(it))
                    }
                    CompactToggleRow("Live Monitor", settings.liveMicMonitorEnabled, HeavyVioletAccent) {
                        onAction(MagicSidebarAction.ToggleLiveMicMonitor(it))
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

                // ===== 6. SWIPE-UP SECTION: PRO ONLINE GAMING TOOLS =====
                CompactSectionCard(title = "PRO GAMING TOOLS", accent = BalancedCyanGlow) {
                    CompactToggleRow("Crosshair", settings.crosshairEnabled, EmeraldGlow) {
                        onAction(MagicSidebarAction.ToggleCrosshair(it))
                    }
                    if (settings.crosshairEnabled) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MiniPillButton(settings.crosshairStyle.label, EmeraldGlow) {
                                onAction(MagicSidebarAction.CycleCrosshairStyle)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
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
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(Color(hex))
                                        .border(if (picked) 1.5.dp else 0.6.dp, if (picked) Color.White else ObsidianDivider, CircleShape)
                                        .clickable { onAction(MagicSidebarAction.SetCrosshairColor(hex)) }
                                )
                            }
                        }
                    }
                    CompactToggleRow("DND Blocker", settings.dndEnabled, UltraCrimsonGlow) {
                        onAction(MagicSidebarAction.ToggleDnd(it))
                    }
                    CompactToggleRow("Touch Guard", settings.touchGuardEnabled, IceBlueGlow) {
                        onAction(MagicSidebarAction.ToggleTouchGuard(it))
                    }
                    Surface(
                        color = BalancedFillDark.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(7.dp),
                        border = BorderStroke(0.75.dp, BalancedCyanGlow),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAction(MagicSidebarAction.TakeScreenshot) }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Screenshot, contentDescription = "Screenshot", tint = BalancedCyanGlow, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("SCREENSHOT", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MagicTextPrimary)
                        }
                    }
                }

                // ===== 7. SWIPE-UP SECTION: THERMAL & BATTERY ALERTS =====
                CompactSectionCard(title = "THERMAL & BATTERY", accent = IceBlueGlow) {
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

                // ===== 8. SWIPE-UP SECTION: EARLY NETWORK DROP WARNING & PING SHIELD =====
                CompactSectionCard(title = "PING SHIELD", accent = BalancedCyanGlow) {
                    CompactToggleRow("Drop Alert", settings.pingShieldEnabled, BalancedCyanGlow) {
                        onAction(MagicSidebarAction.TogglePingShield(it))
                    }
                    Text(
                        text = "${netStats.transport.label} • ${netStats.signalBars}/4 Bars (${netStats.rssiDbm}dBm)",
                        fontSize = 9.sp,
                        color = if (netStats.isWarningActive) WarningAmberGlow else EmeraldGlow
                    )
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

                // ===== 9. SWIPE-UP SECTION: SIDEBAR CUSTOMISATION =====
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
                    CompactToggleRow("Sounds", settings.soundEffectsEnabled, EmeraldGlow) {
                        onAction(MagicSidebarAction.ToggleSoundEffects(it))
                    }
                    CompactToggleRow("Fast Anim", settings.fastAnimationsEnabled, BalancedCyanGlow) {
                        onAction(MagicSidebarAction.ToggleFastAnimations(it))
                    }
                }
            }
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
                        settings.iceCoolingEnabled -> "ICE-COOLING"
                        settings.performanceMode == PerformanceMode.ULTRA -> "ULTRA MAGIC"
                        settings.performanceMode == PerformanceMode.HEAVY -> "HEAVY MAGIC"
                        else -> "GAME MAGIC"
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
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(ObsidianCard)
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Close Drawer", tint = MagicTextSecondary, modifier = Modifier.size(13.dp))
            }
        }

        // Crisp 2x2 Telemetry Strip (Live FPS, RAM %, Temp °C & Bat %, Live Ping ms)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(9.dp))
                .background(ObsidianGreenBg.copy(alpha = 0.88f))
                .border(0.75.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(9.dp))
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
private fun TurboBoostActionButton(
    boostReadout: String?,
    isFlashing: Boolean,
    onBoost: () -> Unit
) {
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .darkNeonGlow(
                glowColor = if (isFlashing) HeavyPurpleGlow else EmeraldGlow,
                cornerRadius = 10.dp,
                glowAlpha = if (isFlashing) 0.55f else 0.34f,
                strokeWidth = 0.95.dp
            )
            .testTag("drawer_turbo_boost_button")
            .clickable { onBoost() }
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        if (isFlashing) {
                            listOf(EmeraldGlow.copy(alpha = 0.55f), HeavyPurpleGlow.copy(alpha = 0.65f))
                        } else {
                            listOf(EmeraldDeepGlow.copy(alpha = 0.48f), HeavyFillDeep.copy(alpha = 0.65f))
                        }
                    )
                )
                .padding(vertical = 8.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Rounded.Bolt, contentDescription = "Turbo Boost", tint = EmeraldWireframe, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                text = boostReadout ?: "TURBO BOOST",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 2x2 Compact Pill Selector featuring all 4 signature Game Magic themes:
 * Balanced (Deep Sapphire / Dark Cyan), Heavy (Deep Royal Violet / Dark Purple),
 * Ultra (Deep Blood Crimson / Red-Orange), and Ice-Cooling (Deep Arctic Indigo / Ice-Blue).
 */
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
        modifier.darkNeonGlow(glowColor, cornerRadius = 8.dp, glowAlpha = 0.34f, strokeWidth = 0.9.dp)
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
            .background(ObsidianCard.copy(alpha = 0.9f))
            .border(0.7.dp, accent.copy(alpha = 0.38f), RoundedCornerShape(10.dp))
            .padding(horizontal = 7.dp, vertical = 6.dp),
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
private fun CompactToggleRow(
    label: String,
    checked: Boolean,
    accent: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MagicTextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        Box(
            modifier = Modifier
                .width(30.dp)
                .height(16.dp)
                .clip(CircleShape)
                .background(if (checked) accent.copy(alpha = 0.85f) else ObsidianGreenBg)
                .border(0.75.dp, if (checked) accent else ObsidianDivider, CircleShape)
                .padding(2.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (checked) ObsidianGreenBg else MagicTextSecondary)
            )
        }
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
        border = BorderStroke(0.7.dp, if (highlighted) tint else ObsidianDivider),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            color = tint,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}
