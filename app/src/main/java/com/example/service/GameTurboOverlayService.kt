package com.example.service

import android.Manifest
import android.app.Activity
import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.example.ui.components.MagicSidebarAction
import com.example.ui.theme.GamingTurboTheme
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
        const val ACTION_RECORD_PROJECTION = "com.gamemagic.action.RECORD_PROJECTION"
        const val EXTRA_PACKAGE_NAME = "extra_pkg"
        const val EXTRA_IS_GAME_ACTIVE = "extra_is_game"
        const val EXTRA_RESULT_CODE = "extra_res_code"
        const val EXTRA_RESULT_DATA = "extra_res_data"

        private const val SAFE_OVERLAY_FLAGS =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

        @Volatile
        var isRunning = false
            private set
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var windowManager: WindowManager

    private var edgeTriggerView: ComposeView? = null
    private var edgeParams: WindowManager.LayoutParams? = null
    private var panelHostView: ComposeView? = null
    private var filterOverlayView: ComposeView? = null
    private var statusBarBlockerView: ComposeView? = null
    private var miniCounterView: ComposeView? = null

    private val isPanelExpanded = MutableStateFlow(false)
    private val boostReadout = MutableStateFlow<String?>(null)

    private var activeGamePackage: String? = null
    private var monitorJob: Job? = null
    private var telemetryJob: Job? = null
    private var settingsJob: Job? = null
    private var recordTimerJob: Job? = null

    private var mediaProjection: MediaProjection? = null
    private var mediaRecorder: MediaRecorder? = null
    private var virtualDisplay: VirtualDisplay? = null

    override fun onCreate() {
        super.onCreate()
        TurboSettingsStore.init(applicationContext)
        RedMagicEngine.init(applicationContext)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        createNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        isRunning = true

        startMonitorLoop()
        startTelemetryLoop()
        observeSettings()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                activeGamePackage = null
                mainHandler.post { hideAllOverlays() }
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                if (activeGamePackage == null) mainHandler.post { hideAllOverlays() }
            }
            ACTION_FOREGROUND_CHANGED -> {
                val pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME)
                val isGame = intent.getBooleanExtra(EXTRA_IS_GAME_ACTIVE, false)
                handleForeground(pkg, isGame)
            }
            ACTION_RECORD_PROJECTION -> {
                val code = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                @Suppress("DEPRECATION")
                val data = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                if (code == Activity.RESULT_OK && data != null) {
                    startRealScreenRecording(code, data)
                } else {
                    Toast.makeText(this, "Screen recording cancelled", Toast.LENGTH_SHORT).show()
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Game Turbo Engine",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText("Red Magic Game Turbo Ready")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun handleForeground(pkg: String?, isGame: Boolean) {
        if (pkg != null && isTransientIgnoredPackage(pkg)) return
        val safeGame = isGame && pkg != null && !isLauncherOrSystem(pkg)
        if (safeGame && pkg != null) {
            if (activeGamePackage != pkg) {
                activeGamePackage = pkg
                RedMagicEngine.loadProfile(applicationContext, pkg)
                onGameEntered()
            }
        } else {
            if (activeGamePackage != null || edgeTriggerView != null || panelHostView != null) {
                activeGamePackage = null
                RedMagicEngine.loadProfile(applicationContext, null)
                onGameExited()
            }
        }
    }

    private fun onGameEntered() {
        val rm = RedMagicEngine.state.value
        if (rm.blockAlerts) SystemTelemetryEngine.setDndEnabled(applicationContext, true)
        if (rm.voiceChanger) VoiceChangerEngine.start(applicationContext, VoicePreset.DEEP_PRO, false)
        mainHandler.post {
            showEdgePill()
            syncAuxOverlays()
        }
    }

    private fun onGameExited() {
        SystemTelemetryEngine.setDndEnabled(applicationContext, false)
        VoiceChangerEngine.stop()
        stopRealScreenRecording(showToast = false)
        mainHandler.post { hideAllOverlays() }
    }

    private fun startTelemetryLoop() {
        telemetryJob = serviceScope.launch {
            while (isActive) {
                val game = activeGamePackage
                if (game == null) {
                    delay(3500L)
                    continue
                }
                RedMagicEngine.addPlaytimeSeconds(applicationContext, game, 3L)
                val rm = RedMagicEngine.state.value
                if (isPanelExpanded.value || rm.showMiniFpsCounter) {
                    RedMagicEngine.refreshHardware(applicationContext)
                }
                delay(3000L)
            }
        }
    }

    private fun startMonitorLoop() {
        monitorJob = serviceScope.launch {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            while (isActive) {
                delay(750L)
                val selected = TurboSettingsStore.state.value.selectedPackages
                    .filterNot { isLauncherOrSystem(it) }
                    .toSet()

                if (selected.isEmpty()) {
                    if (activeGamePackage != null) handleForeground(null, false)
                    continue
                }

                var detectedPkg: String? = null
                val a11yPkg = GameAccessibilityService.instance?.rootInActiveWindow?.packageName?.toString()
                if (a11yPkg != null && !isTransientIgnoredPackage(a11yPkg)) {
                    detectedPkg = a11yPkg
                }

                if (usm != null && (detectedPkg == null || detectedPkg == "com.android.systemui")) {
                    val end = System.currentTimeMillis()
                    val events = usm.queryEvents(end - 5000L, end)
                    val ev = UsageEvents.Event()
                    var latest: String? = null
                    while (events.hasNextEvent()) {
                        events.getNextEvent(ev)
                        if (ev.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                            ev.eventType == UsageEvents.Event.ACTIVITY_RESUMED
                        ) {
                            latest = ev.packageName
                        }
                    }
                    if (latest != null && !isTransientIgnoredPackage(latest)) {
                        detectedPkg = latest
                    }
                }

                if (detectedPkg != null) {
                    val isGame = !isLauncherOrSystem(detectedPkg) && selected.contains(detectedPkg)
                    handleForeground(detectedPkg, isGame)
                }
            }
        }
    }
