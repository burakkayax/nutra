package com.nutra.app.feature.dashboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
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
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nutra.app.core.designsystem.NutraButton
import com.nutra.app.core.designsystem.NutraButtonSize
import com.nutra.app.core.designsystem.NutraButtonStyle
import com.nutra.app.core.designsystem.NutraCard
import com.nutra.app.core.designsystem.NutraContentMaxWidth
import com.nutra.app.core.designsystem.NutraDialog
import com.nutra.app.core.designsystem.NutraDivider
import com.nutra.app.core.designsystem.NutraDot
import com.nutra.app.core.designsystem.NutraFormatters
import com.nutra.app.core.designsystem.NutraIconBadge
import com.nutra.app.core.designsystem.NutraIconButton
import com.nutra.app.core.designsystem.NutraIcons
import com.nutra.app.core.designsystem.NutraLinearProgress
import com.nutra.app.core.designsystem.NutraMacroStat
import com.nutra.app.core.designsystem.NutraNumericInputDialog
import com.nutra.app.core.designsystem.NutraScreenHeader
import com.nutra.app.core.designsystem.NutraSectionHeader
import com.nutra.app.core.designsystem.NutraSkeletonBar
import com.nutra.app.core.designsystem.NutraSnackbarHost
import com.nutra.app.core.designsystem.NutraSnackbarTone
import com.nutra.app.core.designsystem.NutraStepperButton
import com.nutra.app.core.designsystem.NutraTheme
import com.nutra.app.core.designsystem.rememberSkeletonPulseAlpha
import com.nutra.app.domain.model.MealLogItem
import com.nutra.app.domain.model.MealSummary
import com.nutra.app.domain.model.MealType
import com.nutra.app.feature.logging.AddFoodModalSheet
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.TextStyle
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    scrollToTopTrigger: Int = 0,
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
    val listState = rememberLazyListState()

    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0) {
            listState.animateScrollToItem(0)
        }
    }

    LaunchedEffect(state.bannerEventId, state.lastDeletedItem, state.statusBannerMessage) {
        if (state.lastDeletedItem != null || state.statusBannerMessage != null) {
            delay(4500)
            viewModel.dismissUndoBanner()
        }
    }

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
                top = contentPadding.calculateTopPadding() + 28.dp,
                bottom = contentPadding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(NutraTheme.spacing.md),
        ) {
            item(key = "header") {
                Column(modifier = Modifier.padding(bottom = NutraTheme.spacing.sm)) {
                    DashboardHeader(
                        today = state.today,
                        selectedDate = state.selectedDate,
                        isWeekCalendarVisible = state.isWeekCalendarVisible,
                        onToggleWeekCalendar = viewModel::toggleWeekCalendar,
                        onJumpToToday = viewModel::jumpToToday,
                    )

                    AnimatedVisibility(
                        visible = state.isWeekCalendarVisible,
                        enter = expandVertically(tween(220)) + fadeIn(tween(180)),
                        exit = shrinkVertically(tween(180)) + fadeOut(tween(120)),
                    ) {
                        Box(modifier = Modifier.padding(top = NutraTheme.spacing.lg)) {
                            WeekDateStrip(
                                today = state.today,
                                weekStart = state.weekStart,
                                selectedDate = state.selectedDate,
                                onSelectDate = viewModel::selectDate,
                                onPreviousWeek = { viewModel.shiftWeek(-1) },
                                onNextWeek = { viewModel.shiftWeek(1) },
                                onJumpToToday = viewModel::jumpToToday,
                            )
                        }
                    }
                }
            }

            item(key = "hero_summary") {
                Box(modifier = Modifier.padding(bottom = NutraTheme.spacing.sm)) {
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

            item(key = "water_card") {
                Box(modifier = Modifier.padding(bottom = NutraTheme.spacing.sm)) {
                    WaterTrackerCard(
                        currentWaterMl = state.dailySummary.waterMl,
                        targetWaterMl = state.dailySummary.goals.waterMl,
                        onAddWater = viewModel::addWater,
                    )
                }
            }

            item(key = "meals_header") {
                NutraSectionHeader(title = "Öğünler")
            }

            items(
                items = state.dailySummary.meals,
                key = { it.mealType.name },
                contentType = { "meal_card" },
            ) { mealSummary ->
                MealCard(
                    mealSummary = mealSummary,
                    isExpanded = state.expandedMeals.contains(mealSummary.mealType),
                    onToggleExpand = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.toggleMealExpanded(mealSummary.mealType)
                    },
                    onAddFoodClick = { viewModel.openAddFoodModal(mealSummary.mealType) },
                    onEditLogItem = viewModel::openEditLoggedItem,
                    onDeleteLogItem = { item ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.deleteLoggedItem(item)
                    },
                    modifier = Modifier.animateItem(),
                )
            }
        }

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
        val context = LocalContext.current
        val loggingState by viewModel.loggingUiState.collectAsStateWithLifecycle()
        val searchQuery by viewModel.searchQueryState.collectAsStateWithLifecycle()
        AddFoodModalSheet(
            state = loggingState,
            searchQuery = searchQuery,
            onDismiss = viewModel::closeAddFoodModal,
            onDismissSheetFeedback = viewModel::dismissSheetFeedback,
            onSelectMealType = viewModel::selectLoggingMealType,
            onSelectTab = viewModel::selectLoggingTab,
            onSearchQueryChange = viewModel::updateSearchQuery,
            onSelectCategory = viewModel::selectCategory,
            onSelectFavoritesOnly = viewModel::selectFavoritesOnly,
            onSelectFoodForPortion = viewModel::openPortionPicker,
            onQuickAddFood = viewModel::quickLogDefaultFood,
            onClosePortionPicker = viewModel::closePortionPicker,
            onToggleFavorite = viewModel::toggleFavorite,
            onConfirmLogFood = viewModel::logFoodWithPortion,
            onLogSmartCombo = viewModel::logSmartCombo,
            onAiPromptChange = viewModel::updateAiPromptText,
            onRunAiAnalysis = { viewModel.analyzeWithAi(null) },
            onAnalyzePhotoUri = { uri -> viewModel.analyzePhotoWithAi(context.contentResolver, uri) },
            onUpdateAiItemMultiplier = viewModel::updateAiItemMultiplier,
            onToggleAiItemIncluded = viewModel::toggleAiItemIncluded,
            onCommitAiItems = viewModel::commitAiAnalyzedItems,
            onBarcodeInputChange = viewModel::updateBarcodeInput,
            onLookupBarcode = viewModel::lookupBarcode,
            onCustomFoodDraftChange = viewModel::updateCustomFoodDraft,
            onCreateAndLogCustomFood = viewModel::createAndLogCustomFood,
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
    isWeekCalendarVisible: Boolean,
    onToggleWeekCalendar: () -> Unit,
    onJumpToToday: () -> Unit,
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

    NutraScreenHeader(
        title = title,
        eyebrow = eyebrow,
        eyebrowColor = if (isToday) NutraTheme.colors.accent else NutraTheme.colors.textSecondary,
        actions = {
            AnimatedVisibility(
                visible = !isToday && !isWeekCalendarVisible,
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
                    val isSelected = day == selectedDate
                    val isDayToday = day == today
                    val isFuture = day.isAfter(today)

                    val circleBgState = animateColorAsState(
                        targetValue = when {
                            isSelected -> NutraTheme.colors.ink
                            isDayToday -> NutraTheme.colors.accentSoft
                            else -> Color.Transparent
                        },
                        animationSpec = tween(160),
                        label = "dayCircleBg",
                    )
                    val dayNumberColor by animateColorAsState(
                        targetValue = when {
                            isSelected -> NutraTheme.colors.onInk
                            isDayToday -> NutraTheme.colors.onAccentSoft
                            isFuture -> NutraTheme.colors.textTertiary
                            else -> NutraTheme.colors.textPrimary
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
                        modifier = Modifier
                            .weight(1f)
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
                            color = if (isSelected) NutraTheme.colors.textPrimary else NutraTheme.colors.textTertiary,
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
            }
        }
    }
}

@Composable
private fun HeroNutritionCard(
    consumedCalories: Int,
    targetCalories: Int,
    consumedProtein: Float,
    targetProtein: Int,
    consumedCarbs: Float,
    targetCarbs: Int,
    consumedFat: Float,
    targetFat: Int,
) {
    val safeTarget = targetCalories.coerceAtLeast(1)
    val isOver = consumedCalories > safeTarget
    val deltaCalories = if (isOver) consumedCalories - safeTarget else safeTarget - consumedCalories
    val animatedConsumed by animateIntAsState(
        targetValue = consumedCalories,
        animationSpec = tween(240),
        label = "heroCalories",
    )
    val animatedDelta by animateIntAsState(
        targetValue = deltaCalories,
        animationSpec = tween(240),
        label = "heroDelta",
    )
    val completionPercent = ((animatedConsumed.toFloat() / safeTarget) * 100f).roundToInt()
    val progress = consumedCalories.toFloat() / safeTarget

    NutraCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(NutraTheme.spacing.card),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Günlük kalori",
                    style = NutraTheme.typography.caption,
                    color = NutraTheme.colors.textSecondary,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = NutraFormatters.formatInt(animatedConsumed),
                        style = NutraTheme.typography.numberHero,
                        color = NutraTheme.colors.textPrimary,
                        modifier = Modifier.alignByBaseline(),
                    )
                    Text(
                        text = "/ ${NutraFormatters.formatInt(safeTarget)} kcal",
                        style = NutraTheme.typography.body,
                        color = NutraTheme.colors.textSecondary,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
            }

            NutraLinearProgress(
                progress = progress,
                color = NutraTheme.colors.accent,
                height = 8.dp,
                overflowProgress = if (isOver) (progress - 1f).coerceAtLeast(0.05f) else 0f,
                overflowColor = NutraTheme.colors.warning,
                contentDescription = "Günlük kalori $consumedCalories / $safeTarget kcal, %$completionPercent",
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "${NutraFormatters.formatInt(animatedDelta)} kcal",
                        style = NutraTheme.typography.numberSmall,
                        color = if (isOver) NutraTheme.colors.warningText else NutraTheme.colors.textPrimary,
                    )
                    Text(
                        text = if (isOver) "Hedef fazlası" else "Kalan",
                        style = NutraTheme.typography.caption,
                        color = NutraTheme.colors.textTertiary,
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(26.dp)
                        .background(NutraTheme.colors.divider),
                )

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "%$completionPercent",
                        style = NutraTheme.typography.numberSmall,
                        color = NutraTheme.colors.textPrimary,
                    )
                    Text(
                        text = "Tamamlandı",
                        style = NutraTheme.typography.caption,
                        color = NutraTheme.colors.textTertiary,
                    )
                }
            }
        }

        NutraDivider()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            NutraMacroStat(
                label = "Protein",
                consumed = consumedProtein,
                target = targetProtein,
                color = NutraTheme.colors.protein,
                modifier = Modifier.weight(1f),
            )
            NutraMacroStat(
                label = "Karb.",
                consumed = consumedCarbs,
                target = targetCarbs,
                color = NutraTheme.colors.carbs,
                modifier = Modifier.weight(1f),
            )
            NutraMacroStat(
                label = "Yağ",
                consumed = consumedFat,
                target = targetFat,
                color = NutraTheme.colors.fat,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun WaterTrackerCard(
    currentWaterMl: Int,
    targetWaterMl: Int,
    onAddWater: (Int) -> Unit,
) {
    val safeTarget = targetWaterMl.coerceAtLeast(1)
    val animatedWater by animateIntAsState(
        targetValue = currentWaterMl,
        animationSpec = tween(220),
        label = "waterMl",
    )
    val fraction = (currentWaterMl.toFloat() / safeTarget.toFloat()).coerceIn(0f, 1f)
    val actualPercent = ((animatedWater.toFloat() / safeTarget.toFloat()) * 100f).roundToInt()
    val isGoalReached = currentWaterMl >= safeTarget

    NutraCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(NutraTheme.spacing.card),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                    text = "Su takibi",
                    style = NutraTheme.typography.titleSmall,
                    color = NutraTheme.colors.textPrimary,
                )
                Text(
                    text = "%$actualPercent tamamlandı",
                    style = NutraTheme.typography.caption,
                    color = if (isGoalReached) NutraTheme.colors.accent else NutraTheme.colors.textTertiary,
                )
            }

            Row {
                Text(
                    text = NutraFormatters.formatInt(animatedWater),
                    style = NutraTheme.typography.numberMedium,
                    color = NutraTheme.colors.textPrimary,
                    modifier = Modifier.alignByBaseline(),
                )
                Text(
                    text = " / ${NutraFormatters.formatInt(safeTarget)} ml",
                    style = NutraTheme.typography.caption,
                    color = NutraTheme.colors.textTertiary,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }

        NutraLinearProgress(
            progress = fraction,
            color = NutraTheme.colors.water,
            height = 6.dp,
            contentDescription = "Su takibi $currentWaterMl / $safeTarget ml, %$actualPercent",
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WaterQuickPill(
                amountText = "+200 ml",
                onClick = { onAddWater(200) },
                modifier = Modifier.weight(1f),
            )
            WaterQuickPill(
                amountText = "+330 ml",
                onClick = { onAddWater(330) },
                modifier = Modifier.weight(1f),
            )
            WaterQuickPill(
                amountText = "+500 ml",
                onClick = { onAddWater(500) },
                modifier = Modifier.weight(1f),
            )
            NutraIconButton(
                icon = NutraIcons.Remove,
                contentDescription = "200 ml azalt",
                onClick = { onAddWater(-200) },
                size = 48.dp,
                enabled = currentWaterMl > 0,
            )
        }
    }
}

@Composable
private fun WaterQuickPill(
    amountText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScaleState = animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 700f),
        label = "waterPillScale",
    )
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .graphicsLayer {
                val s = pressScaleState.value
                scaleX = s
                scaleY = s
            }
            .clip(NutraTheme.shapes.pill)
            .background(NutraTheme.colors.surfaceMuted)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Button,
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = amountText,
            style = NutraTheme.typography.labelSmall,
            color = NutraTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MealCard(
    mealSummary: MealSummary,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onAddFoodClick: () -> Unit,
    onEditLogItem: (MealLogItem) -> Unit,
    onDeleteLogItem: (MealLogItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val hasItems = mealSummary.items.isNotEmpty()
    val mealIcon = when (mealSummary.mealType) {
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

    NutraCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = NutraTheme.spacing.card, vertical = NutraTheme.spacing.lg),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(NutraTheme.shapes.sm)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = LocalIndication.current,
                    role = Role.Button,
                ) {
                    if (hasItems) {
                        onToggleExpand()
                    } else {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onAddFoodClick()
                    }
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NutraIconBadge(
                    icon = mealIcon,
                    tint = NutraTheme.colors.textPrimary,
                    container = NutraTheme.colors.surfaceMuted,
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
                            text = mealSummary.mealType.title,
                            style = NutraTheme.typography.titleSmall,
                            color = NutraTheme.colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (hasItems) {
                            Icon(
                                imageVector = NutraIcons.ExpandMore,
                                contentDescription = if (isExpanded) "Daralt" else "Genişlet",
                                tint = NutraTheme.colors.textTertiary,
                                modifier = Modifier
                                    .size(18.dp)
                                    .graphicsLayer { rotationZ = chevronRotationState.value },
                            )
                        }
                    }

                    Text(
                        text = if (hasItems) {
                            "${mealSummary.items.size} besin"
                        } else {
                            "Henüz eklenmedi"
                        },
                        style = NutraTheme.typography.caption,
                        color = if (hasItems) NutraTheme.colors.textSecondary else NutraTheme.colors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (hasItems) {
                    Text(
                        text = "${NutraFormatters.formatInt(mealSummary.totalCalories)} kcal",
                        style = NutraTheme.typography.numberSmall,
                        color = NutraTheme.colors.textPrimary,
                    )
                }
                NutraIconButton(
                    icon = Icons.Rounded.Add,
                    contentDescription = "${mealSummary.mealType.title} besin ekle",
                    onClick = onAddFoodClick,
                    size = 44.dp,
                    iconSize = 20.dp,
                    containerColor = NutraTheme.colors.surfaceMuted,
                    contentColor = NutraTheme.colors.textPrimary,
                )
            }
        }

        AnimatedVisibility(
            visible = hasItems && isExpanded,
            enter = expandVertically(tween(220)) + fadeIn(tween(180)),
            exit = shrinkVertically(tween(180)) + fadeOut(tween(120)),
        ) {
            Column(
                modifier = Modifier.padding(top = 14.dp),
            ) {
                NutraDivider()
                mealSummary.items.forEachIndexed { idx, item ->
                    key(item.id) {
                        val dismissState = rememberSwipeToDismissBoxState()
                        var dismissDispatched by remember(item.id, item.timestampMillis) { mutableStateOf(false) }

                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = false,
                            onDismiss = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart && !dismissDispatched) {
                                    dismissDispatched = true
                                    onDeleteLogItem(item)
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
                                            .clip(NutraTheme.shapes.sm)
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
                                    .clip(NutraTheme.shapes.sm)
                                    .background(NutraTheme.colors.surface)
                                    .clickable(role = Role.Button) { onEditLogItem(item) }
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
                                        text = "${item.formattedQuantity} · P ${item.proteinGrams.roundToInt()}g · K ${item.carbsGrams.roundToInt()}g · Y ${item.fatGrams.roundToInt()}g",
                                        style = NutraTheme.typography.caption,
                                        color = NutraTheme.colors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    text = "${NutraFormatters.formatInt(item.calories)} kcal",
                                    style = NutraTheme.typography.numberSmall,
                                    color = NutraTheme.colors.textPrimary,
                                )
                            }
                        }
                        if (idx < mealSummary.items.lastIndex) {
                            NutraDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditLoggedFoodPortionDialog(
    item: MealLogItem,
    onDismiss: () -> Unit,
    onUpdateMultiplier: (Float) -> Unit,
    onDelete: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val colors = NutraTheme.colors
    val isRawGramMode = item.unitLabel == "1 Gram"
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
        "${NutraFormatters.formatInt(multiplier.roundToInt())} g"
    } else {
        "${NutraFormatters.formatMultiplier(multiplier)}x"
    }
    val presets = if (isRawGramMode) listOf(50f, 100f, 150f, 200f) else listOf(0.5f, 1f, 1.5f, 2f)

    NutraDialog(
        onDismissRequest = onDismiss,
        title = item.foodName,
        message = if (isRawGramMode) {
            "Gramaj · ${NutraFormatters.formatInt(previewGrams)} g"
        } else {
            "${item.unitLabel} · ${NutraFormatters.formatInt(previewGrams)} g"
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
                        "${preset.toInt()} g"
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
                    contentDescription = if (isRawGramMode) "10 g azalt" else "Porsiyon azalt",
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
                            onClickLabel = if (isRawGramMode) "Gramaj yaz" else "Porsiyon çarpanı yaz",
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
                    contentDescription = if (isRawGramMode) "10 g artır" else "Porsiyon artır",
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
                title = "Gramaj",
                initialValue = multiplier.roundToInt().toString(),
                unit = "g",
                rangeHint = "1 – 3.000 g",
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
