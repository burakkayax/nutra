package com.nutra.app.feature.analytics

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
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
import com.nutra.app.core.designsystem.NutraColors
import com.nutra.app.core.designsystem.NutraContentMaxWidth
import com.nutra.app.core.designsystem.NutraDialog
import com.nutra.app.core.designsystem.NutraDivider
import com.nutra.app.core.designsystem.NutraDot
import com.nutra.app.core.designsystem.NutraEmptyState
import com.nutra.app.core.designsystem.NutraFormatters
import com.nutra.app.core.designsystem.NutraIcons
import com.nutra.app.core.designsystem.NutraNumericInputDialog
import com.nutra.app.core.designsystem.NutraScreenHeader
import com.nutra.app.core.designsystem.NutraSegmentedControl
import com.nutra.app.core.designsystem.NutraSkeletonBar
import com.nutra.app.core.designsystem.NutraSnackbarHost
import com.nutra.app.core.designsystem.NutraSnackbarTone
import com.nutra.app.core.designsystem.NutraStackedRatioBar
import com.nutra.app.core.designsystem.NutraStepperButton
import com.nutra.app.core.designsystem.NutraTheme
import com.nutra.app.core.designsystem.rememberSkeletonPulseAlpha
import com.nutra.app.domain.model.DailyAnalyticsPoint
import com.nutra.app.domain.model.GoalType
import com.nutra.app.domain.model.MacroGoals
import com.nutra.app.domain.model.WeightTrendPoint
import com.nutra.app.domain.usecase.BodyMetricBounds
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    scrollToTopTrigger: Int = 0,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val layoutDirection = LocalLayoutDirection.current

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshToday()
    }

    val listState = rememberLazyListState()
    var lastAnimatedCalorieRange by rememberSaveable { mutableStateOf<AnalyticsRangeOption?>(null) }
    var lastAnimatedWaterRange by rememberSaveable { mutableStateOf<AnalyticsRangeOption?>(null) }

    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0) {
            listState.animateScrollToItem(0)
        }
    }

    LaunchedEffect(uiState.feedbackEventId, uiState.feedbackMessage) {
        if (uiState.feedbackMessage != null) {
            delay(3000)
            viewModel.clearFeedback()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NutraTheme.colors.background),
    ) {
        Crossfade(
            targetState = uiState.isLoading,
            animationSpec = tween(160),
            label = "AnalyticsLoadingCrossfade",
        ) { isLoading ->
            if (isLoading) {
                AnalyticsLoadingSkeleton(
                    selectedRange = uiState.selectedRange,
                    contentPadding = contentPadding,
                    onSelectRange = viewModel::selectRange,
                )
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
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
                                lastAnimatedRange = lastAnimatedCalorieRange,
                                onRevealRangeAnimated = { lastAnimatedCalorieRange = it },
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
                                lastAnimatedRange = lastAnimatedWaterRange,
                                onRevealRangeAnimated = { lastAnimatedWaterRange = it },
                                onScrub = viewModel::updateScrubbedWaterIndex,
                            )
                        }
                    }
                }
            }
        }

        NutraSnackbarHost(
            visible = uiState.feedbackMessage != null,
            message = uiState.feedbackMessage.orEmpty(),
            tone = NutraSnackbarTone.Success,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    start = NutraTheme.spacing.screen,
                    end = NutraTheme.spacing.screen,
                    bottom = contentPadding.calculateBottomPadding() + 8.dp,
                ),
        )
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
    val placeholderHeights = remember { listOf(180.dp, 112.dp, 180.dp, 180.dp) }
    val pulseAlphaState = rememberSkeletonPulseAlpha("analyticsSkeletonPulse")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
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
                    NutraSkeletonBar(
                        pulseAlphaState = pulseAlphaState,
                        modifier = Modifier.fillMaxWidth(0.38f).height(14.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    NutraSkeletonBar(
                        pulseAlphaState = pulseAlphaState,
                        modifier = Modifier.fillMaxWidth(0.54f).height(26.dp),
                        shape = NutraTheme.shapes.sm,
                    )
                    Spacer(Modifier.height(16.dp))
                    NutraSkeletonBar(
                        pulseAlphaState = pulseAlphaState,
                        modifier = Modifier.fillMaxWidth().height(chartHeight),
                        shape = NutraTheme.shapes.sm,
                    )
                }
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
    lastAnimatedRange: AnalyticsRangeOption?,
    onRevealRangeAnimated: (AnalyticsRangeOption) -> Unit,
    onScrub: (Int?) -> Unit,
) {
    val colors = NutraTheme.colors
    val hasAnyCalories = remember(points) { points.any { it.calories > 0 } }
    val activeDateLabel = activePoint?.let { NutraFormatters.formatShortDate(it.dateIso) }

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
                    text = if (activeDateLabel != null) {
                        "$activeDateLabel · Kalori"
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
                        text = NutraFormatters.formatInt(activePoint?.calories ?: 0),
                        style = NutraTheme.typography.numberLarge,
                        color = colors.textPrimary,
                        modifier = Modifier.alignByBaseline(),
                    )
                    Text(
                        text = "/ ${NutraFormatters.formatInt(activePoint?.targetCalories ?: targetCalories)} kcal",
                        style = NutraTheme.typography.body,
                        color = colors.textSecondary,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Ort. ${NutraFormatters.formatInt(averageCalories)} kcal",
                    style = NutraTheme.typography.caption,
                    color = colors.textSecondary,
                )
                if (scrubbedIndex != null) {
                    Text(
                        text = "Bugüne dön",
                        style = NutraTheme.typography.labelSmall,
                        color = colors.onAccentSoft,
                        modifier = Modifier
                            .clip(NutraTheme.shapes.pill)
                            .background(colors.accentSoft)
                            .clickable(role = Role.Button) { onScrub(null) }
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }

        if (!hasAnyCalories) {
            NutraEmptyState(
                icon = NutraIcons.InsightsOutlined,
                title = "Henüz kalori kaydı yok",
                message = "Bugün sekmesinden öğün eklediğinde grafik oluşur.",
                modifier = Modifier.padding(top = 16.dp),
            )
            return@NutraCard
        }

        val maxChartCalories = remember(points, targetCalories) {
            val peak = points.maxOfOrNull { maxOf(it.calories, it.targetCalories) } ?: targetCalories
            (maxOf(peak, targetCalories) * 1.18f).coerceAtLeast(800f)
        }

        HapticScrubbingBarChart(
            points = points,
            scrubbedIndex = scrubbedIndex,
            activePoint = activePoint,
            valueProvider = { it.calories },
            targetValue = targetCalories,
            targetLineLabel = NutraFormatters.formatInt(targetCalories),
            maxChartValue = maxChartCalories,
            barColorProvider = { pt ->
                when {
                    pt.calories == 0 -> colors.surfaceMuted
                    pt.calories > pt.targetCalories -> colors.warning
                    else -> colors.accent
                }
            },
            selectedRange = selectedRange,
            lastAnimatedRange = lastAnimatedRange,
            onRevealRangeAnimated = onRevealRangeAnimated,
            targetSummaryText = "Hedef: ${NutraFormatters.formatInt(targetCalories)} kcal",
            chartAccessibilityLabel = "Kalori geçmişi grafiği. ${activeDateLabel ?: "Bugün"}: ${activePoint?.calories ?: 0} kcal. Dönem ortalaması: $averageCalories kcal. Hedef: $targetCalories kcal.",
            onScrub = onScrub,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LegendItem(color = colors.accent, text = "Hedef içi", isDot = true)
            LegendItem(color = colors.warning, text = "Hedef fazlası", isDot = true)
            LegendItem(color = colors.textTertiary, text = "Hedef", isDashed = true)
        }
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
    lastAnimatedRange: AnalyticsRangeOption?,
    onRevealRangeAnimated: (AnalyticsRangeOption) -> Unit,
    onScrub: (Int?) -> Unit,
) {
    val colors = NutraTheme.colors
    val hasAnyWater = remember(points) { points.any { it.waterMl > 0 } }
    val activeDateLabel = activePoint?.let { NutraFormatters.formatShortDate(it.dateIso) }

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
                    text = if (activeDateLabel != null) {
                        "$activeDateLabel · Su"
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
                        text = NutraFormatters.formatInt(activePoint?.waterMl ?: 0),
                        style = NutraTheme.typography.numberLarge,
                        color = colors.textPrimary,
                        modifier = Modifier.alignByBaseline(),
                    )
                    Text(
                        text = "/ ${NutraFormatters.formatInt(targetWaterMl)} ml",
                        style = NutraTheme.typography.body,
                        color = colors.textSecondary,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Ort. ${NutraFormatters.formatWaterLitres(averageWaterMl)}",
                    style = NutraTheme.typography.caption,
                    color = colors.textSecondary,
                )
                if (scrubbedIndex != null) {
                    Text(
                        text = "Bugüne dön",
                        style = NutraTheme.typography.labelSmall,
                        color = colors.onAccentSoft,
                        modifier = Modifier
                            .clip(NutraTheme.shapes.pill)
                            .background(colors.accentSoft)
                            .clickable(role = Role.Button) { onScrub(null) }
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }

        if (!hasAnyWater) {
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
            (maxOf(peak, targetWaterMl) * 1.18f).coerceAtLeast(1000f)
        }

        HapticScrubbingBarChart(
            points = points,
            scrubbedIndex = scrubbedIndex,
            activePoint = activePoint,
            valueProvider = { it.waterMl },
            targetValue = targetWaterMl,
            targetLineLabel = NutraFormatters.formatWaterLitres(targetWaterMl),
            maxChartValue = maxChartWater,
            barColorProvider = { pt ->
                if (pt.waterMl == 0) colors.surfaceMuted else colors.water
            },
            selectedRange = selectedRange,
            lastAnimatedRange = lastAnimatedRange,
            onRevealRangeAnimated = onRevealRangeAnimated,
            targetSummaryText = "Hedef: ${NutraFormatters.formatInt(targetWaterMl)} ml",
            chartAccessibilityLabel = "Su geçmişi grafiği. ${activeDateLabel ?: "Bugün"}: ${activePoint?.waterMl ?: 0} ml. Dönem ortalaması: ${NutraFormatters.formatWaterLitres(averageWaterMl)}. Hedef: $targetWaterMl ml.",
            onScrub = onScrub,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LegendItem(color = colors.water, text = "Tüketilen", isDot = true)
            LegendItem(color = colors.textTertiary, text = "Hedef", isDashed = true)
        }
    }
}

@Composable
private fun HapticScrubbingBarChart(
    points: List<DailyAnalyticsPoint>,
    scrubbedIndex: Int?,
    activePoint: DailyAnalyticsPoint?,
    valueProvider: (DailyAnalyticsPoint) -> Int,
    targetValue: Int,
    targetLineLabel: String,
    maxChartValue: Float,
    barColorProvider: (DailyAnalyticsPoint) -> Color,
    selectedRange: AnalyticsRangeOption,
    lastAnimatedRange: AnalyticsRangeOption?,
    onRevealRangeAnimated: (AnalyticsRangeOption) -> Unit,
    targetSummaryText: String,
    chartAccessibilityLabel: String,
    onScrub: (Int?) -> Unit,
) {
    val colors = NutraTheme.colors
    val currentScrubState = rememberUpdatedState(scrubbedIndex)
    val latestOnReveal by rememberUpdatedState(onRevealRangeAnimated)
    val alreadyRevealedForRange = lastAnimatedRange == selectedRange
    val barGrowth = remember { Animatable(if (alreadyRevealedForRange) 1f else 0.35f) }
    LaunchedEffect(selectedRange) {
        if (lastAnimatedRange != selectedRange) {
            barGrowth.snapTo(0.35f)
            barGrowth.animateTo(1f, tween(durationMillis = 240, easing = FastOutSlowInEasing))
            latestOnReveal(selectedRange)
        } else if (barGrowth.value < 1f) {
            barGrowth.snapTo(1f)
        }
    }

    BarChartCanvas(
        points = points,
        scrubbedIndexState = currentScrubState,
        barGrowthState = barGrowth.asState(),
        valueProvider = valueProvider,
        targetValue = targetValue,
        targetLineLabel = targetLineLabel,
        maxChartValue = maxChartValue,
        barColorProvider = barColorProvider,
        selectedRange = selectedRange,
        colors = colors,
        accessibilityLabel = chartAccessibilityLabel,
        onScrub = onScrub,
    )

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
                        text = NutraFormatters.formatShortWeekday(pt.dateIso),
                        style = if (isActive) NutraTheme.typography.labelSmall else NutraTheme.typography.caption,
                        color = if (isActive) colors.textPrimary else colors.textTertiary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Text(
                    text = NutraFormatters.formatShortDate(points.firstOrNull()?.dateIso),
                    style = NutraTheme.typography.caption,
                    color = colors.textTertiary,
                )
                if (scrubbedIndex != null && activePoint != null) {
                    Text(
                        text = NutraFormatters.formatShortDate(activePoint.dateIso),
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
                    text = NutraFormatters.formatShortDate(points.lastOrNull()?.dateIso),
                    style = NutraTheme.typography.caption,
                    color = colors.textTertiary,
                )
            }
        }
    }
}

private fun resolveBarIndex(touchX: Float, widthPx: Int, count: Int): Int =
    ((touchX / widthPx.coerceAtLeast(1)) * count).toInt().coerceIn(0, count - 1)

private fun computeWeightXFractions(points: List<WeightTrendPoint>): FloatArray {
    val count = points.size
    if (count <= 1) return FloatArray(count) { 0f }
    val dates = points.map { runCatching { LocalDate.parse(it.dateIso) }.getOrNull() }
    val firstDate = dates.firstOrNull()
    val lastDate = dates.lastOrNull()
    val totalDays = if (firstDate != null && lastDate != null) {
        ChronoUnit.DAYS.between(firstDate, lastDate)
    } else {
        0L
    }
    return if (totalDays > 0L && dates.all { it != null }) {
        FloatArray(count) { idx ->
            val days = ChronoUnit.DAYS.between(firstDate!!, dates[idx]!!)
            (days.toFloat() / totalDays.toFloat()).coerceIn(0f, 1f)
        }
    } else {
        FloatArray(count) { idx -> idx.toFloat() / (count - 1).toFloat() }
    }
}

private fun resolveNearestXIndex(
    touchX: Float,
    widthPx: Int,
    padXPx: Float,
    xFractions: FloatArray,
): Int {
    if (xFractions.isEmpty()) return 0
    val usableWidth = (widthPx - padXPx * 2f).coerceAtLeast(1f)
    var bestIdx = 0
    var bestDist = Float.MAX_VALUE
    for (i in xFractions.indices) {
        val x = padXPx + usableWidth * xFractions[i]
        val d = abs(touchX - x)
        if (d < bestDist) {
            bestDist = d
            bestIdx = i
        }
    }
    return bestIdx
}

private fun buildMonotoneCubicPath(points: List<Offset>): Path = Path().apply {
    val n = points.size
    if (n == 0) return@apply
    moveTo(points[0].x, points[0].y)
    if (n == 1) return@apply
    if (n == 2) {
        lineTo(points[1].x, points[1].y)
        return@apply
    }

    val dx = FloatArray(n - 1)
    val slopes = FloatArray(n - 1)
    for (i in 0 until n - 1) {
        val step = points[i + 1].x - points[i].x
        dx[i] = step
        slopes[i] = if (step > 0.001f) (points[i + 1].y - points[i].y) / step else 0f
    }

    val m = FloatArray(n)
    m[0] = slopes[0]
    m[n - 1] = slopes[n - 2]
    for (i in 1 until n - 1) {
        m[i] = if (slopes[i - 1] * slopes[i] <= 0f) {
            0f
        } else {
            (slopes[i - 1] + slopes[i]) * 0.5f
        }
    }

    for (i in 0 until n - 1) {
        val d = slopes[i]
        if (abs(d) < 1e-6f) {
            m[i] = 0f
            m[i + 1] = 0f
        } else {
            val a = m[i] / d
            val b = m[i + 1] / d
            val mag = a * a + b * b
            if (mag > 9f) {
                val tau = 3f / sqrt(mag)
                m[i] = tau * a * d
                m[i + 1] = tau * b * d
            }
        }
    }

    for (i in 0 until n - 1) {
        val h = dx[i] / 3f
        val p0 = points[i]
        val p1 = points[i + 1]
        cubicTo(
            p0.x + h,
            p0.y + m[i] * h,
            p1.x - h,
            p1.y - m[i + 1] * h,
            p1.x,
            p1.y,
        )
    }
}

@Composable
private fun BarChartCanvas(
    points: List<DailyAnalyticsPoint>,
    scrubbedIndexState: State<Int?>,
    barGrowthState: State<Float>,
    valueProvider: (DailyAnalyticsPoint) -> Int,
    targetValue: Int,
    targetLineLabel: String,
    maxChartValue: Float,
    barColorProvider: (DailyAnalyticsPoint) -> Color,
    selectedRange: AnalyticsRangeOption,
    colors: NutraColors,
    accessibilityLabel: String,
    onScrub: (Int?) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val latestOnScrub by rememberUpdatedState(onScrub)
    val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) }
    val subtleTertiary = remember(colors.textTertiary) { colors.textTertiary.copy(alpha = 0.45f) }
    val textMeasurer = rememberTextMeasurer()
    val targetLabelStyle = NutraTheme.typography.overline.copy(
        color = colors.textTertiary,
        letterSpacing = NutraTheme.typography.caption.letterSpacing,
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp)
            .height(156.dp),
    ) {
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = accessibilityLabel }
                .pointerInput(points.size) {
                    detectTapGestures { offset ->
                        if (points.isEmpty()) return@detectTapGestures
                        val idx = resolveBarIndex(offset.x, size.width, points.size)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        latestOnScrub(if (scrubbedIndexState.value == idx) null else idx)
                    }
                }
                .pointerInput(points.size) {
                    var lastDraggedIndex = -1
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            if (points.isEmpty()) return@detectHorizontalDragGestures
                            val idx = resolveBarIndex(offset.x, size.width, points.size)
                            lastDraggedIndex = idx
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            latestOnScrub(idx)
                        },
                        onHorizontalDrag = { change, _ ->
                            if (points.isEmpty()) return@detectHorizontalDragGestures
                            change.consume()
                            val idx = resolveBarIndex(change.position.x, size.width, points.size)
                            if (idx != lastDraggedIndex) {
                                lastDraggedIndex = idx
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                latestOnScrub(idx)
                            }
                        },
                        onDragEnd = {
                            lastDraggedIndex = -1
                        },
                        onDragCancel = {
                            lastDraggedIndex = -1
                        },
                    )
                }
                .drawWithCache {
                    val count = points.size
                    val targetY = size.height * (1f - (targetValue / maxChartValue).coerceIn(0f, 1f))
                    val strokePx = 1.dp.toPx()
                    val minStubHeight = 6.dp.toPx()
                    val slotWidth = if (count > 0) size.width / count.toFloat() else 0f
                    val maxBarWidth = when (selectedRange) {
                        AnalyticsRangeOption.DAYS_7 -> 22.dp.toPx()
                        AnalyticsRangeOption.DAYS_14 -> 12.dp.toPx()
                        AnalyticsRangeOption.DAYS_30 -> 6.dp.toPx()
                    }
                    val barWidth = (slotWidth * 0.56f).coerceIn(3.dp.toPx(), maxBarWidth)
                    val corner = CornerRadius(barWidth / 2f, barWidth / 2f)
                    val measuredTarget = textMeasurer.measure(targetLineLabel, style = targetLabelStyle)
                    val labelPadX = 6.dp.toPx()
                    val labelPadY = 2.dp.toPx()
                    val labelBgW = measuredTarget.size.width + labelPadX * 2f
                    val labelBgH = measuredTarget.size.height + labelPadY * 2f
                    val labelBgTop = (targetY - labelBgH - 3.dp.toPx()).coerceAtLeast(0f)
                    val labelBgLeft = (size.width - labelBgW).coerceAtLeast(0f)
                    val labelBgCorner = CornerRadius(labelBgH / 2f, labelBgH / 2f)
                    val labelBgColor = colors.surface.copy(alpha = 0.88f)

                    val barX = FloatArray(count)
                    val targetHeights = FloatArray(count)
                    val activeColors = Array(count) { Color.Transparent }
                    val dimmedColors = Array(count) { Color.Transparent }
                    for (index in 0 until count) {
                        val pt = points[index]
                        val value = valueProvider(pt)
                        barX[index] = index * slotWidth + (slotWidth - barWidth) / 2f
                        targetHeights[index] = if (value > 0) {
                            size.height * (value / maxChartValue).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        val baseColor = barColorProvider(pt)
                        activeColors[index] = baseColor
                        dimmedColors[index] = baseColor.copy(alpha = 0.28f)
                    }

                    onDrawBehind {
                        drawLine(
                            color = subtleTertiary,
                            start = Offset(0f, targetY),
                            end = Offset(size.width, targetY),
                            strokeWidth = strokePx,
                            pathEffect = dashEffect,
                        )
                        if (count == 0) return@onDrawBehind

                        val activeIdx = scrubbedIndexState.value
                        val growth = barGrowthState.value
                        for (index in 0 until count) {
                            val tHeight = targetHeights[index]
                            val rawHeight = if (tHeight > 0f) {
                                (tHeight * growth).coerceAtLeast(minStubHeight)
                            } else {
                                minStubHeight
                            }
                            val isSelected = activeIdx == null || activeIdx == index
                            drawRoundRect(
                                color = if (isSelected) activeColors[index] else dimmedColors[index],
                                topLeft = Offset(barX[index], size.height - rawHeight),
                                size = Size(barWidth, rawHeight),
                                cornerRadius = corner,
                            )
                        }

                        drawRoundRect(
                            color = labelBgColor,
                            topLeft = Offset(labelBgLeft, labelBgTop),
                            size = Size(labelBgW, labelBgH),
                            cornerRadius = labelBgCorner,
                        )
                        drawText(
                            textLayoutResult = measuredTarget,
                            topLeft = Offset(labelBgLeft + labelPadX, labelBgTop + labelPadY),
                        )
                    }
                },
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
                        "${NutraFormatters.formatShortDate(activeDay.dateIso)} · Hedef oranlarla karşılaştırma"
                    } else {
                        "Seçili gün için makro verisi bekleniyor"
                    },
                    style = NutraTheme.typography.caption,
                    color = colors.textSecondary,
                )
            }
        }

        // Actual vs Target stacked bars with inline labels
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Alınan",
                    style = NutraTheme.typography.caption,
                    color = colors.textSecondary,
                    modifier = Modifier.width(46.dp),
                    maxLines = 1,
                )
                NutraStackedRatioBar(
                    proteinRatio = actualP,
                    carbsRatio = actualC,
                    fatRatio = actualF,
                    hasData = totalActualKcal > 0f,
                    height = 12.dp,
                    alpha = 1f,
                    contentDescription = "Alınan makro dağılımı: Protein %${(actualP * 100f).roundToInt()}, Karbonhidrat %${(actualC * 100f).roundToInt()}, Yağ %${(actualF * 100f).roundToInt()}",
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Hedef",
                    style = NutraTheme.typography.caption,
                    color = colors.textTertiary,
                    modifier = Modifier.width(46.dp),
                    maxLines = 1,
                )
                NutraStackedRatioBar(
                    proteinRatio = targetP,
                    carbsRatio = targetC,
                    fatRatio = targetF,
                    hasData = true,
                    height = 6.dp,
                    alpha = 0.45f,
                    contentDescription = "Hedef makro dağılımı: Protein %${(targetP * 100f).roundToInt()}, Karbonhidrat %${(targetC * 100f).roundToInt()}, Yağ %${(targetF * 100f).roundToInt()}",
                    modifier = Modifier.weight(1f),
                )
            }
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
                text = "${NutraFormatters.formatInt(grams)} / ${NutraFormatters.formatInt(targetGrams)} g",
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
    profile: AnalyticsProfileSlice,
    onScrub: (Int?) -> Unit,
    onAddWeightClick: () -> Unit,
) {
    val colors = NutraTheme.colors

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
                    text = "Tüm kayıtlar · Hedef ${NutraFormatters.formatKg(profile.targetWeightKg)} kg",
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
                        text = NutraFormatters.formatShortDate(only.dateIso),
                        style = NutraTheme.typography.caption,
                        color = colors.textSecondary,
                    )
                    Text(
                        text = "${NutraFormatters.formatKg(only.rawWeightKg)} kg",
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
                deltaFromStart > 0.1f -> colors.warningText
                else -> colors.textSecondary
            }
            GoalType.GAIN_MUSCLE -> when {
                deltaFromStart > 0.1f -> colors.accent
                deltaFromStart < -0.1f -> colors.warningText
                else -> colors.textSecondary
            }
            GoalType.MAINTAIN -> when {
                abs(deltaFromStart) < 0.1f -> colors.textSecondary
                abs(deltaFromStart) <= 0.5f -> colors.accent
                else -> colors.warningText
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "${NutraFormatters.formatShortDate(activePoint.dateIso)} · Trend",
                    style = NutraTheme.typography.caption,
                    color = if (scrubbedIndex != null) colors.accent else colors.textSecondary,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "${NutraFormatters.formatKg(activePoint.emaTrendKg)} kg",
                        style = NutraTheme.typography.numberLarge,
                        color = colors.textPrimary,
                        modifier = Modifier.alignByBaseline(),
                    )
                    Text(
                        text = "$deltaSign${NutraFormatters.formatKg(deltaFromStart)} kg",
                        style = NutraTheme.typography.labelSmall,
                        color = deltaColor,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Tartı: ${NutraFormatters.formatKg(activePoint.rawWeightKg)} kg",
                    style = NutraTheme.typography.caption,
                    color = colors.textSecondary,
                )
                if (scrubbedIndex != null) {
                    Text(
                        text = "Son kayıt",
                        style = NutraTheme.typography.labelSmall,
                        color = colors.onAccentSoft,
                        modifier = Modifier
                            .clip(NutraTheme.shapes.pill)
                            .background(colors.accentSoft)
                            .clickable(role = Role.Button) { onScrub(null) }
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }

        val targetWeight = profile.targetWeightKg
        val (minY, maxY) = remember(points, targetWeight) {
            val allValues = buildList {
                points.forEach {
                    add(it.emaTrendKg)
                    add(it.rawWeightKg)
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

        val currentScrubState = rememberUpdatedState(scrubbedIndex)
        WeightTrendCanvas(
            points = points,
            targetWeight = targetWeight,
            minY = minY,
            maxY = maxY,
            colors = colors,
            scrubbedIndexState = currentScrubState,
            accessibilityLabel = "Kilo trendi grafiği. ${NutraFormatters.formatShortDate(activePoint.dateIso)} trend: ${NutraFormatters.formatKg(activePoint.emaTrendKg)} kg, ham tartı: ${NutraFormatters.formatKg(activePoint.rawWeightKg)} kg, hedef: ${NutraFormatters.formatKg(targetWeight)} kg.",
            onScrub = onScrub,
        )

        // Bottom range dates + compact legend
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = NutraFormatters.formatShortDate(points.first().dateIso),
                style = NutraTheme.typography.caption,
                color = colors.textTertiary,
            )
            if (scrubbedIndex != null) {
                Text(
                    text = NutraFormatters.formatShortDate(activePoint.dateIso),
                    style = NutraTheme.typography.labelSmall,
                    color = colors.textPrimary,
                )
            }
            Text(
                text = NutraFormatters.formatShortDate(points.last().dateIso),
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
            )
            LegendItem(
                color = colors.accent.copy(alpha = 0.45f),
                text = "Tartı",
                isDot = true,
            )
            LegendItem(
                color = colors.accent.copy(alpha = 0.55f),
                text = "Hedef",
                isDashed = true,
            )
        }
    }
}

@Composable
private fun WeightTrendCanvas(
    points: List<WeightTrendPoint>,
    targetWeight: Float,
    minY: Float,
    maxY: Float,
    colors: NutraColors,
    scrubbedIndexState: State<Int?>,
    accessibilityLabel: String,
    onScrub: (Int?) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val latestOnScrub by rememberUpdatedState(onScrub)
    val xFractions = remember(points) { computeWeightXFractions(points) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp)
            .height(172.dp),
    ) {
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = accessibilityLabel }
                .pointerInput(points, xFractions) {
                    val padPx = 8.dp.toPx()
                    detectTapGestures { offset ->
                        if (points.size < 2) return@detectTapGestures
                        val idx = resolveNearestXIndex(offset.x, size.width, padPx, xFractions)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        latestOnScrub(if (scrubbedIndexState.value == idx) null else idx)
                    }
                }
                .pointerInput(points, xFractions) {
                    val padPx = 8.dp.toPx()
                    var lastDraggedIndex = -1
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            if (points.size < 2) return@detectHorizontalDragGestures
                            val idx = resolveNearestXIndex(offset.x, size.width, padPx, xFractions)
                            lastDraggedIndex = idx
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            latestOnScrub(idx)
                        },
                        onHorizontalDrag = { change, _ ->
                            if (points.size < 2) return@detectHorizontalDragGestures
                            change.consume()
                            val idx = resolveNearestXIndex(change.position.x, size.width, padPx, xFractions)
                            if (idx != lastDraggedIndex) {
                                lastDraggedIndex = idx
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                latestOnScrub(idx)
                            }
                        },
                        onDragEnd = {
                            lastDraggedIndex = -1
                        },
                        onDragCancel = {
                            lastDraggedIndex = -1
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
                        val frac = xFractions.getOrElse(index) {
                            index.toFloat() / points.lastIndex.coerceAtLeast(1)
                        }
                        return padX + usableWidth * frac
                    }

                    val hasTargetLine = targetWeight in minY..maxY
                    val targetLineY = if (hasTargetLine) yFor(targetWeight) else 0f
                    val targetDashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                    val cursorDashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))

                    val emaPoints = ArrayList<Offset>(points.size)
                    val rawPoints = ArrayList<Offset>(points.size)
                    for (idx in points.indices) {
                        val pt = points[idx]
                        val x = xFor(idx)
                        emaPoints.add(Offset(x, yFor(pt.emaTrendKg)))
                        rawPoints.add(Offset(x, yFor(pt.rawWeightKg)))
                    }

                    val curvePath = buildMonotoneCubicPath(emaPoints)

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
                    val rawDotRadiusPx = 2.75.dp.toPx()
                    val activeRawDotRadiusPx = 3.5.dp.toPx()
                    val focusOuterRadiusPx = 6.dp.toPx()
                    val focusInnerRadiusPx = 4.dp.toPx()
                    val targetLineColor = colors.accent.copy(alpha = 0.45f)
                    val rawDotColor = colors.accent.copy(alpha = 0.36f)
                    val activeRawDotColor = colors.accent.copy(alpha = 0.72f)
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

                        // Raw weigh-in markers behind the smoothed trend curve
                        val activeScrub = scrubbedIndexState.value
                        val focusIdx = (activeScrub ?: emaPoints.lastIndex).coerceIn(0, emaPoints.lastIndex)
                        for (idx in rawPoints.indices) {
                            val isFocusRaw = idx == focusIdx
                            drawCircle(
                                color = if (isFocusRaw) activeRawDotColor else rawDotColor,
                                radius = if (isFocusRaw) activeRawDotRadiusPx else rawDotRadiusPx,
                                center = rawPoints[idx],
                            )
                        }

                        drawPath(path = curvePath, color = accentColor, style = curveStroke)

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
}

@Composable
private fun LegendItem(
    color: Color,
    text: String,
    isDashed: Boolean = false,
    isDot: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when {
            isDot -> {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(color),
                )
            }
            isDashed -> {
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
            }
            else -> {
                Box(
                    modifier = Modifier
                        .width(12.dp)
                        .height(2.5.dp)
                        .clip(NutraTheme.shapes.pill)
                        .background(color),
                )
            }
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
    var weight by rememberSaveable(initialWeight) {
        mutableFloatStateOf(
            initialWeight.coerceIn(BodyMetricBounds.MIN_WEIGHT_KG, BodyMetricBounds.MAX_WEIGHT_KG),
        )
    }
    var showManualInput by rememberSaveable { mutableStateOf(false) }

    NutraDialog(
        onDismissRequest = onDismiss,
        title = "Tartı ekle",
        message = "Bugünkü kilonu kaydederek trend eğrini güncel tut.",
        confirmText = "Kaydet",
        onConfirm = { onSave(weight) },
        dismissText = "Vazgeç",
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val weightPillInteraction = remember { MutableInteractionSource() }
            val isWeightPillPressed by weightPillInteraction.collectIsPressedAsState()
            val weightPillScaleState = animateFloatAsState(
                targetValue = if (isWeightPillPressed) 0.97f else 1f,
                animationSpec = spring(dampingRatio = 0.78f, stiffness = 700f),
                label = "weightPillPressScale",
            )
            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    showManualInput = true
                },
                interactionSource = weightPillInteraction,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .heightIn(min = NutraButtonSize.Small.height)
                    .graphicsLayer {
                        val s = weightPillScaleState.value
                        scaleX = s
                        scaleY = s
                    }
                    .semantics {
                        onClick(label = "Kilo değerini yaz") {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showManualInput = true
                            true
                        }
                    },
                shape = NutraTheme.shapes.pill,
                color = colors.surfaceMuted,
                contentColor = colors.textPrimary,
            ) {
                Row(
                    modifier = Modifier
                        .defaultMinSize(minHeight = NutraButtonSize.Small.height)
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "${NutraFormatters.formatKg(weight)} kg",
                        style = NutraTheme.typography.numberLarge,
                    )
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            // Quick offset pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(-1.0f, -0.5f, 0.5f, 1.0f).forEach { delta ->
                    val prefix = if (delta > 0f) "+" else ""
                    val label = "$prefix${NutraFormatters.formatKg(delta)}"
                    val offsetInteraction = remember { MutableInteractionSource() }
                    val isOffsetPressed by offsetInteraction.collectIsPressedAsState()
                    val offsetScaleState = animateFloatAsState(
                        targetValue = if (isOffsetPressed) 0.97f else 1f,
                        animationSpec = spring(dampingRatio = 0.78f, stiffness = 700f),
                        label = "weightOffsetPressScale",
                    )
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            weight = (((weight + delta) * 10f).roundToInt() / 10f)
                                .coerceIn(BodyMetricBounds.MIN_WEIGHT_KG, BodyMetricBounds.MAX_WEIGHT_KG)
                        },
                        interactionSource = offsetInteraction,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = NutraButtonSize.Small.height)
                            .graphicsLayer {
                                val s = offsetScaleState.value
                                scaleX = s
                                scaleY = s
                            },
                        shape = NutraTheme.shapes.pill,
                        color = colors.surfaceMuted,
                        contentColor = colors.textPrimary,
                    ) {
                        Box(
                            modifier = Modifier
                                .defaultMinSize(minHeight = NutraButtonSize.Small.height)
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                style = NutraTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            // 0.1 kg precision stepper (supports press-and-hold auto-repeat)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NutraStepperButton(
                    icon = NutraIcons.Remove,
                    contentDescription = "0,1 kg azalt",
                    onClick = {
                        weight = (((weight - 0.1f) * 10f).roundToInt() / 10f)
                            .coerceIn(BodyMetricBounds.MIN_WEIGHT_KG, BodyMetricBounds.MAX_WEIGHT_KG)
                    },
                    size = NutraButtonSize.Small.height,
                    containerColor = colors.surfaceMuted,
                )
                Text(
                    text = "0,1 kg · dokunarak yaz",
                    style = NutraTheme.typography.caption,
                    color = colors.textSecondary,
                    modifier = Modifier
                        .clip(NutraTheme.shapes.pill)
                        .clickable(role = Role.Button, onClickLabel = "Kilo değerini yaz") {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showManualInput = true
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
                NutraStepperButton(
                    icon = Icons.Rounded.Add,
                    contentDescription = "0,1 kg artır",
                    onClick = {
                        weight = (((weight + 0.1f) * 10f).roundToInt() / 10f)
                            .coerceIn(BodyMetricBounds.MIN_WEIGHT_KG, BodyMetricBounds.MAX_WEIGHT_KG)
                    },
                    size = NutraButtonSize.Small.height,
                    containerColor = colors.surfaceMuted,
                )
            }
        }
    }

    if (showManualInput) {
        NutraNumericInputDialog(
            title = "Tartı",
            initialValue = NutraFormatters.formatKg(weight),
            unit = "kg",
            rangeHint = "35,0 – 250,0 kg",
            minValue = BodyMetricBounds.MIN_WEIGHT_KG.toDouble(),
            maxValue = BodyMetricBounds.MAX_WEIGHT_KG.toDouble(),
            allowDecimal = true,
            onDismiss = { showManualInput = false },
            onConfirm = { entered ->
                weight = ((entered * 10.0).roundToInt()
                    .coerceIn(BodyMetricBounds.MIN_WEIGHT_TENTHS, BodyMetricBounds.MAX_WEIGHT_TENTHS)) / 10f
                showManualInput = false
            },
        )
    }
}
