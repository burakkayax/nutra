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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.nutra.app.core.designsystem.NutraCard
import com.nutra.app.core.designsystem.NutraContentMaxWidth
import com.nutra.app.core.designsystem.NutraDivider
import com.nutra.app.core.designsystem.NutraFormatters
import com.nutra.app.core.designsystem.NutraMetricStepperRow
import com.nutra.app.core.designsystem.NutraNumericInputDialog
import com.nutra.app.core.designsystem.NutraSectionHeader
import com.nutra.app.core.designsystem.NutraSegmentedControl
import com.nutra.app.core.designsystem.NutraSelectableCard
import com.nutra.app.core.designsystem.NutraStickyScreenHeader
import com.nutra.app.core.designsystem.NutraStickySubScreenHeaderTopReservedPadding
import com.nutra.app.core.designsystem.NutraSwitch
import com.nutra.app.core.designsystem.NutraTheme
import com.nutra.app.core.designsystem.rememberHeaderCollapseFraction
import com.nutra.app.domain.model.ActivityLevel
import com.nutra.app.domain.model.BiologicalSex
import com.nutra.app.domain.model.GoalType
import com.nutra.app.domain.model.UserProfile
import com.nutra.app.domain.usecase.BodyMetricBounds
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class BodyMetricField(
    val dialogTitle: String,
    val unit: String,
    val rangeHint: String,
    val minValue: Double,
    val maxValue: Double,
    val allowDecimal: Boolean,
) {
    CURRENT_WEIGHT("Mevcut kilo", "kg", "30,0 – 300,0 kg", BodyMetricBounds.MIN_WEIGHT_KG.toDouble(), BodyMetricBounds.MAX_WEIGHT_KG.toDouble(), true),
    TARGET_WEIGHT("Hedef kilo", "kg", "30,0 – 300,0 kg", BodyMetricBounds.MIN_WEIGHT_KG.toDouble(), BodyMetricBounds.MAX_WEIGHT_KG.toDouble(), true),
    HEIGHT("Boy", "cm", "130 – 225 cm", BodyMetricBounds.MIN_HEIGHT_CM.toDouble(), BodyMetricBounds.MAX_HEIGHT_CM.toDouble(), false),
    AGE("Yaş", "yaş", "14 – 95 yaş", BodyMetricBounds.MIN_AGE.toDouble(), BodyMetricBounds.MAX_AGE.toDouble(), false),
}

private data class PendingBodyCommit(
    val sex: BiologicalSex,
    val age: Int,
    val heightCm: Int,
    val currentWeightKg: Float,
    val targetWeightKg: Float,
    val activityLevel: ActivityLevel,
    val goalType: GoalType,
    val recalculateGoals: Boolean,
    val seq: Int,
)

@Composable
fun EditBodyProfileScreen(
    profile: UserProfile,
    onNavigateBack: () -> Unit,
    onProfileChange: (
        sex: BiologicalSex,
        age: Int,
        heightCm: Int,
        currentWeightKg: Float,
        targetWeightKg: Float,
        activityLevel: ActivityLevel,
        goalType: GoalType,
        recalculateGoals: Boolean,
    ) -> Unit,
) {
    val colors = NutraTheme.colors
    val haptic = LocalHapticFeedback.current
    val latestOnProfileChange by rememberUpdatedState(onProfileChange)

    var sex by rememberSaveable { mutableStateOf(profile.sex) }
    var age by rememberSaveable { mutableIntStateOf(profile.age) }
    var heightCm by rememberSaveable { mutableIntStateOf(profile.heightCm) }
    var currentWeight by rememberSaveable { mutableFloatStateOf(profile.currentWeightKg) }
    var targetWeight by rememberSaveable { mutableFloatStateOf(profile.targetWeightKg) }
    var activity by rememberSaveable { mutableStateOf(profile.activityLevel) }
    var goalType by rememberSaveable { mutableStateOf(profile.goalType) }
    // Default false so editing height/weight does not unexpectedly wipe custom macro goals
    var recalculateGoals by rememberSaveable { mutableStateOf(false) }
    var activeBodyField by remember { mutableStateOf<BodyMetricField?>(null) }
    var pendingCommit by remember { mutableStateOf<PendingBodyCommit?>(null) }
    var commitSeq by remember { mutableIntStateOf(0) }

    val isAdolescent = age < BodyMetricBounds.ADULT_MIN_AGE
    val effectiveGoalType = if (isAdolescent) GoalType.MAINTAIN else goalType

    // Debounce rapid hold-to-repeat stepper ticks so DataStore/Room aren't flooded,
    // and flush any unpersisted change immediately if the user navigates back within 250ms.
    var lastCommittedSeq by remember { mutableIntStateOf(0) }
    val latestPendingCommit by rememberUpdatedState(pendingCommit)
    val latestCommittedSeq by rememberUpdatedState(lastCommittedSeq)
    LaunchedEffect(pendingCommit) {
        val commit = pendingCommit ?: return@LaunchedEffect
        delay(250L)
        if (commit.seq > lastCommittedSeq) {
            lastCommittedSeq = commit.seq
            latestOnProfileChange(
                commit.sex,
                commit.age,
                commit.heightCm,
                commit.currentWeightKg,
                commit.targetWeightKg,
                commit.activityLevel,
                commit.goalType,
                commit.recalculateGoals,
            )
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            val pending = latestPendingCommit
            if (pending != null && pending.seq > latestCommittedSeq) {
                latestOnProfileChange(
                    pending.sex,
                    pending.age,
                    pending.heightCm,
                    pending.currentWeightKg,
                    pending.targetWeightKg,
                    pending.activityLevel,
                    pending.goalType,
                    pending.recalculateGoals,
                )
            }
        }
    }

    fun persistBodyProfile(
        nextSex: BiologicalSex = sex,
        nextAge: Int = age,
        nextHeightCm: Int = heightCm,
        nextCurrentWeight: Float = currentWeight,
        nextTargetWeight: Float = targetWeight,
        nextActivity: ActivityLevel = activity,
        nextGoalType: GoalType = goalType,
        shouldRecalculate: Boolean = recalculateGoals,
    ) {
        val safeGoal = if (nextAge < BodyMetricBounds.ADULT_MIN_AGE) GoalType.MAINTAIN else nextGoalType
        val effectiveTarget = if (safeGoal == GoalType.MAINTAIN) nextCurrentWeight else nextTargetWeight
        commitSeq++
        pendingCommit = PendingBodyCommit(
            sex = nextSex,
            age = nextAge,
            heightCm = nextHeightCm,
            currentWeightKg = nextCurrentWeight,
            targetWeightKg = effectiveTarget,
            activityLevel = nextActivity,
            goalType = safeGoal,
            recalculateGoals = shouldRecalculate,
            seq = commitSeq,
        )
    }

    val targetWeightMismatchHint = when (effectiveGoalType) {
        GoalType.LOSE_WEIGHT -> if (targetWeight >= currentWeight) {
            "Kilo verme hedefinde hedef kilon genellikle mevcut kilondan düşük olur."
        } else {
            null
        }
        GoalType.GAIN_MUSCLE -> if (targetWeight <= currentWeight) {
            "Hacim kazanımı hedefinde hedef kilon genellikle mevcut kilondan yüksek olur."
        } else {
            null
        }
        GoalType.MAINTAIN -> null
    }

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
            if (isAdolescent) {
                NutraCard(
                    color = colors.surfaceMuted,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "18 yaş altı gözlem ve takip modu aktif",
                        style = NutraTheme.typography.label,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = "Otomatik kalori açığı veya fazlası hesaplanmaz; hedef Kilo Koruma düzeyinde tutulur. Kişisel hedefler için hekim veya diyetisyene danış.",
                        style = NutraTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }

            // Visible recalculate toggle near top so users decide intentionally before tweaking metrics
            NutraCard(contentPadding = PaddingValues(0.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(NutraTheme.shapes.lg)
                        .toggleable(
                            value = recalculateGoals,
                            role = Role.Switch,
                            onValueChange = { enabled ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                recalculateGoals = enabled
                                if (enabled) {
                                    persistBodyProfile(shouldRecalculate = true)
                                }
                            },
                        )
                        .padding(horizontal = NutraTheme.spacing.card, vertical = NutraTheme.spacing.lg),
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
                            text = "Değişikliklerde hedefleri yeniden hesapla",
                            style = NutraTheme.typography.label,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = if (recalculateGoals) {
                                "Ölçü değişince kalori ve makrolar formüle göre güncellenir"
                            } else {
                                "Kapalı: Özel kalori ve makro hedeflerin korunur"
                            },
                            style = NutraTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                    NutraSwitch(
                        checked = recalculateGoals,
                        onCheckedChange = null,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(NutraTheme.spacing.md)) {
                NutraSectionHeader(title = "Cinsiyet")
                NutraSegmentedControl(
                    items = remember { BiologicalSex.entries.toImmutableList() },
                    selectedItem = sex,
                    onItemSelected = { next ->
                        if (next != sex) {
                            sex = next
                            persistBodyProfile(nextSex = next)
                        }
                    },
                    labelProvider = { it.title },
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(NutraTheme.spacing.md)) {
                NutraSectionHeader(title = "Hedefin")
                Column(verticalArrangement = Arrangement.spacedBy(NutraTheme.spacing.md)) {
                    GoalType.entries.forEach { option ->
                        val isRestricted = isAdolescent && option != GoalType.MAINTAIN
                        NutraSelectableCard(
                            title = option.title,
                            subtitle = if (isRestricted) "18 yaş altında hekim/diyetisyen gözetimi gerektirir" else option.subtitle,
                            selected = option == effectiveGoalType,
                            onClick = {
                                if (!isRestricted && option != goalType) {
                                    goalType = option
                                    val nextTarget = if (option == GoalType.MAINTAIN) {
                                        targetWeight = currentWeight
                                        currentWeight
                                    } else {
                                        targetWeight
                                    }
                                    persistBodyProfile(
                                        nextGoalType = option,
                                        nextTargetWeight = nextTarget,
                                    )
                                }
                            },
                            modifier = if (isRestricted) Modifier.graphicsLayer { alpha = 0.45f } else Modifier,
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(NutraTheme.spacing.md)) {
                NutraSectionHeader(title = "Vücut ölçüleri")
                NutraCard(contentPadding = PaddingValues(horizontal = NutraTheme.spacing.card, vertical = NutraTheme.spacing.lg)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        NutraMetricStepperRow(
                            label = "Mevcut kilo",
                            valueText = "${NutraFormatters.formatKg(currentWeight)} kg",
                            onDecrement = {
                                val next = ((currentWeight - 0.5f) * 10f)
                                    .roundToInt()
                                    .coerceIn(BodyMetricBounds.MIN_WEIGHT_TENTHS, BodyMetricBounds.MAX_WEIGHT_TENTHS) / 10f
                                if (abs(next - currentWeight) >= 0.01f) {
                                    currentWeight = next
                                    val nextTarget = if (effectiveGoalType == GoalType.MAINTAIN) {
                                        targetWeight = next
                                        next
                                    } else {
                                        targetWeight
                                    }
                                    persistBodyProfile(
                                        nextCurrentWeight = next,
                                        nextTargetWeight = nextTarget,
                                    )
                                }
                            },
                            onIncrement = {
                                val next = ((currentWeight + 0.5f) * 10f)
                                    .roundToInt()
                                    .coerceIn(BodyMetricBounds.MIN_WEIGHT_TENTHS, BodyMetricBounds.MAX_WEIGHT_TENTHS) / 10f
                                if (abs(next - currentWeight) >= 0.01f) {
                                    currentWeight = next
                                    val nextTarget = if (effectiveGoalType == GoalType.MAINTAIN) {
                                        targetWeight = next
                                        next
                                    } else {
                                        targetWeight
                                    }
                                    persistBodyProfile(
                                        nextCurrentWeight = next,
                                        nextTargetWeight = nextTarget,
                                    )
                                }
                            },
                            onValueClick = { activeBodyField = BodyMetricField.CURRENT_WEIGHT },
                            valueMinWidth = 88.dp,
                        )
                        if (effectiveGoalType != GoalType.MAINTAIN) {
                            NutraDivider()
                            NutraMetricStepperRow(
                                label = "Hedef kilo",
                                valueText = "${NutraFormatters.formatKg(targetWeight)} kg",
                                onDecrement = {
                                    val next = ((targetWeight - 0.5f) * 10f)
                                        .roundToInt()
                                        .coerceIn(BodyMetricBounds.MIN_WEIGHT_TENTHS, BodyMetricBounds.MAX_WEIGHT_TENTHS) / 10f
                                    if (abs(next - targetWeight) >= 0.01f) {
                                        targetWeight = next
                                        persistBodyProfile(
                                            nextTargetWeight = next,
                                            shouldRecalculate = false,
                                        )
                                    }
                                },
                                onIncrement = {
                                    val next = ((targetWeight + 0.5f) * 10f)
                                        .roundToInt()
                                        .coerceIn(BodyMetricBounds.MIN_WEIGHT_TENTHS, BodyMetricBounds.MAX_WEIGHT_TENTHS) / 10f
                                    if (abs(next - targetWeight) >= 0.01f) {
                                        targetWeight = next
                                        persistBodyProfile(
                                            nextTargetWeight = next,
                                            shouldRecalculate = false,
                                        )
                                    }
                                },
                                onValueClick = { activeBodyField = BodyMetricField.TARGET_WEIGHT },
                                valueMinWidth = 88.dp,
                            )
                        }
                        if (targetWeightMismatchHint != null) {
                            Text(
                                text = targetWeightMismatchHint,
                                style = NutraTheme.typography.caption,
                                color = colors.warningText,
                            )
                        }
                        NutraDivider()
                        NutraMetricStepperRow(
                            label = "Boy",
                            valueText = "$heightCm cm",
                            onDecrement = {
                                val next = (heightCm - 1).coerceIn(BodyMetricBounds.MIN_HEIGHT_CM, BodyMetricBounds.MAX_HEIGHT_CM)
                                if (next != heightCm) {
                                    heightCm = next
                                    persistBodyProfile(nextHeightCm = next)
                                }
                            },
                            onIncrement = {
                                val next = (heightCm + 1).coerceIn(BodyMetricBounds.MIN_HEIGHT_CM, BodyMetricBounds.MAX_HEIGHT_CM)
                                if (next != heightCm) {
                                    heightCm = next
                                    persistBodyProfile(nextHeightCm = next)
                                }
                            },
                            onValueClick = { activeBodyField = BodyMetricField.HEIGHT },
                            valueMinWidth = 88.dp,
                        )
                        NutraDivider()
                        NutraMetricStepperRow(
                            label = "Yaş",
                            valueText = "$age yaş",
                            onDecrement = {
                                val next = (age - 1).coerceIn(BodyMetricBounds.MIN_AGE, BodyMetricBounds.MAX_AGE)
                                if (next != age) {
                                    age = next
                                    if (next < BodyMetricBounds.ADULT_MIN_AGE) {
                                        goalType = GoalType.MAINTAIN
                                        targetWeight = currentWeight
                                    }
                                    persistBodyProfile(nextAge = next)
                                }
                            },
                            onIncrement = {
                                val next = (age + 1).coerceIn(BodyMetricBounds.MIN_AGE, BodyMetricBounds.MAX_AGE)
                                if (next != age) {
                                    age = next
                                    persistBodyProfile(nextAge = next)
                                }
                            },
                            onValueClick = { activeBodyField = BodyMetricField.AGE },
                            valueMinWidth = 88.dp,
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(NutraTheme.spacing.md)) {
                NutraSectionHeader(title = "Hareket düzeyi")
                Column(verticalArrangement = Arrangement.spacedBy(NutraTheme.spacing.md)) {
                    ActivityLevel.entries.forEach { level ->
                        NutraSelectableCard(
                            title = level.title,
                            subtitle = level.subtitle,
                            selected = level == activity,
                            onClick = {
                                if (level != activity) {
                                    activity = level
                                    persistBodyProfile(nextActivity = level)
                                }
                            },
                        )
                    }
                }
            }
        }

        NutraStickyScreenHeader(
            title = "Profili düzenle",
            eyebrow = "Ölçülerini ve aktivite düzeyini güncel tut",
            collapseFractionState = headerCollapseFraction,
            onNavigateBack = onNavigateBack,
            onHeaderHeightMeasured = { measuredHeaderHeight = it },
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }

    val editingBodyField = activeBodyField
    if (editingBodyField != null) {
        val currentValue = when (editingBodyField) {
            BodyMetricField.CURRENT_WEIGHT -> NutraFormatters.formatKg(currentWeight)
            BodyMetricField.TARGET_WEIGHT -> NutraFormatters.formatKg(targetWeight)
            BodyMetricField.HEIGHT -> heightCm.toString()
            BodyMetricField.AGE -> age.toString()
        }
        NutraNumericInputDialog(
            title = editingBodyField.dialogTitle,
            initialValue = currentValue,
            unit = editingBodyField.unit,
            rangeHint = editingBodyField.rangeHint,
            minValue = editingBodyField.minValue,
            maxValue = editingBodyField.maxValue,
            allowDecimal = editingBodyField.allowDecimal,
            onDismiss = { activeBodyField = null },
            onConfirm = { entered ->
                when (editingBodyField) {
                    BodyMetricField.CURRENT_WEIGHT -> {
                        val next = ((entered * 10.0).roundToInt()
                            .coerceIn(BodyMetricBounds.MIN_WEIGHT_TENTHS, BodyMetricBounds.MAX_WEIGHT_TENTHS)) / 10f
                        if (abs(next - currentWeight) >= 0.01f) {
                            currentWeight = next
                            val nextTarget = if (goalType == GoalType.MAINTAIN) {
                                targetWeight = next
                                next
                            } else {
                                targetWeight
                            }
                            persistBodyProfile(
                                nextCurrentWeight = next,
                                nextTargetWeight = nextTarget,
                            )
                        }
                    }
                    BodyMetricField.TARGET_WEIGHT -> {
                        val next = ((entered * 10.0).roundToInt()
                            .coerceIn(BodyMetricBounds.MIN_WEIGHT_TENTHS, BodyMetricBounds.MAX_WEIGHT_TENTHS)) / 10f
                        if (abs(next - targetWeight) >= 0.01f) {
                            targetWeight = next
                            persistBodyProfile(
                                nextTargetWeight = next,
                                shouldRecalculate = false,
                            )
                        }
                    }
                    BodyMetricField.HEIGHT -> {
                        val next = entered.roundToInt().coerceIn(BodyMetricBounds.MIN_HEIGHT_CM, BodyMetricBounds.MAX_HEIGHT_CM)
                        if (next != heightCm) {
                            heightCm = next
                            persistBodyProfile(nextHeightCm = next)
                        }
                    }
                    BodyMetricField.AGE -> {
                        val next = entered.roundToInt().coerceIn(BodyMetricBounds.MIN_AGE, BodyMetricBounds.MAX_AGE)
                        if (next != age) {
                            age = next
                            if (next < BodyMetricBounds.ADULT_MIN_AGE) {
                                goalType = GoalType.MAINTAIN
                                targetWeight = currentWeight
                            }
                            persistBodyProfile(nextAge = next)
                        }
                    }
                }
                activeBodyField = null
            },
        )
    }
}
