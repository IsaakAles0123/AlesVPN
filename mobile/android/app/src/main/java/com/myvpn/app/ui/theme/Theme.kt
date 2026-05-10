package com.myvpn.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = AccentGold,
    onPrimary = BackgroundDeep,
    primaryContainer = AccentGoldDim,
    onPrimaryContainer = TextPrimary,
    secondary = InkSecondary,
    onSecondary = BackgroundDeep,
    tertiary = AccentRed,
    onTertiary = Color.White,
    background = BackgroundDeep,
    onBackground = TextPrimary,
    surface = CardGlass,
    onSurface = TextPrimary,
    surfaceVariant = CardSolid,
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
