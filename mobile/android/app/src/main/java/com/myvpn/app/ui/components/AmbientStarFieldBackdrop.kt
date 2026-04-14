package com.myvpn.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.myvpn.app.ui.theme.NeonPurple
import kotlin.math.hypot
import kotlin.math.min
import kotlin.random.Random

private const val AmbientStarCount = 520 * 5
private const val AmbientConstellationGroups = 20
private const val MinConstellationAnchorSpacingPx = 128f

/**
 * Звёзды и мини-созвездия по экрану, кроме глобуса и верхней/нижней полос UI.
 * Зона кнопки питания не вырезается — линии могут проходить под ней как фон.
 */
@Composable
fun AmbientStarFieldBackdrop(modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val topPad = with(density) { 200.dp.toPx() }
    val bottomPad = with(density) { 168.dp.toPx() }
    val globeMargin = with(density) { 14.dp.toPx() }

    BoxWithConstraints(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val rnd = Random((w * 7919f + h * 104729f).toLong())
            val starRnd = Random((w * 31 + h).toLong() xor 0x5EED5EED)
            val globeH = with(density) { GlobeBackdropHeight.toPx() }
            val globeCx = w / 2f
            val globeCy = h - globeH + 0.78f * globeH
            val globeR = min(w * 0.48f, globeH * 0.44f) + globeMargin

            fun allowedStar(p: Offset): Boolean {
                val x = p.x
                val y = p.y
                if (y < min(topPad, h * 0.24f)) return false
                if (y > h - bottomPad) return false
                if (hypot((x - globeCx).toDouble(), (y - globeCy).toDouble()) < globeR) return false
                return true
            }

            val constellationAnchors = ArrayList<Offset>(AmbientConstellationGroups)

            fun anchorFarEnough(p: Offset): Boolean {
                for (a in constellationAnchors) {
                    if (hypot((p.x - a.x).toDouble(), (p.y - a.y).toDouble()) < MinConstellationAnchorSpacingPx) {
                        return false
                    }
                }
                return true
            }

            var starsDrawn = 0
            var starGuard = 0
            while (starsDrawn < AmbientStarCount && starGuard < AmbientStarCount * 8) {
                starGuard++
                val x = rnd.nextFloat() * w
                val y = rnd.nextFloat() * h
                val p = Offset(x, y)
                if (!allowedStar(p)) continue
                starsDrawn++
                val rad = 0.35f + starRnd.nextFloat() * 1.15f
                val a = 0.12f + starRnd.nextFloat() * 0.42f
                val c = if (starRnd.nextBoolean()) {
                    Color.White.copy(alpha = a)
                } else {
                    NeonPurple.copy(alpha = a * 0.95f)
                }
                drawCircle(color = c, radius = rad, center = p)
            }

            val lineColor = Color(0xFFC8C0E8).copy(alpha = 0.22f)
            val lineW = with(density) { 0.85.dp.toPx() }
            var groupsDrawn = 0
            var groupGuard = 0
            while (groupsDrawn < AmbientConstellationGroups && groupGuard < AmbientConstellationGroups * 100) {
                groupGuard++
                val anchorX = rnd.nextFloat() * w
                val anchorY = rnd.nextFloat() * h
                val anchor = Offset(anchorX, anchorY)
                if (!allowedStar(anchor)) continue
                if (!anchorFarEnough(anchor)) continue
                val n = 4 + rnd.nextInt(3)
                val pts = ArrayList<Offset>(n)
                val spread = 48f
                repeat(n) { i ->
                    val px = anchorX + (rnd.nextFloat() - 0.5f) * (spread + i * 8f)
                    val py = anchorY + (rnd.nextFloat() - 0.5f) * (spread + i * 8f)
                    val o = Offset(px.coerceIn(0f, w), py.coerceIn(0f, h))
                    if (allowedStar(o)) pts.add(o)
                }
                if (pts.size < 3) continue
                constellationAnchors.add(anchor)
                groupsDrawn++
                for (i in 0 until pts.lastIndex) {
                    val a = pts[i]
                    val b = pts[i + 1]
                    val path = Path().apply {
                        moveTo(a.x, a.y)
                        lineTo(b.x, b.y)
                    }
                    drawPath(path = path, color = lineColor, style = Stroke(width = lineW))
                }
                if (pts.size >= 4 && rnd.nextBoolean()) {
                    val extra = Path().apply {
                        moveTo(pts.first().x, pts.first().y)
                        lineTo(pts[pts.size / 2].x, pts[pts.size / 2].y)
                    }
                    drawPath(path = extra, color = lineColor.copy(alpha = 0.16f), style = Stroke(width = lineW * 0.85f))
                }
                pts.forEach { p ->
                    drawCircle(color = Color.White.copy(alpha = 0.35f), radius = 1.1f, center = p)
                }
            }
        }
    }
}
