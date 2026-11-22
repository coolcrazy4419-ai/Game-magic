package com.example.core

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Procedural PCM AudioTrack Sound Synthesizer & Haptic Engine for Game Magic.
 * Zero external MP3/WAV dependencies — generates crisp, futuristic waveforms in memory.
 */
object SoundManager {

    private const val TAG = "SoundManager"
    private const val SAMPLE_RATE = 44100
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private enum class WaveShape { SINE, TRIANGLE, SAWTOOTH }

    private fun canPlaySound(context: Context): Boolean {
        if (!TurboSettingsStore.state.value.soundEffectsEnabled) return false
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (am != null && am.ringerMode == AudioManager.RINGER_MODE_SILENT) return false
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (nm != null && nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE) {
                return false
            }
            true
        } catch (_: Exception) {
            true
        }
    }

    /**
     * Crisp futuristic power-up chime played immediately on opening Game Magic.
     */
    fun playAppOpenChime(context: Context) {
        vibrate(context, longArrayOf(0, 20, 30, 35), intArrayOf(0, 110, 0, 175))
        if (!canPlaySound(context)) return
        scope.launch {
            val rise = synthesizeHarmonicSweep(360.0, 720.0, 140, 0.36)
            val chord = synthesizeMagicChord(doubleArrayOf(523.25, 659.25, 783.99, 1046.50), 260, 0.38)
            playPcm(concat(rise, chord))
        }
    }

    /**
     * 820ms rising synthesized turbine-portal sound for the Tagda Launch Sequence.
     */
    fun playEpicLaunchPortal(context: Context) {
        vibrate(context, longArrayOf(0, 40, 50, 55, 40, 85), intArrayOf(0, 120, 0, 180, 0, 255))
        if (!canPlaySound(context)) return
        scope.launch {
            val subBuild = synthesizeSubTurbine(110.0, 480.0, 360, 0.45)
            val portalWarp = synthesizeHarmonicSweep(480.0, 1380.0, 280, 0.48)
            val lockBurst = synthesizeMagicChord(doubleArrayOf(659.25, 987.77, 1318.51), 210, 0.42)
            playPcm(concat(subBuild, portalWarp, lockBurst))
        }
    }

    /**
     * High-energy turbo jet-turbine + sub-bass boost blast for 1-Tap Turbo Boost.
     */
    fun playTurboBoostBlast(context: Context) {
        vibrate(context, longArrayOf(0, 35, 25, 65), intArrayOf(0, 170, 0, 255))
        if (!canPlaySound(context)) return
        scope.launch {
            val subPunch = synthesizeSubTurbine(85.0, 360.0, 170, 0.52)
            val jetRise = synthesizeHarmonicSweep(360.0, 1240.0, 190, 0.46)
            val shimmer = synthesizeBell(1560.0, 140, 0.34)
            playPcm(concat(subPunch, jetRise, shimmer))
        }
    }

    /**
     * Calm dark-cyan chime for Balanced Mode.
     */
    fun playBalancedMode(context: Context) {
        vibrate(context, longArrayOf(0, 20), intArrayOf(0, 95))
        if (!canPlaySound(context)) return
        scope.launch {
            val c1 = synthesizeBell(587.33, 95, 0.34)
            val c2 = synthesizeBell(880.0, 150, 0.36)
            playPcm(concat(c1, c2))
        }
    }

    /**
     * Mechanical lock-in + deep violet thump for Heavy Mode.
     */
    fun playHeavyMode(context: Context) {
        vibrate(context, longArrayOf(0, 32, 22, 42), intArrayOf(0, 160, 0, 210))
        if (!canPlaySound(context)) return
        scope.launch {
            val click = synthesizeSegment(180.0, 95.0, 55, WaveShape.TRIANGLE, 0.50)
            val gap = ShortArray((SAMPLE_RATE * 0.02).toInt())
            val lock = synthesizeSegment(260.0, 520.0, 145, WaveShape.SAWTOOTH, 0.36)
            playPcm(concat(click, gap, lock))
        }
    }

    /**
     * High-tech crimson power-surge dual harmonic sweep for Ultra Mode.
     */
    fun playUltraMode(context: Context) {
        vibrate(context, longArrayOf(0, 45, 45, 75), intArrayOf(0, 220, 0, 255))
        if (!canPlaySound(context)) return
        scope.launch {
            val surge1 = synthesizeSubTurbine(140.0, 680.0, 160, 0.48)
            val surge2 = synthesizeHarmonicSweep(680.0, 1480.0, 210, 0.46)
            playPcm(concat(surge1, surge2))
        }
    }

    /**
     * Glacial crystal chime for Ice-Cooling Mode.
     */
    fun playIceCooling(context: Context) {
        vibrate(context, longArrayOf(0, 18), intArrayOf(0, 100))
        if (!canPlaySound(context)) return
        scope.launch {
            val b1 = synthesizeBell(1318.5, 90, 0.32)
            val b2 = synthesizeBell(1760.0, 100, 0.30)
            val b3 = synthesizeBell(2349.3, 175, 0.28)
            playPcm(concat(b1, b2, b3))
        }
    }

    fun playPanelOpen(context: Context) {
        vibrate(context, longArrayOf(0, 12), intArrayOf(0, 80))
        if (!canPlaySound(context)) return
        scope.launch {
            playPcm(synthesizeSegment(360.0, 860.0, 70, WaveShape.SINE, 0.26))
        }
    }

    fun playPanelClose(context: Context) {
        vibrate(context, longArrayOf(0, 10), intArrayOf(0, 65))
        if (!canPlaySound(context)) return
        scope.launch {
            playPcm(synthesizeSegment(640.0, 240.0, 60, WaveShape.SINE, 0.22))
        }
    }

    fun playAlertPing(context: Context) {
        vibrate(context, longArrayOf(0, 50, 45, 50), intArrayOf(0, 180, 0, 180))
        if (!canPlaySound(context)) return
        scope.launch {
            val p1 = synthesizeBell(1046.5, 85, 0.36)
            val gap = ShortArray((SAMPLE_RATE * 0.05).toInt())
            val p2 = synthesizeBell(880.0, 105, 0.38)
            playPcm(concat(p1, gap, p2))
        }
    }

    private fun synthesizeSegment(
        startFreq: Double,
        endFreq: Double,
        durationMs: Int,
        shape: WaveShape,
        volume: Double
    ): ShortArray {
        val count = (SAMPLE_RATE * durationMs) / 1000
        val samples = ShortArray(count)
        var phase = 0.0
        for (i in 0 until count) {
            val progress = i.toDouble() / count
            val freq = startFreq + (endFreq - startFreq) * progress
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val raw = when (shape) {
                WaveShape.SINE -> sin(phase)
                WaveShape.TRIANGLE -> (2.0 / PI) * kotlin.math.asin(sin(phase))
                WaveShape.SAWTOOTH -> 2.0 * ((phase / (2.0 * PI)) - kotlin.math.floor(phase / (2.0 * PI) + 0.5))
            }
            samples[i] = clampSample(raw * envelope(progress) * volume)
        }
        return samples
    }

    private fun synthesizeSubTurbine(
        startFreq: Double,
        endFreq: Double,
        durationMs: Int,
        volume: Double
    ): ShortArray {
        val count = (SAMPLE_RATE * durationMs) / 1000
        val samples = ShortArray(count)
        var pSub = 0.0
        var pMain = 0.0
        for (i in 0 until count) {
            val progress = i.toDouble() / count
            val freq = startFreq + (endFreq - startFreq) * progress * progress
            pSub += 2.0 * PI * (freq * 0.5) / SAMPLE_RATE
            pMain += 2.0 * PI * freq / SAMPLE_RATE
            val raw = 0.58 * sin(pSub) + 0.42 * sin(pMain)
            samples[i] = clampSample(raw * envelope(progress) * volume)
        }
        return samples
    }

    private fun synthesizeHarmonicSweep(
        startFreq: Double,
        endFreq: Double,
        durationMs: Int,
        volume: Double
    ): ShortArray {
        val count = (SAMPLE_RATE * durationMs) / 1000
        val samples = ShortArray(count)
        var p1 = 0.0
        var p2 = 0.0
        for (i in 0 until count) {
            val progress = i.toDouble() / count
            val freq = startFreq + (endFreq - startFreq) * progress
            p1 += 2.0 * PI * freq / SAMPLE_RATE
            p2 += 2.0 * PI * (freq * 1.5) / SAMPLE_RATE
            val raw = 0.64 * sin(p1) + 0.36 * sin(p2)
            samples[i] = clampSample(raw * envelope(progress) * volume)
        }
        return samples
    }

    private fun synthesizeMagicChord(freqs: DoubleArray, durationMs: Int, volume: Double): ShortArray {
        val count = (SAMPLE_RATE * durationMs) / 1000
        val samples = ShortArray(count)
        val norm = 1.0 / freqs.size.coerceAtLeast(1)
        for (i in 0 until count) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = i.toDouble() / count
            val decay = exp(-progress * 3.6)
            val attack = (progress * 22.0).coerceAtMost(1.0)
            var sum = 0.0
            for (f in freqs) {
                sum += sin(2.0 * PI * f * t)
            }
            samples[i] = clampSample(sum * norm * decay * attack * volume)
        }
        return samples
    }

    private fun synthesizeBell(freq: Double, durationMs: Int, volume: Double): ShortArray {
        val count = (SAMPLE_RATE * durationMs) / 1000
        val samples = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = i.toDouble() / count
            val decay = exp(-progress * 4.2)
            val attack = (progress * 25.0).coerceAtMost(1.0)
            val raw = 0.72 * sin(2.0 * PI * freq * t) + 0.28 * sin(2.0 * PI * (freq * 2.0) * t)
            samples[i] = clampSample(raw * decay * attack * volume)
        }
        return samples
    }

    private fun clampSample(value: Double): Short {
        return (value * Short.MAX_VALUE)
            .toInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            .toShort()
    }

    private fun envelope(progress: Double): Double {
        val fadeIn = (progress / 0.08).coerceIn(0.0, 1.0)
        val fadeOut = ((1.0 - progress) / 0.16).coerceIn(0.0, 1.0)
        return fadeIn * fadeOut
    }

    private fun concat(vararg arrays: ShortArray): ShortArray {
        val total = arrays.sumOf { it.size }
        val result = ShortArray(total)
        var offset = 0
        for (arr in arrays) {
            arr.copyInto(result, offset)
            offset += arr.size
        }
        return result
    }

    private fun playPcm(samples: ShortArray) {
        if (samples.isEmpty()) return
        var track: AudioTrack? = null
        try {
            val byteSize = samples.size * 2
            val minBuf = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(byteSize, minBuf))
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(samples, 0, samples.size)
            track.play()
            val durationMs = (samples.size * 1000L) / SAMPLE_RATE + 35L
            Thread.sleep(durationMs)
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack synthesis playback failed", e)
        } finally {
            try {
                track?.stop()
                track?.release()
            } catch (_: Exception) {}
        }
    }

    private fun vibrate(context: Context, timings: LongArray, amplitudes: IntArray) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator?.hasVibrator() == true) {
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            }
        } catch (_: Exception) {}
    }
}
