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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.myvpn.app.MainViewModel
import com.myvpn.app.R
import com.myvpn.app.ui.components.NeonBackground
import com.myvpn.app.ui.components.dashboard.VpnRefDashboard
import com.myvpn.app.ui.components.dashboard.rememberMockServers
import com.myvpn.app.ui.theme.AlesSpacing
import com.myvpn.app.ui.theme.NeonCyan
import com.myvpn.app.ui.theme.NeonPurple
import com.myvpn.app.ui.theme.TextMuted
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
                .padding(
                    horizontal = AlesSpacing.screenHorizontal,
                    vertical = AlesSpacing.screenVertical,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AlesSpacing.section),
        ) {
            VpnRefDashboard(
                tunnelState = viewModel.tunnelState,
                sessionStartMs = viewModel.vpnSessionStartMs,
                servers = servers,
                selectedIndex = selectedServerIndex,
                onSelectServer = { selectedServerIndex = it },
                onProfileClick = {
                    Toast.makeText(ctx, R.string.app_name, Toast.LENGTH_SHORT).show()
                },
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
                downloadMbps = "—",
                uploadMbps = "—",
                modifier = Modifier.fillMaxWidth(),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AlesSpacing.item),
            ) {
                SectionHeader(text = stringResource(R.string.section_api))
                OutlinedTextField(
                    value = viewModel.baseUrl,
                    onValueChange = viewModel::updateBaseUrl,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    label = { Text(stringResource(R.string.hint_api)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonPurple,
                        unfocusedBorderColor = TextMuted.copy(alpha = 0.5f),
                        focusedLabelColor = NeonCyan,
                        unfocusedLabelColor = TextMuted,
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
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                    contentPadding = PaddingValues(vertical = 14.dp),
                ) {
                    Text(
                        text = stringResource(R.string.btn_test_api),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }

                SectionHeader(
                    text = stringResource(R.string.section_log),
                    modifier = Modifier.padding(top = AlesSpacing.small),
                )
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = viewModel.logText.ifEmpty { "—" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(AlesSpacing.cardInner),
                    )
                }
                Spacer(modifier = Modifier.height(AlesSpacing.section))
            }
        }
    }
}

@Composable
private fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = NeonPurple,
        modifier = modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
}
