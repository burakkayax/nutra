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

private const val TABULAR = "tnum, cv05, ss01"

private fun style(
    size: Int,
    line: Int,
    weight: FontWeight,
    tracking: Double = 0.0,
    tabular: Boolean = false,
) = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
    fontFeatureSettings = if (tabular) TABULAR else "cv05, ss01",
)

/**
 * Type scale. Few sizes, strong contrast between levels.
 *
 * display      — hero numbers (calories left)          48 / Bold
 * numberLarge  — big stats in cards                    28 / Bold
 * numberMedium — medium stats / list values            20 / SemiBold
 * titleLarge   — screen titles                         28 / Bold
 * titleMedium  — section titles / sheet titles         19 / SemiBold
 * titleSmall   — card titles / list item titles        15 / SemiBold
 * bodyLarge    — primary paragraph / inputs            16 / Regular
 * body         — default body                          14 / Regular
 * bodySmall    — secondary body                        13 / Regular
 * label        — buttons, chips, tabs                  14 / SemiBold
 * labelSmall   — small labels, captions emphasised     12 / SemiBold
 * caption      — meta text                             12 / Regular
 * overline     — tiny section eyebrow                  11 / SemiBold, tracked
 */
@Immutable
data class NutraTypography(
    val display: TextStyle,
    val numberLarge: TextStyle,
    val numberMedium: TextStyle,
    val numberSmall: TextStyle,
    val titleLarge: TextStyle,
    val titleMedium: TextStyle,
    val titleSmall: TextStyle,
    val bodyLarge: TextStyle,
    val body: TextStyle,
    val bodySmall: TextStyle,
    val label: TextStyle,
    val labelSmall: TextStyle,
    val caption: TextStyle,
    val overline: TextStyle,
)

val DefaultNutraTypography = NutraTypography(
    display = style(48, 52, FontWeight.Bold, -1.8, tabular = true),
    numberLarge = style(28, 32, FontWeight.Bold, -0.9, tabular = true),
    numberMedium = style(20, 24, FontWeight.SemiBold, -0.5, tabular = true),
    numberSmall = style(15, 20, FontWeight.SemiBold, -0.2, tabular = true),
    titleLarge = style(28, 34, FontWeight.Bold, -0.9),
    titleMedium = style(19, 24, FontWeight.SemiBold, -0.4),
    titleSmall = style(15, 20, FontWeight.SemiBold, -0.15),
    bodyLarge = style(16, 23, FontWeight.Normal, -0.1),
    body = style(14, 20, FontWeight.Normal),
    bodySmall = style(13, 18, FontWeight.Normal),
    label = style(14, 18, FontWeight.SemiBold, -0.1),
    labelSmall = style(12, 16, FontWeight.SemiBold),
    caption = style(12, 16, FontWeight.Normal, tabular = true),
    overline = style(11, 14, FontWeight.SemiBold, 0.6),
)

val Material3Typography = Typography(
    displayLarge = DefaultNutraTypography.display,
    headlineLarge = DefaultNutraTypography.titleLarge,
    headlineMedium = DefaultNutraTypography.titleLarge,
    titleLarge = DefaultNutraTypography.titleMedium,
    titleMedium = DefaultNutraTypography.titleSmall,
    titleSmall = DefaultNutraTypography.titleSmall,
    bodyLarge = DefaultNutraTypography.bodyLarge,
    bodyMedium = DefaultNutraTypography.body,
    bodySmall = DefaultNutraTypography.bodySmall,
    labelLarge = DefaultNutraTypography.label,
    labelMedium = DefaultNutraTypography.labelSmall,
    labelSmall = DefaultNutraTypography.overline,
)

val LocalNutraTypography = staticCompositionLocalOf { DefaultNutraTypography }
