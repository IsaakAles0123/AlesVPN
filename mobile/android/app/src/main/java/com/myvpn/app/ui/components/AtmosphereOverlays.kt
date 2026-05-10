package com.myvpn.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.myvpn.app.ui.theme.AccentRed

/**
 * Лёгкая виньетка для светлого фона: чуть темнее к краям.
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
                    Color(0x06C7C7CC),
                    Color(0x14C7C7CC),
                ),
                center = Offset(cx, cy),
                radius = r,
            ),
        )
    }
}

/**
 * Не используется в [DojangBackground]; оставлено для совместимости.
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
                    Color(0x00000000),
                    Color(0x08C7C7CC),
                    AccentRed.copy(alpha = 0.04f),
                    Color(0x0CC7C7CC),
                ),
                center = Offset(w * 0.5f, h * 0.99f),
                radius = w * 0.95f,
            ),
        )
    }
}
