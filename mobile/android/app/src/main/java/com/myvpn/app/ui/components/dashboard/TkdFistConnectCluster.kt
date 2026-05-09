package com.myvpn.app.ui.components.dashboard

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.myvpn.app.R
import com.myvpn.app.ui.theme.AccentGold
import com.myvpn.app.ui.theme.AccentGoldBright
import com.myvpn.app.ui.theme.BeltBlack
import com.myvpn.app.ui.theme.BeltConnecting
import com.myvpn.app.ui.theme.BeltWhite
import com.myvpn.app.ui.theme.GoldPatch
import com.wireguard.android.backend.Tunnel

@Composable
fun TkdFistConnectCluster(
    tunnelState: Tunnel.State,
    onPowerClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(360.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TkdDobokWithBelt(
            tunnelState = tunnelState,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .padding(horizontal = 16.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        FistPowerButton(
            tunnelState = tunnelState,
            onClick = onPowerClick,
        )
    }
}

@Composable
private fun TkdDobokWithBelt(
    tunnelState: Tunnel.State,
    modifier: Modifier = Modifier,
) {
    val pulse by rememberInfiniteTransition(label = "beltPulse").animateFloat(
        initialValue = 0.88f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "beltAlpha",
    )

    val beltColor = when (tunnelState) {
        Tunnel.State.DOWN -> BeltWhite
        Tunnel.State.UP -> BeltBlack
        Tunnel.State.TOGGLE -> BeltConnecting.copy(alpha = pulse)
    }
    val showGoldPatch = tunnelState == Tunnel.State.UP
    val knotColor = when (tunnelState) {
        Tunnel.State.UP -> Color(0xFF2A2A2A)
        else -> Color(0xFFC8C8C8)
    }

    Box(modifier = modifier) {
        Image(
            painter = painterResource(id = R.drawable.tkd_dobok_torso),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.88f)
                .fillMaxHeight(),
            contentScale = ContentScale.Fit,
        )

        val beltShape = RoundedCornerShape(6.dp)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 26.dp)
                .fillMaxWidth(0.62f)
                .height(22.dp)
                .clip(beltShape)
                .background(beltColor)
                .border(1.dp, Color.Black.copy(alpha = 0.4f), beltShape),
        )

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 26.dp)
                .size(width = 14.dp, height = 20.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(knotColor),
        )

        if (showGoldPatch) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = 64.dp, y = 26.dp)
                    .width(20.dp)
                    .height(15.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(GoldPatch)
                    .border(1.5.dp, AccentGoldBright, RoundedCornerShape(4.dp)),
            )
        }
    }
}

@Composable
private fun FistPowerButton(
    tunnelState: Tunnel.State,
    onClick: () -> Unit,
) {
    val busy = tunnelState == Tunnel.State.TOGGLE
    val enabled = !busy
    val cd = stringResource(R.string.dashboard_connect_fist_cd)
    val borderColor = when (tunnelState) {
        Tunnel.State.UP -> AccentGold
        Tunnel.State.DOWN -> Color(0xFF5C5C68)
        Tunnel.State.TOGGLE -> AccentGold.copy(alpha = 0.45f)
    }
    val borderW = if (tunnelState == Tunnel.State.UP) 3.dp else 2.dp

    Box(
        modifier = Modifier
            .size(140.dp)
            .clip(RoundedCornerShape(32.dp))
            .border(borderW, borderColor, RoundedCornerShape(32.dp))
            .semantics {
                contentDescription = cd
                role = Role.Button
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_tkd_fist),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            contentScale = ContentScale.Fit,
        )
    }
}
