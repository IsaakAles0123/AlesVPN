package com.myvpn.app.ui.components.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myvpn.app.ui.theme.AlesSpacing
import com.myvpn.app.ui.theme.NeonCyan
import com.myvpn.app.ui.theme.NeonPurple
import com.myvpn.app.ui.theme.NeonPurpleDim
import com.myvpn.app.ui.theme.TextMuted
import com.myvpn.app.ui.util.formatSessionDurationHms
import com.wireguard.android.backend.Tunnel
import kotlinx.coroutines.delay

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
    onProfileClick: () -> Unit,
    onPlusClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onProfileClick,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0xFF2A2D38)),
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
            )
        }
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
fun LedSessionTimer(
    tunnelState: Tunnel.State,
    sessionStartMs: Long?,
    modifier: Modifier = Modifier,
) {
    var tick by remember { mutableIntStateOf(0) }
    val active = tunnelState == Tunnel.State.UP && sessionStartMs != null
    LaunchedEffect(active, sessionStartMs) {
        if (!active) return@LaunchedEffect
        while (true) {
            delay(1000)
            tick++
        }
    }
    val display = remember(active, sessionStartMs, tick) {
        if (!active) "00:00:00"
        else formatSessionDurationHms(sessionStartMs!!)
    }
    Text(
        text = display,
        modifier = modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        color = if (active) NeonCyan else TextMuted.copy(alpha = 0.6f),
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 42.sp,
        letterSpacing = 6.sp,
    )
}

@Composable
fun DownloadUploadRow(
    downloadMbps: String,
    uploadMbps: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AlesSpacing.item),
    ) {
        StatChip(
            label = "Download",
            value = downloadMbps,
            accent = NeonCyan,
            modifier = Modifier.weight(1f),
        )
        StatChip(
            label = "Upload",
            value = uploadMbps,
            accent = NeonPurple,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatChip(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF161822),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(accent.copy(alpha = 0.18f), Color(0xFF12131C)),
                    ),
                )
                .padding(horizontal = 14.dp, vertical = 14.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
            )
        }
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
            .height(280.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Кольца за глобусом (первый слой — сзади)
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
        WireframeGlobe(
            modifier = Modifier
                .size(200.dp)
                .align(Alignment.Center),
        )
        androidx.compose.material3.FilledIconButton(
            onClick = { if (enabled) onPowerClick() },
            modifier = Modifier.size(88.dp),
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
private fun WireframeGlobe(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2.1f
        drawCircle(
            color = NeonPurple.copy(alpha = 0.15f),
            radius = r,
            center = c,
            style = Stroke(width = 1.5.dp.toPx()),
        )
        for (i in -2..2) {
            val dy = i * (r / 3f)
            val ry = kotlin.math.sqrt((r * r - dy * dy).coerceAtLeast(0f).toDouble()).toFloat()
            if (ry > 0) {
                drawArc(
                    color = NeonCyan.copy(alpha = 0.12f),
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(c.x - ry, c.y + dy - 2f),
                    size = androidx.compose.ui.geometry.Size(ry * 2, 4f),
                    style = Stroke(1.dp.toPx()),
                )
            }
        }
        for (i in 0..5) {
            val a = i * 30f
            drawArc(
                color = NeonPurple.copy(alpha = 0.08f),
                startAngle = a,
                sweepAngle = 80f,
                useCenter = false,
                topLeft = Offset(c.x - r, c.y - r),
                size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
                style = Stroke(1.dp.toPx()),
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
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp),
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
    onProfileClick: () -> Unit,
    onPlusClick: () -> Unit,
    onPowerClick: () -> Unit,
    downloadMbps: String,
    uploadMbps: String,
    modifier: Modifier = Modifier,
) {
    val selected = servers.getOrElse(selectedIndex) { servers.first() }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AlesSpacing.section),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RefTopBar(onProfileClick = onProfileClick, onPlusClick = onPlusClick)
        ServerLocationPill(server = selected)
        LedSessionTimer(tunnelState = tunnelState, sessionStartMs = sessionStartMs)
        DownloadUploadRow(downloadMbps = downloadMbps, uploadMbps = uploadMbps)
        GlobePowerCluster(tunnelState = tunnelState, onPowerClick = onPowerClick)
        ServerCarousel(
            servers = servers,
            selectedIndex = selectedIndex,
            onSelect = onSelectServer,
        )
    }
}
