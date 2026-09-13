package com.myvpn.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.myvpn.app.ui.theme.BackgroundDeep
import com.myvpn.app.ui.theme.NeonPurple

@Composable
fun NeonBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        BackgroundDeep,
                        Color(0xFF0A0612),
                        BackgroundDeep,
                    ),
                ),
            ),
    ) {
        AmbientStarFieldBackdrop(modifier = Modifier.fillMaxSize())
        DriftingDustMotes(modifier = Modifier.fillMaxSize())
        ScreenVignetteOverlay(modifier = Modifier.fillMaxSize())
        NebulaBottomGlowOverlay(modifier = Modifier.fillMaxSize())
        WireframeGlobeBackdrop(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(GlobeBackdropHeight)
                .offset(y = 0.dp),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 40.dp, y = (-60).dp)
                .size(280.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(NeonPurple.copy(alpha = 0.35f), Color.Transparent),
                    ),
                    shape = CircleShape,
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-60).dp, y = 24.dp)
                .size(200.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            NeonPurple.copy(alpha = 0.14f),
                            Color.Transparent,
                        ),
                    ),
                    shape = CircleShape,
                ),
        )
        content()
    }
}
