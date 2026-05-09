package com.myvpn.app.ui.components.dashboard

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.myvpn.app.ui.theme.DobokFabric
import com.myvpn.app.ui.theme.DobokTrim
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
            .height(340.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TkdDobokWithBelt(
            tunnelState = tunnelState,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(horizontal = 24.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
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

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        drawRoundRect(
            color = DobokFabric,
            topLeft = Offset(w * 0.22f, h * 0.06f),
            size = Size(w * 0.56f, h * 0.74f),
            cornerRadius = CornerRadius(14f, 14f),
        )
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.06f),
            topLeft = Offset(w * 0.22f, h * 0.06f),
            size = Size(w * 0.56f, h * 0.74f),
            cornerRadius = CornerRadius(14f, 14f),
            style = Stroke(1f),
        )

        val collar = Path().apply {
            moveTo(w * 0.5f, h * 0.1f)
            lineTo(w * 0.36f, h * 0.26f)
            lineTo(w * 0.64f, h * 0.26f)
            close()
        }
        drawPath(collar, color = DobokTrim)

        val beltTop = h * 0.52f
        val beltH = h * 0.12f
        val beltLeft = w * 0.16f
        val beltW = w * 0.68f
        drawRoundRect(
            color = beltColor,
            topLeft = Offset(beltLeft, beltTop),
            size = Size(beltW, beltH),
            cornerRadius = CornerRadius(4f, 4f),
        )
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.35f),
            topLeft = Offset(beltLeft, beltTop),
            size = Size(beltW, beltH),
            cornerRadius = CornerRadius(4f, 4f),
            style = Stroke(1.2f),
        )

        val knot = Path().apply {
            val cx = w * 0.5f
            val cy = beltTop + beltH * 0.5f
            val rh = beltH * 0.42f
            val rw = w * 0.06f
            moveTo(cx, cy - rh)
            lineTo(cx + rw, cy)
            lineTo(cx, cy + rh)
            lineTo(cx - rw, cy)
            close()
        }
        drawPath(
            knot,
            color = when (tunnelState) {
                Tunnel.State.UP -> Color(0xFF2A2A2A)
                else -> Color(0xFFD0D0D0)
            },
        )

        if (showGoldPatch) {
            val patchW = w * 0.09f
            val patchH = beltH * 0.72f
            val px = beltLeft + beltW - patchW - w * 0.03f
            val py = beltTop + (beltH - patchH) / 2f
            drawRoundRect(
                color = GoldPatch,
                topLeft = Offset(px, py),
                size = Size(patchW, patchH),
                cornerRadius = CornerRadius(3f, 3f),
            )
            drawRoundRect(
                color = AccentGoldBright,
                topLeft = Offset(px, py),
                size = Size(patchW, patchH),
                cornerRadius = CornerRadius(3f, 3f),
                style = Stroke(1.5f),
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
    val stroke = when (tunnelState) {
        Tunnel.State.UP -> AccentGold
        Tunnel.State.DOWN -> Color(0xFF4A4A52)
        Tunnel.State.TOGGLE -> AccentGold.copy(alpha = 0.5f)
    }
    val fill = when (tunnelState) {
        Tunnel.State.UP -> Color(0xFF2A2418)
        Tunnel.State.DOWN -> Color(0xFF1E1E24)
        Tunnel.State.TOGGLE -> Color(0xFF252018)
    }

    Box(
        modifier = Modifier
            .size(132.dp)
            .clip(RoundedCornerShape(28.dp))
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
        Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
            val w = size.width
            val h = size.height
            val fistPath = Path().apply {
                moveTo(w * 0.52f, h * 0.18f)
                quadraticTo(w * 0.88f, h * 0.2f, w * 0.9f, h * 0.42f)
                quadraticTo(w * 0.92f, h * 0.58f, w * 0.78f, h * 0.68f)
                lineTo(w * 0.72f, h * 0.82f)
                quadraticTo(w * 0.55f, h * 0.92f, w * 0.38f, h * 0.82f)
                lineTo(w * 0.22f, h * 0.62f)
                quadraticTo(w * 0.08f, h * 0.48f, w * 0.12f, h * 0.32f)
                quadraticTo(w * 0.18f, h * 0.16f, w * 0.52f, h * 0.18f)
                close()
            }
            drawPath(fistPath, color = fill)
            drawPath(fistPath, color = stroke, style = Stroke(width = 3.5f))

            val knuckleY = h * 0.32f
            for (i in 0..3) {
                val x = w * (0.28f + i * 0.14f)
                drawLine(
                    color = stroke.copy(alpha = 0.65f),
                    start = Offset(x, knuckleY),
                    end = Offset(x + w * 0.06f, knuckleY + h * 0.02f),
                    strokeWidth = 2f,
                )
            }
            drawArc(
                color = stroke.copy(alpha = 0.5f),
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(w * 0.18f, h * 0.52f),
                size = Size(w * 0.28f, h * 0.22f),
                style = Stroke(2f),
            )
        }
    }
}
