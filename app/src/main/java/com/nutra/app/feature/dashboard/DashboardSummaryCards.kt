package com.nutra.app.feature.dashboard

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.nutra.app.core.designsystem.NutraCard
import com.nutra.app.core.designsystem.NutraDivider
import com.nutra.app.core.designsystem.NutraFormatters
import com.nutra.app.core.designsystem.NutraLinearProgress
import com.nutra.app.core.designsystem.NutraMacroStat
import com.nutra.app.core.designsystem.NutraTheme
import kotlin.math.roundToInt

@Composable
internal fun HeroNutritionCard(
    consumedCalories: Int,
    targetCalories: Int,
    consumedProtein: Float,
    targetProtein: Int,
    consumedCarbs: Float,
    targetCarbs: Int,
    consumedFat: Float,
    targetFat: Int,
) {
    val safeTarget = targetCalories.coerceAtLeast(1)
    val isOver = consumedCalories > safeTarget
    val deltaCalories = if (isOver) consumedCalories - safeTarget else safeTarget - consumedCalories
    val actualCompletionPercent = ((consumedCalories.toFloat() / safeTarget) * 100f).roundToInt()
    val progress = consumedCalories.toFloat() / safeTarget

    NutraCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(NutraTheme.spacing.card),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Günlük kalori",
                    style = NutraTheme.typography.caption,
                    color = NutraTheme.colors.textSecondary,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AnimatedHeroCalorieText(
                        consumedCalories = consumedCalories,
                        modifier = Modifier.alignByBaseline(),
                    )
                    Text(
                        text = "/ ${NutraFormatters.formatInt(safeTarget)} kcal",
                        style = NutraTheme.typography.body,
                        color = NutraTheme.colors.textSecondary,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
            }

            NutraLinearProgress(
                progress = progress,
                color = NutraTheme.colors.accent,
                height = 8.dp,
                overflowProgress = if (isOver) (progress - 1f).coerceAtLeast(0.05f) else 0f,
                overflowColor = NutraTheme.colors.warning,
                contentDescription = "Günlük kalori $consumedCalories / $safeTarget kcal, %$actualCompletionPercent",
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AnimatedHeroDeltaText(
                        deltaCalories = deltaCalories,
                        isOver = isOver,
                    )
                    Text(
                        text = if (isOver) "Hedef fazlası" else "Kalan",
                        style = NutraTheme.typography.caption,
                        color = NutraTheme.colors.textTertiary,
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(26.dp)
                        .background(NutraTheme.colors.divider),
                )

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AnimatedHeroPercentText(
                        consumedCalories = consumedCalories,
                        safeTarget = safeTarget,
                    )
                    Text(
                        text = "Tamamlandı",
                        style = NutraTheme.typography.caption,
                        color = NutraTheme.colors.textTertiary,
                    )
                }
            }
        }

        NutraDivider()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            NutraMacroStat(
                label = "Protein",
                consumed = consumedProtein,
                target = targetProtein,
                color = NutraTheme.colors.protein,
                modifier = Modifier.weight(1f),
            )
            NutraMacroStat(
                label = "Karb.",
                consumed = consumedCarbs,
                target = targetCarbs,
                color = NutraTheme.colors.carbs,
                modifier = Modifier.weight(1f),
            )
            NutraMacroStat(
                label = "Yağ",
                consumed = consumedFat,
                target = targetFat,
                color = NutraTheme.colors.fat,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AnimatedHeroCalorieText(
    consumedCalories: Int,
    modifier: Modifier = Modifier,
) {
    val animatedConsumed by animateIntAsState(
        targetValue = consumedCalories,
        animationSpec = tween(240),
        label = "heroCalories",
    )
    Text(
        text = NutraFormatters.formatInt(animatedConsumed),
        style = NutraTheme.typography.numberHero,
        color = NutraTheme.colors.textPrimary,
        modifier = modifier,
    )
}

@Composable
private fun AnimatedHeroDeltaText(
    deltaCalories: Int,
    isOver: Boolean,
) {
    val animatedDelta by animateIntAsState(
        targetValue = deltaCalories,
        animationSpec = tween(240),
        label = "heroDelta",
    )
    Text(
        text = "${NutraFormatters.formatInt(animatedDelta)} kcal",
        style = NutraTheme.typography.numberSmall,
        color = if (isOver) NutraTheme.colors.warningText else NutraTheme.colors.textPrimary,
    )
}

@Composable
private fun AnimatedHeroPercentText(
    consumedCalories: Int,
    safeTarget: Int,
) {
    val animatedConsumed by animateIntAsState(
        targetValue = consumedCalories,
        animationSpec = tween(240),
        label = "heroPercent",
    )
    val completionPercent = ((animatedConsumed.toFloat() / safeTarget) * 100f).roundToInt()
    Text(
        text = "%$completionPercent",
        style = NutraTheme.typography.numberSmall,
        color = NutraTheme.colors.textPrimary,
    )
}
