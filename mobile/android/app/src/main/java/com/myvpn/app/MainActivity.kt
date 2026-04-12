package com.myvpn.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.myvpn.app.tunnel.AlesWgTunnel
import com.myvpn.app.ui.AlesVpnApp
import com.myvpn.app.ui.theme.AlesVPNTheme
import com.wireguard.android.backend.BackendException
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private val wgExecutor = Executors.newSingleThreadExecutor()

    private val goBackend by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        GoBackend(applicationContext)
    }

    private val viewModel: MainViewModel by viewModels()

    private val wgTunnel: Tunnel by lazy(LazyThreadSafetyMode.NONE) {
        AlesWgTunnel(TUNNEL_NAME) { state ->
            runOnUiThread {
                appendLog("WireGuard: $state")
                viewModel.updateTunnelStateUi(state)
            }
        }
    }

    private val vpnPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            appendLog("Разрешение VPN получено, подключение WireGuard…")
            viewModel.updateTunnelStateUi(Tunnel.State.TOGGLE)
            connectWireGuard()
        } else {
            appendLog("Разрешение VPN отклонено.")
            viewModel.updateTunnelStateUi(Tunnel.State.DOWN)
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
        installSplashScreen()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.navigationBarDividerColor = Color.TRANSPARENT
        }
        setContent {
            AlesVPNTheme {
                AlesVpnApp(
                    viewModel = viewModel,
                    onConnectClick = {
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
                    },
                    onStopClick = {
                        appendLog("Остановка WireGuard…")
                        wgExecutor.execute {
                            runCatching {
                                goBackend.setState(wgTunnel, Tunnel.State.DOWN, null)
                            }.onFailure { e ->
                                runOnUiThread { appendLog("Ошибка остановки: ${e.message}") }
                            }.onSuccess {
                                runOnUiThread { viewModel.updateTunnelStateUi(Tunnel.State.DOWN) }
                            }
                        }
                    },
                )
            }
        }
    }

    private fun startVpnFlowAfterNotificationStep() {
        val prepare = VpnService.prepare(this)
        if (prepare != null) {
            vpnPermission.launch(prepare)
        } else {
            appendLog("Разрешение VPN уже есть, подключение WireGuard…")
            viewModel.updateTunnelStateUi(Tunnel.State.TOGGLE)
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
                        viewModel.updateTunnelStateUi(
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
                        viewModel.updateTunnelStateUi(Tunnel.State.DOWN)
                    },
                )
            }
        }
    }

    private fun appendLog(line: String) {
        viewModel.appendLog(line)
    }

    companion object {
        private const val TUNNEL_NAME = "aleswg"
        private const val ASSET_WG_CONFIG = "wg_sample.conf"
    }
}
