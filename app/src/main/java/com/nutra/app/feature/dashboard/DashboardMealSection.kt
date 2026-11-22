package com.nutra.app.feature.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nutra.app.core.designsystem.NutraButton
import com.nutra.app.core.designsystem.NutraButtonSize
import com.nutra.app.core.designsystem.NutraButtonStyle
import com.nutra.app.core.designsystem.NutraDialog
import com.nutra.app.core.designsystem.NutraDivider
import com.nutra.app.core.designsystem.NutraDot
import com.nutra.app.core.designsystem.NutraFormatters
import com.nutra.app.core.designsystem.NutraIconBadge
import com.nutra.app.core.designsystem.NutraIconButton
import com.nutra.app.core.designsystem.NutraIcons
import com.nutra.app.core.designsystem.NutraNumericInputDialog
import com.nutra.app.core.designsystem.NutraStepperButton
import com.nutra.app.core.designsystem.NutraTheme
import com.nutra.app.domain.model.MealLogItem
import com.nutra.app.domain.model.MealType
import com.nutra.app.domain.model.ServingUnitLabels
import kotlin.math.roundToInt

internal val MealCardTopShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
internal val MealCardBottomShape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)

private val EDIT_RAW_GRAM_PRESETS = listOf(50f, 100f, 150f, 200f)
private val EDIT_MULT_PRESETS = listOf(0.5f, 1f, 1.5f, 2f)

@Composable
internal fun MealCardHeaderRow(
    mealType: MealType,
    itemCount: Int,
    totalCalories: Int,
    isExpanded: Boolean,
    addBottomGap: Boolean,
    onToggleExpand: () -> Unit,
    onAddFoodClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val hasItems = itemCount > 0
    val mealIcon = when (mealType) {
        MealType.BREAKFAST -> NutraIcons.WbSunny
        MealType.LUNCH -> NutraIcons.Restaurant
        MealType.DINNER -> NutraIcons.DinnerDining
        MealType.SNACK -> NutraIcons.LocalCafe
    }
    val chevronRotationState = animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(200),
        label = "mealChevron",
    )
    val cardShape = if (isExpanded) MealCardTopShape else NutraTheme.shapes.lg

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (addBottomGap) Modifier.padding(bottom = NutraTheme.spacing.md) else Modifier)
            .clip(cardShape)
            .background(NutraTheme.colors.surface)
            .padding(
                start = NutraTheme.spacing.card,
                end = NutraTheme.spacing.card,
                top = NutraTheme.spacing.lg,
                bottom = if (isExpanded) 14.dp else NutraTheme.spacing.lg,
            ),
    ) {
        // Flattened siblings: left header area toggles expand (or opens add when empty), right "+" button is a sibling (never nested clickable)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(NutraTheme.shapes.xs)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = LocalIndication.current,
                        role = Role.Button,
                        onClickLabel = if (hasItems) {
                            if (isExpanded) "${mealType.title} öğününü daralt" else "${mealType.title} öğününü genişlet"
                        } else {
                            "${mealType.title} için besin ekle"
                        },
                    ) {
                        if (hasItems) {
                            onToggleExpand()
                        } else {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onAddFoodClick()
                        }
                    }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                NutraIconBadge(
                    icon = mealIcon,
                    tint = NutraTheme.colors.textPrimary,
                    container = NutraTheme.colors.surfaceMuted,
                    size = 38.dp,
                    iconSize = 19.dp,
                    shape = NutraTheme.shapes.xs,
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = mealType.title,
                            style = NutraTheme.typography.titleSmall,
                            color = NutraTheme.colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (hasItems) {
                            Icon(
                                imageVector = NutraIcons.ExpandMore,
                                contentDescription = null,
                                tint = NutraTheme.colors.textTertiary,
                                modifier = Modifier
                                    .size(18.dp)
                                    .graphicsLayer { rotationZ = chevronRotationState.value },
                            )
                        }
                    }

                    Text(
                        text = if (hasItems) {
                            "$itemCount besin"
                        } else {
                            "Henüz eklenmedi"
                        },
                        style = NutraTheme.typography.caption,
                        color = if (hasItems) NutraTheme.colors.textSecondary else NutraTheme.colors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (hasItems) {
                    Text(
                        text = "${NutraFormatters.formatInt(totalCalories)} kcal",
                        style = NutraTheme.typography.numberSmall,
                        color = NutraTheme.colors.textPrimary,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }

            NutraIconButton(
                icon = Icons.Rounded.Add,
                contentDescription = "${mealType.title} besin ekle",
                onClick = onAddFoodClick,
                size = 44.dp,
                iconSize = 20.dp,
                containerColor = NutraTheme.colors.surfaceMuted,
                contentColor = NutraTheme.colors.textPrimary,
            )
        }
    }
}

@Composable
internal fun MealLogLazyRowContainer(
    item: MealLogItem,
    isFirst: Boolean,
    isLast: Boolean,
    addBottomCardGap: Boolean,
    onEdit: (MealLogItem) -> Unit,
    onDelete: (MealLogItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismissState = rememberSwipeToDismissBoxState()
    var dismissDispatched by remember(item.id, item.timestampMillis) { mutableStateOf(false) }
    val macroSummary = remember(
        item.id,
        item.formattedQuantity,
        item.proteinGrams,
        item.carbsGrams,
        item.fatGrams,
    ) {
        "${item.formattedQuantity} · P ${item.proteinGrams.roundToInt()}g · K ${item.carbsGrams.roundToInt()}g · Y ${item.fatGrams.roundToInt()}g"
    }
    val accessibleSummary = remember(
        item.id,
        item.foodName,
        item.formattedQuantity,
        item.calories,
        item.proteinGrams,
        item.carbsGrams,
        item.fatGrams,
    ) {
        "${item.foodName}, ${item.formattedQuantity}, ${item.calories} kilokalori. Protein ${item.proteinGrams.roundToInt()} gram, Karbonhidrat ${item.carbsGrams.roundToInt()} gram, Yağ ${item.fatGrams.roundToInt()} gram."
    }
    val caloriesText = remember(item.calories) {
        "${NutraFormatters.formatInt(item.calories)} kcal"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (addBottomCardGap) Modifier.padding(bottom = NutraTheme.spacing.md) else Modifier)
            .then(if (isLast) Modifier.clip(MealCardBottomShape) else Modifier)
            .background(NutraTheme.colors.surface)
            .padding(
                start = NutraTheme.spacing.card,
                end = NutraTheme.spacing.card,
                bottom = if (isLast) NutraTheme.spacing.lg else 0.dp,
            ),
    ) {
        if (isFirst) {
            NutraDivider()
        }
        SwipeToDismissBox(
            state = dismissState,
            enableDismissFromStartToEnd = false,
            onDismiss = { value ->
                if (value == SwipeToDismissBoxValue.EndToStart && !dismissDispatched) {
                    dismissDispatched = true
                    onDelete(item)
                }
            },
            backgroundContent = {
                val isSwipingToDelete =
                    dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart ||
                        dismissState.targetValue == SwipeToDismissBoxValue.EndToStart
                if (isSwipingToDelete) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(NutraTheme.shapes.xs)
                            .background(NutraTheme.colors.dangerSoft)
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Icon(
                            imageVector = NutraIcons.DeleteOutline,
                            contentDescription = "Sil",
                            tint = NutraTheme.colors.dangerText,
                            modifier = Modifier.graphicsLayer {
                                val p = dismissState.progress.coerceIn(0f, 1f)
                                val scale = 0.8f + (0.3f * p)
                                scaleX = scale
                                scaleY = scale
                                alpha = 0.45f + (0.55f * p)
                            },
                        )
                    }
                }
            },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clip(NutraTheme.shapes.xs)
                    .background(NutraTheme.colors.surface)
                    .semantics(mergeDescendants = true) {
                        contentDescription = accessibleSummary
                        customActions = listOf(
                            CustomAccessibilityAction(label = "Porsiyonu düzenle") {
                                onEdit(item)
                                true
                            },
                            CustomAccessibilityAction(label = "Kaydı sil") {
                                onDelete(item)
                                true
                            },
                        )
                    }
                    .clickable(role = Role.Button, onClickLabel = "Porsiyonu düzenle") { onEdit(item) }
                    .padding(horizontal = 4.dp, vertical = 12.dp),
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
                        text = item.foodName,
                        style = NutraTheme.typography.titleSmall,
                        color = NutraTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = macroSummary,
                        style = NutraTheme.typography.caption,
                        color = NutraTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = caloriesText,
                    style = NutraTheme.typography.numberSmall,
                    color = NutraTheme.colors.textPrimary,
                )
            }
        }
        if (!isLast) {
            NutraDivider()
        }
    }
}

@Composable
internal fun EditLoggedFoodPortionDialog(
    item: MealLogItem,
    onDismiss: () -> Unit,
    onUpdateMultiplier: (Float) -> Unit,
    onDelete: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val colors = NutraTheme.colors
    val unitSuffix = ServingUnitLabels.normalizedBaseUnit(item.baseUnit)
    val isLiquid = unitSuffix == "ml"
    val isRawGramMode = ServingUnitLabels.isRawUnitLabel(item.unitLabel)
    var multiplier by remember(item) {
        mutableFloatStateOf(
            if (isRawGramMode) item.totalGrams.coerceIn(1f, 3000f) else item.multiplier.coerceIn(0.25f, 20f),
        )
    }
    var showManualInputDialog by remember(item) { mutableStateOf(false) }

    val ratio = if (isRawGramMode) {
        multiplier / item.totalGrams.coerceAtLeast(1f)
    } else {
        multiplier / item.multiplier.coerceAtLeast(0.05f)
    }
    val previewCalories = (item.calories * ratio).roundToInt()
    val previewProtein = item.proteinGrams * ratio
    val previewCarbs = item.carbsGrams * ratio
    val previewFat = item.fatGrams * ratio
    val previewGrams = if (isRawGramMode) {
        multiplier.roundToInt()
    } else {
        (item.totalGrams * ratio).roundToInt()
    }
    val valueLabel = if (isRawGramMode) {
        "${NutraFormatters.formatInt(multiplier.roundToInt())} $unitSuffix"
    } else {
        "${NutraFormatters.formatMultiplier(multiplier)}x"
    }
    val presets = if (isRawGramMode) EDIT_RAW_GRAM_PRESETS else EDIT_MULT_PRESETS

    NutraDialog(
        onDismissRequest = onDismiss,
        title = item.foodName,
        message = if (isRawGramMode) {
            "${if (isLiquid) "Hacim" else "Gramaj"} · ${NutraFormatters.formatInt(previewGrams)} $unitSuffix"
        } else {
            "${item.unitLabel} · ${NutraFormatters.formatInt(previewGrams)} $unitSuffix"
        },
        confirmText = null,
        dismissText = null,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "${NutraFormatters.formatInt(previewCalories)} kcal",
                    style = NutraTheme.typography.numberLarge,
                    color = colors.accent,
                    textAlign = TextAlign.Center,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    EditPortionMacroChip("P", "${NutraFormatters.formatMacroGrams(previewProtein)}g", colors.protein)
                    EditPortionMacroChip("K", "${NutraFormatters.formatMacroGrams(previewCarbs)}g", colors.carbs)
                    EditPortionMacroChip("Y", "${NutraFormatters.formatMacroGrams(previewFat)}g", colors.fat)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                presets.forEach { preset ->
                    val isSelected = multiplier == preset
                    val label = if (isRawGramMode) {
                        "${preset.toInt()} $unitSuffix"
                    } else {
                        "${NutraFormatters.formatMultiplier(preset)}x"
                    }
                    val bgState = animateColorAsState(
                        targetValue = if (isSelected) colors.ink else colors.surfaceMuted,
                        animationSpec = tween(160),
                        label = "editPortionPillBg",
                    )
                    val fg by animateColorAsState(
                        targetValue = if (isSelected) colors.onInk else colors.textPrimary,
                        animationSpec = tween(160),
                        label = "editPortionPillFg",
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 40.dp)
                            .clip(NutraTheme.shapes.pill)
                            .drawBehind { drawRect(bgState.value) }
                            .selectable(selected = isSelected, role = Role.RadioButton) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                multiplier = preset
                            }
                            .padding(horizontal = 6.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style = NutraTheme.typography.labelSmall,
                            color = fg,
                            maxLines = 1,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(NutraTheme.shapes.pill)
                    .background(colors.surfaceMuted)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NutraStepperButton(
                    icon = NutraIcons.Remove,
                    contentDescription = if (isRawGramMode) "10 $unitSuffix azalt" else "Porsiyon azalt",
                    onClick = {
                        multiplier = if (isRawGramMode) {
                            (multiplier - 10f).coerceAtLeast(1f)
                        } else {
                            (multiplier - 0.25f).coerceAtLeast(0.25f)
                        }
                    },
                    containerColor = colors.surfaceElevated,
                )
                Row(
                    modifier = Modifier
                        .clip(NutraTheme.shapes.pill)
                        .background(colors.surfaceElevated)
                        .border(1.dp, colors.divider, NutraTheme.shapes.pill)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = if (isRawGramMode) {
                                if (isLiquid) "Hacim yaz" else "Gramaj yaz"
                            } else {
                                "Porsiyon çarpanı yaz"
                            },
                        ) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showManualInputDialog = true
                        }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = valueLabel,
                        style = NutraTheme.typography.numberMedium,
                        color = colors.textPrimary,
                    )
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = null,
                        tint = colors.textTertiary,
                        modifier = Modifier.size(14.dp),
                    )
                }
                NutraStepperButton(
                    icon = Icons.Rounded.Add,
                    contentDescription = if (isRawGramMode) "10 $unitSuffix artır" else "Porsiyon artır",
                    onClick = {
                        multiplier = if (isRawGramMode) {
                            (multiplier + 10f).coerceAtMost(3000f)
                        } else {
                            (multiplier + 0.25f).coerceAtMost(20f)
                        }
                    },
                    containerColor = colors.surfaceElevated,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                NutraButton(
                    text = "Sil",
                    onClick = onDelete,
                    style = NutraButtonStyle.Destructive,
                    size = NutraButtonSize.Medium,
                    hapticFeedback = HapticFeedbackType.LongPress,
                    modifier = Modifier.weight(0.42f),
                )
                NutraButton(
                    text = "Güncelle",
                    onClick = { onUpdateMultiplier(multiplier) },
                    style = NutraButtonStyle.Primary,
                    size = NutraButtonSize.Medium,
                    hapticFeedback = HapticFeedbackType.Confirm,
                    modifier = Modifier.weight(0.58f),
                )
            }
        }
    }

    if (showManualInputDialog) {
        if (isRawGramMode) {
            NutraNumericInputDialog(
                title = if (isLiquid) "Hacim" else "Gramaj",
                initialValue = multiplier.roundToInt().toString(),
                unit = unitSuffix,
                rangeHint = "1 – 3.000 $unitSuffix",
                minValue = 1.0,
                maxValue = 3000.0,
                allowDecimal = true,
                onDismiss = { showManualInputDialog = false },
                onConfirm = { entered ->
                    multiplier = entered.toFloat().coerceIn(1f, 3000f)
                    showManualInputDialog = false
                },
            )
        } else {
            NutraNumericInputDialog(
                title = "Porsiyon miktarı",
                initialValue = NutraFormatters.formatMultiplier(multiplier),
                unit = "×",
                rangeHint = "0,25 – 20",
                minValue = 0.25,
                maxValue = 20.0,
                allowDecimal = true,
                onDismiss = { showManualInputDialog = false },
                onConfirm = { entered ->
                    multiplier = ((entered * 100.0).roundToInt() / 100f).coerceIn(0.25f, 20f)
                    showManualInputDialog = false
                },
            )
        }
    }
}

@Composable
private fun EditPortionMacroChip(
    label: String,
    value: String,
    color: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        NutraDot(color = color, size = 6.dp)
        Text(
            text = "$label $value",
            style = NutraTheme.typography.caption,
            color = NutraTheme.colors.textSecondary,
        )
    }
}
