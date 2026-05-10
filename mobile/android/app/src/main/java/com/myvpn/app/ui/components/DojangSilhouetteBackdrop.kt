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

/** Доли экрана [0,1]. Центр под добок не используем. */
private data class ZoneF(val left: Float, val top: Float, val right: Float, val bottom: Float)

private enum class ZoneKind { CornerCell, SideStrip, SlimGap }

private data class TaggedZone(val z: ZoneF, val kind: ZoneKind)

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
    TaggedZone(ZoneF(0.02f, 0.30f, 0.215f, 0.52f), ZoneKind.SideStrip),
    TaggedZone(ZoneF(0.785f, 0.30f, 0.98f, 0.52f), ZoneKind.SideStrip),
    TaggedZone(ZoneF(0.02f, 0.54f, 0.215f, 0.72f), ZoneKind.SideStrip),
    TaggedZone(ZoneF(0.785f, 0.54f, 0.98f, 0.72f), ZoneKind.SideStrip),
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
        addAll(silhouettePool.take(6))
    }.take(zones.size)
    val order = pool.shuffled(rng)

    val minSide = min(wv, hv)
    val capCorner = minSide * 0.52f
    val capSide = minSide * 0.30f

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
                val s = room * 0.97f - 6f
                s.coerceIn(92f, capCorner).coerceAtMost(room - 6f)
            }
            ZoneKind.SideStrip -> {
                val s = room * 0.98f - 4f
                s.coerceIn(82f, capSide).coerceAtMost(room - 4f)
            }
            ZoneKind.SlimGap -> {
                val s = room * 0.96f - 4f
                s.coerceIn(52f, min(capCorner * 0.48f, room - 5f)).coerceAtMost(room - 4f)
            }
        }

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
        val a = 0.4f
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
