package com.myvpn.app.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.myvpn.app.ui.theme.NeonPurple
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/** Высота «купола»: нижняя граница — горизонтальный срез (как у линии до секции API). */
private val GlobeBackdropHeight = 300.dp

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
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(GlobeBackdropHeight),
    ) {
        val w = size.width
        val hCanvas = size.height
        val cx = w / 2f
        // Центр сферы ниже верха: верх касается y=0, низ уходит за Canvas — срез по низу блока.
        val r = maxOf(w * 0.48f, hCanvas * 0.58f)
        val cy = r
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
            CelestialGlobeData.constellations.forEach { con ->
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

            // Экватор — многослойное свечение
            val eqPath = Path()
            var eqFirst = true
            var lonEq = -88f
            while (lonEq <= 88f) {
                val p = project(0f, lonEq)
                if (p != null) {
                    if (eqFirst) {
                        eqPath.moveTo(p.x, p.y)
                        eqFirst = false
                    } else {
                        eqPath.lineTo(p.x, p.y)
                    }
                }
                lonEq += 2.5f
            }
            val eqBase = 1.1.dp.toPx()
            for (layer in 5 downTo 0) {
                val sw = eqBase * (layer + 1) * 0.85f
                val alpha = 0.04f + (5 - layer) * 0.055f
                drawPath(
                    path = eqPath,
                    color = Color.White.copy(alpha = alpha.coerceIn(0.04f, 0.32f)),
                    style = Stroke(width = sw),
                )
            }
        }

        // Ободок (френель)
        drawCircle(
            color = Color(0xFFC4B8FF).copy(alpha = 0.22f),
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(width = 1.8.dp.toPx()),
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.08f),
            radius = r + 1.5.dp.toPx(),
            center = Offset(cx, cy),
            style = Stroke(width = 1.dp.toPx()),
        )

        // Подписи созвездий
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(200, 230, 228, 255)
            textSize = 11.dp.toPx()
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.NORMAL)
        }
        CelestialGlobeData.constellations.forEach { con ->
            var sx = 0f
            var sy = 0f
            var cnt = 0
            con.starsLatLon.forEach { (la, lo) ->
                val p = project(la, lo) ?: return@forEach
                sx += p.x
                sy += p.y
                cnt++
            }
            if (cnt == 0 || con.name.isBlank()) return@forEach
            val ox = sx / cnt - con.name.length * labelPaint.textSize * 0.22f
            val oy = sy / cnt - labelPaint.textSize * 0.85f
            drawContext.canvas.nativeCanvas.drawText(con.name, ox, oy, labelPaint)
        }
    }
}
