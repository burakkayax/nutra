package com.nutra.app.feature.analytics

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nutra.app.core.designsystem.NutraButton
import com.nutra.app.core.designsystem.NutraButtonSize
import com.nutra.app.core.designsystem.NutraButtonStyle
import com.nutra.app.core.designsystem.NutraCard
import com.nutra.app.core.designsystem.NutraColors
import com.nutra.app.core.designsystem.NutraEmptyState
import com.nutra.app.core.designsystem.NutraFormatters
import com.nutra.app.core.designsystem.NutraIcons
import com.nutra.app.core.designsystem.NutraTheme
import com.nutra.app.domain.model.DailyAnalyticsPoint
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.StateFlow

private val CalorieValueProvider: (DailyAnalyticsPoint) -> Int = { it.calories }
private val WaterValueProvider: (DailyAnalyticsPoint) -> Int = { it.waterMl }

private val BarTargetDashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))

@Composable
internal fun HapticScrubbingCalorieChartCard(
    points: ImmutableList<DailyAnalyticsPoint>,
    todayIso: String,
    scrubbedIndexFlow: StateFlow<Int?>,
    targetCalories: Int,
    averageCalories: Int,
    loggedDaysCount: Int,
    selectedRange: AnalyticsRangeOption,
    lastAnimatedRange: AnalyticsRangeOption?,
    onRevealRangeAnimated: (AnalyticsRangeOption) -> Unit,
    onScrub: (Int?) -> Unit,
) {
    val colors = NutraTheme.colors
    val hasAnyCalories = remember(points) { points.any { it.calories > 0 } }
    val rawScrubbedState = scrubbedIndexFlow.collectAsStateWithLifecycle()
    val scrubbedIndexState = remember(points.size, rawScrubbedState) {
        derivedStateOf {
            if (points.isEmpty()) null else rawScrubbedState.value?.coerceIn(0, points.lastIndex)
        }
    }
    val barColorProvider = remember(colors.surfaceMuted, colors.warning, colors.accent) {
        { pt: DailyAnalyticsPoint ->
            when {
                pt.calories == 0 -> colors.surfaceMuted
                pt.calories > pt.targetCalories -> colors.warning
                else -> colors.accent
            }
        }
    }
    val isOverGoalProvider = remember {
        { pt: DailyAnalyticsPoint -> pt.calories > 0 && pt.calories > pt.targetCalories }
    }

    NutraCard {
        CalorieChartHeader(
            points = points,
            todayIso = todayIso,
            scrubbedIndexState = scrubbedIndexState,
            targetCalories = targetCalories,
            averageCalories = averageCalories,
            loggedDaysCount = loggedDaysCount,
            totalDaysCount = selectedRange.days,
            onResetScrub = { onScrub(null) },
        )

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
            todayIso = todayIso,
            scrubbedIndexState = scrubbedIndexState,
            valueProvider = CalorieValueProvider,
            targetValue = targetCalories,
            targetLineLabel = "${NutraFormatters.formatInt(targetCalories)} kcal",
            maxChartValue = maxChartCalories,
            barColorProvider = barColorProvider,
            isOverGoalProvider = isOverGoalProvider,
            selectedRange = selectedRange,
            lastAnimatedRange = lastAnimatedRange,
            onRevealRangeAnimated = onRevealRangeAnimated,
            targetSummaryText = "Hedef: ${NutraFormatters.formatInt(targetCalories)} kcal",
            accessibilityLabelProvider = { idx ->
                val pt = idx?.let { points.getOrNull(it) }
                val dateLabel = pt?.let { NutraFormatters.formatShortDate(it.dateIso, todayIso) }
                val kcal = pt?.calories ?: averageCalories
                val target = pt?.targetCalories ?: targetCalories
                "Kalori geçmişi grafiği. ${dateLabel ?: "${selectedRange.title} günlük ortalaması ($loggedDaysCount/${selectedRange.days} gün)"}: $kcal kcal. Hedef: $target kcal."
            },
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
private fun CalorieChartHeader(
    points: ImmutableList<DailyAnalyticsPoint>,
    todayIso: String,
    scrubbedIndexState: State<Int?>,
    targetCalories: Int,
    averageCalories: Int,
    loggedDaysCount: Int,
    totalDaysCount: Int,
    onResetScrub: () -> Unit,
) {
    val colors = NutraTheme.colors
    val scrubbedIndex = scrubbedIndexState.value
    val scrubbedPoint = scrubbedIndex?.let { points.getOrNull(it) }
    val scrubbedDateLabel = scrubbedPoint?.let { NutraFormatters.formatShortDate(it.dateIso, todayIso) }
    val displayedCalories = scrubbedPoint?.calories ?: averageCalories
    val displayedTargetCalories = scrubbedPoint?.targetCalories ?: targetCalories

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = if (scrubbedDateLabel != null) {
                    "$scrubbedDateLabel · Kalori"
                } else {
                    "Kalori · Günlük ort. ($loggedDaysCount/$totalDaysCount gün)"
                },
                style = NutraTheme.typography.caption,
                color = if (scrubbedPoint != null) colors.onAccentSoft else colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = NutraFormatters.formatInt(displayedCalories),
                    style = NutraTheme.typography.numberLarge,
                    color = colors.textPrimary,
                    modifier = Modifier.alignByBaseline(),
                )
                Text(
                    text = "/ ${NutraFormatters.formatInt(displayedTargetCalories)} kcal",
                    style = NutraTheme.typography.body,
                    color = colors.textSecondary,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
        if (scrubbedPoint != null) {
            NutraButton(
                text = "Ortalama",
                onClick = onResetScrub,
                style = NutraButtonStyle.Secondary,
                size = NutraButtonSize.Small,
                leadingIcon = Icons.Rounded.Refresh,
            )
        }
    }
}

@Composable
internal fun HapticScrubbingWaterChartCard(
    points: ImmutableList<DailyAnalyticsPoint>,
    todayIso: String,
    scrubbedIndexFlow: StateFlow<Int?>,
    targetWaterMl: Int,
    averageWaterMl: Int,
    loggedDaysCount: Int,
    selectedRange: AnalyticsRangeOption,
    lastAnimatedRange: AnalyticsRangeOption?,
    onRevealRangeAnimated: (AnalyticsRangeOption) -> Unit,
    onScrub: (Int?) -> Unit,
) {
    val colors = NutraTheme.colors
    val hasAnyWater = remember(points) { points.any { it.waterMl > 0 } }
    val rawScrubbedState = scrubbedIndexFlow.collectAsStateWithLifecycle()
    val scrubbedIndexState = remember(points.size, rawScrubbedState) {
        derivedStateOf {
            if (points.isEmpty()) null else rawScrubbedState.value?.coerceIn(0, points.lastIndex)
        }
    }
    val barColorProvider = remember(colors.surfaceMuted, colors.water) {
        { pt: DailyAnalyticsPoint ->
            if (pt.waterMl == 0) colors.surfaceMuted else colors.water
        }
    }

    NutraCard {
        WaterChartHeader(
            points = points,
            todayIso = todayIso,
            scrubbedIndexState = scrubbedIndexState,
            targetWaterMl = targetWaterMl,
            averageWaterMl = averageWaterMl,
            loggedDaysCount = loggedDaysCount,
            totalDaysCount = selectedRange.days,
            onResetScrub = { onScrub(null) },
        )

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
            todayIso = todayIso,
            scrubbedIndexState = scrubbedIndexState,
            valueProvider = WaterValueProvider,
            targetValue = targetWaterMl,
            targetLineLabel = "${NutraFormatters.formatInt(targetWaterMl)} ml",
            maxChartValue = maxChartWater,
            barColorProvider = barColorProvider,
            selectedRange = selectedRange,
            lastAnimatedRange = lastAnimatedRange,
            onRevealRangeAnimated = onRevealRangeAnimated,
            targetSummaryText = "Hedef: ${NutraFormatters.formatInt(targetWaterMl)} ml",
            accessibilityLabelProvider = { idx ->
                val pt = idx?.let { points.getOrNull(it) }
                val dateLabel = pt?.let { NutraFormatters.formatShortDate(it.dateIso, todayIso) }
                val water = pt?.waterMl ?: averageWaterMl
                "Su geçmişi grafiği. ${dateLabel ?: "${selectedRange.title} günlük ortalaması ($loggedDaysCount/${selectedRange.days} gün)"}: $water ml. Hedef: $targetWaterMl ml."
            },
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
private fun WaterChartHeader(
    points: ImmutableList<DailyAnalyticsPoint>,
    todayIso: String,
    scrubbedIndexState: State<Int?>,
    targetWaterMl: Int,
    averageWaterMl: Int,
    loggedDaysCount: Int,
    totalDaysCount: Int,
    onResetScrub: () -> Unit,
) {
    val colors = NutraTheme.colors
    val scrubbedIndex = scrubbedIndexState.value
    val scrubbedPoint = scrubbedIndex?.let { points.getOrNull(it) }
    val scrubbedDateLabel = scrubbedPoint?.let { NutraFormatters.formatShortDate(it.dateIso, todayIso) }
    val displayedWaterMl = scrubbedPoint?.waterMl ?: averageWaterMl

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = if (scrubbedDateLabel != null) {
                    "$scrubbedDateLabel · Su"
                } else {
                    "Su · Günlük ort. ($loggedDaysCount/$totalDaysCount gün)"
                },
                style = NutraTheme.typography.caption,
                color = if (scrubbedPoint != null) colors.onAccentSoft else colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = NutraFormatters.formatInt(displayedWaterMl),
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
        if (scrubbedPoint != null) {
            NutraButton(
                text = "Ortalama",
                onClick = onResetScrub,
                style = NutraButtonStyle.Secondary,
                size = NutraButtonSize.Small,
                leadingIcon = Icons.Rounded.Refresh,
            )
        }
    }
}

@Composable
private fun HapticScrubbingBarChart(
    points: ImmutableList<DailyAnalyticsPoint>,
    todayIso: String,
    scrubbedIndexState: State<Int?>,
    valueProvider: (DailyAnalyticsPoint) -> Int,
    targetValue: Int,
    targetLineLabel: String,
    maxChartValue: Float,
    barColorProvider: (DailyAnalyticsPoint) -> Color,
    isOverGoalProvider: (DailyAnalyticsPoint) -> Boolean = { false },
    selectedRange: AnalyticsRangeOption,
    lastAnimatedRange: AnalyticsRangeOption?,
    onRevealRangeAnimated: (AnalyticsRangeOption) -> Unit,
    targetSummaryText: String,
    accessibilityLabelProvider: (Int?) -> String,
    onScrub: (Int?) -> Unit,
) {
    val colors = NutraTheme.colors
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp)
            .height(156.dp)
            .semantics {
                val activeIdx = scrubbedIndexState.value
                contentDescription = accessibilityLabelProvider(activeIdx)
                if (points.isNotEmpty()) {
                    customActions = buildList {
                        val prevIdx = if (activeIdx == null) points.lastIndex else (activeIdx - 1).coerceAtLeast(0)
                        val nextIdx = if (activeIdx == null) points.lastIndex else (activeIdx + 1).coerceAtMost(points.lastIndex)
                        if (activeIdx == null || activeIdx > 0) {
                            add(CustomAccessibilityAction("Önceki günü incele") { onScrub(prevIdx); true })
                        }
                        if (activeIdx != null && activeIdx < points.lastIndex) {
                            add(CustomAccessibilityAction("Sonraki günü incele") { onScrub(nextIdx); true })
                        }
                        if (activeIdx != null) {
                            add(CustomAccessibilityAction("Ortalamaya dön") { onScrub(null); true })
                        }
                    }
                }
            },
    ) {
        BarChartCanvas(
            points = points,
            scrubbedIndexState = scrubbedIndexState,
            barGrowthState = barGrowth.asState(),
            valueProvider = valueProvider,
            targetValue = targetValue,
            targetLineLabel = targetLineLabel,
            maxChartValue = maxChartValue,
            barColorProvider = barColorProvider,
            isOverGoalProvider = isOverGoalProvider,
            selectedRange = selectedRange,
            colors = colors,
            onScrub = onScrub,
        )
    }

    // X-axis date strip isolated into its own leaf composable so scrub ticks never recompose BarChartCanvas.
    BarChartXAxis(
        points = points,
        todayIso = todayIso,
        selectedRange = selectedRange,
        scrubbedIndexState = scrubbedIndexState,
        targetSummaryText = targetSummaryText,
    )
}

@Composable
private fun BarChartXAxis(
    points: ImmutableList<DailyAnalyticsPoint>,
    todayIso: String,
    selectedRange: AnalyticsRangeOption,
    scrubbedIndexState: State<Int?>,
    targetSummaryText: String,
) {
    val colors = NutraTheme.colors
    val scrubbedIndex = scrubbedIndexState.value
    val activePoint = scrubbedIndex?.let { points.getOrNull(it) }

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
                    text = NutraFormatters.formatShortDate(points.firstOrNull()?.dateIso, todayIso),
                    style = NutraTheme.typography.caption,
                    color = colors.textTertiary,
                )
                if (scrubbedIndex != null && activePoint != null) {
                    Text(
                        text = NutraFormatters.formatShortDate(activePoint.dateIso, todayIso),
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
                    text = NutraFormatters.formatShortDate(points.lastOrNull()?.dateIso, todayIso),
                    style = NutraTheme.typography.caption,
                    color = colors.textTertiary,
                )
            }
        }
    }
}

private fun resolveBarIndex(touchX: Float, widthPx: Int, count: Int): Int =
    ((touchX / widthPx.coerceAtLeast(1)) * count).toInt().coerceIn(0, count - 1)

@Composable
private fun BarChartCanvas(
    points: ImmutableList<DailyAnalyticsPoint>,
    scrubbedIndexState: State<Int?>,
    barGrowthState: State<Float>,
    valueProvider: (DailyAnalyticsPoint) -> Int,
    targetValue: Int,
    targetLineLabel: String,
    maxChartValue: Float,
    barColorProvider: (DailyAnalyticsPoint) -> Color,
    isOverGoalProvider: (DailyAnalyticsPoint) -> Boolean,
    selectedRange: AnalyticsRangeOption,
    colors: NutraColors,
    onScrub: (Int?) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val latestOnScrub by rememberUpdatedState(onScrub)
    val subtleTertiary = remember(colors.textTertiary) { colors.textTertiary.copy(alpha = 0.45f) }
    val textMeasurer = rememberTextMeasurer()
    val typography = NutraTheme.typography
    val targetLabelStyle = remember(typography.overline, typography.caption, colors.textTertiary) {
        typography.overline.copy(
            color = colors.textTertiary,
            letterSpacing = typography.caption.letterSpacing,
        )
    }

    Spacer(
        modifier = Modifier
            .fillMaxSize()
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
                val overCapRadius = (barWidth * 0.24f).coerceIn(1.5.dp.toPx(), 3.dp.toPx())
                val overCapOffsetY = 5.dp.toPx()

                val barX = FloatArray(count)
                val targetHeights = FloatArray(count)
                val activeColors = Array(count) { Color.Transparent }
                val dimmedColors = Array(count) { Color.Transparent }
                val overFlags = BooleanArray(count)
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
                    overFlags[index] = isOverGoalProvider(pt)
                }

                onDrawBehind {
                    drawLine(
                        color = subtleTertiary,
                        start = Offset(0f, targetY),
                        end = Offset(size.width, targetY),
                        strokeWidth = strokePx,
                        pathEffect = BarTargetDashEffect,
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
                        val topY = size.height - rawHeight
                        drawRoundRect(
                            color = if (isSelected) activeColors[index] else dimmedColors[index],
                            topLeft = Offset(barX[index], topY),
                            size = Size(barWidth, rawHeight),
                            cornerRadius = corner,
                        )
                        // Non-color dot indicator above over-goal bars for color-blind accessibility
                        if (overFlags[index] && topY > overCapOffsetY + overCapRadius) {
                            drawCircle(
                                color = if (isSelected) colors.warningText else colors.warningText.copy(alpha = 0.28f),
                                radius = overCapRadius,
                                center = Offset(barX[index] + barWidth / 2f, topY - overCapOffsetY),
                            )
                        }
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

@Composable
internal fun LegendItem(
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
