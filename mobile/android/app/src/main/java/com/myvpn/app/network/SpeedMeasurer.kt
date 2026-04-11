package com.myvpn.app.network

import java.io.InputStream
import java.net.HttpURLConnection
import java.nio.charset.StandardCharsets

object SpeedMeasurer {

    fun readResponseBody(conn: HttpURLConnection): String {
        val code = conn.responseCode
        val stream: InputStream? = if (code in 200..299) conn.inputStream else conn.errorStream
        return stream?.use { s ->
            s.bufferedReader(StandardCharsets.UTF_8).readText()
        }.orEmpty()
    }
}
