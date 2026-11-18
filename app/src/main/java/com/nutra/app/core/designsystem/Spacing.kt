package com.nutra.app.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 4dp base grid. */
@Immutable
data class NutraSpacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 20.dp,
    val xxl: Dp = 24.dp,
    /** Horizontal padding of every screen. */
    val screen: Dp = 20.dp,
    /** Default inner padding of cards. */
    val card: Dp = 20.dp,
    /** Vertical gap between top-level sections of a screen. */
    val section: Dp = 28.dp,
)

/**
 * Corner radii. Outer containers are rounder than inner elements
 * (outer = inner + padding) so nested shapes look concentric.
 */
@Immutable
data class NutraShapes(
    val xs: Shape = RoundedCornerShape(8.dp),
    val sm: Shape = RoundedCornerShape(12.dp),
    val md: Shape = RoundedCornerShape(16.dp),
    val lg: Shape = RoundedCornerShape(24.dp),
    val xl: Shape = RoundedCornerShape(32.dp),
    val sheet: Shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    val pill: Shape = RoundedCornerShape(percent = 50),
)

val LocalNutraSpacing = staticCompositionLocalOf { NutraSpacing() }
val LocalNutraShapes = staticCompositionLocalOf { NutraShapes() }
