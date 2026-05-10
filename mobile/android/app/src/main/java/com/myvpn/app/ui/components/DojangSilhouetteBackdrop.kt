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

private data class SilSlot(val col: Int, val row: Int, val drawable: Int)

/**
 * Силуэты в сетке 3×3 без центра — центр свободен под добок и кулак.
 * Линейный размер ~в 3 раза больше прежнего, но не больше ячейки (без пересечений).
 */
@Composable
fun DojangSilhouetteBackdrop(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        val cw = w / 3f
        val ch = h / 3f
        val tripleTarget = (w * 0.17f).coerceIn(48.dp, 78.dp) * 3f
        val cellFit = minOf(cw, ch) * 0.94f
        val sil: Dp = minOf(tripleTarget, cellFit).coerceAtLeast(108.dp)
        val a = 0.17f

        val slots = listOf(
            SilSlot(0, 0, R.drawable.dojang_bg_yop_chagi),
            SilSlot(1, 0, R.drawable.dojang_bg_roundhouse),
            SilSlot(2, 0, R.drawable.dojang_bg_jumping_kick),
            SilSlot(0, 1, R.drawable.dojang_bg_ready_stance),
            SilSlot(2, 1, R.drawable.dojang_bg_power_punch),
            SilSlot(0, 2, R.drawable.dojang_bg_jumping_kick),
            SilSlot(1, 2, R.drawable.dojang_bg_yop_chagi),
            SilSlot(2, 2, R.drawable.dojang_bg_roundhouse),
        )

        @Composable
        fun Sil(slot: SilSlot) {
            val x = cw * slot.col + (cw - sil) / 2
            val y = ch * slot.row + (ch - sil) / 2
            Image(
                painter = painterResource(slot.drawable),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x, y)
                    .size(sil)
                    .alpha(a),
            )
        }

        slots.forEach { Sil(it) }
    }
}
