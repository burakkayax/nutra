package com.nutra.app.feature.logging

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nutra.app.core.designsystem.NutraBadge
import com.nutra.app.core.designsystem.NutraBottomActionBar
import com.nutra.app.core.designsystem.NutraButton
import com.nutra.app.core.designsystem.NutraButtonSize
import com.nutra.app.core.designsystem.NutraButtonStyle
import com.nutra.app.core.designsystem.NutraCard
import com.nutra.app.core.designsystem.NutraChip
import com.nutra.app.core.designsystem.NutraDivider
import com.nutra.app.core.designsystem.NutraDot
import com.nutra.app.core.designsystem.NutraEmptyState
import com.nutra.app.core.designsystem.NutraFormatters
import com.nutra.app.core.designsystem.NutraIconButton
import com.nutra.app.core.designsystem.NutraIcons
import com.nutra.app.core.designsystem.NutraModalSheet
import com.nutra.app.core.designsystem.NutraSectionHeader
import com.nutra.app.core.designsystem.NutraSelectionIndicator
import com.nutra.app.core.designsystem.NutraSkeletonBar
import com.nutra.app.core.designsystem.NutraSnackbarHost
import com.nutra.app.core.designsystem.NutraSnackbarTone
import com.nutra.app.core.designsystem.NutraStepperButton
import com.nutra.app.core.designsystem.NutraTextField
import com.nutra.app.core.designsystem.NutraTheme
import com.nutra.app.core.designsystem.rememberSkeletonPulseAlpha
import com.nutra.app.domain.model.FoodCategory
import com.nutra.app.domain.model.FoodItem
import com.nutra.app.domain.model.MealType
import com.nutra.app.domain.model.ServingUnitLabels
import com.nutra.app.domain.model.SmartComboSuggestion
import com.nutra.app.feature.dashboard.CustomFoodDraft
import com.nutra.app.feature.dashboard.LoggingSheetUiState
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
private fun sheetCardContainerColor(): Color {
    val colors = NutraTheme.colors
    return if (colors.isDark) colors.surfaceMuted else colors.background
}

private val NON_ALL_FOOD_CATEGORIES = FoodCategory.entries.filter { it != FoodCategory.ALL }
private val PORTION_GRAM_PRESETS = listOf(50, 100, 150, 200)
private val PORTION_MULT_PRESETS = listOf(0.5f, 1f, 1.5f, 2f, 3f)

private sealed interface AddFoodSheetPage {
    data object Catalog : AddFoodSheetPage
    data class Portion(val food: FoodItem) : AddFoodSheetPage
    data object CustomFood : AddFoodSheetPage
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFoodModalSheet(
    state: LoggingSheetUiState,
    searchQuery: String,
    onDismiss: () -> Unit,
    onDismissSheetFeedback: () -> Unit,
    onSelectMealType: (MealType) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSelectCategory: (FoodCategory) -> Unit,
    onSelectFavoritesOnly: () -> Unit,
    onSelectFoodForPortion: (FoodItem) -> Unit,
    onQuickAddFood: (FoodItem) -> Unit,
    onClosePortionPicker: () -> Unit,
    onOpenCustomFoodCreator: () -> Unit,
    onCloseCustomFoodCreator: () -> Unit,
    onToggleFavorite: (FoodItem) -> Unit,
    onConfirmLogFood: (FoodItem, String, Float, Float) -> Unit,
    onLogSmartCombo: (SmartComboSuggestion) -> Unit,
    onCustomFoodDraftChange: (CustomFoodDraft) -> Unit,
    onCreateAndLogCustomFood: (String, String, Float, Int, Float, Float, Float) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val catalogListState = rememberLazyListState()
    val activeMeal = state.activeLoggingMealType
    val portionFood = state.selectedFoodForPortion
    val showSmartCombos = state.shouldShowSmartCombos
    val currentPage: AddFoodSheetPage = when {
        portionFood != null -> AddFoodSheetPage.Portion(portionFood)
        state.isCreatingCustomFood -> AddFoodSheetPage.CustomFood
        else -> AddFoodSheetPage.Catalog
    }

    fun navigateBackFromSubpage() {
        when (currentPage) {
            is AddFoodSheetPage.Portion -> onClosePortionPicker()
            AddFoodSheetPage.CustomFood -> onCloseCustomFoodCreator()
            AddFoodSheetPage.Catalog -> Unit
        }
    }

    LaunchedEffect(state.sheetFeedbackEventId, state.sheetFeedbackMessage) {
        if (state.sheetFeedbackMessage != null) {
            delay(2800)
            onDismissSheetFeedback()
        }
    }

    LaunchedEffect(
        state.activeLoggingMealType,
        state.selectedCategory,
        state.favoritesOnly,
        state.isSearchActive,
    ) {
        if (catalogListState.firstVisibleItemIndex > 0 || catalogListState.firstVisibleItemScrollOffset > 0) {
            catalogListState.scrollToItem(0)
        }
    }

    LaunchedEffect(showSmartCombos) {
        if (showSmartCombos &&
            catalogListState.firstVisibleItemIndex == 0 &&
            catalogListState.firstVisibleItemScrollOffset > 0
        ) {
            catalogListState.scrollToItem(0)
        }
    }

    NutraModalSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        BackHandler(enabled = currentPage !is AddFoodSheetPage.Catalog) {
            navigateBackFromSubpage()
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.94f)
                .imePadding(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Sheet header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = NutraTheme.spacing.screen, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AnimatedVisibility(
                        visible = currentPage !is AddFoodSheetPage.Catalog,
                        enter = fadeIn(tween(150)) + expandHorizontally(tween(160)),
                        exit = fadeOut(tween(110)) + shrinkHorizontally(tween(140)),
                    ) {
                        NutraIconButton(
                            icon = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Geri",
                            onClick = ::navigateBackFromSubpage,
                            size = 40.dp,
                        )
                    }

                    Text(
                        text = when (currentPage) {
                            AddFoodSheetPage.Catalog -> "Besin ekle"
                            is AddFoodSheetPage.Portion -> "Porsiyon"
                            AddFoodSheetPage.CustomFood -> "Özel besin"
                        },
                        style = NutraTheme.typography.titleMedium,
                        color = NutraTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )

                    MealSelectorDropdownPill(
                        activeMeal = activeMeal,
                        onSelectMealType = onSelectMealType,
                    )

                    NutraIconButton(
                        icon = Icons.Rounded.Close,
                        contentDescription = "Kapat",
                        onClick = onDismiss,
                        size = 40.dp,
                    )
                }

                Spacer(Modifier.height(8.dp))

                AnimatedContent(
                    targetState = currentPage,
                    contentKey = { page ->
                        when (page) {
                            AddFoodSheetPage.Catalog -> 0
                            is AddFoodSheetPage.Portion -> 1
                            AddFoodSheetPage.CustomFood -> 2
                        }
                    },
                    transitionSpec = {
                        val enteringSubpage = targetState !is AddFoodSheetPage.Catalog
                        if (enteringSubpage) {
                            (slideInHorizontally(tween(240)) { it / 4 } + fadeIn(tween(180))) togetherWith
                                (slideOutHorizontally(tween(200)) { -it / 6 } + fadeOut(tween(140)))
                        } else {
                            (slideInHorizontally(tween(220)) { -it / 6 } + fadeIn(tween(180))) togetherWith
                                (slideOutHorizontally(tween(200)) { it / 4 } + fadeOut(tween(140)))
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    label = "SheetPageTransition",
                ) { page ->
                    when (page) {
                        is AddFoodSheetPage.Portion -> PortionPickerContent(
                            food = page.food,
                            onToggleFavorite = { onToggleFavorite(page.food) },
                            onConfirm = { unitLabel, unitGrams, multiplier ->
                                onConfirmLogFood(page.food, unitLabel, unitGrams, multiplier)
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                        AddFoodSheetPage.CustomFood -> CustomFoodCreatorTab(
                            mealType = activeMeal,
                            draft = state.customFoodDraft,
                            onDraftChange = onCustomFoodDraftChange,
                            onCreateAndLog = onCreateAndLogCustomFood,
                        )
                        AddFoodSheetPage.Catalog -> CatalogAndSmartComboTab(
                            state = state,
                            searchQuery = searchQuery,
                            activeMeal = activeMeal,
                            listState = catalogListState,
                            onSearchQueryChange = onSearchQueryChange,
                            onOpenCustomFoodCreator = onOpenCustomFoodCreator,
                            onSelectCategory = onSelectCategory,
                            onSelectFavoritesOnly = onSelectFavoritesOnly,
                            onSelectFood = onSelectFoodForPortion,
                            onQuickAddFood = onQuickAddFood,
                            onToggleFavorite = onToggleFavorite,
                            onLogSmartCombo = onLogSmartCombo,
                        )
                    }
                }
            }

            NutraSnackbarHost(
                visible = state.sheetFeedbackMessage != null && currentPage is AddFoodSheetPage.Catalog,
                message = state.sheetFeedbackMessage.orEmpty(),
                tone = NutraSnackbarTone.Success,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = NutraTheme.spacing.screen,
                        end = NutraTheme.spacing.screen,
                        bottom = 14.dp,
                    ),
            )
        }
    }
}

/**
 * Header pillbox with a leading downward arrow and active meal label that opens
 * a dropdown menu to switch the target meal without leaving the current sheet state.
 */
@Composable
private fun MealSelectorDropdownPill(
    activeMeal: MealType,
    onSelectMealType: (MealType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    var expanded by remember { mutableStateOf(false) }
    val arrowRotationState = animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(180),
        label = "mealDropdownArrow",
    )

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .heightIn(min = 40.dp)
                .widthIn(max = 168.dp)
                .clip(NutraTheme.shapes.pill)
                .background(NutraTheme.colors.surfaceMuted)
                .clickable(
                    role = Role.DropdownList,
                    onClickLabel = "Öğün seç",
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    expanded = true
                }
                .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = NutraTheme.colors.textPrimary,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer { rotationZ = arrowRotationState.value },
            )
            Text(
                text = activeMeal.title,
                style = NutraTheme.typography.labelSmall,
                color = NutraTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = NutraTheme.shapes.md,
            containerColor = NutraTheme.colors.surfaceElevated,
            tonalElevation = 0.dp,
            shadowElevation = 6.dp,
            border = BorderStroke(1.dp, NutraTheme.colors.divider),
            modifier = Modifier.widthIn(min = 168.dp),
        ) {
            MealType.entries.forEach { meal ->
                val isSelected = meal == activeMeal
                DropdownMenuItem(
                    text = {
                        Text(
                            text = meal.title,
                            style = if (isSelected) NutraTheme.typography.label else NutraTheme.typography.body,
                            color = if (isSelected) NutraTheme.colors.textPrimary else NutraTheme.colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    trailingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Seçili",
                                tint = NutraTheme.colors.accent,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else {
                        null
                    },
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        expanded = false
                        if (meal != activeMeal) {
                            onSelectMealType(meal)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun CatalogAndSmartComboTab(
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

    Column(modifier = Modifier.fillMaxSize()) {
        CatalogSearchField(
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            onOpenCustomFoodCreator = onOpenCustomFoodCreator,
        )

        Spacer(Modifier.height(8.dp))

        CatalogFoodResultsList(
            rankedFoods = state.rankedFoods,
            smartCombos = state.smartCombos,
            showSmartCombos = showSmartCombos,
            isSearchActive = state.isSearchActive,
            isCatalogLoading = state.isCatalogLoading,
            selectedCategory = state.selectedCategory,
            favoritesOnly = state.favoritesOnly,
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
        chipsListState.animateScrollToItem(targetIdx)
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
            )
        }
        item(key = "cat_favorites") {
            NutraChip(
                text = "Favoriler",
                selected = favoritesOnly,
                leadingIcon = if (favoritesOnly) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                onClick = onSelectFavoritesOnly,
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
            )
        }
    }
}

@Composable
private fun CatalogFoodResultsList(
    rankedFoods: List<FoodItem>,
    smartCombos: List<SmartComboSuggestion>,
    showSmartCombos: Boolean,
    isSearchActive: Boolean,
    isCatalogLoading: Boolean,
    selectedCategory: FoodCategory,
    favoritesOnly: Boolean,
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
                        message = "Farklı bir arama terimi deneyebilir veya + butonundan yeni besin oluşturabilirsiniz.",
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = "${food.name} porsiyon seç") {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelectFood(food)
                }
                .padding(
                    horizontal = NutraTheme.spacing.screen,
                    vertical = 12.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
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

@Composable
private fun PortionPickerContent(
    food: FoodItem,
    onToggleFavorite: () -> Unit,
    onConfirm: (unitLabel: String, unitGrams: Float, multiplier: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val unitSuffix = food.displayUnitSuffix
    val units = remember(food.id, food.defaultUnitLabel, food.defaultUnitGrams, food.alternateUnits, food.baseUnit) {
        food.allUnits
    }
    var selectedUnitIndex by remember(food.id) { mutableIntStateOf(0) }
    var multiplier by remember(food.id) { mutableFloatStateOf(1f) }
    var customGramInput by remember(food.id) { mutableStateOf("") }
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

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(
                start = NutraTheme.spacing.screen,
                end = NutraTheme.spacing.screen,
                bottom = 24.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
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
                        contentDescription = if (food.isFavorite) "Favorilerden çıkar" else "Favorilere ekle",
                        onClick = onToggleFavorite,
                        size = 44.dp,
                        containerColor = if (NutraTheme.colors.isDark) NutraTheme.colors.segmentThumb else NutraTheme.colors.surface,
                        contentColor = if (food.isFavorite) NutraTheme.colors.favorite else NutraTheme.colors.textTertiary,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = if (isCustomGramValid) NutraFormatters.formatInt(liveCalories) else "—",
                        style = NutraTheme.typography.display,
                        color = if (isCustomGramValid) NutraTheme.colors.textPrimary else NutraTheme.colors.textTertiary,
                        modifier = Modifier.alignByBaseline(),
                    )
                    Text(
                        text = "kcal",
                        style = NutraTheme.typography.titleMedium,
                        color = NutraTheme.colors.textSecondary,
                        modifier = Modifier.alignByBaseline(),
                    )
                }

                NutraDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    MacroDataColumn("Protein", "${NutraFormatters.formatMacroGrams(liveProtein)} g", NutraTheme.colors.protein)
                    MacroDataColumn("Karb.", "${NutraFormatters.formatMacroGrams(liveCarbs)} g", NutraTheme.colors.carbs)
                    MacroDataColumn("Yağ", "${NutraFormatters.formatMacroGrams(liveFat)} g", NutraTheme.colors.fat)
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
                                color = NutraTheme.colors.textPrimary,
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
                                color = if (isSelected) NutraTheme.colors.textPrimary else NutraTheme.colors.textSecondary,
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
                    )
                    if (showRawGramError) {
                        Text(
                            text = "1 – 3.000 $unitSuffix arası bir değer girin",
                            style = NutraTheme.typography.caption,
                            color = NutraTheme.colors.dangerText,
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
                            color = NutraTheme.colors.textPrimary,
                        )
                        Text(
                            text = "${NutraFormatters.formatInt(effectiveGrams.roundToInt())} $unitSuffix",
                            style = NutraTheme.typography.caption,
                            color = NutraTheme.colors.textSecondary,
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

                    NutraCard(
                        color = sheetCardContainerColor(),
                        shape = NutraTheme.shapes.md,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val stepperBg = if (NutraTheme.colors.isDark) NutraTheme.colors.segmentThumb else NutraTheme.colors.surfaceMuted
                            NutraStepperButton(
                                icon = NutraIcons.Remove,
                                contentDescription = "Azalt",
                                onClick = { multiplier = (multiplier - 0.25f).coerceAtLeast(0.25f) },
                                containerColor = stepperBg,
                            )

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val multLabel = NutraFormatters.formatMultiplier(multiplier)
                                Text(
                                    text = "$multLabel × ${selectedUnit.label}",
                                    style = NutraTheme.typography.titleSmall,
                                    color = NutraTheme.colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "Toplam ${NutraFormatters.formatInt(effectiveGrams.roundToInt())} $unitSuffix",
                                    style = NutraTheme.typography.caption,
                                    color = NutraTheme.colors.textTertiary,
                                )
                            }

                            NutraStepperButton(
                                icon = Icons.Rounded.Add,
                                contentDescription = "Artır",
                                onClick = { multiplier = (multiplier + 0.25f).coerceAtMost(20f) },
                                containerColor = stepperBg,
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

            NutraButton(
                text = if (isCustomGramValid) {
                    "Ekle · ${NutraFormatters.formatInt(liveCalories)} kcal"
                } else if (food.isLiquid) {
                    "Hacim girin"
                } else {
                    "Gramaj girin"
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

@Composable
private fun CustomFoodCreatorTab(
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
    ) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val parsedGrams = NutraFormatters.parseLocalizedFloat(draft.unitGrams)
    val parsedCalories = draft.calories.toIntOrNull()
    val parsedProtein = NutraFormatters.parseLocalizedFloat(draft.protein) ?: 0f
    val parsedCarbs = NutraFormatters.parseLocalizedFloat(draft.carbs) ?: 0f
    val parsedFat = NutraFormatters.parseLocalizedFloat(draft.fat) ?: 0f
    val macroEstimatedCalories = ((parsedProtein * 4f) + (parsedCarbs * 4f) + (parsedFat * 9f)).roundToInt()
    val isGramsInvalid = draft.unitGrams.isNotEmpty() && (parsedGrams == null || parsedGrams !in 1f..5000f)
    val isCaloriesInvalid = draft.calories.isNotEmpty() && (parsedCalories == null || parsedCalories !in 0..20000)
    val isFormValid = draft.name.isNotBlank() &&
        parsedCalories != null &&
        parsedCalories in 0..20000 &&
        parsedGrams != null &&
        parsedGrams in 1f..5000f

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
            Text(
                text = "Özel besin oluştur",
                style = NutraTheme.typography.titleSmall,
                color = NutraTheme.colors.textPrimary,
            )

            NutraTextField(
                value = draft.name,
                onValueChange = { onDraftChange(draft.copy(name = it)) },
                label = "Besin veya yemek adı",
                placeholder = "Örn: Anne köftesi",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                NutraTextField(
                    value = draft.unitLabel,
                    onValueChange = { onDraftChange(draft.copy(unitLabel = it)) },
                    label = "Porsiyon adı",
                    placeholder = "Örn: 1 Porsiyon",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                    modifier = Modifier.weight(1.2f),
                )
                NutraTextField(
                    value = draft.unitGrams,
                    onValueChange = {
                        onDraftChange(draft.copy(unitGrams = NutraFormatters.sanitizeNumericInput(it, allowDecimal = true)))
                    },
                    label = "Gramaj",
                    placeholder = "100",
                    suffix = "g",
                    isError = isGramsInvalid,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                    modifier = Modifier.weight(0.8f),
                )
            }

            if (isGramsInvalid) {
                Text(
                    text = "Gramaj 1 – 5.000 g arasında olmalıdır",
                    style = NutraTheme.typography.caption,
                    color = NutraTheme.colors.dangerText,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            NutraTextField(
                value = draft.calories,
                onValueChange = {
                    onDraftChange(draft.copy(calories = NutraFormatters.sanitizeNumericInput(it, allowDecimal = false)))
                },
                label = "Kalori",
                placeholder = "240",
                suffix = "kcal",
                isError = isCaloriesInvalid,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
            )

            if (isCaloriesInvalid) {
                Text(
                    text = "Kalori 0 – 20.000 kcal arasında olmalıdır",
                    style = NutraTheme.typography.caption,
                    color = NutraTheme.colors.dangerText,
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

            if (macroEstimatedCalories > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Makro enerjisi: ~${NutraFormatters.formatInt(macroEstimatedCalories)} kcal",
                        style = NutraTheme.typography.caption,
                        color = NutraTheme.colors.textTertiary,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 4.dp, end = 8.dp),
                    )
                    if (parsedCalories != macroEstimatedCalories && macroEstimatedCalories in 0..20000) {
                        Box(
                            modifier = Modifier
                                .clip(NutraTheme.shapes.pill)
                                .background(NutraTheme.colors.accentSoft)
                                .clickable(role = Role.Button) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onDraftChange(draft.copy(calories = macroEstimatedCalories.toString()))
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Kaloriye uygula",
                                style = NutraTheme.typography.labelSmall,
                                color = NutraTheme.colors.onAccentSoft,
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
                text = "Kaydet ve ${mealType.title} öğününe ekle",
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
