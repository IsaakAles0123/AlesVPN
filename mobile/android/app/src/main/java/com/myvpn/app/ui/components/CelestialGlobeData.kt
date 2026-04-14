package com.myvpn.app.ui.components

import kotlin.math.cos
import kotlin.math.hypot
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
     * Минимум между центрами (градусы). Форма сердца ~±10° от центра; [WireframeGlobeBackdrop] обрезает
     * срез по горизонту через центр сферы — низкие широты и задняя сторона не рисуются.
     */
    private const val MinCenterDistanceDeg = 22f
    private const val MinCenterDistanceRelaxedDeg = 18f
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

    /** Как в [WireframeGlobeBackdrop.project]: точка на передней стороне купола. */
    private fun isFacingViewer(latDeg: Float, lonDeg: Float): Boolean {
        val lat = Math.toRadians(latDeg.toDouble()).toFloat()
        val lon = Math.toRadians(normalizeLonDeg(lonDeg + LonRotationDeg).toDouble()).toFloat()
        val z = cos(lat) * cos(lon)
        return z > 0.018f
    }

    /**
     * Все вершины сердца должны быть севернее «резака» экрана (sin(lat)>0) и с запасом,
     * иначе часть контура пропадает в clipRect.
     */
    private fun heartFullyOnVisibleDome(center: Pair<Float, Float>): Boolean {
        val pts = heartAt(center.first, center.second)
        val minLat = pts.minOf { it.first }
        val maxLat = pts.maxOf { it.first }
        if (minLat < 14f || maxLat > 82f) return false
        return pts.all { (la, lo) -> isFacingViewer(la, lo) }
    }

    private fun distance(a: Pair<Float, Float>, b: Pair<Float, Float>): Float =
        hypot((a.first - b.first).toDouble(), (a.second - b.second).toDouble()).toFloat()

    private fun randomLat(): Float = 14f + placementRandom.nextFloat() * 64f
    private fun randomLon(): Float = placementRandom.nextFloat() * 172f - 86f

    /**
     * Ровно [TargetHearts]: якорь Samira, остальные — случайные допустимые центры; порядок 1..5 перемешан.
     */
    private fun heartCenters(): List<Pair<Float, Float>> {
        val anchor = refLat to refLon
        require(heartFullyOnVisibleDome(anchor)) { "anchor heart must fit dome" }

        val chosen = mutableListOf(anchor)

        fun tryPlace(minD: Float): Boolean {
            repeat(RandomPlacementAttempts) {
                val c = randomLat() to randomLon()
                if (!heartFullyOnVisibleDome(c)) return@repeat
                if (chosen.all { distance(c, it) >= minD }) {
                    chosen.add(c)
                    return true
                }
            }
            return false
        }

        while (chosen.size < TargetHearts) {
            if (tryPlace(MinCenterDistanceDeg)) continue
            if (tryPlace(MinCenterDistanceRelaxedDeg)) continue
            break
        }

        if (chosen.size < TargetHearts) {
            val grid = buildList {
                for (lat in 16..72 step 10) {
                    for (lon in -78..78 step 11) {
                        add(lat.toFloat() to lon.toFloat())
                    }
                }
            }.shuffled(placementRandom)
            for (c in grid) {
                if (chosen.size >= TargetHearts) break
                if (!heartFullyOnVisibleDome(c)) continue
                if (chosen.any { distance(c, it) < 0.05f }) continue
                if (chosen.all { distance(c, it) >= MinCenterDistanceRelaxedDeg }) chosen.add(c)
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
