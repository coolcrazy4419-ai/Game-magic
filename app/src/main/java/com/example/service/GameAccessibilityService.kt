package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.core.TurboSettingsStore

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

    private var lastDispatchedState: Pair<String, Boolean>? = null
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

    private fun isHomeLauncher(pkg: String): Boolean {
        if (cachedLauncherPackages.contains(pkg)) return true
        val lower = pkg.lowercase()
        return lower.contains("launcher") ||
            lower == "com.miui.home" ||
            lower == "com.mi.android.globallauncher" ||
            lower == "com.android.launcher3" ||
            lower == "com.google.android.apps.nexuslauncher"
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) return

        val activePkg = rootInActiveWindow?.packageName?.toString()
            ?: event.packageName?.toString()
            ?: return

        val className = event.className?.toString().orEmpty()
        val isOurMainActivity = activePkg == packageName && className.contains("MainActivity")

        if ((activePkg == packageName && !isOurMainActivity) ||
            activePkg.contains("inputmethod") ||
            activePkg.contains("keyboard")
        ) {
            return
        }

        if (activePkg == "com.android.systemui" && !isHomeLauncher(activePkg)) {
            return
        }

        val selected = TurboSettingsStore.state.value.selectedPackages
        val isLauncher = isHomeLauncher(activePkg)
        val isTrackedGame = !isLauncher && !isOurMainActivity && selected.contains(activePkg)

        val newState = activePkg to isTrackedGame
        if (newState == lastDispatchedState) return
        lastDispatchedState = newState

        val svcIntent = Intent(applicationContext, GameTurboOverlayService::class.java).apply {
            action = GameTurboOverlayService.ACTION_FOREGROUND_CHANGED
            putExtra(GameTurboOverlayService.EXTRA_PACKAGE_NAME, activePkg)
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
