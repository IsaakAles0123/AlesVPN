package com.myvpn.app.ui.components.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myvpn.app.R
import com.myvpn.app.ui.theme.AccentGold
import com.myvpn.app.ui.theme.AccentGoldBright
import com.myvpn.app.ui.theme.AccentGoldDim
import com.myvpn.app.ui.theme.AlesSpacing
import com.myvpn.app.ui.theme.TextMuted
import com.wireguard.android.backend.Tunnel

data class MockServer(
    val id: String,
    val flagEmoji: String,
    val country: String,
    val city: String,
)

private val defaultServers = listOf(
    MockServer("us", "🇺🇸", "United States", "New York"),
    MockServer("au", "🇦🇺", "Australia", "Sydney"),
    MockServer("de", "🇩🇪", "Germany", "Frankfurt"),
    MockServer("jp", "🇯🇵", "Japan", "Tokyo"),
)

@Composable
fun RefTopBar(
    onPlusClick: () -> Unit,
    onKeySetupClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            AccentGoldDim.copy(alpha = 0.95f),
                            Color(0xFF4A3A12),
                        ),
                    ),
                )
                .clickable(onClick = onPlusClick)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.WorkspacePremium,
                contentDescription = null,
                tint = GoldPatchTint,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "Get Plus",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
        }
        IconButton(onClick = onKeySetupClick) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = "Ключ доступа",
                tint = AccentGoldBright,
            )
        }
    }
}

private val GoldPatchTint = Color(0xFFFFE08A)

@Composable
fun ConnectionStatusBlock(
    tunnelState: Tunnel.State,
    userVpnAddress: String?,
    modifier: Modifier = Modifier,
) {
    val addr = userVpnAddress?.trim()?.takeIf { it.isNotEmpty() }
    val status = when (tunnelState) {
        Tunnel.State.TOGGLE -> stringResource(R.string.dashboard_status_connecting)
        Tunnel.State.UP -> stringResource(R.string.dashboard_status_on)
        Tunnel.State.DOWN -> stringResource(R.string.dashboard_status_off)
    }
    val addressLine: String? =
        if (tunnelState == Tunnel.State.UP && addr != null) {
            stringResource(R.string.dashboard_vpn_address, addr)
        } else {
            null
        }
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = status,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
        )
        if (addressLine != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = addressLine,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        }
    }
}

@Composable
fun ServerLocationPill(
    server: MockServer,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF1E1F2A))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(server.flagEmoji, fontSize = 20.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = "Server location",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
            Text(
                text = server.country,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
fun ServerCarousel(
    servers: List<MockServer>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xFF050508).copy(alpha = 0.55f),
                    ),
                ),
            )
            .padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 0.dp, bottom = 4.dp),
    ) {
        itemsIndexed(servers) { index, server ->
            val selected = index == selectedIndex
            Card(
                modifier = Modifier
                    .width(148.dp)
                    .then(
                        if (selected) {
                            Modifier
                        } else {
                            Modifier.border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
                        },
                    )
                    .clickable { onSelect(index) },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) AccentGold.copy(alpha = 0.92f) else Color(0xFF14151C),
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 8.dp else 2.dp),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(server.flagEmoji, fontSize = 28.sp)
                        Text(
                            text = "Connect",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) Color.White.copy(alpha = 0.9f) else TextMuted,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Location",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) Color.White.copy(alpha = 0.75f) else TextMuted,
                    )
                    Text(
                        text = server.country,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
fun rememberMockServers(): List<MockServer> = remember { defaultServers }

@Composable
fun VpnRefDashboard(
    tunnelState: Tunnel.State,
    sessionStartMs: Long?,
    servers: List<MockServer>,
    selectedIndex: Int,
    onSelectServer: (Int) -> Unit,
    onKeySetupClick: () -> Unit,
    onPlusClick: () -> Unit,
    onPowerClick: () -> Unit,
    isWgConfigured: Boolean,
    userVpnAddress: String?,
    modifier: Modifier = Modifier,
) {
    val selected = servers.getOrElse(selectedIndex) { servers.first() }
    val timerIdle = stringResource(R.string.dashboard_timer_idle)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = AlesSpacing.screenHorizontal)
                .zIndex(2f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AlesSpacing.section),
        ) {
            RefTopBar(
                onPlusClick = onPlusClick,
                onKeySetupClick = onKeySetupClick,
            )
            if (!isWgConfigured) {
                Text(
                    text = stringResource(R.string.dashboard_no_key_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = AccentGoldBright.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                )
            }
            ServerLocationPill(server = selected)
            ConnectionStatusBlock(
                tunnelState = tunnelState,
                userVpnAddress = userVpnAddress,
            )
            DotMatrixSessionTimer(
                tunnelState = tunnelState,
                sessionStartMs = sessionStartMs,
                idleText = timerIdle,
                primaryColor = AccentGoldBright,
                secondaryColor = TextMuted,
            )
        }
        TkdFistConnectCluster(
            tunnelState = tunnelState,
            onPowerClick = onPowerClick,
            modifier = Modifier
                .align(Alignment.Center)
                .zIndex(1f),
        )
        ServerCarousel(
            servers = servers,
            selectedIndex = selectedIndex,
            onSelect = onSelectServer,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(1f),
        )
    }
}
