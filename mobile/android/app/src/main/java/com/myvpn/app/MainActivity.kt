package com.myvpn.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.myvpn.app.databinding.ActivityMainBinding
import com.myvpn.app.tunnel.AlesWgTunnel
import com.wireguard.android.backend.BackendException
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val wgExecutor = Executors.newSingleThreadExecutor()

    private val goBackend by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        GoBackend(applicationContext)
    }

    private val wgTunnel: Tunnel by lazy(LazyThreadSafetyMode.NONE) {
        AlesWgTunnel(TUNNEL_NAME) { state ->
            runOnUiThread {
                appendLog("WireGuard: $state")
                applyVpnStatus(state)
            }
        }
    }

    private val vpnPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            appendLog("Разрешение VPN получено, подключение WireGuard…")
            applyVpnStatus(Tunnel.State.TOGGLE)
            connectWireGuard()
        } else {
            appendLog("Разрешение VPN отклонено.")
            applyVpnStatus(Tunnel.State.DOWN)
        }
    }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        appendLog(
            if (granted) {
                "Уведомления разрешены."
            } else {
                "Уведомления не выданы — статус туннеля может быть скрыт."
            },
        )
        startVpnFlowAfterNotificationStep()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        applyVpnStatus(Tunnel.State.DOWN)

        binding.btnVpn.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                startVpnFlowAfterNotificationStep()
            }
        }

        binding.btnStopVpn.setOnClickListener {
            appendLog("Остановка WireGuard…")
            wgExecutor.execute {
                runCatching {
                    goBackend.setState(wgTunnel, Tunnel.State.DOWN, null)
                }.onFailure { e ->
                    runOnUiThread { appendLog("Ошибка остановки: ${e.message}") }
                }.onSuccess {
                    runOnUiThread { applyVpnStatus(Tunnel.State.DOWN) }
                }
            }
        }

        binding.btnHealth.setOnClickListener {
            val base = binding.editBaseUrl.text?.toString()?.trim().orEmpty()
            if (base.isEmpty()) {
                Toast.makeText(this, R.string.toast_empty_url, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val url = base.trimEnd('/') + "/actuator/health"
            appendLog("GET $url …")
            Thread {
                val result = runCatching { fetchHealth(url) }
                runOnUiThread {
                    result.fold(
                        onSuccess = { appendLog(it) },
                        onFailure = { appendLog("Ошибка: ${it.message}") },
                    )
                }
            }.start()
        }
    }

    private fun startVpnFlowAfterNotificationStep() {
        val prepare = VpnService.prepare(this)
        if (prepare != null) {
            vpnPermission.launch(prepare)
        } else {
            appendLog("Разрешение VPN уже есть, подключение WireGuard…")
            applyVpnStatus(Tunnel.State.TOGGLE)
            connectWireGuard()
        }
    }

    private fun connectWireGuard() {
        wgExecutor.execute {
            val result = runCatching {
                assets.open(ASSET_WG_CONFIG).use { stream ->
                    val cfg = Config.parse(stream)
                    goBackend.setState(wgTunnel, Tunnel.State.UP, cfg)
                }
            }
            runOnUiThread {
                result.fold(
                    onSuccess = {
                        appendLog(getString(R.string.log_connect_ok))
                        applyVpnStatus(
                            runCatching { goBackend.getState(wgTunnel) }
                                .getOrDefault(Tunnel.State.UP),
                        )
                    },
                    onFailure = { e ->
                        val msg = when (e) {
                            is BackendException -> e.message ?: e.toString()
                            else -> e.message ?: e.toString()
                        }
                        appendLog("Ошибка WireGuard: $msg")
                        applyVpnStatus(Tunnel.State.DOWN)
                    },
                )
            }
        }
    }

    private fun applyVpnStatus(state: Tunnel.State) {
        val label = when (state) {
            Tunnel.State.UP -> getString(R.string.vpn_status_on)
            Tunnel.State.DOWN -> getString(R.string.vpn_status_off)
            Tunnel.State.TOGGLE -> getString(R.string.vpn_status_turning)
        }
        val colorRes = when (state) {
            Tunnel.State.UP -> R.color.status_ok
            Tunnel.State.DOWN -> R.color.status_idle
            Tunnel.State.TOGGLE -> R.color.primary
        }
        binding.textVpnStatus.text = label
        binding.textVpnStatus.setTextColor(ContextCompat.getColor(this, colorRes))
        binding.textVpnStatus.setTypeface(
            null,
            if (state == Tunnel.State.UP) Typeface.BOLD else Typeface.NORMAL,
        )
    }

    private fun appendLog(line: String) {
        binding.textLog.append(line + "\n")
    }

    private fun fetchHealth(urlString: String): String {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.use { s ->
                BufferedReader(InputStreamReader(s, StandardCharsets.UTF_8)).readText()
            }.orEmpty()
            "HTTP $code\n$body"
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val TUNNEL_NAME = "aleswg"
        private const val ASSET_WG_CONFIG = "wg_sample.conf"
    }
}
