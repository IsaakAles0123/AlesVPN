package com.myvpn.app.ui

import androidx.compose.runtime.Composable
import com.myvpn.app.MainViewModel
import com.myvpn.app.ui.screens.HomeScreen

@Composable
fun AlesVpnApp(
    viewModel: MainViewModel,
    onConnectClick: () -> Unit,
    onStopClick: () -> Unit,
) {
    HomeScreen(
        viewModel = viewModel,
        onConnectClick = onConnectClick,
        onStopClick = onStopClick,
    )
}
