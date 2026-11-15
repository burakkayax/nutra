package com.nutra.app.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class NutraSpacing(
    val micro: Dp = 4.dp,
    val extraSmall: Dp = 6.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 12.dp,
    val standard: Dp = 16.dp,
    val screenHorizontal: Dp = 20.dp,
    val heroCardPadding: Dp = 20.dp,
    val sectionGap: Dp = 24.dp,
    val extraLarge: Dp = 32.dp,
)

/**
 * Concentric corner radius hierarchy: R_outer = R_inner + Padding
 * Hero Card (28dp) -> Inner Container (14dp)
 * Standard Card (22dp) -> Inner Button/Chip (10dp)
 */
@Immutable
data class NutraShapes(
    val heroCard: Shape = RoundedCornerShape(28.dp),
    val standardCard: Shape = RoundedCornerShape(22.dp),
    val innerSubCard: Shape = RoundedCornerShape(14.dp),
    val chip: Shape = RoundedCornerShape(10.dp),
    val pill: Shape = RoundedCornerShape(percent = 50),
)

val LocalNutraSpacing = staticCompositionLocalOf { NutraSpacing() }
val LocalNutraShapes = staticCompositionLocalOf { NutraShapes() }
