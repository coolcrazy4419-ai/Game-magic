package com.example

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.RedMagicEngine
import com.example.core.TurboSettingsStore
import com.example.service.GameTurboOverlayService
import com.example.ui.components.CyberSoundPlayer
import com.example.ui.theme.GamingTurboTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledAppItem(
    val name: String,
    val pkg: String,
    val icon: Drawable?
)

class MainActivity : ComponentActivity() {

    private val recordLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        val svcIntent = Intent(this, GameTurboOverlayService::class.java).apply {
            action = GameTurboOverlayService.ACTION_RECORD_PROJECTION
            putExtra(GameTurboOverlayService.EXTRA_RESULT_CODE, res.resultCode)
            putExtra(GameTurboOverlayService.EXTRA_RESULT_DATA, res.data)
        }
        startService(svcIntent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TurboSettingsStore.init(applicationContext)
        RedMagicEngine.init(applicationContext)

        if (intent?.getBooleanExtra("START_RECORDING", false) == true) {
            requestScreenCapture()
        }

        setContent {
            GamingTurboTheme {
                CleanGameMagicHome(
                    onRequestRecord = { requestScreenCapture() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("START_RECORDING", false)) {
            requestScreenCapture()
        }
    }

    private fun requestScreenCapture() {
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        recordLauncher.launch(mpm.createScreenCaptureIntent())
    }
}

private fun updateSelectedGamesSafe(context: Context, updated: Set<String>) {
    val prefsNames = listOf("turbo_settings_prefs", "game_turbo_prefs", "red_magic_prefs")
    for (name in prefsNames) {
        context.getSharedPreferences(name, Context.MODE_PRIVATE)
            .edit()
            .putStringSet("selected_packages", updated)
            .apply()
    }
    TurboSettingsStore.init(context.applicationContext)
}

@Composable
private fun CleanGameMagicHome(
    onRequestRecord: () -> Unit
) {
    val context = LocalContext.current
    val settingsState by TurboSettingsStore.state.collectAsStateWithLifecycle()

    var localSelected by remember { mutableStateOf(settingsState.selectedPackages) }
    LaunchedEffect(settingsState.selectedPackages) {
        if (settingsState.selectedPackages.isNotEmpty()) {
            localSelected = settingsState.selectedPackages
        }
    }

    var overlayEnabled by remember { mutableStateOf(GameTurboOverlayService.isRunning || Settings.canDrawOverlays(context)) }
    var showAddGameDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var installedApps by remember { mutableStateOf<List<InstalledAppItem>>(emptyList()) }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Toast.makeText(
            context,
            if (granted) "Mic Ready for Voice FX" else "Mic permission required for Voice FX",
            Toast.LENGTH_SHORT
        ).show()
    }

    LaunchedEffect(Unit) {
        installedApps = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 && it.packageName != context.packageName }
                .mapNotNull { appInfo ->
                    pm.getLaunchIntentForPackage(appInfo.packageName) ?: return@mapNotNull null
                    InstalledAppItem(
                        name = pm.getApplicationLabel(appInfo).toString(),
                        pkg = appInfo.packageName,
                        icon = try { pm.getApplicationIcon(appInfo) } catch (_: Exception) { null }
                    )
                }
                .sortedBy { it.name.lowercase() }
        }
    }

    LaunchedEffect(overlayEnabled) {
        if (overlayEnabled && Settings.canDrawOverlays(context)) {
            val startIntent = Intent(context, GameTurboOverlayService::class.java).apply {
                action = GameTurboOverlayService.ACTION_START
            }
            ContextCompat.startForegroundService(context, startIntent)
        }
    }

    val neonCyan = Color(0xFF00E5FF)
    val neonPurple = Color(0xFFD500F9)
    val neonGreen = Color(0xFF00E676)
    val bgDark = Color(0xFF060911)
    val cardDark = Color(0xFF0E1422)

    val addedGames = remember(installedApps, localSelected) {
        installedApps.filter { localSelected.contains(it.pkg) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgDark)
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(cardDark)
                        .border(1.dp, neonCyan.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                        .clickable {
                            CyberSoundPlayer.playClickSound(false)
                            showSettingsDialog = true
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "⚙ SETTINGS",
                        color = neonCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(cardDark)
                        .border(
                            width = 2.dp,
                            brush = Brush.linearGradient(listOf(neonCyan, neonPurple, Color(0xFFFF551A))),
                            shape = RoundedCornerShape(20.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⚡",
                        fontSize = 32.sp,
                        color = neonCyan
                    )
                }

                Text(
                    text = "GAME MAGIC",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "AI CYBER ENGINE • ZERO LAG OVERLAY",
                    color = neonCyan.copy(alpha = 0.8f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(cardDark)
                    .border(
                        width = 1.2.dp,
                        color = if (overlayEnabled) neonCyan else Color(0xFF1E293B),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Game Overlay",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (overlayEnabled) "Active • Only in Added Games" else "Disabled",
                        color = if (overlayEnabled) neonGreen else Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }

                Switch(
                    checked = overlayEnabled,
                    onCheckedChange = { wantEnable ->
                        CyberSoundPlayer.playClickSound(wantEnable)
                        if (wantEnable) {
                            if (!Settings.canDrawOverlays(context)) {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                )
                            } else {
                                overlayEnabled = true
                                val intent = Intent(context, GameTurboOverlayService::class.java).apply {
                                    action = GameTurboOverlayService.ACTION_START
                                }
                                ContextCompat.startForegroundService(context, intent)
                            }
                        } else {
                            overlayEnabled = false
                            val stopIntent = Intent(context, GameTurboOverlayService::class.java).apply {
                                action = GameTurboOverlayService.ACTION_STOP
                            }
                            context.startService(stopIntent)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = neonCyan
                    )
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(cardDark)
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(18.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "My Games (${addedGames.size})",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tap game to Boost & Launch",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.5.sp
                    )
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(addedGames, key = { it.pkg }) { game ->
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF151D30))
                                .border(1.dp, neonCyan.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                                .clickable {
                                    CyberSoundPlayer.playLaunchTurbineSound()
                                    if (!Settings.canDrawOverlays(context)) {
                                        Toast.makeText(context, "Enable Game Overlay first", Toast.LENGTH_SHORT).show()
                                        return@clickable
                                    }
                                    overlayEnabled = true
                                    val svc = Intent(context, GameTurboOverlayService::class.java).apply {
                                        action = GameTurboOverlayService.ACTION_FOREGROUND_CHANGED
                                        putExtra(GameTurboOverlayService.EXTRA_PACKAGE_NAME, game.pkg)
                                        putExtra(GameTurboOverlayService.EXTRA_IS_GAME_ACTIVE, true)
                                    }
                                    ContextCompat.startForegroundService(context, svc)
                                    context.packageManager.getLaunchIntentForPackage(game.pkg)?.let { launch ->
                                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(launch)
                                    }
                                }
                                .padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (game.icon != null) {
                                Image(
                                    bitmap = game.icon.toBitmap(96, 96).asImageBitmap(),
                                    contentDescription = game.name,
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(neonCyan.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("🎮", fontSize = 20.sp)
                                }
                            }
                            Text(
                                text = game.name,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    item {
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF121829))
                                .border(1.dp, neonPurple.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                                .clickable {
                                    CyberSoundPlayer.playClickSound(false)
                                    showAddGameDialog = true
                                }
                                .padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(neonPurple.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("+", color = neonPurple, fontSize = 24.sp, fontWeight = FontWeight.Black)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Add Game",
                                color = neonPurple,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddGameDialog) {
        Dialog(onDismissRequest = { showAddGameDialog = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.78f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(cardDark)
                    .border(1.2.dp, neonCyan, RoundedCornerShape(18.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Select Games", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = "DONE",
                        color = neonCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.clickable { showAddGameDialog = false }
                    )
                }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(installedApps, key = { it.pkg }) { app ->
                        val isSelected = localSelected.contains(app.pkg)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) neonCyan.copy(alpha = 0.16f) else Color(0xFF151D30))
                                .clickable {
                                    CyberSoundPlayer.playClickSound(false)
                                    val updated = if (isSelected) {
                                        localSelected - app.pkg
                                    } else {
                                        localSelected + app.pkg
                                    }
                                    localSelected = updated
                                    updateSelectedGamesSafe(context, updated)
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = app.name,
                                color = Color.White,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (isSelected) "ADDED ✓" else "+ ADD",
                                color = if (isSelected) neonGreen else neonCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSettingsDialog) {
        val hasUsage = hasUsageStatsPermission(context)
        val hasOverlay = Settings.canDrawOverlays(context)
        val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        Dialog(onDismissRequest = { showSettingsDialog = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(cardDark)
                    .border(1.2.dp, neonCyan, RoundedCornerShape(18.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Simple Settings", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = "CLOSE",
                        color = neonCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.clickable { showSettingsDialog = false }
                    )
                }

                SettingActionRow(
                    title = "Display Over Other Apps",
                    status = if (hasOverlay) "GRANTED" else "TAP TO ENABLE",
                    granted = hasOverlay
                ) {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                    )
                }

                SettingActionRow(
                    title = "Auto-Detect Direct Game Launch",
                    status = if (hasUsage) "GRANTED" else "TAP TO ENABLE",
                    granted = hasUsage
                ) {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }

                SettingActionRow(
                    title = "Microphone (Voice FX)",
                    status = if (hasMic) "GRANTED" else "TAP TO ALLOW",
                    granted = hasMic
                ) {
                    micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }

                SettingActionRow(
                    title = "Screen Recorder Setup",
                    status = "READY",
                    granted = true
                ) {
                    showSettingsDialog = false
                    onRequestRecord()
                }
            }
        }
    }
}

@Composable
private fun SettingActionRow(
    title: String,
    status: String,
    granted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF151D30))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(
            text = status,
            color = if (granted) Color(0xFF00E676) else Color(0xFFFF551A),
            fontSize = 10.sp,
            fontWeight = FontWeight.Black
        )
    }
}

private fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    }
    return mode == AppOpsManager.MODE_ALLOWED
}
