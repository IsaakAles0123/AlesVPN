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

private data class SilPlacement(val left: Dp, val top: Dp, val size: Dp, val drawable: Int)

private data class RectD(val l: Float, val t: Float, val r: Float, val b: Float)

private fun rectsOverlap(a: RectD, b: RectD, gapDp: Float): Boolean {
    return a.l < b.r + gapDp && a.r + gapDp > b.l && a.t < b.b + gapDp && a.b + gapDp > b.t
}

private fun rectHitsForbidden(r: RectD, w: Float, h: Float): Boolean {
    if (!w.isFinite() || !h.isFinite() || w <= 0f || h <= 0f) return false
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
    rng: kotlin.random.Random,
    gapDp: Float,
    maxTriesPerItem: Int,
): List<SilPlacement>? {
    val pad = 6f
    val silF = sil.value
    val wF = w.value
    val hF = h.value
    if (!silF.isFinite() || !wF.isFinite() || !hF.isFinite()) return null
    if (silF <= 0f || wF <= 0f || hF <= 0f) return null
    if (wF - silF <= 2f * pad || hF - silF <= 2f * pad) return null

    val placed = mutableListOf<RectD>()
    val out = mutableListOf<SilPlacement>()
    val shuffled = drawables.shuffled(rng)

    for (i in shuffled.indices) {
        var placedOne = false
        repeat(maxTriesPerItem) {
            val spanW = wF - silF - 2f * pad
            val spanH = hF - silF - 2f * pad
            if (spanW <= 0f || spanH <= 0f) return@repeat
            val l = pad + rng.nextFloat() * spanW
            val t = pad + rng.nextFloat() * spanH
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

private fun fallbackGridPlacements(w: Dp, h: Dp, gridSil: Dp, targetLarge: Dp): List<SilPlacement> {
    val cw = w / 3f
    val ch = h / 3f
    val cap = minOf(w, h) * 0.42f
    val sil = minOf(targetLarge, minOf(gridSil * 2.2f, minOf(cw, ch) * 0.90f), cap).coerceAtLeast(64.dp)
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
    val slots = listOf(
        Triple(0, 0, pool[0]),
        Triple(1, 0, pool[1]),
        Triple(2, 0, pool[2]),
        Triple(0, 1, pool[3]),
        Triple(2, 1, pool[4]),
        Triple(0, 2, pool[5]),
        Triple(1, 2, pool[6]),
        Triple(2, 2, pool[7]),
    )
    return slots.map { (col, row, dr) ->
        val x = cw * col + (cw - sil) / 2
        val y = ch * row + (ch - sil) / 2
        SilPlacement(x, y, sil, dr)
    }
}

private fun computePlacements(
    w: Dp,
    h: Dp,
    gridSil: Dp,
    targetLarge: Dp,
    seed: Long,
): List<SilPlacement> {
    val wF = w.value
    val hF = h.value
    if (!wF.isFinite() || !hF.isFinite() || wF <= 0f || hF <= 0f) {
        return fallbackGridPlacements(w, h, gridSil, targetLarge)
    }

    val cap = minOf(w, h) * 0.44f
    val minSilWanted = maxOf(72.dp, minOf(gridSil * 0.80f, cap * 0.92f))

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
    val steps = 20
    val gap = 10f

    for (cnt in listOf(8, 7, 6)) {
        val drawables = pool.take(cnt)
        for (step in 0 until steps) {
            val t = step / (steps - 1).coerceAtLeast(1).toFloat()
            val raw = targetLarge * (1f - t) + minSilWanted * t
            val sil = minOf(raw, cap).coerceAtLeast(minOf(minSilWanted, cap))
            if (sil.value > wF - 20f || sil.value > hF - 20f) continue

            repeat(120) { attempt ->
                val rng = kotlin.random.Random(
                    seed xor (step.toLong() shl 48) xor (cnt.toLong() shl 32) xor attempt.toLong(),
                )
                tryPlaceOnce(w, h, sil, drawables, rng, gap, 220)?.let { return it }
            }
        }
    }
    return fallbackGridPlacements(w, h, gridSil, targetLarge)
}

/**
 * Псевдослучайное размещение без пересечений и без захода в центр (добок / кулак).
 * Если подобрать расклад не удаётся — используется сетка 3×3 без центра (силуэты не пропадают).
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
            val targetLarge = (gridSil * 3f).coerceAtMost(minOf(w, h) * 0.48f)
            val seed = w.value.toBits().toLong() xor h.value.toBits().toLong() xor 0xD06A116EEEL
            computePlacements(w, h, gridSil, targetLarge, seed)
        }
        val a = 0.2f
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
