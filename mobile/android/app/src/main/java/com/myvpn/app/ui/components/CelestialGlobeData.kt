package com.myvpn.app.ui.components

/**
 * Созвездия на сфере: [starsLatLon] в градусах (широта, долгота), [edges] — индексы звёзд.
 * Пустое [name] — без подписи (чтобы не перегружать экран).
 */
internal object CelestialGlobeData {

    const val LonRotationDeg: Float = 28f

    data class Constellation(
        val name: String,
        val starsLatLon: List<Pair<Float, Float>>,
        val edges: List<Pair<Int, Int>>,
    )

    /** Только классические созвездия — линии и подписи (без сетки мини-астеризмов, они налезали друг на друга). */
    val namedConstellations: List<Constellation> = listOf(
        Constellation(
            "Aquila",
            listOf(18f to -8f, 22f to -5f, 26f to -3f, 24f to 2f, 20f to 0f),
            listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 4 to 1),
        ),
        Constellation(
            "Lyra",
            listOf(32f to 12f, 34f to 15f, 36f to 13f, 35f to 10f),
            listOf(0 to 1, 1 to 2, 2 to 3, 3 to 0),
        ),
        Constellation(
            "Cygnus",
            listOf(42f to -18f, 46f to -15f, 50f to -12f, 48f to -20f, 44f to -22f),
            listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 4 to 1),
        ),
        Constellation(
            "Hercules",
            listOf(12f to 25f, 16f to 28f, 20f to 30f, 18f to 34f, 14f to 32f, 10f to 28f),
            listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 4 to 5, 5 to 0),
        ),
        Constellation(
            "Orion",
            listOf(-8f to 15f, -4f to 18f, 0f to 20f, -2f to 24f, -10f to 22f, -6f to 12f),
            listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 4 to 5, 5 to 1),
        ),
        Constellation(
            "Gemini",
            listOf(22f to 35f, 24f to 38f, 20f to 40f, 18f to 36f),
            listOf(0 to 1, 2 to 3, 0 to 2),
        ),
        Constellation(
            "Leo",
            listOf(8f to -48f, 12f to -44f, 10f to -40f, 6f to -42f, 4f to -46f),
            listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 4 to 0),
        ),
        Constellation(
            "Ursa Min",
            listOf(68f to -40f, 70f to -38f, 72f to -42f, 69f to -45f),
            listOf(0 to 1, 1 to 2, 2 to 3, 3 to 0),
        ),
        Constellation(
            "Cassiopeia",
            listOf(58f to 20f, 60f to 24f, 56f to 26f, 54f to 22f, 55f to 18f),
            listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4),
        ),
        Constellation(
            "Perseus",
            listOf(48f to 8f, 46f to 12f, 44f to 10f, 45f to 5f, 47f to 4f),
            listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4),
        ),
        Constellation(
            "Pegasus",
            listOf(22f to -48f, 18f to -45f, 20f to -40f, 24f to -42f),
            listOf(0 to 1, 1 to 2, 2 to 3, 3 to 0),
        ),
        Constellation(
            "Andromeda",
            listOf(38f to 6f, 40f to 10f, 36f to 12f, 34f to 8f),
            listOf(0 to 1, 1 to 2, 2 to 3),
        ),
        Constellation(
            "Scorpius",
            listOf(-12f to -32f, -16f to -30f, -20f to -28f, -24f to -26f, -28f to -24f),
            listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4),
        ),
        Constellation(
            "Sagitta",
            listOf(16f to -8f, 18f to -5f, 20f to -6f),
            listOf(0 to 1, 1 to 2),
        ),
        Constellation(
            "Libra",
            listOf(-18f to 28f, -14f to 30f, -16f to 34f, -20f to 32f),
            listOf(0 to 1, 1 to 2, 2 to 3, 3 to 0),
        ),
        Constellation(
            "Capricorn",
            listOf(-28f to -18f, -30f to -14f, -26f to -12f, -24f to -16f),
            listOf(0 to 1, 1 to 2, 2 to 3, 3 to 0),
        ),
        Constellation(
            "Pisces",
            listOf(8f to 58f, 6f to 62f, 4f to 58f, 6f to 54f),
            listOf(0 to 1, 1 to 2, 2 to 3, 3 to 0),
        ),
        Constellation(
            "Virgo",
            listOf(-4f to -8f, 0f to -6f, -2f to -2f, -6f to -4f, -8f to -10f),
            listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4),
        ),
        Constellation(
            "Bootes",
            listOf(32f to -32f, 30f to -28f, 28f to -30f, 34f to -34f, 36f to -30f),
            listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 4 to 1),
        ),
        Constellation(
            "Draco",
            listOf(62f to -10f, 64f to -6f, 66f to -8f, 64f to -12f, 60f to -14f, 58f to -10f),
            listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 4 to 5),
        ),
        Constellation(
            "Centaurus",
            listOf(-48f to 12f, -44f to 14f, -46f to 18f, -50f to 16f),
            listOf(0 to 1, 1 to 2, 2 to 3, 3 to 0),
        ),
    )
}
