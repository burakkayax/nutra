package com.nutra.app.feature.metabolism

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nutra.app.core.designsystem.NutraBadge
import com.nutra.app.core.designsystem.NutraButton
import com.nutra.app.core.designsystem.NutraButtonSize
import com.nutra.app.core.designsystem.NutraButtonStyle
import com.nutra.app.core.designsystem.NutraCard
import com.nutra.app.core.designsystem.NutraContentMaxWidth
import com.nutra.app.core.designsystem.NutraDivider
import com.nutra.app.core.designsystem.NutraDot
import com.nutra.app.core.designsystem.NutraFormatters
import com.nutra.app.core.designsystem.NutraLinearProgress
import com.nutra.app.core.designsystem.NutraSkeletonBar
import com.nutra.app.core.designsystem.NutraSnackbarHost
import com.nutra.app.core.designsystem.NutraSnackbarTone
import com.nutra.app.core.designsystem.NutraStickyScreenHeader
import com.nutra.app.core.designsystem.NutraStickySubScreenHeaderTopReservedPadding
import com.nutra.app.core.designsystem.NutraTheme
import com.nutra.app.core.designsystem.rememberAccessibleSnackbarTimeout
import com.nutra.app.core.designsystem.rememberHeaderCollapseFraction
import com.nutra.app.core.designsystem.rememberSkeletonPulseAlpha
import com.nutra.app.domain.metabolism.CalorieTargetProposal
import com.nutra.app.domain.metabolism.MetabolismAnalysisResult
import com.nutra.app.domain.metabolism.MetabolismGuardrailState
import com.nutra.app.domain.metabolism.TdeeHistoryPoint
import com.nutra.app.domain.metabolism.WeightProjectionPoint
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

private val DashedLineEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
private val CursorDashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))

internal fun metabolismChartIndexForX(
    x: Float,
    widthPx: Int,
    padXPx: Float,
    pointCount: Int,
): Int {
    if (pointCount <= 1) return 0
    val usableWidth = (widthPx.toFloat() - padXPx * 2f).coerceAtLeast(1f)
    val fraction = ((x - padXPx) / usableWidth).coerceIn(0f, 1f)
    return (fraction * (pointCount - 1)).roundToInt().coerceIn(0, pointCount - 1)
}

/**
 * Calm-neutral summary card embedded inside AnalyticsScreen linking to [MetabolismDetailScreen].
 */
@Composable
fun MetabolismSummaryCard(
    analysis: MetabolismAnalysisResult,
    onOpenDetail: () -> Unit,
    onApproveProposal: (CalorieTargetProposal) -> Unit,
    onDismissProposal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NutraTheme.colors

    NutraCard(
        onClick = onOpenDetail,
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
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
                    text = "Adaptif Metabolizma",
                    style = NutraTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Tahmini günlük enerji harcaması",
                    style = NutraTheme.typography.caption,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "Detaylı metabolizma analizi",
                tint = colors.textTertiary,
                modifier = Modifier.size(20.dp),
            )
        }

        // Hero TDEE value
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = NutraFormatters.formatInt(analysis.estimatedTdeeKcal),
                style = NutraTheme.typography.numberLarge,
                color = colors.textPrimary,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                text = "± ${NutraFormatters.formatInt(analysis.uncertaintyKcal)} kcal/gün",
                style = NutraTheme.typography.body,
                color = colors.textSecondary,
                modifier = Modifier.alignByBaseline(),
            )
        }

        NutraDivider()

        // Two essential sub-metrics: Weekly rate & Average intake
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val rateSign = if (analysis.weeklyWeightChangeKg > 0.04f) "+" else ""
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Haftalık hız",
                    style = NutraTheme.typography.caption,
                    color = colors.textSecondary,
                    maxLines = 1,
                )
                Text(
                    text = "$rateSign${NutraFormatters.formatKg(analysis.weeklyWeightChangeKg)} kg/hafta",
                    style = NutraTheme.typography.numberSmall,
                    color = colors.textPrimary,
                    maxLines = 1,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Ort. alım (28 gün)",
                    style = NutraTheme.typography.caption,
                    color = colors.textSecondary,
                    maxLines = 1,
                )
                Text(
                    text = analysis.averageDailyIntakeLast28Kcal?.let { "${NutraFormatters.formatInt(it)} kcal/gün" } ?: "—",
                    style = NutraTheme.typography.numberSmall,
                    color = colors.textPrimary,
                    maxLines = 1,
                )
            }
        }

        // Optional compact calorie proposal banner
        val proposal = analysis.calorieProposal
        if (proposal != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(NutraTheme.shapes.md)
                    .background(colors.accentSoft)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "${proposal.title}: ${NutraFormatters.formatInt(proposal.currentGoals.calories)} → ${NutraFormatters.formatInt(proposal.proposedGoals.calories)} kcal",
                    style = NutraTheme.typography.labelSmall,
                    color = colors.onAccentSoft,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NutraButton(
                        text = "Hedefi güncelle",
                        onClick = { onApproveProposal(proposal) },
                        style = NutraButtonStyle.Primary,
                        size = NutraButtonSize.Small,
                        leadingIcon = Icons.Rounded.Check,
                        hapticFeedback = HapticFeedbackType.Confirm,
                        modifier = Modifier.weight(1f),
                    )
                    NutraButton(
                        text = "Kalsın",
                        onClick = onDismissProposal,
                        style = NutraButtonStyle.Secondary,
                        size = NutraButtonSize.Small,
                    )
                }
            }
        }
    }
}

@Composable
fun MetabolismDetailScreen(
    analysis: MetabolismAnalysisResult,
    onNavigateBack: () -> Unit,
    onApproveProposal: (CalorieTargetProposal) -> Unit,
    onDismissProposal: () -> Unit,
    feedbackMessage: String? = null,
    feedbackEventId: Long = 0L,
    onClearFeedback: () -> Unit = {},
) {
    val colors = NutraTheme.colors
    val snackbarTimeoutMs = rememberAccessibleSnackbarTimeout(baseTimeoutMillis = 2800L, hasAction = false)

    LaunchedEffect(feedbackEventId, feedbackMessage) {
        if (feedbackMessage != null) {
            delay(snackbarTimeoutMs)
            onClearFeedback()
        }
    }

    val listState = rememberLazyListState()
    val headerCollapseFraction = rememberHeaderCollapseFraction(listState)
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val defaultTopPadding = statusBarTop + NutraStickySubScreenHeaderTopReservedPadding
    var measuredHeaderHeight by remember { mutableStateOf(defaultTopPadding) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = NutraContentMaxWidth)
                .fillMaxSize(),
            contentPadding = PaddingValues(
                start = NutraTheme.spacing.screen,
                end = NutraTheme.spacing.screen,
                top = maxOf(defaultTopPadding, measuredHeaderHeight + 6.dp),
                bottom = 36.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
                // 1. Guardrail notice if active
                if (analysis.guardrailState != MetabolismGuardrailState.NORMAL_ADULT && analysis.guardrailNotice != null) {
                    item(key = "guardrail_banner") {
                        NutraCard(
                            color = colors.surfaceMuted,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "Koruyucu gözlem modu aktif",
                                style = NutraTheme.typography.titleSmall,
                                color = colors.textPrimary,
                            )
                            Text(
                                text = analysis.guardrailNotice,
                                style = NutraTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
                    }
                }

                // 2. Hero TDEE & Uncertainty Overview
                item(key = "hero_tdee") {
                    NutraCard(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Tahmini harcama (TDEE)",
                                style = NutraTheme.typography.caption,
                                color = colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            NutraBadge(
                                text = "${analysis.dataQuality.level.title} · %${analysis.confidenceScore}",
                                color = if (analysis.confidenceScore >= 55) colors.onAccentSoft else colors.textSecondary,
                                container = if (analysis.confidenceScore >= 55) colors.accentSoft else colors.surfaceMuted,
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = NutraFormatters.formatInt(analysis.estimatedTdeeKcal),
                                style = NutraTheme.typography.numberHero,
                                color = colors.textPrimary,
                                modifier = Modifier.alignByBaseline(),
                            )
                            Text(
                                text = "± ${NutraFormatters.formatInt(analysis.uncertaintyKcal)} kcal/gün",
                                style = NutraTheme.typography.body,
                                color = colors.textSecondary,
                                modifier = Modifier.alignByBaseline(),
                            )
                        }

                        NutraLinearProgress(
                            progress = analysis.confidenceScore / 100f,
                            color = colors.accent,
                            height = 6.dp,
                        )

                        Text(
                            text = analysis.plainSummary,
                            style = NutraTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )

                        NutraDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            CompactStatColumn("Başlangıç (BMR)", "${NutraFormatters.formatInt(analysis.priorBmrKcal)} kcal")
                            CompactStatColumn("Formül TDEE", "${NutraFormatters.formatInt(analysis.priorTdeeKcal)} kcal")
                            val delta = analysis.tdeeDeltaFromPriorKcal
                            val sign = if (delta > 0) "+" else ""
                            CompactStatColumn("Kalibrasyon", "$sign${NutraFormatters.formatInt(delta)} kcal")
                        }
                    }
                }

                // 3. Approval-gated Calorie Target Proposal
                val proposal = analysis.calorieProposal
                if (proposal != null) {
                    item(key = "calorie_proposal") {
                        NutraCard(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = proposal.title,
                                style = NutraTheme.typography.titleSmall,
                                color = colors.textPrimary,
                            )
                            Text(
                                text = proposal.rationale,
                                style = NutraTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(NutraTheme.shapes.md)
                                    .background(colors.surfaceMuted)
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text("Mevcut hedef", style = NutraTheme.typography.caption, color = colors.textTertiary)
                                    Text(
                                        text = "${NutraFormatters.formatInt(proposal.currentGoals.calories)} kcal",
                                        style = NutraTheme.typography.numberMedium,
                                        color = colors.textSecondary,
                                        maxLines = 1,
                                    )
                                    Text(
                                        text = "P ${proposal.currentGoals.proteinGrams} · K ${proposal.currentGoals.carbsGrams} · Y ${proposal.currentGoals.fatGrams}g",
                                        style = NutraTheme.typography.caption,
                                        color = colors.textTertiary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text("Önerilen hedef", style = NutraTheme.typography.caption, color = colors.accent)
                                    Text(
                                        text = "${NutraFormatters.formatInt(proposal.proposedGoals.calories)} kcal",
                                        style = NutraTheme.typography.numberMedium,
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                    )
                                    Text(
                                        text = "P ${proposal.proposedGoals.proteinGrams} · K ${proposal.proposedGoals.carbsGrams} · Y ${proposal.proposedGoals.fatGrams}g",
                                        style = NutraTheme.typography.caption,
                                        color = colors.textSecondary,
                                        textAlign = TextAlign.End,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            proposal.safetyNote?.let { note ->
                                Text(
                                    text = note,
                                    style = NutraTheme.typography.caption,
                                    color = colors.warningText,
                                )
                            }
                            Text(
                                text = proposal.uncertaintyNote,
                                style = NutraTheme.typography.caption,
                                color = colors.textTertiary,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                NutraButton(
                                    text = "Hedefi güncelle",
                                    onClick = { onApproveProposal(proposal) },
                                    style = NutraButtonStyle.Primary,
                                    size = NutraButtonSize.Medium,
                                    leadingIcon = Icons.Rounded.Check,
                                    hapticFeedback = HapticFeedbackType.Confirm,
                                    modifier = Modifier.weight(1f),
                                )
                                NutraButton(
                                    text = "Kalsın",
                                    onClick = onDismissProposal,
                                    style = NutraButtonStyle.Secondary,
                                    size = NutraButtonSize.Medium,
                                )
                            }
                        }
                    }
                }

                // 4. Interactive TDEE & Calorie Intake Over Time Chart
                item(key = "tdee_history_chart") {
                    val immutableHistory = remember(analysis.tdeeHistory) { analysis.tdeeHistory.toImmutableList() }
                    TdeeHistoryChartCard(
                        history = immutableHistory,
                        todayIso = analysis.analysisDateIso,
                        activeGoalKcal = analysis.activeDailyCalorieGoal,
                    )
                }

                // 5. Non-Linear Dynamic Weight Projection Fan Chart
                item(key = "weight_projection_chart") {
                    DynamicWeightProjectionCard(
                        analysis = analysis,
                    )
                }

                // 6. Multi-Hypothesis Plateau Diagnostics Card
                item(key = "plateau_card") {
                    NutraCard(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Plato ve durağanlık analizi",
                                style = NutraTheme.typography.titleSmall,
                                color = colors.textPrimary,
                            )
                            if (analysis.plateauAssessment.isStalled) {
                                NutraBadge(
                                    text = "${analysis.plateauAssessment.stallDurationDays} gün",
                                    color = if (analysis.plateauAssessment.recommendsWaiting) colors.warningText else colors.onAccentSoft,
                                    container = if (analysis.plateauAssessment.recommendsWaiting) colors.surfaceMuted else colors.accentSoft,
                                )
                            }
                        }
                        Text(
                            text = analysis.plateauAssessment.headline,
                            style = NutraTheme.typography.label,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = analysis.plateauAssessment.explanation,
                            style = NutraTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                        if (analysis.plateauAssessment.factors.isNotEmpty()) {
                            NutraDivider()
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                analysis.plateauAssessment.factors.forEach { factor ->
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Text(
                                                text = factor.type.title,
                                                style = NutraTheme.typography.labelSmall,
                                                color = colors.textPrimary,
                                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                                            )
                                            Text(
                                                text = "%${(factor.likelihoodScore * 100f).roundToInt()}",
                                                style = NutraTheme.typography.caption,
                                                color = colors.textSecondary,
                                            )
                                        }
                                        NutraLinearProgress(
                                            progress = factor.likelihoodScore,
                                            color = if (factor.likelihoodScore >= 0.7f) colors.warning else colors.accent,
                                            height = 4.dp,
                                        )
                                        Text(
                                            text = factor.explanation,
                                            style = NutraTheme.typography.caption,
                                            color = colors.textSecondary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 7. Data Quality & Reliability Transparency Card
                item(key = "data_quality_card") {
                    val dq = analysis.dataQuality
                    NutraCard(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Veri kalitesi ve güvenilirlik",
                            style = NutraTheme.typography.titleSmall,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = dq.summaryMessage,
                            style = NutraTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(NutraTheme.shapes.sm)
                                .background(colors.surfaceMuted)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            CompactStatColumn("Tam gün (28 gün)", "${dq.completeIntakeDaysLast28} gün")
                            CompactStatColumn("Tartı / hafta", "${NutraFormatters.formatKg(dq.weighInsPerWeek)}x")
                            CompactStatColumn("Tartı oynaklığı", "±${NutraFormatters.formatKg(analysis.scaleVolatilityKg)} kg")
                        }
                        dq.improvementTip?.let { tip ->
                            Text(
                                text = tip,
                                style = NutraTheme.typography.caption,
                                color = colors.textTertiary,
                            )
                        }
                    }
                }

                item(key = "medical_disclaimer") {
                    Text(
                        text = "Not: Bu analiz yalnızca kendi kayıtlarına dayanan tahmini bir enerji dengesi modelidir; tıbbi tanı veya tedavi yerine geçmez.",
                        style = NutraTheme.typography.caption,
                        color = colors.textTertiary,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }

                item(key = "bottom_spacer") {
                    Spacer(Modifier.navigationBarsPadding())
                }
        }

        NutraStickyScreenHeader(
            title = "Adaptif Metabolizma",
            eyebrow = "TDEE, kilo projeksiyonu ve plato analizi",
            collapseFractionState = headerCollapseFraction,
            onNavigateBack = onNavigateBack,
            onHeaderHeightMeasured = { measuredHeaderHeight = it },
            modifier = Modifier.align(Alignment.TopCenter),
        )

        NutraSnackbarHost(
            visible = feedbackMessage != null,
            message = feedbackMessage.orEmpty(),
            tone = NutraSnackbarTone.Success,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = NutraTheme.spacing.screen, vertical = 14.dp),
        )
    }
}

@Composable
fun MetabolismDetailLoadingScreen(
    onNavigateBack: () -> Unit,
) {
    val colors = NutraTheme.colors
    val collapseState = remember { mutableFloatStateOf(0f) }
    val pulseAlpha = rememberSkeletonPulseAlpha("metabolismLoadingPulse")
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

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
                    start = NutraTheme.spacing.screen,
                    end = NutraTheme.spacing.screen,
                    top = statusBarTop + NutraStickySubScreenHeaderTopReservedPadding + 8.dp,
                    bottom = 36.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            NutraCard(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                NutraSkeletonBar(pulseAlpha, Modifier.fillMaxWidth(0.45f).height(14.dp))
                NutraSkeletonBar(pulseAlpha, Modifier.fillMaxWidth(0.60f).height(34.dp), shape = NutraTheme.shapes.sm)
                NutraSkeletonBar(pulseAlpha, Modifier.fillMaxWidth().height(6.dp))
                NutraSkeletonBar(pulseAlpha, Modifier.fillMaxWidth(0.85f).height(14.dp))
            }
            repeat(2) {
                NutraCard(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    NutraSkeletonBar(pulseAlpha, Modifier.fillMaxWidth(0.50f).height(14.dp))
                    NutraSkeletonBar(pulseAlpha, Modifier.fillMaxWidth().height(168.dp), shape = NutraTheme.shapes.sm)
                }
            }
        }
        NutraStickyScreenHeader(
            title = "Adaptif Metabolizma",
            eyebrow = "Metabolizma verileri hazırlanıyor...",
            collapseFractionState = collapseState,
            onNavigateBack = onNavigateBack,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun TdeeHistoryChartCard(
    history: ImmutableList<TdeeHistoryPoint>,
    todayIso: String,
    activeGoalKcal: Int,
) {
    val colors = NutraTheme.colors
    val scrubbedIndexState = remember(history) { mutableStateOf<Int?>(null) }

    NutraCard(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TdeeHistoryHeader(
            history = history,
            todayIso = todayIso,
            scrubbedIndexState = scrubbedIndexState,
            onReset = { scrubbedIndexState.value = null },
        )

        if (history.size < 2) {
            Text(
                text = "En az 2 haftalık düzenli öğün ve tartı verisi biriktiğinde zaman içindeki TDEE değişim eğrisi çizilir.",
                style = NutraTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            return@NutraCard
        }

        val (minY, maxY) = remember(history) {
            val vals = buildList {
                history.forEach { pt ->
                    add(pt.estimatedTdeeKcal - pt.uncertaintyKcal)
                    add(pt.estimatedTdeeKcal + pt.uncertaintyKcal)
                    add(pt.priorTdeeKcal)
                    pt.rollingIntakeAvgKcal?.let { add(it) }
                }
            }
            val lo = (vals.minOrNull() ?: 1400) - 120
            val hi = (vals.maxOrNull() ?: 2800) + 120
            lo.toFloat() to hi.toFloat().coerceAtLeast(lo + 400f)
        }

        TdeeHistoryChartCanvas(
            history = history,
            todayIso = todayIso,
            minY = minY,
            maxY = maxY,
            scrubbedIndexState = scrubbedIndexState,
            onScrub = { scrubbedIndexState.value = it },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = NutraFormatters.formatShortDate(history.first().dateIso, todayIso),
                style = NutraTheme.typography.caption,
                color = colors.textTertiary,
            )
            Text(
                text = "${NutraFormatters.formatInt(minY.roundToInt())} – ${NutraFormatters.formatInt(maxY.roundToInt())} kcal",
                style = NutraTheme.typography.caption,
                color = colors.textTertiary,
            )
            Text(
                text = NutraFormatters.formatShortDate(history.last().dateIso, todayIso),
                style = NutraTheme.typography.caption,
                color = colors.textTertiary,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChartLegendChip(colors.accent, "Adaptif TDEE")
            ChartLegendChip(colors.carbs, "Ort. alım (14 gün)")
            ChartLegendChip(colors.textTertiary, "Formül")
        }
    }
}

@Composable
private fun TdeeHistoryHeader(
    history: ImmutableList<TdeeHistoryPoint>,
    todayIso: String,
    scrubbedIndexState: State<Int?>,
    onReset: () -> Unit,
) {
    val colors = NutraTheme.colors
    val scrubbedIndex = scrubbedIndexState.value
    val activePoint = scrubbedIndex?.let { history.getOrNull(it) } ?: history.lastOrNull()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = if (scrubbedIndex != null && activePoint != null) {
                    "${NutraFormatters.formatShortDate(activePoint.dateIso, todayIso)} · TDEE"
                } else {
                    "Zaman içinde tahmini TDEE ve alım"
                },
                style = NutraTheme.typography.caption,
                color = if (scrubbedIndex != null) colors.onAccentSoft else colors.textSecondary,
            )
            if (activePoint != null) {
                Text(
                    text = "${NutraFormatters.formatInt(activePoint.estimatedTdeeKcal)} ± ${NutraFormatters.formatInt(activePoint.uncertaintyKcal)} kcal",
                    style = NutraTheme.typography.numberMedium,
                    color = colors.textPrimary,
                )
                activePoint.rollingIntakeAvgKcal?.let { intake ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        NutraDot(color = colors.carbs, size = 6.dp)
                        Text(
                            text = "14 gün ort. alım: ${NutraFormatters.formatInt(intake)} kcal",
                            style = NutraTheme.typography.caption,
                            color = colors.textSecondary,
                        )
                    }
                }
            }
        }
        if (scrubbedIndex != null) {
            NutraButton(
                text = "Son",
                onClick = onReset,
                style = NutraButtonStyle.Secondary,
                size = NutraButtonSize.Small,
                leadingIcon = Icons.Rounded.Refresh,
            )
        }
    }
}

@Composable
private fun TdeeHistoryChartCanvas(
    history: ImmutableList<TdeeHistoryPoint>,
    todayIso: String,
    minY: Float,
    maxY: Float,
    scrubbedIndexState: State<Int?>,
    onScrub: (Int?) -> Unit,
) {
    val colors = NutraTheme.colors
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(168.dp)
            .semantics {
                val activeIdx = scrubbedIndexState.value ?: history.lastIndex
                val pt = history.getOrNull(activeIdx)
                contentDescription = if (pt != null) {
                    "TDEE geçmişi grafiği. ${NutraFormatters.formatShortDate(pt.dateIso, todayIso)}: ${pt.estimatedTdeeKcal} artı eksi ${pt.uncertaintyKcal} kilokalori."
                } else {
                    "TDEE geçmişi grafiği"
                }
                customActions = buildList {
                    if (activeIdx > 0) {
                        add(CustomAccessibilityAction("Önceki noktayı incele") { onScrub(activeIdx - 1); true })
                    }
                    if (activeIdx < history.lastIndex) {
                        add(CustomAccessibilityAction("Sonraki noktayı incele") { onScrub(activeIdx + 1); true })
                    }
                    if (scrubbedIndexState.value != null) {
                        add(CustomAccessibilityAction("Son kayda dön") { onScrub(null); true })
                    }
                }
            }
            .pointerInput(history.size) {
                val padX = 10.dp.toPx()
                detectTapGestures { offset ->
                    val idx = metabolismChartIndexForX(offset.x, size.width, padX, history.size)
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onScrub(if (scrubbedIndexState.value == idx) null else idx)
                }
            }
            .pointerInput(history.size) {
                val padX = 10.dp.toPx()
                var lastIdx = -1
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        val idx = metabolismChartIndexForX(offset.x, size.width, padX, history.size)
                        lastIdx = idx
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onScrub(idx)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val idx = metabolismChartIndexForX(change.position.x, size.width, padX, history.size)
                        if (idx != lastIdx) {
                            lastIdx = idx
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onScrub(idx)
                        }
                    },
                )
            }
            .drawWithCache {
                val padX = 10.dp.toPx()
                val padY = 10.dp.toPx()
                val w = (size.width - padX * 2f).coerceAtLeast(1f)
                val h = (size.height - padY * 2f).coerceAtLeast(1f)
                val range = (maxY - minY).coerceAtLeast(200f)

                fun xFor(i: Int): Float = padX + w * (i.toFloat() / (history.size - 1).coerceAtLeast(1))
                fun yFor(v: Float): Float = padY + h * (1f - ((v - minY) / range).coerceIn(0f, 1f))

                val bandPath = Path().apply {
                    history.forEachIndexed { i, pt ->
                        val x = xFor(i)
                        val y = yFor((pt.estimatedTdeeKcal + pt.uncertaintyKcal).toFloat())
                        if (i == 0) moveTo(x, y) else lineTo(x, y)
                    }
                    for (i in history.lastIndex downTo 0) {
                        val pt = history[i]
                        lineTo(xFor(i), yFor((pt.estimatedTdeeKcal - pt.uncertaintyKcal).toFloat()))
                    }
                    close()
                }
                val tdeePath = Path().apply {
                    history.forEachIndexed { i, pt ->
                        val x = xFor(i)
                        val y = yFor(pt.estimatedTdeeKcal.toFloat())
                        if (i == 0) moveTo(x, y) else lineTo(x, y)
                    }
                }
                val intakePoints = history.mapIndexedNotNull { i, pt ->
                    pt.rollingIntakeAvgKcal?.let { Offset(xFor(i), yFor(it.toFloat())) }
                }
                val intakePath = Path().apply {
                    intakePoints.forEachIndexed { idx, offset ->
                        if (idx == 0) moveTo(offset.x, offset.y) else lineTo(offset.x, offset.y)
                    }
                }
                val priorY = yFor(history.last().priorTdeeKcal.toFloat())
                val bandColor = colors.accent.copy(alpha = 0.14f)
                val lineStroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val intakeStroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val dashStroke = 1.dp.toPx()

                onDrawBehind {
                    drawPath(bandPath, color = bandColor)
                    drawLine(
                        color = colors.textTertiary.copy(alpha = 0.45f),
                        start = Offset(padX, priorY),
                        end = Offset(size.width - padX, priorY),
                        strokeWidth = dashStroke,
                        pathEffect = DashedLineEffect,
                    )
                    drawPath(tdeePath, color = colors.accent, style = lineStroke)
                    if (intakePoints.size >= 2) {
                        drawPath(intakePath, color = colors.carbs, style = intakeStroke)
                    }
                    intakePoints.forEach { pt ->
                        drawCircle(color = colors.carbs, radius = 3.5.dp.toPx(), center = pt)
                    }
                    val activeIdx = scrubbedIndexState.value
                    if (activeIdx != null && activeIdx in history.indices) {
                        val cx = xFor(activeIdx)
                        val cy = yFor(history[activeIdx].estimatedTdeeKcal.toFloat())
                        drawLine(
                            color = colors.controlBorder,
                            start = Offset(cx, padY),
                            end = Offset(cx, size.height - padY),
                            strokeWidth = dashStroke,
                            pathEffect = CursorDashEffect,
                        )
                        drawCircle(color = colors.surface, radius = 6.dp.toPx(), center = Offset(cx, cy))
                        drawCircle(color = colors.accent, radius = 4.dp.toPx(), center = Offset(cx, cy))
                    }
                }
            },
    )
}

@Composable
private fun DynamicWeightProjectionCard(
    analysis: MetabolismAnalysisResult,
) {
    val colors = NutraTheme.colors
    val curve = analysis.projectionCurve
    val scrubbedIndexState = remember(curve) { mutableStateOf<Int?>(null) }

    NutraCard(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ProjectionChartHeader(
            analysis = analysis,
            scrubbedIndexState = scrubbedIndexState,
            onReset = { scrubbedIndexState.value = null },
        )

        if (curve.size >= 2) {
            val targetWeight = analysis.targetWeightKg
            val (minY, maxY) = remember(curve, targetWeight) {
                val vals = buildList {
                    curve.forEach {
                        add(it.expectedKg)
                        add(it.optimisticKg)
                        add(it.conservativeKg)
                    }
                    if (abs(targetWeight - curve.first().expectedKg) <= 20f) add(targetWeight)
                }
                val lo = (vals.minOrNull() ?: 60f)
                val hi = (vals.maxOrNull() ?: 80f)
                val pad = ((hi - lo).coerceAtLeast(1.5f)) * 0.16f
                (lo - pad) to (hi + pad)
            }

            val immutableCurve = remember(curve) { curve.toImmutableList() }
            ProjectionChartCanvas(
                curve = immutableCurve,
                todayIso = analysis.analysisDateIso,
                targetWeight = targetWeight,
                minY = minY,
                maxY = maxY,
                scrubbedIndexState = scrubbedIndexState,
                onScrub = { scrubbedIndexState.value = it },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Bugün",
                    style = NutraTheme.typography.caption,
                    color = colors.textTertiary,
                )
                Text(
                    text = "${NutraFormatters.formatKg(minY)} – ${NutraFormatters.formatKg(maxY)} kg",
                    style = NutraTheme.typography.caption,
                    color = colors.textTertiary,
                )
                Text(
                    text = "${curve.last().weekOffset}. hafta",
                    style = NutraTheme.typography.caption,
                    color = colors.textTertiary,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChartLegendChip(colors.water, "Beklenen seyir")
                ChartLegendChip(colors.water.copy(alpha = 0.35f), "Belirsizlik bandı")
                ChartLegendChip(colors.accent, "Hedef kilo")
            }
        }

        analysis.targetEta?.let { eta ->
            Text(
                text = eta.summaryText,
                style = NutraTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
        Text(
            text = "Kilo değişimi doğrusal ilerlemez; vücut ağırlığı değiştikçe günlük enerji ihtiyacı da kademeli olarak uyum sağlar. Taralı alan belirsizlik aralığını gösterir.",
            style = NutraTheme.typography.caption,
            color = colors.textTertiary,
        )
    }
}

@Composable
private fun ProjectionChartHeader(
    analysis: MetabolismAnalysisResult,
    scrubbedIndexState: State<Int?>,
    onReset: () -> Unit,
) {
    val colors = NutraTheme.colors
    val curve = analysis.projectionCurve
    val scrubbedIndex = scrubbedIndexState.value
    val activePoint: WeightProjectionPoint? = scrubbedIndex?.let { curve.getOrNull(it) } ?: curve.lastOrNull()
    val referenceToday = remember(analysis.analysisDateIso) {
        runCatching { java.time.LocalDate.parse(analysis.analysisDateIso) }.getOrElse { java.time.LocalDate.now() }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = if (scrubbedIndex != null && activePoint != null) {
                    "${activePoint.weekOffset}. hafta (${NutraFormatters.formatShortDateWithYearIfNeeded(activePoint.dateIso, referenceToday)})"
                } else {
                    "Doğrusal olmayan kilo projeksiyonu (16 hafta)"
                },
                style = NutraTheme.typography.caption,
                color = if (scrubbedIndex != null) colors.onAccentSoft else colors.textSecondary,
            )
            if (activePoint != null) {
                val low = minOf(activePoint.optimisticKg, activePoint.conservativeKg)
                val high = maxOf(activePoint.optimisticKg, activePoint.conservativeKg)
                Text(
                    text = "${NutraFormatters.formatKg(activePoint.expectedKg)} kg (${NutraFormatters.formatKg(low)}–${NutraFormatters.formatKg(high)} kg)",
                    style = NutraTheme.typography.numberSmall,
                    color = colors.textPrimary,
                )
            }
        }
        if (scrubbedIndex != null) {
            NutraButton(
                text = "Özet",
                onClick = onReset,
                style = NutraButtonStyle.Secondary,
                size = NutraButtonSize.Small,
                leadingIcon = Icons.Rounded.Refresh,
            )
        }
    }
}

@Composable
private fun ProjectionChartCanvas(
    curve: ImmutableList<WeightProjectionPoint>,
    todayIso: String,
    targetWeight: Float,
    minY: Float,
    maxY: Float,
    scrubbedIndexState: State<Int?>,
    onScrub: (Int?) -> Unit,
) {
    val colors = NutraTheme.colors
    val haptic = LocalHapticFeedback.current
    val referenceToday = remember(todayIso) {
        runCatching { java.time.LocalDate.parse(todayIso) }.getOrElse { java.time.LocalDate.now() }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(168.dp)
            .semantics {
                val activeIdx = scrubbedIndexState.value ?: curve.lastIndex
                val pt = curve.getOrNull(activeIdx)
                contentDescription = if (pt != null) {
                    "Kilo projeksiyonu grafiği. ${pt.weekOffset}. hafta (${NutraFormatters.formatShortDateWithYearIfNeeded(pt.dateIso, referenceToday)}): beklenen ${NutraFormatters.formatKg(pt.expectedKg)} kilogram."
                } else {
                    "Kilo projeksiyonu grafiği"
                }
                customActions = buildList {
                    if (activeIdx > 0) {
                        add(CustomAccessibilityAction("Önceki haftayı incele") { onScrub(activeIdx - 1); true })
                    }
                    if (activeIdx < curve.lastIndex) {
                        add(CustomAccessibilityAction("Sonraki haftayı incele") { onScrub(activeIdx + 1); true })
                    }
                    if (scrubbedIndexState.value != null) {
                        add(CustomAccessibilityAction("Özete dön") { onScrub(null); true })
                    }
                }
            }
            .pointerInput(curve.size) {
                val padX = 10.dp.toPx()
                detectTapGestures { offset ->
                    val idx = metabolismChartIndexForX(offset.x, size.width, padX, curve.size)
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onScrub(if (scrubbedIndexState.value == idx) null else idx)
                }
            }
            .pointerInput(curve.size) {
                val padX = 10.dp.toPx()
                var lastIdx = -1
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        val idx = metabolismChartIndexForX(offset.x, size.width, padX, curve.size)
                        lastIdx = idx
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onScrub(idx)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val idx = metabolismChartIndexForX(change.position.x, size.width, padX, curve.size)
                        if (idx != lastIdx) {
                            lastIdx = idx
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onScrub(idx)
                        }
                    },
                )
            }
            .drawWithCache {
                val padX = 10.dp.toPx()
                val padY = 10.dp.toPx()
                val w = (size.width - padX * 2f).coerceAtLeast(1f)
                val h = (size.height - padY * 2f).coerceAtLeast(1f)
                val range = (maxY - minY).coerceAtLeast(1f)

                fun xFor(i: Int): Float = padX + w * (i.toFloat() / (curve.size - 1).coerceAtLeast(1))
                fun yFor(kg: Float): Float = padY + h * (1f - ((kg - minY) / range).coerceIn(0f, 1f))

                val fanPath = Path().apply {
                    curve.forEachIndexed { i, pt ->
                        val hi = maxOf(pt.optimisticKg, pt.conservativeKg)
                        if (i == 0) moveTo(xFor(i), yFor(hi)) else lineTo(xFor(i), yFor(hi))
                    }
                    for (i in curve.lastIndex downTo 0) {
                        val lo = minOf(curve[i].optimisticKg, curve[i].conservativeKg)
                        lineTo(xFor(i), yFor(lo))
                    }
                    close()
                }
                val expectedPath = Path().apply {
                    curve.forEachIndexed { i, pt ->
                        if (i == 0) moveTo(xFor(i), yFor(pt.expectedKg)) else lineTo(xFor(i), yFor(pt.expectedKg))
                    }
                }
                val targetY = yFor(targetWeight)
                val showTarget = targetWeight in minY..maxY
                val strokeStyle = Stroke(
                    width = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    pathEffect = DashedLineEffect,
                )

                onDrawBehind {
                    drawPath(fanPath, color = colors.water.copy(alpha = 0.16f))
                    if (showTarget) {
                        drawLine(
                            color = colors.accent.copy(alpha = 0.55f),
                            start = Offset(padX, targetY),
                            end = Offset(size.width - padX, targetY),
                            strokeWidth = 1.2.dp.toPx(),
                            pathEffect = DashedLineEffect,
                        )
                    }
                    drawPath(expectedPath, color = colors.water, style = strokeStyle)
                    val idx = scrubbedIndexState.value
                    if (idx != null && idx in curve.indices) {
                        val cx = xFor(idx)
                        val cy = yFor(curve[idx].expectedKg)
                        drawLine(
                            color = colors.controlBorder,
                            start = Offset(cx, padY),
                            end = Offset(cx, size.height - padY),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = CursorDashEffect,
                        )
                        drawCircle(color = colors.surface, radius = 6.dp.toPx(), center = Offset(cx, cy))
                        drawCircle(color = colors.water, radius = 4.dp.toPx(), center = Offset(cx, cy))
                    }
                }
            },
    )
}

@Composable
private fun CompactStatColumn(
    label: String,
    value: String,
) {
    val colors = NutraTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = NutraTheme.typography.caption,
            color = colors.textTertiary,
            maxLines = 1,
        )
        Text(
            text = value,
            style = NutraTheme.typography.label,
            color = colors.textPrimary,
            maxLines = 1,
        )
    }
}

@Composable
private fun ChartLegendChip(color: Color, label: String) {
    val colors = NutraTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NutraDot(color = color, size = 7.dp)
        Text(
            text = label,
            style = NutraTheme.typography.caption,
            color = colors.textSecondary,
        )
    }
}
