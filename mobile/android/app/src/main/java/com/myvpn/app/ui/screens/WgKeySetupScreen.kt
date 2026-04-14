package com.myvpn.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.myvpn.app.data.VpnSettingsRepository
import com.myvpn.app.ui.components.NeonBackground
import com.myvpn.app.ui.theme.NeonCyan
import com.myvpn.app.ui.theme.TextMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WgKeySetupScreen(
    repository: VpnSettingsRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val scroll = rememberScrollState()

    var privateKey by remember { mutableStateOf(repository.loadPrivateKey()) }
    var address by remember { mutableStateOf(repository.loadAddress()) }
    var pasteBox by remember { mutableStateOf("") }

    NeonBackground {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Ключ доступа",
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Назад",
                                tint = Color.White,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Вставьте сообщение от продавца: первая строка — приватный ключ WireGuard, " +
                        "вторая — ваш IP в сети VPN (например 10.8.0.5 или 10.8.0.5/32). " +
                        "Параметры сервера уже заданы в приложении.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                )

                OutlinedTextField(
                    value = pasteBox,
                    onValueChange = { pasteBox = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Вставить из буфера") },
                    placeholder = {
                        Text(
                            "Строка 1: ключ\nСтрока 2: 10.8.0.5/32",
                            color = TextMuted,
                        )
                    },
                    minLines = 4,
                )

                Button(
                    onClick = {
                        val parsed = VpnSettingsRepository.parseSellerMessage(pasteBox)
                        if (parsed == null) {
                            Toast.makeText(ctx, "Нет текста для разбора", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        privateKey = parsed.first
                        address = parsed.second
                        if (address.isBlank()) {
                            Toast.makeText(
                                ctx,
                                "Добавьте вторую строку с IP или укажите IP ниже",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan.copy(alpha = 0.25f)),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Rounded.ContentPaste, contentDescription = null, tint = NeonCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Разобрать вставку", color = NeonCyan)
                    }
                }

                Text("Или введите вручную:", style = MaterialTheme.typography.labelLarge, color = TextMuted)

                OutlinedTextField(
                    value = privateKey,
                    onValueChange = { privateKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Приватный ключ") },
                    minLines = 2,
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("IP в VPN (Address)") },
                    placeholder = { Text("10.8.0.5/32") },
                    singleLine = true,
                )

                Button(
                    onClick = {
                        val pk = privateKey.trim()
                        val ad = VpnSettingsRepository.normalizeAddress(address)
                        if (pk.isBlank() || ad.isBlank()) {
                            Toast.makeText(
                                ctx,
                                "Нужны приватный ключ и IP в сети VPN",
                                Toast.LENGTH_LONG,
                            ).show()
                            return@Button
                        }
                        repository.save(pk, ad)
                        onBack()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                ) {
                    Text("Сохранить", color = Color.Black)
                }
            }
        }
    }
}
