package com.nutra.app.feature.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nutra.app.core.designsystem.NutraCard
import com.nutra.app.core.designsystem.NutraChip
import com.nutra.app.core.designsystem.NutraSegmentedControl
import com.nutra.app.core.designsystem.NutraTheme
import com.nutra.app.domain.model.DailyAnalyticsPoint
import com.nutra.app.domain.model.WeightTrendPoint
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val TR_LOCALE = Locale.forLanguageTag("tr-TR")

@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NutraTheme.colors.background)
            .padding(contentPadding),
        contentPadding = PaddingValues(
            start = NutraTheme.spacing.screenHorizontal,
            end = NutraTheme.spacing.screenHorizontal,
            top = 14.dp,
            bottom = 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(NutraTheme.spacing.medium),
    ) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Analiz & İlerleme",
                    style = NutraTheme.typography.screenHeader,
                    color = NutraTheme.colors.textPrimary,
                )
                Text(
                    text = "Grafikler üzerinde parmağınızı gezdirerek gün detaylarını inceleyin",
                    style = NutraTheme.typography.bodySmall,
                    color = NutraTheme.colors.textSecondary,
                )
            }
        }

        item(key = "range_selector") {
            NutraSegmentedControl(
                items = AnalyticsRangeOption.entries,
                selectedItem = state.selectedRange,
                onItemSelected = viewModel::selectRange,
                labelProvider = { it.title },
            )
        }

        // 1. Haptic Scrubbing Calorie & Macro Bar Chart Card
        item(key = "calorie_chart") {
            HapticScrubbingCalorieChartCard(
                points = state.dailyPoints,
                selectedIndex = state.scrubbedDayIndex ?: (state.dailyPoints.size - 1).coerceAtLeast(0),
                averageCalories = state.averageCalories,
                onScrubIndexChanged = { newIdx ->
                    if (newIdx != state.scrubbedDayIndex) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.updateScrubbedDayIndex(newIdx)
                    }
                },
            )
        }

        // 2. Macro Distribution Breakdown Card
        item(key = "macro_split") {
            MacroSplitComparisonCard(
                points = state.dailyPoints,
                goalType = state.profile.goalType,
            )
        }

        // 3. Weight History & 7-Day EMA True Trend Curve Card
        item(key = "weight_trend") {
            WeightTrendBezierCard(
                trendPoints = state.weightTrendPoints,
                selectedIndex = state.scrubbedWeightIndex ?: (state.weightTrendPoints.size - 1).coerceAtLeast(0),
                currentWeightKg = state.profile.currentWeightKg,
                targetWeightKg = state.profile.targetWeightKg,
                onScrubIndexChanged = { newIdx ->
                    if (newIdx != state.scrubbedWeightIndex) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.updateScrubbedWeightIndex(newIdx)
                    }
                },
                onOpenLogWeight = viewModel::openAddWeightDialog,
            )
        }
    }

    if (state.showAddWeightDialog) {
        LogWeightDialog(
            initialWeightKg = state.profile.currentWeightKg,
            onDismiss = viewModel::closeAddWeightDialog,
            onConfirm = { newWeight ->
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                viewModel.logNewWeight(newWeight)
            },
        )
    }
}

@Composable
private fun HapticScrubbingCalorieChartCard(
    points: List<DailyAnalyticsPoint>,
    selectedIndex: Int,
    averageCalories: Int,
    onScrubIndexChanged: (Int) -> Unit,
) {
    val activePoint = points.getOrNull(selectedIndex)
    val calorieAccent = NutraTheme.colors.calorieAccent
    val overBudgetAccent = NutraTheme.colors.overBudgetAccent
    val elevatedColor = NutraTheme.colors.elevatedSurface
    val mutedColor = NutraTheme.colors.textMuted

    NutraCard(
        shape = NutraTheme.shapes.heroCard,
        contentPadding = PaddingValues(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Live Scrubbed Day Inspection Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column {
                    val dateTitle = activePoint?.dateIso?.let { iso ->
                        runCatching {
                            val d = LocalDate.parse(iso)
                            "${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, TR_LOCALE)} (${
                                d.dayOfWeek.getDisplayName(TextStyle.SHORT, TR_LOCALE)
                            })"
                        }.getOrDefault(iso)
                    } ?: "Bugün"

                    Text(
                        text = dateTitle,
                        style = NutraTheme.typography.macroLabel,
                        color = NutraTheme.colors.textSecondary,
                    )
                    Text(
                        text = "${activePoint?.calories ?: 0} kcal",
                        style = NutraTheme.typography.statLargeNumber,
                        color = NutraTheme.colors.textPrimary,
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Ort: $averageCalories kcal",
                        style = NutraTheme.typography.macroValue,
                        color = NutraTheme.colors.calorieAccent,
                    )
                    if (activePoint != null) {
                        Text(
                            text = "P:${activePoint.proteinGrams.roundToInt()}g · K:${activePoint.carbsGrams.roundToInt()}g · Y:${activePoint.fatGrams.roundToInt()}g",
                            style = NutraTheme.typography.bodySmall,
                            color = NutraTheme.colors.textSecondary,
                        )
                    }
                }
            }

            // Interactive Canvas Bar Chart with Haptic Scrubbing
            val maxCal = (points.maxOfOrNull { maxOf(it.calories, it.targetCalories) } ?: 2400)
                .coerceAtLeast(1500) * 1.15f

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(168.dp)
                    .pointerInput(points.size) {
                        if (points.isEmpty()) return@pointerInput
                        detectTapGestures { offset ->
                            val slotWidth = size.width / points.size.toFloat()
                            val idx = (offset.x / slotWidth).toInt().coerceIn(0, points.size - 1)
                            onScrubIndexChanged(idx)
                        }
                    }
                    .pointerInput(points.size) {
                        if (points.isEmpty()) return@pointerInput
                        detectHorizontalDragGestures { change, _ ->
                            change.consume()
                            val slotWidth = size.width / points.size.toFloat()
                            val idx = (change.position.x / slotWidth).toInt().coerceIn(0, points.size - 1)
                            onScrubIndexChanged(idx)
                        }
                    },
            ) {
                if (points.isEmpty()) return@Canvas

                val targetCal = points.first().targetCalories
                val targetY = size.height - ((targetCal / maxCal) * size.height).coerceIn(0f, size.height)

                // Dashed Target Calorie Line
                drawLine(
                    color = mutedColor.copy(alpha = 0.45f),
                    start = Offset(0f, targetY),
                    end = Offset(size.width, targetY),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f),
                )

                val count = points.size
                val slotWidth = size.width / count.toFloat()
                val barWidth = (slotWidth * 0.56f).coerceIn(6.dp.toPx(), 24.dp.toPx())

                points.forEachIndexed { idx, pt ->
                    val centerX = (idx + 0.5f) * slotWidth
                    val left = centerX - (barWidth / 2f)
                    val isSelected = idx == selectedIndex

                    // Full Track Pill
                    drawRoundRect(
                        color = elevatedColor.copy(alpha = if (isSelected) 0.9f else 0.45f),
                        topLeft = Offset(left, 0f),
                        size = Size(barWidth, size.height),
                        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
                    )

                    // Filled Calorie Bar
                    val ratio = (pt.calories / maxCal).coerceIn(0f, 1f)
                    val barHeight = (ratio * size.height).coerceAtLeast(if (pt.calories > 0) 6.dp.toPx() else 0f)
                    if (barHeight > 0f) {
                        val isOver = pt.calories > (pt.targetCalories * 1.08f)
                        val baseColor = if (isOver) overBudgetAccent else calorieAccent
                        drawRoundRect(
                            color = if (isSelected) baseColor else baseColor.copy(alpha = 0.55f),
                            topLeft = Offset(left, size.height - barHeight),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MacroSplitComparisonCard(
    points: List<DailyAnalyticsPoint>,
    goalType: com.nutra.app.domain.model.GoalType,
) {
    val totalP = points.sumOf { it.proteinGrams.toDouble() }.toFloat()
    val totalC = points.sumOf { it.carbsGrams.toDouble() }.toFloat()
    val totalF = points.sumOf { it.fatGrams.toDouble() }.toFloat()

    val pKcal = totalP * 4f
    val cKcal = totalC * 4f
    val fKcal = totalF * 9f
    val sumKcal = (pKcal + cKcal + fKcal).coerceAtLeast(1f)

    val actualPRatio = if (totalP + totalC + totalF > 0f) pKcal / sumKcal else goalType.proteinRatio
    val actualCRatio = if (totalP + totalC + totalF > 0f) cKcal / sumKcal else goalType.carbsRatio
    val actualFRatio = if (totalP + totalC + totalF > 0f) fKcal / sumKcal else goalType.fatRatio

    NutraCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Makro Enerji Dağılımı (Gerçekleşen vs. Hedef)",
                style = NutraTheme.typography.cardTitle,
                color = NutraTheme.colors.textPrimary,
            )

            // Multi-Segment Proportional Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(NutraTheme.shapes.pill),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(actualPRatio.coerceAtLeast(0.05f))
                        .fillMaxHeight()
                        .background(NutraTheme.colors.proteinAccent),
                )
                Box(
                    modifier = Modifier
                        .weight(actualCRatio.coerceAtLeast(0.05f))
                        .fillMaxHeight()
                        .background(NutraTheme.colors.carbsAccent),
                )
                Box(
                    modifier = Modifier
                        .weight(actualFRatio.coerceAtLeast(0.05f))
                        .fillMaxHeight()
                        .background(NutraTheme.colors.fatAccent),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MacroPercentItem(
                    title = "Protein",
                    actualPct = (actualPRatio * 100).roundToInt(),
                    targetPct = (goalType.proteinRatio * 100).roundToInt(),
                    color = NutraTheme.colors.proteinAccent,
                )
                MacroPercentItem(
                    title = "Karbonhidrat",
                    actualPct = (actualCRatio * 100).roundToInt(),
                    targetPct = (goalType.carbsRatio * 100).roundToInt(),
                    color = NutraTheme.colors.carbsAccent,
                )
                MacroPercentItem(
                    title = "Yağ",
                    actualPct = (actualFRatio * 100).roundToInt(),
                    targetPct = (goalType.fatRatio * 100).roundToInt(),
                    color = NutraTheme.colors.fatAccent,
                )
            }
        }
    }
}

@Composable
private fun MacroPercentItem(
    title: String,
    actualPct: Int,
    targetPct: Int,
    color: Color,
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(NutraTheme.shapes.pill)
                    .background(color),
            )
            Text(
                text = title,
                style = NutraTheme.typography.macroLabel,
                color = NutraTheme.colors.textSecondary,
            )
        }
        Text(
            text = "%$actualPct (Hedef %$targetPct)",
            style = NutraTheme.typography.macroValue,
            color = NutraTheme.colors.textPrimary,
        )
    }
}

@Composable
private fun WeightTrendBezierCard(
    trendPoints: List<WeightTrendPoint>,
    selectedIndex: Int,
    currentWeightKg: Float,
    targetWeightKg: Float,
    onScrubIndexChanged: (Int) -> Unit,
    onOpenLogWeight: () -> Unit,
) {
    val activePoint = trendPoints.getOrNull(selectedIndex)
    val displayRaw = activePoint?.rawWeightKg ?: currentWeightKg
    val displayEma = activePoint?.emaTrendKg ?: currentWeightKg
    val diffToTarget = abs(displayEma - targetWeightKg)

    val trendColor = NutraTheme.colors.calorieAccent
    val rawDotColor = NutraTheme.colors.textMuted

    NutraCard(
        shape = NutraTheme.shapes.heroCard,
        contentPadding = PaddingValues(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Kilo & Gerçek Trend (7 Günlük EMA)",
                        style = NutraTheme.typography.cardTitle,
                        color = NutraTheme.colors.textPrimary,
                    )
                    Text(
                        text = "Ödem dalgalanmalarını filtreleyen hareketli ortalama",
                        style = NutraTheme.typography.bodySmall,
                        color = NutraTheme.colors.textSecondary,
                    )
                }

                NutraChip(
                    text = "+ Tartı Ekle",
                    selected = true,
                    onClick = onOpenLogWeight,
                )
            }

            // Metrics Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(NutraTheme.shapes.innerSubCard)
                    .background(NutraTheme.colors.elevatedSurface)
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Gerçek Trend", style = NutraTheme.typography.badgeLabel, color = NutraTheme.colors.textSecondary)
                    Text(
                        text = String.format(Locale.US, "%.1f kg", displayEma),
                        style = NutraTheme.typography.cardTitle,
                        color = NutraTheme.colors.calorieAccent,
                    )
                }
                Column {
                    Text("Günlük Tartı", style = NutraTheme.typography.badgeLabel, color = NutraTheme.colors.textSecondary)
                    Text(
                        text = String.format(Locale.US, "%.1f kg", displayRaw),
                        style = NutraTheme.typography.cardTitle,
                        color = NutraTheme.colors.textPrimary,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Hedefe Fark", style = NutraTheme.typography.badgeLabel, color = NutraTheme.colors.textSecondary)
                    Text(
                        text = String.format(Locale.US, "%.1f kg", diffToTarget),
                        style = NutraTheme.typography.cardTitle,
                        color = NutraTheme.colors.textPrimary,
                    )
                }
            }

            if (trendPoints.size >= 2) {
                val minW = (trendPoints.minOf { minOf(it.rawWeightKg, it.emaTrendKg) } - 1.2f)
                val maxW = (trendPoints.maxOf { maxOf(it.rawWeightKg, it.emaTrendKg) } + 1.2f)
                val span = (maxW - minW).coerceAtLeast(1f)

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .pointerInput(trendPoints.size) {
                            detectHorizontalDragGestures { change, _ ->
                                change.consume()
                                val slotW = size.width / trendPoints.size.toFloat()
                                val idx = (change.position.x / slotW).toInt().coerceIn(0, trendPoints.size - 1)
                                onScrubIndexChanged(idx)
                            }
                        },
                ) {
                    val stepX = size.width / (trendPoints.size - 1).toFloat()
                    val emaOffsets = trendPoints.mapIndexed { idx, pt ->
                        val x = idx * stepX
                        val y = size.height - (((pt.emaTrendKg - minW) / span) * size.height)
                        Offset(x, y)
                    }

                    // Smooth Cubic Bezier EMA Trend Path
                    val path = Path().apply {
                        moveTo(emaOffsets.first().x, emaOffsets.first().y)
                        for (i in 0 until emaOffsets.size - 1) {
                            val p0 = emaOffsets[i]
                            val p1 = emaOffsets[i + 1]
                            val ctrlX = (p0.x + p1.x) / 2f
                            cubicTo(ctrlX, p0.y, ctrlX, p1.y, p1.x, p1.y)
                        }
                    }

                    drawPath(
                        path = path,
                        color = trendColor,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                    )

                    // Raw Scale Dots & Selected Indicator
                    trendPoints.forEachIndexed { idx, pt ->
                        val x = idx * stepX
                        val rawY = size.height - (((pt.rawWeightKg - minW) / span) * size.height)
                        drawCircle(
                            color = rawDotColor.copy(alpha = 0.55f),
                            radius = 3.5.dp.toPx(),
                            center = Offset(x, rawY),
                        )
                        if (idx == selectedIndex) {
                            drawCircle(
                                color = trendColor,
                                radius = 6.dp.toPx(),
                                center = emaOffsets[idx],
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "Trend eğrisini görmek için farklı günlerde tartı kaydı ekleyin.",
                    style = NutraTheme.typography.bodySmall,
                    color = NutraTheme.colors.textMuted,
                )
            }
        }
    }
}

@Composable
private fun LogWeightDialog(
    initialWeightKg: Float,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit,
) {
    var weight by remember(initialWeightKg) { mutableFloatStateOf(initialWeightKg) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NutraTheme.colors.cardSurface,
        title = {
            Text(
                text = "Günlük Tartı Kaydı",
                style = NutraTheme.typography.cardTitle,
                color = NutraTheme.colors.textPrimary,
            )
        },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { weight = ((weight - 0.1f) * 10f).roundToInt() / 10f }) {
                    Icon(Icons.Rounded.Remove, contentDescription = "Azalt", tint = NutraTheme.colors.textPrimary)
                }
                Text(
                    text = String.format(Locale.US, "%.1f kg", weight),
                    style = NutraTheme.typography.statLargeNumber,
                    color = NutraTheme.colors.calorieAccent,
                )
                IconButton(onClick = { weight = ((weight + 0.1f) * 10f).roundToInt() / 10f }) {
                    Icon(Icons.Rounded.Add, contentDescription = "Artır", tint = NutraTheme.colors.textPrimary)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(weight) },
                colors = ButtonDefaults.buttonColors(containerColor = NutraTheme.colors.calorieAccent),
            ) {
                Text("Kaydet", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal", color = NutraTheme.colors.textSecondary)
            }
        },
    )
}
