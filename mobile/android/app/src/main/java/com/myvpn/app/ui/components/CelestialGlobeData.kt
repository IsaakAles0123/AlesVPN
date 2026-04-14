package com.myvpn.app.ui.components

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * Только сердца в форме Samira; буквы S / A / L — на первых трёх сердцах в [WireframeGlobeBackdrop].
 * Центры — случайные на куполе при фиксированном якоре Samira; минимальное расстояние между центрами,
 * чтобы сердца не сливались.
 */
internal object CelestialGlobeData {

    const val LonRotationDeg: Float = 28f

    data class Constellation(
        val name: String,
        val starsLatLon: List<Pair<Float, Float>>,
        val edges: List<Pair<Int, Int>>,
    )

    /** Эталонные точки сердца (как у прежней Samira), центр ~(31°, -46°). */
    private val heartBaseLat: FloatArray = floatArrayOf(
        33.2f, 34.2f, 36f, 36.2f, 34.2f, 31.3f, 28.5f, 26.1f, 24.2f, 23.4f,
        24.2f, 26.1f, 28.5f, 31.3f, 34.2f, 36.2f, 36f, 34.2f,
    )
    private val heartBaseLon: FloatArray = floatArrayOf(
        -46f, -45.7f, -44.1f, -41.3f, -39.1f, -39.1f, -41.3f, -44.1f, -45.7f, -46f,
        -46.3f, -47.9f, -50.7f, -52.9f, -52.9f, -50.7f, -47.9f, -46.3f,
    )

    private val refLat: Float = heartBaseLat.sum() / heartBaseLat.size
    private val refLon: Float = heartBaseLon.sum() / heartBaseLon.size

    private val dLat: FloatArray = FloatArray(heartBaseLat.size) { i -> heartBaseLat[i] - refLat }
    private val dLon: FloatArray = FloatArray(heartBaseLon.size) { i -> heartBaseLon[i] - refLon }

    private fun normalizeLon(deg: Float): Float {
        var d = deg % 360f
        if (d > 180f) d -= 360f
        if (d < -180f) d += 360f
        return d.coerceIn(-88f, 88f)
    }

    /** Точки сердца с центром (centerLat, centerLon). */
    fun heartAt(centerLat: Float, centerLon: Float): List<Pair<Float, Float>> {
        return List(dLat.size) { i ->
            (centerLat + dLat[i]).coerceIn(-86f, 86f) to normalizeLon(centerLon + dLon[i])
        }
    }

    /**
     * В градусах — грубый зазор; точное разведение — [MinCenterSepDisc] в координатах проекции диска.
     */
    private const val MinCenterDistanceDeg = 24f
    private const val MinCenterDistanceRelaxedDeg = 20f
    /** Доля радиуса диска: вершины не ближе к лимбу (сплющивание у краёв слева/справа/сверху). */
    private const val DiscEdgeMargin = 0.15f
    /** Минимум расстояния центров в нормированных координатах проекции (как [WireframeGlobeBackdrop.project]). */
    private const val MinCenterSepDisc = 0.34f
    private const val MinCenterSepDiscRelaxed = 0.30f
    private const val MinHeartsForLetters = 3
    private const val TargetHearts = 6
    private const val RandomPlacementAttempts = 900

    private val placementRandom = Random(90210)

    private fun normalizeLonDeg(deg: Float): Float {
        var d = deg % 360f
        if (d > 180f) d -= 360f
        if (d < -180f) d += 360f
        return d
    }

    /** Ортогональная проекция на диск (r=1), как в [WireframeGlobeBackdrop.project] без масштаба экрана. */
    private fun projectDiscXY(latDeg: Float, lonDeg: Float): Pair<Float, Float>? {
        val lat = Math.toRadians(latDeg.toDouble()).toFloat()
        val lon = Math.toRadians(normalizeLonDeg(lonDeg + LonRotationDeg).toDouble()).toFloat()
        val x = cos(lat) * sin(lon)
        val y = sin(lat)
        val z = cos(lat) * cos(lon)
        if (z <= 0.018f) return null
        return x to y
    }

    private fun discRadial(xy: Pair<Float, Float>): Float =
        hypot(xy.first.toDouble(), xy.second.toDouble()).toFloat()

    private fun discCenterSep(a: Pair<Float, Float>, b: Pair<Float, Float>): Float {
        val pa = projectDiscXY(a.first, a.second) ?: return 0f
        val pb = projectDiscXY(b.first, b.second) ?: return 0f
        return hypot((pa.first - pb.first).toDouble(), (pa.second - pb.second).toDouble()).toFloat()
    }

    /**
     * Все вершины внутри диска с отступом от лимба (иначе сплющивание у верхнего и боковых краёв).
     */
    private fun heartFitsProjection(center: Pair<Float, Float>): Boolean {
        val pts = heartAt(center.first, center.second)
        val minLat = pts.minOf { it.first }
        val maxLat = pts.maxOf { it.first }
        if (minLat < 16f || maxLat > 58f) return false
        val maxR = 1f - DiscEdgeMargin
        for ((la, lo) in pts) {
            val xy = projectDiscXY(la, lo) ?: return false
            if (discRadial(xy) > maxR) return false
        }
        return true
    }

    private fun distance(a: Pair<Float, Float>, b: Pair<Float, Float>): Float =
        hypot((a.first - b.first).toDouble(), (a.second - b.second).toDouble()).toFloat()

    /** Узкий диапазон долготы — меньше точек у восточного/западного лимба. */
    private fun randomLat(): Float = 20f + placementRandom.nextFloat() * 26f
    private fun randomLon(): Float = -50f + placementRandom.nextFloat() * 100f

    /**
     * Ровно [TargetHearts]: якорь Samira, остальные — случайные допустимые центры; порядок 1..5 перемешан.
     */
    private fun heartCenters(): List<Pair<Float, Float>> {
        val anchor = refLat to refLon
        check(heartFitsProjection(anchor)) { "anchor heart must fit projection" }

        val chosen = mutableListOf(anchor)

        fun canAdd(c: Pair<Float, Float>, minDeg: Float, minDisc: Float): Boolean {
            if (!heartFitsProjection(c)) return false
            return chosen.all { o ->
                distance(c, o) >= minDeg && discCenterSep(c, o) >= minDisc
            }
        }

        fun tryPlace(minDeg: Float, minDisc: Float): Boolean {
            repeat(RandomPlacementAttempts) {
                val c = randomLat() to randomLon()
                if (canAdd(c, minDeg, minDisc)) {
                    chosen.add(c)
                    return true
                }
            }
            return false
        }

        while (chosen.size < TargetHearts) {
            if (tryPlace(MinCenterDistanceDeg, MinCenterSepDisc)) continue
            if (tryPlace(MinCenterDistanceRelaxedDeg, MinCenterSepDiscRelaxed)) continue
            break
        }

        if (chosen.size < TargetHearts) {
            val grid = buildList {
                for (lat in 18..50 step 8) {
                    for (lon in -48..48 step 9) {
                        add(lat.toFloat() to lon.toFloat())
                    }
                }
            }.shuffled(placementRandom)
            for (c in grid) {
                if (chosen.size >= TargetHearts) break
                if (canAdd(c, MinCenterDistanceRelaxedDeg, MinCenterSepDiscRelaxed)) chosen.add(c)
            }
        }

        val rest = chosen.drop(1).shuffled(placementRandom)
        return listOf(chosen.first()) + rest
    }

    private val heartEdges: List<Pair<Int, Int>> = List(18) { i -> i to ((i + 1) % 18) }

    val namedConstellations: List<Constellation> = run {
        val centers = heartCenters()
        centers.mapIndexed { _, (clat, clon) ->
            Constellation(
                name = "",
                starsLatLon = heartAt(clat, clon),
                edges = heartEdges,
            )
        }
    }
}
