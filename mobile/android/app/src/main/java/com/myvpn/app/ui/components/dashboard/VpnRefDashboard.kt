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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.myvpn.app.R
import com.myvpn.app.ui.theme.AlesSpacing
import com.myvpn.app.ui.theme.NeonCyan
import com.myvpn.app.ui.theme.NeonPurple
import com.myvpn.app.ui.theme.NeonPurpleDim
import com.myvpn.app.ui.theme.TextMuted
import com.myvpn.app.ui.theme.TextPrimary
import com.wireguard.android.backend.Tunnel

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
        IconButton(onClick = onKeySetupClick) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = "Ключ доступа",
                tint = NeonCyan,
            )
        }
    }
}

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
    val statusColor = when (tunnelState) {
        Tunnel.State.UP -> NeonCyan
        Tunnel.State.TOGGLE -> NeonPurple
        Tunnel.State.DOWN -> TextPrimary
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = status,
            style = MaterialTheme.typography.titleSmall,
            color = statusColor,
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
        ringAlphas.forEachIndexed { i, alpha ->
            Box(
                modifier = Modifier
                    .size((140 + i * 28).dp)
                    .border(
                        width = 2.dp,
                        brush = Brush.sweepGradient(
                            listOf(
                                NeonPurple.copy(alpha = alpha * 2f),
                                NeonCyan.copy(alpha = alpha),
                                NeonPurple.copy(alpha = alpha * 2f),
                            ),
                        ),
                        shape = CircleShape,
                    ),
            )
        }
        FilledIconButton(
            onClick = onPowerClick,
            modifier = Modifier.size(88.dp),
            enabled = enabled,
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
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
fun VpnRefDashboard(
    tunnelState: Tunnel.State,
    sessionStartMs: Long?,
    onKeySetupClick: () -> Unit,
    onPlusClick: () -> Unit,
    onPowerClick: () -> Unit,
    isWgConfigured: Boolean,
    userVpnAddress: String?,
    modifier: Modifier = Modifier,
) {
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
                    color = NeonCyan.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                )
            }
            ConnectionStatusBlock(
                tunnelState = tunnelState,
                userVpnAddress = userVpnAddress,
            )
            DotMatrixSessionTimer(
                tunnelState = tunnelState,
                sessionStartMs = sessionStartMs,
                idleText = timerIdle,
            )
        }
        GlobePowerCluster(
            tunnelState = tunnelState,
            onPowerClick = onPowerClick,
            modifier = Modifier
                .align(Alignment.Center)
                .zIndex(1f),
        )
    }
}
