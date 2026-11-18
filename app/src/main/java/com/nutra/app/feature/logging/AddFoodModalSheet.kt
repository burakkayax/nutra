package com.nutra.app.feature.logging

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
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
import com.nutra.app.core.designsystem.NutraIconBadge
import com.nutra.app.core.designsystem.NutraIconButton
import com.nutra.app.core.designsystem.NutraIcons
import com.nutra.app.core.designsystem.NutraModalSheet
import com.nutra.app.core.designsystem.NutraSectionHeader
import com.nutra.app.core.designsystem.NutraSegmentedControl
import com.nutra.app.core.designsystem.NutraSelectionIndicator
import com.nutra.app.core.designsystem.NutraSnackbarHost
import com.nutra.app.core.designsystem.NutraStepperButton
import com.nutra.app.core.designsystem.NutraTextField
import com.nutra.app.core.designsystem.NutraTheme
import com.nutra.app.domain.model.FoodCategory
import com.nutra.app.domain.model.FoodItem
import com.nutra.app.domain.model.MealType
import com.nutra.app.domain.model.SmartComboSuggestion
import com.nutra.app.feature.dashboard.CustomFoodDraft
import com.nutra.app.feature.dashboard.LoggingSheetUiState
import com.nutra.app.feature.dashboard.LoggingTabMode
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
private fun sheetCardContainerColor(): Color {
    val colors = NutraTheme.colors
    return if (colors.isDark) colors.surfaceMuted else colors.background
}

private val AI_SAMPLE_PROMPTS = listOf(
    "Kahvaltı tabağı" to "2 haşlanmış yumurta, 30g ezine beyaz peynir, 1 dilim tam buğday ekmeği",
    "Tavuk & bulgur" to "1 kase mercimek çorbası, 150g ızgara tavuk göğsü, 1 porsiyon bulgur pilavı",
    "Yulaflı yoğurt" to "1 kase süzme yoğurt, 4 yemek kaşığı yulaf ezmesi, 3 ceviz içi",
)

private val SAMPLE_BARCODES = listOf(
    "8690526011012" to "Ezine Beyaz Peynir (Tam Yağlı)",
    "8690637012013" to "Süzme Yoğurt",
    "8690504055105" to "Yulaf Ezmesi",
    "8690572001017" to "Ton Balığı (Suda)",
    "8690698011055" to "Sade Kefir (250ml)",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFoodModalSheet(
    state: LoggingSheetUiState,
    searchQuery: String,
    onDismiss: () -> Unit,
    onDismissSheetFeedback: () -> Unit,
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
    onRunAiAnalysis: () -> Unit,
    onAnalyzePhotoUri: (Uri) -> Unit,
    onUpdateAiItemMultiplier: (String, Float) -> Unit,
    onToggleAiItemIncluded: (String) -> Unit,
    onCommitAiItems: () -> Unit,
    onBarcodeInputChange: (String) -> Unit,
    onLookupBarcode: (String?) -> Unit,
    onCustomFoodDraftChange: (CustomFoodDraft) -> Unit,
    onCreateAndLogCustomFood: (String, String, Float, Int, Float, Float, Float) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val catalogListState = rememberLazyListState()
    val activeMeal = state.activeLoggingMealType
    val portionFood = state.selectedFoodForPortion
    val showSmartCombos = state.shouldShowSmartCombos

    LaunchedEffect(state.sheetFeedbackEventId, state.sheetFeedbackMessage) {
        if (state.sheetFeedbackMessage != null) {
            delay(2800)
            onDismissSheetFeedback()
        }
    }

    LaunchedEffect(state.activeLoggingMealType, state.selectedCategory, state.isSearchActive) {
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
        BackHandler(enabled = portionFood != null) {
            onClosePortionPicker()
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
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
                    visible = portionFood != null,
                    enter = fadeIn(tween(160)) + expandHorizontally(tween(180)),
                    exit = fadeOut(tween(120)) + shrinkHorizontally(tween(160)),
                ) {
                    NutraIconButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Geri",
                        onClick = onClosePortionPicker,
                        size = 40.dp,
                    )
                }

                AnimatedContent(
                    targetState = portionFood != null,
                    transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                    modifier = Modifier.weight(1f),
                    label = "SheetHeaderTitle",
                ) { isPortion ->
                    Text(
                        text = if (isPortion) "Porsiyon" else "Besin ekle",
                        style = NutraTheme.typography.titleMedium,
                        color = NutraTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

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

            Spacer(Modifier.height(6.dp))

            AnimatedContent(
                targetState = portionFood,
                contentKey = { it != null },
                transitionSpec = {
                    val enteringPortion = targetState != null
                    if (enteringPortion) {
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
                label = "SheetPortionTransition",
            ) { selectedFood ->
                if (selectedFood != null) {
                    PortionPickerContent(
                        food = selectedFood,
                        mealType = activeMeal,
                        onBack = onClosePortionPicker,
                        onToggleFavorite = { onToggleFavorite(selectedFood) },
                        onConfirm = { unitLabel, unitGrams, multiplier ->
                            onConfirmLogFood(selectedFood, unitLabel, unitGrams, multiplier)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
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
                                    searchQuery = searchQuery,
                                    activeMeal = activeMeal,
                                    listState = catalogListState,
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
                                    onAnalyzePhotoUri = onAnalyzePhotoUri,
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
                                    draft = state.customFoodDraft,
                                    onDraftChange = onCustomFoodDraftChange,
                                    onCreateAndLog = onCreateAndLogCustomFood,
                                )
                            }
                        }
                    }
                }
            }
            }

            NutraSnackbarHost(
                visible = state.sheetFeedbackMessage != null && portionFood == null,
                message = state.sheetFeedbackMessage.orEmpty(),
                icon = Icons.Rounded.Check,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = NutraTheme.spacing.screen, vertical = 14.dp),
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
                .height(40.dp)
                .widthIn(max = 148.dp)
                .clip(NutraTheme.shapes.pill)
                .background(NutraTheme.colors.surfaceMuted)
                .clickable(
                    role = Role.DropdownList,
                    onClickLabel = "Öğün seç",
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    expanded = true
                }
                .padding(start = 8.dp, end = 10.dp),
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
    onSelectCategory: (FoodCategory) -> Unit,
    onSelectFood: (FoodItem) -> Unit,
    onToggleFavorite: (FoodItem) -> Unit,
    onLogSmartCombo: (SmartComboSuggestion) -> Unit,
) {
    val showSmartCombos = state.shouldShowSmartCombos

    Column(modifier = Modifier.fillMaxSize()) {
        CatalogSearchField(
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
        )

        Spacer(Modifier.height(10.dp))

        CatalogCategoryChips(
            selectedCategory = state.selectedCategory,
            onSelectCategory = onSelectCategory,
        )

        Spacer(Modifier.height(10.dp))

        CatalogFoodResultsList(
            rankedFoods = state.rankedFoods,
            smartCombos = state.smartCombos,
            showSmartCombos = showSmartCombos,
            isSearchActive = state.isSearchActive,
            isCatalogLoading = state.isCatalogLoading,
            selectedCategory = state.selectedCategory,
            activeMeal = activeMeal,
            listState = listState,
            onSelectFood = onSelectFood,
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
) {
    val keyboardController = LocalSoftwareKeyboardController.current
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = NutraTheme.spacing.screen),
    )
}

@Composable
private fun CatalogCategoryChips(
    selectedCategory: FoodCategory,
    onSelectCategory: (FoodCategory) -> Unit,
) {
    val chipsListState = rememberLazyListState()
    LaunchedEffect(selectedCategory) {
        val index = FoodCategory.entries.indexOf(selectedCategory)
        if (index >= 0) {
            chipsListState.animateScrollToItem(index)
        }
    }
    LazyRow(
        state = chipsListState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = NutraTheme.spacing.screen),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = FoodCategory.entries,
            key = { it.name },
        ) { cat ->
            NutraChip(
                text = cat.title,
                selected = cat == selectedCategory,
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
    activeMeal: MealType,
    listState: LazyListState,
    onSelectFood: (FoodItem) -> Unit,
    onToggleFavorite: (FoodItem) -> Unit,
    onLogSmartCombo: (SmartComboSuggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(key = "catalog_top_section", contentType = "top_section") {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (showSmartCombos) {
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
                                        color = NutraTheme.colors.accent,
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

                val title = if (!isSearchActive && selectedCategory == FoodCategory.ALL) {
                    "${activeMeal.title} için önerilenler"
                } else {
                    "Sonuçlar"
                }
                NutraSectionHeader(
                    title = title,
                    supportingText = if (isCatalogLoading) null else "${rankedFoods.size} besin",
                    modifier = Modifier.padding(
                        horizontal = NutraTheme.spacing.screen,
                        vertical = 6.dp,
                    ),
                )
            }
        }

        if (isCatalogLoading && rankedFoods.isEmpty()) {
            items(count = 5, key = { "catalog_skeleton_$it" }, contentType = { "skeleton" }) {
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
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.55f)
                                .height(14.dp)
                                .clip(NutraTheme.shapes.pill)
                                .background(NutraTheme.colors.surfaceMuted),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.75f)
                                .height(11.dp)
                                .clip(NutraTheme.shapes.pill)
                                .background(NutraTheme.colors.surfaceMuted),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(NutraTheme.shapes.pill)
                            .background(NutraTheme.colors.surfaceMuted),
                    )
                }
            }
        } else if (!isCatalogLoading && rankedFoods.isEmpty()) {
            item(key = "empty_catalog", contentType = "empty_state") {
                NutraEmptyState(
                    icon = NutraIcons.SearchOff,
                    title = "Eşleşen besin bulunamadı",
                    message = "Farklı bir arama terimi deneyebilir veya Özel sekmesinden yeni besin oluşturabilirsiniz.",
                )
            }
        } else if (rankedFoods.isNotEmpty()) {
            itemsIndexed(
                items = rankedFoods,
                key = { _, item -> item.id },
                contentType = { _, _ -> "food_row" },
            ) { index, food ->
                val summaryLine = remember(
                    food.id,
                    food.caloriesPer100g,
                    food.defaultUnitGrams,
                    food.defaultUnitLabel,
                ) {
                    val defaultCal = food.caloriesForGrams(food.defaultUnitGrams)
                    "${food.defaultUnitLabel} (${food.defaultUnitGrams.roundToInt()} g) · ${NutraFormatters.formatInt(defaultCal)} kcal"
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
                        contentDescription = "Favori",
                        onClick = { onToggleFavorite(food) },
                        size = 40.dp,
                        iconSize = 18.dp,
                        containerColor = Color.Transparent,
                        contentColor = if (food.isFavorite) NutraTheme.colors.favorite else NutraTheme.colors.textTertiary,
                    )

                    NutraIconButton(
                        icon = Icons.Rounded.Add,
                        contentDescription = "${food.name} seç",
                        onClick = { onSelectFood(food) },
                        size = 40.dp,
                        iconSize = 20.dp,
                        containerColor = NutraTheme.colors.surfaceMuted,
                        contentColor = NutraTheme.colors.textPrimary,
                    )
                }

                if (index < rankedFoods.lastIndex) {
                    NutraDivider(
                        startIndent = NutraTheme.spacing.screen,
                        endIndent = NutraTheme.spacing.screen,
                    )
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
    val keyboardController = LocalSoftwareKeyboardController.current
    val units = remember(food.id, food.defaultUnitLabel, food.defaultUnitGrams, food.alternateUnits) {
        food.allUnits
    }
    var selectedUnitIndex by remember(food.id) { mutableIntStateOf(0) }
    var multiplier by remember(food.id) { mutableFloatStateOf(1f) }
    var customGramInput by remember(food.id) { mutableStateOf("") }

    val selectedUnit = units.getOrElse(selectedUnitIndex) { units.first() }
    val isRawGramMode = selectedUnit.label == "1 Gram"
    val parsedCustomGrams = NutraFormatters.parseLocalizedFloat(customGramInput)
    val isCustomGramValid = !isRawGramMode || (parsedCustomGrams != null && parsedCustomGrams in 1f..3000f)
    val showRawGramError = isRawGramMode && customGramInput.isNotEmpty() && !isCustomGramValid

    val effectiveGrams = if (isRawGramMode) {
        parsedCustomGrams?.coerceIn(1f, 3000f) ?: food.defaultUnitGrams
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
                        contentDescription = "Favori",
                        onClick = onToggleFavorite,
                        size = 40.dp,
                        containerColor = if (NutraTheme.colors.isDark) NutraTheme.colors.segmentThumb else NutraTheme.colors.surface,
                        contentColor = if (food.isFavorite) NutraTheme.colors.favorite else NutraTheme.colors.textTertiary,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = NutraFormatters.formatInt(liveCalories),
                        style = NutraTheme.typography.display,
                        color = NutraTheme.colors.textPrimary,
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
                    MacroDataColumn("Karbonhidrat", "${NutraFormatters.formatMacroGrams(liveCarbs)} g", NutraTheme.colors.carbs)
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(selected = isSelected, role = Role.RadioButton) {
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
                            NutraSelectionIndicator(
                                selected = isSelected,
                                size = 20.dp,
                                iconSize = 13.dp,
                            )

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
                        listOf(50, 100, 150, 200).forEach { gramPreset ->
                            CompactMultiplierPill(
                                text = "$gramPreset g",
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
                        label = "Gramaj",
                        placeholder = "100",
                        suffix = "g",
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
                            text = "1 – 3.000 g arası bir değer girin",
                            style = NutraTheme.typography.caption,
                            color = NutraTheme.colors.danger,
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
                                    text = "Toplam ${effectiveGrams.roundToInt()} g",
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

            Spacer(Modifier.height(8.dp))
        }

        // Sticky footer
        NutraDivider()
        NutraBottomActionBar(includeNavigationInsets = false) {
            NutraButton(
                text = "Geri",
                onClick = onBack,
                style = NutraButtonStyle.Secondary,
                size = NutraButtonSize.Medium,
                modifier = Modifier.weight(0.32f),
            )
            NutraButton(
                text = "Ekle · ${NutraFormatters.formatInt(liveCalories)} kcal",
                onClick = {
                    if (isRawGramMode) {
                        onConfirm("1 Gram", 1f, effectiveGrams)
                    } else {
                        onConfirm(selectedUnit.label, selectedUnit.grams, multiplier)
                    }
                },
                style = NutraButtonStyle.Primary,
                size = NutraButtonSize.Medium,
                leadingIcon = Icons.Rounded.Check,
                enabled = isCustomGramValid,
                hapticFeedback = HapticFeedbackType.Confirm,
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
            .height(40.dp)
            .clip(NutraTheme.shapes.pill)
            .drawBehind { drawRect(bgState.value) }
            .selectable(selected = selected, role = Role.Tab) {
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
    state: LoggingSheetUiState,
    mealType: MealType,
    onAiPromptChange: (String) -> Unit,
    onRunAiAnalysis: () -> Unit,
    onAnalyzePhotoUri: (Uri) -> Unit,
    onUpdateMultiplier: (String, Float) -> Unit,
    onToggleIncluded: (String) -> Unit,
    onCommitItems: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            onAnalyzePhotoUri(uri)
        }
    }

    val (selectedCount, totalSelectedCalories) = remember(state.aiAnalyzedItems) {
        var count = 0
        var cals = 0
        for (item in state.aiAnalyzedItems) {
            if (item.included) {
                count++
                cals += item.effectiveCalories
            }
        }
        count to cals
    }

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
                        icon = NutraIcons.AutoAwesome,
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
                trailing = if (state.aiPromptText.isNotEmpty()) {
                    {
                        NutraIconButton(
                            icon = Icons.Rounded.Close,
                            contentDescription = "Temizle",
                            onClick = { onAiPromptChange("") },
                            size = 36.dp,
                            iconSize = 16.dp,
                            containerColor = NutraTheme.colors.surfaceElevated,
                        )
                    }
                } else {
                    null
                },
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
                AI_SAMPLE_PROMPTS.forEach { (label, prompt) ->
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
                    onClick = onRunAiAnalysis,
                    style = NutraButtonStyle.Primary,
                    size = NutraButtonSize.Medium,
                    loading = state.aiAnalyzing,
                    enabled = !state.aiAnalyzing && state.aiPromptText.isNotBlank(),
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
                    leadingIcon = NutraIcons.PhotoCamera,
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
                    val pillUnselected = if (NutraTheme.colors.isDark) NutraTheme.colors.segmentThumb else NutraTheme.colors.surfaceMuted
                    state.aiAnalyzedItems.forEach { item ->
                        key(item.id) {
                            NutraCard(
                                color = sheetCardContainerColor(),
                                shape = NutraTheme.shapes.md,
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .selectable(selected = item.included, role = Role.Checkbox) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onToggleIncluded(item.id)
                                        },
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    NutraSelectionIndicator(
                                        selected = item.included,
                                        size = 22.dp,
                                        iconSize = 13.dp,
                                    )

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
                                            text = "${item.unitLabel} (${item.effectiveGrams.roundToInt()} g) · P ${NutraFormatters.formatMacroGrams(item.effectiveProtein)}g · K ${NutraFormatters.formatMacroGrams(item.effectiveCarbs)}g · Y ${NutraFormatters.formatMacroGrams(item.effectiveFat)}g",
                                            style = NutraTheme.typography.caption,
                                            color = NutraTheme.colors.textSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }

                                    Text(
                                        text = "${NutraFormatters.formatInt(item.effectiveCalories)} kcal",
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
                                            unselectedColor = pillUnselected,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }

        AnimatedVisibility(
            visible = state.aiAnalyzedItems.isNotEmpty(),
            enter = fadeIn(tween(180)) + slideInVertically(tween(200)) { it / 2 },
            exit = fadeOut(tween(140)) + slideOutVertically(tween(160)) { it / 2 },
        ) {
            Column {
                NutraDivider()
                NutraBottomActionBar(includeNavigationInsets = false) {
                    NutraButton(
                        text = "${mealType.title} öğününe ekle · ${NutraFormatters.formatInt(totalSelectedCalories)} kcal",
                        onClick = onCommitItems,
                        style = NutraButtonStyle.Primary,
                        size = NutraButtonSize.Medium,
                        leadingIcon = Icons.Rounded.Check,
                        enabled = selectedCount > 0,
                        hapticFeedback = HapticFeedbackType.Confirm,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun BarcodeLookupTab(
    state: LoggingSheetUiState,
    onBarcodeChange: (String) -> Unit,
    onLookup: (String?) -> Unit,
) {
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
                icon = NutraIcons.QrCodeScanner,
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
                        size = 40.dp,
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
            color = sheetCardContainerColor(),
            shape = NutraTheme.shapes.md,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            SAMPLE_BARCODES.forEachIndexed { idx, (code, title) ->
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
                if (idx < SAMPLE_BARCODES.lastIndex) {
                    NutraDivider(startIndent = 16.dp, endIndent = 16.dp)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
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
                    placeholder = "1 Porsiyon",
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
                    color = NutraTheme.colors.danger,
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
                    color = NutraTheme.colors.danger,
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
                    label = "Karb",
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
                Text(
                    text = "Makro enerji karşılığı: ~${NutraFormatters.formatInt(macroEstimatedCalories)} kcal",
                    style = NutraTheme.typography.caption,
                    color = NutraTheme.colors.textTertiary,
                    modifier = Modifier.padding(start = 4.dp),
                )
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
