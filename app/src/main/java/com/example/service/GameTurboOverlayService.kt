package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.MainActivity
import com.example.R
import com.example.core.*
import com.example.ui.components.CompactSwipeUpSidebar
import com.example.ui.components.GameVisualFilter
import com.example.ui.components.MagicSidebarAction
import com.example.ui.components.darkNeonGlow
import com.example.ui.components.resolveModeAccent
import com.example.ui.theme.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.roundToInt

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
    private var visualFilterOverlayView: ComposeView? = null
    private var crosshairView: ComposeView? = null
    private var touchGuardTopView: ComposeView? = null
    private var touchGuardBottomView: ComposeView? = null
    private var touchGuardLeftView: ComposeView? = null
    private var touchGuardRightView: ComposeView? = null
    private var alertBannerView: ComposeView? = null
    private var incomingCallBannerView: ComposeView? = null

    private val isPanelExpanded = MutableStateFlow(false)
    private val liveTelemetry = MutableStateFlow(SystemTelemetry())
    private val liveAlertBanner = MutableStateFlow<Pair<String, Color>?>(null)
    private val boostShockwavePulse = MutableStateFlow(0)
    private val boostFreedReadout = MutableStateFlow<String?>(null)

    private val activeVisualFilter = MutableStateFlow(GameVisualFilter.ORIGINAL)
    private val isRecordingScreen = MutableStateFlow(false)
    private val recordingSeconds = MutableStateFlow(0)
    private val floatingCallEnabled = MutableStateFlow(true)
    private val incomingCallNumber = MutableStateFlow<String?>(null)
    private val smartVoiceChatMicOnly = MutableStateFlow(true)
    private val isVoiceChatDetected = MutableStateFlow(false)

    private var activeGamePackage: String? = null
    private var lastEdgeOnLeft: Boolean? = null
    private var lastAlertPingMs: Long = 0L
    private var telemetryJob: Job? = null
    private var fallbackMonitorJob: Job? = null
    private var settingsWatchJob: Job? = null
    private var recordingTimerJob: Job? = null
    private var callReceiverRegistered = false

    private val phoneStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (activeGamePackage == null || !floatingCallEnabled.value) return
            if (intent?.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
                when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
                    TelephonyManager.EXTRA_STATE_RINGING -> {
                        @Suppress("DEPRECATION")
                        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: "Incoming Call"
                        incomingCallNumber.value = number
                        mainHandler.post { showIncomingCallPopup() }
                    }
                    TelephonyManager.EXTRA_STATE_IDLE,
                    TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                        incomingCallNumber.value = null
                        mainHandler.post { dismissIncomingCallPopup() }
                    }
                }
            }
        }
    }

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

        registerCallReceiverSafely()
        startSmartSleepTelemetryLoop()
        startFallbackUsageMonitor()
        observeSettings()
    }

    private fun registerCallReceiverSafely() {
        if (callReceiverRegistered) return
        runCatching {
            val filter = IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(phoneStateReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(phoneStateReceiver, filter)
            }
            callReceiverRegistered = true
        }
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
            description = "Keeps the Game Magic floating sidebar and guards active inside added games"
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
        syncSmartVoiceChangerState(s)
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
        stopRecordingTimer()
        incomingCallNumber.value = null
        mainHandler.post { hideAllGameOverlays() }
    }

    private fun syncSmartVoiceChangerState(s: MagicSettings) {
        if (!s.voiceChangerEnabled || activeGamePackage == null) {
            VoiceChangerEngine.stop()
            isVoiceChatDetected.value = false
            return
        }
        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val inVoiceChat = am?.mode == AudioManager.MODE_IN_COMMUNICATION ||
            (am?.isMicrophoneMute == false && am.mode == AudioManager.MODE_IN_CALL)
        isVoiceChatDetected.value = inVoiceChat

        val shouldRunDsp = !smartVoiceChatMicOnly.value || inVoiceChat || s.liveMicMonitorEnabled
        if (shouldRunDsp) {
            VoiceChangerEngine.start(applicationContext, s.voicePreset, s.liveMicMonitorEnabled)
        } else {
            VoiceChangerEngine.stop()
        }
    }

    private fun startSmartSleepTelemetryLoop() {
        telemetryJob = serviceScope.launch {
            while (isActive) {
                if (activeGamePackage == null) {
                    delay(2500L)
                    continue
                }

                val expanded = isPanelExpanded.value
                val s = TurboSettingsStore.state.value

                syncSmartVoiceChangerState(s)

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
                    else -> 4000L
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
                delay(1200L)
                if (usm == null) continue
                val selected = TurboSettingsStore.state.value.selectedPackages
                if (selected.isEmpty()) {
                    if (activeGamePackage != null) handleForegroundUpdate(null, false)
                    continue
                }

                val a11yPkg = GameAccessibilityService.instance?.rootInActiveWindow?.packageName?.toString()
                if (a11yPkg != null && a11yPkg != packageName && a11yPkg != "com.android.systemui") {
                    val isLauncher = a11yPkg.lowercase().contains("launcher") ||
                        a11yPkg == "com.miui.home" ||
                        a11yPkg == "com.mi.android.globallauncher"
                    val isGame = !isLauncher && selected.contains(a11yPkg)
                    handleForegroundUpdate(a11yPkg, isGame)
                    continue
                }

                val end = System.currentTimeMillis()
                val events = usm.queryEvents(end - 6000L, end)
                val event = UsageEvents.Event()
                var latestForegroundPkg: String? = null
                var activeGamePaused = false

                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    when (event.eventType) {
                        UsageEvents.Event.MOVE_TO_FOREGROUND,
                        UsageEvents.Event.ACTIVITY_RESUMED -> {
                            latestForegroundPkg = event.packageName
                            if (event.packageName == activeGamePackage) {
                                activeGamePaused = false
                            }
                        }
                        UsageEvents.Event.MOVE_TO_BACKGROUND,
                        UsageEvents.Event.ACTIVITY_PAUSED -> {
                            if (event.packageName == activeGamePackage) {
                                activeGamePaused = true
                            }
                        }
                    }
                }

                if (latestForegroundPkg != null && latestForegroundPkg != packageName) {
                    val isLauncher = latestForegroundPkg.lowercase().contains("launcher") ||
                        latestForegroundPkg == "com.miui.home" ||
                        latestForegroundPkg == "com.mi.android.globallauncher"
                    val isGame = !isLauncher && selected.contains(latestForegroundPkg)
                    handleForegroundUpdate(latestForegroundPkg, isGame)
                } else if (activeGamePaused && activeGamePackage != null) {
                    handleForegroundUpdate(null, false)
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

        if (activeVisualFilter.value != GameVisualFilter.ORIGINAL) {
            showOrUpdateVisualFilterOverlay()
        } else {
            dismissVisualFilterOverlay()
        }

        if (s.crosshairEnabled) showCrosshairOverlay() else dismissCrosshairOverlay()
        if (s.touchGuardEnabled || s.dndEnabled) showTouchGuardOverlays() else dismissTouchGuardOverlays()
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
            val targetAlpha = if (dimmed) (s.edgeOpacity * 0.45f).coerceAtLeast(0.25f) else s.edgeOpacity
            val animatedAlpha by animateFloatAsState(
                targetValue = targetAlpha,
                animationSpec = tween(if (s.fastAnimationsEnabled) 180 else 360),
                label = "edgeAlpha"
            )

            val pillW = (20f * s.edgeSizeScale).dp
            val pillH = (62f * s.edgeSizeScale).dp
            val shape = if (s.edgeOnLeft) {
                RoundedCornerShape(topEnd = 13.dp, bottomEnd = 13.dp)
            } else {
                RoundedCornerShape(topStart = 13.dp, bottomStart = 13.dp)
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
                            listOf(ObsidianGreenBg.copy(alpha = 0.94f), accent.copy(alpha = 0.45f), ObsidianGreenBg.copy(alpha = 0.94f))
                        )
                    )
                    .border(1.2.dp, accent.copy(alpha = 0.95f), shape)
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
                                if (kotlin.math.abs(dragAmount.y) > 0.5f) {
                                    edgeParams?.let { lp ->
                                        lp.y = (lp.y + dragAmount.y.roundToInt()).coerceIn(-520, 520)
                                        edgeTriggerView?.let { v ->
                                            runCatching { windowManager.updateViewLayout(v, lp) }
                                        }
                                    }
                                }
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
                    modifier = Modifier.size((15f * s.edgeSizeScale).dp)
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
            val filter by activeVisualFilter.collectAsStateWithLifecycle()
            val recording by isRecordingScreen.collectAsStateWithLifecycle()
            val recSecs by recordingSeconds.collectAsStateWithLifecycle()
            val callEnabled by floatingCallEnabled.collectAsStateWithLifecycle()
            val smartMic by smartVoiceChatMicOnly.collectAsStateWithLifecycle()
            val voiceChatActive by isVoiceChatDetected.collectAsStateWithLifecycle()

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
                        activeFilter = filter,
                        isRecordingScreen = recording,
                        recordingSeconds = recSecs,
                        floatingCallEnabled = callEnabled,
                        smartVoiceChatMicOnly = smartMic,
                        isVoiceChatDetected = voiceChatActive,
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
                    boostFreedReadout.value = if (res.freedMb > 0) "+${res.freedMb} MB" else "${res.processesTrimmed} Clean"
                    delay(2600L)
                    boostFreedReadout.value = null
                }
            }
            is MagicSidebarAction.SelectVisualFilter -> {
                activeVisualFilter.value = action.filter
                mainHandler.post { syncAuxiliaryOverlays() }
                Toast.makeText(applicationContext, "Filter: ${action.filter.label}", Toast.LENGTH_SHORT).show()
            }
            MagicSidebarAction.ToggleScreenRecording -> {
                if (isRecordingScreen.value) {
                    stopRecordingTimer()
                    Toast.makeText(applicationContext, "Screen Recording Saved", Toast.LENGTH_SHORT).show()
                } else {
                    startRecordingTimer()
                    Toast.makeText(applicationContext, "Game Recording Started", Toast.LENGTH_SHORT).show()
                }
            }
            is MagicSidebarAction.ToggleFloatingCall -> {
                floatingCallEnabled.value = action.enabled
                Toast.makeText(
                    applicationContext,
                    if (action.enabled) "In-Game Call Popup ON" else "In-Game Call Popup OFF",
                    Toast.LENGTH_SHORT
                ).show()
            }
            is MagicSidebarAction.ToggleSmartVoiceMic -> {
                smartVoiceChatMicOnly.value = action.enabled
                syncSmartVoiceChangerState(TurboSettingsStore.state.value)
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
                    val hasMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                    if (hasMic) {
                        TurboSettingsStore.update { it.copy(voiceChangerEnabled = true) }
                        syncSmartVoiceChangerState(TurboSettingsStore.state.value)
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
                syncSmartVoiceChangerState(TurboSettingsStore.state.value)
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

    private fun startRecordingTimer() {
        isRecordingScreen.value = true
        recordingSeconds.value = 0
        recordingTimerJob?.cancel()
        recordingTimerJob = serviceScope.launch {
            while (isActive && isRecordingScreen.value) {
                delay(1000L)
                recordingSeconds.value += 1
            }
        }
    }

    private fun stopRecordingTimer() {
        isRecordingScreen.value = false
        recordingTimerJob?.cancel()
        recordingSeconds.value = 0
    }

    private fun showOrUpdateVisualFilterOverlay() {
        if (visualFilterOverlayView != null) return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            SAFE_OVERLAY_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )

        visualFilterOverlayView = createComposeView {
            val filter by activeVisualFilter.collectAsStateWithLifecycle()
            if (filter != GameVisualFilter.ORIGINAL) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(filter.tintColor.copy(alpha = filter.tintAlpha))
                )
            }
        }
        runCatching { windowManager.addView(visualFilterOverlayView, params) }
    }

    private fun dismissVisualFilterOverlay() {
        visualFilterOverlayView?.let {
            runCatching { windowManager.removeView(it) }
            visualFilterOverlayView = null
        }
    }

    private fun showIncomingCallPopup() {
        if (!Settings.canDrawOverlays(this) || incomingCallBannerView != null) return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            SAFE_OVERLAY_FLAGS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 32
        }

        incomingCallBannerView = createComposeView {
            val caller by incomingCallNumber.collectAsStateWithLifecycle()
            if (caller != null) {
                Surface(
                    color = ObsidianGreenBg.copy(alpha = 0.96f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.darkNeonGlow(BalancedCyanGlow, cornerRadius = 14.dp, glowAlpha = 0.5f, strokeWidth = 1.2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Rounded.PhoneInTalk, contentDescription = null, tint = BalancedCyanGlow, modifier = Modifier.size(18.dp))
                        Column {
                            Text("IN-GAME CALL", color = BalancedCyanGlow, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold)
                            Text(caller ?: "Incoming Call", color = MagicTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(EmeraldGlow)
                                .clickable { answerCallInGame() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Call, contentDescription = "Answer", tint = ObsidianGreenBg, modifier = Modifier.size(16.dp))
                        }
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(UltraCrimsonGlow)
                                .clickable { rejectCallInGame() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.CallEnd, contentDescription = "Reject", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
        runCatching { windowManager.addView(incomingCallBannerView, params) }
    }

    private fun answerCallInGame() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED
            ) {
                val tm = getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                @Suppress("DEPRECATION")
                tm?.acceptRingingCall()
                val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                am?.isSpeakerphoneOn = true
            }
        }
        incomingCallNumber.value = null
        dismissIncomingCallPopup()
    }

    private fun rejectCallInGame() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED
            ) {
                val tm = getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                @Suppress("DEPRECATION")
                tm?.endCall()
            }
        }
        incomingCallNumber.value = null
        dismissIncomingCallPopup()
    }

    private fun dismissIncomingCallPopup() {
        incomingCallBannerView?.let {
            runCatching { windowManager.removeView(it) }
            incomingCallBannerView = null
        }
    }

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

    private fun showTouchGuardOverlays() {
        if (touchGuardTopView != null) return
        val topParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            42,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            SAFE_OVERLAY_FLAGS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP }

        val bottomParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            26,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            SAFE_OVERLAY_FLAGS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.BOTTOM }

        val leftParams = WindowManager.LayoutParams(
            26,
            180,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            SAFE_OVERLAY_FLAGS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.START or Gravity.BOTTOM }

        val rightParams = WindowManager.LayoutParams(
            26,
            180,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            SAFE_OVERLAY_FLAGS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.END or Gravity.BOTTOM }

        touchGuardTopView = createComposeView { Box(Modifier.fillMaxSize().background(Color.Transparent)) }
        touchGuardBottomView = createComposeView { Box(Modifier.fillMaxSize().background(Color.Transparent)) }
        touchGuardLeftView = createComposeView { Box(Modifier.fillMaxSize().background(Color.Transparent)) }
        touchGuardRightView = createComposeView { Box(Modifier.fillMaxSize().background(Color.Transparent)) }

        runCatching {
            windowManager.addView(touchGuardTopView, topParams)
            windowManager.addView(touchGuardBottomView, bottomParams)
            windowManager.addView(touchGuardLeftView, leftParams)
            windowManager.addView(touchGuardRightView, rightParams)
        }
    }

    private fun dismissTouchGuardOverlays() {
        listOf(touchGuardTopView, touchGuardBottomView, touchGuardLeftView, touchGuardRightView).forEach { v ->
            v?.let { runCatching { windowManager.removeView(it) } }
        }
        touchGuardTopView = null
        touchGuardBottomView = null
        touchGuardLeftView = null
        touchGuardRightView = null
    }

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
        dismissVisualFilterOverlay()
        dismissCrosshairOverlay()
        dismissTouchGuardOverlays()
        dismissTopEdgeAlertBanner()
        dismissIncomingCallPopup()
    }

    override fun onDestroy() {
        isRunning = false
        telemetryJob?.cancel()
        fallbackMonitorJob?.cancel()
        settingsWatchJob?.cancel()
        recordingTimerJob?.cancel()
        serviceScope.cancel()
        if (callReceiverRegistered) {
            runCatching { unregisterReceiver(phoneStateReceiver) }
            callReceiverRegistered = false
        }
        VoiceChangerEngine.stop()
        SystemTelemetryEngine.stopFpsMonitor()
        mainHandler.post { hideAllGameOverlays() }
        super.onDestroy()
    }
}
