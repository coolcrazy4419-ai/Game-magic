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
import androidx.compose.foundation.BorderStroke
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

/**
 * 100% Anti-Cheat Safe Pro Gaming Overlay Service for Game Magic.
 * - Strictly runs ONLY when an added game is actively in the foreground (never on Home screen).
 * - Preserves 100% of original tools + adds 10 Visual Filters, In-Game Floating Call Handler,
 *   Smart Voice-Chat-Only Mic DSP, Strict DND Control-Center Swipe Blocker, and Screen Recorder.
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

    // New Pro Feature States
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
                val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                when (state) {
                    TelephonyManager.EXTRA_STATE_RINGING -> {
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

    /**
     * Smart Mic / Voice System:
     * When smartVoiceChatMicOnly is ON, VoiceChangerEngine activates ONLY when an in-game voice chat
     * session (AudioManager.MODE_IN_COMMUNICATION or active communication device) is detected.
     */
    private fun syncSmartVoiceChangerState(s: MagicSettings) {
        if (!s.voiceChangerEnabled || activeGamePackage == null) {
            VoiceChangerEngine.stop()
            isVoiceChatDetected.value = false
            return
        }
        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val inVoiceChat = am?.mode == AudioManager.MODE_IN_COMMUNICATION || am?.isMicrophoneMute == false && am.mode == AudioManager.MODE_IN_CALL
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
                val gameActive = activeGamePackage != null
                if (!gameActive) {
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

    /**
     * Fallback Foreground Detector (only used if AccessibilityService is turned off).
     * Uses UsageEvents.MOVE_TO_FOREGROUND so returning to Home Screen immediately hides the overlay.
     */
    private fun startFallbackUsageMonitor() {
        fallbackMonitorJob = serviceScope.launch {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            while (isActive) {
                delay(2000L)
                if (GameAccessibilityService.instance != null || usm == null) continue
                val selected = TurboSettingsStore.state.value.selectedPackages
                if (selected.isEmpty()) {
                    if (activeGamePackage != null) handleForegroundUpdate(null, false)
                    continue
                }
                val end = System.currentTimeMillis()
                val events = usm.queryEvents(end - 15_000L, end)
                val event = UsageEvents.Event()
                var latestForegroundPkg: String? = null
                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                        event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
                    ) {
                        latestForegroundPkg = event.packageName
                    }
                }
                if (latestForegroundPkg != null && latestForegroundPkg != packageName) {
                    val isLauncher = latestForegroundPkg.lowercase().contains("launcher") ||
                        latestForegroundPkg == "com.miui.home"
                    val isGame = !isLauncher && selected.contains(latestForegroundPkg)
                    handleForegroundUpdate(latestForegroundPkg, isGame)
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

        // Strict DND also activates the Control Center / Notification Shade swipe blocker
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
