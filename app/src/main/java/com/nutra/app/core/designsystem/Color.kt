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
    val controlBorder: Color,
    val controlBorderFocused: Color,
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
    val onAccentSoft: Color,
    val accentOnInk: Color,

    // Data colors
    val protein: Color,
    val carbs: Color,
    val fat: Color,
    val water: Color,

    // Status
    val warning: Color,
    val warningText: Color,
    val danger: Color,
    val dangerText: Color,
    val dangerSoft: Color,
    val dangerOnInk: Color,
    val favorite: Color,
)

val LightNutraColors = NutraColors(
    isDark = false,
    background = Color(0xFFF5F5F3),
    surface = Color(0xFFFFFFFF),
    surfaceMuted = Color(0xFFEFEFEC),
    surfaceElevated = Color(0xFFFFFFFF),
    segmentThumb = Color(0xFFFFFFFF),
    divider = Color(0xFFE3E3DE),
    controlBorder = Color(0xFF7D827E),
    controlBorderFocused = Color(0xFF0E7F43),
    scrim = Color(0x66000000),

    textPrimary = Color(0xFF111111),
    textSecondary = Color(0xFF52524E),
    textTertiary = Color(0xFF666662),

    ink = Color(0xFF111111),
    onInk = Color(0xFFFFFFFF),

    accent = Color(0xFF0E7F43),
    accentSoft = Color(0xFFE2F4E9),
    onAccent = Color(0xFFFFFFFF),
    onAccentSoft = Color(0xFF0B6334),
    accentOnInk = Color(0xFF5CE092),

    protein = Color(0xFFD94440),
    carbs = Color(0xFFC47A12),
    fat = Color(0xFF4F60D9),
    water = Color(0xFF197CD2),

    warning = Color(0xFFCC6614),
    warningText = Color(0xFF9A4600),
    danger = Color(0xFFD9383E),
    dangerText = Color(0xFFC62832),
    dangerSoft = Color(0xFFFCE9EA),
    dangerOnInk = Color(0xFFFF7A80),
    favorite = Color(0xFFD9383E),
)

val DarkNutraColors = NutraColors(
    isDark = true,
    background = Color(0xFF0C0C0C),
    surface = Color(0xFF171717),
    surfaceMuted = Color(0xFF242424),
    surfaceElevated = Color(0xFF1C1C1C),
    segmentThumb = Color(0xFF383838),
    divider = Color(0xFF2C2C2A),
    controlBorder = Color(0xFF6F7A74),
    controlBorderFocused = Color(0xFF56D18B),
    scrim = Color(0x99000000),

    textPrimary = Color(0xFFF4F4F2),
    textSecondary = Color(0xFFAEAEA9),
    textTertiary = Color(0xFF92928D),

    ink = Color(0xFFF4F4F2),
    onInk = Color(0xFF0C0C0C),

    accent = Color(0xFF34D27B),
    accentSoft = Color(0xFF12301F),
    onAccent = Color(0xFF06140C),
    onAccentSoft = Color(0xFF69E6A0),
    accentOnInk = Color(0xFF0E7A3E),

    protein = Color(0xFFFF7A72),
    carbs = Color(0xFFF5C856),
    fat = Color(0xFF8D99FF),
    water = Color(0xFF5BB5FF),

    warning = Color(0xFFFF8838),
    warningText = Color(0xFFFFA85C),
    danger = Color(0xFFFF6369),
    dangerText = Color(0xFFFF7A80),
    dangerSoft = Color(0xFF3B1819),
    dangerOnInk = Color(0xFFC62832),
    favorite = Color(0xFFFF6369),
)

val LocalNutraColors = staticCompositionLocalOf { LightNutraColors }
