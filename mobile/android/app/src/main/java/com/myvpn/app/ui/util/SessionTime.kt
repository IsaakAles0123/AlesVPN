package com.myvpn.app.ui.util

fun formatSessionDuration(startMs: Long): String {
    val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(0L)
    val totalSec = elapsed / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) {
        String.format("%d:%02d:%02d", h, m, s)
    } else {
        String.format("%d:%02d", m, s)
    }
}
