package com.myvpn.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

/**
 * Медленный дрейф «космической пыли» (несколько пикселей), поверх звёзд, под виньетом.
 */
@Composable
fun DriftingDustMotes(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "driftDust")
    val phase by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(240_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ph",
    )
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w < 1f || h < 1f) return@Canvas
        val n = 40
        val driftY = (phase * h).rem(h).let { if (it < 0f) it + h else it }
        for (i in 0 until n) {
            val x = (i * 37.3f).rem(w).let { if (it < 0f) it + w else it }
            val baseY = (i * 59.1f).rem(h).let { if (it < 0f) it + h else it }
            var y = (baseY + driftY).rem(h).let { if (it < 0f) it + h else it }
            val a = 0.06f + (i % 5) * 0.03f
            val r = 0.4f + (i % 4) * 0.28f
            drawCircle(
                color = Color(0xFFE0DCFF).copy(alpha = a.coerceIn(0.04f, 0.22f)),
                radius = r,
                center = Offset(x, y),
            )
        }
    }
}
