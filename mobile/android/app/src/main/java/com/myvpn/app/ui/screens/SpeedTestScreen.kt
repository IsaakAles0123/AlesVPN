package com.myvpn.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.myvpn.app.MainViewModel
import com.myvpn.app.ui.components.NeonBackground
import com.myvpn.app.ui.theme.NeonCyan
import com.myvpn.app.ui.theme.NeonPurple
import com.myvpn.app.ui.theme.TextMuted

@Composable
fun SpeedTestScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    NeonBackground {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Тест скорости",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Загрузка 10 МБ с proof.ovh.net (вне VPN, если доступна сеть без туннеля)",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (viewModel.speedTestRunning) {
                CircularProgressIndicator(color = NeonCyan)
            } else {
                Button(
                    onClick = { viewModel.runSpeedTest(ctx.applicationContext) },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                ) {
                    Text("Запустить тест")
                }
            }
            viewModel.speedPingMs?.let { ms ->
                Text(
                    text = "Задержка (условно): ${ms} мс",
                    style = MaterialTheme.typography.titleMedium,
                    color = NeonCyan,
                )
            }
            viewModel.speedDownloadMbps?.let { m ->
                Text(
                    text = "Скорость загрузки: ${"%.2f".format(m)} Мбит/с",
                    style = MaterialTheme.typography.titleMedium,
                    color = NeonCyan,
                )
            }
        }
    }
}
