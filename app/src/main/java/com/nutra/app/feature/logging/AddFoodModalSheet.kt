package com.nutra.app.feature.logging

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nutra.app.core.designsystem.NutraBadge
import com.nutra.app.core.designsystem.NutraButton
import com.nutra.app.core.designsystem.NutraButtonSize
import com.nutra.app.core.designsystem.NutraButtonStyle
import com.nutra.app.core.designsystem.NutraCard
import com.nutra.app.core.designsystem.NutraChip
import com.nutra.app.core.designsystem.NutraDivider
import com.nutra.app.core.designsystem.NutraDot
import com.nutra.app.core.designsystem.NutraEmptyState
import com.nutra.app.core.designsystem.NutraIconBadge
import com.nutra.app.core.designsystem.NutraIconButton
import com.nutra.app.core.designsystem.NutraModalSheet
import com.nutra.app.core.designsystem.NutraSectionHeader
import com.nutra.app.core.designsystem.NutraSegmentedControl
import com.nutra.app.core.designsystem.NutraStepperButton
import com.nutra.app.core.designsystem.NutraTextField
import com.nutra.app.core.designsystem.NutraTheme
import com.nutra.app.domain.model.FoodCategory
import com.nutra.app.domain.model.FoodItem
import com.nutra.app.domain.model.MealType
import com.nutra.app.domain.model.SmartComboSuggestion
import com.nutra.app.feature.dashboard.DashboardUiState
import com.nutra.app.feature.dashboard.LoggingTabMode
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

private val TR_LOCALE = Locale.forLanguageTag("tr-TR")

private fun formatInt(value: Int): String =
    NumberFormat.getIntegerInstance(TR_LOCALE).format(value)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFoodModalSheet(
    state: DashboardUiState,
    onDismiss: () -> Unit,
    onSelectMealType: (MealType) -> Unit,
    onSelectTab: (LoggingTabMode) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSelectCategory: (FoodCategory) -> Unit,
    onSelectFoodForPortion: (FoodItem) -> Unit,
    onClosePortionPicker: () -> Unit,
    onToggleFavorite: (FoodItem) -> Unit,
    onConfirmLogFood: (FoodItem, String, Float, Float) -> Unit,
    onLogSmartCombo: (SmartComboSuggestion) -> Unit,
    onAiPromptChange: (String) -> Unit,
    onRunAiAnalysis: (ByteArray?) -> Unit,
    onUpdateAiItemMultiplier: (String, Float) -> Unit,
    onToggleAiItemIncluded: (String) -> Unit,
    onCommitAiItems: () -> Unit,
    onBarcodeInputChange: (String) -> Unit,
    onLookupBarcode: (String?) -> Unit,
    onCreateAndLogCustomFood: (String, String, Float, Int, Float, Float, Float) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val activeMeal = state.activeLoggingMealType ?: MealType.BREAKFAST
    val portionFood = state.selectedFoodForPortion

    NutraModalSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            // Sheet header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NutraTheme.spacing.screen, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (portionFood != null) {
                    NutraIconButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Geri",
                        onClick = onClosePortionPicker,
                        size = 40.dp,
                    )
                }

                Text(
                    text = if (portionFood != null) "Porsiyon seç" else "Besin ekle",
                    style = NutraTheme.typography.titleMedium,
                    color = NutraTheme.colors.textPrimary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                NutraIconButton(
                    icon = Icons.Rounded.Close,
                    contentDescription = "Kapat",
                    onClick = onDismiss,
                    size = 40.dp,
                )
            }

            // Meal selector row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = NutraTheme.spacing.screen, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MealType.entries.forEach { meal ->
                    NutraChip(
                        text = meal.title,
                        selected = meal == activeMeal,
                        onClick = { onSelectMealType(meal) },
                    )
                }
            }

            if (portionFood != null) {
                PortionPickerContent(
                    food = portionFood,
                    mealType = activeMeal,
                    onBack = onClosePortionPicker,
                    onToggleFavorite = { onToggleFavorite(portionFood) },
                    onConfirm = { unitLabel, unitGrams, multiplier ->
                        onConfirmLogFood(portionFood, unitLabel, unitGrams, multiplier)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = NutraTheme.spacing.screen, vertical = 4.dp),
                ) {
                    NutraSegmentedControl(
                        items = LoggingTabMode.entries,
                        selectedItem = state.loggingTabMode,
                        onItemSelected = onSelectTab,
                        labelProvider = { it.title },
                    )
                }

                Spacer(Modifier.height(8.dp))

                AnimatedContent(
                    targetState = state.loggingTabMode,
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    label = "LoggingTabTransition",
                ) { tabMode ->
                    when (tabMode) {
                        LoggingTabMode.CATALOG -> CatalogAndSmartComboTab(
                            state = state,
                            activeMeal = activeMeal,
                            onSearchQueryChange = onSearchQueryChange,
                            onSelectCategory = onSelectCategory,
                            onSelectFood = onSelectFoodForPortion,
                            onToggleFavorite = onToggleFavorite,
                            onLogSmartCombo = onLogSmartCombo,
                        )
                        LoggingTabMode.AI_ANALYZE -> AiAnalyzeTab(
                            state = state,
                            mealType = activeMeal,
                            onAiPromptChange = onAiPromptChange,
                            onRunAiAnalysis = onRunAiAnalysis,
                            onUpdateMultiplier = onUpdateAiItemMultiplier,
                            onToggleIncluded = onToggleAiItemIncluded,
                            onCommitItems = onCommitAiItems,
                        )
                        LoggingTabMode.BARCODE -> BarcodeLookupTab(
                            state = state,
                            onBarcodeChange = onBarcodeInputChange,
                            onLookup = onLookupBarcode,
                        )
                        LoggingTabMode.CUSTOM -> CustomFoodCreatorTab(
                            mealType = activeMeal,
                            onCreateAndLog = onCreateAndLogCustomFood,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogAndSmartComboTab(
    state: DashboardUiState,
    activeMeal: MealType,
    onSearchQueryChange: (String) -> Unit,
    onSelectCategory: (FoodCategory) -> Unit,
    onSelectFood: (FoodItem) -> Unit,
    onToggleFavorite: (FoodItem) -> Unit,
    onLogSmartCombo: (SmartComboSuggestion) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val showSmartCombos = state.searchQuery.isBlank() &&
        state.selectedCategory == FoodCategory.ALL &&
        state.smartCombos.isNotEmpty()

    Column(modifier = Modifier.fillMaxSize()) {
        NutraTextField(
            value = state.searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = "Besin, yemek veya marka ara...",
            leadingIcon = Icons.Rounded.Search,
            trailing = if (state.searchQuery.isNotEmpty()) {
                {
                    NutraIconButton(
                        icon = Icons.Rounded.Close,
                        contentDescription = "Temizle",
                        onClick = { onSearchQueryChange("") },
                        size = 32.dp,
                        iconSize = 16.dp,
                        containerColor = NutraTheme.colors.surfaceElevated,
                    )
                }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NutraTheme.spacing.screen),
        )

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = NutraTheme.spacing.screen),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FoodCategory.entries.forEach { cat ->
                NutraChip(
                    text = cat.title,
                    selected = cat == state.selectedCategory,
                    onClick = { onSelectCategory(cat) },
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            if (showSmartCombos) {
                item(key = "smart_combos_header") {
                    NutraSectionHeader(
                        title = "Sık birlikte",
                        modifier = Modifier.padding(
                            start = NutraTheme.spacing.screen,
                            end = NutraTheme.spacing.screen,
                            top = 4.dp,
                            bottom = 10.dp,
                        ),
                    )
                }

                item(key = "smart_combos_row") {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = NutraTheme.spacing.screen),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.smartCombos, key = { it.id }) { combo ->
                            NutraCard(
                                color = NutraTheme.colors.background,
                                shape = NutraTheme.shapes.md,
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.width(244.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = combo.title,
                                        style = NutraTheme.typography.titleSmall,
                                        color = NutraTheme.colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 8.dp),
                                    )
                                    NutraBadge(
                                        text = "${formatInt(combo.totalCalories)} kcal",
                                        color = NutraTheme.colors.accent,
                                        container = NutraTheme.colors.accentSoft,
                                    )
                                }

                                Text(
                                    text = combo.items.joinToString(" · ") { it.foodName },
                                    style = NutraTheme.typography.bodySmall,
                                    color = NutraTheme.colors.textSecondary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )

                                NutraButton(
                                    text = "Beraber ekle",
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                        onLogSmartCombo(combo)
                                    },
                                    style = NutraButtonStyle.Primary,
                                    size = NutraButtonSize.Small,
                                    leadingIcon = Icons.Rounded.Add,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            item(key = "catalog_header") {
                val title = if (state.searchQuery.isBlank() && state.selectedCategory == FoodCategory.ALL) {
                    "${activeMeal.title} için önerilenler"
                } else {
                    "Sonuçlar"
                }
                NutraSectionHeader(
                    title = title,
                    supportingText = "${state.rankedFoods.size} besin",
                    modifier = Modifier.padding(
                        horizontal = NutraTheme.spacing.screen,
                        vertical = 6.dp,
                    ),
                )
            }

            if (state.rankedFoods.isEmpty()) {
                item(key = "empty_catalog") {
                    NutraEmptyState(
                        icon = Icons.Rounded.SearchOff,
                        title = "Eşleşen besin bulunamadı",
                        message = "Farklı bir arama terimi deneyebilir veya Özel sekmesinden yeni besin oluşturabilirsiniz.",
                    )
                }
            } else {
                itemsIndexed(state.rankedFoods, key = { _, item -> item.id }) { index, food ->
                    val defaultCal = food.caloriesForGrams(food.defaultUnitGrams)
                    val defaultP = food.proteinForGrams(food.defaultUnitGrams).roundToInt()
                    val defaultC = food.carbsForGrams(food.defaultUnitGrams).roundToInt()
                    val defaultF = food.fatForGrams(food.defaultUnitGrams).roundToInt()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectFood(food)
                            }
                            .padding(
                                horizontal = NutraTheme.spacing.screen,
                                vertical = 12.dp,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                text = food.name,
                                style = NutraTheme.typography.titleSmall,
                                color = NutraTheme.colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${food.defaultUnitLabel} (${food.defaultUnitGrams.roundToInt()} g) · ${formatInt(defaultCal)} kcal",
                                style = NutraTheme.typography.bodySmall,
                                color = NutraTheme.colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "P ${defaultP}g · K ${defaultC}g · Y ${defaultF}g",
                                style = NutraTheme.typography.caption,
                                color = NutraTheme.colors.textTertiary,
                                maxLines = 1,
                            )
                        }

                        NutraIconButton(
                            icon = if (food.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = "Favori",
                            onClick = { onToggleFavorite(food) },
                            size = 40.dp,
                            iconSize = 18.dp,
                            containerColor = Color.Transparent,
                            contentColor = if (food.isFavorite) NutraTheme.colors.protein else NutraTheme.colors.textTertiary,
                        )

                        NutraIconButton(
                            icon = Icons.Rounded.Add,
                            contentDescription = "${food.name} seç",
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectFood(food)
                            },
                            size = 40.dp,
                            iconSize = 20.dp,
                            containerColor = NutraTheme.colors.surfaceMuted,
                            contentColor = NutraTheme.colors.textPrimary,
                        )
                    }

                    if (index < state.rankedFoods.lastIndex) {
                        NutraDivider(startIndent = NutraTheme.spacing.screen)
                    }
                }
            }
        }
    }
}

@Composable
private fun PortionPickerContent(
    food: FoodItem,
    mealType: MealType,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onConfirm: (unitLabel: String, unitGrams: Float, multiplier: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val units = remember(food) { food.allUnits }
    var selectedUnitIndex by remember(food) { mutableIntStateOf(0) }
    var multiplier by remember(food) { mutableFloatStateOf(1f) }
    var customGramInput by remember(food) { mutableStateOf("") }

    val selectedUnit = units.getOrElse(selectedUnitIndex) { units.first() }
    val isRawGramMode = selectedUnit.label == "1 Gram"

    val effectiveGrams = if (isRawGramMode && customGramInput.isNotBlank()) {
        customGramInput.toFloatOrNull()?.coerceIn(1f, 3000f) ?: (selectedUnit.grams * multiplier)
    } else {
        (selectedUnit.grams * multiplier).coerceAtLeast(1f)
    }

    val liveCalories = food.caloriesForGrams(effectiveGrams)
    val liveProtein = food.proteinForGrams(effectiveGrams)
    val liveCarbs = food.carbsForGrams(effectiveGrams)
    val liveFat = food.fatForGrams(effectiveGrams)

    Column(modifier = modifier) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = NutraTheme.spacing.screen),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // Food header + live calorie hero
            NutraCard(
                color = NutraTheme.colors.background,
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
                            color = NutraTheme.colors.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = food.subtitle,
                            style = NutraTheme.typography.bodySmall,
                            color = NutraTheme.colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    NutraIconButton(
                        icon = if (food.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Favori",
                        onClick = onToggleFavorite,
                        size = 40.dp,
                        containerColor = NutraTheme.colors.surface,
                        contentColor = if (food.isFavorite) NutraTheme.colors.protein else NutraTheme.colors.textTertiary,
                    )
                }

                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = formatInt(liveCalories),
                        style = NutraTheme.typography.display,
                        color = NutraTheme.colors.textPrimary,
                    )
                    Text(
                        text = "kcal",
                        style = NutraTheme.typography.titleMedium,
                        color = NutraTheme.colors.textSecondary,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }

                NutraDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    MacroDataColumn("Protein", "${liveProtein} g", NutraTheme.colors.protein)
                    MacroDataColumn("Karbonhidrat", "${liveCarbs} g", NutraTheme.colors.carbs)
                    MacroDataColumn("Yağ", "${liveFat} g", NutraTheme.colors.fat)
                }
            }

            // Serving unit selector
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Porsiyon birimi",
                    style = NutraTheme.typography.titleSmall,
                    color = NutraTheme.colors.textPrimary,
                )

                NutraCard(
                    color = NutraTheme.colors.background,
                    shape = NutraTheme.shapes.md,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    units.forEachIndexed { idx, unit ->
                        val isSelected = idx == selectedUnitIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.RadioButton) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedUnitIndex = idx
                                    customGramInput = if (unit.label == "1 Gram") {
                                        food.defaultUnitGrams.roundToInt().toString()
                                    } else {
                                        ""
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) NutraTheme.colors.ink else Color.Transparent)
                                    .border(
                                        width = 1.5.dp,
                                        color = if (isSelected) NutraTheme.colors.ink else NutraTheme.colors.divider,
                                        shape = CircleShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = NutraTheme.colors.onInk,
                                        modifier = Modifier.size(13.dp),
                                    )
                                }
                            }

                            Text(
                                text = unit.label,
                                style = NutraTheme.typography.titleSmall,
                                color = NutraTheme.colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )

                            Text(
                                text = "${unit.grams.roundToInt()} g · ${food.caloriesForGrams(unit.grams)} kcal",
                                style = NutraTheme.typography.caption,
                                color = if (isSelected) NutraTheme.colors.textPrimary else NutraTheme.colors.textSecondary,
                            )
                        }
                        if (idx < units.lastIndex) {
                            NutraDivider(startIndent = 48.dp)
                        }
                    }
                }
            }

            // Quantity control
            if (isRawGramMode) {
                NutraTextField(
                    value = customGramInput,
                    onValueChange = { customGramInput = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = "Gramaj",
                    placeholder = "100",
                    suffix = "g",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
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
                            color = NutraTheme.colors.textPrimary,
                        )
                        Text(
                            text = "${effectiveGrams.roundToInt()} g",
                            style = NutraTheme.typography.caption,
                            color = NutraTheme.colors.textSecondary,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(0.5f, 1f, 1.5f, 2f, 3f).forEach { preset ->
                            val label = if (preset % 1f == 0f) "${preset.toInt()}x" else "${preset}x"
                            CompactMultiplierPill(
                                text = label,
                                selected = multiplier == preset,
                                onClick = { multiplier = preset },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    NutraCard(
                        color = NutraTheme.colors.background,
                        shape = NutraTheme.shapes.md,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            NutraStepperButton(
                                icon = Icons.Rounded.Remove,
                                contentDescription = "Azalt",
                                onClick = { multiplier = (multiplier - 0.25f).coerceAtLeast(0.25f) },
                            )

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val multLabel = if (multiplier % 1f == 0f) {
                                    "${multiplier.toInt()}"
                                } else {
                                    String.format(Locale.US, "%.2f", multiplier).trimEnd('0').trimEnd('.')
                                }
                                Text(
                                    text = "$multLabel × ${selectedUnit.label}",
                                    style = NutraTheme.typography.titleSmall,
                                    color = NutraTheme.colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "Toplam ${effectiveGrams.roundToInt()} g",
                                    style = NutraTheme.typography.caption,
                                    color = NutraTheme.colors.textTertiary,
                                )
                            }

                            NutraStepperButton(
                                icon = Icons.Rounded.Add,
                                contentDescription = "Artır",
                                onClick = { multiplier = (multiplier + 0.25f).coerceAtMost(20f) },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }

        // Sticky footer
        NutraDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NutraTheme.colors.surfaceElevated)
                .padding(horizontal = NutraTheme.spacing.screen, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NutraButton(
                text = "Geri",
                onClick = onBack,
                style = NutraButtonStyle.Secondary,
                size = NutraButtonSize.Medium,
                modifier = Modifier.weight(0.32f),
            )
            NutraButton(
                text = "Ekle · ${formatInt(liveCalories)} kcal",
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    if (isRawGramMode) {
                        onConfirm("1 Gram", 1f, effectiveGrams)
                    } else {
                        onConfirm(selectedUnit.label, selectedUnit.grams, multiplier)
                    }
                },
                style = NutraButtonStyle.Primary,
                size = NutraButtonSize.Medium,
                leadingIcon = Icons.Rounded.Check,
                modifier = Modifier.weight(0.68f),
            )
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
) {
    val haptic = LocalHapticFeedback.current
    val bg by animateColorAsState(
        targetValue = if (selected) NutraTheme.colors.ink else NutraTheme.colors.surfaceMuted,
        label = "multBg",
    )
    val fg by animateColorAsState(
        targetValue = if (selected) NutraTheme.colors.onInk else NutraTheme.colors.textSecondary,
        label = "multFg",
    )
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(NutraTheme.shapes.pill)
            .background(bg)
            .clickable(role = Role.Tab) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
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

@Composable
private fun AiAnalyzeTab(
    state: DashboardUiState,
    mealType: MealType,
    onAiPromptChange: (String) -> Unit,
    onRunAiAnalysis: (ByteArray?) -> Unit,
    onUpdateMultiplier: (String, Float) -> Unit,
    onToggleIncluded: (String) -> Unit,
    onCommitItems: () -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            val bytes = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
            onRunAiAnalysis(bytes)
        }
    }

    val selectedCount = state.aiAnalyzedItems.count { it.included }
    val totalSelectedCalories = state.aiAnalyzedItems.filter { it.included }.sumOf { it.effectiveCalories }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = NutraTheme.spacing.screen),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NutraIconBadge(
                        icon = Icons.Rounded.AutoAwesome,
                        size = 36.dp,
                        iconSize = 18.dp,
                    )
                    Text(
                        text = "AI besin analizi",
                        style = NutraTheme.typography.titleSmall,
                        color = NutraTheme.colors.textPrimary,
                    )
                }

                NutraBadge(
                    text = if (state.isGeminiKeyConfigured) "Gemini aktif" else "Cihaz içi NLU",
                    color = if (state.isGeminiKeyConfigured) NutraTheme.colors.accent else NutraTheme.colors.textSecondary,
                    container = if (state.isGeminiKeyConfigured) NutraTheme.colors.accentSoft else NutraTheme.colors.surfaceMuted,
                )
            }

            NutraTextField(
                value = state.aiPromptText,
                onValueChange = onAiPromptChange,
                placeholder = "Örn: 2 haşlanmış yumurta, 40g beyaz peynir ve 1 dilim ekmek",
                singleLine = false,
                minLines = 3,
                maxLines = 5,
            )

            if (!state.isGeminiKeyConfigured) {
                Text(
                    text = "API anahtarı olmadan Türkçe metinleri cihaz içinde ayrıştırabilirsiniz. Yemek fotoğrafı analizi için Profilden Gemini anahtarı ekleyebilirsiniz.",
                    style = NutraTheme.typography.caption,
                    color = NutraTheme.colors.textTertiary,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val samples = listOf(
                    "Kahvaltı tabağı" to "2 haşlanmış yumurta, 30g ezine beyaz peynir, 1 dilim tam buğday ekmeği",
                    "Tavuk & bulgur" to "1 kase mercimek çorbası, 150g ızgara tavuk göğsü, 1 porsiyon bulgur pilavı",
                    "Yulaflı yoğurt" to "1 kase süzme yoğurt, 4 yemek kaşığı yulaf ezmesi, 3 ceviz içi",
                )
                samples.forEach { (label, prompt) ->
                    NutraChip(
                        text = label,
                        selected = state.aiPromptText == prompt,
                        onClick = { onAiPromptChange(prompt) },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                NutraButton(
                    text = "Analiz et",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onRunAiAnalysis(null)
                    },
                    style = NutraButtonStyle.Primary,
                    size = NutraButtonSize.Medium,
                    loading = state.aiAnalyzing,
                    enabled = !state.aiAnalyzing,
                    modifier = Modifier.weight(1f),
                )
                NutraButton(
                    text = "Fotoğraf",
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    style = NutraButtonStyle.Secondary,
                    size = NutraButtonSize.Medium,
                    leadingIcon = Icons.Rounded.PhotoCamera,
                    enabled = !state.aiAnalyzing,
                )
            }

            if (state.aiErrorMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(NutraTheme.shapes.md)
                        .background(NutraTheme.colors.dangerSoft)
                        .padding(14.dp),
                ) {
                    Text(
                        text = state.aiErrorMessage,
                        style = NutraTheme.typography.bodySmall,
                        color = NutraTheme.colors.danger,
                    )
                }
            }

            if (state.aiAnalyzedItems.isNotEmpty()) {
                NutraSectionHeader(
                    title = "Ayrıştırılan besinler",
                    supportingText = "$selectedCount seçili",
                )

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.aiAnalyzedItems.forEach { item ->
                        NutraCard(
                            color = NutraTheme.colors.background,
                            shape = NutraTheme.shapes.md,
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(role = Role.Checkbox) { onToggleIncluded(item.id) },
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(if (item.included) NutraTheme.colors.ink else Color.Transparent)
                                        .border(
                                            width = 1.5.dp,
                                            color = if (item.included) NutraTheme.colors.ink else NutraTheme.colors.divider,
                                            shape = CircleShape,
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (item.included) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = NutraTheme.colors.onInk,
                                            modifier = Modifier.size(13.dp),
                                        )
                                    }
                                }

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = item.name,
                                        style = NutraTheme.typography.titleSmall,
                                        color = if (item.included) NutraTheme.colors.textPrimary else NutraTheme.colors.textTertiary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "${item.unitLabel} (${item.effectiveGrams.roundToInt()} g) · P ${item.effectiveProtein}g · K ${item.effectiveCarbs}g · Y ${item.effectiveFat}g",
                                        style = NutraTheme.typography.caption,
                                        color = NutraTheme.colors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }

                                Text(
                                    text = "${formatInt(item.effectiveCalories)} kcal",
                                    style = NutraTheme.typography.numberSmall,
                                    color = if (item.included) NutraTheme.colors.textPrimary else NutraTheme.colors.textTertiary,
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                listOf(0.5f, 1f, 1.5f, 2f).forEach { mult ->
                                    CompactMultiplierPill(
                                        text = "${if (mult % 1f == 0f) mult.toInt() else mult}x",
                                        selected = item.multiplier == mult,
                                        onClick = { onUpdateMultiplier(item.id, mult) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }

        if (state.aiAnalyzedItems.isNotEmpty()) {
            NutraDivider()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NutraTheme.colors.surfaceElevated)
                    .padding(horizontal = NutraTheme.spacing.screen, vertical = 12.dp),
            ) {
                NutraButton(
                    text = "${mealType.title} öğününe ekle · ${formatInt(totalSelectedCalories)} kcal",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        onCommitItems()
                    },
                    style = NutraButtonStyle.Primary,
                    size = NutraButtonSize.Medium,
                    leadingIcon = Icons.Rounded.Check,
                    enabled = selectedCount > 0,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun BarcodeLookupTab(
    state: DashboardUiState,
    onBarcodeChange: (String) -> Unit,
    onLookup: (String?) -> Unit,
) {
    val sampleBarcodes = remember {
        listOf(
            "8690526011012" to "Ezine Beyaz Peynir (Tam Yağlı)",
            "8690637012013" to "Süzme Yoğurt",
            "8690504055105" to "Yulaf Ezmesi",
            "8690572001017" to "Ton Balığı (Suda)",
            "8690698011055" to "Sade Kefir (250ml)",
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = NutraTheme.spacing.screen),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            NutraIconBadge(
                icon = Icons.Rounded.QrCodeScanner,
                size = 36.dp,
                iconSize = 18.dp,
            )
            Column {
                Text(
                    text = "Barkod sorgula",
                    style = NutraTheme.typography.titleSmall,
                    color = NutraTheme.colors.textPrimary,
                )
                Text(
                    text = "Open Food Facts ve yerel katalogda ara",
                    style = NutraTheme.typography.caption,
                    color = NutraTheme.colors.textSecondary,
                )
            }
        }

        NutraTextField(
            value = state.barcodeInput,
            onValueChange = { onBarcodeChange(it.filter { ch -> ch.isDigit() }) },
            label = "EAN-13 / UPC barkod",
            placeholder = "8690526011012",
            trailing = if (state.barcodeInput.isNotEmpty()) {
                {
                    NutraIconButton(
                        icon = Icons.Rounded.Close,
                        contentDescription = "Temizle",
                        onClick = { onBarcodeChange("") },
                        size = 32.dp,
                        iconSize = 16.dp,
                        containerColor = NutraTheme.colors.surfaceElevated,
                    )
                }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Search,
            ),
            keyboardActions = KeyboardActions(
                onSearch = {
                    if (!state.barcodeLoading && state.barcodeInput.length >= 8) {
                        onLookup(null)
                    }
                },
            ),
        )

        NutraButton(
            text = "Barkodu sorgula",
            onClick = { onLookup(null) },
            style = NutraButtonStyle.Primary,
            size = NutraButtonSize.Medium,
            loading = state.barcodeLoading,
            enabled = !state.barcodeLoading && state.barcodeInput.length >= 8,
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.barcodeErrorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(NutraTheme.shapes.md)
                    .background(NutraTheme.colors.dangerSoft)
                    .padding(14.dp),
            ) {
                Text(
                    text = state.barcodeErrorMessage,
                    style = NutraTheme.typography.bodySmall,
                    color = NutraTheme.colors.danger,
                )
            }
        }

        NutraSectionHeader(
            title = "Örnek barkodlar",
            modifier = Modifier.padding(top = 4.dp),
        )

        NutraCard(
            color = NutraTheme.colors.background,
            shape = NutraTheme.shapes.md,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            sampleBarcodes.forEachIndexed { idx, (code, title) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { onLookup(code) }
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = title,
                            style = NutraTheme.typography.titleSmall,
                            color = NutraTheme.colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = code,
                            style = NutraTheme.typography.caption,
                            color = NutraTheme.colors.textTertiary,
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "Seç",
                        tint = NutraTheme.colors.textTertiary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                if (idx < sampleBarcodes.lastIndex) {
                    NutraDivider(startIndent = 16.dp)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun CustomFoodCreatorTab(
    mealType: MealType,
    onCreateAndLog: (
        name: String,
        unitLabel: String,
        unitGrams: Float,
        caloriesPerServing: Int,
        proteinPerServing: Float,
        carbsPerServing: Float,
        fatPerServing: Float,
    ) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var name by remember { mutableStateOf("") }
    var unitLabel by remember { mutableStateOf("1 Porsiyon") }
    var unitGrams by remember { mutableStateOf("100") }
    var calories by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = NutraTheme.spacing.screen),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Özel besin oluştur",
                style = NutraTheme.typography.titleSmall,
                color = NutraTheme.colors.textPrimary,
            )

            NutraTextField(
                value = name,
                onValueChange = { name = it },
                label = "Besin veya yemek adı",
                placeholder = "Örn: Anne köftesi",
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                NutraTextField(
                    value = unitLabel,
                    onValueChange = { unitLabel = it },
                    label = "Porsiyon adı",
                    placeholder = "1 Porsiyon",
                    modifier = Modifier.weight(1.2f),
                )
                NutraTextField(
                    value = unitGrams,
                    onValueChange = { unitGrams = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = "Gramaj",
                    placeholder = "100",
                    suffix = "g",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(0.8f),
                )
            }

            NutraTextField(
                value = calories,
                onValueChange = { calories = it.filter { ch -> ch.isDigit() } },
                label = "Kalori",
                placeholder = "240",
                suffix = "kcal",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                NutraTextField(
                    value = protein,
                    onValueChange = { protein = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = "Protein",
                    placeholder = "0",
                    suffix = "g",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                NutraTextField(
                    value = carbs,
                    onValueChange = { carbs = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = "Karb",
                    placeholder = "0",
                    suffix = "g",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                NutraTextField(
                    value = fat,
                    onValueChange = { fat = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = "Yağ",
                    placeholder = "0",
                    suffix = "g",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(12.dp))
        }

        NutraDivider()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(NutraTheme.colors.surfaceElevated)
                .padding(horizontal = NutraTheme.spacing.screen, vertical = 12.dp),
        ) {
            NutraButton(
                text = "Kaydet ve ${mealType.title} öğününe ekle",
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    onCreateAndLog(
                        name,
                        unitLabel,
                        unitGrams.toFloatOrNull() ?: 100f,
                        calories.toIntOrNull() ?: 0,
                        protein.toFloatOrNull() ?: 0f,
                        carbs.toFloatOrNull() ?: 0f,
                        fat.toFloatOrNull() ?: 0f,
                    )
                },
                style = NutraButtonStyle.Primary,
                size = NutraButtonSize.Medium,
                enabled = name.isNotBlank() && calories.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
