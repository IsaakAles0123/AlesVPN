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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myvpn.app.ui.theme.AlesSpacing
import com.myvpn.app.ui.theme.NeonCyan
import com.myvpn.app.ui.theme.NeonPurple
import com.myvpn.app.ui.theme.NeonPurpleDim
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
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(NeonPurpleDim.copy(alpha = 0.9f), Color(0xFF3D2560)),
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
                tint = Color(0xFFFFE08A),
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "Get Plus",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
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
fun GlobePowerCluster(
    tunnelState: Tunnel.State,
    onPowerClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val busy = tunnelState == Tunnel.State.TOGGLE
    val enabled = !busy
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(272.dp),
        contentAlignment = Alignment.Center,
    ) {
        val ringAlphas = listOf(0.14f, 0.10f, 0.06f, 0.04f)
        ringAlphas.forEachIndexed { i, a ->
            Box(
                modifier = Modifier
                    .size((140 + i * 28).dp)
                    .align(Alignment.Center)
                    .border(
                        width = 2.dp,
                        brush = Brush.sweepGradient(
                            listOf(
                                NeonPurple.copy(alpha = a * 2f),
                                NeonCyan.copy(alpha = a),
                                NeonPurple.copy(alpha = a * 2f),
                            ),
                        ),
                        shape = CircleShape,
                    ),
            )
        }
        androidx.compose.material3.FilledIconButton(
            onClick = { if (enabled) onPowerClick() },
            modifier = Modifier
                .align(Alignment.Center)
                .size(88.dp),
            enabled = enabled,
            shape = CircleShape,
            colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                containerColor = if (tunnelState == Tunnel.State.UP) NeonPurple else Color(0xFF2E3140),
                contentColor = Color.White,
            ),
        ) {
            Icon(
                imageVector = Icons.Rounded.PowerSettingsNew,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
            )
        }
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
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 0.dp, bottom = 2.dp),
    ) {
        itemsIndexed(servers) { index, server ->
            val selected = index == selectedIndex
            Card(
                modifier = Modifier
                    .width(148.dp)
                    .clickable { onSelect(index) },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) NeonPurple.copy(alpha = 0.95f) else Color(0xFF1A1B24),
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
    onPlusClick: () -> Unit,
    onPowerClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = servers.getOrElse(selectedIndex) { servers.first() }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AlesSpacing.section),
        ) {
            RefTopBar(onPlusClick = onPlusClick)
            ServerLocationPill(server = selected)
            DotMatrixSessionTimer(tunnelState = tunnelState, sessionStartMs = sessionStartMs)
        }
        Spacer(modifier = Modifier.weight(1f))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            GlobePowerCluster(tunnelState = tunnelState, onPowerClick = onPowerClick)
            ServerCarousel(
                servers = servers,
                selectedIndex = selectedIndex,
                onSelect = onSelectServer,
                modifier = Modifier.offset(y = (-4).dp),
            )
        }
    }
}
