package com.myvpn.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.myvpn.app.MainViewModel
import com.myvpn.app.ui.screens.HomeScreen
import com.myvpn.app.ui.screens.SpeedTestScreen
import com.myvpn.app.ui.theme.NeonCyan
import com.myvpn.app.ui.theme.NeonPurple
import com.myvpn.app.ui.theme.TextMuted

private sealed class Tab(val route: String, val label: String) {
    data object Home : Tab("home", "Главная")
    data object Speed : Tab("speed", "Тест")
}

@Composable
fun AlesVpnApp(
    viewModel: MainViewModel,
    onConnectClick: () -> Unit,
    onStopClick: () -> Unit,
) {
    val nav = rememberNavController()
    val tabs = listOf(Tab.Home to Icons.Filled.Home, Tab.Speed to Icons.Filled.NetworkCheck)
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route ?: Tab.Home.route

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xCC050508),
            ) {
                tabs.forEach { (tab, icon) ->
                    val selected = current == tab.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(Tab.Home.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (selected) NeonCyan else TextMuted,
                            )
                        },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = NeonCyan,
                            selectedTextColor = NeonCyan,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted,
                            indicatorColor = NeonPurple.copy(alpha = 0.35f),
                        ),
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Tab.Home.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Tab.Home.route) {
                HomeScreen(
                    viewModel = viewModel,
                    onConnectClick = onConnectClick,
                    onStopClick = onStopClick,
                )
            }
            composable(Tab.Speed.route) {
                SpeedTestScreen(viewModel = viewModel)
            }
        }
    }
}
