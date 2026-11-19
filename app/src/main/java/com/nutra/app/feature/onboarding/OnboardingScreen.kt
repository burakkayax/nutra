package com.nutra.app.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nutra.app.core.designsystem.NutraButton
import com.nutra.app.core.designsystem.NutraButtonSize
import com.nutra.app.core.designsystem.NutraButtonStyle
import com.nutra.app.core.designsystem.NutraCard
import com.nutra.app.core.designsystem.NutraContentMaxWidth
import com.nutra.app.core.designsystem.NutraDialog
import com.nutra.app.core.designsystem.NutraDivider
import com.nutra.app.core.designsystem.NutraDot
import com.nutra.app.core.designsystem.NutraFormatters
import com.nutra.app.core.designsystem.NutraIconButton
import com.nutra.app.core.designsystem.NutraIcons
import com.nutra.app.core.designsystem.NutraMetricStepperRow
import com.nutra.app.core.designsystem.NutraNumericInputDialog
import com.nutra.app.core.designsystem.NutraSectionHeader
import com.nutra.app.core.designsystem.NutraSegmentedControl
import com.nutra.app.core.designsystem.NutraSelectableCard
import com.nutra.app.core.designsystem.NutraStackedRatioBar
import com.nutra.app.core.designsystem.NutraStepperButton
import com.nutra.app.core.designsystem.NutraTheme
import com.nutra.app.domain.model.ActivityLevel
import com.nutra.app.domain.model.BiologicalSex
import com.nutra.app.domain.model.GoalType
import com.nutra.app.domain.usecase.BodyMetricBounds
import kotlin.math.roundToInt

private enum class OnboardingNumericField(
    val dialogTitle: String,
    val unit: String,
    val rangeHint: String,
    val minValue: Double,
    val maxValue: Double,
    val allowDecimal: Boolean,
) {
    AGE("Yaş", "yaş", "14 – 95 yaş", BodyMetricBounds.MIN_AGE.toDouble(), BodyMetricBounds.MAX_AGE.toDouble(), false),
    HEIGHT("Boy", "cm", "130 – 225 cm", BodyMetricBounds.MIN_HEIGHT_CM.toDouble(), BodyMetricBounds.MAX_HEIGHT_CM.toDouble(), false),
    CURRENT_WEIGHT("Mevcut kilo", "kg", "35,0 – 250,0 kg", BodyMetricBounds.MIN_WEIGHT_KG.toDouble(), BodyMetricBounds.MAX_WEIGHT_KG.toDouble(), true),
    TARGET_WEIGHT("Hedef kilo", "kg", "35,0 – 250,0 kg", BodyMetricBounds.MIN_WEIGHT_KG.toDouble(), BodyMetricBounds.MAX_WEIGHT_KG.toDouble(), true),
    CALORIES("Günlük kalori hedefi", "kcal", "1.000 – 6.000 kcal", 1000.0, 6000.0, false),
    PROTEIN("Protein hedefi", "g", "30 – 400 g", 30.0, 400.0, false),
    CARBS("Karbonhidrat hedefi", "g", "20 – 600 g", 20.0, 600.0, false),
    FAT("Yağ hedefi", "g", "20 – 250 g", 20.0, 250.0, false),
    WATER("Su hedefi", "ml", "1.000 – 7.000 ml", 1000.0, 7000.0, false),
}

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onOnboardingFinished: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = NutraTheme.colors
    var activeField by remember { mutableStateOf<OnboardingNumericField?>(null) }
    var showSkipDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = state.stepIndex > 0) {
        viewModel.previousStep()
    }

    val rootInsets = WindowInsets.statusBars
        .union(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
        .union(WindowInsets.navigationBars)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(rootInsets),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = NutraContentMaxWidth)
                .fillMaxSize(),
        ) {
            // Minimal top navigation + 3-segment step bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NutraTheme.spacing.screen)
                    .padding(top = NutraTheme.spacing.section, bottom = NutraTheme.spacing.sm),
                verticalArrangement = Arrangement.spacedBy(NutraTheme.spacing.lg),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 42.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state.stepIndex > 0) {
                        NutraIconButton(
                            icon = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Geri",
                            onClick = viewModel::previousStep,
                            size = 42.dp,
                            iconSize = 20.dp,
                        )
                    } else {
                        Box(
                            modifier = Modifier.heightIn(min = 42.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                text = "Nutra",
                                style = NutraTheme.typography.titleMedium,
                                color = colors.textPrimary,
                            )
                        }
                    }

                    if (state.stepIndex < 2) {
                        NutraButton(
                            text = "Atla",
                            onClick = { showSkipDialog = true },
                            style = NutraButtonStyle.Secondary,
                            size = NutraButtonSize.Small,
                            enabled = !state.isCompleting,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(NutraTheme.spacing.sm),
                ) {
                    repeat(3) { idx ->
                        val filled = idx <= state.stepIndex
                        val barColorState = animateColorAsState(
                            targetValue = if (filled) colors.ink else colors.surfaceMuted,
                            animationSpec = tween(220),
                            label = "stepBar$idx",
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(NutraTheme.shapes.pill)
                                .drawBehind { drawRect(barColorState.value) },
                        )
                    }
                }
            }

            // Step content
            AnimatedContent(
                targetState = state.stepIndex,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    val forward = targetState >= initialState
                    (slideInHorizontally(tween(240)) { if (forward) it / 4 else -it / 4 } +
                        fadeIn(tween(200))) togetherWith
                        (slideOutHorizontally(tween(200)) { if (forward) -it / 6 else it / 6 } +
                            fadeOut(tween(150)))
                },
                label = "onboardingStep",
            ) { step ->
                when (step) {
                    0 -> GoalSelectionStep(
                        selectedGoal = state.goalType,
                        onSelectGoal = viewModel::selectGoalType,
                    )

                    1 -> BodyMetricsStep(
                        state = state,
                        onSelectSex = viewModel::selectSex,
                        onAdjustAge = viewModel::adjustAge,
                        onAdjustHeight = viewModel::adjustHeight,
                        onAdjustCurrentWeight = viewModel::adjustCurrentWeight,
                        onAdjustTargetWeight = viewModel::adjustTargetWeight,
                        onSelectActivity = viewModel::selectActivityLevel,
                        onEditField = { activeField = it },
                    )

                    else -> DailyPlanReviewStep(
                        state = state,
                        onAdjustCalories = viewModel::adjustCustomCalories,
                        onAdjustProtein = viewModel::adjustCustomProtein,
                        onAdjustCarbs = viewModel::adjustCustomCarbs,
                        onAdjustFat = viewModel::adjustCustomFat,
                        onAdjustWater = viewModel::adjustCustomWater,
                        onReset = viewModel::resetToMifflinFormula,
                        onEditField = { activeField = it },
                    )
                }
            }

            // Bottom CTA
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.background)
                    .padding(horizontal = NutraTheme.spacing.screen)
                    .padding(top = NutraTheme.spacing.md, bottom = NutraTheme.spacing.lg),
            ) {
                val isLast = state.stepIndex == 2
                NutraButton(
                    text = if (isLast) "Başlayalım" else "Devam",
                    onClick = {
                        if (isLast) {
                            viewModel.completeOnboarding(onOnboardingFinished)
                        } else {
                            viewModel.nextStep()
                        }
                    },
                    style = NutraButtonStyle.Primary,
                    size = NutraButtonSize.Large,
                    trailingIcon = if (isLast) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.ArrowForward,
                    loading = isLast && state.isCompleting,
                    enabled = !state.isCompleting,
                    hapticFeedback = if (isLast) HapticFeedbackType.Confirm else HapticFeedbackType.TextHandleMove,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (showSkipDialog) {
        NutraDialog(
            onDismissRequest = { showSkipDialog = false },
            title = "Kurulumu atla?",
            message = "Varsayılan günlük hedeflerle (${NutraFormatters.formatInt(state.customGoals.calories)} kcal) hemen başlayabilir, ölçülerini daha sonra Profil sekmesinden güncelleyebilirsin.",
            confirmText = "Hemen başla",
            onConfirm = {
                showSkipDialog = false
                viewModel.completeOnboarding(onOnboardingFinished)
            },
            dismissText = "Devam et",
            onDismiss = { showSkipDialog = false },
        )
    }

    val editingField = activeField
    if (editingField != null) {
        val currentValue = when (editingField) {
            OnboardingNumericField.AGE -> state.age.toString()
            OnboardingNumericField.HEIGHT -> state.heightCm.toString()
            OnboardingNumericField.CURRENT_WEIGHT -> NutraFormatters.formatKg(state.currentWeightKg)
            OnboardingNumericField.TARGET_WEIGHT -> NutraFormatters.formatKg(state.targetWeightKg)
            OnboardingNumericField.CALORIES -> state.customGoals.calories.toString()
            OnboardingNumericField.PROTEIN -> state.customGoals.proteinGrams.toString()
            OnboardingNumericField.CARBS -> state.customGoals.carbsGrams.toString()
            OnboardingNumericField.FAT -> state.customGoals.fatGrams.toString()
            OnboardingNumericField.WATER -> state.customGoals.waterMl.toString()
        }
        NutraNumericInputDialog(
            title = editingField.dialogTitle,
            initialValue = currentValue,
            unit = editingField.unit,
            rangeHint = editingField.rangeHint,
            minValue = editingField.minValue,
            maxValue = editingField.maxValue,
            allowDecimal = editingField.allowDecimal,
            onDismiss = { activeField = null },
            onConfirm = { entered ->
                when (editingField) {
                    OnboardingNumericField.AGE -> viewModel.setAge(entered.roundToInt())
                    OnboardingNumericField.HEIGHT -> viewModel.setHeight(entered.roundToInt())
                    OnboardingNumericField.CURRENT_WEIGHT -> viewModel.setCurrentWeight(entered.toFloat())
                    OnboardingNumericField.TARGET_WEIGHT -> viewModel.setTargetWeight(entered.toFloat())
                    OnboardingNumericField.CALORIES -> viewModel.setCustomCalories(entered.roundToInt())
                    OnboardingNumericField.PROTEIN -> viewModel.setCustomProtein(entered.roundToInt())
                    OnboardingNumericField.CARBS -> viewModel.setCustomCarbs(entered.roundToInt())
                    OnboardingNumericField.FAT -> viewModel.setCustomFat(entered.roundToInt())
                    OnboardingNumericField.WATER -> viewModel.setCustomWater(entered.roundToInt())
                }
                activeField = null
            },
        )
    }
}

@Composable
private fun StepHeadline(
    stepNumber: Int,
    title: String,
    subtitle: String,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = NutraTheme.colors
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Adım $stepNumber / 3",
                style = NutraTheme.typography.labelSmall,
                color = colors.accent,
            )
            trailing?.invoke()
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = title,
            style = NutraTheme.typography.titleLarge,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = NutraTheme.typography.bodyLarge,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun GoalSelectionStep(
    selectedGoal: GoalType,
    onSelectGoal: (GoalType) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = NutraTheme.spacing.screen)
            .padding(top = NutraTheme.spacing.sm, bottom = NutraTheme.spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(NutraTheme.spacing.xxl),
    ) {
        StepHeadline(
            stepNumber = 1,
            title = "Nasıl bir hedefle başlıyoruz?",
            subtitle = "Kalori ve makro hedeflerini buna göre hesaplayacağız.",
        )

        Column(verticalArrangement = Arrangement.spacedBy(NutraTheme.spacing.md)) {
            GoalType.entries.forEach { goal ->
                val icon = when (goal) {
                    GoalType.LOSE_WEIGHT -> NutraIcons.TrendingDown
                    GoalType.MAINTAIN -> NutraIcons.TrendingFlat
                    GoalType.GAIN_MUSCLE -> NutraIcons.TrendingUp
                }
                NutraSelectableCard(
                    title = goal.title,
                    subtitle = goal.subtitle,
                    icon = icon,
                    selected = goal == selectedGoal,
                    onClick = { onSelectGoal(goal) },
                )
            }
        }
    }
}

@Composable
private fun BodyMetricsStep(
    state: OnboardingUiState,
    onSelectSex: (BiologicalSex) -> Unit,
    onAdjustAge: (Int) -> Unit,
    onAdjustHeight: (Int) -> Unit,
    onAdjustCurrentWeight: (Float) -> Unit,
    onAdjustTargetWeight: (Float) -> Unit,
    onSelectActivity: (ActivityLevel) -> Unit,
    onEditField: (OnboardingNumericField) -> Unit,
) {
    val showTargetWeight = state.goalType != GoalType.MAINTAIN
    val targetWeightMismatchHint = when (state.goalType) {
        GoalType.LOSE_WEIGHT -> if (state.targetWeightKg >= state.currentWeightKg) {
            "Kilo verme hedefinde hedef kilon genellikle mevcut kilondan düşük olur."
        } else {
            null
        }
        GoalType.GAIN_MUSCLE -> if (state.targetWeightKg <= state.currentWeightKg) {
            "Hacim kazanımı hedefinde hedef kilon genellikle mevcut kilondan yüksek olur."
        } else {
            null
        }
        GoalType.MAINTAIN -> null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = NutraTheme.spacing.screen)
            .padding(top = NutraTheme.spacing.sm, bottom = NutraTheme.spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(NutraTheme.spacing.xxl),
    ) {
        StepHeadline(
            stepNumber = 2,
            title = "Seni biraz tanıyalım",
            subtitle = "Bireysel metabolizma hesabın için ölçülerini seç veya üzerine basarak yaz.",
        )

        Column(verticalArrangement = Arrangement.spacedBy(NutraTheme.spacing.md)) {
            NutraSectionHeader(title = "Cinsiyet")
            NutraSegmentedControl(
                items = BiologicalSex.entries,
                selectedItem = state.sex,
                onItemSelected = onSelectSex,
                labelProvider = { it.title },
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(NutraTheme.spacing.md)) {
            NutraSectionHeader(title = "Vücut ölçüleri")
            NutraCard(contentPadding = PaddingValues(horizontal = NutraTheme.spacing.card, vertical = NutraTheme.spacing.lg)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    NutraMetricStepperRow(
                        label = "Yaş",
                        valueText = "${state.age} yaş",
                        onDecrement = { onAdjustAge(-1) },
                        onIncrement = { onAdjustAge(1) },
                        onValueClick = { onEditField(OnboardingNumericField.AGE) },
                        valueMinWidth = 80.dp,
                    )
                    NutraDivider()
                    NutraMetricStepperRow(
                        label = "Boy",
                        valueText = "${state.heightCm} cm",
                        onDecrement = { onAdjustHeight(-1) },
                        onIncrement = { onAdjustHeight(1) },
                        onValueClick = { onEditField(OnboardingNumericField.HEIGHT) },
                        valueMinWidth = 80.dp,
                    )
                    NutraDivider()
                    NutraMetricStepperRow(
                        label = "Mevcut kilo",
                        valueText = "${NutraFormatters.formatKg(state.currentWeightKg)} kg",
                        onDecrement = { onAdjustCurrentWeight(-0.5f) },
                        onIncrement = { onAdjustCurrentWeight(0.5f) },
                        onValueClick = { onEditField(OnboardingNumericField.CURRENT_WEIGHT) },
                        valueMinWidth = 80.dp,
                    )
                    if (showTargetWeight) {
                        NutraDivider()
                        NutraMetricStepperRow(
                            label = "Hedef kilo",
                            valueText = "${NutraFormatters.formatKg(state.targetWeightKg)} kg",
                            onDecrement = { onAdjustTargetWeight(-0.5f) },
                            onIncrement = { onAdjustTargetWeight(0.5f) },
                            onValueClick = { onEditField(OnboardingNumericField.TARGET_WEIGHT) },
                            valueMinWidth = 80.dp,
                        )
                    }
                    if (targetWeightMismatchHint != null) {
                        Text(
                            text = targetWeightMismatchHint,
                            style = NutraTheme.typography.caption,
                            color = NutraTheme.colors.warningText,
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(NutraTheme.spacing.md)) {
            NutraSectionHeader(title = "Hareket düzeyi")
            Column(verticalArrangement = Arrangement.spacedBy(NutraTheme.spacing.md)) {
                ActivityLevel.entries.forEach { level ->
                    val icon = when (level) {
                        ActivityLevel.SEDENTARY -> NutraIcons.ActivityLevel1
                        ActivityLevel.LIGHT -> NutraIcons.ActivityLevel2
                        ActivityLevel.MODERATE -> NutraIcons.ActivityLevel3
                        ActivityLevel.ACTIVE -> NutraIcons.ActivityLevel4
                    }
                    NutraSelectableCard(
                        title = level.title,
                        subtitle = level.subtitle,
                        icon = icon,
                        selected = level == state.activityLevel,
                        onClick = { onSelectActivity(level) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyPlanReviewStep(
    state: OnboardingUiState,
    onAdjustCalories: (Int) -> Unit,
    onAdjustProtein: (Int) -> Unit,
    onAdjustCarbs: (Int) -> Unit,
    onAdjustFat: (Int) -> Unit,
    onAdjustWater: (Int) -> Unit,
    onReset: () -> Unit,
    onEditField: (OnboardingNumericField) -> Unit,
) {
    val colors = NutraTheme.colors
    val goals = state.customGoals
    val haptic = LocalHapticFeedback.current

    val proteinKcal = goals.proteinGrams * 4f
    val carbsKcal = goals.carbsGrams * 4f
    val fatKcal = goals.fatGrams * 9f
    val totalMacroKcal = (proteinKcal + carbsKcal + fatKcal).coerceAtLeast(1f)

    val pRatio = proteinKcal / totalMacroKcal
    val cRatio = carbsKcal / totalMacroKcal
    val fRatio = fatKcal / totalMacroKcal
    val pShare = (pRatio * 100f).roundToInt()
    val cShare = (cRatio * 100f).roundToInt()
    val fShare = (100 - pShare - cShare).coerceAtLeast(0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = NutraTheme.spacing.screen)
            .padding(top = NutraTheme.spacing.sm, bottom = NutraTheme.spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(NutraTheme.spacing.xxl),
    ) {
        StepHeadline(
            stepNumber = 3,
            title = "Günlük planın hazır",
            subtitle = "Mifflin-St Jeor hesaplamasına göre önerilen hedeflerini istersen özelleştir.",
            trailing = if (state.isCustomized) {
                {
                    NutraButton(
                        text = "Sıfırla",
                        onClick = onReset,
                        style = NutraButtonStyle.Secondary,
                        size = NutraButtonSize.Small,
                        leadingIcon = Icons.Rounded.Refresh,
                        hapticFeedback = HapticFeedbackType.Confirm,
                    )
                }
            } else {
                null
            },
        )

        // Hero calorie summary card
        NutraCard {
            Text(
                text = "Günlük kalori hedefi",
                style = NutraTheme.typography.label,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = NutraTheme.spacing.md),
                horizontalArrangement = Arrangement.spacedBy(NutraTheme.spacing.md, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NutraStepperButton(
                    icon = NutraIcons.Remove,
                    contentDescription = "50 kcal azalt",
                    onClick = { onAdjustCalories(-50) },
                    size = 44.dp,
                )

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier
                            .clip(NutraTheme.shapes.md)
                            .background(colors.surfaceMuted)
                            .border(1.dp, colors.divider, NutraTheme.shapes.md)
                            .clickable(role = Role.Button, onClickLabel = "Kalori hedefini yaz") {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onEditField(OnboardingNumericField.CALORIES)
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = NutraFormatters.formatInt(goals.calories),
                            style = NutraTheme.typography.numberHero,
                            color = colors.textPrimary,
                            maxLines = 1,
                            softWrap = false,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "kcal / gün",
                                style = NutraTheme.typography.caption,
                                color = colors.accent,
                                maxLines = 1,
                            )
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    }
                }

                NutraStepperButton(
                    icon = Icons.Rounded.Add,
                    contentDescription = "50 kcal artır",
                    onClick = { onAdjustCalories(50) },
                    size = 44.dp,
                )
            }

            // Animated macro calorie share bar
            NutraStackedRatioBar(
                proteinRatio = pRatio,
                carbsRatio = cRatio,
                fatRatio = fRatio,
                hasData = true,
                height = 8.dp,
                contentDescription = "Makro dağılımı: Protein %$pShare, Karbonhidrat %$cShare, Yağ %$fShare",
                modifier = Modifier.padding(top = NutraTheme.spacing.xl),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = NutraTheme.spacing.md),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MacroShareLegend(color = colors.protein, text = "Protein %$pShare")
                MacroShareLegend(color = colors.carbs, text = "Karb. %$cShare")
                MacroShareLegend(color = colors.fat, text = "Yağ %$fShare")
            }
        }

        // Macro & water steppers
        NutraCard(contentPadding = PaddingValues(horizontal = NutraTheme.spacing.card, vertical = NutraTheme.spacing.lg)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NutraMetricStepperRow(
                    label = "Protein",
                    valueText = "${goals.proteinGrams} g",
                    accentColor = colors.protein,
                    onDecrement = { onAdjustProtein(-5) },
                    onIncrement = { onAdjustProtein(5) },
                    onValueClick = { onEditField(OnboardingNumericField.PROTEIN) },
                )
                NutraDivider()
                NutraMetricStepperRow(
                    label = "Karbonhidrat",
                    valueText = "${goals.carbsGrams} g",
                    accentColor = colors.carbs,
                    onDecrement = { onAdjustCarbs(-5) },
                    onIncrement = { onAdjustCarbs(5) },
                    onValueClick = { onEditField(OnboardingNumericField.CARBS) },
                )
                NutraDivider()
                NutraMetricStepperRow(
                    label = "Yağ",
                    valueText = "${goals.fatGrams} g",
                    accentColor = colors.fat,
                    onDecrement = { onAdjustFat(-5) },
                    onIncrement = { onAdjustFat(5) },
                    onValueClick = { onEditField(OnboardingNumericField.FAT) },
                )
                NutraDivider()
                NutraMetricStepperRow(
                    label = "Su hedefi",
                    valueText = "${NutraFormatters.formatInt(goals.waterMl)} ml",
                    accentColor = colors.water,
                    onDecrement = { onAdjustWater(-250) },
                    onIncrement = { onAdjustWater(250) },
                    onValueClick = { onEditField(OnboardingNumericField.WATER) },
                )
            }
        }
    }
}

@Composable
private fun MacroShareLegend(
    color: androidx.compose.ui.graphics.Color,
    text: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        NutraDot(color = color, size = 7.dp)
        Text(
            text = text,
            style = NutraTheme.typography.caption,
            color = NutraTheme.colors.textSecondary,
        )
    }
}
