package com.myvpn.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.myvpn.app.ui.theme.AccentGoldDim
import com.myvpn.app.ui.theme.BackgroundDeep
import com.myvpn.app.ui.theme.BackgroundWarm

/**
 * Тёмный фон в духе тренировочного зала (тхэквондо), без космоса и глобуса.
 */
@Composable
fun TkdBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        BackgroundWarm,
                        BackgroundDeep,
                        Color(0xFF060404),
                    ),
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(220.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            AccentGoldDim.copy(alpha = 0.07f),
                            Color(0xFF120A06).copy(alpha = 0.85f),
                        ),
                    ),
                ),
        )
        content()
    }
}
