package com.hyse.debtslayer.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFCE93D8),              // ✅ FIX: purple lebih terang supaya terbaca di dark bg (bukan MaiPurple yang terlalu gelap)
    onPrimary = Color(0xFF1C1B1F),            // ✅ FIX: teks di atas primary → gelap (karena primary sekarang terang)
    primaryContainer = Color(0xFF4A235A),     // ✅ FIX: container lebih terang dari sebelumnya (#6A1B9A terlalu gelap)
    onPrimaryContainer = Color(0xFFEDD9F5),   // ✅ FIX: teks di container → sangat terang, readable

    secondary = PurpleGrey80,
    onSecondary = Color(0xFF1C1B1F),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = PurpleGrey80,

    tertiary = Pink80,

    background = BackgroundDark,
    onBackground = Color(0xFFE6E1E5),

    surface = Color(0xFF2A2730),              // ✅ FIX: sedikit lebih terang dari pure dark, card lebih visible
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF3D3847),       // ✅ FIX: lebih terang dari #49454F supaya konten di atasnya kontras
    onSurfaceVariant = Color(0xFFD5CFE0),     // ✅ FIX: lebih terang dari #CAC4D0

    error = DebtRedDark,                      // ✅ sudah di-fix di Color.kt → #EF5350
    onError = Color.White,

    outline = Color(0xFFAA9BB5)              // ✅ FIX: outline lebih terang supaya border card visible
)

private val LightColorScheme = lightColorScheme(
    primary = MaiPurple,
    onPrimary = Color.White,
    primaryContainer = MaiPurpleLight,
    onPrimaryContainer = Color(0xFF21005D),

    secondary = PurpleGrey40,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),

    tertiary = Pink40,

    background = BackgroundLight,
    onBackground = Color(0xFF1C1B1F),

    surface = SurfaceLight,
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),

    error = DebtRed,
    onError = Color.White,

    outline = Color(0xFF79747E)
)

@Composable
fun DebtSlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
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