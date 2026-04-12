package com.myvpn.app.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.myvpn.app.ui.theme.NeonPurple
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

private data class LabelBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    /** Пересечение с зазором [pad] между прямоугольниками. */
    fun overlaps(o: LabelBounds, pad: Float): Boolean {
        val h = left <= o.right + pad && right + pad >= o.left
        val v = top <= o.bottom + pad && bottom + pad >= o.top
        return h && v
    }
}

private fun normalizeLonDeg(deg: Float): Float {
    var d = deg % 360f
    if (d > 180f) d -= 360f
    if (d < -180f) d += 360f
    return d
}

/**
 * Небесная сфера: звёздное поле, Млечный Путь, созвездия, светящийся экватор — в духе «celestial globe».
 */
@Composable
fun WireframeGlobeBackdrop(
    modifier: Modifier = Modifier,
) {
    // Высоту задаёт родитель (NeonBackground). Не добавлять сюда .height(...) — иначе внешний размер игнорируется.
    Canvas(
        modifier = modifier.fillMaxWidth(),
    ) {
        val w = size.width
        val hCanvas = size.height
        val cx = w / 2f
        // Центр сферы ниже середины канваса — купол у нижнего края экрана, не «в середине UI».
        val r = minOf(w * 0.48f, hCanvas * 0.44f)
        val cy = hCanvas * 0.78f
        val lonRot = CelestialGlobeData.LonRotationDeg
        val disc = Path().apply {
            addOval(
                androidx.compose.ui.geometry.Rect(
                    cx - r,
                    cy - r,
                    cx + r,
                    cy + r,
                ),
            )
        }

        fun project(latDeg: Float, lonDeg: Float): Offset? {
            val lat = Math.toRadians(latDeg.toDouble()).toFloat()
            val lon = Math.toRadians(normalizeLonDeg(lonDeg + lonRot).toDouble()).toFloat()
            val x = r * cos(lat) * sin(lon)
            val y = r * sin(lat)
            val z = r * cos(lat) * cos(lon)
            if (z <= r * 0.012f) return null
            return Offset(cx + x, cy - y)
        }

        // Только северная полусфера (y на экране ≤ cy — экватор как нижний срез)
        clipRect(left = 0f, top = 0f, right = w, bottom = cy) {

        // Внешнее мягкое свечение
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    NeonPurple.copy(alpha = 0.38f),
                    NeonPurple.copy(alpha = 0.1f),
                    Color.Transparent,
                ),
                center = Offset(cx, cy - r * 0.08f),
                radius = r * 1.45f,
            ),
            radius = r * 1.28f,
            center = Offset(cx, cy),
        )

        clipPath(disc) {
            // «Стеклянный» шар: тёмно-фиолетовый с бликом
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF4A3D72).copy(alpha = 0.95f),
                        Color(0xFF2A1F45).copy(alpha = 0.92f),
                        Color(0xFF140A28).copy(alpha = 0.98f),
                    ),
                    center = Offset(cx - r * 0.28f, cy - r * 0.22f),
                    radius = r * 1.15f,
                ),
                radius = r * 0.995f,
                center = Offset(cx, cy),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.06f),
                        Color.Transparent,
                    ),
                    center = Offset(cx - r * 0.35f, cy - r * 0.35f),
                    radius = r * 0.55f,
                ),
                radius = r * 0.9f,
                center = Offset(cx, cy),
            )

            val bgRandom = Random(90210)
            val dustRandom = Random(31415)

            // Млечный Путь — плотная дуга мелкой пыли
            repeat(780) {
                val t = dustRandom.nextFloat()
                val lat = (sin(t * Math.PI.toFloat() * 2.3f) * 38f + dustRandom.nextFloat() * 6f - 3f).coerceIn(-72f, 72f)
                val lon = -55f + t * 115f + (dustRandom.nextFloat() - 0.5f) * 8f
                val p = project(lat, lon) ?: return@repeat
                val br = 0.6f + dustRandom.nextFloat() * 1.6f
                val a = 0.12f + dustRandom.nextFloat() * 0.35f
                val tint = if (dustRandom.nextBoolean()) {
                    Color(0xFFE8E0FF).copy(alpha = a)
                } else {
                    NeonPurple.copy(alpha = a * 1.1f)
                }
                drawCircle(color = tint, radius = br, center = p)
            }

            // Фоновые звёзды
            repeat(520) {
                val lat = bgRandom.nextFloat() * 160f - 80f
                val lon = bgRandom.nextFloat() * 160f - 80f
                val p = project(lat, lon) ?: return@repeat
                if (hypot((p.x - cx).toDouble(), (p.y - cy).toDouble()) > r * 0.995) return@repeat
                val rad = 0.45f + bgRandom.nextFloat() * 1.35f
                val a = 0.15f + bgRandom.nextFloat() * 0.45f
                drawCircle(
                    color = Color.White.copy(alpha = a),
                    radius = rad,
                    center = p,
                )
            }

            // Линии созвездий
            val linePaint = Stroke(width = 1.1.dp.toPx())
            CelestialGlobeData.namedConstellations.forEach { con ->
                con.edges.forEach { (ia, ib) ->
                    val a = con.starsLatLon.getOrNull(ia) ?: return@forEach
                    val b = con.starsLatLon.getOrNull(ib) ?: return@forEach
                    val pa = project(a.first, a.second)
                    val pb = project(b.first, b.second)
                    if (pa != null && pb != null) {
                        val path = Path().apply {
                            moveTo(pa.x, pa.y)
                            lineTo(pb.x, pb.y)
                        }
                        drawPath(
                            path = path,
                            color = Color(0xFFE8E8FF).copy(alpha = 0.42f),
                            style = linePaint,
                        )
                    }
                }
                // Яркие узлы созвездий
                con.starsLatLon.forEach { (la, lo) ->
                    val p = project(la, lo) ?: return@forEach
                    drawCircle(color = Color.White.copy(alpha = 0.88f), radius = 2.2.dp.toPx(), center = p)
                    drawCircle(color = Color(0xFFB8A8FF).copy(alpha = 0.35f), radius = 4f, center = p)
                }
            }

            // Экватор не рисуем — многослойные белые штрихи воспринимались как полоса через UI
        }

        // Ободок (френель), без яркой белой каймы снизу
        drawCircle(
            color = Color(0xFFC4B8FF).copy(alpha = 0.18f),
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(width = 1.6.dp.toPx()),
        )
        drawCircle(
            color = NeonPurple.copy(alpha = 0.12f),
            radius = r + 1.2.dp.toPx(),
            center = Offset(cx, cy),
            style = Stroke(width = 0.8.dp.toPx()),
        )

        // Подписи: центр по астеризму, сдвиги при пересечении с уже размещёнными
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(200, 230, 228, 255)
            textSize = 10.5.dp.toPx()
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.NORMAL)
        }
        val fm = labelPaint.fontMetrics
        val pad = 4.dp.toPx()
        val nudgePx = 13.dp.toPx()
        val nudges = listOf(
            Offset(0f, 0f),
            Offset(0f, -nudgePx),
            Offset(nudgePx, 0f),
            Offset(-nudgePx, 0f),
            Offset(0f, nudgePx),
            Offset(nudgePx * 0.85f, -nudgePx * 0.85f),
            Offset(-nudgePx * 0.85f, -nudgePx * 0.85f),
            Offset(nudgePx, nudgePx),
            Offset(-nudgePx, nudgePx),
        )
        val placed = mutableListOf<LabelBounds>()

        CelestialGlobeData.namedConstellations.find { it.starsLatLon.size == 18 }?.let { samira ->
            var sx = 0f
            var sy = 0f
            var cnt = 0
            samira.starsLatLon.forEach { (la, lo) ->
                val p = project(la, lo) ?: return@forEach
                sx += p.x
                sy += p.y
                cnt++
            }
            if (cnt > 0) {
                val mx = sx / cnt
                val my = sy / cnt
                val sPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.argb(235, 245, 230, 255)
                    textSize = 13.5.dp.toPx()
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                }
                val fmS = sPaint.fontMetrics
                val letter = "S"
                val sw = sPaint.measureText(letter)
                val baseline = my - (fmS.ascent + fmS.descent) / 2f
                val left = mx - sw / 2f
                val top = baseline + fmS.ascent
                val bottom = baseline + fmS.descent
                drawContext.canvas.nativeCanvas.drawText(letter, left, baseline, sPaint)
                placed.add(LabelBounds(left, top, left + sw, bottom))
            }
        }

        val candidates = CelestialGlobeData.namedConstellations.mapNotNull { con ->
            if (con.name.isBlank()) return@mapNotNull null
            var sx = 0f
            var sy = 0f
            var cnt = 0
            con.starsLatLon.forEach { (la, lo) ->
                val p = project(la, lo) ?: return@forEach
                sx += p.x
                sy += p.y
                cnt++
            }
            if (cnt == 0) return@mapNotNull null
            Triple(con.name, sx / cnt, sy / cnt)
        }.sortedBy { it.third }

        for ((name, mx, my) in candidates) {
            val textW = labelPaint.measureText(name)
            val halfW = textW / 2f
            val baseline = my - fm.ascent * 0.35f
            for (nudge in nudges) {
                val left = mx - halfW + nudge.x
                val right = mx + halfW + nudge.x
                val top = baseline + fm.ascent + nudge.y
                val bottom = baseline + fm.descent + nudge.y
                val rect = LabelBounds(left, top, right, bottom)
                if (placed.any { rect.overlaps(it, pad) }) continue
                drawContext.canvas.nativeCanvas.drawText(name, left, baseline + nudge.y, labelPaint)
                placed.add(rect)
                break
            }
        }

        }
    }
}
