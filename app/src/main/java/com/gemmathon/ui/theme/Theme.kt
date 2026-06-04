package com.gemmathon.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF86EFAC),       // Green 300
    onPrimary = Color(0xFF003920),
    primaryContainer = Color(0xFF00522E),
    onPrimaryContainer = Color(0xFFA7F3D0),
    secondary = Color(0xFF7DD3FC),      // Sky 300
    onSecondary = Color(0xFF003549),
    secondaryContainer = Color(0xFF004C68),
    onSecondaryContainer = Color(0xFFBAE6FD),
    tertiary = Color(0xFFC4B5FD),       // Violet 300
    onTertiary = Color(0xFF2E1065),
    background = Color(0xFF0F172A),     // Slate 900
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF1E293B),        // Slate 800
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF334155), // Slate 700
    onSurfaceVariant = Color(0xFFCBD5E1),
    error = Color(0xFFFCA5A5),          // Red 300
    onError = Color(0xFF7F1D1D)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF16A34A),        // Green 600
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCFCE7),
    onPrimaryContainer = Color(0xFF052E16),
    secondary = Color(0xFF0284C7),      // Sky 600
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF082F49),
    tertiary = Color(0xFF7C3AED),       // Violet 600
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF)
)

@Composable
fun GemmathonTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
