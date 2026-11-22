package com.example.core

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.RandomAccessFile
import java.net.InetSocketAddress
import java.net.Socket

enum class RedMagicPerfMode(val label: String) {
    BALANCED("Balanced"),
    HEAVY("Heavy"),
    ULTRA("Ultra"),
    LAG_FREE("Lag-free")
}

enum class RedMagicFilter(val label: String, val colorHex: Long, val alpha: Float) {
    ORIGINAL("Original", 0x00000000, 0f),
    PRO_CLARITY("Pro Clarity", 0xFF00E5FF, 0.05f),
    NIGHT_VISION("Night Vision", 0xFF76FF03, 0.08f),
    VIVID_BOOST("Vivid Boost", 0xFFFF551A, 0.07f),
    EYE_CARE("Eye Care", 0xFFFF9E00, 0.12f),
    COOL_ICE("Cool Ice", 0xFF2979FF, 0.08f)
}

enum class RedMagicSoundMode(val label: String) {
    BASS("Bass"),
    FOOTSTEPS("Footsteps"),
    BALANCED("Balanced"),
    OFF("Off")
}

data class RedMagicState(
    val perfMode: RedMagicPerfMode = RedMagicPerfMode.ULTRA,
    val filter: RedMagicFilter = RedMagicFilter.ORIGINAL,
    val sharpness: Float = 0.72f,
    val contrast: Float = 0.68f,
    val saturation: Float = 0.64f,
    val hdrBoost: Boolean = true,
    val targetFps: Int = 120,
    val resolutionLabel: String = "FHD",
    val shadowsEnabled: Boolean = true,
    val antiAliasing: Boolean = true,
    val smoothTouch: Boolean = true,
    val soundMode: RedMagicSoundMode = RedMagicSoundMode.FOOTSTEPS,
    val voiceChanger: Boolean = false,
    val micMute: Boolean = false,
    val profilePerGame: Boolean = true,
    val blockAlerts: Boolean = true,
    val lockControlCenter: Boolean = true,
    val smallCallBanner: Boolean = true,
    val brightnessLock: Boolean = false,
    val aiIsolation: Boolean = false,
    val showMiniFpsCounter: Boolean = false,
    val touchSensitivity: Float = 0.80f,
    val touchResponse: Float = 0.88f,
    val isRecording: Boolean = false,
    val recordingSeconds: Int = 0,
    val activeGamePkg: String? = null
)

data class HardwareSnapshot(
    val deviceName: String = Build.MODEL,
    val androidVersion: String = "Android ${Build.VERSION.RELEASE}",
    val chipset: String = "Dimensity 7025-Ultra",
    val totalRamGb: Int = 8,
    val totalStorageGb: Int = 128,
    val refreshRateHz: Int = 120,
    val fps: Int = 90,
    val pingMs: Int = 24,
    val cpuPercent: Int = 36,
    val gpuPercent: Int = 48,
    val ramUsedGb: Float = 4.1f,
    val tempC: Float = 36.2f,
    val batteryPct: Int = 85
)

object RedMagicEngine {
    private const val PREFS_NAME = "red_magic_final_prefs"
    private val _state = MutableStateFlow(RedMagicState())
    val state: StateFlow<RedMagicState> = _state.asStateFlow()

    private val _hardware = MutableStateFlow(HardwareSnapshot())
    val hardware: StateFlow<HardwareSnapshot> = _hardware.asStateFlow()

    private var initialized = false
    private var lastCpuTotal = 0L
    private var lastCpuIdle = 0L
    @Volatile private var cachedPingMs = 24

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        loadProfile(context, null)
    }

    private fun prefixFor(pkg: String?): String {
        return if (!pkg.isNullOrBlank() && _state.value.profilePerGame) "game_${pkg}_" else "global_"
    }

    fun loadProfile(context: Context, pkg: String?) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val perGame = sp.getBoolean("global_profilePerGame", true)
        val p = if (!pkg.isNullOrBlank() && perGame && sp.contains("game_${pkg}_perfMode")) "game_${pkg}_" else "global_"

        _state.value = RedMagicState(
            perfMode = RedMagicPerfMode.entries.getOrElse(sp.getInt("${p}perfMode", 2)) { RedMagicPerfMode.ULTRA },
            filter = RedMagicFilter.entries.getOrElse(sp.getInt("${p}filter", 0)) { RedMagicFilter.ORIGINAL },
            sharpness = sp.getFloat("${p}sharpness", 0.72f),
            contrast = sp.getFloat("${p}contrast", 0.68f),
            saturation = sp.getFloat("${p}saturation", 0.64f),
            hdrBoost = sp.getBoolean("${p}hdrBoost", true),
            targetFps = sp.getInt("${p}targetFps", 120),
            resolutionLabel = sp.getString("${p}res", "FHD") ?: "FHD",
            shadowsEnabled = sp.getBoolean("${p}shadows", true),
            antiAliasing = sp.getBoolean("${p}aa", true),
            smoothTouch = sp.getBoolean("${p}smoothTouch", true),
            soundMode = RedMagicSoundMode.entries.getOrElse(sp.getInt("${p}soundMode", 1)) { RedMagicSoundMode.FOOTSTEPS },
            voiceChanger = sp.getBoolean("${p}voiceChanger", false),
            micMute = sp.getBoolean("${p}micMute", false),
            profilePerGame = perGame,
            blockAlerts = sp.getBoolean("${p}blockAlerts", true),
            lockControlCenter = sp.getBoolean("${p}lockControlCenter", true),
            smallCallBanner = sp.getBoolean("${p}smallCallBanner", true),
            brightnessLock = sp.getBoolean("${p}brightnessLock", false),
            aiIsolation = sp.getBoolean("${p}aiIsolation", false),
            showMiniFpsCounter = sp.getBoolean("${p}showMiniFpsCounter", false),
            touchSensitivity = sp.getFloat("${p}touchSens", 0.80f),
            touchResponse = sp.getFloat("${p}touchResp", 0.88f),
            isRecording = _state.value.isRecording,
            recordingSeconds = _state.value.recordingSeconds,
            activeGamePkg = pkg
        )
    }

    fun update(context: Context, transform: (RedMagicState) -> RedMagicState) {
        val next = transform(_state.value)
        _state.value = next
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val p = prefixFor(next.activeGamePkg)
        sp.edit()
            .putBoolean("global_profilePerGame", next.profilePerGame)
            .putInt("${p}perfMode", next.perfMode.ordinal)
            .putInt("${p}filter", next.filter.ordinal)
            .putFloat("${p}sharpness", next.sharpness)
            .putFloat("${p}contrast", next.contrast)
            .putFloat("${p}saturation", next.saturation)
            .putBoolean("${p}hdrBoost", next.hdrBoost)
            .putInt("${p}targetFps", next.targetFps)
            .putString("${p}res", next.resolutionLabel)
            .putBoolean("${p}shadows", next.shadowsEnabled)
            .putBoolean("${p}aa", next.antiAliasing)
            .putBoolean("${p}smoothTouch", next.smoothTouch)
            .putInt("${p}soundMode", next.soundMode.ordinal)
            .putBoolean("${p}voiceChanger", next.voiceChanger)
            .putBoolean("${p}micMute", next.micMute)
            .putBoolean("${p}blockAlerts", next.blockAlerts)
            .putBoolean("${p}lockControlCenter", next.lockControlCenter)
            .putBoolean("${p}smallCallBanner", next.smallCallBanner)
            .putBoolean("${p}brightnessLock", next.brightnessLock)
            .putBoolean("${p}aiIsolation", next.aiIsolation)
            .putBoolean("${p}showMiniFpsCounter", next.showMiniFpsCounter)
            .putFloat("${p}touchSens", next.touchSensitivity)
            .putFloat("${p}touchResp", next.touchResponse)
            .apply()
    }

    fun addPlaytimeSeconds(context: Context, pkg: String, seconds: Long) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val prev = sp.getLong("playtime_$pkg", 0L)
        sp.edit().putLong("playtime_$pkg", prev + seconds).apply()
    }

    fun getPlaytimeHoursText(context: Context, pkg: String): String {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val secs = sp.getLong("playtime_$pkg", 0L)
        val hrs = secs / 3600f
        return if (hrs < 0.1f) "${(secs / 60)}m" else "${"%.1f".format(hrs)}h"
    }

    fun setBrightnessLock(context: Context, lock: Boolean) {
        update(context) { it.copy(brightnessLock = lock) }
        if (Settings.System.canWrite(context)) {
            runCatching {
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    if (lock) Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                    else Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                )
            }
        }
    }

    fun refreshHardware(context: Context) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)
        val totalRam = ((memInfo.totalMem / (1024.0 * 1024 * 1024)) + 0.5).toInt().coerceAtLeast(4)
        val usedRam = ((memInfo.totalMem - memInfo.availMem) / (1024f * 1024f * 1024f)).coerceIn(1.2f, totalRam.toFloat())

        val stat = StatFs(Environment.getDataDirectory().path)
        val totalStorage = ((stat.totalBytes / (1024.0 * 1024 * 1024)) + 0.5).toInt().coerceAtLeast(64)

        val battIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 80) ?: 80
        val scale = battIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val battPct = ((level * 100f) / scale.coerceAtLeast(1)).toInt()
        val tempTenths = battIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 360) ?: 360
        val tempC = tempTenths / 10f

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        @Suppress("DEPRECATION")
        val hz = wm?.defaultDisplay?.refreshRate?.toInt()?.coerceAtLeast(60) ?: 120

        val cpuPct = readCpuUsagePercent()
        val gpuPct = (cpuPct * 1.12f).toInt().coerceIn(16, 94)
        val liveFps = SystemTelemetryEngine.currentFps.value.takeIf { it > 0 } ?: _state.value.targetFps.coerceAtMost(hz)

        runCatching {
            val start = SystemClock.elapsedRealtime()
            Socket().use { socket ->
                socket.connect(InetSocketAddress("1.1.1.1", 53), 400)
            }
            cachedPingMs = (SystemClock.elapsedRealtime() - start).toInt().coerceIn(12, 180)
        }

        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && Build.SOC_MODEL != Build.UNKNOWN) {
            Build.SOC_MODEL
        } else {
            "Dimensity 7025-Ultra"
        }

        _hardware.value = HardwareSnapshot(
            deviceName = Build.MODEL,
            androidVersion = "Android ${Build.VERSION.RELEASE}",
            chipset = soc,
            totalRamGb = totalRam,
            totalStorageGb = totalStorage,
            refreshRateHz = hz,
            fps = liveFps,
            pingMs = cachedPingMs,
            cpuPercent = cpuPct,
            gpuPercent = gpuPct,
            ramUsedGb = usedRam,
            tempC = tempC,
            batteryPct = battPct
        )
    }

    private fun readCpuUsagePercent(): Int {
        return runCatching {
            RandomAccessFile("/proc/stat", "r").use { reader ->
                val parts = reader.readLine().trim().split("\\s+".toRegex())
                val user = parts[1].toLong()
                val nice = parts[2].toLong()
                val sys = parts[3].toLong()
                val idle = parts[4].toLong()
                val total = user + nice + sys + idle
                val diffTotal = (total - lastCpuTotal).coerceAtLeast(1L)
                val diffIdle = (idle - lastCpuIdle).coerceAtLeast(0L)
                lastCpuTotal = total
                lastCpuIdle = idle
                (((diffTotal - diffIdle) * 100L) / diffTotal).toInt().coerceIn(15, 95)
            }
        }.getOrDefault(35)
    }
}
