package com.myvpn.app.ui.components

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
import androidx.compose.ui.unit.dp
import com.myvpn.app.ui.theme.NeonPurple
import kotlin.math.cos
import kotlin.math.sin

private val GlobeBackdropHeight = 460.dp

private fun normalizeLonDeg(deg: Float): Float {
    var d = deg % 360f
    if (d > 180f) d -= 360f
    if (d < -180f) d += 360f
    return d
}

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
        val h = size.height
        val cx = w / 2f
        val cy = h * 0.42f
        val r = minOf(w, h) * 0.48f
        val lonRot = GlobeContinentData.LonRotationDeg

        fun project(latDeg: Float, lonDeg: Float): Offset? {
            val lat = Math.toRadians(latDeg.toDouble()).toFloat()
            val lon = Math.toRadians(normalizeLonDeg(lonDeg + lonRot).toDouble()).toFloat()
            val x = r * cos(lat) * sin(lon)
            val y = r * sin(lat)
            val z = r * cos(lat) * cos(lon)
            if (z <= r * 0.015f) return null
            return Offset(cx + x, cy - y)
        }

        fun drawSparseGrid() {
            val lineAlpha = 0.07f
            val lineW = 1.dp.toPx()
            val latStep = 20f
            val lonStep = 24f
            var lonDeg = -90f
            while (lonDeg <= 90f) {
                val path = Path()
                var first = true
                var latDegInner = -88f
                while (latDegInner <= 88f) {
                    val p = project(latDegInner, lonDeg)
                    if (p != null) {
                        if (first) {
                            path.moveTo(p.x, p.y)
                            first = false
                        } else {
                            path.lineTo(p.x, p.y)
                        }
                    }
                    latDegInner += 5f
                }
                drawPath(
                    path = path,
                    color = NeonPurple.copy(alpha = lineAlpha),
                    style = Stroke(width = lineW),
                )
                lonDeg += lonStep
            }
            var latDeg = -70f
            while (latDeg <= 70f) {
                val path = Path()
                var first = true
                lonDeg = -88f
                while (lonDeg <= 88f) {
                    val p = project(latDeg, lonDeg)
                    if (p != null) {
                        if (first) {
                            path.moveTo(p.x, p.y)
                            first = false
                        } else {
                            path.lineTo(p.x, p.y)
                        }
                    }
                    lonDeg += 5f
                }
                drawPath(
                    path = path,
                    color = NeonPurple.copy(alpha = lineAlpha * 0.85f),
                    style = Stroke(width = lineW),
                )
                latDeg += latStep
            }
        }

        fun drawGeodesicPolyline(
            points: List<Pair<Float, Float>>,
            color: Color,
            strokeWidth: Float,
            stepsPerEdge: Int = 10,
        ) {
            if (points.size < 2) return
            val path = Path()
            var hasMove = false
            for (i in 0 until points.size - 1) {
                val (la0, lo0) = points[i]
                val (la1, lo1) = points[i + 1]
                for (s in 0..stepsPerEdge) {
                    val t = s / stepsPerEdge.toFloat()
                    val lat = la0 + (la1 - la0) * t
                    val lon = lo0 + (lo1 - lo0) * t
                    val p = project(lat, lon)
                    if (p != null) {
                        if (!hasMove) {
                            path.moveTo(p.x, p.y)
                            hasMove = true
                        } else {
                            path.lineTo(p.x, p.y)
                        }
                    } else {
                        hasMove = false
                    }
                }
            }
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = strokeWidth),
            )
        }

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    NeonPurple.copy(alpha = 0.32f),
                    NeonPurple.copy(alpha = 0.09f),
                    Color.Transparent,
                ),
                center = Offset(cx, cy),
                radius = r * 1.35f,
            ),
            radius = r * 1.2f,
            center = Offset(cx, cy),
        )

        drawSparseGrid()

        drawCircle(
            color = NeonPurple.copy(alpha = 0.22f),
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(width = 1.5.dp.toPx()),
        )

        val coastW = 2.1.dp.toPx()
        val coastMain = Color(0xFFD8C8F0).copy(alpha = 0.55f)
        val coastSecondary = Color(0xFFC4B0E8).copy(alpha = 0.38f)

        drawGeodesicPolyline(
            points = GlobeContinentData.AmericasOutline,
            color = coastMain,
            strokeWidth = coastW,
            stepsPerEdge = 12,
        )
        drawGeodesicPolyline(
            points = GlobeContinentData.AfricaEuropeOutline,
            color = coastSecondary,
            strokeWidth = coastW * 0.85f,
            stepsPerEdge = 12,
        )

        // Экватор — дополнительная подсказка «глобус»
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
            lonEq += 3f
        }
        drawPath(
            path = eqPath,
            color = NeonPurple.copy(alpha = 0.12f),
            style = Stroke(width = 1.dp.toPx()),
        )
    }
}
