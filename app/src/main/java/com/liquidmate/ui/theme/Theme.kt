package com.liquidmate.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFF3D8BFF)
val AccentSoft = Color(0xFF7FC4FF)

private val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = Color(0xFF7C6CFF),
    background = Color(0xFFF4F6FB),
    surface = Color(0xFFF7F8FC),
    surfaceVariant = Color(0xFFE9EDF5),
    onSurface = Color(0xFF141A26),
    onSurfaceVariant = Color(0xFF5B6472),
)

private val DarkColors = darkColorScheme(
    primary = AccentSoft,
    onPrimary = Color(0xFF05101F),
    secondary = Color(0xFFB5A8FF),
    background = Color(0xFF0A0E17),
    surface = Color(0xFF11151F),
    surfaceVariant = Color(0xFF232A38),
    onSurface = Color(0xFFE8ECF4),
    onSurfaceVariant = Color(0xFF9AA3B2),
)

enum class ThemeMode { System, Light, Dark }

@Composable
fun LiquidMateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
