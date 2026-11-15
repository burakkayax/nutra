package com.nutra.app.feature.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nutra.app.core.designsystem.NutraCard
import com.nutra.app.core.designsystem.NutraSegmentedControl
import com.nutra.app.core.designsystem.NutraTheme
import com.nutra.app.domain.model.ActivityLevel
import com.nutra.app.domain.model.BiologicalSex
import com.nutra.app.domain.model.GoalType
import kotlin.math.roundToInt

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onOnboardingFinished: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NutraTheme.colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = NutraTheme.spacing.screenHorizontal),
    ) {
        // Top Header & Progress Indicator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.stepIndex > 0) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.previousStep()
                        },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Geri",
                            tint = NutraTheme.colors.textPrimary,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(3) { idx ->
                        val active = idx <= state.stepIndex
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .size(width = if (idx == state.stepIndex) 28.dp else 12.dp, height = 6.dp)
                                .clip(NutraTheme.shapes.pill)
                                .background(
                                    if (active) NutraTheme.colors.calorieAccent else NutraTheme.colors.elevatedSurface,
                                ),
                        )
                    }
                }
            }

            TextButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    viewModel.completeOnboarding(onOnboardingFinished)
                },
            ) {
                Text(
                    text = "Hızlı Başla",
                    style = NutraTheme.typography.macroValue,
                    color = NutraTheme.colors.textSecondary,
                )
            }
        }

        AnimatedContent(
            targetState = state.stepIndex,
            modifier = Modifier.weight(1f),
            label = "OnboardingStepTransition",
        ) { step ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (step) {
                    0 -> StepGoalSelection(
                        selectedGoal = state.goalType,
                        onSelectGoal = viewModel::selectGoalType,
                    )
                    1 -> StepBodyMetricsAndActivity(
                        state = state,
                        onSelectSex = viewModel::selectSex,
                        onAdjustAge = viewModel::adjustAge,
                        onAdjustHeight = viewModel::adjustHeight,
                        onAdjustWeight = viewModel::adjustCurrentWeight,
                        onAdjustTargetWeight = viewModel::adjustTargetWeight,
                        onSelectActivity = viewModel::selectActivityLevel,
                    )
                    else -> StepCalculatedPlanReview(
                        state = state,
                        onAdjustCalories = viewModel::adjustCustomCalories,
                        onAdjustProtein = viewModel::adjustCustomProtein,
                        onAdjustCarbs = viewModel::adjustCustomCarbs,
                        onAdjustFat = viewModel::adjustCustomFat,
                        onAdjustWater = viewModel::adjustCustomWater,
                        onResetFormula = viewModel::resetToMifflinFormula,
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        // Bottom Primary CTA
        Button(
            onClick = {
                if (state.stepIndex < 2) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.nextStep()
                } else {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    viewModel.completeOnboarding(onOnboardingFinished)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .height(54.dp),
            shape = NutraTheme.shapes.innerSubCard,
            colors = ButtonDefaults.buttonColors(
                containerColor = NutraTheme.colors.calorieAccent,
                contentColor = Color.White,
            ),
        ) {
            Text(
                text = if (state.stepIndex < 2) "Devam Et" else "Planımı Onayla ve Başla",
                style = NutraTheme.typography.cardTitle,
                color = Color.White,
            )
            Spacer(modifier = Modifier.size(8.dp))
            Icon(
                imageVector = if (state.stepIndex < 2) {
                    Icons.AutoMirrored.Rounded.ArrowForward
                } else {
                    Icons.Rounded.Check
                },
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun StepGoalSelection(
    selectedGoal: GoalType,
    onSelectGoal: (GoalType) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Text(
        text = "Temel Hedefiniz Nedir?",
        style = NutraTheme.typography.screenHeader,
        color = NutraTheme.colors.textPrimary,
    )
    Text(
        text = "Mifflin-St Jeor metabolizma denklemine göre günlük enerji ve makro dengenizi optimize edelim.",
        style = NutraTheme.typography.bodyRegular,
        color = NutraTheme.colors.textSecondary,
    )
    Spacer(modifier = Modifier.height(6.dp))

    GoalType.entries.forEach { goal ->
        val isSelected = goal == selectedGoal
        NutraCard(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSelectGoal(goal)
            },
            borderColor = if (isSelected) {
                NutraTheme.colors.calorieAccent
            } else {
                NutraTheme.colors.hairlineBorder
            },
            backgroundColor = if (isSelected) {
                NutraTheme.colors.calorieAccent.copy(alpha = if (NutraTheme.colors.isDark) 0.14f else 0.08f)
            } else {
                NutraTheme.colors.cardSurface
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = goal.title,
                        style = NutraTheme.typography.cardTitle,
                        color = NutraTheme.colors.textPrimary,
                    )
                    val badgeText = when {
                        goal.calorieDelta < 0 -> "${goal.calorieDelta} kcal"
                        goal.calorieDelta > 0 -> "+${goal.calorieDelta} kcal"
                        else -> "Denge"
                    }
                    Box(
                        modifier = Modifier
                            .clip(NutraTheme.shapes.chip)
                            .background(NutraTheme.colors.elevatedSurface)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = badgeText,
                            style = NutraTheme.typography.badgeLabel,
                            color = if (isSelected) NutraTheme.colors.calorieAccent else NutraTheme.colors.textSecondary,
                        )
                    }
                }
                Text(
                    text = goal.subtitle,
                    style = NutraTheme.typography.bodyRegular,
                    color = NutraTheme.colors.textSecondary,
                )
                Text(
                    text = "Makro Oranı: %${(goal.proteinRatio * 100).roundToInt()} Protein · %${(goal.carbsRatio * 100).roundToInt()} Karb · %${(goal.fatRatio * 100).roundToInt()} Yağ",
                    style = NutraTheme.typography.macroLabel,
                    color = NutraTheme.colors.textMuted,
                )
            }
        }
    }
}

@Composable
private fun StepBodyMetricsAndActivity(
    state: OnboardingUiState,
    onSelectSex: (BiologicalSex) -> Unit,
    onAdjustAge: (Int) -> Unit,
    onAdjustHeight: (Int) -> Unit,
    onAdjustWeight: (Float) -> Unit,
    onAdjustTargetWeight: (Float) -> Unit,
    onSelectActivity: (ActivityLevel) -> Unit,
) {
    Text(
        text = "Vücut & Aktivite Profiliniz",
        style = NutraTheme.typography.screenHeader,
        color = NutraTheme.colors.textPrimary,
    )
    Text(
        text = "Tüm hesaplamalar tamamen cihazınızda çevrimdışı yapılır.",
        style = NutraTheme.typography.bodyRegular,
        color = NutraTheme.colors.textSecondary,
    )

    NutraSegmentedControl(
        items = BiologicalSex.entries,
        selectedItem = state.sex,
        onItemSelected = onSelectSex,
        labelProvider = { it.title },
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MetricStepperCard(
            title = "Yaş",
            valueText = "${state.age}",
            unit = "yıl",
            onDecrement = { onAdjustAge(-1) },
            onIncrement = { onAdjustAge(1) },
            modifier = Modifier.weight(1f),
        )
        MetricStepperCard(
            title = "Boy",
            valueText = "${state.heightCm}",
            unit = "cm",
            onDecrement = { onAdjustHeight(-1) },
            onIncrement = { onAdjustHeight(1) },
            modifier = Modifier.weight(1f),
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MetricStepperCard(
            title = "Mevcut Kilo",
            valueText = String.format(java.util.Locale.US, "%.1f", state.currentWeightKg),
            unit = "kg",
            onDecrement = { onAdjustWeight(-0.5f) },
            onIncrement = { onAdjustWeight(0.5f) },
            modifier = Modifier.weight(1f),
        )
        MetricStepperCard(
            title = "Hedef Kilo",
            valueText = String.format(java.util.Locale.US, "%.1f", state.targetWeightKg),
            unit = "kg",
            onDecrement = { onAdjustTargetWeight(-0.5f) },
            onIncrement = { onAdjustTargetWeight(0.5f) },
            modifier = Modifier.weight(1f),
        )
    }

    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "Günlük Hareket Düzeyi",
        style = NutraTheme.typography.sectionTitle,
        color = NutraTheme.colors.textPrimary,
    )

    ActivityLevel.entries.forEach { level ->
        val isSelected = level == state.activityLevel
        NutraCard(
            onClick = { onSelectActivity(level) },
            borderColor = if (isSelected) NutraTheme.colors.calorieAccent else NutraTheme.colors.hairlineBorder,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = level.title,
                        style = NutraTheme.typography.foodItemTitle,
                        color = NutraTheme.colors.textPrimary,
                    )
                    Text(
                        text = level.subtitle,
                        style = NutraTheme.typography.bodySmall,
                        color = NutraTheme.colors.textSecondary,
                    )
                }
                Text(
                    text = "x${level.multiplier}",
                    style = NutraTheme.typography.macroValue,
                    color = if (isSelected) NutraTheme.colors.calorieAccent else NutraTheme.colors.textMuted,
                )
            }
        }
    }
}

@Composable
private fun StepCalculatedPlanReview(
    state: OnboardingUiState,
    onAdjustCalories: (Int) -> Unit,
    onAdjustProtein: (Int) -> Unit,
    onAdjustCarbs: (Int) -> Unit,
    onAdjustFat: (Int) -> Unit,
    onAdjustWater: (Int) -> Unit,
    onResetFormula: () -> Unit,
) {
    val goals = state.customGoals
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Kişisel Beslenme Planınız",
            style = NutraTheme.typography.screenHeader,
            color = NutraTheme.colors.textPrimary,
        )
        if (state.isCustomized) {
            TextButton(onClick = onResetFormula) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = NutraTheme.colors.calorieAccent,
                )
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = "Formüle Dön",
                    style = NutraTheme.typography.macroLabel,
                    color = NutraTheme.colors.calorieAccent,
                )
            }
        }
    }
    Text(
        text = "Mifflin-St Jeor formülüyle hesaplanan günlük hedefleriniz aşağıdadır. Dilerseniz '+' ve '-' ile hemen özelleştirebilirsiniz.",
        style = NutraTheme.typography.bodyRegular,
        color = NutraTheme.colors.textSecondary,
    )

    NutraCard(
        shape = NutraTheme.shapes.heroCard,
        contentPadding = PaddingValues(NutraTheme.spacing.heroCardPadding),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            AdjustableGoalRow(
                label = "Günlük Kalori Hedefi",
                valueText = "${goals.calories} kcal",
                accentColor = NutraTheme.colors.calorieAccent,
                onDecrement = { onAdjustCalories(-50) },
                onIncrement = { onAdjustCalories(50) },
            )
            AdjustableGoalRow(
                label = "Protein",
                valueText = "${goals.proteinGrams} g",
                accentColor = NutraTheme.colors.proteinAccent,
                onDecrement = { onAdjustProtein(-5) },
                onIncrement = { onAdjustProtein(5) },
            )
            AdjustableGoalRow(
                label = "Karbonhidrat",
                valueText = "${goals.carbsGrams} g",
                accentColor = NutraTheme.colors.carbsAccent,
                onDecrement = { onAdjustCarbs(-5) },
                onIncrement = { onAdjustCarbs(5) },
            )
            AdjustableGoalRow(
                label = "Yağ",
                valueText = "${goals.fatGrams} g",
                accentColor = NutraTheme.colors.fatAccent,
                onDecrement = { onAdjustFat(-5) },
                onIncrement = { onAdjustFat(5) },
            )
            AdjustableGoalRow(
                label = "Günlük Su Hedefi",
                valueText = "${goals.waterMl} ml",
                accentColor = NutraTheme.colors.waterAccent,
                onDecrement = { onAdjustWater(-100) },
                onIncrement = { onAdjustWater(100) },
            )
        }
    }
}

@Composable
private fun MetricStepperCard(
    title: String,
    valueText: String,
    unit: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    NutraCard(
        modifier = modifier,
        contentPadding = PaddingValues(14.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = NutraTheme.typography.macroLabel,
                color = NutraTheme.colors.textSecondary,
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = valueText,
                    style = NutraTheme.typography.statLargeNumber,
                    color = NutraTheme.colors.textPrimary,
                )
                Text(
                    text = unit,
                    style = NutraTheme.typography.macroLabel,
                    color = NutraTheme.colors.textMuted,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StepperButton(
                    icon = Icons.Rounded.Remove,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDecrement()
                    },
                )
                StepperButton(
                    icon = Icons.Rounded.Add,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onIncrement()
                    },
                )
            }
        }
    }
}

@Composable
private fun AdjustableGoalRow(
    label: String,
    valueText: String,
    accentColor: Color,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(NutraTheme.shapes.pill)
                    .background(accentColor),
            )
            Text(
                text = label,
                style = NutraTheme.typography.foodItemTitle,
                color = NutraTheme.colors.textPrimary,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StepperButton(
                icon = Icons.Rounded.Remove,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onDecrement()
                },
            )
            Text(
                text = valueText,
                style = NutraTheme.typography.cardTitle,
                color = NutraTheme.colors.textPrimary,
            )
            StepperButton(
                icon = Icons.Rounded.Add,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onIncrement()
                },
            )
        }
    }
}

@Composable
private fun StepperButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(NutraTheme.shapes.chip)
            .background(NutraTheme.colors.elevatedSurface)
            .border(1.dp, NutraTheme.colors.hairlineBorder, NutraTheme.shapes.chip)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = NutraTheme.colors.textPrimary,
            modifier = Modifier.size(16.dp),
        )
    }
}
