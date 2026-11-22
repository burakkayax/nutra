package com.nutra.app.feature.logging

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nutra.app.core.designsystem.NutraBottomActionBar
import com.nutra.app.core.designsystem.NutraButton
import com.nutra.app.core.designsystem.NutraButtonSize
import com.nutra.app.core.designsystem.NutraButtonStyle
import com.nutra.app.core.designsystem.NutraCard
import com.nutra.app.core.designsystem.NutraDivider
import com.nutra.app.core.designsystem.NutraDot
import com.nutra.app.core.designsystem.NutraFormatters
import com.nutra.app.core.designsystem.NutraIconButton
import com.nutra.app.core.designsystem.NutraSelectionIndicator
import com.nutra.app.core.designsystem.NutraTextField
import com.nutra.app.core.designsystem.NutraTheme
import com.nutra.app.domain.model.FoodItem
import com.nutra.app.domain.model.ServingUnitLabels
import kotlin.math.roundToInt

private val PORTION_GRAM_PRESETS = listOf(50, 100, 150, 200)
private val PORTION_MULT_PRESETS = listOf(0.5f, 1f, 1.5f, 2f, 3f)

@Composable
internal fun PortionPickerContent(
    food: FoodItem,
    hasStrictDietaryGuardrail: Boolean,
    onToggleFavorite: () -> Unit,
    onConfirm: (unitLabel: String, unitGrams: Float, multiplier: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val colors = NutraTheme.colors
    val unitSuffix = food.displayUnitSuffix
    val units = remember(food.id, food.defaultUnitLabel, food.defaultUnitGrams, food.alternateUnits, food.baseUnit) {
        food.allUnits
    }
    var selectedUnitIndex by remember(food.id) { mutableIntStateOf(0) }
    var multiplier by remember(food.id) { mutableFloatStateOf(1f) }
    var customGramInput by remember(food.id) {
        val initialIsRaw = units.firstOrNull()?.let { ServingUnitLabels.isRawUnitLabel(it.label) } == true
        mutableStateOf(
            if (initialIsRaw) food.defaultUnitGrams.roundToInt().coerceIn(1, 3000).toString() else "",
        )
    }
    var detailsExpanded by remember(food.id) { mutableStateOf(false) }

    val selectedUnit = units.getOrElse(selectedUnitIndex) { units.first() }
    val isRawGramMode = ServingUnitLabels.isRawUnitLabel(selectedUnit.label)
    val parsedCustomGrams = NutraFormatters.parseLocalizedFloat(customGramInput)
    val isCustomGramValid = !isRawGramMode || (parsedCustomGrams != null && parsedCustomGrams in 1f..3000f)
    val showRawGramError = isRawGramMode && customGramInput.isNotEmpty() && !isCustomGramValid

    val effectiveGrams = if (isRawGramMode) {
        if (isCustomGramValid) parsedCustomGrams!!.coerceIn(1f, 3000f) else 0f
    } else {
        (selectedUnit.grams * multiplier).coerceAtLeast(1f)
    }

    val liveCalories = if (effectiveGrams > 0f) food.caloriesForGrams(effectiveGrams) else 0
    val liveProtein = if (effectiveGrams > 0f) food.proteinForGrams(effectiveGrams) else 0f
    val liveCarbs = if (effectiveGrams > 0f) food.carbsForGrams(effectiveGrams) else 0f
    val liveFat = if (effectiveGrams > 0f) food.fatForGrams(effectiveGrams) else 0f

    Column(modifier = modifier) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = NutraTheme.spacing.screen,
                    end = NutraTheme.spacing.screen,
                    bottom = 16.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (hasStrictDietaryGuardrail && food.isCustom && !food.allergenVerified) {
                NutraCard(
                    color = colors.surfaceMuted,
                    shape = NutraTheme.shapes.md,
                    contentPadding = PaddingValues(12.dp),
                ) {
                    Text(
                        text = "Bu özel besinin alerjen ve diyet etiketi doğrulanmamıştır; eklemeden önce içeriğini kendin kontrol et.",
                        style = NutraTheme.typography.caption,
                        color = colors.warningText,
                    )
                }
            }

            // Food header + live calorie hero
            NutraCard(
                color = sheetCardContainerColor(),
                shape = NutraTheme.shapes.lg,
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = food.name,
                            style = NutraTheme.typography.titleMedium,
                            color = colors.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = food.subtitle,
                            style = NutraTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    NutraIconButton(
                        icon = if (food.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (food.isFavorite) "Favorilerden çıkar" else "Favorilere ekle",
                        onClick = onToggleFavorite,
                        size = 44.dp,
                        containerColor = if (colors.isDark) colors.segmentThumb else colors.surface,
                        contentColor = if (food.isFavorite) colors.favorite else colors.textTertiary,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = if (isCustomGramValid) NutraFormatters.formatInt(liveCalories) else "—",
                        style = NutraTheme.typography.display,
                        color = if (isCustomGramValid) colors.textPrimary else colors.textTertiary,
                        modifier = Modifier.alignByBaseline(),
                    )
                    Text(
                        text = "kcal",
                        style = NutraTheme.typography.titleMedium,
                        color = colors.textSecondary,
                        modifier = Modifier.alignByBaseline(),
                    )
                }

                NutraDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    MacroDataColumn("Protein", "${NutraFormatters.formatMacroGrams(liveProtein)} g", colors.protein)
                    MacroDataColumn("Karb.", "${NutraFormatters.formatMacroGrams(liveCarbs)} g", colors.carbs)
                    MacroDataColumn("Yağ", "${NutraFormatters.formatMacroGrams(liveFat)} g", colors.fat)
                }
            }

            // Serving unit selector
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Porsiyon birimi",
                    style = NutraTheme.typography.titleSmall,
                    color = colors.textPrimary,
                )

                NutraCard(
                    color = sheetCardContainerColor(),
                    shape = NutraTheme.shapes.md,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    units.forEachIndexed { idx, unit ->
                        val isSelected = idx == selectedUnitIndex
                        val isRawUnit = ServingUnitLabels.isRawUnitLabel(unit.label)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(selected = isSelected, role = Role.RadioButton) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedUnitIndex = idx
                                    customGramInput = if (isRawUnit) {
                                        food.defaultUnitGrams.roundToInt().toString()
                                    } else {
                                        ""
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            NutraSelectionIndicator(
                                selected = isSelected,
                                size = 20.dp,
                                iconSize = 13.dp,
                            )

                            Text(
                                text = if (isRawUnit) {
                                    if (food.isLiquid) "Özel hacim" else "Özel gramaj"
                                } else {
                                    unit.label
                                },
                                style = NutraTheme.typography.titleSmall,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )

                            Text(
                                text = if (isRawUnit) {
                                    "Serbest giriş"
                                } else {
                                    "${NutraFormatters.formatInt(unit.grams.roundToInt())} $unitSuffix · ${NutraFormatters.formatInt(food.caloriesForGrams(unit.grams))} kcal"
                                },
                                style = NutraTheme.typography.caption,
                                color = if (isSelected) colors.textPrimary else colors.textSecondary,
                            )
                        }
                        if (idx < units.lastIndex) {
                            NutraDivider(startIndent = 48.dp, endIndent = 16.dp)
                        }
                    }
                }
            }

            // Quantity control
            if (isRawGramMode) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PORTION_GRAM_PRESETS.forEach { gramPreset ->
                            CompactMultiplierPill(
                                text = "$gramPreset $unitSuffix",
                                selected = parsedCustomGrams?.roundToInt() == gramPreset,
                                onClick = { customGramInput = gramPreset.toString() },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    NutraTextField(
                        value = customGramInput,
                        onValueChange = {
                            customGramInput = NutraFormatters.sanitizeNumericInput(it, allowDecimal = true)
                        },
                        label = if (food.isLiquid) "Hacim" else "Gramaj",
                        placeholder = "100",
                        suffix = unitSuffix,
                        isError = showRawGramError,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { keyboardController?.hide() },
                        ),
                        modifier = if (showRawGramError) {
                            Modifier.semantics { error("1 – 3.000 $unitSuffix arası bir değer gir") }
                        } else {
                            Modifier
                        },
                    )
                    if (showRawGramError) {
                        Text(
                            text = "1 – 3.000 $unitSuffix arası bir değer gir",
                            style = NutraTheme.typography.caption,
                            color = colors.dangerText,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Miktar",
                            style = NutraTheme.typography.titleSmall,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = "${NutraFormatters.formatInt(effectiveGrams.roundToInt())} $unitSuffix",
                            style = NutraTheme.typography.caption,
                            color = colors.textSecondary,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PORTION_MULT_PRESETS.forEach { preset ->
                            val label = "${NutraFormatters.formatMultiplier(preset)}x"
                            CompactMultiplierPill(
                                text = label,
                                selected = multiplier == preset,
                                onClick = { multiplier = preset },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            if (food.hasExtendedDetails) {
                ExtendedNutritionDetailsCard(
                    food = food,
                    effectiveAmount = effectiveGrams,
                    isAmountValid = isCustomGramValid && effectiveGrams > 0f,
                    expanded = detailsExpanded,
                    onToggleExpanded = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        detailsExpanded = !detailsExpanded
                    },
                )
            }
        }

        NutraDivider()
        NutraBottomActionBar(includeNavigationInsets = false) {
            NutraButton(
                text = if (isCustomGramValid) {
                    "Ekle · ${NutraFormatters.formatInt(liveCalories)} kcal"
                } else if (food.isLiquid) {
                    "Hacim gir"
                } else {
                    "Gramaj gir"
                },
                onClick = {
                    if (isRawGramMode) {
                        onConfirm(ServingUnitLabels.rawUnitFor(food.baseUnit), 1f, effectiveGrams)
                    } else {
                        onConfirm(selectedUnit.label, selectedUnit.grams, multiplier)
                    }
                },
                style = NutraButtonStyle.Primary,
                size = NutraButtonSize.Medium,
                leadingIcon = if (isCustomGramValid) Icons.Rounded.Check else null,
                enabled = isCustomGramValid,
                hapticFeedback = HapticFeedbackType.Confirm,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ExtendedNutritionDetailsCard(
    food: FoodItem,
    effectiveAmount: Float,
    isAmountValid: Boolean,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
) {
    val arrowRotationState = animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(180),
        label = "extendedNutritionChevron",
    )

    val rows = remember(food, effectiveAmount, isAmountValid) {
        buildList {
            fun addIfPresent(label: String, per100: Float?, unit: String) {
                if (per100 == null) return
                val formatted = if (isAmountValid) {
                    val scaled = food.scaledNutrient(per100, effectiveAmount) ?: 0f
                    "${NutraFormatters.formatMacroGrams(scaled)} $unit"
                } else {
                    "—"
                }
                add(label to formatted)
            }
            addIfPresent("Lif", food.fiberPer100g, "g")
            addIfPresent("Şeker", food.sugarPer100g, "g")
            addIfPresent("Doymuş yağ", food.saturatedFatPer100g, "g")
            addIfPresent("Tuz", food.saltPer100g, "g")
            addIfPresent("Sodyum", food.sodiumMgPer100g, "mg")
            addIfPresent("Kalsiyum", food.calciumMgPer100g, "mg")
            addIfPresent("Potasyum", food.potassiumMgPer100g, "mg")
            addIfPresent("Demir", food.ironMgPer100g, "mg")
            addIfPresent("C vitamini", food.vitaminCMgPer100g, "mg")
        }
    }

    if (rows.isEmpty()) return

    val previewLine = remember(rows) {
        rows.take(3).joinToString(" · ") { (label, value) -> "$label $value" }
    }

    NutraCard(
        color = sheetCardContainerColor(),
        shape = NutraTheme.shapes.md,
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    role = Role.Button,
                    onClickLabel = if (expanded) "Besin detaylarını gizle" else "Besin detaylarını göster",
                    onClick = onToggleExpanded,
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Besin detayları",
                    style = NutraTheme.typography.titleSmall,
                    color = NutraTheme.colors.textPrimary,
                )
                if (!expanded) {
                    Text(
                        text = previewLine,
                        style = NutraTheme.typography.caption,
                        color = NutraTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = NutraTheme.colors.textSecondary,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer { rotationZ = arrowRotationState.value },
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(200)) + fadeIn(tween(160)),
            exit = shrinkVertically(tween(160)) + fadeOut(tween(120)),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                NutraDivider(startIndent = 16.dp, endIndent = 16.dp)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rows.chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            pair.forEach { (label, value) ->
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = label,
                                        style = NutraTheme.typography.caption,
                                        color = NutraTheme.colors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        text = value,
                                        style = NutraTheme.typography.labelSmall,
                                        color = NutraTheme.colors.textPrimary,
                                        maxLines = 1,
                                    )
                                }
                            }
                            if (pair.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MacroDataColumn(
    label: String,
    value: String,
    color: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            NutraDot(color = color, size = 7.dp)
            Text(
                text = label,
                style = NutraTheme.typography.caption,
                color = NutraTheme.colors.textSecondary,
            )
        }
        Text(
            text = value,
            style = NutraTheme.typography.numberMedium,
            color = NutraTheme.colors.textPrimary,
        )
    }
}

@Composable
private fun CompactMultiplierPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    unselectedColor: Color = NutraTheme.colors.surfaceMuted,
) {
    val haptic = LocalHapticFeedback.current
    val bgState = animateColorAsState(
        targetValue = if (selected) NutraTheme.colors.ink else unselectedColor,
        animationSpec = tween(150),
        label = "multBg",
    )
    val fg by animateColorAsState(
        targetValue = if (selected) NutraTheme.colors.onInk else NutraTheme.colors.textSecondary,
        animationSpec = tween(150),
        label = "multFg",
    )
    Box(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(NutraTheme.shapes.pill)
            .drawBehind { drawRect(bgState.value) }
            .selectable(selected = selected, role = Role.Tab) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 4.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = NutraTheme.typography.labelSmall,
            color = fg,
            maxLines = 1,
        )
    }
}
