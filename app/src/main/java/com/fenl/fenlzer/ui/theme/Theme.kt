package com.fenl.fenlzer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import com.fenl.fenlzer.data.settings.ThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = FenlzerPurple,
    onPrimary = FenlzerTextPrimary,
    secondary = FenlzerTeal,
    onSecondary = FenlzerAmoledBackground,
    tertiary = FenlzerPurpleMuted,
    background = FenlzerDarkBackground,
    onBackground = FenlzerTextPrimary,
    surface = FenlzerDarkSurface,
    onSurface = FenlzerTextPrimary,
    surfaceVariant = FenlzerDarkSurfaceRaised,
    onSurfaceVariant = FenlzerTextSecondary,
    outline = FenlzerOutline
)

private val AmoledColorScheme = darkColorScheme(
    primary = FenlzerPurple,
    onPrimary = FenlzerTextPrimary,
    secondary = FenlzerTeal,
    onSecondary = FenlzerAmoledBackground,
    tertiary = FenlzerPurpleMuted,
    background = FenlzerAmoledBackground,
    onBackground = FenlzerTextPrimary,
    surface = FenlzerAmoledSurface,
    onSurface = FenlzerTextPrimary,
    surfaceVariant = FenlzerDarkSurface,
    onSurfaceVariant = FenlzerTextSecondary,
    outline = FenlzerOutline
)

@Composable
fun FenlzerTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeMode) {
        ThemeMode.DARK -> DarkColorScheme
        ThemeMode.AMOLED -> AmoledColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
