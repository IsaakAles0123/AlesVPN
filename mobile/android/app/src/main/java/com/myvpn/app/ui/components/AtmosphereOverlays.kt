package com.myvpn.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.myvpn.app.ui.theme.NeonPurple

/**
 * Виньет: к центру экрана чуть светлее, к углам темнее.
 */
@Composable
fun ScreenVignetteOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w < 1f || h < 1f) return@Canvas
        val cx = w * 0.5f
        val cy = h * 0.36f
        val r = kotlin.math.hypot(w.toDouble(), h.toDouble()).toFloat() * 0.75f
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0x00000000),
                    Color(0x1805050C),
                    Color(0x5505050E),
                ),
                center = Offset(cx, cy),
                radius = r,
            ),
        )
    }
}

/**
 * Мягкое свечение снизу (туманность у «горизонта» планеты).
 */
@Composable
fun NebulaBottomGlowOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w < 1f || h < 1f) return@Canvas
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    NeonPurple.copy(alpha = 0.28f),
                    NeonPurple.copy(alpha = 0.12f),
                    Color(0x331A0A1E),
                    Color(0x00000000),
                ),
                center = Offset(w * 0.5f, h * 0.92f),
                radius = w * 1.05f,
            ),
        )
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0x00000000),
                    NeonPurple.copy(alpha = 0.10f),
                    Color(0xFF0A0612).copy(alpha = 0.55f),
                ),
                startY = h * 0.62f,
                endY = h,
            ),
        )
    }
}
