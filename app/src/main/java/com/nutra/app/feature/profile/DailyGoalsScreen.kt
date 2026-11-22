package com.nutra.app.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.nutra.app.core.designsystem.NutraButton
import com.nutra.app.core.designsystem.NutraButtonSize
import com.nutra.app.core.designsystem.NutraButtonStyle
import com.nutra.app.core.designsystem.NutraCard
import com.nutra.app.core.designsystem.NutraContentMaxWidth
import com.nutra.app.core.designsystem.NutraDivider
import com.nutra.app.core.designsystem.NutraFormatters
import com.nutra.app.core.designsystem.NutraMetricStepperRow
import com.nutra.app.core.designsystem.NutraNumericInputDialog
import com.nutra.app.core.designsystem.NutraSectionHeader
import com.nutra.app.core.designsystem.NutraSnackbarHost
import com.nutra.app.core.designsystem.NutraSnackbarTone
import com.nutra.app.core.designsystem.NutraStackedRatioBar
import com.nutra.app.core.designsystem.NutraStickyScreenHeader
import com.nutra.app.core.designsystem.NutraStickySubScreenHeaderTopReservedPadding
import com.nutra.app.core.designsystem.NutraTheme
import com.nutra.app.core.designsystem.rememberAccessibleSnackbarTimeout
import com.nutra.app.core.designsystem.rememberHeaderCollapseFraction
import com.nutra.app.domain.metabolism.MetabolismAnalysisResult
import com.nutra.app.domain.model.GoalChangeSource
import com.nutra.app.domain.model.MacroGoals
import com.nutra.app.domain.model.UserProfile
import com.nutra.app.domain.usecase.MacroGoalBounds
import com.nutra.app.domain.usecase.NutritionCalculatorUseCase
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private enum class DailyGoalField(
    val dialogTitle: String,
    val unit: String,
    val rangeHint: String,
    val minValue: Double,
    val maxValue: Double,
) {
    CALORIES("Kalori hedefi", "kcal", "1.000 – 6.000 kcal", 1000.0, 6000.0),
    PROTEIN("Protein hedefi", "g", "30 – 400 g", 30.0, 400.0),
    CARBS("Karbonhidrat hedefi", "g", "20 – 600 g", 20.0, 600.0),
    FAT("Yağ hedefi", "g", "20 – 250 g", 20.0, 250.0),
    WATER("Su hedefi", "ml", "1.000 – 7.000 ml", 1000.0, 7000.0),
}

@Composable
fun DailyGoalsScreen(
    profile: UserProfile,
    onNavigateBack: () -> Unit,
    onGoalsChange: (MacroGoals, GoalChangeSource) -> Unit,
    metabolismAnalysis: MetabolismAnalysisResult? = null,
    onRecalculateMifflin: (() -> Unit)? = null,
    onUndoRecalculate: (() -> Unit)? = null,
    feedback: ProfileFeedback? = null,
    onClearFeedback: () -> Unit = {},
) {
    val colors = NutraTheme.colors
    val haptic = LocalHapticFeedback.current
    val calculator = remember { NutritionCalculatorUseCase() }
    val bounds = remember { MacroGoalBounds() }
    val initialGoals = profile.goals

    var calories by rememberSaveable { mutableIntStateOf(initialGoals.calories) }
    var proteinGrams by rememberSaveable { mutableIntStateOf(initialGoals.proteinGrams) }
    var carbsGrams by rememberSaveable { mutableIntStateOf(initialGoals.carbsGrams) }
    var fatGrams by rememberSaveable { mutableIntStateOf(initialGoals.fatGrams) }
    var waterMl by rememberSaveable { mutableIntStateOf(initialGoals.waterMl) }

    // Keep local stepper values synced when external formula recalc / undo updates profile.goals
    LaunchedEffect(profile.goals) {
        calories = profile.goals.calories
        proteinGrams = profile.goals.proteinGrams
        carbsGrams = profile.goals.carbsGrams
        fatGrams = profile.goals.fatGrams
        waterMl = profile.goals.waterMl
    }

    var calorieScalingBaseline by remember { mutableStateOf<MacroGoals?>(null) }
    var lastCalorieScaledGoals by remember { mutableStateOf<MacroGoals?>(null) }
    var activeGoalField by remember { mutableStateOf<DailyGoalField?>(null) }

    val draftGoals = MacroGoals(
        calories = calories,
        proteinGrams = proteinGrams,
        carbsGrams = carbsGrams,
        fatGrams = fatGrams,
        waterMl = waterMl,
    )

    fun applyGoals(
        updated: MacroGoals,
        source: GoalChangeSource = GoalChangeSource.MANUAL,
    ) {
        if (updated == draftGoals) return
        calories = updated.calories
        proteinGrams = updated.proteinGrams
        carbsGrams = updated.carbsGrams
        fatGrams = updated.fatGrams
        waterMl = updated.waterMl
        onGoalsChange(updated, source)
    }

    fun applyCalorieTarget(targetCalories: Int) {
        val current = draftGoals
        if (lastCalorieScaledGoals != current) {
            calorieScalingBaseline = current
        }
        val baseline = calorieScalingBaseline ?: current.also { calorieScalingBaseline = it }
        val scaled = calculator.scaleMacrosForCalorieGoal(
            baselineGoals = baseline,
            targetCalories = targetCalories,
            bounds = bounds,
        ).copy(waterMl = current.waterMl)
        lastCalorieScaledGoals = scaled
        applyGoals(scaled, GoalChangeSource.MANUAL)
    }

    fun updateMacro(transform: (MacroGoals) -> MacroGoals) {
        calorieScalingBaseline = null
        lastCalorieScaledGoals = null
        val recalculated = calculator.recalculateCaloriesFromMacros(
            goals = transform(draftGoals),
            bounds = bounds,
        )
        applyGoals(recalculated, GoalChangeSource.MANUAL)
    }

    fun updateWater(nextWaterMl: Int) {
        val clamped = nextWaterMl.coerceIn(bounds.minWaterMl, bounds.maxWaterMl)
        if (clamped == waterMl) return
        waterMl = clamped
        calorieScalingBaseline = calorieScalingBaseline?.copy(waterMl = clamped)
        lastCalorieScaledGoals = lastCalorieScaledGoals?.copy(waterMl = clamped)
        onGoalsChange(draftGoals.copy(waterMl = clamped), GoalChangeSource.MANUAL)
    }

    fun recalculateFromProfile() {
        calorieScalingBaseline = null
        lastCalorieScaledGoals = null
        if (onRecalculateMifflin != null) {
            onRecalculateMifflin()
        } else {
            val calculated = calculator.calculateGoals(
                sex = profile.sex,
                age = profile.age,
                heightCm = profile.heightCm,
                weightKg = profile.currentWeightKg,
                activityLevel = profile.activityLevel,
                goalType = profile.goalType,
            )
            applyGoals(calculated, GoalChangeSource.FORMULA)
        }
    }

    val snackbarTimeoutMs = rememberAccessibleSnackbarTimeout(
        baseTimeoutMillis = if (feedback?.canUndoGoals == true) 4500L else 3000L,
        hasAction = feedback?.canUndoGoals == true,
    )
    LaunchedEffect(feedback?.eventId, feedback?.message) {
        if (feedback != null) {
            delay(snackbarTimeoutMs)
            onClearFeedback()
        }
    }

    val proteinKcal = draftGoals.proteinGrams * 4f
    val carbsKcal = draftGoals.carbsGrams * 4f
    val fatKcal = draftGoals.fatGrams * 9f
    val totalMacroKcal = (proteinKcal + carbsKcal + fatKcal).coerceAtLeast(1f)
    val proteinRatio = proteinKcal / totalMacroKcal
    val carbsRatio = carbsKcal / totalMacroKcal
    val fatRatio = fatKcal / totalMacroKcal

    val scrollState = rememberScrollState()
    val headerCollapseFraction = rememberHeaderCollapseFraction(scrollState)
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val defaultTopPadding = statusBarTop + NutraStickySubScreenHeaderTopReservedPadding
    var measuredHeaderHeight by remember { mutableStateOf(defaultTopPadding) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = NutraContentMaxWidth)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .navigationBarsPadding()
                .padding(horizontal = NutraTheme.spacing.screen)
                .padding(
                    top = maxOf(defaultTopPadding, measuredHeaderHeight + 6.dp),
                    bottom = 24.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(NutraTheme.spacing.xl),
        ) {
            if (profile.isAdolescentObservationMode) {
                NutraCard(
                    color = colors.surfaceMuted,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "18 yaş altı gözlem ve takip modu",
                        style = NutraTheme.typography.label,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = "Otomatik hesaplama yalnızca denge (koruma) referansı üretir. Kişisel beslenme hedefleri için hekim veya diyetisyene danış.",
                        style = NutraTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }

            // Live energy & macro split overview
            NutraCard(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${NutraFormatters.formatInt(draftGoals.calories)} kcal",
                        style = NutraTheme.typography.numberMedium,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = "P %${(proteinRatio * 100f).roundToInt()} · K %${(carbsRatio * 100f).roundToInt()} · Y %${(fatRatio * 100f).roundToInt()}",
                        style = NutraTheme.typography.caption,
                        color = colors.textSecondary,
                    )
                }
                NutraStackedRatioBar(
                    proteinRatio = proteinRatio,
                    carbsRatio = carbsRatio,
                    fatRatio = fatRatio,
                    hasData = true,
                    height = 8.dp,
                    contentDescription = "Hedef makro dağılımı: Protein %${(proteinRatio * 100f).roundToInt()}, Karbonhidrat %${(carbsRatio * 100f).roundToInt()}, Yağ %${(fatRatio * 100f).roundToInt()}",
                )
                if (metabolismAnalysis != null) {
                    NutraDivider()
                    Text(
                        text = if (metabolismAnalysis.isAdaptiveCalibrated) {
                            "Adaptif TDEE tahmini: ~${NutraFormatters.formatInt(metabolismAnalysis.estimatedTdeeKcal)} ± ${NutraFormatters.formatInt(metabolismAnalysis.uncertaintyKcal)} kcal (Güven %${metabolismAnalysis.confidenceScore})"
                        } else {
                            "Formül TDEE tahmini: ~${NutraFormatters.formatInt(metabolismAnalysis.priorTdeeKcal)} kcal · Düzenli kayıtla kişiselleşir"
                        },
                        style = NutraTheme.typography.caption,
                        color = colors.textSecondary,
                    )
                    val adaptiveProposal = metabolismAnalysis.calorieProposal
                    if (adaptiveProposal != null && !profile.isAdolescentObservationMode) {
                        NutraButton(
                            text = "Adaptif öneriyi uygula (${NutraFormatters.formatInt(adaptiveProposal.proposedGoals.calories)} kcal)",
                            onClick = { applyGoals(adaptiveProposal.proposedGoals, GoalChangeSource.ADAPTIVE_APPROVED) },
                            style = NutraButtonStyle.Secondary,
                            size = NutraButtonSize.Small,
                            hapticFeedback = HapticFeedbackType.Confirm,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // Calorie & macro steppers (uniform valueMinWidth = 104.dp aligns all +/- columns)
            Column(verticalArrangement = Arrangement.spacedBy(NutraTheme.spacing.md)) {
                NutraSectionHeader(
                    title = "Kalori ve makrolar",
                    actionLabel = "Oto hesapla",
                    actionIcon = Icons.Rounded.Refresh,
                    actionContainerColor = colors.surfaceElevated,
                    actionHaptic = HapticFeedbackType.Confirm,
                    onAction = ::recalculateFromProfile,
                )

                NutraCard(contentPadding = PaddingValues(horizontal = NutraTheme.spacing.card, vertical = NutraTheme.spacing.lg)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        NutraMetricStepperRow(
                            label = "Kalori",
                            valueText = "${NutraFormatters.formatInt(draftGoals.calories)} kcal",
                            accentColor = colors.accent,
                            onDecrement = { applyCalorieTarget(draftGoals.calories - 50) },
                            onIncrement = { applyCalorieTarget(draftGoals.calories + 50) },
                            onValueClick = { activeGoalField = DailyGoalField.CALORIES },
                            valueMinWidth = 104.dp,
                        )
                        NutraDivider()
                        NutraMetricStepperRow(
                            label = "Protein",
                            valueText = "${draftGoals.proteinGrams} g",
                            accentColor = colors.protein,
                            onDecrement = { updateMacro { it.copy(proteinGrams = it.proteinGrams - 5) } },
                            onIncrement = { updateMacro { it.copy(proteinGrams = it.proteinGrams + 5) } },
                            onValueClick = { activeGoalField = DailyGoalField.PROTEIN },
                            valueMinWidth = 104.dp,
                        )
                        NutraDivider()
                        NutraMetricStepperRow(
                            label = "Karbonhidrat",
                            valueText = "${draftGoals.carbsGrams} g",
                            accentColor = colors.carbs,
                            onDecrement = { updateMacro { it.copy(carbsGrams = it.carbsGrams - 5) } },
                            onIncrement = { updateMacro { it.copy(carbsGrams = it.carbsGrams + 5) } },
                            onValueClick = { activeGoalField = DailyGoalField.CARBS },
                            valueMinWidth = 104.dp,
                        )
                        NutraDivider()
                        NutraMetricStepperRow(
                            label = "Yağ",
                            valueText = "${draftGoals.fatGrams} g",
                            accentColor = colors.fat,
                            onDecrement = { updateMacro { it.copy(fatGrams = it.fatGrams - 5) } },
                            onIncrement = { updateMacro { it.copy(fatGrams = it.fatGrams + 5) } },
                            onValueClick = { activeGoalField = DailyGoalField.FAT },
                            valueMinWidth = 104.dp,
                        )
                    }
                }
            }

            // Water goal stepper
            Column(verticalArrangement = Arrangement.spacedBy(NutraTheme.spacing.md)) {
                NutraSectionHeader(title = "Su hedefi")

                NutraCard(contentPadding = PaddingValues(horizontal = NutraTheme.spacing.card, vertical = NutraTheme.spacing.lg)) {
                    NutraMetricStepperRow(
                        label = "Günlük su",
                        valueText = "${NutraFormatters.formatInt(draftGoals.waterMl)} ml",
                        accentColor = colors.water,
                        onDecrement = { updateWater(draftGoals.waterMl - 250) },
                        onIncrement = { updateWater(draftGoals.waterMl + 250) },
                        onValueClick = { activeGoalField = DailyGoalField.WATER },
                        valueMinWidth = 104.dp,
                    )
                }
            }
        }

        NutraStickyScreenHeader(
            title = "Günlük hedefler",
            eyebrow = "Kalori, makro ve su hedeflerini ayarla",
            collapseFractionState = headerCollapseFraction,
            onNavigateBack = onNavigateBack,
            onHeaderHeightMeasured = { measuredHeaderHeight = it },
            modifier = Modifier.align(Alignment.TopCenter),
        )

        NutraSnackbarHost(
            visible = feedback != null,
            message = feedback?.message.orEmpty(),
            tone = feedback?.tone ?: NutraSnackbarTone.Success,
            actionLabel = if (feedback?.canUndoGoals == true && onUndoRecalculate != null) "Geri al" else null,
            onAction = if (feedback?.canUndoGoals == true && onUndoRecalculate != null) {
                {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    onUndoRecalculate()
                }
            } else {
                null
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = NutraTheme.spacing.screen, vertical = 12.dp),
        )
    }

    val editingField = activeGoalField
    if (editingField != null) {
        val currentValue = when (editingField) {
            DailyGoalField.CALORIES -> draftGoals.calories.toString()
            DailyGoalField.PROTEIN -> draftGoals.proteinGrams.toString()
            DailyGoalField.CARBS -> draftGoals.carbsGrams.toString()
            DailyGoalField.FAT -> draftGoals.fatGrams.toString()
            DailyGoalField.WATER -> draftGoals.waterMl.toString()
        }
        NutraNumericInputDialog(
            title = editingField.dialogTitle,
            initialValue = currentValue,
            unit = editingField.unit,
            rangeHint = editingField.rangeHint,
            minValue = editingField.minValue,
            maxValue = editingField.maxValue,
            allowDecimal = false,
            onDismiss = { activeGoalField = null },
            onConfirm = { entered ->
                val rounded = entered.roundToInt()
                when (editingField) {
                    DailyGoalField.CALORIES -> applyCalorieTarget(rounded)
                    DailyGoalField.PROTEIN -> updateMacro { it.copy(proteinGrams = rounded) }
                    DailyGoalField.CARBS -> updateMacro { it.copy(carbsGrams = rounded) }
                    DailyGoalField.FAT -> updateMacro { it.copy(fatGrams = rounded) }
                    DailyGoalField.WATER -> updateWater(rounded)
                }
                activeGoalField = null
            },
        )
    }
}
