package com.myvpn.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.myvpn.app.ui.components.NeonBackground
import com.myvpn.app.ui.components.dashboard.VpnRefDashboard
import com.myvpn.app.ui.components.dashboard.rememberMockServers
import com.myvpn.app.ui.theme.AlesSpacing
import com.wireguard.android.backend.Tunnel

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onConnectClick: () -> Unit,
    onStopClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val servers = rememberMockServers()
    var selectedServerIndex by remember { mutableIntStateOf(0) }

    NeonBackground {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = AlesSpacing.screenHorizontal)
                .padding(top = 8.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            VpnRefDashboard(
                tunnelState = viewModel.tunnelState,
                sessionStartMs = viewModel.vpnSessionStartMs,
                servers = servers,
                selectedIndex = selectedServerIndex,
                onSelectServer = { selectedServerIndex = it },
                onPlusClick = {
                    Toast.makeText(ctx, "Get Plus", Toast.LENGTH_SHORT).show()
                },
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
