package com.nutra.app.feature.dashboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
import com.nutra.app.core.designsystem.NutraDialog
import com.nutra.app.core.designsystem.NutraDivider
import com.nutra.app.core.designsystem.NutraFormatters
import com.nutra.app.core.designsystem.NutraIconBadge
import com.nutra.app.core.designsystem.NutraIconButton
import com.nutra.app.core.designsystem.NutraIcons
import com.nutra.app.core.designsystem.NutraLinearProgress
import com.nutra.app.core.designsystem.NutraMacroStat
import com.nutra.app.core.designsystem.NutraNumericInputDialog
import com.nutra.app.core.designsystem.NutraScreenHeader
import com.nutra.app.core.designsystem.NutraSectionHeader
import com.nutra.app.core.designsystem.NutraSnackbarHost
import com.nutra.app.core.designsystem.NutraStepperButton
import com.nutra.app.core.designsystem.NutraTheme
import com.nutra.app.domain.model.MealLogItem
import com.nutra.app.domain.model.MealSummary
import com.nutra.app.domain.model.MealType
import com.nutra.app.feature.logging.AddFoodModalSheet
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

private fun formatInt(value: Int): String = NutraFormatters.formatInt(value)

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        viewModel.refreshToday()
    }
    if (state.isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(NutraTheme.colors.background),
        )
        return
    }
    val haptic = LocalHapticFeedback.current
    val layoutDirection = LocalLayoutDirection.current

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
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection) + NutraTheme.spacing.screen,
                end = contentPadding.calculateEndPadding(layoutDirection) + NutraTheme.spacing.screen,
                top = contentPadding.calculateTopPadding() + 28.dp,
                bottom = contentPadding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(NutraTheme.spacing.xl),
        ) {
            item(key = "header") {
                Column {
                    DashboardHeader(
                        today = state.today,
                        selectedDate = state.selectedDate,
                        isWeekCalendarVisible = state.isWeekCalendarVisible,
                        onToggleWeekCalendar = viewModel::toggleWeekCalendar,
                    )

                    AnimatedVisibility(
                        visible = state.isWeekCalendarVisible,
                        enter = expandVertically(tween(220)) + fadeIn(tween(180)),
                        exit = shrinkVertically(tween(180)) + fadeOut(tween(120)),
                    ) {
                        Box(modifier = Modifier.padding(top = NutraTheme.spacing.lg)) {
                            WeekDateStrip(
                                today = state.today,
                                weekDays = state.weekDays,
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

            item(key = "water_card") {
                WaterTrackerCard(
                    currentWaterMl = state.dailySummary.waterMl,
                    targetWaterMl = state.dailySummary.goals.waterMl,
                    onAddWater = { delta ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.addWater(delta)
                    },
                )
            }

            item(key = "meals_section") {
                Column(verticalArrangement = Arrangement.spacedBy(NutraTheme.spacing.md)) {
                    NutraSectionHeader(title = "Öğünler")
                    state.dailySummary.meals.forEach { mealSummary ->
                        key(mealSummary.mealType.name) {
                            MealCard(
                                mealSummary = mealSummary,
                                isExpanded = state.expandedMeals.contains(mealSummary.mealType),
                                onToggleExpand = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.toggleMealExpanded(mealSummary.mealType)
                                },
                                onAddFoodClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.openAddFoodModal(mealSummary.mealType)
                                },
                                onEditLogItem = viewModel::openEditLoggedItem,
                                onDeleteLogItem = { item ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.deleteLoggedItem(item)
                                },
                            )
                        }
                    }
                }
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
            icon = if (deletedItem == null && bannerVisible) Icons.Rounded.Check else null,
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
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                viewModel.updateLoggedItemMultiplier(editingItem, newMult)
            },
            onDelete = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
            onSelectMealType = viewModel::selectLoggingMealType,
            onSelectTab = viewModel::selectLoggingTab,
            onSearchQueryChange = viewModel::updateSearchQuery,
            onSelectCategory = viewModel::selectCategory,
            onSelectFoodForPortion = viewModel::openPortionPicker,
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
private fun DashboardHeader(
    today: LocalDate,
    selectedDate: LocalDate,
    isWeekCalendarVisible: Boolean,
    onToggleWeekCalendar: () -> Unit,
) {
    val isToday = selectedDate == today
    val (eyebrow, title) = remember(selectedDate, today) {
        val dayOfWeek = selectedDate.dayOfWeek.getDisplayName(TextStyle.FULL, NutraFormatters.TR_LOCALE)
            .replaceFirstChar { it.uppercaseChar() }
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
            !isToday -> NutraTheme.colors.accent
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
            NutraIconButton(
                icon = NutraIcons.CalendarMonth,
                contentDescription = if (isWeekCalendarVisible) {
                    "Hafta takvimini gizle"
                } else {
                    "Hafta takvimini göster"
                },
                onClick = onToggleWeekCalendar,
                size = 40.dp,
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
    weekDays: List<LocalDate>,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onJumpToToday: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val monthLabel = remember(selectedDate) {
        "${
            selectedDate.month.getDisplayName(TextStyle.FULL, NutraFormatters.TR_LOCALE)
                .replaceFirstChar { it.uppercaseChar() }
        } ${selectedDate.year}"
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
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Bugüne dön",
                            style = NutraTheme.typography.labelSmall,
                            color = NutraTheme.colors.accent,
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

        val weekStart = remember(weekDays, selectedDate) {
            weekDays.firstOrNull() ?: selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
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
                            isDayToday -> NutraTheme.colors.accent
                            else -> NutraTheme.colors.textPrimary
                        },
                        animationSpec = tween(160),
                        label = "dayText",
                    )

                    val dayShort = remember(day) {
                        day.dayOfWeek.getDisplayName(TextStyle.SHORT, NutraFormatters.TR_LOCALE)
                            .trimEnd('.')
                            .take(3)
                            .replaceFirstChar { it.uppercaseChar() }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(NutraTheme.shapes.md)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
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
    val completionPercent = ((consumedCalories.toFloat() / safeTarget) * 100f).roundToInt()
    val progress = consumedCalories.toFloat() / safeTarget
    val barColor by animateColorAsState(
        targetValue = if (isOver) NutraTheme.colors.warning else NutraTheme.colors.accent,
        animationSpec = tween(220),
        label = "calorieBarColor",
    )

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
                        text = formatInt(consumedCalories),
                        style = NutraTheme.typography.numberHero,
                        color = NutraTheme.colors.textPrimary,
                        modifier = Modifier.alignByBaseline(),
                    )
                    Text(
                        text = "/ ${formatInt(safeTarget)} kcal",
                        style = NutraTheme.typography.body,
                        color = NutraTheme.colors.textSecondary,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
            }

            NutraLinearProgress(
                progress = progress,
                color = barColor,
                height = 8.dp,
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
                        text = "${formatInt(deltaCalories)} kcal",
                        style = NutraTheme.typography.numberSmall,
                        color = if (isOver) NutraTheme.colors.warning else NutraTheme.colors.textPrimary,
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
                label = "Karb",
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
    val fraction = (currentWaterMl.toFloat() / safeTarget.toFloat()).coerceIn(0f, 1f)

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
                    text = "%${(fraction * 100).roundToInt()} tamamlandı",
                    style = NutraTheme.typography.caption,
                    color = NutraTheme.colors.textTertiary,
                )
            }

            Row {
                Text(
                    text = formatInt(currentWaterMl),
                    style = NutraTheme.typography.numberMedium,
                    color = NutraTheme.colors.textPrimary,
                    modifier = Modifier.alignByBaseline(),
                )
                Text(
                    text = " / ${formatInt(safeTarget)} ml",
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
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WaterQuickPill(
                amountText = "+200 ml",
                containerLabel = "Bardak",
                onClick = { onAddWater(200) },
                modifier = Modifier.weight(1f),
            )
            WaterQuickPill(
                amountText = "+330 ml",
                containerLabel = "Kupa",
                onClick = { onAddWater(330) },
                modifier = Modifier.weight(1f),
            )
            WaterQuickPill(
                amountText = "+500 ml",
                containerLabel = "Şişe",
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
    containerLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .height(48.dp)
            .clip(NutraTheme.shapes.pill)
            .background(NutraTheme.colors.surfaceMuted)
            .clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = amountText,
            style = NutraTheme.typography.labelSmall,
            color = NutraTheme.colors.textPrimary,
            maxLines = 1,
        )
        Text(
            text = containerLabel,
            style = NutraTheme.typography.caption,
            color = NutraTheme.colors.textTertiary,
            maxLines = 1,
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
) {
    val hasItems = mealSummary.items.isNotEmpty()
    val mealIcon = when (mealSummary.mealType) {
        MealType.BREAKFAST -> NutraIcons.WbSunny
        MealType.LUNCH -> NutraIcons.Restaurant
        MealType.DINNER -> NutraIcons.DinnerDining
        MealType.SNACK -> NutraIcons.LocalCafe
    }

    NutraCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = NutraTheme.spacing.card, vertical = NutraTheme.spacing.lg),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                ) {
                    if (hasItems) onToggleExpand() else onAddFoodClick()
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
                                imageVector = if (isExpanded) NutraIcons.ExpandLess else NutraIcons.ExpandMore,
                                contentDescription = if (isExpanded) "Daralt" else "Genişlet",
                                tint = NutraTheme.colors.textTertiary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

                    Text(
                        text = if (hasItems) {
                            "P ${mealSummary.totalProtein.roundToInt()}g · K ${mealSummary.totalCarbs.roundToInt()}g · Y ${mealSummary.totalFat.roundToInt()}g"
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
                        text = "${formatInt(mealSummary.totalCalories)} kcal",
                        style = NutraTheme.typography.numberSmall,
                        color = NutraTheme.colors.textPrimary,
                    )
                }
                NutraIconButton(
                    icon = Icons.Rounded.Add,
                    contentDescription = "${mealSummary.mealType.title} besin ekle",
                    onClick = onAddFoodClick,
                    size = 40.dp,
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
                modifier = Modifier
                    .padding(top = 14.dp)
                    .animateContentSize(tween(180)),
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
                                        tint = NutraTheme.colors.danger,
                                    )
                                }
                            },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(NutraTheme.shapes.sm)
                                    .background(NutraTheme.colors.surface)
                                    .clickable(role = Role.Button) { onEditLogItem(item) }
                                    .padding(vertical = 12.dp),
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
                                    text = "${formatInt(item.calories)} kcal",
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
    val isRawGramMode = item.unitLabel == "1 Gram"
    var multiplier by remember(item) {
        mutableFloatStateOf(
            if (isRawGramMode) item.totalGrams.coerceIn(1f, 3000f) else item.multiplier.coerceIn(0.25f, 20f),
        )
    }
    var showManualGramDialog by remember(item) { mutableStateOf(false) }

    val ratio = if (isRawGramMode) {
        multiplier / item.totalGrams.coerceAtLeast(1f)
    } else {
        multiplier / item.multiplier.coerceAtLeast(0.05f)
    }
    val previewCalories = (item.calories * ratio).roundToInt()
    val previewGrams = if (isRawGramMode) {
        multiplier.roundToInt()
    } else {
        (item.totalGrams * ratio).roundToInt()
    }
    val valueLabel = if (isRawGramMode) {
        "${multiplier.roundToInt()} g"
    } else {
        "${NutraFormatters.formatMultiplier(multiplier)}x"
    }
    val presets = if (isRawGramMode) listOf(50f, 100f, 150f, 200f) else listOf(0.5f, 1f, 1.5f, 2f)

    NutraDialog(
        onDismissRequest = onDismiss,
        title = item.foodName,
        message = if (isRawGramMode) "Gramaj · $previewGrams g" else "${item.unitLabel} · $previewGrams g",
        confirmText = null,
        dismissText = null,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = "${formatInt(previewCalories)} kcal",
                style = NutraTheme.typography.numberLarge,
                color = NutraTheme.colors.accent,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

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
                        targetValue = if (isSelected) NutraTheme.colors.ink else NutraTheme.colors.surfaceMuted,
                        animationSpec = tween(160),
                        label = "editPortionPillBg",
                    )
                    val fg by animateColorAsState(
                        targetValue = if (isSelected) NutraTheme.colors.onInk else NutraTheme.colors.textPrimary,
                        animationSpec = tween(160),
                        label = "editPortionPillFg",
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(NutraTheme.shapes.pill)
                            .drawBehind { drawRect(bgState.value) }
                            .clickable(role = Role.RadioButton) { multiplier = preset }
                            .padding(horizontal = 6.dp),
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
                    .background(NutraTheme.colors.surfaceMuted)
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
                    containerColor = NutraTheme.colors.surfaceElevated,
                )
                Box(
                    modifier = Modifier
                        .clip(NutraTheme.shapes.pill)
                        .then(
                            if (isRawGramMode) {
                                Modifier.clickable(role = Role.Button) { showManualGramDialog = true }
                            } else {
                                Modifier
                            },
                        )
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = valueLabel,
                        style = NutraTheme.typography.numberMedium,
                        color = NutraTheme.colors.textPrimary,
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
                    containerColor = NutraTheme.colors.surfaceElevated,
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
                    modifier = Modifier.weight(0.42f),
                )
                NutraButton(
                    text = "Güncelle",
                    onClick = { onUpdateMultiplier(multiplier) },
                    style = NutraButtonStyle.Primary,
                    size = NutraButtonSize.Medium,
                    modifier = Modifier.weight(0.58f),
                )
            }
        }
    }

    if (showManualGramDialog && isRawGramMode) {
        NutraNumericInputDialog(
            title = "Gramaj",
            initialValue = multiplier.roundToInt().toString(),
            unit = "g",
            rangeHint = "1 – 3.000 g",
            minValue = 1.0,
            maxValue = 3000.0,
            allowDecimal = true,
            onDismiss = { showManualGramDialog = false },
            onConfirm = { entered ->
                multiplier = entered.toFloat().coerceIn(1f, 3000f)
                showManualGramDialog = false
            },
        )
    }
}
