package com.example.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class PerformanceMode(val label: String) {
    BALANCED("Balanced"),
    HEAVY("Heavy"),
    ULTRA("Ultra")
}

enum class VoicePreset(val label: String) {
    ORIGINAL("Original"),
    GIRL("Girl"),
    ROBOT("Robot"),
    DEEP_DEMON("Demon"),
    ANIME_HERO("Anime"),
    RADIO("Radio")
}

enum class CrosshairStyle(val label: String) {
    DOT("Dot"),
    PLUS("Plus"),
    CIRCLE("Circle"),
    TACTICAL("Pro")
}

data class MagicSettings(
    val selectedPackages: Set<String> = emptySet(),
    val performanceMode: PerformanceMode = PerformanceMode.BALANCED,
    val sustainedPerformanceEnabled: Boolean = true,
    val use24HourClock: Boolean = false,
    val voiceChangerEnabled: Boolean = false,
    val voicePreset: VoicePreset = VoicePreset.ORIGINAL,
    val liveMicMonitorEnabled: Boolean = false,
    val crosshairEnabled: Boolean = false,
    val crosshairStyle: CrosshairStyle = CrosshairStyle.TACTICAL,
    val crosshairColorHex: Long = 0xFF00E676L,
    val crosshairSizeDp: Int = 22,
    val dndEnabled: Boolean = false,
    val touchGuardEnabled: Boolean = false,
    val iceCoolingEnabled: Boolean = false,
    val overheatThresholdC: Float = 41.0f,
    val lowBatteryThresholdPct: Int = 20,
    val pingShieldEnabled: Boolean = true,
    val pingAlertThresholdMs: Int = 120,
    // Sidebar Customisation
    val edgeOnLeft: Boolean = false,
    val edgeSizeScale: Float = 1.0f,
    val edgeOpacity: Float = 0.85f,
    val soundEffectsEnabled: Boolean = true,
    val fastAnimationsEnabled: Boolean = true,
    // Ephemeral runtime readouts
    val lastFreedRamMb: Long = 0L,
    val coolingBaselineTempC: Float = 0f
)

/**
 * SharedPreferences-backed single source of truth for Game Magic.
 * Synchronizes instantaneously between the Home Screen, App Picker, and In-Game Compact Sidebar.
 */
object TurboSettingsStore {

    private const val PREFS_NAME = "game_magic_prefs_v2"
    private const val KEY_PACKAGES = "selected_packages"
    private const val KEY_MODE = "performance_mode"
    private const val KEY_SUSTAINED = "sustained_perf"
    private const val KEY_24H_CLOCK = "clock_24h"
    private const val KEY_VOICE_ON = "voice_changer_on"
    private const val KEY_VOICE_PRESET = "voice_preset"
    private const val KEY_MIC_MONITOR = "mic_monitor_on"
    private const val KEY_CROSSHAIR_ON = "crosshair_on"
    private const val KEY_CROSSHAIR_STYLE = "crosshair_style"
    private const val KEY_CROSSHAIR_COLOR = "crosshair_color"
    private const val KEY_CROSSHAIR_SIZE = "crosshair_size"
    private const val KEY_DND = "dnd_enabled"
    private const val KEY_TOUCH_GUARD = "touch_guard_enabled"
    private const val KEY_ICE_COOL = "ice_cooling_enabled"
    private const val KEY_OVERHEAT_LIMIT = "overheat_limit_c"
    private const val KEY_LOW_BAT_LIMIT = "low_bat_limit_pct"
    private const val KEY_PING_SHIELD = "ping_shield_enabled"
    private const val KEY_PING_LIMIT = "ping_limit_ms"
    private const val KEY_EDGE_LEFT = "edge_on_left"
    private const val KEY_EDGE_SIZE = "edge_size_scale"
    private const val KEY_EDGE_OPACITY = "edge_opacity"
    private const val KEY_SOUND = "sound_effects"
    private const val KEY_FAST_ANIM = "fast_animations"

    private var prefs: SharedPreferences? = null
    private val _state = MutableStateFlow(MagicSettings())
    val state: StateFlow<MagicSettings> = _state.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sp
        _state.value = readFromPrefs(sp)
    }

    private fun readFromPrefs(sp: SharedPreferences): MagicSettings {
        val mode = runCatching {
            PerformanceMode.valueOf(sp.getString(KEY_MODE, PerformanceMode.BALANCED.name) ?: PerformanceMode.BALANCED.name)
        }.getOrDefault(PerformanceMode.BALANCED)

        val voice = runCatching {
            VoicePreset.valueOf(sp.getString(KEY_VOICE_PRESET, VoicePreset.ORIGINAL.name) ?: VoicePreset.ORIGINAL.name)
        }.getOrDefault(VoicePreset.ORIGINAL)

        val chStyle = runCatching {
            CrosshairStyle.valueOf(sp.getString(KEY_CROSSHAIR_STYLE, CrosshairStyle.TACTICAL.name) ?: CrosshairStyle.TACTICAL.name)
        }.getOrDefault(CrosshairStyle.TACTICAL)

        return MagicSettings(
            selectedPackages = sp.getStringSet(KEY_PACKAGES, emptySet())?.toSet() ?: emptySet(),
            performanceMode = mode,
            sustainedPerformanceEnabled = sp.getBoolean(KEY_SUSTAINED, true),
            use24HourClock = sp.getBoolean(KEY_24H_CLOCK, false),
            voiceChangerEnabled = sp.getBoolean(KEY_VOICE_ON, false),
            voicePreset = voice,
            liveMicMonitorEnabled = sp.getBoolean(KEY_MIC_MONITOR, false),
            crosshairEnabled = sp.getBoolean(KEY_CROSSHAIR_ON, false),
            crosshairStyle = chStyle,
            crosshairColorHex = sp.getLong(KEY_CROSSHAIR_COLOR, 0xFF00E676L),
            crosshairSizeDp = sp.getInt(KEY_CROSSHAIR_SIZE, 22),
            dndEnabled = sp.getBoolean(KEY_DND, false),
            touchGuardEnabled = sp.getBoolean(KEY_TOUCH_GUARD, false),
            iceCoolingEnabled = sp.getBoolean(KEY_ICE_COOL, false),
            overheatThresholdC = sp.getFloat(KEY_OVERHEAT_LIMIT, 41.0f),
            lowBatteryThresholdPct = sp.getInt(KEY_LOW_BAT_LIMIT, 20),
            pingShieldEnabled = sp.getBoolean(KEY_PING_SHIELD, true),
            pingAlertThresholdMs = sp.getInt(KEY_PING_LIMIT, 120),
            edgeOnLeft = sp.getBoolean(KEY_EDGE_LEFT, false),
            edgeSizeScale = sp.getFloat(KEY_EDGE_SIZE, 1.0f),
            edgeOpacity = sp.getFloat(KEY_EDGE_OPACITY, 0.85f),
            soundEffectsEnabled = sp.getBoolean(KEY_SOUND, true),
            fastAnimationsEnabled = sp.getBoolean(KEY_FAST_ANIM, true)
        )
    }

    fun update(transform: (MagicSettings) -> MagicSettings) {
        _state.update { current ->
            val next = transform(current)
            persist(next)
            next
        }
    }

    fun addPackage(packageName: String) {
        update { it.copy(selectedPackages = it.selectedPackages + packageName) }
    }

    fun removePackage(packageName: String) {
        update { it.copy(selectedPackages = it.selectedPackages - packageName) }
    }

    private fun persist(s: MagicSettings) {
        prefs?.edit()?.apply {
            putStringSet(KEY_PACKAGES, s.selectedPackages)
            putString(KEY_MODE, s.performanceMode.name)
            putBoolean(KEY_SUSTAINED, s.sustainedPerformanceEnabled)
            putBoolean(KEY_24H_CLOCK, s.use24HourClock)
            putBoolean(KEY_VOICE_ON, s.voiceChangerEnabled)
            putString(KEY_VOICE_PRESET, s.voicePreset.name)
            putBoolean(KEY_MIC_MONITOR, s.liveMicMonitorEnabled)
            putBoolean(KEY_CROSSHAIR_ON, s.crosshairEnabled)
            putString(KEY_CROSSHAIR_STYLE, s.crosshairStyle.name)
            putLong(KEY_CROSSHAIR_COLOR, s.crosshairColorHex)
            putInt(KEY_CROSSHAIR_SIZE, s.crosshairSizeDp)
            putBoolean(KEY_DND, s.dndEnabled)
            putBoolean(KEY_TOUCH_GUARD, s.touchGuardEnabled)
            putBoolean(KEY_ICE_COOL, s.iceCoolingEnabled)
            putFloat(KEY_OVERHEAT_LIMIT, s.overheatThresholdC)
            putInt(KEY_LOW_BAT_LIMIT, s.lowBatteryThresholdPct)
            putBoolean(KEY_PING_SHIELD, s.pingShieldEnabled)
            putInt(KEY_PING_LIMIT, s.pingAlertThresholdMs)
            putBoolean(KEY_EDGE_LEFT, s.edgeOnLeft)
            putFloat(KEY_EDGE_SIZE, s.edgeSizeScale)
            putFloat(KEY_EDGE_OPACITY, s.edgeOpacity)
            putBoolean(KEY_SOUND, s.soundEffectsEnabled)
            putBoolean(KEY_FAST_ANIM, s.fastAnimationsEnabled)
            apply()
        }
    }
}
