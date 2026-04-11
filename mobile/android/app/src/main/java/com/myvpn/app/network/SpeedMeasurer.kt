package com.myvpn.app.network

import android.content.Context
import android.net.Network
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlin.system.measureTimeMillis

object SpeedMeasurer {

    private const val BUFFER = 8192

    suspend fun measurePingMs(network: Network?, urlString: String): Long = withContext(Dispatchers.IO) {
        val url = URL(urlString)
        val conn = (if (network != null) network.openConnection(url) else url.openConnection()) as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.instanceFollowRedirects = true
        try {
            measureTimeMillis {
                conn.connect()
                conn.inputStream.use { it.read() }
            }
        } finally {
            conn.disconnect()
        }
    }

    suspend fun measureDownloadMbps(
        @Suppress("UNUSED_PARAMETER") context: Context,
        network: Network?,
        urlString: String,
    ): Float = withContext(Dispatchers.IO) {
        val url = URL(urlString)
        val conn = (if (network != null) network.openConnection(url) else url.openConnection()) as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        conn.instanceFollowRedirects = true
        try {
            conn.connect()
            if (conn.responseCode !in 200..299) return@withContext 0f
            val bytes = conn.contentLengthLong.takeIf { it > 0 } ?: return@withContext 0f
            val elapsed = measureTimeMillis {
                conn.inputStream.use { stream ->
                    val buf = ByteArray(BUFFER)
                    while (stream.read(buf) >= 0) { /* drain */ }
                }
            }.coerceAtLeast(1L)
            val seconds = elapsed / 1000.0
            val bits = bytes * 8.0
            (bits / seconds / 1_000_000.0).toFloat()
        } catch (_: Exception) {
            0f
        } finally {
            conn.disconnect()
        }
    }

    fun readResponseBody(conn: HttpURLConnection): String {
        val code = conn.responseCode
        val stream: InputStream? = if (code in 200..299) conn.inputStream else conn.errorStream
        return stream?.use { s ->
            s.bufferedReader(StandardCharsets.UTF_8).readText()
        }.orEmpty()
    }
}
