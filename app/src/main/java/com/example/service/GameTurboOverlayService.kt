package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.MainActivity
import com.example.R
import com.example.core.*
import com.example.ui.components.CompactSwipeUpSidebar
import com.example.ui.components.MagicSidebarAction
import com.example.ui.components.darkNeonGlow
import com.example.ui.components.resolveModeAccent
import com.example.ui.theme.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.roundToInt

/**
 * 100% Anti-Cheat Safe Ultra-Compact Floating Sidebar Overlay Service for Game Magic.
 * - Uses strictly non-intrusive WindowManager flags (FLAG_NOT_FOCUSABLE | FLAG_LAYOUT_IN_SCREEN | FLAG_HARDWARE_ACCELERATED).
 * - Crosshair adds FLAG_NOT_TOUCHABLE for 0ms passthrough.
 * - Smart Sleep Mode pauses Choreographer FPS monitoring and slows telemetry polling when collapsed.
 * - Ultra-compact drawer (~176dp wide, max 64% height) with vertical swipe-up scrolling for all tools.
 */
class GameTurboOverlayService : Service() {

    companion object {
        const val CHANNEL_ID = "game_magic_overlay_channel"
        const val NOTIFICATION_ID = 2027
        const val ACTION_START = "com.gamemagic.action.START"
        const val ACTION_STOP = "com.gamemagic.action.STOP"
        const val ACTION_FOREGROUND_CHANGED = "com.gamemagic.action.FOREGROUND_CHANGED"
        const val EXTRA_PACKAGE_NAME = "extra_pkg"
        const val EXTRA_IS_GAME_ACTIVE = "extra_is_game"

        private const val SAFE_OVERLAY_FLAGS =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

        @Volatile
        var isRunning = false
            private set
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var windowManager: WindowManager

    private var edgeTriggerView: ComposeView? = null
    private var edgeParams: WindowManager.LayoutParams? = null
    private var panelHostView: ComposeView? = null
    private var crosshairView: ComposeView? = null
    private var touchGuardTopView: ComposeView? = null
    private var touchGuardLeftView: ComposeView? = null
    private var touchGuardRightView: ComposeView? = null
    private var alertBannerView: ComposeView? = null

    private val isPanelExpanded = MutableStateFlow(false)
    private val liveTelemetry = MutableStateFlow(SystemTelemetry())
    private val liveAlertBanner = MutableStateFlow<Pair<String, Color>?>(null)
    private val boostShockwavePulse = MutableStateFlow(0)
    private val boostFreedReadout = MutableStateFlow<String?>(null)

    private var activeGamePackage: String? = null
    private var lastEdgeOnLeft: Boolean? = null
    private var lastAlertPingMs: Long = 0L
    private var telemetryJob: Job? = null
    private var fallbackMonitorJob: Job? = null
    private var settingsWatchJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        TurboSettingsStore.init(applicationContext)
        NetworkMonitorEngine.registerCallback(applicationContext)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        createNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        isRunning = true

        startSmartSleepTelemetryLoop()
        startFallbackUsageMonitor()
        observeSettings()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_FOREGROUND_CHANGED -> {
                val pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME)
                val isGame = intent.getBooleanExtra(EXTRA_IS_GAME_ACTIVE, false)
                handleForegroundUpdate(pkg, isGame)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Game Magic Engine",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps the ultra-compact Game Magic floating sidebar and guards active"
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_notification_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun handleForegroundUpdate(pkg: String?, isGameActive: Boolean) {
        if (isGameActive && pkg != null) {
            if (activeGamePackage != pkg) {
                activeGamePackage = pkg
                onGameEntered()
            }
        } else {
            if (activeGamePackage != null) {
                activeGamePackage = null
                onGameExited()
            }
        }
    }

    private fun onGameEntered() {
        val s = TurboSettingsStore.state.value
        if (s.dndEnabled) SystemTelemetryEngine.setDndEnabled(applicationContext, true)
        if (s.voiceChangerEnabled) {
            VoiceChangerEngine.start(applicationContext, s.voicePreset, s.liveMicMonitorEnabled)
        }
        mainHandler.post {
            showEdgeTrigger()
            syncAuxiliaryOverlays()
        }
    }

    private fun onGameExited() {
        val s = TurboSettingsStore.state.value
        if (s.dndEnabled) SystemTelemetryEngine.setDndEnabled(applicationContext, false)
        VoiceChangerEngine.stop()
        SystemTelemetryEngine.stopFpsMonitor()
        mainHandler.post { hideAllGameOverlays() }
    }

    /**
     * Smart Sleep Mode:
     * - When panel is EXPANDED: crisp 1500ms telemetry updates + Choreographer FPS active.
     * - When panel is COLLAPSED: FPS monitor paused, lightweight 5000ms (8000ms in Ice-Cooling) check
     *   for Overheat, Low Battery, and Early Network Drop Warning so the game keeps 100% CPU/GPU priority.
     */
    private fun startSmartSleepTelemetryLoop() {
        telemetryJob = serviceScope.launch {
            while (isActive) {
                val gameActive = activeGamePackage != null
                if (!gameActive) {
                    delay(3500L)
                    continue
                }

                val expanded = isPanelExpanded.value
                val s = TurboSettingsStore.state.value

                val sys = SystemTelemetryEngine.readTelemetry(applicationContext)
                liveTelemetry.value = sys

                val net = if (expanded || s.pingShieldEnabled) {
                    NetworkMonitorEngine.sampleOnce(applicationContext)
                } else {
                    NetworkMonitorEngine.telemetry.value
                }

                evaluateEarlyAlerts(sys, net, s)

                val sleepMs = when {
                    expanded -> 1500L
                    s.iceCoolingEnabled -> 8000L
                    else -> 5000L
                }
                delay(sleepMs)
            }
        }
    }

    private fun evaluateEarlyAlerts(sys: SystemTelemetry, net: NetworkTelemetry, s: MagicSettings) {
        val alert: Pair<String, Color>? = when {
            sys.batteryTempC >= s.overheatThresholdC ->
                "Overheat ${"%.1f".format(sys.batteryTempC)}°C • Enable Ice-Cool" to UltraCrimsonGlow
            !sys.isCharging && sys.batteryPercent in 1..s.lowBatteryThresholdPct ->
                "Low Battery ${sys.batteryPercent}% • Plug In" to WarningAmberGlow
            s.pingShieldEnabled && net.isWarningActive && net.warningMessage != null ->
                net.warningMessage!! to BalancedCyanGlow
            else -> null
        }

        liveAlertBanner.value = alert
        if (alert != null) {
            val now = System.currentTimeMillis()
            if (now - lastAlertPingMs > 18_000L) {
                lastAlertPingMs = now
                SoundManager.playAlertPing(applicationContext)
            }
            mainHandler.post { showTopEdgeAlertBanner() }
        } else {
            mainHandler.post { dismissTopEdgeAlertBanner() }
        }
    }

    private fun startFallbackUsageMonitor() {
        fallbackMonitorJob = serviceScope.launch {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            while (isActive) {
                delay(2500L)
                if (GameAccessibilityService.instance != null || usm == null) continue
                val selected = TurboSettingsStore.state.value.selectedPackages
                if (selected.isEmpty()) {
                    if (activeGamePackage != null) handleForegroundUpdate(null, false)
                    continue
                }
                val end = System.currentTimeMillis()
                val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, end - 8000L, end)
                val topPkg = stats?.maxByOrNull { it.lastTimeUsed }?.packageName
                if (topPkg != null && topPkg != packageName) {
                    handleForegroundUpdate(topPkg, selected.contains(topPkg))
                }
            }
        }
    }

    private fun observeSettings() {
        settingsWatchJob = serviceScope.launch {
            TurboSettingsStore.state.collect { s ->
                if (activeGamePackage != null) {
                    mainHandler.post {
                        syncAuxiliaryOverlays()
                        if (lastEdgeOnLeft != null && lastEdgeOnLeft != s.edgeOnLeft) {
                            repositionEdgeSide(s.edgeOnLeft)
                        }
                        lastEdgeOnLeft = s.edgeOnLeft
                    }
                }
            }
        }
    }

    private fun repositionEdgeSide(onLeft: Boolean) {
        val view = edgeTriggerView ?: return
        val lp = edgeParams ?: return
        lp.gravity = (if (onLeft) Gravity.START else Gravity.END) or Gravity.CENTER_VERTICAL
        runCatching { windowManager.updateViewLayout(view, lp) }
    }

    private fun syncAuxiliaryOverlays() {
        if (!Settings.canDrawOverlays(this)) return
        val s = TurboSettingsStore.state.value
        if (s.crosshairEnabled) showCrosshairOverlay() else dismissCrosshairOverlay()
        if (s.touchGuardEnabled) showTouchGuardOverlays() else dismissTouchGuardOverlays()
    }

    private fun createComposeView(content: @Composable () -> Unit): ComposeView {
        return ComposeView(this).apply {
            val owner = MyLifecycleOwner()
            owner.performRestore(null)
            owner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            owner.handleLifecycleEvent(Lifecycle.Event.ON_START)
            owner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(MyViewModelStoreOwner())
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                GamingTurboTheme {
                    content()
                }
            }
        }
    }

    /**
     * Tiny, translucent, vertically draggable glowing edge pill (auto-dims after 3s for Smart Sleep).
     */
    private fun showEdgeTrigger() {
        if (!Settings.canDrawOverlays(this) || edgeTriggerView != null) return
        val initialSettings = TurboSettingsStore.state.value
        lastEdgeOnLeft = initialSettings.edgeOnLeft

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            SAFE_OVERLAY_FLAGS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = (if (initialSettings.edgeOnLeft) Gravity.START else Gravity.END) or Gravity.CENTER_VERTICAL
            y = 0
        }
        edgeParams = params

        edgeTriggerView = createComposeView {
            val s by TurboSettingsStore.state.collectAsStateWithLifecycle()
            var interactTick by remember { mutableIntStateOf(0) }
            var dimmed by remember { mutableStateOf(false) }

            LaunchedEffect(interactTick) {
                dimmed = false
                delay(3000L)
                dimmed = true
            }

            val accent = resolveModeAccent(s)
            val targetAlpha = if (dimmed) (s.edgeOpacity * 0.40f).coerceAtLeast(0.22f) else s.edgeOpacity
            val animatedAlpha by animateFloatAsState(
                targetValue = targetAlpha,
                animationSpec = tween(if (s.fastAnimationsEnabled) 180 else 360),
                label = "edgeAlpha"
            )

            val pillW = (18f * s.edgeSizeScale).dp
            val pillH = (56f * s.edgeSizeScale).dp
            val shape = if (s.edgeOnLeft) {
                RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp)
            } else {
                RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
            }

            var dragAccumX by remember { mutableFloatStateOf(0f) }

            Box(
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .alpha(animatedAlpha)
                    .width(pillW)
                    .height(pillH)
                    .clip(shape)
                    .background(
                        Brush.verticalGradient(
                            listOf(ObsidianGreenBg.copy(alpha = 0.92f), accent.copy(alpha = 0.38f), ObsidianGreenBg.copy(alpha = 0.92f))
                        )
                    )
                    .border(1.dp, accent.copy(alpha = 0.9f), shape)
                    .pointerInput(s.edgeOnLeft) {
                        detectDragGestures(
                            onDragStart = {
                                interactTick++
                                dragAccumX = 0f
                            },
                            onDragEnd = { dragAccumX = 0f },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragAccumX += dragAmount.x
                                // Vertical drag moves the tiny pill smoothly along the edge
                                if (kotlin.math.abs(dragAmount.y) > 0.5f) {
                                    edgeParams?.let { lp ->
                                        lp.y = (lp.y + dragAmount.y.roundToInt()).coerceIn(-520, 520)
                                        edgeTriggerView?.let { v ->
                                            runCatching { windowManager.updateViewLayout(v, lp) }
                                        }
                                    }
                                }
                                // Inward swipe opens the compact drawer
                                val swipedInward = if (s.edgeOnLeft) dragAccumX > 22f else dragAccumX < -22f
                                if (swipedInward) {
                                    dragAccumX = 0f
                                    openCompactDrawer()
                                }
                            }
                        )
                    }
                    .clickable {
                        interactTick++
                        openCompactDrawer()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (s.edgeOnLeft) Icons.Rounded.ChevronRight else Icons.Rounded.ChevronLeft,
                    contentDescription = "Open Game Magic",
                    tint = accent,
                    modifier = Modifier.size((14f * s.edgeSizeScale).dp)
                )
            }
        }

        runCatching { windowManager.addView(edgeTriggerView, params) }
    }

    private fun openCompactDrawer() {
        if (!Settings.canDrawOverlays(this)) return
        if (panelHostView != null) {
            isPanelExpanded.value = true
            SystemTelemetryEngine.startFpsMonitor()
            return
        }

        SoundManager.playPanelOpen(applicationContext)
        isPanelExpanded.value = true
        SystemTelemetryEngine.startFpsMonitor()

        val sNow = TurboSettingsStore.state.value
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            SAFE_OVERLAY_FLAGS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = (if (sNow.edgeOnLeft) Gravity.START else Gravity.END) or Gravity.CENTER_VERTICAL
        }

        panelHostView = createComposeView {
            val expanded by isPanelExpanded.collectAsStateWithLifecycle()
            val settings by TurboSettingsStore.state.collectAsStateWithLifecycle()
            val sysStats by liveTelemetry.collectAsStateWithLifecycle()
            val netStats by NetworkMonitorEngine.telemetry.collectAsStateWithLifecycle()
            val pulseTick by boostShockwavePulse.collectAsStateWithLifecycle()
            val boostText by boostFreedReadout.collectAsStateWithLifecycle()

            val dockAlign = if (settings.edgeOnLeft) Alignment.CenterStart else Alignment.CenterEnd
            val animMs = if (settings.fastAnimationsEnabled) 150 else 280

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        closeCompactDrawer()
                    },
                contentAlignment = dockAlign
            ) {
                AnimatedVisibility(
                    visible = expanded,
                    enter = slideInHorizontally(
                        initialOffsetX = { if (settings.edgeOnLeft) -it else it },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = if (settings.fastAnimationsEnabled) Spring.StiffnessMedium else Spring.StiffnessLow
                        )
                    ),
                    exit = slideOutHorizontally(
                        targetOffsetX = { if (settings.edgeOnLeft) -it else it },
                        animationSpec = tween(animMs)
                    )
                ) {
                    CompactSwipeUpSidebar(
                        settings = settings,
                        sysStats = sysStats,
                        netStats = netStats,
                        boostShockwaveTick = pulseTick,
                        boostReadout = boostText,
                        onClose = { closeCompactDrawer() },
                        onAction = { handleAction(it) }
                    )
                }
            }
        }.apply {
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    closeCompactDrawer()
                }
                false
            }
        }

        runCatching { windowManager.addView(panelHostView, params) }
    }

    private fun closeCompactDrawer() {
        if (!isPanelExpanded.value && panelHostView == null) return
        SoundManager.playPanelClose(applicationContext)
        isPanelExpanded.value = false
        // Smart Sleep: stop Choreographer FPS monitor immediately when collapsed
        SystemTelemetryEngine.stopFpsMonitor()
        val delayMs = if (TurboSettingsStore.state.value.fastAnimationsEnabled) 160L else 290L
        mainHandler.postDelayed({
            panelHostView?.let {
                runCatching { windowManager.removeView(it) }
                panelHostView = null
            }
        }, delayMs)
    }

    private fun handleAction(action: MagicSidebarAction) {
        when (action) {
            MagicSidebarAction.TurboBoostNow -> {
                boostShockwavePulse.value = boostShockwavePulse.value + 1
                SoundManager.playTurboBoostBlast(applicationContext)
                serviceScope.launch {
                    val res = SystemTelemetryEngine.boostMemory(applicationContext, aggressive = true)
                    liveTelemetry.value = SystemTelemetryEngine.readTelemetry(applicationContext)
                    boostFreedReadout.value = if (res.freedMb > 0) "+${res.freedMb} MB Freed" else "${res.processesTrimmed} Cached Cleared"
                    delay(2600L)
                    boostFreedReadout.value = null
                }
            }
            is MagicSidebarAction.SetPerformanceMode -> {
                TurboSettingsStore.update { it.copy(performanceMode = action.mode, iceCoolingEnabled = false) }
                when (action.mode) {
                    PerformanceMode.BALANCED -> SoundManager.playBalancedMode(applicationContext)
                    PerformanceMode.HEAVY -> SoundManager.playHeavyMode(applicationContext)
                    PerformanceMode.ULTRA -> SoundManager.playUltraMode(applicationContext)
                }
                if (action.mode != PerformanceMode.BALANCED) {
                    serviceScope.launch {
                        SystemTelemetryEngine.boostMemory(applicationContext, aggressive = action.mode == PerformanceMode.ULTRA)
                    }
                }
            }
            is MagicSidebarAction.ToggleSustainedPerformance -> {
                TurboSettingsStore.update { it.copy(sustainedPerformanceEnabled = action.enabled) }
            }
            is MagicSidebarAction.Toggle24HourClock -> {
                TurboSettingsStore.update { it.copy(use24HourClock = action.enabled) }
            }
            is MagicSidebarAction.ToggleVoiceChanger -> {
                if (action.enabled) {
                    val s = TurboSettingsStore.state.value
                    val started = VoiceChangerEngine.start(applicationContext, s.voicePreset, s.liveMicMonitorEnabled)
                    if (started) {
                        TurboSettingsStore.update { it.copy(voiceChangerEnabled = true) }
                    } else {
                        Toast.makeText(applicationContext, "Grant Mic permission in Game Magic first", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    VoiceChangerEngine.stop()
                    TurboSettingsStore.update { it.copy(voiceChangerEnabled = false, liveMicMonitorEnabled = false) }
                }
            }
            is MagicSidebarAction.SelectVoicePreset -> {
                TurboSettingsStore.update { it.copy(voicePreset = action.preset) }
                VoiceChangerEngine.updatePreset(action.preset)
                if (TurboSettingsStore.state.value.voiceChangerEnabled) {
                    VoiceChangerEngine.start(applicationContext, action.preset, TurboSettingsStore.state.value.liveMicMonitorEnabled)
                }
            }
            is MagicSidebarAction.ToggleLiveMicMonitor -> {
                if (action.enabled) {
                    val s = TurboSettingsStore.state.value
                    val ok = VoiceChangerEngine.start(applicationContext, s.voicePreset, monitorOutput = true)
                    if (ok) {
                        TurboSettingsStore.update { it.copy(voiceChangerEnabled = true, liveMicMonitorEnabled = true) }
                    } else {
                        Toast.makeText(applicationContext, "Grant Mic permission first", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    VoiceChangerEngine.setMonitorOutput(false)
                    TurboSettingsStore.update { it.copy(liveMicMonitorEnabled = false) }
                }
            }
            is MagicSidebarAction.ToggleCrosshair -> {
                TurboSettingsStore.update { it.copy(crosshairEnabled = action.enabled) }
            }
            MagicSidebarAction.CycleCrosshairStyle -> {
                val all = CrosshairStyle.entries
                val next = all[(TurboSettingsStore.state.value.crosshairStyle.ordinal + 1) % all.size]
                TurboSettingsStore.update { it.copy(crosshairStyle = next) }
            }
            is MagicSidebarAction.SetCrosshairColor -> {
                TurboSettingsStore.update { it.copy(crosshairColorHex = action.hex) }
            }
            is MagicSidebarAction.AdjustCrosshairSize -> {
                TurboSettingsStore.update { it.copy(crosshairSizeDp = (it.crosshairSizeDp + action.delta).coerceIn(14, 38)) }
            }
            is MagicSidebarAction.ToggleDnd -> {
                val ok = SystemTelemetryEngine.setDndEnabled(applicationContext, action.enabled)
                if (ok) {
                    TurboSettingsStore.update { it.copy(dndEnabled = action.enabled) }
                } else {
                    Toast.makeText(applicationContext, "Allow Do Not Disturb Access first", Toast.LENGTH_SHORT).show()
                }
            }
            is MagicSidebarAction.ToggleTouchGuard -> {
                TurboSettingsStore.update { it.copy(touchGuardEnabled = action.enabled) }
            }
            MagicSidebarAction.TakeScreenshot -> {
                closeCompactDrawer()
                mainHandler.postDelayed({
                    val ok = GameAccessibilityService.triggerSystemScreenshot()
                    if (!ok) {
                        Toast.makeText(applicationContext, "Enable Game Magic Accessibility for Screenshot", Toast.LENGTH_SHORT).show()
                    }
                }, 260L)
            }
            is MagicSidebarAction.ToggleIceCooling -> {
                val currentTemp = liveTelemetry.value.batteryTempC
                TurboSettingsStore.update {
                    it.copy(
                        iceCoolingEnabled = action.enabled,
                        coolingBaselineTempC = if (action.enabled) currentTemp else 0f
                    )
                }
                if (action.enabled) {
                    SoundManager.playIceCooling(applicationContext)
                }
            }
            is MagicSidebarAction.SetOverheatThreshold -> {
                TurboSettingsStore.update { it.copy(overheatThresholdC = action.tempC) }
            }
            is MagicSidebarAction.SetLowBatteryThreshold -> {
                TurboSettingsStore.update { it.copy(lowBatteryThresholdPct = action.pct) }
            }
            is MagicSidebarAction.TogglePingShield -> {
                TurboSettingsStore.update { it.copy(pingShieldEnabled = action.enabled) }
            }
            is MagicSidebarAction.SetPingThreshold -> {
                TurboSettingsStore.update { it.copy(pingAlertThresholdMs = action.ms) }
            }
            is MagicSidebarAction.SetEdgeOnLeft -> {
                TurboSettingsStore.update { it.copy(edgeOnLeft = action.left) }
            }
            is MagicSidebarAction.SetEdgeSize -> {
                TurboSettingsStore.update { it.copy(edgeSizeScale = action.scale) }
            }
            is MagicSidebarAction.SetEdgeOpacity -> {
                TurboSettingsStore.update { it.copy(edgeOpacity = action.opacity) }
            }
            is MagicSidebarAction.ToggleSoundEffects -> {
                TurboSettingsStore.update { it.copy(soundEffectsEnabled = action.enabled) }
            }
            is MagicSidebarAction.ToggleFastAnimations -> {
                TurboSettingsStore.update { it.copy(fastAnimationsEnabled = action.enabled) }
            }
        }
    }

    /**
     * Zero-touch (FLAG_NOT_TOUCHABLE) center aiming reticle overlay.
     */
    private fun showCrosshairOverlay() {
        if (crosshairView != null) return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            SAFE_OVERLAY_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        crosshairView = createComposeView {
            val s by TurboSettingsStore.state.collectAsStateWithLifecycle()
            val tint = Color(s.crosshairColorHex)
            val size = s.crosshairSizeDp.dp

            Canvas(modifier = Modifier.size(size)) {
                val cx = this.size.width / 2f
                val cy = this.size.height / 2f
                val stroke = 2.dp.toPx()
                when (s.crosshairStyle) {
                    CrosshairStyle.DOT -> {
                        drawCircle(Color.Black.copy(alpha = 0.7f), radius = 3.8.dp.toPx(), center = Offset(cx, cy))
                        drawCircle(tint, radius = 2.6.dp.toPx(), center = Offset(cx, cy))
                    }
                    CrosshairStyle.PLUS -> {
                        drawLine(tint, Offset(cx, 0f), Offset(cx, this.size.height), strokeWidth = stroke)
                        drawLine(tint, Offset(0f, cy), Offset(this.size.width, cy), strokeWidth = stroke)
                    }
                    CrosshairStyle.CIRCLE -> {
                        drawCircle(tint, radius = (this.size.minDimension / 2f) - stroke, style = Stroke(stroke))
                        drawCircle(tint, radius = 1.8.dp.toPx(), center = Offset(cx, cy))
                    }
                    CrosshairStyle.TACTICAL -> {
                        val gap = 4.dp.toPx()
                        drawLine(tint, Offset(cx, 0f), Offset(cx, cy - gap), strokeWidth = stroke)
                        drawLine(tint, Offset(cx, cy + gap), Offset(cx, this.size.height), strokeWidth = stroke)
                        drawLine(tint, Offset(0f, cy), Offset(cx - gap, cy), strokeWidth = stroke)
                        drawLine(tint, Offset(cx + gap, cy), Offset(this.size.width, cy), strokeWidth = stroke)
                        drawCircle(tint, radius = 1.7.dp.toPx(), center = Offset(cx, cy))
                    }
                }
            }
        }
        runCatching { windowManager.addView(crosshairView, params) }
    }

    private fun dismissCrosshairOverlay() {
        crosshairView?.let {
            runCatching { windowManager.removeView(it) }
            crosshairView = null
        }
    }

    /**
     * Touch Guard: slim invisible edge strips preventing accidental status-bar pull-downs & corner mistouches.
     */
    private fun showTouchGuardOverlays() {
        if (touchGuardTopView != null) return
        val topParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            24,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            SAFE_OVERLAY_FLAGS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP }

        val leftParams = WindowManager.LayoutParams(
            24,
            160,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            SAFE_OVERLAY_FLAGS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.START or Gravity.BOTTOM }

        val rightParams = WindowManager.LayoutParams(
            24,
            160,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            SAFE_OVERLAY_FLAGS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.END or Gravity.BOTTOM }

        touchGuardTopView = createComposeView { Box(Modifier.fillMaxSize().background(Color.Transparent)) }
        touchGuardLeftView = createComposeView { Box(Modifier.fillMaxSize().background(Color.Transparent)) }
        touchGuardRightView = createComposeView { Box(Modifier.fillMaxSize().background(Color.Transparent)) }

        runCatching {
            windowManager.addView(touchGuardTopView, topParams)
            windowManager.addView(touchGuardLeftView, leftParams)
            windowManager.addView(touchGuardRightView, rightParams)
        }
    }

    private fun dismissTouchGuardOverlays() {
        listOf(touchGuardTopView, touchGuardLeftView, touchGuardRightView).forEach { v ->
            v?.let { runCatching { windowManager.removeView(it) } }
        }
        touchGuardTopView = null
        touchGuardLeftView = null
        touchGuardRightView = null
    }

    /**
     * Tiny non-intrusive top-edge alert pill (FLAG_NOT_TOUCHABLE) for Overheat, Low Battery, or Early Ping Warning.
     */
    private fun showTopEdgeAlertBanner() {
        if (!Settings.canDrawOverlays(this) || alertBannerView != null) return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            SAFE_OVERLAY_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 26
        }

        alertBannerView = createComposeView {
            val alert by liveAlertBanner.collectAsStateWithLifecycle()
            AnimatedVisibility(visible = alert != null, enter = fadeIn(), exit = fadeOut()) {
                alert?.let { (message, accent) ->
                    Surface(
                        color = ObsidianGreenBg.copy(alpha = 0.92f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.darkNeonGlow(accent, cornerRadius = 10.dp, glowAlpha = 0.34f, strokeWidth = 0.85.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = accent, modifier = Modifier.size(13.dp))
                            Text(message, color = MagicTextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        runCatching { windowManager.addView(alertBannerView, params) }
    }

    private fun dismissTopEdgeAlertBanner() {
        alertBannerView?.let {
            runCatching { windowManager.removeView(it) }
            alertBannerView = null
        }
    }

    private fun hideAllGameOverlays() {
        closeCompactDrawer()
        edgeTriggerView?.let {
            runCatching { windowManager.removeView(it) }
            edgeTriggerView = null
        }
        dismissCrosshairOverlay()
        dismissTouchGuardOverlays()
        dismissTopEdgeAlertBanner()
    }

    override fun onDestroy() {
        isRunning = false
        telemetryJob?.cancel()
        fallbackMonitorJob?.cancel()
        settingsWatchJob?.cancel()
        serviceScope.cancel()
        VoiceChangerEngine.stop()
        SystemTelemetryEngine.stopFpsMonitor()
        mainHandler.post { hideAllGameOverlays() }
        super.onDestroy()
    }
}
