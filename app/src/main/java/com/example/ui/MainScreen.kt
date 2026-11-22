package com.example.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.InstalledAppInfo
import com.example.ui.components.CyberMagicLaunchOverlay
import com.example.ui.components.EmeraldHexGridBackground
import com.example.ui.components.SplashIntroOverlay
import com.example.ui.components.darkNeonGlow
import com.example.ui.screens.AppPickerScreen
import com.example.ui.theme.*
import com.example.ui.viewmodel.PermissionStatus
import com.example.ui.viewmodel.TurboViewModel

/**
 * Ultra-Clean Game Magic Home Screen:
 * Displays ONLY the user's added Games & Apps grid, a minimal "+ Add" button,
 * and a compact Permission Setup card (hidden automatically once required permissions are granted).
 * Zero performance toggles, telemetry, or customisation clutter on Home — all tools live in the
 * ultra-compact in-game slide-out sidebar.
 */
@Composable
fun MainScreen(viewModel: TurboViewModel = viewModel()) {
    val context = LocalContext.current
    val addedApps by viewModel.addedApps.collectAsStateWithLifecycle()
    val appsLoading by viewModel.appsLoading.collectAsStateWithLifecycle()
    val perms by viewModel.permissions.collectAsStateWithLifecycle()
    val bannerText by viewModel.statusBanner.collectAsStateWithLifecycle()

    var showSplash by rememberSaveable { mutableStateOf(true) }
    var showAppPicker by remember { mutableStateOf(false) }
    var launchingApp by remember { mutableStateOf<InstalledAppInfo?>(null) }

    val micPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshPermissions()
    }

    EmeraldHexGridBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (showAppPicker) {
                    AppPickerScreen(
                        viewModel = viewModel,
                        onBack = { showAppPicker = false }
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Minimal Brand Header + "+ Add" Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(11.dp))
                                        .background(
                                            Brush.linearGradient(listOf(ObsidianCard, HeavyFillDark))
                                        )
                                        .darkNeonGlow(EmeraldGlow, cornerRadius = 11.dp, glowAlpha = 0.34f, strokeWidth = 0.9.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoAwesome,
                                        contentDescription = "Game Magic",
                                        tint = EmeraldGlow,
                                        modifier = Modifier.size(21.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "GAME MAGIC",
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = MagicTextPrimary,
                                        letterSpacing = 1.4.sp
                                    )
                                    Text(
                                        text = if (addedApps.isEmpty()) "Add games to arm magic sidebar" else "${addedApps.size} Armed • Tap to launch",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MagicTextSecondary
                                    )
                                }
                            }

                            Surface(
                                color = EmeraldDeepGlow.copy(alpha = 0.22f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .height(42.dp)
                                    .darkNeonGlow(EmeraldGlow, cornerRadius = 12.dp, glowAlpha = 0.30f, strokeWidth = 0.9.dp)
                                    .testTag("home_add_button")
                                    .clickable { showAppPicker = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = "Add Apps", tint = EmeraldWireframe, modifier = Modifier.size(18.dp))
                                    Text(
                                        text = "Add",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MagicTextPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Compact Permission Setup Card — strictly hidden once required permissions are granted
                        AnimatedVisibility(visible = !perms.allCoreGranted) {
                            CompactPermissionSetupCard(
                                perms = perms,
                                onAllowOverlay = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                                        )
                                    }
                                },
                                onAllowAccessibility = {
                                    runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                                },
                                onAllowDnd = {
                                    runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
                                },
                                onAllowMic = {
                                    micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            )
                        }

                        // Added Games & Apps Grid (ONLY user-added apps)
                        if (appsLoading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = EmeraldGlow, strokeWidth = 2.5.dp)
                            }
                        } else if (addedApps.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    color = ObsidianCard.copy(alpha = 0.90f),
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .darkNeonGlow(EmeraldGlow, cornerRadius = 18.dp, glowAlpha = 0.25f, strokeWidth = 0.9.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(RoundedCornerShape(18.dp))
                                                .background(
                                                    Brush.linearGradient(listOf(BalancedFillDark, HeavyFillDark))
                                                )
                                                .darkNeonGlow(EmeraldGlow, cornerRadius = 18.dp, glowAlpha = 0.35f, strokeWidth = 0.95.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.SportsEsports,
                                                contentDescription = null,
                                                tint = EmeraldWireframe,
                                                modifier = Modifier.size(34.dp)
                                            )
                                        }
                                        Text(
                                            text = "Your Magic Library is Empty",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MagicTextPrimary
                                        )
                                        Text(
                                            text = "Add your favorite games and apps. Tapping any game launches it with the Cyber-Magic sequence and arms the ultra-compact in-game sidebar.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MagicTextSecondary,
                                            textAlign = TextAlign.Center,
                                            lineHeight = 18.sp
                                        )
                                        Button(
                                            onClick = { showAppPicker = true },
                                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGlow),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                                .testTag("home_empty_add_button")
                                        ) {
                                            Icon(Icons.Rounded.Add, contentDescription = null, tint = ObsidianGreenBg, modifier = Modifier.size(20.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = "ADD GAMES & APPS",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = ObsidianGreenBg,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 104.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(bottom = 18.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            ) {
                                items(addedApps, key = { it.packageName }) { app ->
                                    AddedGameTile(
                                        app = app,
                                        onLaunch = {
                                            viewModel.playEpicLaunchSound()
                                            launchingApp = app
                                        },
                                        onRemove = {
                                            viewModel.removeAppFromMagic(app.packageName, app.name)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Minimal Bottom Status Toast
                AnimatedVisibility(
                    visible = bannerText != null && launchingApp == null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    bannerText?.let { msg ->
                        Surface(
                            color = ObsidianSurface.copy(alpha = 0.96f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.darkNeonGlow(EmeraldGlow, cornerRadius = 12.dp, glowAlpha = 0.3f, strokeWidth = 0.85.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = EmeraldGlow, modifier = Modifier.size(16.dp))
                                Text(msg, style = MaterialTheme.typography.bodySmall, color = MagicTextPrimary)
                            }
                        }
                    }
                }
            }
        }

        // Full-Screen 900ms "Tagda Launch Sequence" Cyber-Magic Overlay
        launchingApp?.let { target ->
            CyberMagicLaunchOverlay(
                app = target,
                onLaunchComplete = {
                    val toLaunch = target
                    launchingApp = null
                    viewModel.launchGameNow(toLaunch)
                }
            )
        }

        // Sequential Intro Splash: Logo -> "GAME MAGIC" -> Home Screen
        if (showSplash) {
            SplashIntroOverlay(onFinished = { showSplash = false })
        }
    }
}

@Composable
private fun AddedGameTile(
    app: InstalledAppInfo,
    onLaunch: () -> Unit,
    onRemove: () -> Unit
) {
    val iconBitmap = remember(app.packageName) {
        runCatching { app.icon?.toBitmap(128, 128)?.asImageBitmap() }.getOrNull()
    }

    Surface(
        color = ObsidianCard.copy(alpha = 0.92f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .darkNeonGlow(
                glowColor = if (app.isGame) EmeraldGlow else HeavyPurpleGlow,
                cornerRadius = 16.dp,
                glowAlpha = 0.24f,
                strokeWidth = 0.85.dp
            )
            .testTag("game_tile_${app.name}")
            .clickable { onLaunch() }
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Subtle Top-Right Remove Pill
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(ObsidianGreenBg.copy(alpha = 0.78f))
                    .clickable { onRemove() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Remove ${app.name}",
                    tint = MagicTextSecondary,
                    modifier = Modifier.size(13.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(ObsidianGreenBg)
                        .darkNeonGlow(
                            glowColor = if (app.isGame) EmeraldDeepGlow else HeavyFillDeep,
                            cornerRadius = 14.dp,
                            glowAlpha = 0.25f,
                            strokeWidth = 0.75.dp
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (iconBitmap != null) {
                        Image(
                            bitmap = iconBitmap,
                            contentDescription = app.name,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.SportsEsports,
                            contentDescription = app.name,
                            tint = EmeraldGlow,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Text(
                    text = app.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MagicTextPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Surface(
                    color = EmeraldDeepGlow.copy(alpha = 0.24f),
                    shape = RoundedCornerShape(7.dp),
                    border = BorderStroke(0.7.dp, EmeraldGlow.copy(alpha = 0.7f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = EmeraldWireframe, modifier = Modifier.size(12.dp))
                        Text("PLAY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EmeraldWireframe)
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactPermissionSetupCard(
    perms: PermissionStatus,
    onAllowOverlay: () -> Unit,
    onAllowAccessibility: () -> Unit,
    onAllowDnd: () -> Unit,
    onAllowMic: () -> Unit
) {
    Surface(
        color = ObsidianCard.copy(alpha = 0.94f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .darkNeonGlow(HeavyPurpleGlow, cornerRadius = 14.dp, glowAlpha = 0.26f, strokeWidth = 0.85.dp)
            .testTag("permission_setup_card")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Icon(Icons.Rounded.Shield, contentDescription = null, tint = HeavyVioletAccent, modifier = Modifier.size(16.dp))
                Text(
                    text = "Quick Permission Setup",
                    style = MaterialTheme.typography.labelMedium,
                    color = MagicTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (!perms.overlayGranted) {
                    PermChip("Overlay", Modifier.weight(1f), onAllowOverlay)
                }
                if (!perms.accessibilityGranted) {
                    PermChip("Auto-Detect", Modifier.weight(1f), onAllowAccessibility)
                }
                if (!perms.dndGranted) {
                    PermChip("DND", Modifier.weight(1f), onAllowDnd)
                }
                if (!perms.micGranted) {
                    PermChip("Voice DSP", Modifier.weight(1f), onAllowMic)
                }
            }
        }
    }
}

@Composable
private fun PermChip(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        color = HeavyFillDark.copy(alpha = 0.85f),
        shape = RoundedCornerShape(9.dp),
        border = BorderStroke(0.8.dp, HeavyPurpleGlow),
        modifier = modifier
            .height(34.dp)
            .clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MagicTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
