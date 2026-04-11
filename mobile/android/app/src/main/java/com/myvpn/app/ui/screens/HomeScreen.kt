package com.myvpn.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.myvpn.app.MainViewModel
import com.myvpn.app.R
import com.myvpn.app.ui.components.NeonBackground
import com.myvpn.app.ui.components.VerticalSwipeVpnSlider
import com.myvpn.app.ui.theme.NeonCyan
import com.myvpn.app.ui.theme.NeonPurple
import com.myvpn.app.ui.theme.TextMuted
import com.myvpn.app.ui.util.formatSessionDuration
import com.wireguard.android.backend.Tunnel

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onConnectClick: () -> Unit,
    onStopClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    NeonBackground {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.screen_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
            )

            val statusText = when (viewModel.tunnelState) {
                Tunnel.State.UP -> stringResource(R.string.vpn_status_on)
                Tunnel.State.DOWN -> stringResource(R.string.vpn_status_off)
                Tunnel.State.TOGGLE -> stringResource(R.string.vpn_status_turning)
            }
            Text(
                text = "${stringResource(R.string.vpn_status_label)}: $statusText",
                style = MaterialTheme.typography.titleMedium,
                color = when (viewModel.tunnelState) {
                    Tunnel.State.UP -> NeonCyan
                    Tunnel.State.DOWN -> TextMuted
                    Tunnel.State.TOGGLE -> NeonPurple
                },
            )

            val session = viewModel.vpnSessionStartMs
            if (session != null && viewModel.tunnelState == Tunnel.State.UP) {
                Text(
                    text = "Сессия: ${formatSessionDuration(session)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = NeonCyan,
                )
            }

            Text(
                text = stringResource(R.string.section_connection),
                style = MaterialTheme.typography.titleMedium,
                color = NeonPurple,
            )
            VerticalSwipeVpnSlider(
                tunnelState = viewModel.tunnelState,
                onSwipeToConnect = onConnectClick,
                onSwipeToDisconnect = onStopClick,
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.section_api),
                    style = MaterialTheme.typography.titleMedium,
                    color = NeonPurple,
                )
                OutlinedTextField(
                    value = viewModel.baseUrl,
                    onValueChange = viewModel::updateBaseUrl,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.hint_api)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonPurple,
                        unfocusedBorderColor = TextMuted,
                        focusedLabelColor = NeonCyan,
                        cursorColor = NeonCyan,
                    ),
                )
                Button(
                    onClick = {
                        val base = viewModel.baseUrl.trim()
                        if (base.isEmpty()) {
                            Toast.makeText(ctx, R.string.toast_empty_url, Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.runHealthCheck(
                                ctx.applicationContext,
                                base,
                                onResult = { viewModel.appendLog(it) },
                                onError = { viewModel.appendLog("Ошибка: $it") },
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Text(stringResource(R.string.btn_test_api))
                }

                Text(
                    text = stringResource(R.string.section_log),
                    style = MaterialTheme.typography.titleMedium,
                    color = NeonPurple,
                )
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = viewModel.logText.ifEmpty { "—" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
