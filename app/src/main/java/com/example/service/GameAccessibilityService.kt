package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.core.TurboSettingsStore

/**
 * 100% Anti-Cheat Safe AccessibilityService listening strictly for TYPE_WINDOW_STATE_CHANGED.
 * Strictly ignores home launchers and non-added apps so Game Magic NEVER appears on the home screen.
 */
class GameAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "GameAccessibilitySvc"

        @Volatile
        var instance: GameAccessibilityService? = null
            private set

        fun triggerSystemScreenshot(): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
            val svc = instance ?: return false
            return try {
                svc.performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
            } catch (e: Exception) {
                Log.e(TAG, "Screenshot action failed", e)
                false
            }
        }
    }

    private var lastForegroundPackage: String? = null
    private var cachedLauncherPackages: Set<String> = emptySet()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        TurboSettingsStore.init(applicationContext)
        cachedLauncherPackages = resolveHomeLaunchers()
    }

    private fun resolveHomeLaunchers(): Set<String> {
        return try {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val infos = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            infos.mapNotNull { it.activityInfo?.packageName }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun isHomeOrSystemOverlay(pkg: String): Boolean {
        if (cachedLauncherPackages.contains(pkg)) return true
        val lower = pkg.lowercase()
        return lower.contains("launcher") ||
            lower == "com.miui.home" ||
            lower == "com.mi.android.globallauncher" ||
            lower == "com.android.launcher3" ||
            lower == "com.google.android.apps.nexuslauncher"
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val className = event.className?.toString().orEmpty()

        // Ignore transient IME keyboards and our own overlay windows (not MainActivity)
        val isOurMainActivity = pkg == packageName && className.contains("MainActivity")
        if ((pkg == packageName && !isOurMainActivity) ||
            pkg.contains("inputmethod") ||
            pkg.contains("keyboard")
        ) {
            return
        }

        // Ignore transient system dialogs unless user pulled down notification shade / returned home
        if (pkg == "com.android.systemui" && !className.contains("StatusBar") && !className.contains("NotificationShade")) {
            return
        }

        if (pkg == lastForegroundPackage) return
        lastForegroundPackage = pkg

        val selected = TurboSettingsStore.state.value.selectedPackages
        val isLauncher = isHomeOrSystemOverlay(pkg)
        val isTrackedGame = !isLauncher && !isOurMainActivity && selected.contains(pkg)

        val svcIntent = Intent(applicationContext, GameTurboOverlayService::class.java).apply {
            action = GameTurboOverlayService.ACTION_FOREGROUND_CHANGED
            putExtra(GameTurboOverlayService.EXTRA_PACKAGE_NAME, pkg)
            putExtra(GameTurboOverlayService.EXTRA_IS_GAME_ACTIVE, isTrackedGame)
        }
        try {
            if (isTrackedGame || GameTurboOverlayService.isRunning) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(svcIntent)
                } else {
                    startService(svcIntent)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dispatch foreground package change", e)
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }
}
