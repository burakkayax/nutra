package com.nutra.app.feature.dashboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nutra.app.core.designsystem.NutraCard
import com.nutra.app.core.designsystem.NutraContentMaxWidth
import com.nutra.app.core.designsystem.NutraFormatters
import com.nutra.app.core.designsystem.NutraIconButton
import com.nutra.app.core.designsystem.NutraIcons
import com.nutra.app.core.designsystem.NutraSectionHeader
import com.nutra.app.core.designsystem.NutraSkeletonBar
import com.nutra.app.core.designsystem.NutraSnackbarHost
import com.nutra.app.core.designsystem.NutraSnackbarTone
import com.nutra.app.core.designsystem.NutraStickyHeaderTopReservedPadding
import com.nutra.app.core.designsystem.NutraStickyScreenHeader
import com.nutra.app.core.designsystem.NutraTheme
import com.nutra.app.core.designsystem.rememberHeaderCollapseFraction
import com.nutra.app.core.designsystem.rememberSkeletonPulseAlpha
import com.nutra.app.domain.model.MealType
import com.nutra.app.feature.intelligence.DashboardIntelligenceCard
import com.nutra.app.feature.intelligence.DashboardIntelligenceCardState
import com.nutra.app.feature.intelligence.IntelligenceStudioTab
import com.nutra.app.feature.logging.AddFoodModalSheet
import java.time.LocalDate
import java.time.format.TextStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    scrollToTopTrigger: Int = 0,
    intelligenceCardStateFlow: StateFlow<DashboardIntelligenceCardState?>? = null,
    onOpenIntelligenceStudio: (MealType?, IntelligenceStudioTab) -> Unit = { _, _ -> },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        viewModel.refreshToday()
    }
    if (state.isLoading) {
        DashboardLoadingSkeleton(contentPadding = contentPadding)
        return
    }
    val haptic = LocalHapticFeedback.current
    val layoutDirection = LocalLayoutDirection.current
    val accessibilityManager = LocalAccessibilityManager.current
    val listState = rememberLazyListState()
    val headerCollapseFraction = rememberHeaderCollapseFraction(listState)
    val defaultTopClearance = contentPadding.calculateTopPadding() + NutraStickyHeaderTopReservedPadding
    var measuredHeaderHeight by remember { mutableStateOf(defaultTopClearance) }
    val effectiveTopPadding = maxOf(defaultTopClearance, measuredHeaderHeight + 4.dp)

    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0) {
            listState.animateScrollToItem(0)
        }
    }

    LaunchedEffect(state.bannerEventId, state.lastDeletedItem, state.statusBannerMessage) {
        if (state.lastDeletedItem != null || state.statusBannerMessage != null) {
            val timeoutMs = accessibilityManager?.calculateRecommendedTimeoutMillis(
                originalTimeoutMillis = 4500L,
                containsIcons = false,
                containsText = true,
                containsControls = state.lastDeletedItem != null,
            ) ?: 4500L
            delay(timeoutMs)
            viewModel.dismissUndoBanner()
        }
    }

    val meals = state.dailySummary.meals
    val expandedMeals = state.expandedMeals

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NutraTheme.colors.background),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = NutraContentMaxWidth)
                .fillMaxSize(),
            contentPadding = PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection) + NutraTheme.spacing.screen,
                end = contentPadding.calculateEndPadding(layoutDirection) + NutraTheme.spacing.screen,
                top = effectiveTopPadding,
                bottom = contentPadding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.Top,
        ) {
            item(key = "hero_summary", contentType = "hero_summary") {
                Column(modifier = Modifier.padding(bottom = NutraTheme.spacing.xl)) {
                    AnimatedVisibility(
                        visible = state.isWeekCalendarVisible,
                        enter = expandVertically(tween(220)) + fadeIn(tween(180)),
                        exit = shrinkVertically(tween(180)) + fadeOut(tween(120)),
                    ) {
                        Spacer(modifier = Modifier.height(132.dp))
                    }
                    HeroNutritionCard(
                        consumedCalories = state.dailySummary.consumedCalories,
                        targetCalories = state.dailySummary.goals.calories,
                        consumedProtein = state.dailySummary.consumedProtein,
                        targetProtein = state.dailySummary.goals.proteinGrams,
                        consumedCarbs = state.dailySummary.consumedCarbs,
                        targetCarbs = state.dailySummary.goals.carbsGrams,
                        consumedFat = state.dailySummary.consumedFat,
                        targetFat = state.dailySummary.goals.fatGrams,
                    )
                }
            }

            item(key = "water_card", contentType = "water_card") {
                Box(modifier = Modifier.padding(bottom = NutraTheme.spacing.xl)) {
                    WaterTrackerCard(
                        currentWaterMl = state.dailySummary.waterMl,
                        targetWaterMl = state.dailySummary.goals.waterMl,
                        onAddWater = viewModel::addWater,
                    )
                }
            }

            item(key = "meals_header", contentType = "meals_header") {
                Box(modifier = Modifier.padding(bottom = NutraTheme.spacing.md)) {
                    NutraSectionHeader(title = "Öğünler")
                }
            }

            if (intelligenceCardStateFlow != null) {
                item(key = "nutra_intelligence_card", contentType = "nutra_intelligence_card") {
                    DashboardIntelligenceCardSlot(
                        cardStateFlow = intelligenceCardStateFlow,
                        onOpenIntelligenceStudio = onOpenIntelligenceStudio,
                    )
                }
            }

            // Flattened lazy meal cards: only visible meal log rows are composed & measured (O(viewport))
            meals.forEachIndexed { mealIndex, mealSummary ->
                val mealType = mealSummary.mealType
                val hasItems = mealSummary.items.isNotEmpty()
                val isExpanded = hasItems && expandedMeals.contains(mealType)
                val isLastMeal = mealIndex == meals.lastIndex

                item(
                    key = "meal_header_${mealType.name}",
                    contentType = "meal_header",
                ) {
                    MealCardHeaderRow(
                        mealType = mealType,
                        itemCount = mealSummary.items.size,
                        totalCalories = mealSummary.totalCalories,
                        isExpanded = isExpanded,
                        addBottomGap = !isExpanded && !isLastMeal,
                        onToggleExpand = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.toggleMealExpanded(mealType)
                        },
                        onAddFoodClick = { viewModel.openAddFoodModal(mealType) },
                        modifier = Modifier.animateItem(),
                    )
                }

                if (isExpanded) {
                    itemsIndexed(
                        items = mealSummary.items,
                        key = { _, item -> "meal_item_${item.id}" },
                        contentType = { _, _ -> "meal_item" },
                    ) { idx, item ->
                        val isFirst = idx == 0
                        val isLast = idx == mealSummary.items.lastIndex
                        MealLogLazyRowContainer(
                            item = item,
                            isFirst = isFirst,
                            isLast = isLast,
                            addBottomCardGap = isLast && !isLastMeal,
                            onEdit = viewModel::openEditLoggedItem,
                            onDelete = { deleted ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.deleteLoggedItem(deleted)
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }

        DashboardHeader(
            today = state.today,
            selectedDate = state.selectedDate,
            weekStart = state.weekStart,
            isWeekCalendarVisible = state.isWeekCalendarVisible,
            collapseFractionState = headerCollapseFraction,
            horizontalPadding = PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection) + NutraTheme.spacing.screen,
                end = contentPadding.calculateEndPadding(layoutDirection) + NutraTheme.spacing.screen,
            ),
            onToggleWeekCalendar = viewModel::toggleWeekCalendar,
            onJumpToToday = viewModel::jumpToToday,
            onSelectDate = viewModel::selectDate,
            onPreviousWeek = { viewModel.shiftWeek(-1) },
            onNextWeek = { viewModel.shiftWeek(1) },
            onHeaderHeightMeasured = { h ->
                if (!state.isWeekCalendarVisible) {
                    measuredHeaderHeight = h
                }
            },
            modifier = Modifier.align(Alignment.TopCenter),
        )

        val deletedItem = state.lastDeletedItem
        val bannerVisible = deletedItem != null || state.statusBannerMessage != null
        val bannerText = if (deletedItem != null) {
            "${deletedItem.foodName} silindi"
        } else {
            state.statusBannerMessage.orEmpty()
        }

        NutraSnackbarHost(
            visible = bannerVisible,
            message = bannerText,
            actionLabel = if (deletedItem != null) "Geri al" else null,
            onAction = if (deletedItem != null) {
                {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    viewModel.undoDeleteLoggedItem()
                }
            } else {
                null
            },
            tone = if (deletedItem == null && bannerVisible) NutraSnackbarTone.Success else NutraSnackbarTone.Neutral,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    start = NutraTheme.spacing.screen,
                    end = NutraTheme.spacing.screen,
                    bottom = contentPadding.calculateBottomPadding() + 8.dp,
                ),
        )
    }

    val editingItem = state.editingLogItem
    if (editingItem != null) {
        EditLoggedFoodPortionDialog(
            item = editingItem,
            onDismiss = viewModel::closeEditLoggedItem,
            onUpdateMultiplier = { newMult ->
                viewModel.updateLoggedItemMultiplier(editingItem, newMult)
            },
            onDelete = {
                viewModel.deleteLoggedItem(editingItem)
            },
        )
    }

    if (state.activeLoggingMealType != null) {
        val loggingState by viewModel.loggingUiState.collectAsStateWithLifecycle()
        val searchQuery by viewModel.searchQueryState.collectAsStateWithLifecycle()
        AddFoodModalSheet(
            state = loggingState,
            searchQuery = searchQuery,
            onDismiss = viewModel::closeAddFoodModal,
            onDismissSheetFeedback = viewModel::dismissSheetFeedback,
            onSelectMealType = viewModel::selectLoggingMealType,
            onSearchQueryChange = viewModel::updateSearchQuery,
            onSelectCategory = viewModel::selectCategory,
            onSelectFavoritesOnly = viewModel::selectFavoritesOnly,
            onSelectFoodForPortion = viewModel::openPortionPicker,
            onQuickAddFood = viewModel::quickLogDefaultFood,
            onClosePortionPicker = viewModel::closePortionPicker,
            onOpenCustomFoodCreator = viewModel::openCustomFoodCreator,
            onCloseCustomFoodCreator = viewModel::closeCustomFoodCreator,
            onToggleFavorite = viewModel::toggleFavorite,
            onConfirmLogFood = viewModel::logFoodWithPortion,
            onLogSmartCombo = viewModel::logSmartCombo,
            onCustomFoodDraftChange = viewModel::updateCustomFoodDraft,
            onCreateAndLogCustomFood = viewModel::createAndLogCustomFood,
        )
    }
}

@Composable
private fun DashboardIntelligenceCardSlot(
    cardStateFlow: StateFlow<DashboardIntelligenceCardState?>,
    onOpenIntelligenceStudio: (MealType?, IntelligenceStudioTab) -> Unit,
) {
    val cardState by cardStateFlow.collectAsStateWithLifecycle()
    val resolved = cardState ?: return
    if (resolved.mealPages.isEmpty()) return
    Box(modifier = Modifier.padding(bottom = NutraTheme.spacing.md)) {
        DashboardIntelligenceCard(
            cardState = resolved,
            onOpenStudio = { mealType ->
                onOpenIntelligenceStudio(
                    mealType,
                    IntelligenceStudioTab.RECOMMENDATIONS,
                )
            },
        )
    }
}

@Composable
private fun DashboardLoadingSkeleton(
    contentPadding: PaddingValues,
) {
    val layoutDirection = LocalLayoutDirection.current
    val pulseAlpha = rememberSkeletonPulseAlpha("dashboardSkeletonPulse")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NutraTheme.colors.background),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = NutraContentMaxWidth)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = contentPadding.calculateStartPadding(layoutDirection) + NutraTheme.spacing.screen,
                    end = contentPadding.calculateEndPadding(layoutDirection) + NutraTheme.spacing.screen,
                    top = contentPadding.calculateTopPadding() + 28.dp,
                    bottom = contentPadding.calculateBottomPadding() + 16.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                NutraSkeletonBar(pulseAlpha, Modifier.fillMaxWidth(0.42f).height(14.dp))
                NutraSkeletonBar(pulseAlpha, Modifier.fillMaxWidth(0.32f).height(28.dp), shape = NutraTheme.shapes.sm)
            }
            NutraCard(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                NutraSkeletonBar(pulseAlpha, Modifier.fillMaxWidth(0.35f).height(12.dp))
                NutraSkeletonBar(pulseAlpha, Modifier.fillMaxWidth(0.55f).height(34.dp), shape = NutraTheme.shapes.sm)
                NutraSkeletonBar(pulseAlpha, Modifier.fillMaxWidth().height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    repeat(3) {
                        NutraSkeletonBar(pulseAlpha, Modifier.weight(1f).height(40.dp), shape = NutraTheme.shapes.sm)
                    }
                }
            }
            NutraCard(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NutraSkeletonBar(pulseAlpha, Modifier.fillMaxWidth(0.4f).height(16.dp))
                NutraSkeletonBar(pulseAlpha, Modifier.fillMaxWidth().height(6.dp))
                NutraSkeletonBar(pulseAlpha, Modifier.fillMaxWidth().height(48.dp), shape = NutraTheme.shapes.pill)
            }
            repeat(3) {
                NutraCard {
                    NutraSkeletonBar(pulseAlpha, Modifier.fillMaxWidth(0.6f).height(20.dp))
                }
            }
        }
    }
}

@Composable
private fun DashboardHeader(
    today: LocalDate,
    selectedDate: LocalDate,
    weekStart: LocalDate,
    isWeekCalendarVisible: Boolean,
    collapseFractionState: State<Float>,
    horizontalPadding: PaddingValues,
    onToggleWeekCalendar: () -> Unit,
    onJumpToToday: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onHeaderHeightMeasured: (Dp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val isToday = selectedDate == today
    val (eyebrow, title) = remember(selectedDate, today) {
        val dayOfWeek = NutraFormatters.capitalizeTr(
            selectedDate.dayOfWeek.getDisplayName(TextStyle.FULL, NutraFormatters.TR_LOCALE),
        )
        val monthName = selectedDate.month.getDisplayName(TextStyle.FULL, NutraFormatters.TR_LOCALE)
        val eyebrowText = if (selectedDate.year == today.year) {
            "$dayOfWeek, ${selectedDate.dayOfMonth} $monthName"
        } else {
            "$dayOfWeek, ${selectedDate.dayOfMonth} $monthName ${selectedDate.year}"
        }
        val titleText = when (selectedDate) {
            today -> "Bugün"
            today.minusDays(1) -> "Dün"
            today.plusDays(1) -> "Yarın"
            else -> "${selectedDate.dayOfMonth} $monthName"
        }
        eyebrowText to titleText
    }

    val calendarContainerColor by animateColorAsState(
        targetValue = when {
            isWeekCalendarVisible -> NutraTheme.colors.ink
            !isToday -> NutraTheme.colors.accentSoft
            else -> NutraTheme.colors.surfaceMuted
        },
        animationSpec = tween(180),
        label = "calendarButtonBg",
    )
    val calendarContentColor by animateColorAsState(
        targetValue = when {
            isWeekCalendarVisible -> NutraTheme.colors.onInk
            !isToday -> NutraTheme.colors.onAccentSoft
            else -> NutraTheme.colors.textPrimary
        },
        animationSpec = tween(180),
        label = "calendarButtonFg",
    )

    NutraStickyScreenHeader(
        title = title,
        collapseFractionState = collapseFractionState,
        modifier = modifier,
        eyebrow = eyebrow,
        eyebrowColor = if (isToday) NutraTheme.colors.onAccentSoft else NutraTheme.colors.textSecondary,
        horizontalPadding = horizontalPadding,
        onHeaderHeightMeasured = onHeaderHeightMeasured,
        actions = {
            AnimatedVisibility(
                visible = !isToday && !isWeekCalendarVisible,
                enter = fadeIn(tween(160)) + expandHorizontally(tween(180)),
                exit = fadeOut(tween(120)) + shrinkHorizontally(tween(160)),
            ) {
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .heightIn(min = 44.dp)
                        .clip(NutraTheme.shapes.pill)
                        .background(NutraTheme.colors.accentSoft)
                        .clickable(role = Role.Button) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onJumpToToday()
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Bugün",
                        style = NutraTheme.typography.labelSmall,
                        color = NutraTheme.colors.onAccentSoft,
                    )
                }
            }
            NutraIconButton(
                icon = NutraIcons.CalendarMonth,
                contentDescription = if (isWeekCalendarVisible) {
                    "Hafta takvimini gizle"
                } else {
                    "Hafta takvimini göster"
                },
                onClick = onToggleWeekCalendar,
                size = 44.dp,
                iconSize = 20.dp,
                containerColor = calendarContainerColor,
                contentColor = calendarContentColor,
            )
        },
        bottomContent = {
            AnimatedVisibility(
                visible = isWeekCalendarVisible,
                enter = expandVertically(tween(220)) + fadeIn(tween(180)),
                exit = shrinkVertically(tween(180)) + fadeOut(tween(120)),
            ) {
                Box(modifier = Modifier.padding(top = NutraTheme.spacing.md)) {
                    WeekDateStrip(
                        today = today,
                        weekStart = weekStart,
                        selectedDate = selectedDate,
                        onSelectDate = onSelectDate,
                        onPreviousWeek = onPreviousWeek,
                        onNextWeek = onNextWeek,
                        onJumpToToday = onJumpToToday,
                    )
                }
            }
        },
    )
}

@Composable
private fun WeekDateStrip(
    today: LocalDate,
    weekStart: LocalDate,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onJumpToToday: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val latestOnPrevWeek by rememberUpdatedState(onPreviousWeek)
    val latestOnNextWeek by rememberUpdatedState(onNextWeek)
    val monthLabel = remember(weekStart) {
        NutraFormatters.formatMonthSpan(weekStart)
    }
    val isToday = selectedDate == today

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = monthLabel,
                style = NutraTheme.typography.labelSmall,
                color = NutraTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AnimatedVisibility(
                    visible = !isToday,
                    enter = fadeIn(tween(160)) + expandHorizontally(tween(180)),
                    exit = fadeOut(tween(120)) + shrinkHorizontally(tween(160)),
                ) {
                    Box(
                        modifier = Modifier
                            .heightIn(min = 40.dp)
                            .clip(NutraTheme.shapes.pill)
                            .background(NutraTheme.colors.accentSoft)
                            .clickable(role = Role.Button) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onJumpToToday()
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Bugüne dön",
                            style = NutraTheme.typography.labelSmall,
                            color = NutraTheme.colors.onAccentSoft,
                        )
                    }
                }
                NutraIconButton(
                    icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = "Önceki hafta",
                    onClick = onPreviousWeek,
                    size = 40.dp,
                    iconSize = 18.dp,
                    containerColor = NutraTheme.colors.surfaceMuted,
                    contentColor = NutraTheme.colors.textSecondary,
                )
                NutraIconButton(
                    icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = "Sonraki hafta",
                    onClick = onNextWeek,
                    size = 40.dp,
                    iconSize = 18.dp,
                    containerColor = NutraTheme.colors.surfaceMuted,
                    contentColor = NutraTheme.colors.textSecondary,
                )
            }
        }

        AnimatedContent(
            targetState = weekStart,
            transitionSpec = {
                val forward = targetState >= initialState
                (slideInHorizontally(tween(200)) { if (forward) it / 6 else -it / 6 } +
                    fadeIn(tween(160))) togetherWith
                    (slideOutHorizontally(tween(160)) { if (forward) -it / 6 else it / 6 } +
                        fadeOut(tween(120)))
            },
            modifier = Modifier.pointerInput(Unit) {
                var accumulatedDrag = 0f
                val thresholdPx = 48.dp.toPx()
                detectHorizontalDragGestures(
                    onDragStart = { accumulatedDrag = 0f },
                    onDragEnd = {
                        if (accumulatedDrag <= -thresholdPx) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            latestOnNextWeek()
                        } else if (accumulatedDrag >= thresholdPx) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            latestOnPrevWeek()
                        }
                        accumulatedDrag = 0f
                    },
                    onDragCancel = { accumulatedDrag = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        accumulatedDrag += dragAmount
                    },
                )
            },
            label = "weekStripTransition",
        ) { renderedWeekStart ->
            val renderedWeekDays = remember(renderedWeekStart) {
                (0L..6L).map { renderedWeekStart.plusDays(it) }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                renderedWeekDays.forEach { day ->
                    WeekDayCell(
                        day = day,
                        isSelected = day == selectedDate,
                        isDayToday = day == today,
                        isFuture = day.isAfter(today),
                        onSelectDate = onSelectDate,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekDayCell(
    day: LocalDate,
    isSelected: Boolean,
    isDayToday: Boolean,
    isFuture: Boolean,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val colors = NutraTheme.colors
    val circleBgState = animateColorAsState(
        targetValue = when {
            isSelected -> colors.ink
            isDayToday -> colors.accentSoft
            else -> Color.Transparent
        },
        animationSpec = tween(160),
        label = "dayCircleBg",
    )
    val dayNumberColor by animateColorAsState(
        targetValue = when {
            isSelected -> colors.onInk
            isDayToday -> colors.onAccentSoft
            isFuture -> colors.textTertiary
            else -> colors.textPrimary
        },
        animationSpec = tween(160),
        label = "dayText",
    )
    val dayShort = remember(day) {
        NutraFormatters.capitalizeTr(
            day.dayOfWeek.getDisplayName(TextStyle.SHORT, NutraFormatters.TR_LOCALE)
                .trimEnd('.')
                .take(3),
        )
    }

    Column(
        modifier = modifier
            .clip(NutraTheme.shapes.md)
            .selectable(
                selected = isSelected,
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current,
                role = Role.Tab,
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSelectDate(day)
            }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = dayShort,
            style = NutraTheme.typography.caption,
            color = if (isSelected) colors.textPrimary else colors.textTertiary,
            maxLines = 1,
        )
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .drawBehind { drawRect(circleBgState.value) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "${day.dayOfMonth}",
                style = NutraTheme.typography.titleSmall,
                color = dayNumberColor,
                maxLines = 1,
            )
        }
    }
}
