package com.nutra.app.feature.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nutra.app.core.designsystem.NutraBottomActionBar
import com.nutra.app.core.designsystem.NutraButton
import com.nutra.app.core.designsystem.NutraButtonSize
import com.nutra.app.core.designsystem.NutraButtonStyle
import com.nutra.app.core.designsystem.NutraCard
import com.nutra.app.core.designsystem.NutraContentMaxWidth
import com.nutra.app.core.designsystem.NutraDialog
import com.nutra.app.core.designsystem.NutraDivider
import com.nutra.app.core.designsystem.NutraFormatters
import com.nutra.app.core.designsystem.NutraIconButton
import com.nutra.app.core.designsystem.NutraMetricStepperRow
import com.nutra.app.core.designsystem.NutraNumericInputDialog
import com.nutra.app.core.designsystem.NutraSectionHeader
import com.nutra.app.core.designsystem.NutraSegmentedControl
import com.nutra.app.core.designsystem.NutraSelectableCard
import com.nutra.app.core.designsystem.NutraSwitch
import com.nutra.app.core.designsystem.NutraTheme
import com.nutra.app.domain.model.ActivityLevel
import com.nutra.app.domain.model.BiologicalSex
import com.nutra.app.domain.model.GoalType
import com.nutra.app.domain.model.UserProfile
import com.nutra.app.domain.usecase.BodyMetricBounds
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
    CURRENT_WEIGHT("Mevcut kilo", "kg", "35,0 – 250,0 kg", BodyMetricBounds.MIN_WEIGHT_KG.toDouble(), BodyMetricBounds.MAX_WEIGHT_KG.toDouble(), true),
    TARGET_WEIGHT("Hedef kilo", "kg", "35,0 – 250,0 kg", BodyMetricBounds.MIN_WEIGHT_KG.toDouble(), BodyMetricBounds.MAX_WEIGHT_KG.toDouble(), true),
    HEIGHT("Boy", "cm", "130 – 225 cm", BodyMetricBounds.MIN_HEIGHT_CM.toDouble(), BodyMetricBounds.MAX_HEIGHT_CM.toDouble(), false),
    AGE("Yaş", "yaş", "14 – 95 yaş", BodyMetricBounds.MIN_AGE.toDouble(), BodyMetricBounds.MAX_AGE.toDouble(), false),
}

@Composable
fun EditBodyProfileScreen(
    profile: UserProfile,
    onNavigateBack: () -> Unit,
    onSave: (
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

    var sex by rememberSaveable { mutableStateOf(profile.sex) }
    var age by rememberSaveable { mutableIntStateOf(profile.age) }
    var heightCm by rememberSaveable { mutableIntStateOf(profile.heightCm) }
    var currentWeight by rememberSaveable { mutableFloatStateOf(profile.currentWeightKg) }
    var targetWeight by rememberSaveable { mutableFloatStateOf(profile.targetWeightKg) }
    var activity by rememberSaveable { mutableStateOf(profile.activityLevel) }
    var goalType by rememberSaveable { mutableStateOf(profile.goalType) }
    var recalculateGoals by rememberSaveable { mutableStateOf(true) }
    var activeBodyField by remember { mutableStateOf<BodyMetricField?>(null) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    val effectiveTargetWeight = if (goalType == GoalType.MAINTAIN) currentWeight else targetWeight
    val hasUnsavedChanges = sex != profile.sex ||
        age != profile.age ||
        heightCm != profile.heightCm ||
        abs(currentWeight - profile.currentWeightKg) >= 0.05f ||
        abs(effectiveTargetWeight - profile.targetWeightKg) >= 0.05f ||
        activity != profile.activityLevel ||
        goalType != profile.goalType

    val targetWeightMismatchHint = when (goalType) {
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

    fun requestExit() {
        if (hasUnsavedChanges) {
            showDiscardDialog = true
        } else {
            onNavigateBack()
        }
    }

    BackHandler(onBack = ::requestExit)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = NutraContentMaxWidth)
                .fillMaxSize(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NutraTheme.spacing.screen)
                    .padding(top = NutraTheme.spacing.section, bottom = NutraTheme.spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NutraTheme.spacing.md),
            ) {
                NutraIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Geri",
                    onClick = ::requestExit,
                    size = 44.dp,
                    iconSize = 20.dp,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "Profili düzenle",
                        style = NutraTheme.typography.titleMedium,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Ölçülerini ve aktivite düzeyini güncel tut",
                        style = NutraTheme.typography.caption,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = NutraTheme.spacing.screen)
                    .padding(top = NutraTheme.spacing.md, bottom = NutraTheme.spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(NutraTheme.spacing.xxl),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(NutraTheme.spacing.md)) {
                    NutraSectionHeader(title = "Cinsiyet")
                    NutraSegmentedControl(
                        items = BiologicalSex.entries,
                        selectedItem = sex,
                        onItemSelected = { sex = it },
                        labelProvider = { it.title },
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(NutraTheme.spacing.md)) {
                    NutraSectionHeader(title = "Hedefin")
                    Column(verticalArrangement = Arrangement.spacedBy(NutraTheme.spacing.md)) {
                        GoalType.entries.forEach { option ->
                            NutraSelectableCard(
                                title = option.title,
                                subtitle = option.subtitle,
                                selected = option == goalType,
                                onClick = {
                                    goalType = option
                                    if (option == GoalType.MAINTAIN) {
                                        targetWeight = currentWeight
                                    }
                                },
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
                                    currentWeight = next
                                    if (goalType == GoalType.MAINTAIN) targetWeight = next
                                },
                                onIncrement = {
                                    val next = ((currentWeight + 0.5f) * 10f)
                                        .roundToInt()
                                        .coerceIn(BodyMetricBounds.MIN_WEIGHT_TENTHS, BodyMetricBounds.MAX_WEIGHT_TENTHS) / 10f
                                    currentWeight = next
                                    if (goalType == GoalType.MAINTAIN) targetWeight = next
                                },
                                onValueClick = { activeBodyField = BodyMetricField.CURRENT_WEIGHT },
                                valueMinWidth = 80.dp,
                            )
                            if (goalType != GoalType.MAINTAIN) {
                                NutraDivider()
                                NutraMetricStepperRow(
                                    label = "Hedef kilo",
                                    valueText = "${NutraFormatters.formatKg(targetWeight)} kg",
                                    onDecrement = {
                                        targetWeight = ((targetWeight - 0.5f) * 10f)
                                            .roundToInt()
                                            .coerceIn(BodyMetricBounds.MIN_WEIGHT_TENTHS, BodyMetricBounds.MAX_WEIGHT_TENTHS) / 10f
                                    },
                                    onIncrement = {
                                        targetWeight = ((targetWeight + 0.5f) * 10f)
                                            .roundToInt()
                                            .coerceIn(BodyMetricBounds.MIN_WEIGHT_TENTHS, BodyMetricBounds.MAX_WEIGHT_TENTHS) / 10f
                                    },
                                    onValueClick = { activeBodyField = BodyMetricField.TARGET_WEIGHT },
                                    valueMinWidth = 80.dp,
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
                                onDecrement = { heightCm = (heightCm - 1).coerceIn(BodyMetricBounds.MIN_HEIGHT_CM, BodyMetricBounds.MAX_HEIGHT_CM) },
                                onIncrement = { heightCm = (heightCm + 1).coerceIn(BodyMetricBounds.MIN_HEIGHT_CM, BodyMetricBounds.MAX_HEIGHT_CM) },
                                onValueClick = { activeBodyField = BodyMetricField.HEIGHT },
                                valueMinWidth = 80.dp,
                            )
                            NutraDivider()
                            NutraMetricStepperRow(
                                label = "Yaş",
                                valueText = "$age yaş",
                                onDecrement = { age = (age - 1).coerceIn(BodyMetricBounds.MIN_AGE, BodyMetricBounds.MAX_AGE) },
                                onIncrement = { age = (age + 1).coerceIn(BodyMetricBounds.MIN_AGE, BodyMetricBounds.MAX_AGE) },
                                onValueClick = { activeBodyField = BodyMetricField.AGE },
                                valueMinWidth = 80.dp,
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
                                onClick = { activity = level },
                            )
                        }
                    }
                }

                NutraCard(contentPadding = PaddingValues(0.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(NutraTheme.shapes.lg)
                            .toggleable(
                                value = recalculateGoals,
                                role = Role.Switch,
                                onValueChange = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    recalculateGoals = it
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
                                text = "Hedefleri yeniden hesapla",
                                style = NutraTheme.typography.label,
                                color = colors.textPrimary,
                            )
                            Text(
                                text = "Mifflin-St Jeor formülüne göre kalori ve makroları günceller",
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
            }

            NutraDivider()
            NutraBottomActionBar {
                NutraButton(
                    text = "Vazgeç",
                    onClick = ::requestExit,
                    style = NutraButtonStyle.Secondary,
                    size = NutraButtonSize.Medium,
                    modifier = Modifier.weight(0.38f),
                )
                NutraButton(
                    text = "Kaydet",
                    onClick = {
                        onSave(
                            sex,
                            age,
                            heightCm,
                            currentWeight,
                            effectiveTargetWeight,
                            activity,
                            goalType,
                            recalculateGoals,
                        )
                    },
                    style = NutraButtonStyle.Primary,
                    size = NutraButtonSize.Medium,
                    hapticFeedback = HapticFeedbackType.Confirm,
                    modifier = Modifier.weight(0.62f),
                )
            }
        }
    }

    if (showDiscardDialog) {
        NutraDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = "Değişikliklerden vazgeç?",
            message = "Yaptığın profil değişiklikleri kaydedilmedi. Çıkmak istediğine emin misin?",
            confirmText = "Vazgeç ve çık",
            onConfirm = {
                showDiscardDialog = false
                onNavigateBack()
            },
            dismissText = "Düzenlemeye dön",
            onDismiss = { showDiscardDialog = false },
            destructive = true,
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
                        currentWeight = next
                        if (goalType == GoalType.MAINTAIN) targetWeight = next
                    }
                    BodyMetricField.TARGET_WEIGHT -> {
                        targetWeight = ((entered * 10.0).roundToInt()
                            .coerceIn(BodyMetricBounds.MIN_WEIGHT_TENTHS, BodyMetricBounds.MAX_WEIGHT_TENTHS)) / 10f
                    }
                    BodyMetricField.HEIGHT -> {
                        heightCm = entered.roundToInt().coerceIn(BodyMetricBounds.MIN_HEIGHT_CM, BodyMetricBounds.MAX_HEIGHT_CM)
                    }
                    BodyMetricField.AGE -> {
                        age = entered.roundToInt().coerceIn(BodyMetricBounds.MIN_AGE, BodyMetricBounds.MAX_AGE)
                    }
                }
                activeBodyField = null
            },
        )
    }
}
