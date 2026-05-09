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
import kotlin.math.cos
import kotlin.math.sin

/**
 * Несколько крошечных «светлячков»: мягкое свечение, медленный дрейф, быстрое мерцание.
 */
@Composable
fun DriftingDustMotes(modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "fireflies")
    val drift by inf.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(220_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "dr",
    )
    val tw by inf.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(5_200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "tw",
    )
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w < 1f || h < 1f) return@Canvas

        val n = 14
        val pi2 = 2.0 * kotlin.math.PI
        for (i in 0 until n) {
            // Мерцание: своя фаза + общий «тик» tw — разные ритмы
            val pulse = 0.32f + 0.68f * (0.5f * (1f + sin(
                pi2 * (tw * 1.9 + i * 0.11) + i * 0.6,
            ).toFloat()))
            // Медленные разнесённые дуги, чтобы не в одном круге
            val ax = drift * pi2 * 0.18 + i * 0.55
            val ay = drift * pi2 * 0.14 + i * 0.33
            val x = (w * 0.5f + w * 0.4f * sin(ax).toFloat() * (0.55f + (i % 3) * 0.1f))
                .coerceIn(8f, w - 8f)
            val y = (h * 0.45f + h * 0.35f * cos(ay).toFloat() * (0.5f + (i % 4) * 0.08f))
                .coerceIn(8f, h - 8f)
            val c = Offset(x, y)

            val a = pulse.coerceIn(0.2f, 1f)
            // Внешнее слабое свечение
            drawCircle(
                color = Color(0xFFB8E8FF).copy(alpha = 0.08f * a),
                radius = 5.8f,
                center = c,
            )
            drawCircle(
                color = Color(0xFFDCF6FF).copy(alpha = 0.18f * a),
                radius = 2.6f,
                center = c,
            )
            // Тёплое яркое ядрышко
            drawCircle(
                color = Color(0xFFFFF4E0).copy(alpha = 0.5f * a + 0.1f),
                radius = 1.15f,
                center = c,
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.7f * a + 0.1f),
                radius = 0.55f,
                center = c,
            )
        }
    }
}
