package com.nutra.app.feature.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nutra.app.core.designsystem.CalorieDisplayMode
import com.nutra.app.core.designsystem.NutraCalorieArcRing
import com.nutra.app.core.designsystem.NutraCard
import com.nutra.app.core.designsystem.NutraChip
import com.nutra.app.core.designsystem.NutraMacroProgressBar
import com.nutra.app.core.designsystem.NutraTheme
import com.nutra.app.domain.model.DayGoalStatus
import com.nutra.app.domain.model.DayStatusIndicator
import com.nutra.app.domain.model.MealLogItem
import com.nutra.app.domain.model.MealSummary
import com.nutra.app.domain.model.MealType
import com.nutra.app.feature.logging.AddFoodModalSheet
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private val TR_LOCALE = Locale.forLanguageTag("tr-TR")

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current

    // Auto-dismiss status / undo banner after 4.5 seconds
    LaunchedEffect(state.lastDeletedItem, state.statusBannerMessage) {
        if (state.lastDeletedItem != null || state.statusBannerMessage != null) {
            delay(4500)
            viewModel.dismissUndoBanner()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NutraTheme.colors.background)
            .padding(contentPadding),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = NutraTheme.spacing.screenHorizontal,
                end = NutraTheme.spacing.screenHorizontal,
                top = 12.dp,
                bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(NutraTheme.spacing.medium),
        ) {
            // 1. Top Date Header & AI Quick Button
            item(key = "header") {
                DateNavigationHeader(
                    selectedDate = state.selectedDate,
                    onPreviousWeek = { viewModel.shiftWeek(-1) },
                    onNextWeek = { viewModel.shiftWeek(1) },
                    onJumpToToday = viewModel::jumpToToday,
                    onOpenAiQuickLog = {
                        val defaultMeal = inferCurrentMealType()
                        viewModel.openAddFoodModal(defaultMeal, LoggingTabMode.AI_ANALYZE)
                    },
                )
            }

            // 2. 7-Day Week Strip with Semantic Status Dots
            item(key = "week_strip") {
                WeekDateStrip(
                    weekDays = state.weekDays,
                    selectedDate = state.selectedDate,
                    statusMap = state.weekStatusMap,
                    onSelectDate = viewModel::selectDate,
                )
            }

            // 3. Bento Hero Summary Card (Calorie Ring + 3 Macro Bars)
            item(key = "bento_hero") {
                BentoHeroNutritionCard(
                    consumedCalories = state.dailySummary.consumedCalories,
                    targetCalories = state.dailySummary.goals.calories,
                    consumedProtein = state.dailySummary.consumedProtein,
                    targetProtein = state.dailySummary.goals.proteinGrams,
                    consumedCarbs = state.dailySummary.consumedCarbs,
                    targetCarbs = state.dailySummary.goals.carbsGrams,
                    consumedFat = state.dailySummary.consumedFat,
                    targetFat = state.dailySummary.goals.fatGrams,
                    displayMode = state.calorieDisplayMode,
                    onToggleDisplayMode = viewModel::toggleCalorieDisplayMode,
                )
            }

            // 4. Compact Hydration (Water) Card with Multi-Container Chips
            item(key = "water_card") {
                CompactWaterTrackerCard(
                    currentWaterMl = state.dailySummary.waterMl,
                    targetWaterMl = state.dailySummary.goals.waterMl,
                    onAddWater = { delta ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.addWater(delta)
                    },
                )
            }

            // 5. Section Header for Meals
            item(key = "meals_section_title") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Günlük Öğünler",
                        style = NutraTheme.typography.sectionTitle,
                        color = NutraTheme.colors.textPrimary,
                    )
                    Text(
                        text = "${state.dailySummary.meals.sumOf { it.items.size }} kayıt",
                        style = NutraTheme.typography.macroLabel,
                        color = NutraTheme.colors.textMuted,
                    )
                }
            }

            // 6. Accordion Meal Cards (Breakfast, Lunch, Dinner, Snack)
            state.dailySummary.meals.forEach { mealSummary ->
                item(key = "meal_${mealSummary.mealType.name}") {
                    AccordionMealCard(
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

        // Bottom Undo / Status Banner
        AnimatedVisibility(
            visible = state.lastDeletedItem != null || state.statusBannerMessage != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = NutraTheme.spacing.screenHorizontal, vertical = 14.dp),
        ) {
            NutraCard(
                shape = NutraTheme.shapes.innerSubCard,
                backgroundColor = NutraTheme.colors.textPrimary,
                borderColor = NutraTheme.colors.textPrimary,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val deleted = state.lastDeletedItem
                    Text(
                        text = if (deleted != null) {
                            "${deleted.foodName} silindi"
                        } else {
                            state.statusBannerMessage.orEmpty()
                        },
                        style = NutraTheme.typography.foodItemTitle,
                        color = NutraTheme.colors.background,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (deleted != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(NutraTheme.shapes.chip)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                    viewModel.undoDeleteLoggedItem()
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Undo,
                                contentDescription = null,
                                tint = NutraTheme.colors.calorieAccent,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = "Geri Al",
                                style = NutraTheme.typography.macroValue,
                                color = NutraTheme.colors.calorieAccent,
                            )
                        }
                    }
                }
            }
        }
    }

    // Quick Edit Logged Portion Dialog
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

    // Unified Add Food Modal Sheet
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
private fun DateNavigationHeader(
    selectedDate: LocalDate,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onJumpToToday: () -> Unit,
    onOpenAiQuickLog: () -> Unit,
) {
    val today = LocalDate.now()
    val isToday = selectedDate == today
    val dateLabel = when (selectedDate) {
        today -> "Bugün, ${selectedDate.dayOfMonth} ${selectedDate.month.getDisplayName(TextStyle.FULL, TR_LOCALE)}"
        today.minusDays(1) -> "Dün, ${selectedDate.dayOfMonth} ${selectedDate.month.getDisplayName(TextStyle.FULL, TR_LOCALE)}"
        today.plusDays(1) -> "Yarın, ${selectedDate.dayOfMonth} ${selectedDate.month.getDisplayName(TextStyle.FULL, TR_LOCALE)}"
        else -> "${selectedDate.dayOfMonth} ${selectedDate.month.getDisplayName(TextStyle.FULL, TR_LOCALE)} ${selectedDate.year}"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "Nutra",
                style = NutraTheme.typography.macroLabel,
                color = NutraTheme.colors.calorieAccent,
            )
            Text(
                text = dateLabel,
                style = NutraTheme.typography.screenHeader,
                color = NutraTheme.colors.textPrimary,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (!isToday) {
                NutraChip(
                    text = "Bugün",
                    selected = true,
                    onClick = onJumpToToday,
                )
            }
            NutraChip(
                text = "AI Ekle",
                selected = false,
                onClick = onOpenAiQuickLog,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = NutraTheme.colors.calorieAccent,
                        modifier = Modifier.size(15.dp),
                    )
                },
            )
            IconButton(
                onClick = onPreviousWeek,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = "Önceki Hafta",
                    tint = NutraTheme.colors.textSecondary,
                )
            }
            IconButton(
                onClick = onNextWeek,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = "Sonraki Hafta",
                    tint = NutraTheme.colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun WeekDateStrip(
    weekDays: List<LocalDate>,
    selectedDate: LocalDate,
    statusMap: Map<String, DayStatusIndicator>,
    onSelectDate: (LocalDate) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val today = LocalDate.now()

    NutraCard(
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            weekDays.forEach { day ->
                val isSelected = day == selectedDate
                val isToday = day == today
                val indicator = statusMap[day.toString()]
                val status = indicator?.status ?: DayGoalStatus.EMPTY

                val pillBg by animateColorAsState(
                    targetValue = when {
                        isSelected -> NutraTheme.colors.calorieAccent
                        isToday -> NutraTheme.colors.elevatedSurface
                        else -> Color.Transparent
                    },
                    label = "DayPillBg",
                )

                val dayName = day.dayOfWeek.getDisplayName(TextStyle.SHORT, TR_LOCALE)
                    .take(3)
                    .replaceFirstChar { it.uppercaseChar() }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(NutraTheme.shapes.innerSubCard)
                        .background(pillBg)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelectDate(day)
                        }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = dayName,
                        style = NutraTheme.typography.badgeLabel,
                        color = if (isSelected) Color.White.copy(alpha = 0.88f) else NutraTheme.colors.textMuted,
                    )
                    Text(
                        text = "${day.dayOfMonth}",
                        style = NutraTheme.typography.cardTitle,
                        color = if (isSelected) Color.White else NutraTheme.colors.textPrimary,
                    )
                    // 4dp Semantic Status Dot
                    val dotColor = when (status) {
                        DayGoalStatus.ON_TARGET -> if (isSelected) Color.White else NutraTheme.colors.calorieAccent
                        DayGoalStatus.OVER_BUDGET -> if (isSelected) Color.White else NutraTheme.colors.overBudgetAccent
                        DayGoalStatus.PARTIAL -> if (isSelected) Color.White.copy(alpha = 0.7f) else NutraTheme.colors.textMuted
                        DayGoalStatus.EMPTY -> Color.Transparent
                    }
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(NutraTheme.shapes.pill)
                            .background(dotColor),
                    )
                }
            }
        }
    }
}

@Composable
private fun BentoHeroNutritionCard(
    consumedCalories: Int,
    targetCalories: Int,
    consumedProtein: Float,
    targetProtein: Int,
    consumedCarbs: Float,
    targetCarbs: Int,
    consumedFat: Float,
    targetFat: Int,
    displayMode: CalorieDisplayMode,
    onToggleDisplayMode: () -> Unit,
) {
    val isOverBudget = consumedCalories > targetCalories
    val overAmount = consumedCalories - targetCalories

    NutraCard(
        shape = NutraTheme.shapes.heroCard,
        contentPadding = PaddingValues(NutraTheme.spacing.heroCardPadding),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NutraCalorieArcRing(
                    consumedCalories = consumedCalories,
                    targetCalories = targetCalories,
                    displayMode = displayMode,
                    onToggleDisplayMode = onToggleDisplayMode,
                    size = 144.dp,
                    strokeWidth = 12.dp,
                )

                Spacer(modifier = Modifier.width(18.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    NutraMacroProgressBar(
                        label = "Protein",
                        consumedGrams = consumedProtein,
                        targetGrams = targetProtein,
                        accentColor = NutraTheme.colors.proteinAccent,
                    )
                    NutraMacroProgressBar(
                        label = "Karbonhidrat",
                        consumedGrams = consumedCarbs,
                        targetGrams = targetCarbs,
                        accentColor = NutraTheme.colors.carbsAccent,
                    )
                    NutraMacroProgressBar(
                        label = "Yağ",
                        consumedGrams = consumedFat,
                        targetGrams = targetFat,
                        accentColor = NutraTheme.colors.fatAccent,
                    )
                }
            }

            // Concentric Footer Strip inside Hero Card (14dp inner radius)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(NutraTheme.shapes.innerSubCard)
                    .background(
                        if (isOverBudget) {
                            NutraTheme.colors.overBudgetContainer.copy(alpha = if (NutraTheme.colors.isDark) 0.45f else 0.7f)
                        } else {
                            NutraTheme.colors.elevatedSurface
                        },
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Alınan: $consumedCalories / $targetCalories kcal",
                    style = NutraTheme.typography.macroValue,
                    color = NutraTheme.colors.textPrimary,
                )
                Text(
                    text = if (isOverBudget) {
                        "+$overAmount kcal hedef üstü"
                    } else {
                        "Halkaya dokun: görünüm değiştir"
                    },
                    style = NutraTheme.typography.badgeLabel,
                    color = if (isOverBudget) {
                        NutraTheme.colors.overBudgetAccent
                    } else {
                        NutraTheme.colors.textMuted
                    },
                )
            }
        }
    }
}

@Composable
private fun CompactWaterTrackerCard(
    currentWaterMl: Int,
    targetWaterMl: Int,
    onAddWater: (Int) -> Unit,
) {
    val safeTarget = targetWaterMl.coerceAtLeast(1)
    val fraction = (currentWaterMl.toFloat() / safeTarget.toFloat()).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 360f),
        label = "WaterProgressFill",
    )

    NutraCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(NutraTheme.spacing.standard),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(NutraTheme.shapes.chip)
                            .background(NutraTheme.colors.waterContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WaterDrop,
                            contentDescription = null,
                            tint = NutraTheme.colors.waterAccent,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                    Column {
                        Text(
                            text = "Su Takibi",
                            style = NutraTheme.typography.cardTitle,
                            color = NutraTheme.colors.textPrimary,
                        )
                        Text(
                            text = "%${(fraction * 100).roundToInt()} tamamlandı",
                            style = NutraTheme.typography.bodySmall,
                            color = NutraTheme.colors.textSecondary,
                        )
                    }
                }

                Text(
                    text = "$currentWaterMl / $safeTarget ml",
                    style = NutraTheme.typography.cardTitle,
                    color = NutraTheme.colors.waterAccent,
                )
            }

            // Smooth Water Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(NutraTheme.shapes.pill)
                    .background(NutraTheme.colors.elevatedSurface),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedFraction.coerceAtLeast(0.02f))
                        .fillMaxHeight()
                        .clip(NutraTheme.shapes.pill)
                        .background(NutraTheme.colors.waterAccent),
                )
            }

            // Multi-Container Quick Add Chips (+200ml Bardak, +330ml Kupa, +500ml Şişe, -200ml)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WaterQuickChip(
                    label = "+200ml Bardak",
                    onClick = { onAddWater(200) },
                    modifier = Modifier.weight(1f),
                )
                WaterQuickChip(
                    label = "+330ml Kupa",
                    onClick = { onAddWater(330) },
                    modifier = Modifier.weight(1f),
                )
                WaterQuickChip(
                    label = "+500ml Şişe",
                    onClick = { onAddWater(500) },
                    modifier = Modifier.weight(1f),
                )
                if (currentWaterMl > 0) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(NutraTheme.shapes.chip)
                            .background(NutraTheme.colors.elevatedSurface)
                            .border(1.dp, NutraTheme.colors.hairlineBorder, NutraTheme.shapes.chip)
                            .clickable { onAddWater(-200) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Remove,
                            contentDescription = "200ml Azalt",
                            tint = NutraTheme.colors.textSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WaterQuickChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(NutraTheme.shapes.chip)
            .background(NutraTheme.colors.waterAccent.copy(alpha = if (NutraTheme.colors.isDark) 0.16f else 0.10f))
            .border(1.dp, NutraTheme.colors.waterAccent.copy(alpha = 0.45f), NutraTheme.shapes.chip)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = NutraTheme.typography.badgeLabel,
            color = NutraTheme.colors.waterAccent,
            maxLines = 1,
        )
    }
}

@Composable
private fun AccordionMealCard(
    mealSummary: MealSummary,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onAddFoodClick: () -> Unit,
    onEditLogItem: (MealLogItem) -> Unit,
    onDeleteLogItem: (MealLogItem) -> Unit,
) {
    val hasItems = mealSummary.items.isNotEmpty()

    NutraCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = hasItems, onClick = onToggleExpand),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = mealSummary.mealType.title,
                            style = NutraTheme.typography.cardTitle,
                            color = NutraTheme.colors.textPrimary,
                        )
                        if (hasItems) {
                            Text(
                                text = "${mealSummary.totalCalories} kcal",
                                style = NutraTheme.typography.macroValue,
                                color = NutraTheme.colors.calorieAccent,
                            )
                        } else {
                            Text(
                                text = "0 kcal",
                                style = NutraTheme.typography.macroLabel,
                                color = NutraTheme.colors.textMuted,
                            )
                        }
                    }

                    if (hasItems) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "P: ${mealSummary.totalProtein.roundToInt()}g · K: ${mealSummary.totalCarbs.roundToInt()}g · Y: ${mealSummary.totalFat.roundToInt()}g",
                            style = NutraTheme.typography.bodySmall,
                            color = NutraTheme.colors.textSecondary,
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (hasItems) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = null,
                            tint = NutraTheme.colors.textSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    // Ergonomic 40dp Add Button (Concentric 10dp radius)
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(NutraTheme.shapes.chip)
                            .background(NutraTheme.colors.calorieAccent.copy(alpha = if (NutraTheme.colors.isDark) 0.22f else 0.14f))
                            .border(1.dp, NutraTheme.colors.calorieAccent.copy(alpha = 0.55f), NutraTheme.shapes.chip)
                            .clickable(onClick = onAddFoodClick),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "${mealSummary.mealType.title} Besin Ekle",
                            tint = NutraTheme.colors.calorieAccent,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = hasItems && isExpanded,
                enter = expandVertically(animationSpec = spring(stiffness = 400f)) + fadeIn(),
                exit = shrinkVertically(animationSpec = spring(stiffness = 400f)) + fadeOut(),
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(color = NutraTheme.colors.hairlineBorder, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(6.dp))

                    mealSummary.items.forEach { item ->
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
                                        .clip(NutraTheme.shapes.chip)
                                        .background(NutraTheme.colors.proteinAccent.copy(alpha = 0.18f))
                                        .padding(horizontal = 14.dp),
                                    contentAlignment = Alignment.CenterEnd,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = "Sil",
                                        tint = NutraTheme.colors.proteinAccent,
                                    )
                                }
                            },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(NutraTheme.shapes.chip)
                                    .background(NutraTheme.colors.cardSurface)
                                    .clickable { onEditLogItem(item) }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.foodName,
                                        style = NutraTheme.typography.foodItemTitle,
                                        color = NutraTheme.colors.textPrimary,
                                    )
                                    Text(
                                        text = "${item.formattedQuantity} · P:${item.proteinGrams.roundToInt()}g K:${item.carbsGrams.roundToInt()}g Y:${item.fatGrams.roundToInt()}g",
                                        style = NutraTheme.typography.bodySmall,
                                        color = NutraTheme.colors.textSecondary,
                                    )
                                }
                                Text(
                                    text = "${item.calories} kcal",
                                    style = NutraTheme.typography.macroValue,
                                    color = NutraTheme.colors.textPrimary,
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

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NutraTheme.colors.cardSurface,
        title = {
            Text(
                text = item.foodName,
                style = NutraTheme.typography.cardTitle,
                color = NutraTheme.colors.textPrimary,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Birim: ${item.unitLabel} ($previewGrams g · $previewCalories kcal)",
                    style = NutraTheme.typography.bodyRegular,
                    color = NutraTheme.colors.calorieAccent,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(0.5f, 1f, 1.5f, 2f).forEach { preset ->
                        NutraChip(
                            text = "${if (preset % 1f == 0f) preset.toInt() else preset}x",
                            selected = multiplier == preset,
                            onClick = { multiplier = preset },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onUpdateMultiplier(multiplier) },
                colors = ButtonDefaults.buttonColors(containerColor = NutraTheme.colors.calorieAccent),
            ) {
                Text("Güncelle", color = Color.White)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) {
                    Text("Sil", color = NutraTheme.colors.proteinAccent)
                }
                TextButton(onClick = onDismiss) {
                    Text("İptal", color = NutraTheme.colors.textSecondary)
                }
            }
        },
    )
}

private fun inferCurrentMealType(): MealType {
    val hour = java.time.LocalTime.now().hour
    return when (hour) {
        in 5..10 -> MealType.BREAKFAST
        in 11..15 -> MealType.LUNCH
        in 17..21 -> MealType.DINNER
        else -> MealType.SNACK
    }
}
