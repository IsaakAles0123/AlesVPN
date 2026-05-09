package com.myvpn.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myvpn.app.ui.theme.AlesSpacing
import com.myvpn.app.ui.theme.NeonCyan
import com.wireguard.android.backend.Tunnel
import kotlinx.coroutines.launch

/**
 * Подключение: потянуть ручку вверх. Отключение: одно нажатие на ручку (без свайпа).
 */
@Composable
fun VerticalSwipeVpnSlider(
    tunnelState: Tunnel.State,
    onSwipeToConnect: () -> Unit,
    onSwipeToDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
    trackWidth: Dp = 82.dp,
    trackHeight: Dp = 204.dp,
    handleHeight: Dp = 80.dp,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val offsetY = remember { Animatable(0f) }
    var maxPx by remember { mutableFloatStateOf(1f) }

    val isBusy = tunnelState == Tunnel.State.TOGGLE
    val showConnect = tunnelState == Tunnel.State.DOWN
    val label = if (showConnect) "VPN" else "STOP"
    val accentGreen = Color(0xFF39FF88)

    LaunchedEffect(tunnelState) {
        if (isBusy) offsetY.snapTo(0f)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (showConnect) {
                "Потяните вверх, чтобы подключиться"
            } else {
                "Нажмите STOP, чтобы отключить"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = AlesSpacing.small),
        )

        BoxWithConstraints(
            modifier = Modifier
                .width(trackWidth)
                .height(trackHeight),
        ) {
            val innerPad = 8.dp
            val innerPadPx = with(density) { innerPad.toPx() }
            val handlePx = with(density) { handleHeight.toPx() }
            val hPx = constraints.maxHeight.toFloat()
            val computedMax = (hPx - handlePx - innerPadPx * 2f).coerceAtLeast(24f)
            maxPx = computedMax

            LaunchedEffect(computedMax) {
                offsetY.updateBounds(0f, computedMax)
            }

            // Подложка: сетка точек + свечение снизу
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .shadow(16.dp, RoundedCornerShape(trackWidth / 2), clip = false)
                    .clip(RoundedCornerShape(trackWidth / 2))
                    .background(Color(0xFF121318)),
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val step = 10.dp.toPx()
                    var y = 0f
                    while (y < size.height) {
                        var x = 0f
                        while (x < size.width) {
                            drawCircle(
                                color = Color.White.copy(alpha = 0.04f),
                                radius = 1.2.dp.toPx(),
                                center = Offset(x, y),
                            )
                            x += step
                        }
                        y += step
                    }
                    val cx = size.width / 2f
                    val cy = size.height - 8.dp.toPx()
                    for (i in 0..4) {
                        val r = 12f + i * 10f
                        drawArc(
                            color = NeonCyan.copy(alpha = 0.12f - i * 0.015f),
                            startAngle = 200f,
                            sweepAngle = 140f,
                            useCenter = false,
                            topLeft = Offset(cx - r, cy - r),
                            size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                        )
                    }
                }

                // Шевроны только для режима подключения (свайп вверх)
                if (showConnect) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("▲", color = Color.White, fontSize = 12.sp)
                        Text("▲", color = Color(0xFF5C6378), fontSize = 11.sp, modifier = Modifier.offset(y = (-4).dp))
                    }
                }

                // Тёмный «жёлоб»
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 30.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF0A0B10), Color(0xFF1B1C24)),
                            ),
                        ),
                )

                // Ручка: свайп только для подключения; отключение — по нажатию
                val yDp = with(density) { offsetY.value.toDp() }
                val interactionStop = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = innerPad)
                        .width(trackWidth - 18.dp)
                        .height(handleHeight)
                        .offset(y = -yDp)
                        .shadow(8.dp, RoundedCornerShape(handleHeight / 2))
                        .clip(RoundedCornerShape(handleHeight / 2))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF6D7380), Color(0xFF3E424B), Color(0xFF2A2D35)),
                            ),
                        )
                        .then(
                            when {
                                isBusy -> Modifier
                                showConnect -> Modifier.pointerInput(maxPx) {
                                    detectVerticalDragGestures(
                                        onVerticalDrag = { _, dragAmount ->
                                            scope.launch {
                                                val next = (offsetY.value - dragAmount).coerceIn(0f, maxPx)
                                                offsetY.snapTo(next)
                                            }
                                        },
                                        onDragEnd = {
                                            scope.launch {
                                                val threshold = maxPx * 0.72f
                                                if (offsetY.value >= threshold) {
                                                    onSwipeToConnect()
                                                }
                                                offsetY.animateTo(0f, tween(280))
                                            }
                                        },
                                        onDragCancel = {
                                            scope.launch { offsetY.animateTo(0f, tween(220)) }
                                        },
                                    )
                                }
                                else -> Modifier.clickable(
                                    interactionSource = interactionStop,
                                    indication = ripple(),
                                    enabled = !isBusy,
                                    onClick = onSwipeToDisconnect,
                                )
                            },
                        )
                        .padding(vertical = 6.dp, horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .width(32.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (showConnect) NeonCyan else accentGreen),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = label,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 0.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF22252E)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PowerSettingsNew,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.92f),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            if (isBusy) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                )
            }
        }
    }
}
