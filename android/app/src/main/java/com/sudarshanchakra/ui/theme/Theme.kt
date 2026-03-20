package com.sudarshanchakra.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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
    onError = CardWhite,
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFE8A04A),
    onPrimary = Color(0xFF1A1510),
    primaryContainer = Color(0xFF5C3D14),
    onPrimaryContainer = Color(0xFFFFE0B0),
    secondary = Color(0xFFE8A060),
    onSecondary = Color(0xFF1A1510),
    tertiary = Color(0xFF7BC48A),
    onTertiary = Color(0xFF0D1A10),
    background = Color(0xFF1A1814),
    onBackground = Color(0xFFE8E2D8),
    surface = Color(0xFF2A2620),
    onSurface = Color(0xFFE8E2D8),
    surfaceVariant = Color(0xFF3A342C),
    onSurfaceVariant = Color(0xFFC8C2B8),
    outline = Color(0xFF6A6258),
    outlineVariant = Color(0xFF4A443C),
    error = Color(0xFFE85545),
    onError = Color(0xFFFFFFFF),
)

@Composable
fun SudarshanChakraTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content,
    )
}
