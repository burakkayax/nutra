package com.nutra.app.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Light Theme — Warm Alabaster
val LightBackground = Color(0xFFF7F6F2)
val LightCardSurface = Color(0xFFFFFFFF)
val LightElevatedSurface = Color(0xFFEFECE6)
val LightHairlineBorder = Color(0xFFE2DFD7)
val LightTextPrimary = Color(0xFF121417)
val LightTextSecondary = Color(0xFF5C616B)
val LightTextMuted = Color(0xFF8A909C)

// Dark Theme — Deep Obsidian (OLED friendly)
val DarkBackground = Color(0xFF0B0C0E)
val DarkCardSurface = Color(0xFF14161B)
val DarkElevatedSurface = Color(0xFF1D2027)
val DarkHairlineBorder = Color(0xFF252932)
val DarkTextPrimary = Color(0xFFF3F4F6)
val DarkTextSecondary = Color(0xFF949BA6)
val DarkTextMuted = Color(0xFF646B78)

// Semantic Nutrition Accents — Light
val LightCalorieAccent = Color(0xFF059669)
val LightCalorieContainer = Color(0xFFD1FAE5)
val LightOverBudgetAccent = Color(0xFFD97706)
val LightOverBudgetContainer = Color(0xFFFEF3C7)

val LightProteinAccent = Color(0xFFE11D48)
val LightProteinContainer = Color(0xFFFFE4E6)

val LightCarbsAccent = Color(0xFFD97706)
val LightCarbsContainer = Color(0xFFFEF3C7)

val LightFatAccent = Color(0xFF7C3AED)
val LightFatContainer = Color(0xFFEDE9FE)

val LightWaterAccent = Color(0xFF0284C7)
val LightWaterContainer = Color(0xFFE0F2FE)

// Semantic Nutrition Accents — Dark
val DarkCalorieAccent = Color(0xFF10B981)
val DarkCalorieContainer = Color(0xFF064E3B)
val DarkOverBudgetAccent = Color(0xFFF59E0B)
val DarkOverBudgetContainer = Color(0xFF451A03)

val DarkProteinAccent = Color(0xFFF43F5E)
val DarkProteinContainer = Color(0xFF4C0519)

val DarkCarbsAccent = Color(0xFFF59E0B)
val DarkCarbsContainer = Color(0xFF451A03)

val DarkFatAccent = Color(0xFF8B5CF6)
val DarkFatContainer = Color(0xFF2E1065)

val DarkWaterAccent = Color(0xFF38BDF8)
val DarkWaterContainer = Color(0xFF0C4A6E)

@Immutable
data class NutraSemanticColors(
    val isDark: Boolean,
    val background: Color,
    val cardSurface: Color,
    val elevatedSurface: Color,
    val hairlineBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val calorieAccent: Color,
    val calorieContainer: Color,
    val overBudgetAccent: Color,
    val overBudgetContainer: Color,
    val proteinAccent: Color,
    val proteinContainer: Color,
    val carbsAccent: Color,
    val carbsContainer: Color,
    val fatAccent: Color,
    val fatContainer: Color,
    val waterAccent: Color,
    val waterContainer: Color,
)

val LightNutraSemanticColors = NutraSemanticColors(
    isDark = false,
    background = LightBackground,
    cardSurface = LightCardSurface,
    elevatedSurface = LightElevatedSurface,
    hairlineBorder = LightHairlineBorder,
    textPrimary = LightTextPrimary,
    textSecondary = LightTextSecondary,
    textMuted = LightTextMuted,
    calorieAccent = LightCalorieAccent,
    calorieContainer = LightCalorieContainer,
    overBudgetAccent = LightOverBudgetAccent,
    overBudgetContainer = LightOverBudgetContainer,
    proteinAccent = LightProteinAccent,
    proteinContainer = LightProteinContainer,
    carbsAccent = LightCarbsAccent,
    carbsContainer = LightCarbsContainer,
    fatAccent = LightFatAccent,
    fatContainer = LightFatContainer,
    waterAccent = LightWaterAccent,
    waterContainer = LightWaterContainer,
)

val DarkNutraSemanticColors = NutraSemanticColors(
    isDark = true,
    background = DarkBackground,
    cardSurface = DarkCardSurface,
    elevatedSurface = DarkElevatedSurface,
    hairlineBorder = DarkHairlineBorder,
    textPrimary = DarkTextPrimary,
    textSecondary = DarkTextSecondary,
    textMuted = DarkTextMuted,
    calorieAccent = DarkCalorieAccent,
    calorieContainer = DarkCalorieContainer,
    overBudgetAccent = DarkOverBudgetAccent,
    overBudgetContainer = DarkOverBudgetContainer,
    proteinAccent = DarkProteinAccent,
    proteinContainer = DarkProteinContainer,
    carbsAccent = DarkCarbsAccent,
    carbsContainer = DarkCarbsContainer,
    fatAccent = DarkFatAccent,
    fatContainer = DarkFatContainer,
    waterAccent = DarkWaterAccent,
    waterContainer = DarkWaterContainer,
)

val LocalNutraColors = staticCompositionLocalOf { LightNutraSemanticColors }
