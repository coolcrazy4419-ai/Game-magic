package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.core.TurboSettingsStore

/**
 * 100% Anti-Cheat Safe AccessibilityService listening strictly for TYPE_WINDOW_STATE_CHANGED.
 * Never reads window content, never performs automated clicks/macros (no dispatchGesture).
 * Detects when an added game enters or leaves the foreground and supports 1-tap screenshots.
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

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        TurboSettingsStore.init(applicationContext)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val className = event.className?.toString().orEmpty()

        val isOurMainActivity = pkg == packageName && className.contains("MainActivity")
        if (pkg == "com.android.systemui" ||
            (pkg == packageName && !isOurMainActivity) ||
            pkg.contains("inputmethod") ||
            pkg.contains("keyboard")
        ) {
            return
        }

        if (pkg == lastForegroundPackage) return
        lastForegroundPackage = pkg

        val selected = TurboSettingsStore.state.value.selectedPackages
        val isTrackedGame = !isOurMainActivity && selected.contains(pkg)

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
