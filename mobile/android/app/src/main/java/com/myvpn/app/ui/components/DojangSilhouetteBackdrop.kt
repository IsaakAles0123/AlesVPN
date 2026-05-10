package com.myvpn.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myvpn.app.R
import kotlin.math.min
import kotlin.random.Random

private data class SilPlacement(val left: Dp, val top: Dp, val size: Dp, val drawable: Int)

/** Доли экрана [0,1]: зоны не заходят в центр (добок / кулак). */
private data class ZoneF(val left: Float, val top: Float, val right: Float, val bottom: Float)

private val silhouettePool = listOf(
    R.drawable.dojang_bg_yop_chagi,
    R.drawable.dojang_bg_roundhouse,
    R.drawable.dojang_bg_jumping_kick,
    R.drawable.dojang_bg_ready_stance,
    R.drawable.dojang_bg_power_punch,
    R.drawable.dojang_bg_jumping_kick,
    R.drawable.dojang_bg_yop_chagi,
    R.drawable.dojang_bg_roundhouse,
)

/**
 * Восемь раздельных зон + случайное смещение внутри каждой.
 * Углы — крупные (~до ~42% меньшей стороны экрана), боковые полосы — поменьше, но заметнее старой сетки.
 */
private fun jitteredZonePlacements(w: Dp, h: Dp, seed: Long): List<SilPlacement> {
    val wv = w.value
    val hv = h.value
    if (!wv.isFinite() || !hv.isFinite() || wv <= 0f || hv <= 0f) return emptyList()

    val rng = Random(seed)
    val order = silhouettePool.shuffled(rng)

    val zones = listOf(
        ZoneF(0.02f, 0.02f, 0.44f, 0.28f),
        ZoneF(0.56f, 0.02f, 0.98f, 0.28f),
        ZoneF(0.02f, 0.74f, 0.44f, 0.98f),
        ZoneF(0.56f, 0.74f, 0.98f, 0.98f),
        ZoneF(0.02f, 0.30f, 0.20f, 0.52f),
        ZoneF(0.80f, 0.30f, 0.98f, 0.52f),
        ZoneF(0.02f, 0.54f, 0.20f, 0.72f),
        ZoneF(0.80f, 0.54f, 0.98f, 0.72f),
    )

    val maxSilCap = min(wv, hv) * 0.42f
    val bigBoost = 1.08f

    return zones.mapIndexed { i, z ->
        val zl = z.left * wv
        val zt = z.top * hv
        val zr = z.right * wv
        val zb = z.bottom * hv
        val zw = (zr - zl).coerceAtLeast(16f)
        val zh = (zb - zt).coerceAtLeast(16f)
        val isBigCorner = i < 4
        val fill = if (isBigCorner) 0.92f * bigBoost else 0.94f
        var silF = minOf(zw, zh) * fill - 10f
        silF = silF.coerceIn(52f, maxSilCap)
        val spanL = (zw - silF).coerceAtLeast(0f)
        val spanT = (zh - silF).coerceAtLeast(0f)
        val left = zl + rng.nextFloat() * spanL
        val top = zt + rng.nextFloat() * spanT
        SilPlacement(left.dp, top.dp, silF.dp, order[i])
    }
}

@Composable
fun DojangSilhouetteBackdrop(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        val placements = remember(w, h) {
            val seed = w.value.toBits().toLong() xor h.value.toBits().toLong() xor 0xD06A116EEEL
            jitteredZonePlacements(w, h, seed)
        }
        val a = 0.28f
        placements.forEach { p ->
            Image(
                painter = painterResource(p.drawable),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(p.left, p.top)
                    .size(p.size)
                    .alpha(a),
            )
        }
    }
}
