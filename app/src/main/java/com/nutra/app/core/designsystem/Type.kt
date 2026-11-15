package com.nutra.app.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.nutra.app.R

val InterFontFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.inter_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.inter_semibold, FontWeight.Medium, FontStyle.Normal),
    Font(R.font.inter_semibold, FontWeight.SemiBold, FontStyle.Normal),
    Font(R.font.inter_semibold, FontWeight.Bold, FontStyle.Normal),
    Font(R.font.inter_semibold_italic, FontWeight.SemiBold, FontStyle.Italic),
)

private const val TABULAR_FEATURES = "tnum, cv05"

@Immutable
data class NutraCustomTypography(
    val heroCalorieNumber: TextStyle,
    val statLargeNumber: TextStyle,
    val screenHeader: TextStyle,
    val sectionTitle: TextStyle,
    val cardTitle: TextStyle,
    val foodItemTitle: TextStyle,
    val bodyRegular: TextStyle,
    val bodySmall: TextStyle,
    val macroValue: TextStyle,
    val macroLabel: TextStyle,
    val badgeLabel: TextStyle,
)

val DefaultNutraCustomTypography = NutraCustomTypography(
    heroCalorieNumber = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 38.sp,
        lineHeight = 42.sp,
        letterSpacing = (-1.2).sp,
        fontFeatureSettings = TABULAR_FEATURES,
    ),
    statLargeNumber = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.8).sp,
        fontFeatureSettings = TABULAR_FEATURES,
    ),
    screenHeader = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.5).sp,
        fontFeatureSettings = TABULAR_FEATURES,
    ),
    sectionTitle = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.3).sp,
        fontFeatureSettings = TABULAR_FEATURES,
    ),
    cardTitle = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 21.sp,
        letterSpacing = (-0.2).sp,
        fontFeatureSettings = TABULAR_FEATURES,
    ),
    foodItemTitle = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.1).sp,
        fontFeatureSettings = TABULAR_FEATURES,
    ),
    bodyRegular = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontFeatureSettings = TABULAR_FEATURES,
    ),
    bodySmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontFeatureSettings = TABULAR_FEATURES,
    ),
    macroValue = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 17.sp,
        letterSpacing = (-0.1).sp,
        fontFeatureSettings = TABULAR_FEATURES,
    ),
    macroLabel = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp,
        fontFeatureSettings = TABULAR_FEATURES,
    ),
    badgeLabel = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.2.sp,
        fontFeatureSettings = TABULAR_FEATURES,
    ),
)

val Material3Typography = Typography(
    displayLarge = DefaultNutraCustomTypography.heroCalorieNumber,
    headlineMedium = DefaultNutraCustomTypography.screenHeader,
    titleLarge = DefaultNutraCustomTypography.sectionTitle,
    titleMedium = DefaultNutraCustomTypography.cardTitle,
    bodyLarge = DefaultNutraCustomTypography.foodItemTitle,
    bodyMedium = DefaultNutraCustomTypography.bodyRegular,
    bodySmall = DefaultNutraCustomTypography.bodySmall,
    labelLarge = DefaultNutraCustomTypography.macroValue,
    labelMedium = DefaultNutraCustomTypography.macroLabel,
    labelSmall = DefaultNutraCustomTypography.badgeLabel,
)

val LocalNutraTypography = staticCompositionLocalOf { DefaultNutraCustomTypography }
