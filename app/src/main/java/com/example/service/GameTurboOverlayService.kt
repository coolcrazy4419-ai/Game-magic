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

    private fun startActiveVoicePreset() {
        val preset = VoicePreset.entries.getOrElse(1) { VoicePreset.entries.first() }
        VoiceChangerEngine.start(applicationContext, preset, false)
    }

    private fun onGameEntered() {
        val rm = RedMagicEngine.state.value
        if (rm.blockAlerts) SystemTelemetryEngine.setDndEnabled(applicationContext, true)
        if (rm.voiceChanger) startActiveVoicePreset()
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

    private fun isTransientIgnoredPackage(pkg: String): Boolean {
        val l = pkg.lowercase()
        return l.contains("inputmethod") ||
            l.contains("keyboard") ||
            l.contains("incallui") ||
            l.contains("dialer") ||
            l.contains("telecom") ||
            l.contains("permissioncontroller") ||
            l == "com.android.systemui"
    }

    private fun isLauncherOrSystem(pkg: String): Boolean {
        if (pkg == packageName || pkg == "com.android.systemui" || pkg == "android") return true
        val l = pkg.lowercase()
        return l.contains("launcher") ||
            l == "com.miui.home" ||
            l == "com.mi.android.globallauncher" ||
            l == "com.android.launcher3" ||
            l.contains("miui.securitycenter") ||
            l.contains("settings")
    }

    private fun observeSettings() {
        settingsJob = serviceScope.launch {
            RedMagicEngine.state.collect {
                if (activeGamePackage != null) {
                    mainHandler.post { syncAuxOverlays() }
                }
            }
        }
    }

    private fun syncAuxOverlays() {
        if (!Settings.canDrawOverlays(this)) return
        val rm = RedMagicEngine.state.value
        if (rm.filter != RedMagicFilter.ORIGINAL || rm.hdrBoost) showFilterOverlay() else dismissFilterOverlay()
        if (rm.lockControlCenter || rm.blockAlerts) showStatusBarShadeBlocker() else dismissStatusBarShadeBlocker()
        if (rm.showMiniFpsCounter) showMiniFpsCounterOverlay() else dismissMiniFpsCounterOverlay()
    }

    private fun createComposeView(content: @Composable () -> Unit): ComposeView {
        val owner = MyLifecycleOwner()
        val vmOwner = MyViewModelStoreOwner()
        owner.performRestore(null)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        return ComposeView(this).apply {
            tag = Pair(owner, vmOwner)
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(vmOwner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent { GamingTurboTheme { content() } }
        }
    }

    private fun destroyComposeView(view: ComposeView?) {
        view ?: return
        val owners = view.tag as? Pair<*, *>
        val lifecycleOwner = owners?.first as? MyLifecycleOwner
        val vmStoreOwner = owners?.second as? MyViewModelStoreOwner
        if (lifecycleOwner != null) {
            try {
                lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            } catch (_: Exception) {
            }
        }
        try {
            vmStoreOwner?.viewModelStore?.clear()
        } catch (_: Exception) {
        }
        try {
            windowManager.removeView(view)
        } catch (_: Exception) {
        }
    }

    private fun showEdgePill() {
        if (!Settings.canDrawOverlays(this) || edgeTriggerView != null || activeGamePackage == null) return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            SAFE_OVERLAY_FLAGS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            y = -60
        }
        edgeParams = params

        edgeTriggerView = createComposeView {
            val shape = RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp)
            var dragAccumX by remember { mutableFloatStateOf(0f) }

            Box(
                modifier = Modifier
                    .width(20.dp)
                    .height(68.dp)
                    .clip(shape)
                    .background(Color(0xEA0B0B0E))
                    .border(1.4.dp, Color(0xFFFF551A), shape)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { dragAccumX = 0f },
                            onDragEnd = { dragAccumX = 0f },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragAccumX += dragAmount.x
                                if (kotlin.math.abs(dragAmount.y) > 0.5f) {
                                    edgeParams?.let { lp ->
                                        lp.y = (lp.y + dragAmount.y.roundToInt()).coerceIn(-480, 480)
                                        edgeTriggerView?.let { v ->
                                            try {
                                                windowManager.updateViewLayout(v, lp)
                                            } catch (_: Exception) {
                                            }
                                        }
                                    }
                                }
                                if (dragAccumX > 20f) {
                                    dragAccumX = 0f
                                    openSidePanel()
                                }
                            }
                        )
                    }
                    .clickable { openSidePanel() },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(34.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF551A))
                )
            }
        }
        try {
            windowManager.addView(edgeTriggerView, params)
        } catch (_: Exception) {
        }
    }

    private fun openSidePanel() {
        if (!Settings.canDrawOverlays(this) || activeGamePackage == null) return
        if (panelHostView != null) {
            isPanelExpanded.value = true
            return
        }
        isPanelExpanded.value = true
        serviceScope.launch { RedMagicEngine.refreshHardware(applicationContext) }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            SAFE_OVERLAY_FLAGS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }

        panelHostView = createComposeView {
            val expanded by isPanelExpanded.collectAsStateWithLifecycle()
            val rmState by RedMagicEngine.state.collectAsStateWithLifecycle()
            val hw by RedMagicEngine.hardware.collectAsStateWithLifecycle()
            val boostMsg by boostReadout.collectAsStateWithLifecycle()

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (expanded) 0.28f else 0f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { closeSidePanel() },
                contentAlignment = Alignment.CenterStart
            ) {
                AnimatedVisibility(
                    visible = expanded,
                    enter = slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(160)),
                    exit = slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(140))
                ) {
                    CompactSwipeUpSidebar(
                        rmState = rmState,
                        hw = hw,
                        boostReadout = boostMsg,
                        onClose = { closeSidePanel() },
                        onAction = { handleSidebarAction(it) }
                    )
                }
            }
        }.apply {
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE) closeSidePanel()
                false
            }
        }
        try {
            windowManager.addView(panelHostView, params)
        } catch (_: Exception) {
        }
    }

    private fun closeSidePanel() {
        if (!isPanelExpanded.value && panelHostView == null) return
        isPanelExpanded.value = false
        mainHandler.postDelayed({
            destroyComposeView(panelHostView)
            panelHostView = null
        }, 150L)
    }

    private fun handleSidebarAction(action: MagicSidebarAction) {
        when (action) {
            MagicSidebarAction.TurboBoostNow -> {
                serviceScope.launch {
                    val res = SystemTelemetryEngine.boostMemory(applicationContext, aggressive = false)
                    RedMagicEngine.refreshHardware(applicationContext)
                    boostReadout.value = if (res.freedMb > 0) "RAM Freed +${res.freedMb} MB" else "RAM Optimized"
                    delay(2000L)
                    boostReadout.value = null
                }
            }
            MagicSidebarAction.TakeScreenshot -> {
                closeSidePanel()
                mainHandler.postDelayed({
                    if (!GameAccessibilityService.triggerSystemScreenshot()) {
                        Toast.makeText(this, "Enable Game Magic Accessibility", Toast.LENGTH_SHORT).show()
                    }
                }, 200L)
            }
            MagicSidebarAction.ToggleScreenRecording -> {
                if (RedMagicEngine.state.value.isRecording) {
                    stopRealScreenRecording(showToast = true)
                } else {
                    closeSidePanel()
                    val launchIntent = Intent(this, MainActivity::class.java)
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(launchIntent)
                }
            }
            is MagicSidebarAction.SetPerfMode -> {
                RedMagicEngine.update(applicationContext) { it.copy(perfMode = action.mode) }
            }
            is MagicSidebarAction.ToggleBlockAlerts -> {
                SystemTelemetryEngine.setDndEnabled(applicationContext, action.enabled)
                RedMagicEngine.update(applicationContext) { it.copy(blockAlerts = action.enabled) }
            }
            is MagicSidebarAction.ToggleLockControlCenter -> {
                RedMagicEngine.update(applicationContext) { it.copy(lockControlCenter = action.enabled) }
            }
            is MagicSidebarAction.ToggleSmallCallBanner -> {
                RedMagicEngine.update(applicationContext) { it.copy(smallCallBanner = action.enabled) }
            }
            is MagicSidebarAction.ToggleBrightnessLock -> {
                RedMagicEngine.setBrightnessLock(applicationContext, action.enabled)
            }
            is MagicSidebarAction.ToggleAiIsolation -> {
                RedMagicEngine.update(applicationContext) { it.copy(aiIsolation = action.enabled) }
                if (action.enabled) {
                    serviceScope.launch {
                        SystemTelemetryEngine.boostMemory(applicationContext, aggressive = false)
                        boostReadout.value = "AI Isolation Active"
                        delay(1800L)
                        boostReadout.value = null
                    }
                }
            }
            is MagicSidebarAction.ToggleMiniFpsCounter -> {
                RedMagicEngine.update(applicationContext) { it.copy(showMiniFpsCounter = action.enabled) }
            }
            is MagicSidebarAction.LaunchFloatingApp -> {
                closeSidePanel()
                launchQuickFloatingApp(action.pkg)
            }
            is MagicSidebarAction.ToggleVoiceChanger -> {
                if (action.enabled) {
                    val ok = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                    if (ok) {
                        startActiveVoicePreset()
                        RedMagicEngine.update(applicationContext) { it.copy(voiceChanger = true) }
                    } else {
                        Toast.makeText(this, "Grant Mic permission in Game Magic", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    VoiceChangerEngine.stop()
                    RedMagicEngine.update(applicationContext) { it.copy(voiceChanger = false) }
                }
            }
            is MagicSidebarAction.SetTouchSensitivity -> {
                RedMagicEngine.update(applicationContext) { it.copy(touchSensitivity = action.value) }
            }
            else -> Unit
        }
    }

    private fun launchQuickFloatingApp(targetPkg: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(targetPkg)
        if (launchIntent == null) {
            Toast.makeText(this, "App not installed", Toast.LENGTH_SHORT).show()
            return
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val opts = ActivityOptions.makeBasic()
                val dm = resources.displayMetrics
                val w = (dm.widthPixels * 0.45f).toInt()
                val h = (dm.heightPixels * 0.55f).toInt()
                opts.setLaunchBounds(Rect(60, 120, 60 + w, 120 + h))
                startActivity(launchIntent, opts.toBundle())
            } else {
                startActivity(launchIntent)
            }
        } catch (_: Exception) {
            startActivity(launchIntent)
        }
    }

    private fun showMiniFpsCounterOverlay() {
        if (miniCounterView != null) return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            SAFE_OVERLAY_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 96
            y = 18
        }
        miniCounterView = createComposeView {
            val hw by RedMagicEngine.hardware.collectAsStateWithLifecycle()
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xCC0B0B0E))
                    .border(0.8.dp, Color(0xFFFF551A), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("${hw.fps} FPS", color = Color(0xFFFF551A), fontSize = 9.5.sp, fontWeight = FontWeight.Black)
                Text("${hw.pingMs} ms", color = Color(0xFF00E676), fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
            }
        }
        try {
            windowManager.addView(miniCounterView, params)
        } catch (_: Exception) {
        }
    }

    private fun dismissMiniFpsCounterOverlay() {
        destroyComposeView(miniCounterView)
        miniCounterView = null
    }

    private fun showStatusBarShadeBlocker() {
        if (statusBarBlockerView != null) return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            68,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            SAFE_OVERLAY_FLAGS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP }

        statusBarBlockerView = createComposeView {
            Box(modifier = Modifier.fillMaxSize().background(Color.Transparent))
        }.apply {
            setOnTouchListener { _, _ -> true }
        }
        try {
            windowManager.addView(statusBarBlockerView, params)
        } catch (_: Exception) {
        }
    }

    private fun dismissStatusBarShadeBlocker() {
        destroyComposeView(statusBarBlockerView)
        statusBarBlockerView = null
    }

    private fun showFilterOverlay() {
        if (filterOverlayView != null) return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            SAFE_OVERLAY_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        filterOverlayView = createComposeView {
            val rm by RedMagicEngine.state.collectAsStateWithLifecycle()
            val baseAlpha = rm.filter.alpha
            val hdrExtra = if (rm.hdrBoost) 0.03f else 0f
            val finalAlpha = (baseAlpha + hdrExtra).coerceIn(0f, 0.22f)
            val tint = if (rm.filter == RedMagicFilter.ORIGINAL && rm.hdrBoost) Color(0xFFFF551A) else Color(rm.filter.colorHex)
            if (finalAlpha > 0f) {
                Box(modifier = Modifier.fillMaxSize().background(tint.copy(alpha = finalAlpha)))
            }
        }
        try {
            windowManager.addView(filterOverlayView, params)
        } catch (_: Exception) {
        }
    }

    private fun dismissFilterOverlay() {
        destroyComposeView(filterOverlayView)
        filterOverlayView = null
    }

    private fun startRealScreenRecording(resultCode: Int, data: Intent) {
        try {
            val dm = resources.displayMetrics
            val width = (dm.widthPixels / 2) * 2
            val height = (dm.heightPixels / 2) * 2
            val density = dm.densityDpi

            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "GameTurbo_${System.currentTimeMillis()}.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/GameTurbo")
                }
            }
            val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return
            val pfd = contentResolver.openFileDescriptor(uri, "w") ?: return

            @Suppress("DEPRECATION")
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else MediaRecorder()
            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setOutputFile(pfd.fileDescriptor)
            recorder.setVideoSize(width, height)
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            recorder.setVideoEncodingBitRate(6_000_000)
            recorder.setVideoFrameRate(30)
            recorder.prepare()

            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val proj = mpm.getMediaProjection(resultCode, data)
            val vd = proj.createVirtualDisplay(
                "GameTurboRec", width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                recorder.surface, null, null
            )

            recorder.start()
            mediaProjection = proj
            mediaRecorder = recorder
            virtualDisplay = vd

            RedMagicEngine.update(applicationContext) { it.copy(isRecording = true, recordingSeconds = 0) }
            recordTimerJob?.cancel()
            recordTimerJob = serviceScope.launch {
                while (isActive && RedMagicEngine.state.value.isRecording) {
                    delay(1000L)
                    RedMagicEngine.update(applicationContext) { it.copy(recordingSeconds = it.recordingSeconds + 1) }
                }
            }
            Toast.makeText(this, "Recording Started", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, "Screen recording failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRealScreenRecording(showToast: Boolean) {
        recordTimerJob?.cancel()
        try {
            mediaRecorder?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaRecorder?.reset()
        } catch (_: Exception) {
        }
        try {
            mediaRecorder?.release()
        } catch (_: Exception) {
        }
        try {
            virtualDisplay?.release()
        } catch (_: Exception) {
        }
        try {
            mediaProjection?.stop()
        } catch (_: Exception) {
        }
        mediaRecorder = null
        virtualDisplay = null
        mediaProjection = null
        RedMagicEngine.update(applicationContext) { it.copy(isRecording = false, recordingSeconds = 0) }
        if (showToast) Toast.makeText(this, "Saved to Movies/GameTurbo", Toast.LENGTH_SHORT).show()
    }

    private fun hideAllOverlays() {
        closeSidePanel()
        destroyComposeView(edgeTriggerView)
        edgeTriggerView = null
        dismissFilterOverlay()
        dismissStatusBarShadeBlocker()
        dismissMiniFpsCounterOverlay()
    }

    override fun onDestroy() {
        isRunning = false
        monitorJob?.cancel()
        telemetryJob?.cancel()
        settingsJob?.cancel()
        stopRealScreenRecording(showToast = false)
        serviceScope.cancel()
        mainHandler.post { hideAllOverlays() }
        super.onDestroy()
    }
}
