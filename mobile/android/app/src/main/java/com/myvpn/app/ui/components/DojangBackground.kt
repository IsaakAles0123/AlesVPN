package com.myvpn.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.myvpn.app.ui.theme.BackgroundDeep
import com.myvpn.app.ui.theme.BackgroundMid

/**
 * Минималистичный фон в духе зала: спокойный градиент, едва заметные горизонтали (татами).
 * Без звёзд, глобуса и неоновых бликов.
 */
@Composable
fun DojangBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        BackgroundDeep,
                        BackgroundMid,
                        BackgroundDeep,
                    ),
                ),
            ),
    ) {
        SubtleTatamiLinesOverlay(modifier = Modifier.fillMaxSize())
        ScreenVignetteOverlay(modifier = Modifier.fillMaxSize())
        content()
    }
}

@Composable
private fun SubtleTatamiLinesOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val step = 52.dp.toPx()
        var y = 0f
        val c = Color.White.copy(alpha = 0.035f)
        val w = size.width
        val h = size.height
        while (y < h) {
            drawLine(
                color = c,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1f,
            )
            y += step
        }
    }
}
