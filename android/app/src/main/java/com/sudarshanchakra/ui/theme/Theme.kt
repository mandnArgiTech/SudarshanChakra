package com.sudarshanchakra.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = Terracotta,
    onPrimary = CardWhite,
    primaryContainer = Terracotta.copy(alpha = 0.12f),
    onPrimaryContainer = TerracottaDark,
    secondary = HighOrange,
    onSecondary = CardWhite,
    secondaryContainer = HighOrange.copy(alpha = 0.12f),
    onSecondaryContainer = HighOrange,
    tertiary = SuccessGreen,
    onTertiary = CardWhite,
    tertiaryContainer = SuccessGreen.copy(alpha = 0.12f),
    onTertiaryContainer = SuccessGreen,
    background = CreamBackground,
    onBackground = TextPrimary,
    surface = CardWhite,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceLight,
    onSurfaceVariant = TextSecondary,
    outline = DividerColor,
    outlineVariant = DividerColor.copy(alpha = 0.5f),
    error = CriticalRed,
    onError = CardWhite
)

@Composable
fun SudarshanChakraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
