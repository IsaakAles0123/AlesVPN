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

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    NeonPurple.copy(alpha = 0.28f),
                    NeonPurple.copy(alpha = 0.08f),
                    Color.Transparent,
                ),
                center = Offset(cx, cy),
                radius = r * 1.35f,
            ),
            radius = r * 1.2f,
            center = Offset(cx, cy),
        )

        fun project(latDeg: Float, lonDeg: Float): Offset? {
            val lat = Math.toRadians(latDeg.toDouble()).toFloat()
            val lon = Math.toRadians(lonDeg.toDouble()).toFloat()
            val x = r * cos(lat) * sin(lon)
            val y = r * sin(lat)
            val z = r * cos(lat) * cos(lon)
            if (z <= r * 0.02f) return null
            return Offset(cx + x, cy - y)
        }

        val lineAlpha = 0.14f
        val lineW = 1.2.dp.toPx()
        val dotR = 1.8.dp.toPx()
        val dotAlpha = 0.42f

        val latStep = 12f
        val lonStep = 12f

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
                latDegInner += 4f
            }
            drawPath(
                path = path,
                color = NeonPurple.copy(alpha = lineAlpha),
                style = Stroke(width = lineW),
            )
            lonDeg += lonStep
        }

        var latDeg = -75f
        while (latDeg <= 75f) {
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
                lonDeg += 4f
            }
            drawPath(
                path = path,
                color = NeonPurple.copy(alpha = lineAlpha * 0.9f),
                style = Stroke(width = lineW),
            )
            latDeg += latStep
        }

        drawCircle(
            color = NeonPurple.copy(alpha = 0.2f),
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(width = 1.5.dp.toPx()),
        )

        latDeg = -78f
        while (latDeg <= 78f) {
            lonDeg = -78f
            while (lonDeg <= 78f) {
                val p = project(latDeg, lonDeg)
                if (p != null) {
                    drawCircle(
                        color = Color.White.copy(alpha = dotAlpha),
                        radius = dotR,
                        center = p,
                    )
                }
                lonDeg += lonStep
            }
            latDeg += latStep
        }
    }
}
