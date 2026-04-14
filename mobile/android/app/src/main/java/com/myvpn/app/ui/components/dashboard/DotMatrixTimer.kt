package com.myvpn.app.ui.components.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import com.wireguard.android.backend.Tunnel
import kotlinx.coroutines.delay

private const val ROWS = 7
private const val COLS = 5

private fun pattern(ch: Char): Array<String> = when (ch) {
    '0' -> arrayOf(".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###.")
    '1' -> arrayOf("..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###.")
    '2' -> arrayOf(".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####")
    '3' -> arrayOf(".###.", "....#", "....#", "..###", "....#", "#...#", ".###.")
    '4' -> arrayOf("...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#.")
    '5' -> arrayOf("#####", "#....", "####.", "....#", "....#", "#...#", ".###.")
    '6' -> arrayOf("..##.", ".#..#", "#....", "####.", "#...#", "#...#", ".###.")
    '7' -> arrayOf("#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#...")
    '8' -> arrayOf(".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###.")
    '9' -> arrayOf(".###.", "#...#", "#...#", ".####", "....#", "#...#", ".###.")
    ':' -> arrayOf(".....", "..#..", ".....", ".....", "..#..", ".....", ".....")
    '.' -> arrayOf(".....", ".....", ".....", ".....", ".....", "..#..", ".....")
    else -> Array(ROWS) { "....." }
}

/** Ширина матрицы при cell = 1px (средний зазор между символами 0.35). */
private fun dotMatrixWidthUnitFactor(mainPart: String, fracPart: String): Float {
    val g = 0.35f
    fun f(s: String): Float {
        if (s.isEmpty()) return 0f
        val n = s.length
        return n * COLS + (n - 1) * g
    }
    return f(mainPart) + 1.8f * g + f(fracPart)
}

private fun buildTimeString(elapsedMs: Long): Pair<String, String> {
    val elapsed = elapsedMs.coerceAtLeast(0L)
    val wholeSeconds = elapsed / 1000
    val hundredths = (elapsed % 1000) / 10
    val m = wholeSeconds / 60
    val s = wholeSeconds % 60
    val main = if (m <= 99) {
        String.format("%02d:%02d", m, s)
    } else {
        String.format("%d:%02d", m, s)
    }
    val frac = String.format(".%02d", hundredths)
    return main to frac
}

@Composable
fun DotMatrixSessionTimer(
    tunnelState: Tunnel.State,
    sessionStartMs: Long?,
    modifier: Modifier = Modifier,
    primaryColor: Color = Color(0xFFF5F5F5),
    secondaryColor: Color = Color(0xFF6B6B75),
) {
    var tick by remember { mutableIntStateOf(0) }
    val active = tunnelState == Tunnel.State.UP && sessionStartMs != null
    LaunchedEffect(active, sessionStartMs) {
        if (!active) return@LaunchedEffect
        while (true) {
            delay(16)
            tick++
        }
    }

    val (mainPart, fracPart) = remember(active, sessionStartMs, tick) {
        if (!active) {
            "00:00" to ".00"
        } else {
            buildTimeString(System.currentTimeMillis() - sessionStartMs!!)
        }
    }

    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val K = dotMatrixWidthUnitFactor(mainPart, fracPart)
        val maxCellPx = with(density) { 11.dp.toPx() }
        val wPx = with(density) { max(maxWidth.toPx(), 1f) }
        val cellFitW = (wPx * 0.96f) / K
        val cellPref = minOf(cellFitW, maxCellPx)
        val canvasHeightDp = with(density) { (ROWS * cellPref).toDp() }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(canvasHeightDp.coerceAtLeast(28.dp)),
        ) {
            val maxW = size.width
            val maxH = size.height
            var cell = (maxW * 0.96f) / K
            cell = min(cell, maxH * 0.98f / ROWS)
            cell = min(cell, maxCellPx)
            val dotR = cell * 0.32f
            val gapBetweenChars = cell * 0.35f

            fun measureStringWidth(str: String): Float {
                var w = 0f
                str.forEachIndexed { i, _ ->
                    w += COLS * cell
                    if (i < str.lastIndex) w += gapBetweenChars
                }
                return w
            }

            val totalW = measureStringWidth(mainPart) + gapBetweenChars * 1.8f + measureStringWidth(fracPart)
            var startX = (maxW - totalW) / 2f
            val startY = (maxH - ROWS * cell) / 2f

            val mainColor = if (active) primaryColor else secondaryColor.copy(alpha = 0.55f)
            val fracColor = secondaryColor.copy(alpha = if (active) 0.82f else 0.42f)

            var x = startX
            mainPart.forEachIndexed { i, ch ->
                val pat = pattern(ch)
                for (row in 0 until ROWS) {
                    val line = pat[row]
                    for (col in 0 until min(COLS, line.length)) {
                        if (line[col] != '#') continue
                        val cx = x + col * cell + cell / 2f
                        val cy = startY + row * cell + cell / 2f
                        drawCircle(color = mainColor, radius = dotR, center = Offset(cx, cy))
                    }
                }
                x += COLS * cell + if (i < mainPart.lastIndex) gapBetweenChars else 0f
            }
            x += gapBetweenChars * 1.8f
            fracPart.forEachIndexed { i, ch ->
                val pat = pattern(ch)
                for (row in 0 until ROWS) {
                    val line = pat[row]
                    for (col in 0 until min(COLS, line.length)) {
                        if (line[col] != '#') continue
                        val cx = x + col * cell + cell / 2f
                        val cy = startY + row * cell + cell / 2f
                        drawCircle(color = fracColor, radius = dotR, center = Offset(cx, cy))
                    }
                }
                x += COLS * cell + if (i < fracPart.lastIndex) gapBetweenChars else 0f
            }
        }
    }
}
