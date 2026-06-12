package com.mimo.remote.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// MiMo brand colors
val MiMoCyan = Color(0xFF00D4FF)
val MiMoCyanDark = Color(0xFF00A8CC)
val MiMoPurple = Color(0xFF7C3AED)
val MiMoGreen = Color(0xFF10B981)
val MiMoRed = Color(0xFFEF4444)
val MiMoOrange = Color(0xFFF59E0B)

private val DarkColorScheme = darkColorScheme(
    primary = MiMoCyan,
    secondary = MiMoPurple,
    tertiary = MiMoGreen,
    background = Color(0xFF0D1117),
    surface = Color(0xFF161B22),
    surfaceVariant = Color(0xFF21262D),
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onBackground = Color(0xFFE6EDF3),
    onSurface = Color(0xFFE6EDF3),
    onSurfaceVariant = Color(0xFF8B949E),
    error = MiMoRed,
)

private val LightColorScheme = lightColorScheme(
    primary = MiMoCyanDark,
    secondary = MiMoPurple,
    tertiary = MiMoGreen,
    background = Color(0xFFF6F8FA),
    surface = Color.White,
    surfaceVariant = Color(0xFFF0F3F6),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF1F2328),
    onSurface = Color(0xFF1F2328),
    onSurfaceVariant = Color(0xFF656D76),
    error = MiMoRed,
)

@Composable
fun MiMoRemoteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MiMoTypography,
        content = content
    )
}
