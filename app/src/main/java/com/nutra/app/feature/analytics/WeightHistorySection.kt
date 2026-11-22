package com.nutra.app.feature.analytics

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nutra.app.core.designsystem.NutraButton
import com.nutra.app.core.designsystem.NutraButtonSize
import com.nutra.app.core.designsystem.NutraButtonStyle
import com.nutra.app.core.designsystem.NutraCard
import com.nutra.app.core.designsystem.NutraColors
import com.nutra.app.core.designsystem.NutraDialog
import com.nutra.app.core.designsystem.NutraDivider
import com.nutra.app.core.designsystem.NutraEmptyState
import com.nutra.app.core.designsystem.NutraFormatters
import com.nutra.app.core.designsystem.NutraIcons
import com.nutra.app.core.designsystem.NutraNumericInputDialog
import com.nutra.app.core.designsystem.NutraStepperButton
import com.nutra.app.core.designsystem.NutraTheme
import com.nutra.app.domain.model.GoalType
import com.nutra.app.domain.model.WeightTrendPoint
import com.nutra.app.domain.usecase.BodyMetricBounds
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.StateFlow

private val WEIGHT_QUICK_DELTAS = listOf(-1.0f, -0.5f, 0.5f, 1.0f)
private val WeightPillPressSpring = spring<Float>(dampingRatio = 0.78f, stiffness = 700f)

private val WeightTargetDashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
private val WeightCursorDashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))

private fun computeWeightXFractions(points: ImmutableList<WeightTrendPoint>): FloatArray {
    val count = points.size
    if (count <= 1) return FloatArray(count)
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

internal fun resolveNearestXIndex(
    touchX: Float,
    widthPx: Int,
    padXPx: Float,
    xFractions: FloatArray,
): Int {
    val size = xFractions.size
    if (size <= 1) return 0
    val usableWidth = (widthPx - padXPx * 2f).coerceAtLeast(1f)
    val targetFraction = (touchX - padXPx) / usableWidth
    if (targetFraction <= xFractions[0]) return 0
    if (targetFraction >= xFractions[size - 1]) {
        // Match first occurrence if duplicate max fractions exist
        var idx = size - 1
        while (idx > 0 && xFractions[idx - 1] == xFractions[idx]) idx--
        return idx
    }
    // Binary search for first index where xFractions[lo] >= targetFraction (O(log N))
    var lo = 0
    var hi = size - 1
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (xFractions[mid] < targetFraction) {
            lo = mid + 1
        } else {
            hi = mid
        }
    }
    val leftIdx = (lo - 1).coerceAtLeast(0)
    val leftDist = abs(targetFraction - xFractions[leftIdx])
    val rightDist = abs(targetFraction - xFractions[lo])
    var bestIdx = if (leftDist <= rightDist) leftIdx else lo
    while (bestIdx > 0 && xFractions[bestIdx - 1] == xFractions[bestIdx]) {
        bestIdx--
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
internal fun WeightTrendBezierCard(
    points: ImmutableList<WeightTrendPoint>,
    todayIso: String,
    hasAnyWeightHistory: Boolean,
    selectedRange: AnalyticsRangeOption,
    scrubbedIndexFlow: StateFlow<Int?>,
    profile: AnalyticsProfileSlice,
    onScrub: (Int?) -> Unit,
    onAddWeightClick: () -> Unit,
    onOpenWeightHistory: () -> Unit,
) {
    val colors = NutraTheme.colors
    val rawScrubbedState = scrubbedIndexFlow.collectAsStateWithLifecycle()
    val scrubbedIndexState = remember(points.size, rawScrubbedState) {
        derivedStateOf {
            if (points.isEmpty()) null else rawScrubbedState.value?.coerceIn(0, points.lastIndex)
        }
    }

    NutraCard(contentPadding = PaddingValues(0.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(NutraTheme.spacing.card),
        ) {
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
                        text = "Hedef ${NutraFormatters.formatKg(profile.targetWeightKg)} kg",
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

            when {
                points.isEmpty() -> {
                    NutraEmptyState(
                        icon = NutraIcons.MonitorWeight,
                        title = if (hasAnyWeightHistory) "Bu dönemde kilo kaydı yok" else "Henüz kilo kaydı yok",
                        message = if (hasAnyWeightHistory) {
                            "Son ${selectedRange.days} günde tartı girilmemiş. Yeni bir kayıt ekleyebilir veya dönemi genişletebilirsin."
                        } else {
                            "Günlük tartını eklediğinde dalgalanmaları yumuşatan 7 günlük trend eğrisi çizilir."
                        },
                        modifier = Modifier.padding(top = 18.dp),
                        action = {
                            NutraButton(
                                text = if (hasAnyWeightHistory) "Kilo ekle" else "İlk kiloyu ekle",
                                onClick = onAddWeightClick,
                                style = NutraButtonStyle.Primary,
                                size = NutraButtonSize.Medium,
                            )
                        },
                    )
                }

                points.size == 1 -> {
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
                                text = NutraFormatters.formatShortDate(only.dateIso, todayIso),
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
                }

                else -> {
                    WeightTrendScrubHeader(
                        points = points,
                        todayIso = todayIso,
                        selectedRange = selectedRange,
                        scrubbedIndexState = scrubbedIndexState,
                        goalType = profile.goalType,
                        onScrub = onScrub,
                    )

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

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 18.dp)
                            .height(172.dp)
                            .semantics {
                                val activeIdx = scrubbedIndexState.value ?: points.lastIndex
                                val activePoint = points.getOrElse(activeIdx) { points.last() }
                                contentDescription = "Kilo trendi grafiği. ${NutraFormatters.formatShortDate(activePoint.dateIso, todayIso)} trend: ${NutraFormatters.formatKg(activePoint.emaTrendKg)} kg, ham tartı: ${NutraFormatters.formatKg(activePoint.rawWeightKg)} kg, hedef: ${NutraFormatters.formatKg(targetWeight)} kg."
                                customActions = buildList {
                                    if (activeIdx > 0) {
                                        add(CustomAccessibilityAction("Önceki tartıyı incele") { onScrub(activeIdx - 1); true })
                                    }
                                    if (activeIdx < points.lastIndex) {
                                        add(CustomAccessibilityAction("Sonraki tartıyı incele") { onScrub(activeIdx + 1); true })
                                    }
                                    if (scrubbedIndexState.value != null) {
                                        add(CustomAccessibilityAction("Son kayda dön") { onScrub(null); true })
                                    }
                                }
                            },
                    ) {
                        WeightTrendCanvas(
                            points = points,
                            targetWeight = targetWeight,
                            minY = minY,
                            maxY = maxY,
                            colors = colors,
                            scrubbedIndexState = scrubbedIndexState,
                            onScrub = onScrub,
                        )
                    }

                    // Bottom range dates isolated into a leaf composable so scrub ticks never recompose WeightTrendCanvas.
                    WeightTrendXAxis(
                        points = points,
                        todayIso = todayIso,
                        scrubbedIndexState = scrubbedIndexState,
                    )

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
        }

        if (hasAnyWeightHistory) {
            WeightHistoryFooterRow(onClick = onOpenWeightHistory)
        }
    }
}

@Composable
private fun WeightHistoryFooterRow(
    onClick: () -> Unit,
) {
    val colors = NutraTheme.colors
    val haptic = LocalHapticFeedback.current

    NutraDivider()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = NutraTheme.spacing.card, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Son ölçümleri gör",
            style = NutraTheme.typography.label,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun WeightTrendScrubHeader(
    points: ImmutableList<WeightTrendPoint>,
    todayIso: String,
    selectedRange: AnalyticsRangeOption,
    scrubbedIndexState: State<Int?>,
    goalType: GoalType,
    onScrub: (Int?) -> Unit,
) {
    val colors = NutraTheme.colors
    val scrubbedIndex = scrubbedIndexState.value
    val activePoint = scrubbedIndex?.let { points.getOrNull(it) } ?: points.last()
    val firstRawWeight = points.first().rawWeightKg
    val deltaFromStart = activePoint.rawWeightKg - firstRawWeight
    val deltaSign = if (deltaFromStart >= 0.05f) "+" else ""
    val deltaColor = when (goalType) {
        GoalType.LOSE_WEIGHT -> when {
            deltaFromStart < -0.1f -> colors.onAccentSoft
            deltaFromStart > 0.1f -> colors.warningText
            else -> colors.textSecondary
        }
        GoalType.GAIN_MUSCLE -> when {
            deltaFromStart > 0.1f -> colors.onAccentSoft
            deltaFromStart < -0.1f -> colors.warningText
            else -> colors.textSecondary
        }
        GoalType.MAINTAIN -> when {
            abs(deltaFromStart) < 0.1f -> colors.textSecondary
            abs(deltaFromStart) <= 0.5f -> colors.onAccentSoft
            else -> colors.warningText
        }
    }
    val deltaContextLabel = if (scrubbedIndex == null) {
        "son ${selectedRange.days} günde"
    } else {
        "dönem başına göre"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "${NutraFormatters.formatShortDate(activePoint.dateIso, todayIso)} · Trend ${NutraFormatters.formatKg(activePoint.emaTrendKg)} kg",
                style = NutraTheme.typography.caption,
                color = if (scrubbedIndex != null) colors.onAccentSoft else colors.textSecondary,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "${NutraFormatters.formatKg(activePoint.rawWeightKg)} kg",
                    style = NutraTheme.typography.numberLarge,
                    color = colors.textPrimary,
                    modifier = Modifier.alignByBaseline(),
                )
                Text(
                    text = "$deltaSign${NutraFormatters.formatKg(deltaFromStart)} kg ($deltaContextLabel)",
                    style = NutraTheme.typography.labelSmall,
                    color = deltaColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }

        if (scrubbedIndex != null) {
            NutraButton(
                text = "Son kayıt",
                onClick = { onScrub(null) },
                style = NutraButtonStyle.Secondary,
                size = NutraButtonSize.Small,
                leadingIcon = Icons.Rounded.Refresh,
            )
        }
    }
}

@Composable
private fun WeightTrendXAxis(
    points: ImmutableList<WeightTrendPoint>,
    todayIso: String,
    scrubbedIndexState: State<Int?>,
) {
    val colors = NutraTheme.colors
    val scrubbedIndex = scrubbedIndexState.value
    val activePoint = scrubbedIndex?.let { points.getOrNull(it) } ?: points.last()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = NutraFormatters.formatShortDate(points.first().dateIso, todayIso),
            style = NutraTheme.typography.caption,
            color = colors.textTertiary,
        )
        if (scrubbedIndex != null) {
            Text(
                text = NutraFormatters.formatShortDate(activePoint.dateIso, todayIso),
                style = NutraTheme.typography.labelSmall,
                color = colors.textPrimary,
            )
        }
        Text(
            text = NutraFormatters.formatShortDate(points.last().dateIso, todayIso),
            style = NutraTheme.typography.caption,
            color = if (scrubbedIndex == null) colors.textSecondary else colors.textTertiary,
        )
    }
}

@Composable
private fun WeightTrendCanvas(
    points: ImmutableList<WeightTrendPoint>,
    targetWeight: Float,
    minY: Float,
    maxY: Float,
    colors: NutraColors,
    scrubbedIndexState: State<Int?>,
    onScrub: (Int?) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val latestOnScrub by rememberUpdatedState(onScrub)
    val xFractions = remember(points) { computeWeightXFractions(points) }

    Spacer(
        modifier = Modifier
            .fillMaxSize()
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
                val cursorColor = colors.controlBorder

                onDrawBehind {
                    if (hasTargetLine) {
                        drawLine(
                            color = targetLineColor,
                            start = Offset(padX, targetLineY),
                            end = Offset(size.width - padX, targetLineY),
                            strokeWidth = targetStrokePx,
                            pathEffect = WeightTargetDashEffect,
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
                            color = cursorColor,
                            start = Offset(focusOffset.x, 0f),
                            end = Offset(focusOffset.x, size.height),
                            strokeWidth = targetStrokePx,
                            pathEffect = WeightCursorDashEffect,
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

@Composable
internal fun LogWeightDialog(
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

    if (showManualInput) {
        NutraNumericInputDialog(
            title = "Tartı",
            initialValue = NutraFormatters.formatKg(weight),
            unit = "kg",
            rangeHint = "30,0 – 300,0 kg",
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
        return
    }

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
                animationSpec = WeightPillPressSpring,
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
                WEIGHT_QUICK_DELTAS.forEach { delta ->
                    val prefix = if (delta > 0f) "+" else ""
                    val label = "$prefix${NutraFormatters.formatKg(delta)}"
                    val offsetInteraction = remember { MutableInteractionSource() }
                    val isOffsetPressed by offsetInteraction.collectIsPressedAsState()
                    val offsetScaleState = animateFloatAsState(
                        targetValue = if (isOffsetPressed) 0.97f else 1f,
                        animationSpec = WeightPillPressSpring,
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
}
