package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.*
import com.example.service.GameTurboOverlayService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class PermissionStatus(
    val overlayGranted: Boolean = false,
    val accessibilityGranted: Boolean = false,
    val dndGranted: Boolean = false,
    val micGranted: Boolean = false
) {
    val allCoreGranted: Boolean
        get() = overlayGranted && accessibilityGranted && dndGranted && micGranted
}

/**
 * Clean, lightweight ViewModel for Game Magic Home Screen & App Picker.
 * All in-game toggles and telemetry live strictly inside the compact floating sidebar.
 */
class TurboViewModel(application: Application) : AndroidViewModel(application) {

    private val ctx = application.applicationContext

    val settings: StateFlow<MagicSettings> = TurboSettingsStore.state

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    private val _appsLoading = MutableStateFlow(true)
    val appsLoading: StateFlow<Boolean> = _appsLoading.asStateFlow()

    /**
     * Strictly contains ONLY the games and apps explicitly added by the user.
     */
    val addedApps: StateFlow<List<InstalledAppInfo>> = combine(_installedApps, settings) { all, s ->
        all.filter { s.selectedPackages.contains(it.packageName) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _permissions = MutableStateFlow(PermissionStatus())
    val permissions: StateFlow<PermissionStatus> = _permissions.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    init {
        TurboSettingsStore.init(ctx)
        NetworkMonitorEngine.registerCallback(ctx)
        refreshPermissions()
        loadApps()
    }

    fun refreshPermissions() {
        _permissions.value = PermissionStatus(
            overlayGranted = Settings.canDrawOverlays(ctx),
            accessibilityGranted = SystemTelemetryEngine.isAccessibilityEnabled(ctx),
            dndGranted = SystemTelemetryEngine.hasDndAccess(ctx),
            micGranted = VoiceChangerEngine.hasMicPermission(ctx)
        )
        if (_permissions.value.overlayGranted && settings.value.selectedPackages.isNotEmpty()) {
            ensureOverlayServiceArmed()
        }
    }

    fun loadApps() {
        viewModelScope.launch {
            _appsLoading.value = true
            _installedApps.value = SystemTelemetryEngine.loadLaunchableApps(ctx)
            _appsLoading.value = false
        }
    }

    fun showTransientMessage(msg: String) {
        viewModelScope.launch {
            _statusBanner.value = msg
            delay(2400L)
            if (_statusBanner.value == msg) _statusBanner.value = null
        }
    }

    fun addAppToMagic(packageName: String, displayName: String) {
        TurboSettingsStore.addPackage(packageName)
        SoundManager.playBalancedMode(ctx)
        ensureOverlayServiceArmed()
        showTransientMessage("Added $displayName to Game Magic")
    }

    fun removeAppFromMagic(packageName: String, displayName: String) {
        TurboSettingsStore.removePackage(packageName)
        showTransientMessage("Removed $displayName")
    }

    fun ensureOverlayServiceArmed() {
        if (!Settings.canDrawOverlays(ctx)) return
        if (!GameTurboOverlayService.isRunning) {
            val intent = Intent(ctx, GameTurboOverlayService::class.java).apply {
                action = GameTurboOverlayService.ACTION_START
            }
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ctx.startForegroundService(intent)
                } else {
                    ctx.startService(intent)
                }
            }
        }
    }

    fun playEpicLaunchSound() {
        SoundManager.playEpicLaunchPortal(ctx)
    }

    /**
     * Called right after the 900ms Cyber-Magic Launch Animation completes.
     * Arms the compact floating edge trigger and launches the target game package cleanly.
     */
    fun launchGameNow(app: InstalledAppInfo) {
        ensureOverlayServiceArmed()
        if (Settings.canDrawOverlays(ctx)) {
            val notifyIntent = Intent(ctx, GameTurboOverlayService::class.java).apply {
                action = GameTurboOverlayService.ACTION_FOREGROUND_CHANGED
                putExtra(GameTurboOverlayService.EXTRA_PACKAGE_NAME, app.packageName)
                putExtra(GameTurboOverlayService.EXTRA_IS_GAME_ACTIVE, true)
            }
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ctx.startForegroundService(notifyIntent)
                } else {
                    ctx.startService(notifyIntent)
                }
            }
        }

        val launchIntent = ctx.packageManager.getLaunchIntentForPackage(app.packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(launchIntent)
        } else {
            showTransientMessage("Unable to launch ${app.name}")
        }
    }
}
