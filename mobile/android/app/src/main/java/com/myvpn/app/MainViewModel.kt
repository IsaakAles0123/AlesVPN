package com.myvpn.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myvpn.app.network.SpeedMeasurer
import com.myvpn.app.network.findNetworkBypassingVpn
import com.wireguard.android.backend.Tunnel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class MainViewModel : ViewModel() {

    var baseUrl by mutableStateOf("")
        private set

    var logText by mutableStateOf("")
        private set

    var tunnelState by mutableStateOf(Tunnel.State.DOWN)
        private set

    var vpnSessionStartMs by mutableStateOf<Long?>(null)
        private set

    var speedTestRunning by mutableStateOf(false)
        private set

    var speedPingMs by mutableStateOf<Long?>(null)
        private set

    var speedDownloadMbps by mutableStateOf<Float?>(null)
        private set

    fun updateBaseUrl(value: String) {
        baseUrl = value
    }

    fun appendLog(line: String) {
        logText += line + "\n"
    }

    fun updateTunnelStateUi(state: Tunnel.State) {
        tunnelState = state
        when (state) {
            Tunnel.State.UP -> if (vpnSessionStartMs == null) vpnSessionStartMs = System.currentTimeMillis()
            Tunnel.State.DOWN -> vpnSessionStartMs = null
            Tunnel.State.TOGGLE -> { /* keep timer */ }
        }
    }

    fun updateSpeedTestRunning(running: Boolean) {
        speedTestRunning = running
    }

    fun updateSpeedResults(pingMs: Long?, downloadMbps: Float?) {
        speedPingMs = pingMs
        speedDownloadMbps = downloadMbps
    }

    fun runHealthCheck(
        context: android.content.Context,
        base: String,
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        val trimmed = base.trim()
        if (trimmed.isEmpty()) {
            onError("empty_url")
            return
        }
        val baseNormalized = normalizeHttpBase(trimmed)
        val url = baseNormalized + "/actuator/health"
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val u = URL(url)
                val host = u.host.lowercase(Locale.ROOT)
                // 10.0.2.2 — хост ПК из официального эмулятора; привязка к «сети без VPN» часто
                // не имеет маршрута до этого адреса → таймаут. 127.0.0.1 — для `adb reverse tcp:8080 tcp:8080`.
                val conn: HttpURLConnection = if (isLocalDevHost(host)) {
                    u.openConnection() as HttpURLConnection
                } else {
                    val network = findNetworkBypassingVpn(context)
                        ?: error(
                            "Сеть вне VPN не найдена. Нужны INTERNET + ACCESS_NETWORK_STATE. " +
                                "Или отключите WireGuard и повторите проверку API.",
                        )
                    network.openConnection(u) as HttpURLConnection
                }
                conn.requestMethod = "GET"
                conn.connectTimeout = 15_000
                conn.readTimeout = 15_000
                try {
                    val code = conn.responseCode
                    val body = SpeedMeasurer.readResponseBody(conn)
                    withContext(Dispatchers.Main) { onResult("HTTP $code\n$body") }
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e.message ?: e.toString()) }
            }
        }
    }

    /** Дописывает http:// если пользователь ввёл только host:port (иначе URL без схемы даёт «no protocol»). */
    private fun normalizeHttpBase(base: String): String {
        val t = base.trim().trimEnd('/')
        if (t.startsWith("http://", ignoreCase = true) || t.startsWith("https://", ignoreCase = true)) {
            return t
        }
        return "http://${t.trimStart('/')}"
    }

    /** Эмулятор / adb reverse / loopback: подключение без выбора Network (иначе часто connection refused/timeout). */
    private fun isLocalDevHost(host: String): Boolean = when (host) {
        "10.0.2.2", "10.0.3.2", "127.0.0.1", "localhost" -> true
        else -> false
    }

    fun runSpeedTest(context: android.content.Context) {
        val testUrl = "https://proof.ovh.net/files/10Mb.dat"
        val pingUrl = "https://proof.ovh.net/"
        viewModelScope.launch {
            speedTestRunning = true
            updateSpeedResults(null, null)
            try {
                val network = findNetworkBypassingVpn(context)
                val ping = SpeedMeasurer.measurePingMs(network, pingUrl)
                val mbps = SpeedMeasurer.measureDownloadMbps(context, network, testUrl)
                updateSpeedResults(ping, mbps)
            } catch (e: Exception) {
                appendLog("Speed test: ${e.message}")
                updateSpeedResults(null, null)
            } finally {
                speedTestRunning = false
            }
        }
    }
}
