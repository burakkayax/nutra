package com.nutra.app.feature.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nutra.app.core.designsystem.NutraCard
import com.nutra.app.core.designsystem.NutraFormatters
import com.nutra.app.core.designsystem.NutraIconButton
import com.nutra.app.core.designsystem.NutraIcons
import com.nutra.app.core.designsystem.NutraLinearProgress
import com.nutra.app.core.designsystem.NutraTheme
import com.nutra.app.core.designsystem.isCompactWidthOrLargeFont
import kotlin.math.roundToInt

private val WaterPillPressSpring = spring<Float>(dampingRatio = 0.78f, stiffness = 700f)

@Composable
internal fun WaterTrackerCard(
    currentWaterMl: Int,
    targetWaterMl: Int,
    onAddWater: (Int) -> Unit,
) {
    val safeTarget = targetWaterMl.coerceAtLeast(1)
    val fraction = (currentWaterMl.toFloat() / safeTarget.toFloat()).coerceIn(0f, 1f)
    val targetPercent = ((currentWaterMl.toFloat() / safeTarget.toFloat()) * 100f).roundToInt()
    val isGoalReached = currentWaterMl >= safeTarget

    NutraCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(NutraTheme.spacing.card),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Su takibi",
                    style = NutraTheme.typography.titleSmall,
                    color = NutraTheme.colors.textPrimary,
                )
                AnimatedWaterPercentText(
                    currentWaterMl = currentWaterMl,
                    safeTarget = safeTarget,
                    isGoalReached = isGoalReached,
                )
            }

            Row {
                AnimatedWaterAmountText(
                    currentWaterMl = currentWaterMl,
                    modifier = Modifier.alignByBaseline(),
                )
                Text(
                    text = " / ${NutraFormatters.formatInt(safeTarget)} ml",
                    style = NutraTheme.typography.caption,
                    color = NutraTheme.colors.textTertiary,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }

        NutraLinearProgress(
            progress = fraction,
            color = NutraTheme.colors.water,
            height = 6.dp,
            contentDescription = "Su takibi $currentWaterMl / $safeTarget ml, %$targetPercent",
        )

        val useGridWaterPills = isCompactWidthOrLargeFont()
        if (useGridWaterPills) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    WaterQuickPill(
                        amountText = "+200 ml",
                        onClick = { onAddWater(200) },
                        modifier = Modifier.weight(1f),
                    )
                    WaterQuickPill(
                        amountText = "+330 ml",
                        onClick = { onAddWater(330) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    WaterQuickPill(
                        amountText = "+500 ml",
                        onClick = { onAddWater(500) },
                        modifier = Modifier.weight(1f),
                    )
                    WaterQuickPill(
                        amountText = "-200 ml",
                        onClick = { if (currentWaterMl > 0) onAddWater(-200) },
                        enabled = currentWaterMl > 0,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WaterQuickPill(
                    amountText = "+200 ml",
                    onClick = { onAddWater(200) },
                    modifier = Modifier.weight(1f),
                )
                WaterQuickPill(
                    amountText = "+330 ml",
                    onClick = { onAddWater(330) },
                    modifier = Modifier.weight(1f),
                )
                WaterQuickPill(
                    amountText = "+500 ml",
                    onClick = { onAddWater(500) },
                    modifier = Modifier.weight(1f),
                )
                NutraIconButton(
                    icon = NutraIcons.Remove,
                    contentDescription = "200 ml azalt",
                    onClick = { onAddWater(-200) },
                    size = 48.dp,
                    enabled = currentWaterMl > 0,
                )
            }
        }
    }
}

@Composable
private fun AnimatedWaterAmountText(
    currentWaterMl: Int,
    modifier: Modifier = Modifier,
) {
    val animatedWater by animateIntAsState(
        targetValue = currentWaterMl,
        animationSpec = tween(220),
        label = "waterMl",
    )
    Text(
        text = NutraFormatters.formatInt(animatedWater),
        style = NutraTheme.typography.numberMedium,
        color = NutraTheme.colors.textPrimary,
        modifier = modifier,
    )
}

@Composable
private fun AnimatedWaterPercentText(
    currentWaterMl: Int,
    safeTarget: Int,
    isGoalReached: Boolean,
) {
    val animatedWater by animateIntAsState(
        targetValue = currentWaterMl,
        animationSpec = tween(220),
        label = "waterPercent",
    )
    val actualPercent = ((animatedWater.toFloat() / safeTarget.toFloat()) * 100f).roundToInt()
    Text(
        text = "%$actualPercent tamamlandı",
        style = NutraTheme.typography.caption,
        color = if (isGoalReached) NutraTheme.colors.onAccentSoft else NutraTheme.colors.textTertiary,
    )
}

@Composable
private fun WaterQuickPill(
    amountText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScaleState = animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.95f else 1f,
        animationSpec = WaterPillPressSpring,
        label = "waterPillScale",
    )
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .graphicsLayer {
                alpha = if (enabled) 1f else 0.4f
                val s = pressScaleState.value
                scaleX = s
                scaleY = s
            }
            .clip(NutraTheme.shapes.pill)
            .background(NutraTheme.colors.surfaceMuted)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = amountText,
            style = NutraTheme.typography.labelSmall,
            color = NutraTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
