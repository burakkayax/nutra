package com.nutra.app.feature.logging

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nutra.app.core.designsystem.NutraBadge
import com.nutra.app.core.designsystem.NutraButton
import com.nutra.app.core.designsystem.NutraButtonSize
import com.nutra.app.core.designsystem.NutraButtonStyle
import com.nutra.app.core.designsystem.NutraCard
import com.nutra.app.core.designsystem.NutraChip
import com.nutra.app.core.designsystem.NutraDivider
import com.nutra.app.core.designsystem.NutraEmptyState
import com.nutra.app.core.designsystem.NutraFormatters
import com.nutra.app.core.designsystem.NutraIconButton
import com.nutra.app.core.designsystem.NutraIcons
import com.nutra.app.core.designsystem.NutraSectionHeader
import com.nutra.app.core.designsystem.NutraSkeletonBar
import com.nutra.app.core.designsystem.NutraTextField
import com.nutra.app.core.designsystem.NutraTheme
import com.nutra.app.core.designsystem.rememberSkeletonPulseAlpha
import com.nutra.app.domain.model.FoodCategory
import com.nutra.app.domain.model.FoodItem
import com.nutra.app.domain.model.MealType
import com.nutra.app.domain.model.ServingUnitLabels
import com.nutra.app.domain.model.SmartComboSuggestion
import com.nutra.app.feature.dashboard.LoggingSheetUiState
import kotlin.math.roundToInt
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

private val NON_ALL_FOOD_CATEGORIES = FoodCategory.entries.filter { it != FoodCategory.ALL }

@Composable
internal fun CatalogAndSmartComboTab(
    state: LoggingSheetUiState,
    searchQuery: String,
    activeMeal: MealType,
    listState: LazyListState,
    onSearchQueryChange: (String) -> Unit,
    onOpenCustomFoodCreator: () -> Unit,
    onSelectCategory: (FoodCategory) -> Unit,
    onSelectFavoritesOnly: () -> Unit,
    onSelectFood: (FoodItem) -> Unit,
    onQuickAddFood: (FoodItem) -> Unit,
    onToggleFavorite: (FoodItem) -> Unit,
    onLogSmartCombo: (SmartComboSuggestion) -> Unit,
) {
    val showSmartCombos = state.shouldShowSmartCombos
    val immutableRankedFoods = remember(state.rankedFoods) { state.rankedFoods.toImmutableList() }
    val immutableSmartCombos = remember(state.smartCombos) { state.smartCombos.toImmutableList() }

    Column(modifier = Modifier.fillMaxSize()) {
        CatalogSearchField(
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            onOpenCustomFoodCreator = onOpenCustomFoodCreator,
        )

        Spacer(Modifier.height(8.dp))

        CatalogFoodResultsList(
            rankedFoods = immutableRankedFoods,
            smartCombos = immutableSmartCombos,
            showSmartCombos = showSmartCombos,
            isSearchActive = state.isSearchActive,
            isCatalogLoading = state.isCatalogLoading,
            selectedCategory = state.selectedCategory,
            favoritesOnly = state.favoritesOnly,
            hasStrictDietaryGuardrail = state.hasStrictDietaryGuardrail,
            activeMeal = activeMeal,
            listState = listState,
            onSelectCategory = onSelectCategory,
            onSelectFavoritesOnly = onSelectFavoritesOnly,
            onSelectFood = onSelectFood,
            onQuickAddFood = onQuickAddFood,
            onToggleFavorite = onToggleFavorite,
            onLogSmartCombo = onLogSmartCombo,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
    }
}

@Composable
private fun CatalogSearchField(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onOpenCustomFoodCreator: () -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val haptic = LocalHapticFeedback.current
    val addInteractionSource = remember { MutableInteractionSource() }
    val isAddPressed by addInteractionSource.collectIsPressedAsState()
    val addPressScaleState = animateFloatAsState(
        targetValue = if (isAddPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 550f),
        label = "customFoodAddPressScale",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = NutraTheme.spacing.screen),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NutraTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = "Besin, yemek veya marka ara...",
            leadingIcon = Icons.Rounded.Search,
            trailing = if (searchQuery.isNotEmpty()) {
                {
                    NutraIconButton(
                        icon = Icons.Rounded.Close,
                        contentDescription = "Temizle",
                        onClick = { onSearchQueryChange("") },
                        size = 40.dp,
                        iconSize = 16.dp,
                        containerColor = NutraTheme.colors.surfaceElevated,
                    )
                }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = { keyboardController?.hide() },
            ),
            modifier = Modifier.weight(1f),
        )

        Box(
            modifier = Modifier
                .size(52.dp)
                .graphicsLayer {
                    val s = addPressScaleState.value
                    scaleX = s
                    scaleY = s
                }
                .clip(NutraTheme.shapes.md)
                .background(NutraTheme.colors.surfaceMuted)
                .clickable(
                    interactionSource = addInteractionSource,
                    indication = LocalIndication.current,
                    role = Role.Button,
                    onClickLabel = "Özel besin oluştur",
                ) {
                    keyboardController?.hide()
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onOpenCustomFoodCreator()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "Özel besin oluştur",
                tint = NutraTheme.colors.textPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun CatalogCategoryChips(
    selectedCategory: FoodCategory,
    favoritesOnly: Boolean,
    onSelectCategory: (FoodCategory) -> Unit,
    onSelectFavoritesOnly: () -> Unit,
) {
    val chipsListState = rememberLazyListState()
    LaunchedEffect(selectedCategory, favoritesOnly) {
        val targetIdx = if (favoritesOnly) {
            1
        } else {
            val catIdx = FoodCategory.entries.indexOf(selectedCategory)
            if (catIdx <= 0) 0 else catIdx + 1
        }
        val visible = chipsListState.layoutInfo.visibleItemsInfo
        val targetInfo = visible.firstOrNull { it.index == targetIdx }
        val viewportStart = chipsListState.layoutInfo.viewportStartOffset
        val viewportEnd = chipsListState.layoutInfo.viewportEndOffset
        val isFullyVisible = targetInfo != null &&
            targetInfo.offset >= viewportStart &&
            (targetInfo.offset + targetInfo.size) <= viewportEnd
        if (!isFullyVisible) {
            chipsListState.animateScrollToItem(targetIdx)
        }
    }
    LazyRow(
        state = chipsListState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = NutraTheme.spacing.screen),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "cat_all") {
            NutraChip(
                text = FoodCategory.ALL.title,
                selected = !favoritesOnly && selectedCategory == FoodCategory.ALL,
                onClick = { onSelectCategory(FoodCategory.ALL) },
                role = Role.Tab,
            )
        }
        item(key = "cat_favorites") {
            NutraChip(
                text = "Favoriler",
                selected = favoritesOnly,
                leadingIcon = if (favoritesOnly) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                onClick = onSelectFavoritesOnly,
                role = Role.Tab,
            )
        }
        items(
            items = NON_ALL_FOOD_CATEGORIES,
            key = { it.name },
        ) { cat ->
            NutraChip(
                text = cat.title,
                selected = !favoritesOnly && cat == selectedCategory,
                onClick = { onSelectCategory(cat) },
                role = Role.Tab,
            )
        }
    }
}

@Composable
private fun CatalogFoodResultsList(
    rankedFoods: ImmutableList<FoodItem>,
    smartCombos: ImmutableList<SmartComboSuggestion>,
    showSmartCombos: Boolean,
    isSearchActive: Boolean,
    isCatalogLoading: Boolean,
    selectedCategory: FoodCategory,
    favoritesOnly: Boolean,
    hasStrictDietaryGuardrail: Boolean,
    activeMeal: MealType,
    listState: LazyListState,
    onSelectCategory: (FoodCategory) -> Unit,
    onSelectFavoritesOnly: () -> Unit,
    onSelectFood: (FoodItem) -> Unit,
    onQuickAddFood: (FoodItem) -> Unit,
    onToggleFavorite: (FoodItem) -> Unit,
    onLogSmartCombo: (SmartComboSuggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(key = "catalog_category_chips", contentType = "category_chips") {
            Box(modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)) {
                CatalogCategoryChips(
                    selectedCategory = selectedCategory,
                    favoritesOnly = favoritesOnly,
                    onSelectCategory = onSelectCategory,
                    onSelectFavoritesOnly = onSelectFavoritesOnly,
                )
            }
        }

        if (showSmartCombos) {
            item(key = "catalog_smart_combos", contentType = "smart_combos") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    NutraSectionHeader(
                        title = "Sık birlikte",
                        modifier = Modifier.padding(
                            start = NutraTheme.spacing.screen,
                            end = NutraTheme.spacing.screen,
                            top = 4.dp,
                            bottom = 10.dp,
                        ),
                    )

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = NutraTheme.spacing.screen),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(smartCombos, key = { it.id }) { combo ->
                            NutraCard(
                                color = sheetCardContainerColor(),
                                shape = NutraTheme.shapes.md,
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.width(268.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Text(
                                        text = combo.title,
                                        style = NutraTheme.typography.titleSmall,
                                        color = NutraTheme.colors.textPrimary,
                                        minLines = 2,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 8.dp),
                                    )
                                    NutraBadge(
                                        text = "${NutraFormatters.formatInt(combo.totalCalories)} kcal",
                                        color = NutraTheme.colors.onAccentSoft,
                                        container = NutraTheme.colors.accentSoft,
                                    )
                                }

                                Text(
                                    text = combo.itemsPreview,
                                    style = NutraTheme.typography.bodySmall,
                                    color = NutraTheme.colors.textSecondary,
                                    minLines = 2,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )

                                NutraButton(
                                    text = "Beraber ekle",
                                    onClick = { onLogSmartCombo(combo) },
                                    style = NutraButtonStyle.Primary,
                                    size = NutraButtonSize.Small,
                                    leadingIcon = Icons.Rounded.Add,
                                    hapticFeedback = HapticFeedbackType.Confirm,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                }
            }
        }

        item(key = "catalog_results_header", contentType = "results_header") {
            val title = when {
                favoritesOnly -> "Favoriler"
                !isSearchActive && selectedCategory == FoodCategory.ALL -> "${activeMeal.title} için önerilenler"
                else -> "Sonuçlar"
            }
            NutraSectionHeader(
                title = title,
                supportingText = if (isCatalogLoading) null else "${NutraFormatters.formatInt(rankedFoods.size)} besin",
                modifier = Modifier.padding(
                    horizontal = NutraTheme.spacing.screen,
                    vertical = 6.dp,
                ),
            )
        }

        if (isCatalogLoading && rankedFoods.isEmpty()) {
            item(key = "catalog_skeleton", contentType = "skeleton") {
                CatalogLoadingSkeletonRows()
            }
        } else if (!isCatalogLoading && rankedFoods.isEmpty()) {
            item(key = "empty_catalog", contentType = "empty_state") {
                if (favoritesOnly && !isSearchActive) {
                    NutraEmptyState(
                        icon = Icons.Rounded.FavoriteBorder,
                        title = "Henüz favori besin yok",
                        message = "Sık tükettiğin besinlerin yanındaki kalp ikonuna dokunarak buraya ekleyebilirsin.",
                    )
                } else {
                    NutraEmptyState(
                        icon = NutraIcons.SearchOff,
                        title = "Eşleşen besin bulunamadı",
                        message = "Farklı bir arama terimi deneyebilir veya + butonundan yeni besin oluşturabilirsin.",
                    )
                }
            }
        } else if (rankedFoods.isNotEmpty()) {
            itemsIndexed(
                items = rankedFoods,
                key = { _, item -> item.id },
                contentType = { _, _ -> "food_row" },
            ) { index, food ->
                CatalogFoodRow(
                    food = food,
                    showUnverifiedBadge = hasStrictDietaryGuardrail && food.isCustom && !food.allergenVerified,
                    showBottomDivider = index < rankedFoods.lastIndex,
                    onSelectFood = onSelectFood,
                    onToggleFavorite = onToggleFavorite,
                    onQuickAddFood = onQuickAddFood,
                )
            }
        }
    }
}

@Composable
private fun CatalogLoadingSkeletonRows() {
    val pulseAlpha = rememberSkeletonPulseAlpha("catalogSkeletonPulse")
    Column(modifier = Modifier.fillMaxWidth()) {
        repeat(5) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NutraTheme.spacing.screen, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NutraSkeletonBar(
                        pulseAlphaState = pulseAlpha,
                        modifier = Modifier.fillMaxWidth(0.55f).height(14.dp),
                    )
                    NutraSkeletonBar(
                        pulseAlphaState = pulseAlpha,
                        modifier = Modifier.fillMaxWidth(0.75f).height(11.dp),
                    )
                }
                NutraSkeletonBar(
                    pulseAlphaState = pulseAlpha,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
    }
}

@Composable
private fun CatalogFoodRow(
    food: FoodItem,
    showUnverifiedBadge: Boolean,
    showBottomDivider: Boolean,
    onSelectFood: (FoodItem) -> Unit,
    onToggleFavorite: (FoodItem) -> Unit,
    onQuickAddFood: (FoodItem) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val summaryLine = remember(
        food.id,
        food.caloriesPer100g,
        food.defaultUnitGrams,
        food.defaultUnitLabel,
        food.baseUnit,
    ) {
        val unitSuffix = food.displayUnitSuffix
        val defaultCal = food.caloriesForGrams(food.defaultUnitGrams)
        val roundedAmount = food.defaultUnitGrams.roundToInt()
        val isRedundant100 = roundedAmount == 100 && (
            food.defaultUnitLabel == ServingUnitLabels.BASE_100G ||
                food.defaultUnitLabel == ServingUnitLabels.BASE_100ML ||
                food.defaultUnitLabel.equals("100 $unitSuffix", ignoreCase = true)
            )
        if (isRedundant100) {
            "100 $unitSuffix · ${NutraFormatters.formatInt(defaultCal)} kcal"
        } else {
            "${food.defaultUnitLabel} (${NutraFormatters.formatInt(roundedAmount)} $unitSuffix) · ${NutraFormatters.formatInt(defaultCal)} kcal"
        }
    }
    val macroLine = remember(
        food.id,
        food.proteinPer100g,
        food.carbsPer100g,
        food.fatPer100g,
        food.defaultUnitGrams,
    ) {
        val defaultP = food.proteinForGrams(food.defaultUnitGrams).roundToInt()
        val defaultC = food.carbsForGrams(food.defaultUnitGrams).roundToInt()
        val defaultF = food.fatForGrams(food.defaultUnitGrams).roundToInt()
        "P ${defaultP}g · K ${defaultC}g · Y ${defaultF}g"
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Flattened siblings: text area selects portion, favorite & quick-add are sibling buttons (never nested clickable)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = NutraTheme.spacing.screen,
                    end = NutraTheme.spacing.screen,
                    top = 4.dp,
                    bottom = 4.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(NutraTheme.shapes.xs)
                    .clickable(role = Role.Button, onClickLabel = "${food.name} porsiyon seç") {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelectFood(food)
                    }
                    .padding(vertical = 8.dp, horizontal = 2.dp),
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
                    text = summaryLine,
                    style = NutraTheme.typography.bodySmall,
                    color = NutraTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = macroLine,
                    style = NutraTheme.typography.caption,
                    color = NutraTheme.colors.textTertiary,
                    maxLines = 1,
                )
                if (showUnverifiedBadge) {
                    Text(
                        text = "Özel besin · Alerjen etiketi doğrulanmadı",
                        style = NutraTheme.typography.caption,
                        color = NutraTheme.colors.warningText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            NutraIconButton(
                icon = if (food.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = if (food.isFavorite) "Favorilerden çıkar" else "Favorilere ekle",
                onClick = { onToggleFavorite(food) },
                size = 44.dp,
                iconSize = 18.dp,
                containerColor = Color.Transparent,
                contentColor = if (food.isFavorite) NutraTheme.colors.favorite else NutraTheme.colors.textTertiary,
            )

            NutraIconButton(
                icon = Icons.Rounded.Add,
                contentDescription = "${food.name} hızlı ekle (${food.defaultUnitLabel})",
                onClick = { onQuickAddFood(food) },
                size = 44.dp,
                iconSize = 20.dp,
                containerColor = NutraTheme.colors.surfaceMuted,
                contentColor = NutraTheme.colors.textPrimary,
                hapticFeedback = HapticFeedbackType.Confirm,
            )
        }

        if (showBottomDivider) {
            NutraDivider(
                startIndent = NutraTheme.spacing.screen,
                endIndent = NutraTheme.spacing.screen,
            )
        }
    }
}
