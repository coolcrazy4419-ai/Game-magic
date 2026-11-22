package com.example.core

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.util.Log
import android.view.Choreographer
import android.view.Window
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SystemTelemetry(
    val ramUsagePercent: Int = 0,
    val ramAvailMb: Long = 0L,
    val ramTotalMb: Long = 0L,
    val batteryTempC: Float = 0f,
    val batteryPercent: Int = 0,
    val isCharging: Boolean = false,
    val refreshRateHz: Int = 60,
    val liveFps: Int = 60
)

data class InstalledAppInfo(
    val name: String,
    val packageName: String,
    val isGame: Boolean,
    val icon: Drawable?
)

data class BoostResult(
    val freedMb: Long,
    val processesTrimmed: Int
)

/**
 * 100% Anti-Cheat Safe Hardware Telemetry & System Optimization Engine.
 * Uses strictly legitimate Android public APIs (ActivityManager.MemoryInfo, BatteryManager,
 * Choreographer, NotificationManager, Window.setSustainedPerformanceMode).
 */
object SystemTelemetryEngine {

    private const val TAG = "SystemTelemetryEngine"

    @Volatile
    private var measuredFps: Int = 60
    private var frameCount = 0
    private var fpsWindowStartNanos = 0L
    private var choreographerRunning = false

    private val fpsCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (fpsWindowStartNanos == 0L) {
                fpsWindowStartNanos = frameTimeNanos
            }
            frameCount++
            val elapsed = frameTimeNanos - fpsWindowStartNanos
            if (elapsed >= 1_000_000_000L) {
                measuredFps = ((frameCount * 1_000_000_000L) / elapsed).toInt().coerceIn(1, 144)
                frameCount = 0
                fpsWindowStartNanos = frameTimeNanos
            }
            if (choreographerRunning) {
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
    }

    /**
     * Smart Sleep compliant: run Choreographer FPS monitor ONLY when sidebar is expanded
     * so active gameplay retains 100% CPU/GPU priority while collapsed.
     */
    fun startFpsMonitor() {
        if (choreographerRunning) return
        choreographerRunning = true
        frameCount = 0
        fpsWindowStartNanos = 0L
        Choreographer.getInstance().postFrameCallback(fpsCallback)
    }

    fun stopFpsMonitor() {
        choreographerRunning = false
        Choreographer.getInstance().removeFrameCallback(fpsCallback)
    }

    fun readTelemetry(context: Context): SystemTelemetry {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val totalMb = (memInfo.totalMem / (1024L * 1024L)).coerceAtLeast(1L)
        val availMb = memInfo.availMem / (1024L * 1024L)
        val usedMb = (totalMb - availMb).coerceAtLeast(0L)
        val ramPct = ((usedMb * 100L) / totalMb).toInt().coerceIn(0, 100)

        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 0
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val batteryPct = if (scale > 0 && level >= 0) ((level * 100) / scale).coerceIn(0, 100) else 0
        val tempRaw = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val batteryTemp = tempRaw / 10f
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val hz = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                (context.display?.refreshRate ?: 60f).toInt()
            } else {
                @Suppress("DEPRECATION")
                wm.defaultDisplay.refreshRate.toInt()
            }
        } catch (_: Exception) {
            60
        }

        val currentFps = if (choreographerRunning) measuredFps.coerceAtMost(hz.coerceAtLeast(60)) else hz

        return SystemTelemetry(
            ramUsagePercent = ramPct,
            ramAvailMb = availMb,
            ramTotalMb = totalMb,
            batteryTempC = batteryTemp,
            batteryPercent = batteryPct,
            isCharging = isCharging,
            refreshRateHz = hz,
            liveFps = currentFps
        )
    }

    /**
     * Legitimate background memory cache trim using ActivityManager.killBackgroundProcesses
     * + JVM GC. Never touches foreground game processes.
     */
    suspend fun boostMemory(context: Context, aggressive: Boolean): BoostResult = withContext(Dispatchers.Default) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val before = ActivityManager.MemoryInfo()
        am.getMemoryInfo(before)

        val selfPkg = context.packageName
        val selectedGames = TurboSettingsStore.state.value.selectedPackages
        var trimmed = 0

        try {
            val running = am.runningAppProcesses.orEmpty()
            for (proc in running) {
                if (proc.importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE) {
                    proc.pkgList?.forEach { pkg ->
                        if (pkg != selfPkg && !selectedGames.contains(pkg)) {
                            runCatching {
                                am.killBackgroundProcesses(pkg)
                                trimmed++
                            }
                        }
                    }
                }
            }
            if (aggressive) {
                val installed = context.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                for (app in installed) {
                    val isUserApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0
                    if (isUserApp && app.packageName != selfPkg && !selectedGames.contains(app.packageName)) {
                        runCatching {
                            am.killBackgroundProcesses(app.packageName)
                            trimmed++
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Background cache trim error", e)
        }

        System.gc()
        Runtime.getRuntime().gc()

        val after = ActivityManager.MemoryInfo()
        am.getMemoryInfo(after)
        val freedMb = ((after.availMem - before.availMem) / (1024L * 1024L)).coerceAtLeast(0L)
        TurboSettingsStore.update { it.copy(lastFreedRamMb = freedMb) }
        BoostResult(freedMb = freedMb, processesTrimmed = trimmed)
    }

    /**
     * Legitimate Android Sustained Performance Mode API (API 24+).
     */
    fun applySustainedPerformance(window: Window?, enabled: Boolean) {
        if (window == null) return
        try {
            window.setSustainedPerformanceMode(enabled)
        } catch (e: Exception) {
            Log.w(TAG, "SustainedPerformanceMode not supported on this window: ${e.message}")
        }
    }

    fun setDndEnabled(context: Context, enabled: Boolean): Boolean {
        return try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!nm.isNotificationPolicyAccessGranted) return false
            nm.setInterruptionFilter(
                if (enabled) NotificationManager.INTERRUPTION_FILTER_PRIORITY
                else NotificationManager.INTERRUPTION_FILTER_ALL
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "DND toggle error", e)
            false
        }
    }

    suspend fun loadLaunchableApps(context: Context): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val selfPkg = context.packageName
        val discovered = linkedMapOf<String, ApplicationInfo>()

        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(mainIntent, 0)
        }
        for (ri in resolveInfos) {
            val info = ri.activityInfo?.applicationInfo ?: continue
            if (info.packageName != selfPkg) {
                discovered[info.packageName] = info
            }
        }

        runCatching {
            val allInstalled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledApplications(0)
            }
            for (appInfo in allInstalled) {
                if (appInfo.packageName == selfPkg || discovered.containsKey(appInfo.packageName)) continue
                if (pm.getLaunchIntentForPackage(appInfo.packageName) != null) {
                    discovered[appInfo.packageName] = appInfo
                }
            }
        }

        discovered.values
            .map { appInfo ->
                val label = runCatching { appInfo.loadLabel(pm).toString() }.getOrDefault("App")
                val icon = runCatching { appInfo.loadIcon(pm) }.getOrNull()
                val isGame = (appInfo.category == ApplicationInfo.CATEGORY_GAME) ||
                    ((appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0)
                InstalledAppInfo(
                    name = label,
                    packageName = appInfo.packageName,
                    isGame = isGame,
                    icon = icon
                )
            }
            .sortedWith(compareByDescending<InstalledAppInfo> { it.isGame }.thenBy { it.name.lowercase() })
    }

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun hasDndAccess(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.isNotificationPolicyAccessGranted
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun isAccessibilityEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains(context.packageName)
    }
}
