package com.kobser.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = KobserYellow,
    onPrimary = KobserBlack,
    primaryContainer = Color(0xFF4B2E00), // Darker orange for containers
    onPrimaryContainer = KobserYellow,

    secondary = KobserSurfaceContainerHigh,
    onSecondary = KobserOnSurface,
    secondaryContainer = KobserSurfaceVariant,
    onSecondaryContainer = KobserOnSurface,

    background = KobserBackground,
    onBackground = KobserOnSurface,

    surface = KobserSurface,
    onSurface = KobserOnSurface,
    surfaceVariant = KobserSurfaceVariant,
    onSurfaceVariant = KobserOnSurface,

    surfaceContainer = KobserSurfaceContainer,
    surfaceContainerLow = KobserSurface,
    surfaceContainerHigh = KobserSurfaceContainerHigh,
    surfaceContainerHighest = KobserSurfaceContainerHighest,

    outline = KobserOutline,
    outlineVariant = KobserOutlineVariant,

    error = Color(0xFFFF5449),
    onError = KobserBlack,
    errorContainer = Color(0xFF410E0B),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun KobserTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = KobserTypography,
        content = content,
    )
}
