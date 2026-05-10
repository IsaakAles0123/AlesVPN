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

private data class RectD(val l: Float, val t: Float, val r: Float, val b: Float)

private fun rectsOverlap(a: RectD, b: RectD, gapDp: Float): Boolean {
    return a.l < b.r + gapDp && a.r + gapDp > b.l && a.t < b.b + gapDp && a.b + gapDp > b.t
}

private fun rectHitsForbidden(r: RectD, w: Float, h: Float): Boolean {
    val fx1 = w * 0.22f
    val fy1 = h * 0.30f
    val fx2 = w * 0.78f
    val fy2 = h * 0.72f
    return r.l < fx2 && r.r > fx1 && r.t < fy2 && r.b > fy1
}

private fun tryPlaceOnce(
    w: Dp,
    h: Dp,
    sil: Dp,
    drawables: List<Int>,
    rng: Random,
    gapDp: Float,
    maxTriesPerItem: Int,
): List<SilPlacement>? {
    val pad = 6f
    val silF = sil.value
    val wF = w.value
    val hF = h.value
    if (silF <= 0f || wF - silF <= 2 * pad || hF - silF <= 2 * pad) return null

    val placed = mutableListOf<RectD>()
    val out = mutableListOf<SilPlacement>()
    val shuffled = drawables.shuffled(rng)

    for (i in shuffled.indices) {
        var placedOne = false
        repeat(maxTriesPerItem) {
            val l = pad + rng.nextFloat() * (wF - silF - 2f * pad)
            val t = pad + rng.nextFloat() * (hF - silF - 2f * pad)
            val r = RectD(l, t, l + silF, t + silF)
            if (rectHitsForbidden(r, wF, hF)) return@repeat
            if (placed.any { rectsOverlap(r, it, gapDp) }) return@repeat
            placed.add(r)
            out.add(SilPlacement(l.dp, t.dp, sil, shuffled[i]))
            placedOne = true
            return@repeat
        }
        if (!placedOne) return null
    }
    return out
}

private fun computePlacements(
    w: Dp,
    h: Dp,
    gridSil: Dp,
    targetLarge: Dp,
    seed: Long,
): List<SilPlacement> {
    val pool = listOf(
        R.drawable.dojang_bg_yop_chagi,
        R.drawable.dojang_bg_roundhouse,
        R.drawable.dojang_bg_jumping_kick,
        R.drawable.dojang_bg_ready_stance,
        R.drawable.dojang_bg_power_punch,
        R.drawable.dojang_bg_jumping_kick,
        R.drawable.dojang_bg_yop_chagi,
        R.drawable.dojang_bg_roundhouse,
    )
    val minSil = maxOf(88.dp, gridSil * 0.82f)
    val steps = 22
    val gap = 12f

    for (cnt in listOf(8, 7, 6)) {
        val drawables = pool.take(cnt)
        for (step in 0 until steps) {
            val t = step / (steps - 1).coerceAtLeast(1).toFloat()
            val sil = (targetLarge * (1f - t) + minSil * t).let { s: Dp ->
                minOf(s, minOf(w, h) * 0.46f).coerceAtLeast(minSil)
            }
            repeat(96) { attempt ->
                val rng = Random(seed xor (step.toLong() shl 48) xor (cnt.toLong() shl 32) xor attempt.toLong())
                tryPlaceOnce(w, h, sil, drawables, rng, gap, 140)?.let { return it }
            }
        }
    }
    return emptyList()
}

/**
 * Псевдослучайное размещение без пересечений и без захода в центр (добок / кулак).
 * Размер — примерно в 3 раза больше прежнего сеточного; при нехватке места слегка уменьшается.
 */
@Composable
fun DojangSilhouetteBackdrop(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        val placements = remember(w, h) {
            val cw = w / 3f
            val ch = h / 3f
            val tripleTarget = (w * 0.17f).coerceIn(48.dp, 78.dp) * 3f
            val cellFit = minOf(cw, ch) * 0.94f
            val gridSil = minOf(tripleTarget, cellFit).coerceAtLeast(108.dp)
            val targetLarge = (gridSil * 3f).coerceAtMost(minOf(w, h) * 0.5f)
            val seed = w.value.toBits().toLong() xor h.value.toBits().toLong() xor 0xD06A116EEEL
            computePlacements(w, h, gridSil, targetLarge, seed)
        }
        val a = 0.17f
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
