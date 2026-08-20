package com.sinura.personaltrainer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Forest,
    onPrimary = Color.White,
    primaryContainer = Lime,
    onPrimaryContainer = Forest,
    secondary = Leaf,
    onSecondary = Color.White,
    background = Sand,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE7EFE8),
    onSurfaceVariant = WarmGray,
    tertiary = Color(0xFFB86E00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE08A),
    onTertiaryContainer = Color(0xFF3F2A00),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Lime,
    onPrimary = Forest,
    primaryContainer = CardDark,
    onPrimaryContainer = Lime,
    secondary = Leaf,
    onSecondary = Color.White,
    background = Color(0xFF0B1A14),
    onBackground = Sand,
    surface = CardDark,
    onSurface = Sand,
    surfaceVariant = Color(0xFF1E3A30),
    onSurfaceVariant = Color(0xFFC5D2CA),
    tertiary = Color(0xFFFFB74D),
    onTertiary = Color(0xFF3F2A00),
    tertiaryContainer = Color(0xFF5C4300),
    onTertiaryContainer = Color(0xFFFFE08A),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun PersonalTrainerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}
