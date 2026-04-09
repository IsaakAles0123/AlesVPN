package com.myvpn.app

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.myvpn.app.databinding.ActivityMainBinding
import com.myvpn.app.tunnel.MyVpnService
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val vpnPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            appendLog("Разрешение VPN получено, запуск сервиса…")
            startService(Intent(this, MyVpnService::class.java))
        } else {
            appendLog("Разрешение VPN отклонено.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnVpn.setOnClickListener {
            val prepare = VpnService.prepare(this)
            if (prepare != null) {
                vpnPermission.launch(prepare)
            } else {
                appendLog("Разрешение уже есть, запуск сервиса…")
                startService(Intent(this, MyVpnService::class.java))
            }
        }

        binding.btnStopVpn.setOnClickListener {
            startService(
                Intent(this, MyVpnService::class.java).setAction(MyVpnService.ACTION_STOP),
            )
            appendLog("Запрошена остановка VPN.")
        }

        binding.btnHealth.setOnClickListener {
            val base = binding.editBaseUrl.text?.toString()?.trim().orEmpty()
            if (base.isEmpty()) {
                Toast.makeText(this, "Укажите базовый URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val url = base.trimEnd('/') + "/actuator/health"
            appendLog("GET $url …")
            Thread {
                val result = runCatching { fetchHealth(url) }
                runOnUiThread {
                    result.fold(
                        onSuccess = { appendLog(it) },
                        onFailure = { appendLog("Ошибка: ${it.message}") },
                    )
                }
            }.start()
        }
    }

    private fun appendLog(line: String) {
        binding.textLog.append(line + "\n")
    }

    private fun fetchHealth(urlString: String): String {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.use { s ->
                BufferedReader(InputStreamReader(s, StandardCharsets.UTF_8)).readText()
            }.orEmpty()
            "HTTP $code\n$body"
        } finally {
            conn.disconnect()
        }
    }
}
