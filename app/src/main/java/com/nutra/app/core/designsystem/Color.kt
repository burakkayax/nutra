package com.nutra.app.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Nutra color system — "Calm Neutral".
 *
 * Principles:
 *  - Neutral, slightly warm greys carry 95% of the UI.
 *  - "Ink" (near-black in light, near-white in dark) is the primary action color.
 *  - A single brand accent (green) marks progress / positive state.
 *  - Macro colors are harmonised and only used for data (rings, bars, dots).
 *  - No borders on cards: hierarchy comes from surface contrast + spacing.
 */
@Immutable
data class NutraColors(
    val isDark: Boolean,

    // Surfaces
    val background: Color,
    val surface: Color,
    val surfaceMuted: Color,
    val surfaceElevated: Color,
    val segmentThumb: Color,
    val divider: Color,
    val scrim: Color,

    // Text
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,

    // Primary action ("ink")
    val ink: Color,
    val onInk: Color,

    // Brand accent
    val accent: Color,
    val accentSoft: Color,
    val onAccent: Color,

    // Data colors
    val protein: Color,
    val carbs: Color,
    val fat: Color,
    val water: Color,

    // Status
    val warning: Color,
    val danger: Color,
    val dangerSoft: Color,
    val favorite: Color,
)

val LightNutraColors = NutraColors(
    isDark = false,
    background = Color(0xFFF5F5F3),
    surface = Color(0xFFFFFFFF),
    surfaceMuted = Color(0xFFEFEFEC),
    surfaceElevated = Color(0xFFFFFFFF),
    segmentThumb = Color(0xFFFFFFFF),
    divider = Color(0xFFE8E8E4),
    scrim = Color(0x66000000),

    textPrimary = Color(0xFF111111),
    textSecondary = Color(0xFF6B6B68),
    textTertiary = Color(0xFF767672),

    ink = Color(0xFF111111),
    onInk = Color(0xFFFFFFFF),

    accent = Color(0xFF118749),
    accentSoft = Color(0xFFE2F4E9),
    onAccent = Color(0xFFFFFFFF),

    protein = Color(0xFFEF5F5B),
    carbs = Color(0xFFF0A23B),
    fat = Color(0xFF6B7BF2),
    water = Color(0xFF2F9BEF),

    warning = Color(0xFFE5822F),
    danger = Color(0xFFE5484D),
    dangerSoft = Color(0xFFFCE9EA),
    favorite = Color(0xFFE5484D),
)

val DarkNutraColors = NutraColors(
    isDark = true,
    background = Color(0xFF0C0C0C),
    surface = Color(0xFF171717),
    surfaceMuted = Color(0xFF242424),
    surfaceElevated = Color(0xFF1C1C1C),
    segmentThumb = Color(0xFF383838),
    divider = Color(0xFF262626),
    scrim = Color(0x99000000),

    textPrimary = Color(0xFFF4F4F2),
    textSecondary = Color(0xFFA3A39E),
    textTertiary = Color(0xFF858580),

    ink = Color(0xFFF4F4F2),
    onInk = Color(0xFF0C0C0C),

    accent = Color(0xFF34D27B),
    accentSoft = Color(0xFF12301F),
    onAccent = Color(0xFF06140C),

    protein = Color(0xFFFF7A72),
    carbs = Color(0xFFFFB95C),
    fat = Color(0xFF8D99FF),
    water = Color(0xFF5BB5FF),

    warning = Color(0xFFFF9A4D),
    danger = Color(0xFFFF6369),
    dangerSoft = Color(0xFF3B1819),
    favorite = Color(0xFFFF6369),
)

val LocalNutraColors = staticCompositionLocalOf { LightNutraColors }
