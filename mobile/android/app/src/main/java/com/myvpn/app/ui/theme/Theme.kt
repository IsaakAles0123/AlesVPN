package com.myvpn.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = NeonPurple,
    onPrimary = Color.White,
    primaryContainer = NeonPurpleDim,
    secondary = NeonCyan,
    onSecondary = Color.Black,
    background = BackgroundDeep,
    onBackground = TextPrimary,
    surface = CardGlass,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFF1A1520),
    onSurfaceVariant = TextMuted,
)

@Composable
fun AlesVPNTheme(
    @Suppress("UNUSED_PARAMETER")
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = AlesTypography,
        content = content,
    )
}
