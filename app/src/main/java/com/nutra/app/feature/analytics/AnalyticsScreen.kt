package com.nutra.app.feature.analytics

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nutra.app.core.designsystem.NutraCard
import com.nutra.app.core.designsystem.NutraContentMaxWidth
import com.nutra.app.core.designsystem.NutraDivider
import com.nutra.app.core.designsystem.NutraDot
import com.nutra.app.core.designsystem.NutraScreenHeader
import com.nutra.app.core.designsystem.NutraSegmentedControl
import com.nutra.app.core.designsystem.NutraSkeletonBar
import com.nutra.app.core.designsystem.NutraSnackbarHost
import com.nutra.app.core.designsystem.NutraSnackbarTone
import com.nutra.app.core.designsystem.NutraStackedRatioBar
import com.nutra.app.core.designsystem.NutraStickyHeaderTopReservedPadding
import com.nutra.app.core.designsystem.NutraStickyScreenHeader
import com.nutra.app.core.designsystem.NutraTheme
import com.nutra.app.core.designsystem.rememberAccessibleSnackbarTimeout
import com.nutra.app.core.designsystem.rememberHeaderCollapseFraction
import com.nutra.app.core.designsystem.rememberSkeletonPulseAlpha
import com.nutra.app.domain.model.MacroGoals
import com.nutra.app.feature.metabolism.MetabolismSummaryCard
import kotlin.math.roundToInt
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay

@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    scrollToTopTrigger: Int = 0,
    onOpenMetabolismDetail: () -> Unit = {},
    onOpenWeightHistory: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val metabolismAnalysis by viewModel.metabolismAnalysis.collectAsStateWithLifecycle()
    val layoutDirection = LocalLayoutDirection.current

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshToday()
    }

    val listState = rememberLazyListState()
    val headerCollapseFraction = rememberHeaderCollapseFraction(listState)
    val defaultTopClearance = contentPadding.calculateTopPadding() + NutraStickyHeaderTopReservedPadding
    var measuredHeaderHeight by remember { mutableStateOf(defaultTopClearance) }
    var lastAnimatedCalorieRange by rememberSaveable { mutableStateOf<AnalyticsRangeOption?>(null) }
    var lastAnimatedWaterRange by rememberSaveable { mutableStateOf<AnalyticsRangeOption?>(null) }
    val onRevealCalorieRange = remember { { range: AnalyticsRangeOption -> lastAnimatedCalorieRange = range } }
    val onRevealWaterRange = remember { { range: AnalyticsRangeOption -> lastAnimatedWaterRange = range } }
    val snackbarTimeoutMs = rememberAccessibleSnackbarTimeout(baseTimeoutMillis = 3000L, hasAction = false)

    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0) {
            listState.animateScrollToItem(0)
        }
    }

    LaunchedEffect(uiState.feedbackEventId, uiState.feedbackMessage) {
        if (uiState.feedbackMessage != null) {
            delay(snackbarTimeoutMs)
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
                            top = maxOf(defaultTopClearance, measuredHeaderHeight + 4.dp),
                            bottom = contentPadding.calculateBottomPadding() + 16.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        item(key = "header") {
                            NutraSegmentedControl(
                                items = remember { AnalyticsRangeOption.entries.toImmutableList() },
                                selectedItem = uiState.selectedRange,
                                onItemSelected = viewModel::selectRange,
                                labelProvider = { it.title },
                            )
                        }

                        item(key = "calorie_chart") {
                            val immutableDailyPoints = remember(uiState.dailyPoints) { uiState.dailyPoints.toImmutableList() }
                            HapticScrubbingCalorieChartCard(
                                points = immutableDailyPoints,
                                todayIso = uiState.todayIso,
                                scrubbedIndexFlow = viewModel.scrubbedDayIndex,
                                targetCalories = uiState.profile.goals.calories,
                                averageCalories = uiState.averageCalories,
                                loggedDaysCount = uiState.loggedCalorieDaysCount,
                                selectedRange = uiState.selectedRange,
                                lastAnimatedRange = lastAnimatedCalorieRange,
                                onRevealRangeAnimated = onRevealCalorieRange,
                                onScrub = viewModel::updateScrubbedDayIndex,
                            )
                        }

                        item(key = "macro_split") {
                            MacroSplitComparisonCard(
                                averageProteinGrams = uiState.averageProteinGrams,
                                averageCarbsGrams = uiState.averageCarbsGrams,
                                averageFatGrams = uiState.averageFatGrams,
                                goals = uiState.profile.goals,
                            )
                        }

                        val analysis = metabolismAnalysis
                        if (analysis != null) {
                            item(key = "adaptive_metabolism") {
                                MetabolismSummaryCard(
                                    analysis = analysis,
                                    onOpenDetail = onOpenMetabolismDetail,
                                    onApproveProposal = viewModel::approveCalorieProposal,
                                    onDismissProposal = viewModel::dismissCalorieProposal,
                                )
                            }
                        }

                        item(key = "weight_trend") {
                            val immutableWeightPoints = remember(uiState.weightTrendPoints) { uiState.weightTrendPoints.toImmutableList() }
                            WeightTrendBezierCard(
                                points = immutableWeightPoints,
                                todayIso = uiState.todayIso,
                                hasAnyWeightHistory = uiState.hasAnyWeightHistory,
                                selectedRange = uiState.selectedRange,
                                scrubbedIndexFlow = viewModel.scrubbedWeightIndex,
                                profile = uiState.profile,
                                onScrub = viewModel::updateScrubbedWeightIndex,
                                onAddWeightClick = viewModel::openAddWeightDialog,
                                onOpenWeightHistory = onOpenWeightHistory,
                            )
                        }

                        item(key = "water_chart") {
                            val immutableDailyPoints = remember(uiState.dailyPoints) { uiState.dailyPoints.toImmutableList() }
                            HapticScrubbingWaterChartCard(
                                points = immutableDailyPoints,
                                todayIso = uiState.todayIso,
                                scrubbedIndexFlow = viewModel.scrubbedWaterIndex,
                                targetWaterMl = uiState.profile.goals.waterMl,
                                averageWaterMl = uiState.averageWaterMl,
                                loggedDaysCount = uiState.loggedWaterDaysCount,
                                selectedRange = uiState.selectedRange,
                                lastAnimatedRange = lastAnimatedWaterRange,
                                onRevealRangeAnimated = onRevealWaterRange,
                                onScrub = viewModel::updateScrubbedWaterIndex,
                            )
                        }
                    }

                    NutraStickyScreenHeader(
                        title = "İlerleme",
                        eyebrow = "Beslenme ve kilo özeti",
                        collapseFractionState = headerCollapseFraction,
                        horizontalPadding = PaddingValues(
                            start = contentPadding.calculateStartPadding(layoutDirection) + NutraTheme.spacing.screen,
                            end = contentPadding.calculateEndPadding(layoutDirection) + NutraTheme.spacing.screen,
                        ),
                        onHeaderHeightMeasured = { measuredHeaderHeight = it },
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
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
        val latestWeight = uiState.latestRecordedWeightKg
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
                    items = remember { AnalyticsRangeOption.entries.toImmutableList() },
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
private fun MacroSplitComparisonCard(
    averageProteinGrams: Float,
    averageCarbsGrams: Float,
    averageFatGrams: Float,
    goals: MacroGoals,
) {
    val colors = NutraTheme.colors
    val proteinKcal = averageProteinGrams * 4f
    val carbsKcal = averageCarbsGrams * 4f
    val fatKcal = averageFatGrams * 9f
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
                    text = if (totalActualKcal > 0f) {
                        "Hedef oranlarla karşılaştırma"
                    } else {
                        "Seçili dönem için makro verisi bekleniyor"
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
                actualPercent = (actualP * 100f).roundToInt(),
                targetPercent = (targetP * 100f).roundToInt(),
                modifier = Modifier.weight(1f),
            )
            MacroSplitColumn(
                label = "Karb.",
                dotColor = colors.carbs,
                actualPercent = (actualC * 100f).roundToInt(),
                targetPercent = (targetC * 100f).roundToInt(),
                modifier = Modifier.weight(1f),
            )
            MacroSplitColumn(
                label = "Yağ",
                dotColor = colors.fat,
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
        Text(
            text = "Hedef %$targetPercent",
            style = NutraTheme.typography.caption,
            color = colors.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
