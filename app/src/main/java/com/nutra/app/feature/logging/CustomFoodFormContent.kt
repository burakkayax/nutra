package com.nutra.app.feature.logging

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nutra.app.core.designsystem.NutraBottomActionBar
import com.nutra.app.core.designsystem.NutraButton
import com.nutra.app.core.designsystem.NutraButtonSize
import com.nutra.app.core.designsystem.NutraButtonStyle
import com.nutra.app.core.designsystem.NutraDivider
import com.nutra.app.core.designsystem.NutraFormatters
import com.nutra.app.core.designsystem.NutraSegmentedControl
import com.nutra.app.core.designsystem.NutraTextField
import com.nutra.app.core.designsystem.NutraTheme
import com.nutra.app.domain.model.MealType
import com.nutra.app.domain.model.ServingUnitLabels
import com.nutra.app.feature.dashboard.CustomFoodDraft
import kotlin.math.roundToInt
import kotlinx.collections.immutable.persistentListOf

private fun MealType.toDativeTurkishLabel(): String = when (this) {
    MealType.BREAKFAST -> "Kahvaltıya"
    MealType.LUNCH -> "Öğle yemeğine"
    MealType.DINNER -> "Akşam yemeğine"
    MealType.SNACK -> "Ara öğüne"
}

@Composable
internal fun CustomFoodCreatorTab(
    mealType: MealType,
    draft: CustomFoodDraft,
    onDraftChange: (CustomFoodDraft) -> Unit,
    onCreateAndLog: (
        name: String,
        unitLabel: String,
        unitGrams: Float,
        caloriesPerServing: Int,
        proteinPerServing: Float,
        carbsPerServing: Float,
        fatPerServing: Float,
        baseUnit: String,
    ) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val colors = NutraTheme.colors
    val unitSuffix = ServingUnitLabels.normalizedBaseUnit(draft.baseUnit)
    val isLiquid = unitSuffix == "ml"

    val parsedGrams = NutraFormatters.parseLocalizedFloat(draft.unitGrams)
    val parsedCalories = draft.calories.toIntOrNull()
    val parsedProtein = NutraFormatters.parseLocalizedFloat(draft.protein) ?: 0f
    val parsedCarbs = NutraFormatters.parseLocalizedFloat(draft.carbs) ?: 0f
    val parsedFat = NutraFormatters.parseLocalizedFloat(draft.fat) ?: 0f
    val macroEstimatedCalories = ((parsedProtein * 4f) + (parsedCarbs * 4f) + (parsedFat * 9f)).roundToInt()
    val isGramsInvalid = draft.unitGrams.isNotEmpty() && (parsedGrams == null || parsedGrams !in 1f..5000f)
    val isCaloriesInvalid = draft.calories.isNotEmpty() && (parsedCalories == null || parsedCalories !in 0..20000)
    val macroMassSum = parsedProtein + parsedCarbs + parsedFat
    val exceedsServingMass = parsedGrams != null && parsedGrams > 0f && macroMassSum > parsedGrams * 1.05f
    val exceedsCalorieDensity = parsedGrams != null && parsedGrams > 0f && parsedCalories != null &&
        ((parsedCalories * 100f) / parsedGrams).roundToInt() > 950
    val hasCalorieMacroMismatch = parsedCalories != null && macroEstimatedCalories > 0 &&
        kotlin.math.abs(parsedCalories - macroEstimatedCalories) > maxOf(60, (macroEstimatedCalories * 0.25f).roundToInt())
    val isFormValid = draft.name.isNotBlank() &&
        parsedCalories != null &&
        parsedCalories in 0..20000 &&
        parsedGrams != null &&
        parsedGrams in 1f..5000f &&
        !exceedsServingMass &&
        !exceedsCalorieDensity

    fun submitCustomFood() {
        val safeGrams = parsedGrams ?: return
        val safeCalories = parsedCalories ?: return
        if (!isFormValid) return
        onCreateAndLog(
            draft.name,
            draft.unitLabel,
            safeGrams,
            safeCalories,
            parsedProtein,
            parsedCarbs,
            parsedFat,
            unitSuffix,
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = NutraTheme.spacing.screen),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "Özel besin oluştur",
                    style = NutraTheme.typography.titleSmall,
                    color = colors.textPrimary,
                )
                Text(
                    text = "Kalori ve makro değerlerini aşağıda tanımladığın 1 porsiyon için gir.",
                    style = NutraTheme.typography.caption,
                    color = colors.textSecondary,
                )
            }

            NutraTextField(
                value = draft.name,
                onValueChange = { onDraftChange(draft.copy(name = it)) },
                label = "Besin veya yemek adı",
                placeholder = "Örn: Anne köftesi",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
            )

            NutraSegmentedControl(
                items = remember { persistentListOf("g", "ml") },
                selectedItem = unitSuffix,
                onItemSelected = { nextUnit ->
                    onDraftChange(draft.copy(baseUnit = nextUnit))
                },
                labelProvider = { unit -> if (unit == "ml") "Sıvı (ml)" else "Katı (g)" },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                NutraTextField(
                    value = draft.unitLabel,
                    onValueChange = { onDraftChange(draft.copy(unitLabel = it)) },
                    label = "Porsiyon adı",
                    placeholder = if (isLiquid) "Örn: 1 Bardak" else "Örn: 1 Porsiyon",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                    modifier = Modifier.weight(1.2f),
                )
                NutraTextField(
                    value = draft.unitGrams,
                    onValueChange = {
                        onDraftChange(draft.copy(unitGrams = NutraFormatters.sanitizeNumericInput(it, allowDecimal = true)))
                    },
                    label = if (isLiquid) "Hacim" else "Gramaj",
                    placeholder = if (isLiquid) "200" else "100",
                    suffix = unitSuffix,
                    isError = isGramsInvalid,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                    modifier = Modifier
                        .weight(0.8f)
                        .then(
                            if (isGramsInvalid) {
                                Modifier.semantics { error("Porsiyon miktarı 1 – 5.000 $unitSuffix arasında olmalıdır") }
                            } else {
                                Modifier
                            },
                        ),
                )
            }

            if (isGramsInvalid) {
                Text(
                    text = "${if (isLiquid) "Hacim" else "Gramaj"} 1 – 5.000 $unitSuffix arasında olmalıdır",
                    style = NutraTheme.typography.caption,
                    color = colors.dangerText,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            NutraTextField(
                value = draft.calories,
                onValueChange = {
                    onDraftChange(draft.copy(calories = NutraFormatters.sanitizeNumericInput(it, allowDecimal = false)))
                },
                label = "Kalori (bu porsiyon için)",
                placeholder = "240",
                suffix = "kcal",
                isError = isCaloriesInvalid || exceedsCalorieDensity,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                modifier = if (isCaloriesInvalid) {
                    Modifier.semantics { error("Kalori 0 – 20.000 kcal arasında olmalıdır") }
                } else if (exceedsCalorieDensity) {
                    Modifier.semantics { error("100 $unitSuffix başına 950 kcal sınırı aşıldı") }
                } else {
                    Modifier
                },
            )

            if (isCaloriesInvalid) {
                Text(
                    text = "Kalori 0 – 20.000 kcal arasında olmalıdır",
                    style = NutraTheme.typography.caption,
                    color = colors.dangerText,
                    modifier = Modifier.padding(start = 4.dp),
                )
            } else if (exceedsCalorieDensity) {
                Text(
                    text = "100 $unitSuffix başına 950 kcal sınırını aşıyor; kalori veya porsiyon miktarını kontrol et.",
                    style = NutraTheme.typography.caption,
                    color = colors.dangerText,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NutraTextField(
                    value = draft.protein,
                    onValueChange = {
                        onDraftChange(draft.copy(protein = NutraFormatters.sanitizeNumericInput(it, allowDecimal = true)))
                    },
                    label = "Protein",
                    placeholder = "0",
                    suffix = "g",
                    compact = true,
                    isError = exceedsServingMass,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                    modifier = Modifier.weight(1f),
                )
                NutraTextField(
                    value = draft.carbs,
                    onValueChange = {
                        onDraftChange(draft.copy(carbs = NutraFormatters.sanitizeNumericInput(it, allowDecimal = true)))
                    },
                    label = "Karb.",
                    placeholder = "0",
                    suffix = "g",
                    compact = true,
                    isError = exceedsServingMass,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                    modifier = Modifier.weight(1f),
                )
                NutraTextField(
                    value = draft.fat,
                    onValueChange = {
                        onDraftChange(draft.copy(fat = NutraFormatters.sanitizeNumericInput(it, allowDecimal = true)))
                    },
                    label = "Yağ",
                    placeholder = "0",
                    suffix = "g",
                    compact = true,
                    isError = exceedsServingMass,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            keyboardController?.hide()
                            if (isFormValid) submitCustomFood()
                        },
                    ),
                    modifier = Modifier.weight(1f),
                )
            }

            if (exceedsServingMass) {
                Text(
                    text = "Protein + karbonhidrat + yağ toplamı (${NutraFormatters.formatMacroGrams(macroMassSum)} g), porsiyon miktarını aşamaz.",
                    style = NutraTheme.typography.caption,
                    color = colors.dangerText,
                    modifier = Modifier.padding(start = 4.dp),
                )
            } else if (hasCalorieMacroMismatch) {
                Text(
                    text = "Girilen kalori, makro toplamından (~${NutraFormatters.formatInt(macroEstimatedCalories)} kcal) belirgin farklı görünüyor.",
                    style = NutraTheme.typography.caption,
                    color = colors.warningText,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            if (macroEstimatedCalories > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Makro enerjisi: ~${NutraFormatters.formatInt(macroEstimatedCalories)} kcal",
                        style = NutraTheme.typography.caption,
                        color = colors.textTertiary,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 4.dp, end = 8.dp),
                    )
                    if (parsedCalories != macroEstimatedCalories && macroEstimatedCalories in 0..20000) {
                        Box(
                            modifier = Modifier
                                .heightIn(min = 40.dp)
                                .clip(NutraTheme.shapes.pill)
                                .background(colors.accentSoft)
                                .clickable(role = Role.Button) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onDraftChange(draft.copy(calories = macroEstimatedCalories.toString()))
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Kaloriye uygula",
                                style = NutraTheme.typography.labelSmall,
                                color = colors.onAccentSoft,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }

        NutraDivider()
        NutraBottomActionBar(includeNavigationInsets = false) {
            NutraButton(
                text = "Kaydet ve ${mealType.toDativeTurkishLabel()} ekle",
                onClick = ::submitCustomFood,
                style = NutraButtonStyle.Primary,
                size = NutraButtonSize.Medium,
                enabled = isFormValid,
                hapticFeedback = HapticFeedbackType.Confirm,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
