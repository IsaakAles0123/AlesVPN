package com.myvpn.app.ui.components.dashboard

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.myvpn.app.R
import com.myvpn.app.ui.theme.AccentGold
import com.myvpn.app.ui.theme.AccentRed
import com.myvpn.app.ui.theme.AlesSpacing
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
                .border(1.dp, Color.Black.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                .background(Color(0xFFFAFAFA), RoundedCornerShape(24.dp))
                .clickable(onClick = onPlusClick)
                .padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(width = 3.dp, height = 18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(AccentRed),
            )
            Icon(
                imageVector = Icons.Rounded.WorkspacePremium,
                contentDescription = null,
                tint = AccentGold,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "Get Plus",
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
        }
        IconButton(onClick = onKeySetupClick) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = "Ключ доступа",
                tint = AccentGold,
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
        Tunnel.State.UP -> AccentRed
        Tunnel.State.TOGGLE -> TextMuted
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
fun DobokFistCluster(
    tunnelState: Tunnel.State,
    onPowerClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val busy = tunnelState == Tunnel.State.TOGGLE
    val enabled = !busy
    val dobokRes = if (tunnelState == Tunnel.State.UP) {
        R.drawable.dobok_black_belt
    } else {
        R.drawable.dobok_white_belt
    }
    val dobokCd = when (tunnelState) {
        Tunnel.State.UP -> stringResource(R.string.dashboard_dobok_vpn_on_cd)
        Tunnel.State.TOGGLE -> stringResource(R.string.dashboard_status_connecting)
        Tunnel.State.DOWN -> stringResource(R.string.dashboard_dobok_vpn_off_cd)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(400.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.offset(y = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(
                modifier = Modifier
                    .border(2.5.dp, Color(0xFF0A0A0A), RoundedCornerShape(16.dp))
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(dobokRes),
                    contentDescription = dobokCd,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(width = 188.dp, height = 216.dp),
                )
            }
            Image(
                painter = painterResource(R.drawable.ic_connect_fist),
                contentDescription = stringResource(R.string.dashboard_connect_fist_cd),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(102.dp)
                    .alpha(if (enabled) 1f else 0.45f)
                    .clickable(
                        enabled = enabled,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false, radius = 52.dp),
                        onClick = onPowerClick,
                    ),
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
                    color = AccentRed.copy(alpha = 0.9f),
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
                primaryColor = TextPrimary,
                secondaryColor = TextMuted,
            )
        }
        DobokFistCluster(
            tunnelState = tunnelState,
            onPowerClick = onPowerClick,
            modifier = Modifier
                .align(Alignment.Center)
                .zIndex(1f),
        )
    }
}
