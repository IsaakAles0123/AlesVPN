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
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

private data class SilPlacement(val left: Dp, val top: Dp, val size: Dp, val drawable: Int)

/** Доли экрана [0,1]. Центр под добок не используем. */
private data class ZoneF(val left: Float, val top: Float, val right: Float, val bottom: Float)

private enum class ZoneKind { CornerCell, SideStrip, SlimGap, TimerBand }

private data class TaggedZone(val z: ZoneF, val kind: ZoneKind)

/** Безопасный clamp: нижняя граница не выше верхней (иначе Float.coerceIn падает на API). */
private fun clampSil(raw: Float, minDesired: Float, maxAllowed: Float): Float {
    val hi = max(1f, maxAllowed)
    val lo = min(minDesired, hi)
    return raw.coerceIn(lo, hi)
}

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

private fun allZones(): List<TaggedZone> = listOf(
    TaggedZone(ZoneF(0.02f, 0.02f, 0.30f, 0.29f), ZoneKind.CornerCell),
    TaggedZone(ZoneF(0.30f, 0.02f, 0.46f, 0.29f), ZoneKind.CornerCell),
    TaggedZone(ZoneF(0.56f, 0.02f, 0.72f, 0.29f), ZoneKind.CornerCell),
    TaggedZone(ZoneF(0.72f, 0.02f, 0.98f, 0.29f), ZoneKind.CornerCell),
    TaggedZone(ZoneF(0.02f, 0.73f, 0.30f, 0.98f), ZoneKind.CornerCell),
    TaggedZone(ZoneF(0.30f, 0.73f, 0.46f, 0.98f), ZoneKind.CornerCell),
    TaggedZone(ZoneF(0.56f, 0.73f, 0.72f, 0.98f), ZoneKind.CornerCell),
    TaggedZone(ZoneF(0.72f, 0.73f, 0.98f, 0.98f), ZoneKind.CornerCell),
    TaggedZone(ZoneF(0.02f, 0.31f, 0.28f, 0.43f), ZoneKind.TimerBand),
    TaggedZone(ZoneF(0.02f, 0.43f, 0.28f, 0.49f), ZoneKind.TimerBand),
    TaggedZone(ZoneF(0.72f, 0.31f, 0.98f, 0.43f), ZoneKind.TimerBand),
    TaggedZone(ZoneF(0.72f, 0.43f, 0.98f, 0.49f), ZoneKind.TimerBand),
    TaggedZone(ZoneF(0.02f, 0.52f, 0.215f, 0.72f), ZoneKind.SideStrip),
    TaggedZone(ZoneF(0.785f, 0.52f, 0.98f, 0.72f), ZoneKind.SideStrip),
    TaggedZone(ZoneF(0.44f, 0.02f, 0.56f, 0.29f), ZoneKind.SlimGap),
    TaggedZone(ZoneF(0.44f, 0.73f, 0.56f, 0.98f), ZoneKind.SlimGap),
)

private fun jitteredZonePlacements(w: Dp, h: Dp, seed: Long): List<SilPlacement> {
    val wv = w.value
    val hv = h.value
    if (!wv.isFinite() || !hv.isFinite() || wv <= 0f || hv <= 0f) return emptyList()

    val rng = Random(seed)
    val zones = allZones()
    val pool = buildList {
        addAll(silhouettePool)
        addAll(silhouettePool)
    }.take(zones.size)
    val order = pool.shuffled(rng)

    val minSide = min(wv, hv)
    val capCorner = minSide * 0.52f
    val capSide = minSide * 0.30f
    val capTimer = minSide * 0.38f

    return zones.mapIndexed { i, tz ->
        val z = tz.z
        val zl = z.left * wv
        val zt = z.top * hv
        val zr = z.right * wv
        val zb = z.bottom * hv
        val zw = (zr - zl).coerceAtLeast(12f)
        val zh = (zb - zt).coerceAtLeast(12f)
        val room = minOf(zw, zh)

        val silF = when (tz.kind) {
            ZoneKind.CornerCell -> {
                val raw = room * 0.97f - 6f
                val maxFit = min(capCorner, room - 6f)
                clampSil(raw, 92f, maxFit)
            }
            ZoneKind.SideStrip -> {
                val raw = room * 0.98f - 4f
                val maxFit = min(capSide, room - 4f)
                clampSil(raw, 82f, maxFit)
            }
            ZoneKind.SlimGap -> {
                val raw = room * 0.96f - 4f
                val maxFit = min(min(capCorner * 0.48f, room - 5f), room - 4f)
                clampSil(raw, 52f, maxFit)
            }
            ZoneKind.TimerBand -> {
                val raw = room * 0.97f - 4f
                val maxFit = min(min(capTimer, capCorner * 0.5f), room - 4f)
                clampSil(raw, 88f, maxFit)
            }
        }.let { s -> if (s.isFinite() && s > 0f) s else 24f }

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
        val a = 0.44f
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
