package com.example.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.abs

enum class NetworkTransportType(val label: String) {
    WIFI("Wi-Fi"),
    CELLULAR_5G("5G/Cell"),
    OFFLINE("Offline")
}

data class NetworkTelemetry(
    val transport: NetworkTransportType = NetworkTransportType.OFFLINE,
    val signalBars: Int = 0, // 0..4
    val rssiDbm: Int = -100,
    val pingMs: Int = -1,
    val jitterMs: Int = 0,
    val isWarningActive: Boolean = false,
    val warningMessage: String? = null
)

/**
 * Early Network Drop Warning & Ping Shield Engine.
 * Monitors real Wi-Fi/5G RSSI and async TCP latency without inspecting or modifying game packets.
 */
object NetworkMonitorEngine {

    private val _telemetry = MutableStateFlow(NetworkTelemetry())
    val telemetry: StateFlow<NetworkTelemetry> = _telemetry.asStateFlow()

    private var callbackRegistered = false
    private var activeTransport = NetworkTransportType.OFFLINE
    private var lastPingMs: Int = -1

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            activeTransport = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkTransportType.WIFI
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkTransportType.CELLULAR_5G
                else -> NetworkTransportType.OFFLINE
            }
        }

        override fun onLost(network: Network) {
            activeTransport = NetworkTransportType.OFFLINE
            _telemetry.value = _telemetry.value.copy(
                transport = NetworkTransportType.OFFLINE,
                signalBars = 0,
                isWarningActive = true,
                warningMessage = "Signal Lost — Switch Net"
            )
        }
    }

    fun registerCallback(context: Context) {
        if (callbackRegistered) return
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.registerDefaultNetworkCallback(networkCallback)
            callbackRegistered = true
            updateActiveTransportSync(cm)
        } catch (_: Exception) {}
    }

    private fun updateActiveTransportSync(cm: ConnectivityManager) {
        val active = cm.activeNetwork
        val caps = if (active != null) cm.getNetworkCapabilities(active) else null
        activeTransport = when {
            caps == null -> NetworkTransportType.OFFLINE
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkTransportType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkTransportType.CELLULAR_5G
            else -> NetworkTransportType.OFFLINE
        }
    }

    suspend fun sampleOnce(context: Context): NetworkTelemetry = withContext(Dispatchers.IO) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        updateActiveTransportSync(cm)

        val (bars, rssi) = readSignal(context, activeTransport)
        val ping = if (activeTransport != NetworkTransportType.OFFLINE) measureTcpPing("1.1.1.1") else -1
        val jitter = if (ping >= 0 && lastPingMs >= 0) abs(ping - lastPingMs) else 0
        if (ping >= 0) lastPingMs = ping

        val threshold = TurboSettingsStore.state.value.pingAlertThresholdMs
        val warning = when {
            activeTransport == NetworkTransportType.OFFLINE -> "Offline — Connection Dropped"
            ping < 0 -> "Packet Drop — Lag Spike Risk"
            ping >= threshold -> "Ping Spike ${ping}ms (>${threshold}ms)"
            bars in 0..1 -> "Weak ${activeTransport.label} Signal ($bars/4)"
            else -> null
        }

        val result = NetworkTelemetry(
            transport = activeTransport,
            signalBars = bars,
            rssiDbm = rssi,
            pingMs = ping,
            jitterMs = jitter,
            isWarningActive = warning != null,
            warningMessage = warning
        )
        _telemetry.value = result
        result
    }

    private fun readSignal(context: Context, transport: NetworkTransportType): Pair<Int, Int> {
        return try {
            when (transport) {
                NetworkTransportType.WIFI -> {
                    val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    @Suppress("DEPRECATION")
                    val rssi = wm.connectionInfo?.rssi ?: -100
                    val level = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        wm.calculateSignalLevel(rssi).coerceIn(0, 4)
                    } else {
                        @Suppress("DEPRECATION")
                        WifiManager.calculateSignalLevel(rssi, 5).coerceIn(0, 4)
                    }
                    level to rssi
                }
                NetworkTransportType.CELLULAR_5G -> {
                    val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                    val level = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        tm.signalStrength?.level?.coerceIn(0, 4) ?: 3
                    } else {
                        3
                    }
                    level to (-60 - (4 - level) * 14)
                }
                NetworkTransportType.OFFLINE -> 0 to -120
            }
        } catch (_: Exception) {
            if (transport == NetworkTransportType.OFFLINE) 0 to -120 else 3 to -72
        }
    }

    private fun measureTcpPing(host: String): Int {
        val start = System.nanoTime()
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, 53), 1500)
                ((System.nanoTime() - start) / 1_000_000L).toInt().coerceAtLeast(1)
            }
        } catch (_: Exception) {
            -1
        }
    }
}
