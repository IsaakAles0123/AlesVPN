package com.myvpn.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.myvpn.app.MainViewModel
import com.myvpn.app.R
import com.myvpn.app.data.VpnSettingsRepository
import com.myvpn.app.ui.components.NeonBackground
import com.myvpn.app.ui.components.dashboard.VpnRefDashboard
import com.myvpn.app.ui.components.dashboard.rememberMockServers
import com.wireguard.android.backend.Tunnel

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    wgSettingsRepository: VpnSettingsRepository,
    onConnectClick: () -> Unit,
    onStopClick: () -> Unit,
    onOpenKeySetup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val servers = rememberMockServers()
    var selectedServerIndex by remember { mutableIntStateOf(0) }
    val isConfigured = wgSettingsRepository.isConfigured()
    val userVpnAddr = if (isConfigured) wgSettingsRepository.loadAddress().trim() else ""

    NeonBackground {
        Column(
            modifier = modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(top = 4.dp, bottom = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            VpnRefDashboard(
                tunnelState = viewModel.tunnelState,
                sessionStartMs = viewModel.vpnSessionStartMs,
                servers = servers,
                selectedIndex = selectedServerIndex,
                onSelectServer = { selectedServerIndex = it },
                onKeySetupClick = onOpenKeySetup,
                onPlusClick = {
                    openPurchaseUrl(ctx)
                },
                isWgConfigured = isConfigured,
                userVpnAddress = userVpnAddr.ifBlank { null },
                onCheckExternalIp = { openExternalIpCheck(ctx) },
                onPowerClick = {
                    when (viewModel.tunnelState) {
                        Tunnel.State.DOWN -> onConnectClick()
                        Tunnel.State.UP -> onStopClick()
                        Tunnel.State.TOGGLE -> Unit
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
            )
        }
    }
}

private fun openPurchaseUrl(ctx: Context) {
    val u = ctx.getString(R.string.ales_purchase_url).trim()
    if (u.isEmpty() || (!u.startsWith("http://") && !u.startsWith("https://"))) {
        Toast.makeText(ctx, R.string.ales_purchase_url_unset, Toast.LENGTH_LONG).show()
        return
    }
    val host = runCatching { Uri.parse(u).host?.lowercase() }.getOrNull().orEmpty()
    if (host == "example.com" || host.endsWith(".example.com")) {
        Toast.makeText(ctx, R.string.ales_purchase_url_example_com, Toast.LENGTH_LONG).show()
        return
    }
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(u))
    if (intent.resolveActivity(ctx.packageManager) == null) {
        Toast.makeText(ctx, R.string.ales_purchase_url_no_browser, Toast.LENGTH_LONG).show()
        return
    }
    try {
        ctx.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(ctx, R.string.ales_purchase_url_open_error, Toast.LENGTH_LONG).show()
    }
}

private fun openExternalIpCheck(ctx: Context) {
    runCatching {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://api.ipify.org")),
        )
    }
}
