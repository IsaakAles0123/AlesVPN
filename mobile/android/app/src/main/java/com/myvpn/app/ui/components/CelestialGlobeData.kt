package com.myvpn.app.ui.components

import kotlin.math.hypot

/**
 * Только сердца в форме Samira; буквы S / A / L — на первых трёх сердцах в [WireframeGlobeBackdrop].
 * Центры подобраны с минимальным расстоянием в (lat, lon), чтобы фигуры не слипались.
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

    private const val MinCenterDistanceDeg = 13f
    private const val MinCenterDistanceRelaxedDeg = 8.5f
    private const val MinHeartsForLetters = 3
    private const val MaxHearts = 12

    private fun distance(a: Pair<Float, Float>, b: Pair<Float, Float>): Float =
        hypot((a.first - b.first).toDouble(), (a.second - b.second).toDouble()).toFloat()

    private fun isDuplicate(c: Pair<Float, Float>, chosen: List<Pair<Float, Float>>): Boolean =
        chosen.any { distance(it, c) < 0.08f }

    /**
     * Кандидаты по куполу; сначала заполняем около якоря, затем — восточнее (правый видимый край глобуса).
     */
    private fun heartCenters(): List<Pair<Float, Float>> {
        val anchor = refLat to refLon
        val candidates = buildList {
            for (lat in -18..58 step 11) {
                for (lon in -78..78 step 13) {
                    add(lat.toFloat() to lon.toFloat())
                }
            }
            for (lat in -14..54 step 9) {
                for (lon in 5..82 step 9) {
                    add(lat.toFloat() to lon.toFloat())
                }
            }
            add(-8f to -62f)
            add(44f to 48f)
            add(52f to -58f)
            add(38f to 62f)
            add(22f to 70f)
            add(48f to 58f)
            add(12f to 45f)
        }.distinctBy { "${it.first},${it.second}" }

        val chosen = mutableListOf(anchor)

        fun addGreedy(order: List<Pair<Float, Float>>, minD: Float) {
            for (c in order) {
                if (chosen.size >= MaxHearts) return
                if (isDuplicate(c, chosen)) continue
                if (chosen.all { distance(c, it) >= minD }) chosen.add(c)
            }
        }

        val restNear = candidates
            .filter { distance(it, anchor) >= 0.01f }
            .sortedBy { distance(it, anchor) }
        addGreedy(restNear, MinCenterDistanceDeg)

        val eastFirst = candidates
            .filter { !isDuplicate(it, chosen) && it.second > 5f }
            .sortedByDescending { it.second }
        addGreedy(eastFirst, MinCenterDistanceDeg)

        val restAny = candidates
            .filter { !isDuplicate(it, chosen) }
            .sortedBy { distance(it, anchor) }
        addGreedy(restAny, MinCenterDistanceDeg)

        if (chosen.size < MinHeartsForLetters) {
            addGreedy(
                candidates.filter { !isDuplicate(it, chosen) }.sortedBy { distance(it, anchor) },
                MinCenterDistanceRelaxedDeg,
            )
        }

        val eastFill = candidates
            .filter { !isDuplicate(it, chosen) && it.second >= 12f }
            .sortedByDescending { it.second }
        addGreedy(eastFill, MinCenterDistanceRelaxedDeg)

        return chosen
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
