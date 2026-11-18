package com.nutra.app.feature.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nutra.app.core.designsystem.NutraBottomActionBar
import com.nutra.app.core.designsystem.NutraButton
import com.nutra.app.core.designsystem.NutraButtonSize
import com.nutra.app.core.designsystem.NutraButtonStyle
import com.nutra.app.core.designsystem.NutraCard
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
import kotlin.math.roundToInt

private enum class BodyMetricField(
    val dialogTitle: String,
    val unit: String,
    val rangeHint: String,
    val minValue: Double,
    val maxValue: Double,
    val allowDecimal: Boolean,
) {
    CURRENT_WEIGHT("Mevcut kilo", "kg", "35,0 – 250,0 kg", 35.0, 250.0, true),
    TARGET_WEIGHT("Hedef kilo", "kg", "35,0 – 250,0 kg", 35.0, 250.0, true),
    HEIGHT("Boy", "cm", "130 – 225 cm", 130.0, 225.0, false),
    AGE("Yaş", "yaş", "14 – 95 yaş", 14.0, 95.0, false),
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

    var sex by rememberSaveable { mutableStateOf(profile.sex) }
    var age by rememberSaveable { mutableIntStateOf(profile.age) }
    var heightCm by rememberSaveable { mutableIntStateOf(profile.heightCm) }
    var currentWeight by rememberSaveable { mutableFloatStateOf(profile.currentWeightKg) }
    var targetWeight by rememberSaveable { mutableFloatStateOf(profile.targetWeightKg) }
    var activity by rememberSaveable { mutableStateOf(profile.activityLevel) }
    var goalType by rememberSaveable { mutableStateOf(profile.goalType) }
    var recalculateGoals by rememberSaveable { mutableStateOf(true) }
    var activeBodyField by remember { mutableStateOf<BodyMetricField?>(null) }

    BackHandler(onBack = onNavigateBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
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
                onClick = onNavigateBack,
                size = 40.dp,
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
                            onClick = { goalType = option },
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
                                currentWeight = ((currentWeight - 0.5f) * 10f)
                                    .roundToInt()
                                    .coerceIn(350, 2500) / 10f
                            },
                            onIncrement = {
                                currentWeight = ((currentWeight + 0.5f) * 10f)
                                    .roundToInt()
                                    .coerceIn(350, 2500) / 10f
                            },
                            onValueClick = { activeBodyField = BodyMetricField.CURRENT_WEIGHT },
                            valueMinWidth = 80.dp,
                        )
                        NutraDivider()
                        NutraMetricStepperRow(
                            label = "Hedef kilo",
                            valueText = "${NutraFormatters.formatKg(targetWeight)} kg",
                            onDecrement = {
                                targetWeight = ((targetWeight - 0.5f) * 10f)
                                    .roundToInt()
                                    .coerceIn(350, 2500) / 10f
                            },
                            onIncrement = {
                                targetWeight = ((targetWeight + 0.5f) * 10f)
                                    .roundToInt()
                                    .coerceIn(350, 2500) / 10f
                            },
                            onValueClick = { activeBodyField = BodyMetricField.TARGET_WEIGHT },
                            valueMinWidth = 80.dp,
                        )
                        NutraDivider()
                        NutraMetricStepperRow(
                            label = "Boy",
                            valueText = "$heightCm cm",
                            onDecrement = { heightCm = (heightCm - 1).coerceIn(130, 225) },
                            onIncrement = { heightCm = (heightCm + 1).coerceIn(130, 225) },
                            onValueClick = { activeBodyField = BodyMetricField.HEIGHT },
                            valueMinWidth = 80.dp,
                        )
                        NutraDivider()
                        NutraMetricStepperRow(
                            label = "Yaş",
                            valueText = "$age yaş",
                            onDecrement = { age = (age - 1).coerceIn(14, 95) },
                            onIncrement = { age = (age + 1).coerceIn(14, 95) },
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

            NutraCard(contentPadding = PaddingValues(horizontal = NutraTheme.spacing.card, vertical = NutraTheme.spacing.lg)) {
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
                        onCheckedChange = { recalculateGoals = it },
                    )
                }
            }
        }

        NutraDivider()
        NutraBottomActionBar {
            NutraButton(
                text = "Vazgeç",
                onClick = onNavigateBack,
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
                        targetWeight,
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
                        currentWeight = ((entered * 10.0).roundToInt().coerceIn(350, 2500)) / 10f
                    }
                    BodyMetricField.TARGET_WEIGHT -> {
                        targetWeight = ((entered * 10.0).roundToInt().coerceIn(350, 2500)) / 10f
                    }
                    BodyMetricField.HEIGHT -> {
                        heightCm = entered.roundToInt().coerceIn(130, 225)
                    }
                    BodyMetricField.AGE -> {
                        age = entered.roundToInt().coerceIn(14, 95)
                    }
                }
                activeBodyField = null
            },
        )
    }
}
