package com.nutra.app.core.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

enum class CalorieDisplayMode {
    REMAINING,
    CONSUMED,
    PERCENTAGE,
    ;

    fun next(): CalorieDisplayMode = when (this) {
        REMAINING -> CONSUMED
        CONSUMED -> PERCENTAGE
        PERCENTAGE -> REMAINING
    }
}

@Composable
fun NutraCard(
    modifier: Modifier = Modifier,
    shape: Shape = NutraTheme.shapes.standardCard,
    backgroundColor: Color = NutraTheme.colors.cardSurface,
    borderColor: Color = NutraTheme.colors.hairlineBorder,
    contentPadding: PaddingValues = PaddingValues(NutraTheme.spacing.standard),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val borderStroke = BorderStroke(1.dp, borderColor)
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = backgroundColor,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = borderStroke,
        ) {
            Box(modifier = Modifier.padding(contentPadding)) {
                content()
            }
        }
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = backgroundColor,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = borderStroke,
        ) {
            Box(modifier = Modifier.padding(contentPadding)) {
                content()
            }
        }
    }
}

@Composable
fun NutraCalorieArcRing(
    consumedCalories: Int,
    targetCalories: Int,
    displayMode: CalorieDisplayMode,
    onToggleDisplayMode: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 152.dp,
    strokeWidth: Dp = 13.dp,
) {
    val haptic = LocalHapticFeedback.current
    val safeTarget = targetCalories.coerceAtLeast(1)
    val rawRatio = consumedCalories.toFloat() / safeTarget.toFloat()
    val isOverBudget = consumedCalories > safeTarget
    val overBudgetAmount = (consumedCalories - safeTarget).coerceAtLeast(0)
    val remainingCalories = (safeTarget - consumedCalories).coerceAtLeast(0)

    val animatedProgress by animateFloatAsState(
        targetValue = rawRatio.coerceIn(0f, 1.25f),
        animationSpec = spring(
            dampingRatio = 0.85f,
            stiffness = 320f,
        ),
        label = "CalorieArcProgress",
    )

    val ringColor by animateColorAsState(
        targetValue = if (isOverBudget) {
            NutraTheme.colors.overBudgetAccent
        } else {
            NutraTheme.colors.calorieAccent
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "CalorieRingColor",
    )

    val trackColor = NutraTheme.colors.elevatedSurface
    val overFlowColor = NutraTheme.colors.proteinAccent.copy(alpha = 0.85f)

    Box(
        modifier = modifier
            .size(size)
            .clip(NutraTheme.shapes.pill)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggleDisplayMode()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(strokeWidth / 2)) {
            val startAngle = 135f
            val totalSweep = 270f
            val arcSize = Size(this.size.width, this.size.height)

            // Background Track
            drawArc(
                color = trackColor,
                startAngle = startAngle,
                sweepAngle = totalSweep,
                useCenter = false,
                topLeft = Offset.Zero,
                size = arcSize,
                style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round),
            )

            // Primary Progress Arc (0..100%)
            val mainSweep = (animatedProgress.coerceIn(0f, 1f)) * totalSweep
            if (mainSweep > 0.5f) {
                drawArc(
                    color = ringColor,
                    startAngle = startAngle,
                    sweepAngle = mainSweep,
                    useCenter = false,
                    topLeft = Offset.Zero,
                    size = arcSize,
                    style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round),
                )
            }

            // Gentle Over-budget Segment (100%..125%)
            if (animatedProgress > 1f) {
                val extraFraction = (animatedProgress - 1f).coerceIn(0f, 0.25f)
                val extraSweep = extraFraction * totalSweep
                drawArc(
                    color = overFlowColor,
                    startAngle = startAngle + totalSweep - extraSweep,
                    sweepAngle = extraSweep,
                    useCenter = false,
                    topLeft = Offset.Zero,
                    size = arcSize,
                    style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round),
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 14.dp),
        ) {
            val mainValueText = when (displayMode) {
                CalorieDisplayMode.REMAINING -> {
                    if (isOverBudget) "+$overBudgetAmount" else "$remainingCalories"
                }
                CalorieDisplayMode.CONSUMED -> "$consumedCalories"
                CalorieDisplayMode.PERCENTAGE -> "${(rawRatio * 100).roundToInt()}%"
            }

            val subLabelText = when (displayMode) {
                CalorieDisplayMode.REMAINING -> {
                    if (isOverBudget) "kcal hedef üstü" else "kcal kalan"
                }
                CalorieDisplayMode.CONSUMED -> "/ $safeTarget kcal"
                CalorieDisplayMode.PERCENTAGE -> "günlük hedef"
            }

            Text(
                text = mainValueText,
                style = NutraTheme.typography.statLargeNumber,
                color = if (isOverBudget && displayMode == CalorieDisplayMode.REMAINING) {
                    NutraTheme.colors.overBudgetAccent
                } else {
                    NutraTheme.colors.textPrimary
                },
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subLabelText,
                style = NutraTheme.typography.macroLabel,
                color = NutraTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun NutraMacroProgressBar(
    label: String,
    consumedGrams: Float,
    targetGrams: Int,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val safeTarget = targetGrams.coerceAtLeast(1)
    val fraction = (consumedGrams / safeTarget.toFloat()).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = spring(
            dampingRatio = 0.85f,
            stiffness = 380f,
        ),
        label = "MacroBar_$label",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(NutraTheme.shapes.pill)
                        .background(accentColor),
                )
                Text(
                    text = label,
                    style = NutraTheme.typography.macroLabel,
                    color = NutraTheme.colors.textSecondary,
                )
            }
            Text(
                text = "${consumedGrams.roundToInt()} / ${safeTarget}g",
                style = NutraTheme.typography.macroValue,
                color = NutraTheme.colors.textPrimary,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(NutraTheme.shapes.pill)
                .background(NutraTheme.colors.elevatedSurface),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedFraction.coerceAtLeast(0.02f))
                    .fillMaxHeight()
                    .clip(NutraTheme.shapes.pill)
                    .background(accentColor),
            )
        }
    }
}

@Composable
fun NutraChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = NutraTheme.colors.calorieAccent,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    val bgColor by animateColorAsState(
        targetValue = if (selected) {
            accentColor.copy(alpha = if (NutraTheme.colors.isDark) 0.22f else 0.14f)
        } else {
            NutraTheme.colors.elevatedSurface
        },
        label = "ChipBg",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) {
            accentColor.copy(alpha = 0.65f)
        } else {
            NutraTheme.colors.hairlineBorder
        },
        label = "ChipBorder",
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) accentColor else NutraTheme.colors.textSecondary,
        label = "ChipText",
    )

    Row(
        modifier = modifier
            .height(36.dp)
            .clip(NutraTheme.shapes.chip)
            .background(bgColor)
            .border(1.dp, borderColor, NutraTheme.shapes.chip)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (leadingIcon != null) {
            leadingIcon()
        }
        Text(
            text = text,
            style = NutraTheme.typography.macroValue,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun <T> NutraSegmentedControl(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    labelProvider: (T) -> String,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(NutraTheme.colors.elevatedSurface)
            .border(1.dp, NutraTheme.colors.hairlineBorder, RoundedCornerShape(13.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEach { item ->
            val isSelected = item == selectedItem
            val bgColor by animateColorAsState(
                targetValue = if (isSelected) NutraTheme.colors.cardSurface else Color.Transparent,
                label = "SegmentBg",
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) NutraTheme.colors.textPrimary else NutraTheme.colors.textSecondary,
                label = "SegmentText",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(NutraTheme.shapes.chip)
                    .background(bgColor)
                    .then(
                        if (isSelected) {
                            Modifier.border(1.dp, NutraTheme.colors.hairlineBorder, NutraTheme.shapes.chip)
                        } else {
                            Modifier
                        },
                    )
                    .clickable {
                        if (!isSelected) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onItemSelected(item)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = labelProvider(item),
                    style = NutraTheme.typography.macroValue,
                    color = textColor,
                    maxLines = 1,
                )
            }
        }
    }
}
