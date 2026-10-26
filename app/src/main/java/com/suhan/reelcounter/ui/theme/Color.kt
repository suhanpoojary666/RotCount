package com.suhan.reelcounter.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class ReelColors(
    val background: Color,
    val surface: Color,
    val text: Color,
    val textSecondary: Color,
    val separator: Color,
    val accent: Color,
    val barMuted: Color,
    val pill: Color
)

val LightReelColors = ReelColors(
    background = Color(0xFFF2F2F7),
    surface = Color(0xFFFFFFFF),
    text = Color(0xFF000000),
    textSecondary = Color(0xFF8E8E93),
    separator = Color(0xFFE5E5EA),
    accent = Color(0xFF007AFF),
    barMuted = Color(0xFFD1D1D6),
    pill = Color(0xFF000000)
)

val DarkReelColors = ReelColors(
    background = Color(0xFF000000),
    surface = Color(0xFF1C1C1E),
    text = Color(0xFFFFFFFF),
    textSecondary = Color(0xFF8E8E93),
    separator = Color(0xFF38383A),
    accent = Color(0xFF0A84FF),
    barMuted = Color(0xFF3A3A3C),
    pill = Color(0xFF000000)
)
