package com.myvpn.app.ui

import androidx.compose.runtime.Composable
import com.myvpn.app.MainViewModel
import com.myvpn.app.data.VpnSettingsRepository
import com.myvpn.app.ui.screens.HomeScreen
import com.myvpn.app.ui.screens.WgKeySetupScreen

@Composable
fun AlesVpnApp(
    viewModel: MainViewModel,
    wgSettingsRepository: VpnSettingsRepository,
    onConnectClick: () -> Unit,
    onStopClick: () -> Unit,
) {
    if (viewModel.showWgKeySetup) {
        WgKeySetupScreen(
            repository = wgSettingsRepository,
            onBack = { viewModel.closeWgKeySetup() },
        )
    } else {
        HomeScreen(
            viewModel = viewModel,
            onConnectClick = onConnectClick,
            onStopClick = onStopClick,
            onOpenKeySetup = { viewModel.openWgKeySetup() },
        )
    }
}
