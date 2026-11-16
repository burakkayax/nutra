package com.nutra.app.feature.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DinnerDining
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nutra.app.core.designsystem.NutraButton
import com.nutra.app.core.designsystem.NutraButtonSize
import com.nutra.app.core.designsystem.NutraButtonStyle
import com.nutra.app.core.designsystem.NutraCard
import com.nutra.app.core.designsystem.NutraDialog
import com.nutra.app.core.designsystem.NutraDivider
import com.nutra.app.core.designsystem.NutraDot
import com.nutra.app.core.designsystem.NutraIconBadge
import com.nutra.app.core.designsystem.NutraIconButton
import com.nutra.app.core.designsystem.NutraLinearProgress
import com.nutra.app.core.designsystem.NutraMacroStat
import com.nutra.app.core.designsystem.NutraScreenHeader
import com.nutra.app.core.designsystem.NutraSectionHeader
import com.nutra.app.core.designsystem.NutraSnackbarHost
import com.nutra.app.core.designsystem.NutraTheme
import com.nutra.app.domain.model.DayGoalStatus
import com.nutra.app.domain.model.DayStatusIndicator
import com.nutra.app.domain.model.MealLogItem
import com.nutra.app.domain.model.MealSummary
import com.nutra.app.domain.model.MealType
import com.nutra.app.feature.logging.AddFoodModalSheet
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private val TR_LOCALE = Locale.forLanguageTag("tr-TR")

private fun formatInt(value: Int): String =
    NumberFormat.getIntegerInstance(TR_LOCALE).format(value)

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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

    LaunchedEffect(state.lastDeletedItem, state.statusBannerMessage) {
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
            verticalArrangement = Arrangement.spacedBy(NutraTheme.spacing.lg),
        ) {
            item(key = "header") {
                Column {
                    DashboardHeader(
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
                                weekDays = state.weekDays,
                                selectedDate = state.selectedDate,
                                statusMap = state.weekStatusMap,
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

            item(key = "meals_header") {
                val totalEntries = state.dailySummary.meals.sumOf { it.items.size }
                NutraSectionHeader(
                    title = "Öğünler",
                    supportingText = if (totalEntries > 0) "$totalEntries kayıt" else null,
                    modifier = Modifier.padding(top = NutraTheme.spacing.xs),
                )
            }

            state.dailySummary.meals.forEach { mealSummary ->
                item(key = "meal_${mealSummary.mealType.name}") {
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
        AddFoodModalSheet(
            state = state,
            onDismiss = viewModel::closeAddFoodModal,
            onSelectMealType = { viewModel.openAddFoodModal(it, state.loggingTabMode) },
            onSelectTab = viewModel::selectLoggingTab,
            onSearchQueryChange = viewModel::updateSearchQuery,
            onSelectCategory = viewModel::selectCategory,
            onSelectFoodForPortion = viewModel::openPortionPicker,
            onClosePortionPicker = viewModel::closePortionPicker,
            onToggleFavorite = viewModel::toggleFavorite,
            onConfirmLogFood = viewModel::logFoodWithPortion,
            onLogSmartCombo = viewModel::logSmartCombo,
            onAiPromptChange = viewModel::updateAiPromptText,
            onRunAiAnalysis = viewModel::analyzeWithAi,
            onUpdateAiItemMultiplier = viewModel::updateAiItemMultiplier,
            onToggleAiItemIncluded = viewModel::toggleAiItemIncluded,
            onCommitAiItems = viewModel::commitAiAnalyzedItems,
            onBarcodeInputChange = viewModel::updateBarcodeInput,
            onLookupBarcode = viewModel::lookupBarcode,
            onCreateAndLogCustomFood = viewModel::createAndLogCustomFood,
        )
    }
}

@Composable
private fun DashboardHeader(
    selectedDate: LocalDate,
    isWeekCalendarVisible: Boolean,
    onToggleWeekCalendar: () -> Unit,
) {
    val today = LocalDate.now()
    val isToday = selectedDate == today
    val dayOfWeek = selectedDate.dayOfWeek.getDisplayName(TextStyle.FULL, TR_LOCALE)
        .replaceFirstChar { it.uppercaseChar() }
    val monthName = selectedDate.month.getDisplayName(TextStyle.FULL, TR_LOCALE)
    val eyebrow = if (selectedDate.year == today.year) {
        "$dayOfWeek, ${selectedDate.dayOfMonth} $monthName"
    } else {
        "$dayOfWeek, ${selectedDate.dayOfMonth} $monthName ${selectedDate.year}"
    }
    val title = when (selectedDate) {
        today -> "Bugün"
        today.minusDays(1) -> "Dün"
        today.plusDays(1) -> "Yarın"
        else -> "${selectedDate.dayOfMonth} $monthName"
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
                icon = Icons.Rounded.CalendarMonth,
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
    weekDays: List<LocalDate>,
    selectedDate: LocalDate,
    statusMap: Map<String, DayStatusIndicator>,
    onSelectDate: (LocalDate) -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onJumpToToday: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val today = LocalDate.now()
    val monthLabel = "${
        selectedDate.month.getDisplayName(TextStyle.FULL, TR_LOCALE)
            .replaceFirstChar { it.uppercaseChar() }
    } ${selectedDate.year}"
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
                if (!isToday) {
                    Text(
                        text = "Bugüne dön",
                        style = NutraTheme.typography.labelSmall,
                        color = NutraTheme.colors.accent,
                        modifier = Modifier
                            .clip(NutraTheme.shapes.pill)
                            .background(NutraTheme.colors.accentSoft)
                            .clickable(role = Role.Button) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onJumpToToday()
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                NutraIconButton(
                    icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = "Önceki hafta",
                    onClick = onPreviousWeek,
                    size = 34.dp,
                    iconSize = 18.dp,
                    containerColor = NutraTheme.colors.surfaceMuted,
                    contentColor = NutraTheme.colors.textSecondary,
                )
                NutraIconButton(
                    icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = "Sonraki hafta",
                    onClick = onNextWeek,
                    size = 34.dp,
                    iconSize = 18.dp,
                    containerColor = NutraTheme.colors.surfaceMuted,
                    contentColor = NutraTheme.colors.textSecondary,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            weekDays.forEach { day ->
                val isSelected = day == selectedDate
                val isDayToday = day == today
                val status = statusMap[day.toString()]?.status ?: DayGoalStatus.EMPTY

                val circleBg by animateColorAsState(
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

                val dayShort = day.dayOfWeek.getDisplayName(TextStyle.SHORT, TR_LOCALE)
                    .trimEnd('.')
                    .take(3)
                    .replaceFirstChar { it.uppercaseChar() }

                val dotColor = if (isDayToday) {
                    Color.Transparent
                } else {
                    when (status) {
                        DayGoalStatus.ON_TARGET -> NutraTheme.colors.accent
                        DayGoalStatus.OVER_BUDGET -> NutraTheme.colors.warning
                        DayGoalStatus.PARTIAL -> NutraTheme.colors.textTertiary
                        DayGoalStatus.EMPTY -> Color.Transparent
                    }
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
                            .background(circleBg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "${day.dayOfMonth}",
                            style = NutraTheme.typography.titleSmall,
                            color = dayNumberColor,
                            maxLines = 1,
                        )
                    }
                    NutraDot(color = dotColor, size = 5.dp)
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
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = formatInt(consumedCalories),
                        style = NutraTheme.typography.numberLarge,
                        color = NutraTheme.colors.textPrimary,
                    )
                    Text(
                        text = "/ ${formatInt(safeTarget)} kcal",
                        style = NutraTheme.typography.body,
                        color = NutraTheme.colors.textSecondary,
                        modifier = Modifier.padding(bottom = 3.dp),
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
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
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

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NutraIconBadge(
                    icon = Icons.Rounded.WaterDrop,
                    tint = NutraTheme.colors.water,
                    container = NutraTheme.colors.waterSoft,
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
            }

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = formatInt(currentWaterMl),
                    style = NutraTheme.typography.numberMedium,
                    color = NutraTheme.colors.textPrimary,
                )
                Text(
                    text = " / ${formatInt(safeTarget)} ml",
                    style = NutraTheme.typography.caption,
                    color = NutraTheme.colors.textTertiary,
                    modifier = Modifier.padding(bottom = 2.dp),
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
                icon = Icons.Rounded.Remove,
                contentDescription = "200 ml azalt",
                onClick = { onAddWater(-200) },
                size = 44.dp,
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
            .height(44.dp)
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
            style = NutraTheme.typography.overline.copy(letterSpacing = NutraTheme.typography.caption.letterSpacing),
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
        MealType.BREAKFAST -> Icons.Rounded.WbSunny
        MealType.LUNCH -> Icons.Rounded.Restaurant
        MealType.DINNER -> Icons.Rounded.DinnerDining
        MealType.SNACK -> Icons.Rounded.LocalCafe
    }

    NutraCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
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
                                imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
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
            Column(modifier = Modifier.padding(top = 14.dp)) {
                NutraDivider()
                mealSummary.items.forEachIndexed { idx, item ->
                    key(item.id) {
                        val dismissState = rememberSwipeToDismissBoxState()

                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = false,
                            onDismiss = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart) {
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
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = "Sil",
                                        tint = NutraTheme.colors.danger,
                                    )
                                }
                            },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
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
    var multiplier by remember(item) { mutableFloatStateOf(item.multiplier) }
    val ratio = multiplier / item.multiplier.coerceAtLeast(0.05f)
    val previewCalories = (item.calories * ratio).roundToInt()
    val previewGrams = (item.totalGrams * ratio).roundToInt()
    val multLabel = if (multiplier % 1f == 0f) {
        "${multiplier.toInt()}x"
    } else {
        "${String.format(Locale.US, "%.2f", multiplier).trimEnd('0').trimEnd('.')}x"
    }

    NutraDialog(
        onDismissRequest = onDismiss,
        title = item.foodName,
        message = "${item.unitLabel} · $previewGrams g",
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
                listOf(0.5f, 1f, 1.5f, 2f).forEach { preset ->
                    val isSelected = multiplier == preset
                    val label = "${if (preset % 1f == 0f) preset.toInt() else preset}x"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(NutraTheme.shapes.pill)
                            .background(
                                if (isSelected) NutraTheme.colors.ink else NutraTheme.colors.surfaceMuted
                            )
                            .clickable(role = Role.RadioButton) { multiplier = preset }
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style = NutraTheme.typography.labelSmall,
                            color = if (isSelected) NutraTheme.colors.onInk else NutraTheme.colors.textPrimary,
                            maxLines = 1,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(NutraTheme.shapes.md)
                    .background(NutraTheme.colors.surfaceMuted)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NutraStepperButton(
                    icon = Icons.Rounded.Remove,
                    contentDescription = "Porsiyon azalt",
                    onClick = { multiplier = (multiplier - 0.25f).coerceAtLeast(0.25f) },
                    containerColorOverride = NutraTheme.colors.surfaceElevated,
                )
                Text(
                    text = multLabel,
                    style = NutraTheme.typography.numberMedium,
                    color = NutraTheme.colors.textPrimary,
                )
                NutraStepperButton(
                    icon = Icons.Rounded.Add,
                    contentDescription = "Porsiyon artır",
                    onClick = { multiplier = (multiplier + 0.25f).coerceAtMost(20f) },
                    containerColorOverride = NutraTheme.colors.surfaceElevated,
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
}

@Composable
private fun NutraStepperButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    containerColorOverride: Color,
) {
    NutraIconButton(
        icon = icon,
        contentDescription = contentDescription,
        onClick = onClick,
        size = 40.dp,
        iconSize = 18.dp,
        containerColor = containerColorOverride,
        contentColor = NutraTheme.colors.textPrimary,
    )
}
