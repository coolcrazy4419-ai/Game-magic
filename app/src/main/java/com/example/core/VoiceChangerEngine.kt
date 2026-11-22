package com.example.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/**
 * 100% Anti-Cheat Safe Real-Time Audio DSP Voice Changer Engine.
 * Uses standard Android AudioRecord + AudioTrack PCM streaming exclusively.
 * Never hooks, injects, or accesses any other application or game process.
 */
object VoiceChangerEngine {

    private const val TAG = "VoiceChangerEngine"
    private const val SAMPLE_RATE = 22050
    private val dspScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var activePreset: VoicePreset = VoicePreset.ORIGINAL

    @Volatile
    private var monitorOutputEnabled: Boolean = false

    @Volatile
    var isRunning: Boolean = false
        private set

    private var dspJob: Job? = null

    fun hasMicPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun updatePreset(preset: VoicePreset) {
        activePreset = preset
    }

    fun setMonitorOutput(enabled: Boolean) {
        monitorOutputEnabled = enabled
    }

    /**
     * Starts low-latency DSP voice processing when Voice Changer or Live Mic Monitor is enabled.
     * Returns false if RECORD_AUDIO permission is not yet granted.
     */
    fun start(context: Context, preset: VoicePreset, monitorOutput: Boolean): Boolean {
        if (!hasMicPermission(context)) return false
        activePreset = preset
        monitorOutputEnabled = monitorOutput
        if (isRunning) return true

        isRunning = true
        dspJob?.cancel()
        dspJob = dspScope.launch {
            runDspLoop()
        }
        return true
    }

    fun stop() {
        isRunning = false
        dspJob?.cancel()
        dspJob = null
    }

    private fun runDspLoop() {
        val minRecBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(2048)

        val minPlayBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(2048)

        var recorder: AudioRecord? = null
        var player: AudioTrack? = null

        try {
            recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minRecBuf * 2
            )

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                Log.w(TAG, "AudioRecord failed to initialize")
                isRunning = false
                return
            }

            player = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minPlayBuf * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            val frameSize = 1024
            val inputBuf = ShortArray(frameSize)
            val outputBuf = ShortArray(frameSize)

            // Circular ring buffer for smooth overlapping granular pitch shifting
            val ringSize = 4096
            val ringMask = ringSize - 1
            val ring = FloatArray(ringSize)
            var writeIdx = 0
            var readPhaseA = 0.0
            var readPhaseB = ringSize * 0.25

            // DSP filter states
            var robotPhase = 0.0
            var lpState = 0f
            var hpPrevIn = 0f
            var hpPrevOut = 0f

            recorder.startRecording()
            player.play()

            while (isRunning && dspScope.isActive) {
                val readCount = recorder.read(inputBuf, 0, frameSize)
                if (readCount <= 0) continue

                val preset = activePreset
                val pitchRatio = when (preset) {
                    VoicePreset.GIRL -> 1.42
                    VoicePreset.DEEP_DEMON -> 0.68
                    VoicePreset.ANIME_HERO -> 1.22
                    else -> 1.0
                }

                for (i in 0 until readCount) {
                    val x = inputBuf[i] / 32768f
                    ring[writeIdx and ringMask] = x
                    writeIdx++

                    var y = when (preset) {
                        VoicePreset.ORIGINAL -> x

                        VoicePreset.GIRL, VoicePreset.DEEP_DEMON, VoicePreset.ANIME_HERO -> {
                            // Dual-head overlapping granular pitch shifter with Hann crossfade
                            val grainLen = 1024.0
                            readPhaseA = (readPhaseA + pitchRatio) % grainLen
                            readPhaseB = (readPhaseA + grainLen * 0.5) % grainLen

                            val delayA = grainLen - readPhaseA
                            val delayB = grainLen - readPhaseB

                            val sA = readInterpolated(ring, ringMask, writeIdx - delayA)
                            val sB = readInterpolated(ring, ringMask, writeIdx - delayB)

                            val wA = (0.5 * (1.0 - cos(2.0 * PI * readPhaseA / grainLen))).toFloat()
                            val wB = (0.5 * (1.0 - cos(2.0 * PI * readPhaseB / grainLen))).toFloat()
                            var shifted = sA * wA + sB * wB

                            if (preset == VoicePreset.DEEP_DEMON) {
                                // Subtle warm sub-harmonic growl saturation
                                shifted = tanh((shifted * 1.45).toDouble()).toFloat()
                            } else if (preset == VoicePreset.GIRL || preset == VoicePreset.ANIME_HERO) {
                                // Crisp high-frequency formant clarity boost
                                val hp = shifted - lpState
                                lpState += 0.22f * (shifted - lpState)
                                shifted = (shifted + hp * 0.25f)
                            }
                            shifted
                        }

                        VoicePreset.ROBOT -> {
                            // 85Hz metallic ring modulation + short comb echo
                            robotPhase += 2.0 * PI * 85.0 / SAMPLE_RATE
                            if (robotPhase > 2.0 * PI) robotPhase -= 2.0 * PI
                            val carrier = sin(robotPhase).toFloat()
                            val delayed = ring[(writeIdx - 180) and ringMask]
                            (x * carrier * 0.82f) + (delayed * 0.35f)
                        }

                        VoicePreset.RADIO -> {
                            // 300Hz..3000Hz walkie-talkie bandpass + soft clipping + light static
                            val hp = 0.88f * (hpPrevOut + x - hpPrevIn)
                            hpPrevIn = x
                            hpPrevOut = hp
                            lpState += 0.36f * (hp - lpState)
                            val drive = tanh((lpState * 2.1).toDouble()).toFloat() * 0.82f
                            val staticNoise = (Random.nextFloat() - 0.5f) * 0.015f
                            drive + staticNoise
                        }
                    }

                    // Soft limiter to prevent clipping
                    y = y.coerceIn(-0.98f, 0.98f)
                    outputBuf[i] = (y * 32767f).toInt().toShort()
                }

                if (monitorOutputEnabled) {
                    player.write(outputBuf, 0, readCount)
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Mic permission missing for voice DSP", e)
        } catch (e: Exception) {
            Log.e(TAG, "Voice DSP loop error", e)
        } finally {
            isRunning = false
            runCatching { recorder?.stop() }
            runCatching { recorder?.release() }
            runCatching { player?.stop() }
            runCatching { player?.release() }
        }
    }

    private fun readInterpolated(ring: FloatArray, mask: Int, pos: Double): Float {
        val base = kotlin.math.floor(pos).toInt()
        val frac = (pos - base).toFloat()
        val s0 = ring[base and mask]
        val s1 = ring[(base + 1) and mask]
        return s0 + (s1 - s0) * frac
    }
}
