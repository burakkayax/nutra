package com.nutra.app.feature.logging

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.nutra.app.core.designsystem.NutraIconButton
import com.nutra.app.core.designsystem.NutraModalSheet
import com.nutra.app.core.designsystem.NutraPredictiveBackOverlayHost
import com.nutra.app.core.designsystem.NutraSnackbarHost
import com.nutra.app.core.designsystem.NutraSnackbarTone
import com.nutra.app.core.designsystem.NutraTheme
import com.nutra.app.core.designsystem.PredictiveBackTransform
import com.nutra.app.core.designsystem.predictiveBackBackground
import com.nutra.app.core.designsystem.rememberAccessibleSnackbarTimeout
import com.nutra.app.core.designsystem.rememberPredictiveBackOverlayState
import com.nutra.app.domain.model.FoodCategory
import com.nutra.app.domain.model.FoodItem
import com.nutra.app.domain.model.MealType
import com.nutra.app.domain.model.SmartComboSuggestion
import com.nutra.app.feature.dashboard.CustomFoodDraft
import com.nutra.app.feature.dashboard.LoggingSheetUiState
import kotlinx.coroutines.delay

@Composable
internal fun sheetCardContainerColor(): Color {
    val colors = NutraTheme.colors
    return if (colors.isDark) colors.surfaceMuted else colors.background
}

private sealed interface AddFoodSubPage {
    data class Portion(val food: FoodItem) : AddFoodSubPage
    data object CustomFood : AddFoodSubPage
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
    onCreateAndLogCustomFood: (String, String, Float, Int, Float, Float, Float, String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val catalogListState = rememberLazyListState()
    val activeMeal = state.activeLoggingMealType
    val portionFood = state.selectedFoodForPortion
    val showSmartCombos = state.shouldShowSmartCombos
    val activeSubPage: AddFoodSubPage? = when {
        portionFood != null -> AddFoodSubPage.Portion(portionFood)
        state.isCreatingCustomFood -> AddFoodSubPage.CustomFood
        else -> null
    }

    fun navigateBackFromSubpage() {
        when (activeSubPage) {
            is AddFoodSubPage.Portion -> onClosePortionPicker()
            AddFoodSubPage.CustomFood -> onCloseCustomFoodCreator()
            null -> Unit
        }
    }

    val snackbarTimeoutMs = rememberAccessibleSnackbarTimeout(baseTimeoutMillis = 2800L, hasAction = false)
    LaunchedEffect(state.sheetFeedbackEventId, state.sheetFeedbackMessage) {
        if (state.sheetFeedbackMessage != null) {
            delay(snackbarTimeoutMs)
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
        val colors = NutraTheme.colors
        val density = LocalDensity.current.density
        val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        val reducedMotion = NutraTheme.reducedMotion
        val subPageBackState = rememberPredictiveBackOverlayState(activeSubPage)
        val isCatalogInteractive = activeSubPage == null && !subPageBackState.isCommittingExit
        val latestCatalogInteractive by rememberUpdatedState(isCatalogInteractive)

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
                        visible = activeSubPage != null,
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
                        text = when (activeSubPage) {
                            null -> "Besin ekle"
                            is AddFoodSubPage.Portion -> "Porsiyon"
                            AddFoodSubPage.CustomFood -> "Özel besin"
                        },
                        style = NutraTheme.typography.titleMedium,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )

                    MealSelectorDropdownPill(
                        activeMeal = activeMeal,
                        onSelectMealType = onSelectMealType,
                        compact = activeSubPage != null,
                    )

                    NutraIconButton(
                        icon = Icons.Rounded.Close,
                        contentDescription = "Kapat",
                        onClick = onDismiss,
                        size = 44.dp,
                    )
                }

                Spacer(Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    // Base layer: Catalog stays mounted behind subpages so Predictive Back reveals it cleanly.
                    val catalogBaseModifier = Modifier
                        .fillMaxSize()
                        .zIndex(1f)
                        .predictiveBackBackground(
                            transformProvider = {
                                if (subPageBackState.isBackgroundRevealed) {
                                    subPageBackState.currentTransform(
                                        density = density,
                                        deviceCornerRadiusPx = 0f,
                                        isDarkTheme = colors.isDark,
                                        reducedMotion = reducedMotion,
                                        isRtl = isRtl,
                                    )
                                } else {
                                    PredictiveBackTransform.Identity
                                }
                            },
                        )
                        .focusProperties { canFocus = isCatalogInteractive }
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    if (!latestCatalogInteractive) {
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                            }
                        }

                    Box(
                        modifier = if (isCatalogInteractive) {
                            catalogBaseModifier
                        } else {
                            catalogBaseModifier.clearAndSetSemantics {}
                        },
                    ) {
                        CatalogAndSmartComboTab(
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

                    // Subpage overlay layer with Cross-Activity Predictive Back; when null (Catalog),
                    // Material 3 ModalBottomSheet's own native predictive back dismissal takes over.
                    NutraPredictiveBackOverlayHost(
                        currentDestination = activeSubPage,
                        state = subPageBackState,
                        onDismiss = ::navigateBackFromSubpage,
                        deviceCornerRadiusPx = 0f,
                        modifier = Modifier.zIndex(2f),
                    ) { page ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(colors.surfaceElevated),
                        ) {
                            when (page) {
                                is AddFoodSubPage.Portion -> PortionPickerContent(
                                    food = page.food,
                                    hasStrictDietaryGuardrail = state.hasStrictDietaryGuardrail,
                                    onToggleFavorite = { onToggleFavorite(page.food) },
                                    onConfirm = { unitLabel, unitGrams, multiplier ->
                                        onConfirmLogFood(page.food, unitLabel, unitGrams, multiplier)
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                )
                                AddFoodSubPage.CustomFood -> CustomFoodCreatorTab(
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

            NutraSnackbarHost(
                visible = state.sheetFeedbackMessage != null,
                message = state.sheetFeedbackMessage.orEmpty(),
                tone = if (activeSubPage is AddFoodSubPage.CustomFood) NutraSnackbarTone.Error else NutraSnackbarTone.Success,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = NutraTheme.spacing.screen,
                        end = NutraTheme.spacing.screen,
                        bottom = if (activeSubPage == null) 14.dp else 82.dp,
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
    compact: Boolean = false,
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
                .heightIn(min = 44.dp)
                .widthIn(max = if (compact) 136.dp else 168.dp)
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
