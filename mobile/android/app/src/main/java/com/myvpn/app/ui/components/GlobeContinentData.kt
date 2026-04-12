package com.myvpn.app.ui.components

/**
 * Упрощённые контуры береговых линий (широта, долгота в градусах WGS84).
 * Поворот сферы задаётся в [WireframeGlobeBackdrop] — здесь «как на карте».
 */
internal object GlobeContinentData {

    /** Сдвиг долготы: после +90° Америки оказываются по центру диска. */
    const val LonRotationDeg: Float = 90f

    /**
     * Внешний контур Америк (Тихий → мыс Горн → Атлантика → Карибы → … → Аляска).
     * Не географически идеален, но читается как континенты.
     */
    val AmericasOutline: List<Pair<Float, Float>> = listOf(
        71.3f to -156.3f,
        70.5f to -141.0f,
        65.0f to -130.0f,
        60.0f to -139.5f,
        59.5f to -135.5f,
        54.8f to -130.3f,
        49.0f to -125.2f,
        46.2f to -124.4f,
        42.0f to -124.6f,
        37.8f to -122.5f,
        34.4f to -119.5f,
        32.7f to -117.2f,
        29.1f to -113.1f,
        25.0f to -109.4f,
        22.3f to -106.4f,
        18.5f to -105.5f,
        16.3f to -99.2f,
        16.8f to -95.3f,
        20.6f to -97.0f,
        25.6f to -97.4f,
        29.8f to -94.4f,
        30.8f to -86.0f,
        25.9f to -80.1f,
        24.5f to -81.0f,
        26.4f to -82.0f,
        30.4f to -81.4f,
        30.7f to -86.1f,
        29.0f to -89.6f,
        19.4f to -96.9f,
        14.7f to -92.5f,
        13.9f to -90.8f,
        9.3f to -75.6f,
        10.4f to -71.5f,
        14.9f to -61.0f,
        5.6f to -55.2f,
        -4.3f to -51.2f,
        -8.2f to -34.9f,
        -15.8f to -47.9f,
        -23.0f to -43.1f,
        -33.0f to -51.6f,
        -41.8f to -63.4f,
        -52.6f to -69.3f,
        -54.8f to -65.2f,
        -42.1f to -62.8f,
        -22.1f to -70.0f,
        -3.6f to -80.5f,
        8.3f to -79.7f,
        15.7f to -91.7f,
        23.4f to -109.7f,
        39.0f to -123.8f,
        54.5f to -129.9f,
        60.6f to -140.0f,
        71.3f to -156.3f,
    )

    /** Западное побережье Африки + намёк на Европу (правый край глобуса). */
    val AfricaEuropeOutline: List<Pair<Float, Float>> = listOf(
        52.0f to -10.0f,
        50.5f to -5.5f,
        48.0f to -5.0f,
        43.5f to -2.0f,
        40.0f to -9.0f,
        37.0f to -9.0f,
        35.8f to -6.0f,
        33.5f to -8.0f,
        31.0f to -10.0f,
        27.0f to -13.0f,
        20.0f to -17.0f,
        10.0f to -16.0f,
        5.0f to -5.0f,
        0.0f to 9.0f,
        -10.0f to 14.0f,
        -20.0f to 13.0f,
        -33.0f to 18.0f,
        -35.0f to 20.0f,
    )
}
