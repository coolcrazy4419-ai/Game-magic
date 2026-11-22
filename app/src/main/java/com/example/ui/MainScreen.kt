package com.example.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.*
import com.example.ui.viewmodel.TurboViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val BgBlack = Color(0xFF070709)
private val CardDark = Color(0xFF101015)
private val BorderOrange = Color(0xFFFF551A)
private val BorderCrimson = Color(0xFFFF2A3C)
private val TextWhite = Color(0xFFF5F5F7)
private val TextGray = Color(0xFF9494A3)

@Composable
fun MainScreen(viewModel: TurboViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val addedApps by viewModel.addedApps.collectAsStateWithLifecycle()
    val allApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val perms by viewModel.permissions.collectAsStateWithLifecycle()
    val banner by viewModel.statusBanner.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        RedMagicEngine.init(ctx)
        delay(250L) // Instant 0ms UI render first, then sample hardware
        while (true) {
            RedMagicEngine.refreshHardware(ctx)
            delay(3500L)
        }
    }

    val rm by RedMagicEngine.state.collectAsStateWithLifecycle()
    val hw by RedMagicEngine.hardware.collectAsStateWithLifecycle()
    var showAppPicker by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(BgBlack)) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 28.dp)
        ) {
            // 1. Top Bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "GAME TURBO",
                        color = BorderOrange,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp
                    )
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(CardDark)
                            .border(1.dp, BorderOrange.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("${"%.0f".format(hw.tempC)}°C", color = BorderOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("${hw.batteryPct}%", color = Color(0xFF00E676), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (!perms.overlayGranted || !perms.accessibilityGranted) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x26FF2A3C))
                            .border(1.dp, BorderCrimson, RoundedCornerShape(12.dp))
                            .clickable {
                                if (!perms.overlayGranted) {
                                    ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                                } else {
                                    ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                }
                            }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (!perms.overlayGranted) "Tap to allow Display Over Apps" else "Tap to enable Game Magic Accessibility",
                            color = TextWhite,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(Icons.Rounded.ArrowForward, contentDescription = null, tint = BorderCrimson, modifier = Modifier.size(16.dp))
                    }
                }
            }

            // 2. Device Specs + Glowing TURBO LAUNCH Ring
            item {
                RmSectionCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(hw.deviceName, color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(hw.androidVersion, color = TextGray, fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SpecCell("Chipset", hw.chipset, Modifier.weight(1f))
                        SpecCell("RAM", "${hw.totalRamGb} GB", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SpecCell("Storage", "${hw.totalStorageGb} GB", Modifier.weight(1f))
                        SpecCell("Display", "${hw.refreshRateHz} Hz", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(14.dp))
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(116.dp)
                                .clip(CircleShape)
                                .background(Brush.radialGradient(listOf(BorderOrange.copy(alpha = 0.28f), CardDark)))
                                .border(2.5.dp, BorderOrange, CircleShape)
                                .clickable {
                                    scope.launch {
                                        SystemTelemetryEngine.boostMemory(ctx, aggressive = false)
                                        RedMagicEngine.refreshHardware(ctx)
                                        viewModel.showTransientMessage("Turbo Boosted • ${addedApps.firstOrNull()?.name ?: "Ready"}")
                                        addedApps.firstOrNull()?.let { viewModel.launchGameNow(it) }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("TURBO", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                                Text("LAUNCH", color = BorderOrange, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 3. Performance Mode
            item {
                RmSectionTitle("Performance mode")
                RmSectionCard {
                    val modes = RedMagicPerfMode.entries
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PerfModeChip(modes[0], rm.perfMode == modes[0], Modifier.weight(1f)) {
                                RedMagicEngine.update(ctx) { it.copy(perfMode = modes[0]) }
                            }
                            PerfModeChip(modes[1], rm.perfMode == modes[1], Modifier.weight(1f)) {
                                RedMagicEngine.update(ctx) { it.copy(perfMode = modes[1]) }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PerfModeChip(modes[2], rm.perfMode == modes[2], Modifier.weight(1f)) {
                                RedMagicEngine.update(ctx) { it.copy(perfMode = modes[2]) }
                            }
                            PerfModeChip(modes[3], rm.perfMode == modes[3], Modifier.weight(1f), isCrimson = true) {
                                RedMagicEngine.update(ctx) { it.copy(perfMode = modes[3]) }
                            }
                        }
                    }
                }
            }

            // 4. Live Stats
            item {
                RmSectionTitle("Live stats")
                RmSectionCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LiveStatBox("FPS", "${hw.fps}", (hw.fps / 120f).coerceIn(0.2f, 1f), BorderOrange, Modifier.weight(1f))
                            LiveStatBox("Ping", "${hw.pingMs} ms", (1f - hw.pingMs / 150f).coerceIn(0.2f, 1f), Color(0xFF00E676), Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LiveStatBox("CPU", "${hw.cpuPercent}%", hw.cpuPercent / 100f, BorderOrange, Modifier.weight(1f))
                            LiveStatBox("GPU", "${hw.gpuPercent}%", hw.gpuPercent / 100f, BorderOrange, Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LiveStatBox("RAM", "${"%.1f".format(hw.ramUsedGb)} GB", (hw.ramUsedGb / hw.totalRamGb).coerceIn(0.1f, 1f), BorderOrange, Modifier.weight(1f))
                            LiveStatBox("Temp", "${"%.0f".format(hw.tempC)}°C", (hw.tempC / 50f).coerceIn(0.2f, 1f), if (hw.tempC >= 41f) BorderCrimson else BorderOrange, Modifier.weight(1f))
                        }
                    }
                }
            }

            // 5. Boost Tools
            item {
                RmSectionTitle("Boost tools")
                RmSectionCard {
                    val tools = listOf("Clean CPU", "Clean GPU", "Clean RAM", "Network boost", "Close background", "Cool down")
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        tools.chunked(3).forEach { rowTools ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                rowTools.forEach { label ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(9.dp))
                                            .background(BgBlack)
                                            .border(0.9.dp, BorderOrange.copy(alpha = 0.7f), RoundedCornerShape(9.dp))
                                            .clickable {
                                                scope.launch {
                                                    val res = SystemTelemetryEngine.boostMemory(ctx, aggressive = false)
                                                    RedMagicEngine.refreshHardware(ctx)
                                                    viewModel.showTransientMessage("$label Done (+${res.freedMb} MB)")
                                                }
                                            }
                                            .padding(vertical = 9.dp, horizontal = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(label, color = TextWhite, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 6. Pro Gaming Filters (6 Best Filters + Sliders + HDR Boost)
            item {
                RmSectionTitle("Pro gaming filters")
                RmSectionCard {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        RedMagicFilter.entries.chunked(3).forEach { rowFilters ->
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                rowFilters.forEach { f ->
                                    val sel = rm.filter == f
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (sel) BorderOrange.copy(alpha = 0.25f) else BgBlack)
                                            .border(if (sel) 1.dp else 0.6.dp, if (sel) BorderOrange else TextGray.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                            .clickable { RedMagicEngine.update(ctx) { it.copy(filter = f) } }
                                            .padding(vertical = 7.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(f.label, color = if (sel) TextWhite else TextGray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        RmSliderRow("Sharpness", rm.sharpness) { v -> RedMagicEngine.update(ctx) { it.copy(sharpness = v) } }
                        RmSliderRow("Contrast", rm.contrast) { v -> RedMagicEngine.update(ctx) { it.copy(contrast = v) } }
                        RmSliderRow("Saturation", rm.saturation) { v -> RedMagicEngine.update(ctx) { it.copy(saturation = v) } }
                        RmSwitchRow("HDR Boost", rm.hdrBoost) { v -> RedMagicEngine.update(ctx) { it.copy(hdrBoost = v) } }
                    }
                }
            }

            // 7. Graphics
            item {
                RmSectionTitle("Graphics")
                RmSectionCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(60, 90, 120, 144).forEach { fps ->
                                val label = if (fps == 144) "Max" else "$fps"
                                val sel = rm.targetFps == fps
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (sel) BorderOrange.copy(alpha = 0.25f) else BgBlack)
                                        .border(1.dp, if (sel) BorderOrange else TextGray.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .clickable { RedMagicEngine.update(ctx) { it.copy(targetFps = fps) } }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(label, color = if (sel) TextWhite else TextGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Auto", "HD", "FHD", "Ultra").forEach { res ->
                                val sel = rm.resolutionLabel == res
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (sel) BorderOrange.copy(alpha = 0.25f) else BgBlack)
                                        .border(1.dp, if (sel) BorderOrange else TextGray.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .clickable { RedMagicEngine.update(ctx) { it.copy(resolutionLabel = res) } }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(res, color = if (sel) TextWhite else TextGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        RmSwitchRow("Shadows", rm.shadowsEnabled) { v -> RedMagicEngine.update(ctx) { it.copy(shadowsEnabled = v) } }
                        RmSwitchRow("Anti-aliasing", rm.antiAliasing) { v -> RedMagicEngine.update(ctx) { it.copy(antiAliasing = v) } }
                        RmSwitchRow("Smooth touch", rm.smoothTouch) { v -> RedMagicEngine.update(ctx) { it.copy(smoothTouch = v) } }
                    }
                }
            }

            // 8. Sound & Voice
            item {
                RmSectionTitle("Sound and voice")
                RmSectionCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            RedMagicSoundMode.entries.forEach { sm ->
                                val sel = rm.soundMode == sm
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (sel) BorderOrange.copy(alpha = 0.25f) else BgBlack)
                                        .border(1.dp, if (sel) BorderOrange else TextGray.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .clickable { RedMagicEngine.update(ctx) { it.copy(soundMode = sm) } }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(sm.label, color = if (sel) TextWhite else TextGray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        RmSwitchRow("Voice changer", rm.voiceChanger) { v -> RedMagicEngine.update(ctx) { it.copy(voiceChanger = v) } }
                        RmSwitchRow("Mic mute", rm.micMute) { v -> RedMagicEngine.update(ctx) { it.copy(micMute = v) } }
                    }
                }
            }

            // 9. My Games + Per-Game Profile
            item {
                RmSectionTitle("My games")
                RmSectionCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            addedApps.take(3).forEach { game ->
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(BgBlack)
                                        .border(0.9.dp, BorderOrange.copy(alpha = 0.65f), RoundedCornerShape(9.dp))
                                        .clickable { viewModel.launchGameNow(game) }
                                        .padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(game.name, color = TextWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(RedMagicEngine.getPlaytimeHoursText(ctx, game.packageName), color = BorderOrange, fontSize = 9.sp)
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .width(64.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(BorderOrange.copy(alpha = 0.16f))
                                    .border(1.dp, BorderOrange, RoundedCornerShape(9.dp))
                                    .clickable { showAppPicker = true }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("+ Add", color = BorderOrange, fontSize = 11.sp, fontWeight = FontWeight.Black)
                            }
                        }
                        RmSwitchRow("Profile per game", rm.profilePerGame) { v -> RedMagicEngine.update(ctx) { it.copy(profilePerGame = v) } }
                    }
                }
            }

            // 10. Alerts
            item {
                RmSectionTitle("Alerts")
                RmSectionCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = if (hw.batteryPct <= 20) "Battery low (${hw.batteryPct}%). Plug in charger." else "Thermal & Battery Guard Active (${"%.1f".format(hw.tempC)}°C)",
                            color = BorderOrange,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(BgBlack)
                                .border(0.8.dp, BorderOrange.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Ping test with live stats", color = TextGray, fontSize = 11.sp)
                            Text(
                                text = "Connected (${hw.pingMs} ms)",
                                color = Color(0xFF00E676),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable {
                                    scope.launch {
                                        NetworkMonitorEngine.sampleOnce(ctx)
                                        RedMagicEngine.refreshHardware(ctx)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        banner?.let { msg ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 22.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardDark)
                    .border(1.dp, BorderOrange, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(msg, color = TextWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (showAppPicker) {
            AlertDialog(
                onDismissRequest = { showAppPicker = false },
                containerColor = CardDark,
                title = { Text("Add / Remove Games", color = BorderOrange, fontWeight = FontWeight.Black) },
                text = {
                    LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                        items(allApps.size) { idx ->
                            val app = allApps[idx]
                            val isAdded = addedApps.any { it.packageName == app.packageName }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isAdded) viewModel.removeAppFromMagic(app.packageName, app.name)
                                        else viewModel.addAppToMagic(app.packageName, app.name)
                                    }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(app.name, color = TextWhite, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text(if (isAdded) "ADDED" else "+ ADD", color = if (isAdded) Color(0xFF00E676) else BorderOrange, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAppPicker = false }) {
                        Text("DONE", color = BorderOrange, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}

@Composable
private fun RmSectionTitle(text: String) {
    Text(text = text, color = BorderOrange, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
private fun RmSectionCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardDark)
            .border(1.dp, BorderOrange.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        content = content
    )
}

@Composable
private fun SpecCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(BgBlack)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, color = TextGray, fontSize = 9.sp)
        Text(value, color = TextWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PerfModeChip(mode: RedMagicPerfMode, selected: Boolean, modifier: Modifier = Modifier, isCrimson: Boolean = false, onClick: () -> Unit) {
    val accent = if (isCrimson) BorderCrimson else BorderOrange
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) accent.copy(alpha = 0.22f) else BgBlack)
            .border(1.1.dp, if (selected) accent else TextGray.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(mode.label, color = if (selected) TextWhite else TextGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LiveStatBox(label: String, value: String, progress: Float, barColor: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(BgBlack)
            .padding(8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = TextGray, fontSize = 9.5.sp)
            Text(value, color = TextWhite, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(5.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
            color = barColor,
            trackColor = Color(0xFF22222A)
        )
    }
}

@Composable
private fun RmSliderRow(label: String, value: Float, onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = TextGray, fontSize = 10.sp)
            Text("${(value * 100).toInt()}", color = TextWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.height(24.dp),
            colors = SliderDefaults.colors(thumbColor = BorderOrange, activeTrackColor = BorderOrange)
        )
    }
}

@Composable
private fun RmSwitchRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextWhite, fontSize = 11.sp)
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = BorderOrange)
        )
    }
}
