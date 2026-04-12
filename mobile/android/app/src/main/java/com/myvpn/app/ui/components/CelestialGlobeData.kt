package com.myvpn.app.ui.components

/**
 * Декоративные созвездия (условные координаты на сфере, градусы).
 * Рёбра — индексы звёзд в [starsLatLon].
 */
internal object CelestialGlobeData {

    const val LonRotationDeg: Float = 28f

    data class Constellation(
        val name: String,
        val starsLatLon: List<Pair<Float, Float>>,
        val edges: List<Pair<Int, Int>>,
    )

    /** Несколько узнаваемых «палочек» созвездий, не астрономическая точность. */
    val constellations: List<Constellation> = listOf(
        Constellation(
            name = "Aquila",
            starsLatLon = listOf(
                18f to -8f,
                22f to -5f,
                26f to -3f,
                24f to 2f,
                20f to 0f,
            ),
            edges = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 4 to 1),
        ),
        Constellation(
            name = "Lyra",
            starsLatLon = listOf(
                32f to 12f,
                34f to 15f,
                36f to 13f,
                35f to 10f,
            ),
            edges = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 0),
        ),
        Constellation(
            name = "Cygnus",
            starsLatLon = listOf(
                42f to -18f,
                46f to -15f,
                50f to -12f,
                48f to -20f,
                44f to -22f,
            ),
            edges = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 4 to 1),
        ),
        Constellation(
            name = "Hercules",
            starsLatLon = listOf(
                12f to 25f,
                16f to 28f,
                20f to 30f,
                18f to 34f,
                14f to 32f,
                10f to 28f,
            ),
            edges = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 4 to 5, 5 to 0),
        ),
    )
}
