package com.myvpn.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = AccentGold,
    onPrimary = Color(0xFF1A1408),
    primaryContainer = AccentGoldDim,
    onPrimaryContainer = Color(0xFFFFE8A8),
    secondary = AccentGoldBright,
    onSecondary = Color(0xFF1A1408),
    tertiary = GoldPatch,
    onTertiary = Color(0xFF1A1408),
    background = BackgroundDeep,
    onBackground = TextPrimary,
    surface = CardGlass,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFF1E1810),
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
