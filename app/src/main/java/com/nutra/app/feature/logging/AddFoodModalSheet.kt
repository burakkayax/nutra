package com.nutra.app.feature.logging

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nutra.app.core.designsystem.NutraCard
import com.nutra.app.core.designsystem.NutraChip
import com.nutra.app.core.designsystem.NutraSegmentedControl
import com.nutra.app.core.designsystem.NutraTheme
import com.nutra.app.domain.model.AiAnalyzedFoodItem
import com.nutra.app.domain.model.FoodCategory
import com.nutra.app.domain.model.FoodItem
import com.nutra.app.domain.model.MealType
import com.nutra.app.domain.model.SmartComboSuggestion
import com.nutra.app.feature.dashboard.DashboardUiState
import com.nutra.app.feature.dashboard.LoggingTabMode
import kotlin.math.roundToInt

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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = NutraTheme.colors.background,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .navigationBarsPadding()
                .padding(horizontal = NutraTheme.spacing.screenHorizontal),
        ) {
            // Top Sheet Header + Meal Switcher
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "${activeMeal.title} Öğününe Ekle",
                        style = NutraTheme.typography.screenHeader,
                        color = NutraTheme.colors.textPrimary,
                    )
                    Text(
                        text = "Öğün saatine ve geçmiş tercihlerinize göre sıralandı",
                        style = NutraTheme.typography.bodySmall,
                        color = NutraTheme.colors.textSecondary,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Kapat",
                        tint = NutraTheme.colors.textSecondary,
                    )
                }
            }

            // Meal Selector Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 12.dp),
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

            // If a specific food is selected for portion customization, show the Portion Picker view
            val portionFood = state.selectedFoodForPortion
            if (portionFood != null) {
                ErgonomicPortionPickerSheetContent(
                    food = portionFood,
                    mealType = activeMeal,
                    onBack = onClosePortionPicker,
                    onToggleFavorite = { onToggleFavorite(portionFood) },
                    onConfirm = { unitLabel, unitGrams, multiplier ->
                        onConfirmLogFood(portionFood, unitLabel, unitGrams, multiplier)
                    },
                )
            } else {
                // 4-Mode Segmented Control
                NutraSegmentedControl(
                    items = LoggingTabMode.entries,
                    selectedItem = state.loggingTabMode,
                    onItemSelected = onSelectTab,
                    labelProvider = { it.title },
                )

                Spacer(modifier = Modifier.height(12.dp))

                AnimatedContent(
                    targetState = state.loggingTabMode,
                    modifier = Modifier.weight(1f),
                    label = "LoggingModeContent",
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

    Column(modifier = Modifier.fillMaxSize()) {
        // Turkish-Character-Tolerant Instant Search Box
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = "Besin ara (örn: kofte, mercimek corbasi, ezine)...",
                    style = NutraTheme.typography.bodyRegular,
                    color = NutraTheme.colors.textMuted,
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = NutraTheme.colors.textSecondary,
                )
            },
            trailingIcon = {
                if (state.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Temizle",
                            tint = NutraTheme.colors.textSecondary,
                        )
                    }
                }
            },
            singleLine = true,
            shape = NutraTheme.shapes.innerSubCard,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = NutraTheme.colors.cardSurface,
                unfocusedContainerColor = NutraTheme.colors.cardSurface,
                focusedBorderColor = NutraTheme.colors.calorieAccent,
                unfocusedBorderColor = NutraTheme.colors.hairlineBorder,
                focusedTextColor = NutraTheme.colors.textPrimary,
                unfocusedTextColor = NutraTheme.colors.textPrimary,
            ),
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Category Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
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

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Co-occurrence Smart Combo Suggestions (when search is blank)
            if (state.searchQuery.isBlank() && state.smartCombos.isNotEmpty()) {
                item(key = "smart_combos_header") {
                    Text(
                        text = "✨ Sıkça Birlikte Tüketilen Kombin Önerileri",
                        style = NutraTheme.typography.macroValue,
                        color = NutraTheme.colors.calorieAccent,
                    )
                }

                items(state.smartCombos, key = { it.id }) { combo ->
                    NutraCard(
                        borderColor = NutraTheme.colors.calorieAccent.copy(alpha = 0.55f),
                        backgroundColor = NutraTheme.colors.calorieAccent.copy(
                            alpha = if (NutraTheme.colors.isDark) 0.12f else 0.07f,
                        ),
                        contentPadding = PaddingValues(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = combo.title,
                                        style = NutraTheme.typography.cardTitle,
                                        color = NutraTheme.colors.textPrimary,
                                    )
                                    Text(
                                        text = combo.subtitle,
                                        style = NutraTheme.typography.bodySmall,
                                        color = NutraTheme.colors.textSecondary,
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(NutraTheme.shapes.chip)
                                        .background(NutraTheme.colors.calorieAccent)
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                            onLogSmartCombo(combo)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                ) {
                                    Text(
                                        text = "+ Beraber Ekle (${combo.totalCalories} kcal)",
                                        style = NutraTheme.typography.badgeLabel,
                                        color = Color.White,
                                    )
                                }
                            }

                            Text(
                                text = combo.items.joinToString(" · ") { "${it.foodName} (${it.unitLabel})" },
                                style = NutraTheme.typography.bodySmall,
                                color = NutraTheme.colors.textSecondary,
                            )
                        }
                    }
                }

                item(key = "ranked_header") {
                    Text(
                        text = "${activeMeal.title} İçin Önerilen & Sık Tüketilenler",
                        style = NutraTheme.typography.macroValue,
                        color = NutraTheme.colors.textSecondary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            items(state.rankedFoods, key = { it.id }) { food ->
                val defaultCal = food.caloriesForGrams(food.defaultUnitGrams)
                val defaultP = food.proteinForGrams(food.defaultUnitGrams).roundToInt()
                val defaultC = food.carbsForGrams(food.defaultUnitGrams).roundToInt()
                val defaultF = food.fatForGrams(food.defaultUnitGrams).roundToInt()
                val isMealPreferred = food.preferredMealTypes.contains(activeMeal)

                NutraCard(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelectFood(food)
                    },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = food.name,
                                    style = NutraTheme.typography.foodItemTitle,
                                    color = NutraTheme.colors.textPrimary,
                                )
                                if (isMealPreferred) {
                                    Box(
                                        modifier = Modifier
                                            .clip(NutraTheme.shapes.chip)
                                            .background(NutraTheme.colors.elevatedSurface)
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                    ) {
                                        Text(
                                            text = activeMeal.title,
                                            style = NutraTheme.typography.badgeLabel,
                                            color = NutraTheme.colors.calorieAccent,
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${food.defaultUnitLabel} (${food.defaultUnitGrams.roundToInt()}g) · P:${defaultP}g K:${defaultC}g Y:${defaultF}g",
                                style = NutraTheme.typography.bodySmall,
                                color = NutraTheme.colors.textSecondary,
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "$defaultCal kcal",
                                style = NutraTheme.typography.macroValue,
                                color = NutraTheme.colors.textPrimary,
                            )
                            IconButton(
                                onClick = { onToggleFavorite(food) },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    imageVector = if (food.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                    contentDescription = "Favori",
                                    tint = if (food.isFavorite) NutraTheme.colors.proteinAccent else NutraTheme.colors.textMuted,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ErgonomicPortionPickerSheetContent(
    food: FoodItem,
    mealType: MealType,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onConfirm: (unitLabel: String, unitGrams: Float, multiplier: Float) -> Unit,
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Food Header Card
        NutraCard(
            shape = NutraTheme.shapes.heroCard,
            contentPadding = PaddingValues(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = food.name,
                            style = NutraTheme.typography.screenHeader,
                            color = NutraTheme.colors.textPrimary,
                        )
                        Text(
                            text = food.subtitle,
                            style = NutraTheme.typography.bodyRegular,
                            color = NutraTheme.colors.textSecondary,
                        )
                    }
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (food.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = "Favori",
                            tint = if (food.isFavorite) NutraTheme.colors.proteinAccent else NutraTheme.colors.textMuted,
                        )
                    }
                }

                // Live Macro & Calorie Preview Strip (tnum aligned)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(NutraTheme.shapes.innerSubCard)
                        .background(NutraTheme.colors.elevatedSurface)
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LiveMetricColumn("Kalori", "$liveCalories kcal", NutraTheme.colors.calorieAccent)
                    LiveMetricColumn("Protein", "${liveProtein}g", NutraTheme.colors.proteinAccent)
                    LiveMetricColumn("Karb", "${liveCarbs}g", NutraTheme.colors.carbsAccent)
                    LiveMetricColumn("Yağ", "${liveFat}g", NutraTheme.colors.fatAccent)
                }
            }
        }

        // 1. Natural Serving Units
        Text(
            text = "1. Doğal Porsiyon Birimi Seçin",
            style = NutraTheme.typography.sectionTitle,
            color = NutraTheme.colors.textPrimary,
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            units.forEachIndexed { idx, unit ->
                val isSelected = idx == selectedUnitIndex
                NutraCard(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        selectedUnitIndex = idx
                        if (unit.label == "1 Gram") {
                            customGramInput = food.defaultUnitGrams.roundToInt().toString()
                        } else {
                            customGramInput = ""
                        }
                    },
                    borderColor = if (isSelected) NutraTheme.colors.calorieAccent else NutraTheme.colors.hairlineBorder,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = unit.label,
                            style = NutraTheme.typography.foodItemTitle,
                            color = NutraTheme.colors.textPrimary,
                        )
                        Text(
                            text = "${unit.grams.roundToInt()} g · ${food.caloriesForGrams(unit.grams)} kcal",
                            style = NutraTheme.typography.macroValue,
                            color = if (isSelected) NutraTheme.colors.calorieAccent else NutraTheme.colors.textSecondary,
                        )
                    }
                }
            }
        }

        // 2. Quick Multiplier Chips + Haptic Stepper (or Exact Gram Input)
        if (isRawGramMode) {
            Text(
                text = "2. Tam Gramaj Girin",
                style = NutraTheme.typography.sectionTitle,
                color = NutraTheme.colors.textPrimary,
            )
            OutlinedTextField(
                value = customGramInput,
                onValueChange = { customGramInput = it.filter { ch -> ch.isDigit() || ch == '.' } },
                label = { Text("Gram (g)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                text = "2. Porsiyon Çarpanı ve Miktar (${effectiveGrams.roundToInt()} g)",
                style = NutraTheme.typography.sectionTitle,
                color = NutraTheme.colors.textPrimary,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(0.5f, 1f, 1.5f, 2f, 3f).forEach { preset ->
                    val label = if (preset % 1f == 0f) "${preset.toInt()}x" else "${preset}x"
                    NutraChip(
                        text = label,
                        selected = multiplier == preset,
                        onClick = { multiplier = preset },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Fine Haptic Stepper (-0.25 / +0.25)
            NutraCard(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(NutraTheme.shapes.chip)
                            .background(NutraTheme.colors.elevatedSurface)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                multiplier = (multiplier - 0.25f).coerceAtLeast(0.25f)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Remove, contentDescription = "Azalt", tint = NutraTheme.colors.textPrimary)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val multLabel = if (multiplier % 1f == 0f) {
                            "${multiplier.toInt()}"
                        } else {
                            String.format(java.util.Locale.US, "%.2f", multiplier).trimEnd('0').trimEnd('.')
                        }
                        Text(
                            text = "$multLabel × ${selectedUnit.label}",
                            style = NutraTheme.typography.cardTitle,
                            color = NutraTheme.colors.textPrimary,
                        )
                        Text(
                            text = "Toplam ${effectiveGrams.roundToInt()} gram",
                            style = NutraTheme.typography.bodySmall,
                            color = NutraTheme.colors.textSecondary,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(NutraTheme.shapes.chip)
                            .background(NutraTheme.colors.elevatedSurface)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                multiplier = (multiplier + 0.25f).coerceAtMost(20f)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Artır", tint = NutraTheme.colors.textPrimary)
                    }
                }
            }
        }

        // Bottom Action Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onBack,
                modifier = Modifier
                    .weight(0.38f)
                    .height(52.dp),
                shape = NutraTheme.shapes.innerSubCard,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NutraTheme.colors.elevatedSurface,
                    contentColor = NutraTheme.colors.textPrimary,
                ),
            ) {
                Text("Geri", style = NutraTheme.typography.cardTitle)
            }

            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    if (isRawGramMode) {
                        onConfirm("1 Gram", 1f, effectiveGrams)
                    } else {
                        onConfirm(selectedUnit.label, selectedUnit.grams, multiplier)
                    }
                },
                modifier = Modifier
                    .weight(0.62f)
                    .height(52.dp),
                shape = NutraTheme.shapes.innerSubCard,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NutraTheme.colors.calorieAccent,
                    contentColor = Color.White,
                ),
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("${mealType.title} Ekle ($liveCalories kcal)", style = NutraTheme.typography.macroValue, color = Color.White)
            }
        }
    }
}

@Composable
private fun LiveMetricColumn(
    label: String,
    value: String,
    color: Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = NutraTheme.typography.cardTitle,
            color = color,
        )
        Text(
            text = label,
            style = NutraTheme.typography.badgeLabel,
            color = NutraTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun AiAnalyzeTab(
    state: DashboardUiState,
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NutraCard(
            contentPadding = PaddingValues(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = NutraTheme.colors.calorieAccent,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Doğal Dil & Fotoğraf ile AI Analizi",
                            style = NutraTheme.typography.cardTitle,
                            color = NutraTheme.colors.textPrimary,
                        )
                    }
                    Text(
                        text = if (state.isGeminiKeyConfigured) "Gemini Cloud" else "Cihaz İçi NLU + AI",
                        style = NutraTheme.typography.badgeLabel,
                        color = NutraTheme.colors.calorieAccent,
                    )
                }

                OutlinedTextField(
                    value = state.aiPromptText,
                    onValueChange = onAiPromptChange,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    placeholder = {
                        Text(
                            text = "Örn: 2 haşlanmış yumurta, 40g ezine beyaz peynir ve 1 dilim tam buğday ekmeği",
                            style = NutraTheme.typography.bodyRegular,
                            color = NutraTheme.colors.textMuted,
                        )
                    },
                    shape = NutraTheme.shapes.innerSubCard,
                )

                // Quick One-Tap Sample Prompts
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val samples = listOf(
                        "2 haşlanmış yumurta, 30g ezine beyaz peynir, 1 dilim tam buğday ekmeği",
                        "1 kase mercimek çorbası, 150g ızgara tavuk göğsü, 1 porsiyon bulgur pilavı",
                        "1 kase süzme yoğurt, 4 yemek kaşığı yulaf ezmesi, 3 ceviz içi",
                    )
                    samples.forEachIndexed { idx, sample ->
                        NutraChip(
                            text = "Örnek Öğün ${idx + 1}",
                            selected = state.aiPromptText == sample,
                            onClick = { onAiPromptChange(sample) },
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onRunAiAnalysis(null)
                        },
                        enabled = !state.aiAnalyzing,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = NutraTheme.shapes.chip,
                        colors = ButtonDefaults.buttonColors(containerColor = NutraTheme.colors.calorieAccent),
                    ) {
                        if (state.aiAnalyzing) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp),
                            )
                        } else {
                            Text("Metni Ayrıştır", style = NutraTheme.typography.macroValue, color = Color.White)
                        }
                    }

                    Button(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        enabled = !state.aiAnalyzing,
                        modifier = Modifier.height(46.dp),
                        shape = NutraTheme.shapes.chip,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NutraTheme.colors.elevatedSurface,
                            contentColor = NutraTheme.colors.textPrimary,
                        ),
                    ) {
                        Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Tabak Fotoğrafı", style = NutraTheme.typography.macroValue)
                    }
                }

                if (state.aiErrorMessage != null) {
                    Text(
                        text = state.aiErrorMessage,
                        style = NutraTheme.typography.bodySmall,
                        color = NutraTheme.colors.proteinAccent,
                    )
                }
            }
        }

        // Interactive Breakdown & Review List
        if (state.aiAnalyzedItems.isNotEmpty()) {
            val totalSelectedCalories = state.aiAnalyzedItems.filter { it.included }.sumOf { it.effectiveCalories }

            Text(
                text = "Ayrıştırılan Besinler (Onaylamadan Önce Düzenleyebilirsiniz)",
                style = NutraTheme.typography.macroValue,
                color = NutraTheme.colors.textSecondary,
            )

            state.aiAnalyzedItems.forEach { item ->
                NutraCard(
                    borderColor = if (item.included) NutraTheme.colors.calorieAccent else NutraTheme.colors.hairlineBorder,
                    contentPadding = PaddingValues(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onToggleIncluded(item.id) },
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(NutraTheme.shapes.chip)
                                        .background(
                                            if (item.included) NutraTheme.colors.calorieAccent else NutraTheme.colors.elevatedSurface,
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (item.included) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = item.name,
                                        style = NutraTheme.typography.foodItemTitle,
                                        color = NutraTheme.colors.textPrimary,
                                    )
                                    Text(
                                        text = "${item.unitLabel} (${item.effectiveGrams.roundToInt()}g) · Güven %${(item.confidence * 100).roundToInt()}",
                                        style = NutraTheme.typography.bodySmall,
                                        color = NutraTheme.colors.textSecondary,
                                    )
                                }
                            }

                            Text(
                                text = "${item.effectiveCalories} kcal",
                                style = NutraTheme.typography.cardTitle,
                                color = NutraTheme.colors.calorieAccent,
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "P:${item.effectiveProtein}g · K:${item.effectiveCarbs}g · Y:${item.effectiveFat}g",
                                style = NutraTheme.typography.badgeLabel,
                                color = NutraTheme.colors.textSecondary,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(0.5f, 1f, 1.5f, 2f).forEach { mult ->
                                    NutraChip(
                                        text = "${if (mult % 1f == 0f) mult.toInt() else mult}x",
                                        selected = item.multiplier == mult,
                                        onClick = { onUpdateMultiplier(item.id, mult) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    onCommitItems()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = NutraTheme.shapes.innerSubCard,
                colors = ButtonDefaults.buttonColors(containerColor = NutraTheme.colors.calorieAccent),
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Seçilenleri Öğüne Ekle ($totalSelectedCalories kcal)",
                    style = NutraTheme.typography.cardTitle,
                    color = Color.White,
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NutraCard(
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.QrCodeScanner,
                        contentDescription = null,
                        tint = NutraTheme.colors.calorieAccent,
                    )
                    Text(
                        text = "Barkod ile Paketli Ürün Sorgula",
                        style = NutraTheme.typography.cardTitle,
                        color = NutraTheme.colors.textPrimary,
                    )
                }

                OutlinedTextField(
                    value = state.barcodeInput,
                    onValueChange = { onBarcodeChange(it.filter { ch -> ch.isDigit() }) },
                    label = { Text("EAN-13 / UPC Barkod Numarası") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = NutraTheme.shapes.innerSubCard,
                )

                Button(
                    onClick = { onLookup(null) },
                    enabled = !state.barcodeLoading && state.barcodeInput.length >= 8,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = NutraTheme.shapes.chip,
                    colors = ButtonDefaults.buttonColors(containerColor = NutraTheme.colors.calorieAccent),
                ) {
                    if (state.barcodeLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                        )
                    } else {
                        Text("Barkodu Sorgula (Yerel + OpenFoodFacts)", color = Color.White)
                    }
                }

                if (state.barcodeErrorMessage != null) {
                    Text(
                        text = state.barcodeErrorMessage,
                        style = NutraTheme.typography.bodySmall,
                        color = NutraTheme.colors.proteinAccent,
                    )
                }
            }
        }

        Text(
            text = "Hızlı Test Edilebilir Barkodlar (Çevrimdışı & Online)",
            style = NutraTheme.typography.macroValue,
            color = NutraTheme.colors.textSecondary,
        )

        val sampleBarcodes = listOf(
            "8690526011012" to "Ezine Beyaz Peynir (Tam Yağlı)",
            "8690637012013" to "Süzme Yoğurt",
            "8690504055105" to "Yulaf Ezmesi",
            "8690572001017" to "Ton Balığı (Suda)",
            "8690698011055" to "Sade Kefir (250ml)",
        )

        sampleBarcodes.forEach { (code, title) ->
            NutraCard(
                onClick = { onLookup(code) },
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = title,
                            style = NutraTheme.typography.foodItemTitle,
                            color = NutraTheme.colors.textPrimary,
                        )
                        Text(
                            text = "Barkod: $code",
                            style = NutraTheme.typography.bodySmall,
                            color = NutraTheme.colors.textSecondary,
                        )
                    }
                    Text(
                        text = "Seç",
                        style = NutraTheme.typography.macroValue,
                        color = NutraTheme.colors.calorieAccent,
                    )
                }
            }
        }
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
    var name by remember { mutableStateOf("") }
    var unitLabel by remember { mutableStateOf("1 Porsiyon") }
    var unitGrams by remember { mutableStateOf("150") }
    var calories by remember { mutableStateOf("240") }
    var protein by remember { mutableStateOf("18") }
    var carbs by remember { mutableStateOf("22") }
    var fat by remember { mutableStateOf("8") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NutraCard(
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "4 Alanlı Hızlı Özel Besin & Ev Yemeği Oluşturucu",
                    style = NutraTheme.typography.cardTitle,
                    color = NutraTheme.colors.textPrimary,
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("1. Besin / Yemek Adı (örn: Anne Köftesi)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = unitLabel,
                        onValueChange = { unitLabel = it },
                        label = { Text("2. Porsiyon Adı") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = unitGrams,
                        onValueChange = { unitGrams = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("Gramaj (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }

                OutlinedTextField(
                    value = calories,
                    onValueChange = { calories = it.filter { ch -> ch.isDigit() } },
                    label = { Text("3. Porsiyon Kalorisi (kcal)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = protein,
                        onValueChange = { protein = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("Protein (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = carbs,
                        onValueChange = { carbs = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("Karb (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = fat,
                        onValueChange = { fat = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("Yağ (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }

                Button(
                    onClick = {
                        onCreateAndLog(
                            name,
                            unitLabel,
                            unitGrams.toFloatOrNull() ?: 100f,
                            calories.toIntOrNull() ?: 150,
                            protein.toFloatOrNull() ?: 10f,
                            carbs.toFloatOrNull() ?: 15f,
                            fat.toFloatOrNull() ?: 5f,
                        )
                    },
                    enabled = name.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = NutraTheme.shapes.innerSubCard,
                    colors = ButtonDefaults.buttonColors(containerColor = NutraTheme.colors.calorieAccent),
                ) {
                    Text(
                        text = "Kataloğa Kaydet ve ${mealType.title} Öğününe Ekle",
                        style = NutraTheme.typography.macroValue,
                        color = Color.White,
                    )
                }
            }
        }
    }
}
