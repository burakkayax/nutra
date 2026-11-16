package com.nutra.app.feature.analytics

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nutra.app.core.designsystem.NutraButton
import com.nutra.app.core.designsystem.NutraButtonSize
import com.nutra.app.core.designsystem.NutraButtonStyle
import com.nutra.app.core.designsystem.NutraCard
import com.nutra.app.core.designsystem.NutraDialog
import com.nutra.app.core.designsystem.NutraDivider
import com.nutra.app.core.designsystem.NutraDot
import com.nutra.app.core.designsystem.NutraEmptyState
import com.nutra.app.core.designsystem.NutraFormatters
import com.nutra.app.core.designsystem.NutraIconButton
import com.nutra.app.core.designsystem.NutraIcons
import com.nutra.app.core.designsystem.NutraNumericInputDialog
import com.nutra.app.core.designsystem.NutraScreenHeader
import com.nutra.app.core.designsystem.NutraSegmentedControl
import com.nutra.app.core.designsystem.NutraTheme
import com.nutra.app.domain.model.DailyAnalyticsPoint
import com.nutra.app.domain.model.GoalType
import com.nutra.app.domain.model.MacroGoals
import com.nutra.app.domain.model.UserProfile
import com.nutra.app.domain.model.WeightTrendPoint
import kotlin.math.abs
import kotlin.math.roundToInt

private fun formatInt(value: Int): String = NutraFormatters.formatInt(value)

private fun formatKg(value: Float): String = NutraFormatters.formatKg(value)

private fun formatWaterLitres(ml: Int): String = NutraFormatters.formatWaterLitres(ml)

private fun formatShortDate(dateIso: String?): String = NutraFormatters.formatShortDate(dateIso)

private fun formatShortWeekday(dateIso: String): String = NutraFormatters.formatShortWeekday(dateIso)

@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val layoutDirection = LocalLayoutDirection.current

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshToday()
    }

    if (uiState.isLoading) {
        AnalyticsLoadingSkeleton(
            selectedRange = uiState.selectedRange,
            contentPadding = contentPadding,
            onSelectRange = viewModel::selectRange,
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NutraTheme.colors.background),
        contentPadding = PaddingValues(
            start = contentPadding.calculateStartPadding(layoutDirection) + NutraTheme.spacing.screen,
            end = contentPadding.calculateEndPadding(layoutDirection) + NutraTheme.spacing.screen,
            top = contentPadding.calculateTopPadding() + 28.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                NutraScreenHeader(
                    title = "İlerleme",
                    eyebrow = "Beslenme ve kilo özeti",
                )

                NutraSegmentedControl(
                    items = AnalyticsRangeOption.entries,
                    selectedItem = uiState.selectedRange,
                    onItemSelected = viewModel::selectRange,
                    labelProvider = { it.title },
                )
            }
        }

        item(key = "calorie_chart") {
            HapticScrubbingCalorieChartCard(
                points = uiState.dailyPoints,
                scrubbedIndex = uiState.scrubbedDayIndex,
                activePoint = uiState.activeDayPoint,
                targetCalories = uiState.profile.goals.calories,
                averageCalories = uiState.averageCalories,
                selectedRange = uiState.selectedRange,
                onScrub = viewModel::updateScrubbedDayIndex,
            )
        }

        item(key = "macro_split") {
            MacroSplitComparisonCard(
                activeDay = uiState.activeDayPoint,
                goals = uiState.profile.goals,
            )
        }

        item(key = "weight_trend") {
            WeightTrendBezierCard(
                points = uiState.weightTrendPoints,
                scrubbedIndex = uiState.scrubbedWeightIndex,
                profile = uiState.profile,
                onScrub = viewModel::updateScrubbedWeightIndex,
                onAddWeightClick = viewModel::openAddWeightDialog,
            )
        }

        item(key = "water_chart") {
            HapticScrubbingWaterChartCard(
                points = uiState.dailyPoints,
                scrubbedIndex = uiState.scrubbedWaterIndex,
                activePoint = uiState.activeWaterDayPoint,
                targetWaterMl = uiState.profile.goals.waterMl,
                averageWaterMl = uiState.averageWaterMl,
                selectedRange = uiState.selectedRange,
                onScrub = viewModel::updateScrubbedWaterIndex,
            )
        }
    }

    if (uiState.showAddWeightDialog) {
        val latestWeight = uiState.weightTrendPoints.lastOrNull()?.rawWeightKg
            ?: uiState.profile.currentWeightKg
        LogWeightDialog(
            initialWeight = latestWeight,
            onDismiss = viewModel::closeAddWeightDialog,
            onSave = viewModel::logNewWeight,
        )
    }
}

@Composable
private fun AnalyticsLoadingSkeleton(
    selectedRange: AnalyticsRangeOption,
    contentPadding: PaddingValues,
    onSelectRange: (AnalyticsRangeOption) -> Unit,
) {
    val colors = NutraTheme.colors
    val layoutDirection = LocalLayoutDirection.current
    val placeholderHeights = listOf(180.dp, 112.dp, 180.dp, 180.dp)
    val infiniteTransition = rememberInfiniteTransition(label = "analyticsSkeletonPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(
                start = contentPadding.calculateStartPadding(layoutDirection) + NutraTheme.spacing.screen,
                end = contentPadding.calculateEndPadding(layoutDirection) + NutraTheme.spacing.screen,
                top = contentPadding.calculateTopPadding() + 28.dp,
                bottom = contentPadding.calculateBottomPadding() + 16.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            NutraScreenHeader(
                title = "İlerleme",
                eyebrow = "Beslenme ve kilo özeti",
            )

            NutraSegmentedControl(
                items = AnalyticsRangeOption.entries,
                selectedItem = selectedRange,
                onItemSelected = onSelectRange,
                labelProvider = { it.title },
            )
        }

        placeholderHeights.forEach { chartHeight ->
            NutraCard {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.38f)
                        .height(14.dp)
                        .alpha(pulseAlpha)
                        .clip(NutraTheme.shapes.pill)
                        .background(colors.surfaceMuted),
                )
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.54f)
                        .height(26.dp)
                        .alpha(pulseAlpha)
                        .clip(NutraTheme.shapes.sm)
                        .background(colors.surfaceMuted),
                )
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(chartHeight)
                        .alpha(pulseAlpha)
                        .clip(NutraTheme.shapes.sm)
                        .background(colors.surfaceMuted),
                )
            }
        }
    }
}

@Composable
private fun HapticScrubbingCalorieChartCard(
    points: List<DailyAnalyticsPoint>,
    scrubbedIndex: Int?,
    activePoint: DailyAnalyticsPoint?,
    targetCalories: Int,
    averageCalories: Int,
    selectedRange: AnalyticsRangeOption,
    onScrub: (Int?) -> Unit,
) {
    val colors = NutraTheme.colors

    NutraCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = if (scrubbedIndex != null && activePoint != null) {
                        formatShortDate(activePoint.dateIso)
                    } else {
                        "Kalori geçmişi"
                    },
                    style = NutraTheme.typography.caption,
                    color = if (scrubbedIndex != null) colors.accent else colors.textSecondary,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = formatInt(activePoint?.calories ?: 0),
                        style = NutraTheme.typography.numberLarge,
                        color = colors.textPrimary,
                        modifier = Modifier.alignByBaseline(),
                    )
                    Text(
                        text = "/ ${formatInt(activePoint?.targetCalories ?: targetCalories)} kcal",
                        style = NutraTheme.typography.body,
                        color = colors.textSecondary,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
            }

            Text(
                text = "Ort. ${formatInt(averageCalories)} kcal",
                style = NutraTheme.typography.caption,
                color = colors.textSecondary,
            )
        }

        if (points.isEmpty()) {
            NutraEmptyState(
                icon = NutraIcons.MonitorWeight,
                title = "Henüz kalori kaydı yok",
                message = "Bugün sekmesinden öğün eklediğinde grafik oluşur.",
                modifier = Modifier.padding(top = 16.dp),
            )
            return@NutraCard
        }

        val maxChartCalories = remember(points, targetCalories) {
            val peak = points.maxOfOrNull { maxOf(it.calories, it.targetCalories) } ?: targetCalories
            (maxOf(peak, targetCalories) * 1.15f).coerceAtLeast(800f)
        }

        HapticScrubbingBarChart(
            points = points,
            scrubbedIndex = scrubbedIndex,
            activePoint = activePoint,
            valueProvider = { it.calories },
            targetValue = targetCalories,
            maxChartValue = maxChartCalories,
            barColorProvider = { pt ->
                when {
                    pt.calories == 0 -> colors.surfaceMuted
                    pt.calories > pt.targetCalories -> colors.warning
                    else -> colors.accent
                }
            },
            selectedRange = selectedRange,
            targetSummaryText = "Hedef: ${formatInt(targetCalories)} kcal",
            onScrub = onScrub,
        )
    }
}

@Composable
private fun HapticScrubbingWaterChartCard(
    points: List<DailyAnalyticsPoint>,
    scrubbedIndex: Int?,
    activePoint: DailyAnalyticsPoint?,
    targetWaterMl: Int,
    averageWaterMl: Int,
    selectedRange: AnalyticsRangeOption,
    onScrub: (Int?) -> Unit,
) {
    val colors = NutraTheme.colors

    NutraCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = if (scrubbedIndex != null && activePoint != null) {
                        formatShortDate(activePoint.dateIso)
                    } else {
                        "Su geçmişi"
                    },
                    style = NutraTheme.typography.caption,
                    color = if (scrubbedIndex != null) colors.accent else colors.textSecondary,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = formatInt(activePoint?.waterMl ?: 0),
                        style = NutraTheme.typography.numberLarge,
                        color = colors.textPrimary,
                        modifier = Modifier.alignByBaseline(),
                    )
                    Text(
                        text = "/ ${formatInt(targetWaterMl)} ml",
                        style = NutraTheme.typography.body,
                        color = colors.textSecondary,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
            }

            Text(
                text = "Ort. ${formatWaterLitres(averageWaterMl)}",
                style = NutraTheme.typography.caption,
                color = colors.textSecondary,
            )
        }

        if (points.isEmpty()) {
            NutraEmptyState(
                icon = NutraIcons.WaterDrop,
                title = "Henüz su kaydı yok",
                message = "Bugün sekmesinden su eklediğinde grafik oluşur.",
                modifier = Modifier.padding(top = 16.dp),
            )
            return@NutraCard
        }

        val maxChartWater = remember(points, targetWaterMl) {
            val peak = points.maxOfOrNull { it.waterMl } ?: targetWaterMl
            (maxOf(peak, targetWaterMl) * 1.15f).coerceAtLeast(1000f)
        }

        HapticScrubbingBarChart(
            points = points,
            scrubbedIndex = scrubbedIndex,
            activePoint = activePoint,
            valueProvider = { it.waterMl },
            targetValue = targetWaterMl,
            maxChartValue = maxChartWater,
            barColorProvider = { pt ->
                if (pt.waterMl == 0) colors.surfaceMuted else colors.water
            },
            selectedRange = selectedRange,
            targetSummaryText = "Hedef: ${formatInt(targetWaterMl)} ml",
            footerHintText = "Çubukların üzerinde kaydırarak günlük su tüketimini incele · Hedef ${formatInt(targetWaterMl)} ml",
            onScrub = onScrub,
        )
    }
}

@Composable
private fun HapticScrubbingBarChart(
    points: List<DailyAnalyticsPoint>,
    scrubbedIndex: Int?,
    activePoint: DailyAnalyticsPoint?,
    valueProvider: (DailyAnalyticsPoint) -> Int,
    targetValue: Int,
    maxChartValue: Float,
    barColorProvider: (DailyAnalyticsPoint) -> Color,
    selectedRange: AnalyticsRangeOption,
    targetSummaryText: String,
    footerHintText: String? = null,
    onScrub: (Int?) -> Unit,
) {
    val colors = NutraTheme.colors
    val haptic = LocalHapticFeedback.current
    val currentScrub by rememberUpdatedState(scrubbedIndex)
    val latestOnScrub by rememberUpdatedState(onScrub)
    val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) }
    val subtleTertiary = remember(colors.textTertiary) { colors.textTertiary.copy(alpha = 0.45f) }
    val barGrowth = remember { Animatable(0f) }
    LaunchedEffect(selectedRange) {
        barGrowth.snapTo(0.25f)
        barGrowth.animateTo(1f, tween(durationMillis = 240, easing = FastOutSlowInEasing))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp)
            .height(156.dp),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(points.size) {
                    detectTapGestures { offset ->
                        if (points.isEmpty()) return@detectTapGestures
                        val idx = ((offset.x / size.width.coerceAtLeast(1)) * points.size)
                            .toInt()
                            .coerceIn(0, points.lastIndex)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        latestOnScrub(if (currentScrub == idx) null else idx)
                    }
                }
                .pointerInput(points.size) {
                    var lastDraggedIndex = -1
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            if (points.isEmpty()) return@detectHorizontalDragGestures
                            val idx = ((offset.x / size.width.coerceAtLeast(1)) * points.size)
                                .toInt()
                                .coerceIn(0, points.lastIndex)
                            lastDraggedIndex = idx
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            latestOnScrub(idx)
                        },
                        onHorizontalDrag = { change, _ ->
                            if (points.isEmpty()) return@detectHorizontalDragGestures
                            change.consume()
                            val idx = ((change.position.x / size.width.coerceAtLeast(1)) * points.size)
                                .toInt()
                                .coerceIn(0, points.lastIndex)
                            if (idx != lastDraggedIndex) {
                                lastDraggedIndex = idx
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                latestOnScrub(idx)
                            }
                        },
                        onDragEnd = {
                            lastDraggedIndex = -1
                            latestOnScrub(null)
                        },
                        onDragCancel = {
                            lastDraggedIndex = -1
                            latestOnScrub(null)
                        },
                    )
                },
        ) {
            val targetY = size.height * (1f - (targetValue / maxChartValue).coerceIn(0f, 1f))
            drawLine(
                color = subtleTertiary,
                start = Offset(0f, targetY),
                end = Offset(size.width, targetY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dashEffect,
            )

            val count = points.size
            val slotWidth = size.width / count.toFloat()
            val maxBarWidth = when (selectedRange) {
                AnalyticsRangeOption.DAYS_7 -> 22.dp.toPx()
                AnalyticsRangeOption.DAYS_14 -> 12.dp.toPx()
                AnalyticsRangeOption.DAYS_30 -> 6.dp.toPx()
            }
            val barWidth = (slotWidth * 0.56f).coerceIn(3.dp.toPx(), maxBarWidth)
            val minStubHeight = 6.dp.toPx()
            val corner = CornerRadius(barWidth / 2f, barWidth / 2f)
            val activeIdx = currentScrub

            for (index in 0 until count) {
                val pt = points[index]
                val value = valueProvider(pt)
                val isSelected = activeIdx == null || activeIdx == index
                val x = index * slotWidth + (slotWidth - barWidth) / 2f
                val rawHeight = if (value > 0) {
                    val targetHeight = (size.height * (value / maxChartValue).coerceIn(0f, 1f))
                    (targetHeight * barGrowth.value).coerceAtLeast(minStubHeight)
                } else {
                    minStubHeight
                }
                val y = size.height - rawHeight

                val baseColor = barColorProvider(pt)
                val finalColor = if (isSelected) baseColor else baseColor.copy(alpha = 0.28f)

                drawRoundRect(
                    color = finalColor,
                    topLeft = Offset(x, y),
                    size = Size(barWidth, rawHeight),
                    cornerRadius = corner,
                )
            }
        }
    }

    // X-axis date strip
    AnimatedContent(
        targetState = selectedRange,
        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
        label = "barChartXAxis",
    ) { range ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (range == AnalyticsRangeOption.DAYS_7 && points.size <= 7) {
                points.forEachIndexed { index, pt ->
                    val isActive = scrubbedIndex == index
                    Text(
                        text = formatShortWeekday(pt.dateIso),
                        style = NutraTheme.typography.caption,
                        color = if (isActive) colors.textPrimary else colors.textTertiary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Text(
                    text = formatShortDate(points.firstOrNull()?.dateIso),
                    style = NutraTheme.typography.caption,
                    color = colors.textTertiary,
                )
                if (scrubbedIndex != null && activePoint != null) {
                    Text(
                        text = formatShortDate(activePoint.dateIso),
                        style = NutraTheme.typography.labelSmall,
                        color = colors.textPrimary,
                    )
                } else {
                    Text(
                        text = targetSummaryText,
                        style = NutraTheme.typography.caption,
                        color = colors.textTertiary,
                    )
                }
                Text(
                    text = formatShortDate(points.lastOrNull()?.dateIso),
                    style = NutraTheme.typography.caption,
                    color = colors.textTertiary,
                )
            }
        }
    }

    if (!footerHintText.isNullOrBlank()) {
        Text(
            text = footerHintText,
            style = NutraTheme.typography.caption,
            color = colors.textTertiary,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun MacroSplitComparisonCard(
    activeDay: DailyAnalyticsPoint?,
    goals: MacroGoals,
) {
    val colors = NutraTheme.colors
    val proteinG = activeDay?.proteinGrams ?: 0f
    val carbsG = activeDay?.carbsGrams ?: 0f
    val fatG = activeDay?.fatGrams ?: 0f

    val proteinKcal = proteinG * 4f
    val carbsKcal = carbsG * 4f
    val fatKcal = fatG * 9f
    val totalActualKcal = proteinKcal + carbsKcal + fatKcal

    val actualP = if (totalActualKcal > 0f) proteinKcal / totalActualKcal else 0f
    val actualC = if (totalActualKcal > 0f) carbsKcal / totalActualKcal else 0f
    val actualF = if (totalActualKcal > 0f) fatKcal / totalActualKcal else 0f

    val targetProteinKcal = goals.proteinGrams * 4f
    val targetCarbsKcal = goals.carbsGrams * 4f
    val targetFatKcal = goals.fatGrams * 9f
    val totalTargetKcal = (targetProteinKcal + targetCarbsKcal + targetFatKcal).coerceAtLeast(1f)

    val targetP = targetProteinKcal / totalTargetKcal
    val targetC = targetCarbsKcal / totalTargetKcal
    val targetF = targetFatKcal / totalTargetKcal

    NutraCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Makro dengesi",
                    style = NutraTheme.typography.titleMedium,
                    color = colors.textPrimary,
                )
                Text(
                    text = if (activeDay != null && totalActualKcal > 0f) {
                        "${formatShortDate(activeDay.dateIso)} · Hedef oranlarla karşılaştırma"
                    } else {
                        "Seçili gün için makro verisi bekleniyor"
                    },
                    style = NutraTheme.typography.caption,
                    color = colors.textSecondary,
                )
            }
        }

        // Actual vs Target stacked bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StackedRatioBar(
                proteinRatio = actualP,
                carbsRatio = actualC,
                fatRatio = actualF,
                hasData = totalActualKcal > 0f,
                height = 12.dp,
                alpha = 1f,
            )
            StackedRatioBar(
                proteinRatio = targetP,
                carbsRatio = targetC,
                fatRatio = targetF,
                hasData = true,
                height = 5.dp,
                alpha = 0.45f,
            )
        }

        NutraDivider(modifier = Modifier.padding(vertical = 16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MacroSplitColumn(
                label = "Protein",
                dotColor = colors.protein,
                grams = proteinG.roundToInt(),
                targetGrams = goals.proteinGrams,
                actualPercent = (actualP * 100f).roundToInt(),
                targetPercent = (targetP * 100f).roundToInt(),
                modifier = Modifier.weight(1f),
            )
            MacroSplitColumn(
                label = "Karb.",
                dotColor = colors.carbs,
                grams = carbsG.roundToInt(),
                targetGrams = goals.carbsGrams,
                actualPercent = (actualC * 100f).roundToInt(),
                targetPercent = (targetC * 100f).roundToInt(),
                modifier = Modifier.weight(1f),
            )
            MacroSplitColumn(
                label = "Yağ",
                dotColor = colors.fat,
                grams = fatG.roundToInt(),
                targetGrams = goals.fatGrams,
                actualPercent = (actualF * 100f).roundToInt(),
                targetPercent = (targetF * 100f).roundToInt(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StackedRatioBar(
    proteinRatio: Float,
    carbsRatio: Float,
    fatRatio: Float,
    hasData: Boolean,
    height: androidx.compose.ui.unit.Dp,
    alpha: Float,
) {
    val colors = NutraTheme.colors
    val pColor = colors.protein.copy(alpha = alpha)
    val cColor = colors.carbs.copy(alpha = alpha)
    val fColor = colors.fat.copy(alpha = alpha)
    val trackColor = colors.surfaceMuted

    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(NutraTheme.shapes.pill)
            .drawBehind {
                if (!hasData) {
                    drawRect(color = trackColor)
                    return@drawBehind
                }
                val pVal = if (proteinRatio > 0.005f) proteinRatio.coerceAtLeast(0.01f) else 0f
                val cVal = if (carbsRatio > 0.005f) carbsRatio.coerceAtLeast(0.01f) else 0f
                val fVal = if (fatRatio > 0.005f) fatRatio.coerceAtLeast(0.01f) else 0f
                val total = (pVal + cVal + fVal).coerceAtLeast(0.001f)

                val gapPx = 2.dp.toPx()
                val activeCount = (if (pVal > 0f) 1 else 0) + (if (cVal > 0f) 1 else 0) + (if (fVal > 0f) 1 else 0)
                val usableWidth = (size.width - gapPx * (activeCount - 1).coerceAtLeast(0)).coerceAtLeast(0f)
                var x = 0f

                if (pVal > 0f) {
                    val w = usableWidth * (pVal / total)
                    drawRect(color = pColor, topLeft = Offset(x, 0f), size = Size(w, size.height))
                    x += w + gapPx
                }
                if (cVal > 0f) {
                    val w = usableWidth * (cVal / total)
                    drawRect(color = cColor, topLeft = Offset(x, 0f), size = Size(w, size.height))
                    x += w + gapPx
                }
                if (fVal > 0f) {
                    val w = (size.width - x).coerceAtLeast(0f)
                    drawRect(color = fColor, topLeft = Offset(x, 0f), size = Size(w, size.height))
                }
            },
    )
}

@Composable
private fun MacroSplitColumn(
    label: String,
    dotColor: Color,
    grams: Int,
    targetGrams: Int,
    actualPercent: Int,
    targetPercent: Int,
    modifier: Modifier = Modifier,
) {
    val colors = NutraTheme.colors
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            NutraDot(color = dotColor, size = 7.dp)
            Text(
                text = label,
                style = NutraTheme.typography.caption,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "%$actualPercent",
            style = NutraTheme.typography.numberMedium,
            color = colors.textPrimary,
        )
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = "${grams} / ${targetGrams} g",
                style = NutraTheme.typography.caption,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Hedef %$targetPercent",
                style = NutraTheme.typography.caption,
                color = colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun WeightTrendBezierCard(
    points: List<WeightTrendPoint>,
    scrubbedIndex: Int?,
    profile: UserProfile,
    onScrub: (Int?) -> Unit,
    onAddWeightClick: () -> Unit,
) {
    val colors = NutraTheme.colors
    val haptic = LocalHapticFeedback.current
    val currentScrub by rememberUpdatedState(scrubbedIndex)

    NutraCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Kilo trendi",
                    style = NutraTheme.typography.titleMedium,
                    color = colors.textPrimary,
                )
                Text(
                    text = "7 günlük ortalama · Hedef ${formatKg(profile.targetWeightKg)} kg",
                    style = NutraTheme.typography.caption,
                    color = colors.textSecondary,
                )
            }

            NutraButton(
                text = "Kilo ekle",
                onClick = onAddWeightClick,
                style = NutraButtonStyle.Secondary,
                size = NutraButtonSize.Small,
                leadingIcon = Icons.Rounded.Add,
            )
        }

        if (points.isEmpty()) {
            NutraEmptyState(
                icon = NutraIcons.MonitorWeight,
                title = "Henüz kilo kaydı yok",
                message = "Günlük tartını eklediğinde dalgalanmaları yumuşatan 7 günlük trend eğrisi çizilir.",
                modifier = Modifier.padding(top = 18.dp),
                action = {
                    NutraButton(
                        text = "İlk kiloyu ekle",
                        onClick = onAddWeightClick,
                        style = NutraButtonStyle.Primary,
                        size = NutraButtonSize.Medium,
                    )
                },
            )
            return@NutraCard
        }

        if (points.size == 1) {
            val only = points.first()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = formatShortDate(only.dateIso),
                        style = NutraTheme.typography.caption,
                        color = colors.textSecondary,
                    )
                    Text(
                        text = "${formatKg(only.rawWeightKg)} kg",
                        style = NutraTheme.typography.numberLarge,
                        color = colors.textPrimary,
                    )
                }
                Text(
                    text = "Eğri için 1 kayıt daha ekle",
                    style = NutraTheme.typography.caption,
                    color = colors.textTertiary,
                )
            }
            return@NutraCard
        }

        val activePoint = scrubbedIndex?.let { points.getOrNull(it) } ?: points.last()
        val firstEma = points.first().emaTrendKg
        val deltaFromStart = activePoint.emaTrendKg - firstEma
        val deltaSign = if (deltaFromStart >= 0.05f) "+" else ""
        val deltaColor = when (profile.goalType) {
            GoalType.LOSE_WEIGHT -> when {
                deltaFromStart < -0.1f -> colors.accent
                deltaFromStart > 0.1f -> colors.warning
                else -> colors.textSecondary
            }
            GoalType.GAIN_MUSCLE -> when {
                deltaFromStart > 0.1f -> colors.accent
                deltaFromStart < -0.1f -> colors.warning
                else -> colors.textSecondary
            }
            GoalType.MAINTAIN -> when {
                abs(deltaFromStart) < 0.1f -> colors.textSecondary
                abs(deltaFromStart) <= 0.5f -> colors.accent
                else -> colors.warning
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "${formatShortDate(activePoint.dateIso)} · Trend",
                    style = NutraTheme.typography.caption,
                    color = if (scrubbedIndex != null) colors.accent else colors.textSecondary,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "${formatKg(activePoint.emaTrendKg)} kg",
                        style = NutraTheme.typography.numberLarge,
                        color = colors.textPrimary,
                        modifier = Modifier.alignByBaseline(),
                    )
                    Text(
                        text = "$deltaSign${formatKg(deltaFromStart)} kg",
                        style = NutraTheme.typography.labelSmall,
                        color = deltaColor,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Tartı",
                    style = NutraTheme.typography.caption,
                    color = colors.textTertiary,
                )
                Text(
                    text = "${formatKg(activePoint.rawWeightKg)} kg",
                    style = NutraTheme.typography.numberSmall,
                    color = colors.textSecondary,
                )
            }
        }

        val targetWeight = profile.targetWeightKg
        val (minY, maxY) = remember(points, targetWeight) {
            val allValues = buildList {
                points.forEach {
                    add(it.rawWeightKg)
                    add(it.emaTrendKg)
                }
                if (abs(targetWeight - points.last().emaTrendKg) <= 25f) {
                    add(targetWeight)
                }
            }
            val rawMin = allValues.minOrNull() ?: 60f
            val rawMax = allValues.maxOrNull() ?: 80f
            val span = (rawMax - rawMin).coerceAtLeast(1.5f)
            val pad = span * 0.18f
            (rawMin - pad) to (rawMax + pad)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp)
                .height(172.dp),
        ) {
            val currentScrubState = rememberUpdatedState(scrubbedIndex)
            val latestOnScrub by rememberUpdatedState(onScrub)

            Spacer(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(points.size) {
                        detectTapGestures { offset ->
                            if (points.size < 2) return@detectTapGestures
                            val idx = ((offset.x / size.width.coerceAtLeast(1)) * points.lastIndex)
                                .roundToInt()
                                .coerceIn(0, points.lastIndex)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            latestOnScrub(if (currentScrubState.value == idx) null else idx)
                        }
                    }
                    .pointerInput(points.size) {
                        var lastDraggedIndex = -1
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                if (points.size < 2) return@detectHorizontalDragGestures
                                val idx = ((offset.x / size.width.coerceAtLeast(1)) * points.lastIndex)
                                    .roundToInt()
                                    .coerceIn(0, points.lastIndex)
                                lastDraggedIndex = idx
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                latestOnScrub(idx)
                            },
                            onHorizontalDrag = { change, _ ->
                                if (points.size < 2) return@detectHorizontalDragGestures
                                change.consume()
                                val idx = ((change.position.x / size.width.coerceAtLeast(1)) * points.lastIndex)
                                    .roundToInt()
                                    .coerceIn(0, points.lastIndex)
                                if (idx != lastDraggedIndex) {
                                    lastDraggedIndex = idx
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    latestOnScrub(idx)
                                }
                            },
                            onDragEnd = {
                                lastDraggedIndex = -1
                                latestOnScrub(null)
                            },
                            onDragCancel = {
                                lastDraggedIndex = -1
                                latestOnScrub(null)
                            },
                        )
                    }
                    .drawWithCache {
                        val range = (maxY - minY).coerceAtLeast(0.5f)
                        val padX = 8.dp.toPx()
                        val usableWidth = (size.width - padX * 2f).coerceAtLeast(1f)
                        val padTop = 8.dp.toPx()
                        val usableHeight = (size.height - padTop * 2f).coerceAtLeast(1f)

                        fun yFor(weight: Float): Float {
                            val norm = ((weight - minY) / range).coerceIn(0f, 1f)
                            return padTop + usableHeight * (1f - norm)
                        }

                        fun xFor(index: Int): Float {
                            val frac = index.toFloat() / points.lastIndex.coerceAtLeast(1)
                            return padX + usableWidth * frac
                        }

                        val hasTargetLine = targetWeight in minY..maxY
                        val targetLineY = if (hasTargetLine) yFor(targetWeight) else 0f
                        val targetDashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                        val cursorDashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))

                        val emaPoints = ArrayList<Offset>(points.size)
                        val rawOffsets = ArrayList<Offset>(points.size)
                        for (idx in points.indices) {
                            val pt = points[idx]
                            val x = xFor(idx)
                            emaPoints.add(Offset(x, yFor(pt.emaTrendKg)))
                            rawOffsets.add(Offset(x, yFor(pt.rawWeightKg)))
                        }

                        val curvePath = Path().apply {
                            moveTo(emaPoints.first().x, emaPoints.first().y)
                            for (i in 0 until emaPoints.lastIndex) {
                                val p0 = emaPoints[i]
                                val p1 = emaPoints[i + 1]
                                val midX = (p0.x + p1.x) / 2f
                                cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
                            }
                        }

                        val fillPath = Path().apply {
                            addPath(curvePath)
                            lineTo(emaPoints.last().x, size.height)
                            lineTo(emaPoints.first().x, size.height)
                            close()
                        }

                        val fillBrush = Brush.verticalGradient(
                            colors = listOf(
                                colors.accent.copy(alpha = 0.16f),
                                Color.Transparent,
                            ),
                            startY = 0f,
                            endY = size.height,
                        )

                        val curveStroke = Stroke(
                            width = 2.5.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        )
                        val targetStrokePx = 1.dp.toPx()
                        val rawDotRadiusPx = 3.dp.toPx()
                        val focusOuterRadiusPx = 6.dp.toPx()
                        val focusInnerRadiusPx = 4.dp.toPx()
                        val targetLineColor = colors.accent.copy(alpha = 0.45f)
                        val rawDotColor = colors.textTertiary.copy(alpha = 0.5f)
                        val accentColor = colors.accent
                        val surfaceColor = colors.surface
                        val dividerColor = colors.divider

                        onDrawBehind {
                            if (hasTargetLine) {
                                drawLine(
                                    color = targetLineColor,
                                    start = Offset(padX, targetLineY),
                                    end = Offset(size.width - padX, targetLineY),
                                    strokeWidth = targetStrokePx,
                                    pathEffect = targetDashEffect,
                                )
                            }

                            drawPath(path = fillPath, brush = fillBrush)
                            drawPath(path = curvePath, color = accentColor, style = curveStroke)

                            for (i in rawOffsets.indices) {
                                drawCircle(
                                    color = rawDotColor,
                                    radius = rawDotRadiusPx,
                                    center = rawOffsets[i],
                                )
                            }

                            val activeScrub = currentScrubState.value
                            val focusIdx = (activeScrub ?: emaPoints.lastIndex).coerceIn(0, emaPoints.lastIndex)
                            val focusOffset = emaPoints[focusIdx]

                            if (activeScrub != null) {
                                drawLine(
                                    color = dividerColor,
                                    start = Offset(focusOffset.x, 0f),
                                    end = Offset(focusOffset.x, size.height),
                                    strokeWidth = targetStrokePx,
                                    pathEffect = cursorDashEffect,
                                )
                            }

                            drawCircle(
                                color = surfaceColor,
                                radius = focusOuterRadiusPx,
                                center = focusOffset,
                            )
                            drawCircle(
                                color = accentColor,
                                radius = focusInnerRadiusPx,
                                center = focusOffset,
                            )
                        }
                    },
            )
        }

        // Bottom range dates + compact legend
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatShortDate(points.first().dateIso),
                style = NutraTheme.typography.caption,
                color = colors.textTertiary,
            )
            if (scrubbedIndex != null) {
                Text(
                    text = formatShortDate(activePoint.dateIso),
                    style = NutraTheme.typography.labelSmall,
                    color = colors.textPrimary,
                )
            }
            Text(
                text = formatShortDate(points.last().dateIso),
                style = NutraTheme.typography.caption,
                color = if (scrubbedIndex == null) colors.textSecondary else colors.textTertiary,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LegendItem(
                color = colors.accent,
                text = "Trend",
                isLine = true,
            )
            LegendItem(
                color = colors.textTertiary.copy(alpha = 0.65f),
                text = "Günlük tartı",
                isLine = false,
            )
            LegendItem(
                color = colors.accent.copy(alpha = 0.55f),
                text = "Hedef",
                isLine = true,
                isDashed = true,
            )
        }
    }
}

@Composable
private fun LegendItem(
    color: Color,
    text: String,
    isLine: Boolean,
    isDashed: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (isDashed) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Box(
                    modifier = Modifier
                        .width(5.dp)
                        .height(2.dp)
                        .clip(NutraTheme.shapes.pill)
                        .background(color),
                )
                Box(
                    modifier = Modifier
                        .width(5.dp)
                        .height(2.dp)
                        .clip(NutraTheme.shapes.pill)
                        .background(color),
                )
            }
        } else if (isLine) {
            Box(
                modifier = Modifier
                    .width(12.dp)
                    .height(2.5.dp)
                    .clip(NutraTheme.shapes.pill)
                    .background(color),
            )
        } else {
            NutraDot(color = color, size = 6.dp)
        }
        Text(
            text = text,
            style = NutraTheme.typography.caption,
            color = NutraTheme.colors.textTertiary,
        )
    }
}

@Composable
private fun LogWeightDialog(
    initialWeight: Float,
    onDismiss: () -> Unit,
    onSave: (Float) -> Unit,
) {
    val colors = NutraTheme.colors
    val haptic = LocalHapticFeedback.current
    var weight by remember(initialWeight) { mutableFloatStateOf(initialWeight.coerceIn(35f, 250f)) }
    var showManualInput by remember { mutableStateOf(false) }

    NutraDialog(
        onDismissRequest = onDismiss,
        title = "Tartı ekle",
        message = "Bugünkü kilonu kaydederek trend eğrini güncel tut.",
        confirmText = "Kaydet",
        onConfirm = {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            onSave(weight)
        },
        dismissText = "Vazgeç",
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(NutraTheme.shapes.pill)
                    .background(colors.surfaceMuted)
                    .clickable(role = Role.Button) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showManualInput = true
                    }
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${formatKg(weight)} kg",
                    style = NutraTheme.typography.numberLarge,
                    color = colors.textPrimary,
                )
            }

            // Quick offset pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(-1.0f, -0.5f, 0.5f, 1.0f).forEach { delta ->
                    val prefix = if (delta > 0f) "+" else ""
                    val label = "$prefix${formatKg(delta)}"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(NutraTheme.shapes.pill)
                            .background(colors.surfaceMuted)
                            .clickable(role = Role.Button) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                weight = (((weight + delta) * 10f).roundToInt() / 10f).coerceIn(35f, 250f)
                            }
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style = NutraTheme.typography.labelSmall,
                            color = colors.textPrimary,
                            maxLines = 1,
                        )
                    }
                }
            }

            // 0.1 kg precision stepper
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(NutraTheme.shapes.pill)
                    .background(colors.surfaceMuted)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NutraIconButton(
                    icon = NutraIcons.Remove,
                    contentDescription = "0.1 kg azalt",
                    onClick = {
                        weight = (((weight - 0.1f) * 10f).roundToInt() / 10f).coerceIn(35f, 250f)
                    },
                    size = 40.dp,
                    iconSize = 18.dp,
                    containerColor = colors.surfaceElevated,
                )
                Text(
                    text = "0,1 kg · dokunarak yaz",
                    style = NutraTheme.typography.caption,
                    color = colors.textSecondary,
                )
                NutraIconButton(
                    icon = Icons.Rounded.Add,
                    contentDescription = "0.1 kg artır",
                    onClick = {
                        weight = (((weight + 0.1f) * 10f).roundToInt() / 10f).coerceIn(35f, 250f)
                    },
                    size = 40.dp,
                    iconSize = 18.dp,
                    containerColor = colors.surfaceElevated,
                )
            }
        }
    }

    if (showManualInput) {
        NutraNumericInputDialog(
            title = "Tartı",
            initialValue = formatKg(weight),
            unit = "kg",
            rangeHint = "35,0 – 250,0 kg",
            minValue = 35.0,
            maxValue = 250.0,
            allowDecimal = true,
            onDismiss = { showManualInput = false },
            onConfirm = { entered ->
                weight = ((entered * 10.0).roundToInt().coerceIn(350, 2500)) / 10f
                showManualInput = false
            },
        )
    }
}
