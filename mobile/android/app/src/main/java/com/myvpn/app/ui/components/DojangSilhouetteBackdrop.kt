package com.myvpn.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myvpn.app.R

/**
 * Декоративные силуэты (PNG с прозрачным фоном).
 * Размер и позиции подобраны так, чтобы не заходить в центральную колонку с добоком и кулаком.
 */
@Composable
fun DojangSilhouetteBackdrop(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val h = maxHeight
        val w = maxWidth
        val sil = (w * 0.17f).coerceIn(48.dp, 78.dp)
        val a = 0.19f

        @Composable
        fun Sil(drawable: Int, align: Alignment, xOff: Dp, yOff: Dp) {
            Image(
                painter = painterResource(drawable),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(align)
                    .offset(xOff, yOff)
                    .size(sil)
                    .alpha(a),
            )
        }

        Sil(R.drawable.dojang_bg_yop_chagi, Alignment.TopStart, w * 0.04f, h * 0.11f)
        Sil(R.drawable.dojang_bg_roundhouse, Alignment.TopEnd, -w * 0.04f, h * 0.13f)
        Sil(R.drawable.dojang_bg_ready_stance, Alignment.CenterStart, w * 0.02f, -h * 0.15f)
        Sil(R.drawable.dojang_bg_power_punch, Alignment.CenterEnd, -w * 0.02f, -h * 0.09f)
        Sil(R.drawable.dojang_bg_jumping_kick, Alignment.BottomStart, w * 0.05f, -h * 0.11f)
    }
}
