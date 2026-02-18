package com.hyse.debtslayer.ui.theme

import androidx.compose.ui.graphics.Color

// Light Theme Colors
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// Mai Purple Palette
val MaiPurple = Color(0xFF9C27B0)
val MaiPurpleLight = Color(0xFFE1BEE7)
val MaiPurpleDark = Color(0xFF6A1B9A)

// Status Colors
val DebtRed = Color(0xFFE53935)
val DebtRedDark = Color(0xFFEF5350)       // ✅ FIX: lebih terang supaya kontras di dark bg
val SuccessGreen = Color(0xFF43A047)
val SuccessGreenDark = Color(0xFF66BB6A)  // ✅ FIX: lebih terang di dark
val WarningOrange = Color(0xFFFF6F00)

// Chat Bubble Colors - LIGHT
val ChatUserBubbleLight = Color(0xFFE3F2FD)
val ChatAiBubbleLight = Color(0xFFF3E5F5)

// Chat Bubble Colors - DARK
val ChatUserBubbleDark = Color(0xFF1E3A5F)
val ChatAiBubbleDark = Color(0xFF6A1B9A)  // ✅ FIX: dinaikkan dari #4A148C → lebih terang supaya teks putih terbaca

// Background Colors
val BackgroundLight = Color(0xFFFFFBFE)
val BackgroundDark = Color(0xFF1C1B1F)

val SurfaceLight = Color(0xFFFFFBFE)
val SurfaceDark = Color(0xFF1C1B1F)

// Card Colors
val CardLight = Color(0xFFF5F5F5)
val CardDark = Color(0xFF2D2D2D)

// ✅ BARU: warna teks sekunder yang readable di dark theme
val OnSurfaceSecondaryDark = Color(0xFFB0AAB8)   // alpha ~0.7 tapi fixed, bukan relative
val OnSurfaceTertiaryDark = Color(0xFF8F8898)     // alpha ~0.55