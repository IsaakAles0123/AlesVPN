package com.myvpn.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = AccentRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD6),
    onPrimaryContainer = Color(0xFF410002),
    secondary = AccentGold,
    onSecondary = Color.White,
    tertiary = InkSecondary,
    onTertiary = Color.White,
    background = BackgroundDeep,
    onBackground = TextPrimary,
    surface = Color.White,
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
        colorScheme = LightColors,
        typography = AlesTypography,
        content = content,
    )
}
