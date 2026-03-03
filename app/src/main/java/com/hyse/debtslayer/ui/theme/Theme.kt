package com.hyse.debtslayer.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    // ── Primary ───────────────────────────────────────────────────
    primary            = MaiPurpleBright,       // #CE93D8 — terang, readable di dark
    onPrimary          = Color(0xFF1C1B1F),      // teks gelap di atas primary terang
    primaryContainer   = Color(0xFF4A235A),
    onPrimaryContainer = Color(0xFFEDD9F5),

    // ── Secondary ─────────────────────────────────────────────────
    secondary          = PurpleGrey80,
    onSecondary        = Color(0xFF1C1B1F),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFCCC2DC),

    // ── Tertiary ──────────────────────────────────────────────────
    tertiary           = Pink80,
    onTertiary         = Color(0xFF1C1B1F),
    tertiaryContainer  = Color(0xFF5C2D3E),
    onTertiaryContainer = Color(0xFFFFD8E4),

    // ── Background & Surface ──────────────────────────────────────
    background         = BackgroundDark,         // #141218
    onBackground       = TextPrimaryDark,        // #E6E1E5

    surface            = SurfaceDark,            // #1E1B22
    onSurface          = TextPrimaryDark,        // #E6E1E5
    surfaceVariant     = Color(0xFF3D3847),
    onSurfaceVariant   = TextSecondaryDark,      // #CAC4D0

    // ── Error ─────────────────────────────────────────────────────
    error              = DebtRedDark,            // #EF9A9A — terang di dark
    onError            = Color(0xFF1C1B1F),
    errorContainer     = Color(0xFF5C1A1A),
    onErrorContainer   = Color(0xFFFFDAD6),

    // ── Outline ───────────────────────────────────────────────────
    outline            = OutlineDark,            // #938F99
    outlineVariant     = Color(0xFF4D4458),

    // ── Surface tones ─────────────────────────────────────────────
    inverseSurface     = Color(0xFFE6E1E5),
    inverseOnSurface   = Color(0xFF1C1B1F),
    inversePrimary     = MaiPurple,
    scrim              = Color(0xFF000000)
)

private val LightColorScheme = lightColorScheme(
    // ── Primary ───────────────────────────────────────────────────
    primary            = MaiPurple,              // #9C27B0
    onPrimary          = Color.White,
    primaryContainer   = MaiPurpleLight,         // #E1BEE7
    onPrimaryContainer = Color(0xFF21005D),

    // ── Secondary ─────────────────────────────────────────────────
    secondary          = PurpleGrey40,
    onSecondary        = Color.White,
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),

    // ── Tertiary ──────────────────────────────────────────────────
    tertiary           = Pink40,
    onTertiary         = Color.White,
    tertiaryContainer  = Color(0xFFFFD8E4),
    onTertiaryContainer = Color(0xFF31111D),

    // ── Background & Surface ──────────────────────────────────────
    background         = BackgroundLight,
    onBackground       = TextPrimaryLight,

    surface            = SurfaceLight,
    onSurface          = TextPrimaryLight,
    surfaceVariant     = Color(0xFFE7E0EC),
    onSurfaceVariant   = TextSecondaryLight,

    // ── Error ─────────────────────────────────────────────────────
    error              = DebtRed,
    onError            = Color.White,
    errorContainer     = Color(0xFFFFDAD6),
    onErrorContainer   = Color(0xFF410002),

    // ── Outline ───────────────────────────────────────────────────
    outline            = OutlineLight,
    outlineVariant     = Color(0xFFCAC4D0),

    // ── Surface tones ─────────────────────────────────────────────
    inverseSurface     = Color(0xFF313033),
    inverseOnSurface   = Color(0xFFF4EFF4),
    inversePrimary     = MaiPurpleBright,
    scrim              = Color(0xFF000000)
)

@Composable
fun DebtSlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

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