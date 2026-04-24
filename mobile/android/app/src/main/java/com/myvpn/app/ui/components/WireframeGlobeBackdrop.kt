package com.myvpn.app.ui.components

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
import androidx.compose.ui.unit.dp
import com.myvpn.app.ui.theme.NeonPurple
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

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

            // Тонкая сетка меридиан / параллели (чуть выше «пыль»).
            val gridColor = Color(0xFFC8C0E8).copy(alpha = 0.065f)
            val sw = 0.35f.dp.toPx()
            for (lonM in 0 until 360 step 24) {
                val pathM = Path()
                var needMoveM = true
                var hasSegmentM = false
                var laM = -78f
                while (laM <= 78f) {
                    val p = project(laM, lonM.toFloat())
                    if (p != null) {
                        if (needMoveM) {
                            pathM.moveTo(p.x, p.y)
                            needMoveM = false
                            hasSegmentM = true
                        } else {
                            pathM.lineTo(p.x, p.y)
                        }
                    } else {
                        needMoveM = true
                    }
                    laM += 2.4f
                }
                if (hasSegmentM) {
                    drawPath(pathM, gridColor, style = Stroke(width = sw))
                }
            }
            for (latM in -60..60 step 20) {
                val pathP = Path()
                var needMoveP = true
                var hasSegP = false
                var loM = -180f
                while (loM <= 180f) {
                    val p = project(latM.toFloat(), loM)
                    if (p != null) {
                        if (needMoveP) {
                            pathP.moveTo(p.x, p.y)
                            needMoveP = false
                            hasSegP = true
                        } else {
                            pathP.lineTo(p.x, p.y)
                        }
                    } else {
                        needMoveP = true
                    }
                    loM += 3.5f
                }
                if (hasSegP) {
                    drawPath(
                        pathP,
                        gridColor.copy(alpha = 0.05f),
                        style = Stroke(width = sw * 0.85f),
                    )
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

        }
    }
}
