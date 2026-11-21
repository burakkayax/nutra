package com.nutra.app.feature.intelligence

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nutra.app.core.designsystem.NutraBadge
import com.nutra.app.core.designsystem.NutraButton
import com.nutra.app.core.designsystem.NutraButtonSize
import com.nutra.app.core.designsystem.NutraButtonStyle
import com.nutra.app.core.designsystem.NutraCard
import com.nutra.app.core.designsystem.NutraChip
import com.nutra.app.core.designsystem.NutraContentMaxWidth
import com.nutra.app.core.designsystem.NutraDivider
import com.nutra.app.core.designsystem.NutraDot
import com.nutra.app.core.designsystem.NutraEmptyState
import com.nutra.app.core.designsystem.NutraFormatters
import com.nutra.app.core.designsystem.NutraIconBadge
import com.nutra.app.core.designsystem.NutraIconButton
import com.nutra.app.core.designsystem.NutraIcons
import com.nutra.app.core.designsystem.NutraLinearProgress
import com.nutra.app.core.designsystem.NutraSectionHeader
import com.nutra.app.core.designsystem.NutraSegmentedControl
import com.nutra.app.core.designsystem.NutraSnackbarHost
import com.nutra.app.core.designsystem.NutraSnackbarTone
import com.nutra.app.core.designsystem.NutraStepperButton
import com.nutra.app.core.designsystem.NutraTextField
import com.nutra.app.core.designsystem.NutraTheme
import com.nutra.app.domain.intelligence.HabitMaturity
import com.nutra.app.domain.intelligence.MealRecommendation
import com.nutra.app.domain.intelligence.RecommendedFoodPortion
import com.nutra.app.domain.model.FoodItem
import com.nutra.app.domain.model.MealType
import com.nutra.app.domain.model.ServingUnitLabels
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Compact, calm contextual card displayed on DashboardScreen linking to Nutra Intelligence.
 */
@Composable
fun DashboardIntelligenceCard(
    cardState: DashboardIntelligenceCardState,
    onOpenStudio: () -> Unit,
    onOpenBalancer: () -> Unit,
    onQuickLogTopRecommendation: (MealRecommendation) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NutraTheme.colors
    val topRec = cardState.topRecommendation

    NutraCard(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NutraIconBadge(
                    icon = NutraIcons.AutoAwesome,
                    tint = colors.onAccentSoft,
                    container = colors.accentSoft,
                    size = 36.dp,
                    iconSize = 18.dp,
                )
                Text(
                    text = "Nutra Intelligence",
                    style = NutraTheme.typography.titleSmall,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                NutraBadge(
                    text = cardState.maturity.title,
                    color = colors.textSecondary,
                    container = colors.surfaceMuted,
                )
            }
            Text(
                text = "${cardState.mealType.title} · ~${NutraFormatters.formatInt(cardState.targetCalories)} kcal · ${NutraFormatters.formatInt(cardState.targetProteinGrams.roundToInt())} g protein",
                style = NutraTheme.typography.caption,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (topRec != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(NutraTheme.shapes.md)
                    .background(colors.surfaceMuted)
                    .clickable(onClick = onOpenStudio)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = topRec.title,
                        style = NutraTheme.typography.label,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                    )
                    Text(
                        text = "${NutraFormatters.formatInt(topRec.totalCalories)} kcal",
                        style = NutraTheme.typography.numberSmall,
                        color = colors.accent,
                    )
                }
                Text(
                    text = "P ${NutraFormatters.formatMacroGrams(topRec.totalProtein)}g · K ${NutraFormatters.formatMacroGrams(topRec.totalCarbs)}g · Y ${NutraFormatters.formatMacroGrams(topRec.totalFat)}g",
                    style = NutraTheme.typography.caption,
                    color = colors.textSecondary,
                )
                topRec.reasons.firstOrNull()?.let { reason ->
                    Text(
                        text = reason,
                        style = NutraTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        } else {
            Text(
                text = cardState.fallbackNote,
                style = NutraTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NutraButton(
                text = "Önerileri gör",
                onClick = onOpenStudio,
                style = NutraButtonStyle.Primary,
                size = NutraButtonSize.Small,
                trailingIcon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                modifier = Modifier.weight(1f),
            )
            if (topRec != null) {
                NutraButton(
                    text = "Hızlı ekle",
                    onClick = { onQuickLogTopRecommendation(topRec) },
                    style = NutraButtonStyle.Secondary,
                    size = NutraButtonSize.Small,
                    leadingIcon = Icons.Rounded.Add,
                    hapticFeedback = HapticFeedbackType.Confirm,
                )
            } else {
                NutraButton(
                    text = "Öğün dengele",
                    onClick = onOpenBalancer,
                    style = NutraButtonStyle.Secondary,
                    size = NutraButtonSize.Small,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NutraIntelligenceScreen(
    viewModel: IntelligenceViewModel,
    onNavigateBack: () -> Unit,
    onLoggedMeal: (MealType, String) -> Unit = { _, _ -> },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = NutraTheme.colors

    BackHandler(onBack = onNavigateBack)

    LaunchedEffect(state.feedbackEventId, state.feedbackMessage) {
        if (state.feedbackMessage != null) {
            delay(2800)
            viewModel.clearFeedback()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = NutraContentMaxWidth)
                .fillMaxSize(),
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NutraTheme.spacing.screen)
                    .padding(top = NutraTheme.spacing.section, bottom = NutraTheme.spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NutraTheme.spacing.md),
            ) {
                NutraIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Geri",
                    onClick = onNavigateBack,
                    size = 44.dp,
                    iconSize = 20.dp,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "Nutra Intelligence",
                        style = NutraTheme.typography.titleMedium,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Kişiselleştirilmiş öğün ve porsiyon",
                        style = NutraTheme.typography.caption,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (state.activeTab == IntelligenceStudioTab.RECOMMENDATIONS) {
                    NutraIconButton(
                        icon = Icons.Rounded.Refresh,
                        contentDescription = "Yeni alternatifler",
                        onClick = viewModel::refreshAlternatives,
                        size = 44.dp,
                        iconSize = 20.dp,
                    )
                }
            }

            // Meal selector chips + Mode segmented control
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NutraTheme.spacing.screen),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NutraSegmentedControl(
                    items = IntelligenceStudioTab.entries,
                    selectedItem = state.activeTab,
                    onItemSelected = viewModel::selectTab,
                    labelProvider = { it.title },
                )

                val mealChipsListState = rememberLazyListState()
                LaunchedEffect(state.selectedMealType) {
                    val targetIdx = MealType.entries.indexOf(state.selectedMealType).coerceAtLeast(0)
                    mealChipsListState.animateScrollToItem(targetIdx)
                }
                LazyRow(
                    state = mealChipsListState,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(MealType.entries, key = { it.name }) { meal ->
                        NutraChip(
                            text = meal.title,
                            selected = meal == state.selectedMealType,
                            onClick = { viewModel.selectMealType(meal) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            AnimatedContent(
                targetState = state.activeTab,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                label = "IntelligenceTabContent",
            ) { tab ->
                when (tab) {
                    IntelligenceStudioTab.RECOMMENDATIONS -> RecommendationsTabContent(
                        state = state,
                        viewModel = viewModel,
                        onLoggedMeal = onLoggedMeal,
                    )
                    IntelligenceStudioTab.PORTION_BALANCER -> PortionBalancerTabContent(
                        state = state,
                        viewModel = viewModel,
                        onLoggedMeal = onLoggedMeal,
                    )
                }
            }
        }

        NutraSnackbarHost(
            visible = state.feedbackMessage != null,
            message = state.feedbackMessage.orEmpty(),
            tone = NutraSnackbarTone.Success,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = NutraTheme.spacing.screen, vertical = 12.dp),
        )
    }
}

@Composable
private fun RecommendationsTabContent(
    state: IntelligenceUiState,
    viewModel: IntelligenceViewModel,
    onLoggedMeal: (MealType, String) -> Unit,
) {
    val colors = NutraTheme.colors
    val report = state.report

    if (report == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Öneriler hazırlanıyor...",
                style = NutraTheme.typography.body,
                color = colors.textSecondary,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = NutraTheme.spacing.screen,
            end = NutraTheme.spacing.screen,
            top = 4.dp,
            bottom = 36.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Budget & Learning Context Card
        item(key = "budget_overview") {
            NutraCard(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${report.mealType.title} hedef bütçesi",
                        style = NutraTheme.typography.titleSmall,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    NutraBadge(
                        text = report.eatingProfile.maturity.title,
                        color = if (report.eatingProfile.maturity == HabitMaturity.ESTABLISHED) {
                            colors.onAccentSoft
                        } else {
                            colors.textSecondary
                        },
                        container = if (report.eatingProfile.maturity == HabitMaturity.ESTABLISHED) {
                            colors.accentSoft
                        } else {
                            colors.surfaceMuted
                        },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    BudgetMetricPill(
                        label = "Kalori",
                        value = "${NutraFormatters.formatInt(report.budget.targetCalories)} kcal",
                        accent = colors.accent,
                        modifier = Modifier.weight(1f),
                    )
                    BudgetMetricPill(
                        label = "Protein",
                        value = "${NutraFormatters.formatInt(report.budget.targetProteinGrams.roundToInt())} g",
                        accent = colors.protein,
                        modifier = Modifier.weight(1f),
                    )
                    BudgetMetricPill(
                        label = "Karb.",
                        value = "${NutraFormatters.formatInt(report.budget.targetCarbsGrams.roundToInt())} g",
                        accent = colors.carbs,
                        modifier = Modifier.weight(1f),
                    )
                    BudgetMetricPill(
                        label = "Yağ",
                        value = "${NutraFormatters.formatInt(report.budget.targetFatGrams.roundToInt())} g",
                        accent = colors.fat,
                        modifier = Modifier.weight(1f),
                    )
                }

                Text(
                    text = report.budget.budgetSummaryText,
                    style = NutraTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )

                report.prioritizedMicronutrientNote?.let { note ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(NutraTheme.shapes.sm)
                            .background(colors.accentSoft)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NutraDot(color = colors.accent, size = 7.dp)
                        Text(
                            text = note,
                            style = NutraTheme.typography.caption,
                            color = colors.onAccentSoft,
                        )
                    }
                }

                report.coldStartNote?.let { coldNote ->
                    Text(
                        text = coldNote,
                        style = NutraTheme.typography.caption,
                        color = colors.textTertiary,
                    )
                }
            }
        }

        if (report.recommendations.isEmpty()) {
            item(key = "empty_recs") {
                NutraEmptyState(
                    icon = NutraIcons.Restaurant,
                    title = "Uygun öneri oluşturulamadı",
                    message = "Alerjen veya beslenme kısıtların tüm adayları elemiş olabilir. Profil → Beslenme tercihlerinden filtrelerini kontrol edebilir veya Öğün Dengele sekmesini kullanabilirsin.",
                )
            }
        } else {
            items(report.recommendations, key = { it.id }) { effective ->
                RecommendationDetailCard(
                    recommendation = effective,
                    hasManualEdits = !state.portionOverrides[effective.id].isNullOrEmpty(),
                    onAdjustPortion = { portion, delta ->
                        viewModel.adjustRecommendationItemGrams(effective.id, portion, delta)
                    },
                    onResetPortions = { viewModel.resetRecommendationPortions(effective.id) },
                    onCustomizeInBalancer = { viewModel.loadRecommendationIntoBalancer(effective) },
                    onDislikeFood = viewModel::dislikeFood,
                    onAcceptAndLog = {
                        viewModel.logRecommendation(effective, onLogged = onLoggedMeal)
                    },
                )
            }
        }

        item(key = "refresh_footer") {
            NutraButton(
                text = "Farklı öğün alternatifleri üret",
                onClick = viewModel::refreshAlternatives,
                style = NutraButtonStyle.Secondary,
                size = NutraButtonSize.Medium,
                leadingIcon = Icons.Rounded.Refresh,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecommendationDetailCard(
    recommendation: MealRecommendation,
    hasManualEdits: Boolean,
    onAdjustPortion: (RecommendedFoodPortion, Float) -> Unit,
    onResetPortions: () -> Unit,
    onCustomizeInBalancer: () -> Unit,
    onDislikeFood: (RecommendedFoodPortion) -> Unit,
    onAcceptAndLog: () -> Unit,
) {
    val colors = NutraTheme.colors
    var isEditingPortions by remember(recommendation.id) { mutableStateOf(false) }

    NutraCard(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Header: tag + title + fit badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = recommendation.styleTag.label,
                    style = NutraTheme.typography.overline,
                    color = colors.accent,
                )
                Text(
                    text = recommendation.title,
                    style = NutraTheme.typography.titleSmall,
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            NutraBadge(
                text = "${NutraFormatters.formatInt(recommendation.totalCalories)} kcal",
                color = colors.onAccentSoft,
                container = colors.accentSoft,
            )
        }

        // Macro strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(NutraTheme.shapes.sm)
                .background(colors.surfaceMuted)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MacroInlineTag("P", "${NutraFormatters.formatMacroGrams(recommendation.totalProtein)}g", colors.protein)
            MacroInlineTag("K", "${NutraFormatters.formatMacroGrams(recommendation.totalCarbs)}g", colors.carbs)
            MacroInlineTag("Y", "${NutraFormatters.formatMacroGrams(recommendation.totalFat)}g", colors.fat)
            MacroInlineTag("Uyum", "%${recommendation.fitScore}", colors.accent)
        }

        // Food items list with optional inline portion editing & dislike button
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            recommendation.items.forEachIndexed { idx, portion ->
                if (idx > 0) NutraDivider()
                RecommendedPortionRow(
                    portion = portion,
                    isEditing = isEditingPortions,
                    onAdjustGrams = { delta -> onAdjustPortion(portion, delta) },
                    onDislike = { onDislikeFood(portion) },
                )
            }
        }

        // Known Micronutrients (missing-data transparent)
        if (recommendation.micronutrientHighlights.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                recommendation.micronutrientHighlights.forEach { micro ->
                    NutraBadge(
                        text = "${micro.type.title}: ${micro.formattedValue}",
                        color = colors.textSecondary,
                        container = colors.surfaceMuted,
                    )
                }
            }
            recommendation.missingMicronutrientNote?.let { missing ->
                Text(
                    text = missing,
                    style = NutraTheme.typography.caption,
                    color = colors.textTertiary,
                )
            }
        }

        // Reasons ("Neden bu öğün?")
        if (recommendation.reasons.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Neden bu öneri?",
                    style = NutraTheme.typography.labelSmall,
                    color = colors.textPrimary,
                )
                recommendation.reasons.forEach { reason ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        NutraDot(
                            color = colors.accent,
                            size = 5.dp,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Text(
                            text = reason,
                            style = NutraTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                }
            }
        }

        // Expected daily goal impact preview
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(NutraTheme.shapes.sm)
                .background(colors.surfaceMuted)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = recommendation.goalImpact.impactSummary,
                style = NutraTheme.typography.caption,
                color = colors.textPrimary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Kalori %${recommendation.goalImpact.caloriePercentAfter}",
                        style = NutraTheme.typography.caption,
                        color = colors.textSecondary,
                    )
                    NutraLinearProgress(
                        progress = recommendation.goalImpact.caloriesAfter.toFloat() /
                            recommendation.goalImpact.calorieGoal.coerceAtLeast(1).toFloat(),
                        color = colors.accent,
                        height = 4.dp,
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Protein %${recommendation.goalImpact.proteinPercentAfter}",
                        style = NutraTheme.typography.caption,
                        color = colors.textSecondary,
                    )
                    NutraLinearProgress(
                        progress = recommendation.goalImpact.proteinAfter /
                            recommendation.goalImpact.proteinGoal.coerceAtLeast(1).toFloat(),
                        color = colors.protein,
                        height = 4.dp,
                    )
                }
            }
        }

        // Action row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NutraButton(
                text = "${recommendation.mealType.title} ekle",
                onClick = onAcceptAndLog,
                style = NutraButtonStyle.Primary,
                size = NutraButtonSize.Medium,
                leadingIcon = Icons.Rounded.Add,
                hapticFeedback = HapticFeedbackType.Confirm,
                modifier = Modifier.weight(1f),
            )
            NutraIconButton(
                icon = Icons.Rounded.Edit,
                contentDescription = "Porsiyonları düzenle",
                onClick = { isEditingPortions = !isEditingPortions },
                containerColor = if (isEditingPortions) colors.accentSoft else colors.surfaceMuted,
                contentColor = if (isEditingPortions) colors.onAccentSoft else colors.textPrimary,
                size = 48.dp,
            )
        }

        AnimatedVisibility(visible = isEditingPortions) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (hasManualEdits) {
                    NutraButton(
                        text = "Otomatik porsiyona dön",
                        onClick = onResetPortions,
                        style = NutraButtonStyle.Secondary,
                        size = NutraButtonSize.Small,
                        modifier = Modifier.weight(1f),
                    )
                }
                NutraButton(
                    text = "Dengeleyicide özelleştir",
                    onClick = onCustomizeInBalancer,
                    style = NutraButtonStyle.Secondary,
                    size = NutraButtonSize.Small,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun RecommendedPortionRow(
    portion: RecommendedFoodPortion,
    isEditing: Boolean,
    onAdjustGrams: (Float) -> Unit,
    onDislike: (() -> Unit)? = null,
) {
    val colors = NutraTheme.colors
    val suffix = portion.food.displayUnitSuffix
    val quantityText = if (ServingUnitLabels.isRawUnitLabel(portion.unitLabel)) {
        "${portion.totalGrams.roundToInt()} $suffix"
    } else {
        "${NutraFormatters.formatMultiplier(portion.multiplier)}x ${portion.unitLabel} (${portion.totalGrams.roundToInt()} $suffix)"
    }
    val step = if (portion.maxGrams <= 60f) 5f else if (portion.maxGrams <= 150f) 10f else 20f

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = portion.food.name,
                    style = NutraTheme.typography.label,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "$quantityText · ${NutraFormatters.formatInt(portion.calories)} kcal · P ${NutraFormatters.formatMacroGrams(portion.proteinGrams)}g",
                    style = NutraTheme.typography.caption,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (isEditing) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NutraStepperButton(
                        icon = NutraIcons.Remove,
                        contentDescription = "Porsiyonu azalt",
                        onClick = { onAdjustGrams(-step) },
                        enabled = portion.totalGrams > portion.minGrams + 1f,
                    )
                    NutraStepperButton(
                        icon = Icons.Rounded.Add,
                        contentDescription = "Porsiyonu artır",
                        onClick = { onAdjustGrams(step) },
                        enabled = portion.totalGrams < portion.maxGrams - 1f,
                    )
                    if (onDislike != null) {
                        NutraIconButton(
                            icon = Icons.Rounded.Close,
                            contentDescription = "Bu besini bir daha önerme",
                            onClick = onDislike,
                            size = 34.dp,
                            iconSize = 15.dp,
                            containerColor = colors.dangerSoft,
                            contentColor = colors.dangerText,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PortionBalancerTabContent(
    state: IntelligenceUiState,
    viewModel: IntelligenceViewModel,
    onLoggedMeal: (MealType, String) -> Unit,
) {
    val colors = NutraTheme.colors
    val report = state.report

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = NutraTheme.spacing.screen,
            end = NutraTheme.spacing.screen,
            top = 4.dp,
            bottom = 36.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "balancer_intro") {
            NutraCard(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Kendi öğününü hedefe göre dengele",
                    style = NutraTheme.typography.titleSmall,
                    color = colors.textPrimary,
                )
                Text(
                    text = "Yemek istediğin 2–4 besini seç (ör. tavuk, bulgur, yoğurt). Nutra Intelligence kalan kalori ve protein hedefine göre en gerçekçi gramajları otomatik hesaplar.",
                    style = NutraTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
                if (report != null) {
                    Text(
                        text = "Aktif hedef (${state.selectedMealType.title}): ~${NutraFormatters.formatInt(report.budget.targetCalories)} kcal · ${NutraFormatters.formatInt(report.budget.targetProteinGrams.roundToInt())} g protein",
                        style = NutraTheme.typography.caption,
                        color = colors.accent,
                    )
                }
            }
        }

        // Selected food pills
        if (state.builderSelectedFoods.isNotEmpty()) {
            item(key = "selected_chips") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NutraSectionHeader(
                        title = "Seçilen besinler (${state.builderSelectedFoods.size}/4)",
                        actionLabel = "Temizle",
                        onAction = viewModel::clearBuilderSelection,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.builderSelectedFoods.forEach { food ->
                            NutraChip(
                                text = food.name,
                                selected = true,
                                leadingIcon = Icons.Rounded.Close,
                                onClick = { viewModel.toggleBuilderFood(food) },
                            )
                        }
                    }
                }
            }
        }

        // Live Optimized Result Card
        val optimized = state.builderOptimized
        if (optimized != null && optimized.items.isNotEmpty()) {
            item(key = "optimized_result") {
                NutraCard(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Önerilen porsiyonlar",
                            style = NutraTheme.typography.titleSmall,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        NutraBadge(
                            text = "${NutraFormatters.formatInt(optimized.totalCalories)} kcal · %${optimized.fitScore}",
                            color = colors.onAccentSoft,
                            container = colors.accentSoft,
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(NutraTheme.shapes.sm)
                            .background(colors.surfaceMuted)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        MacroInlineTag("Protein", "${NutraFormatters.formatMacroGrams(optimized.totalProtein)} g", colors.protein)
                        MacroInlineTag("Karb.", "${NutraFormatters.formatMacroGrams(optimized.totalCarbs)} g", colors.carbs)
                        MacroInlineTag("Yağ", "${NutraFormatters.formatMacroGrams(optimized.totalFat)} g", colors.fat)
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        optimized.items.forEachIndexed { idx, portion ->
                            if (idx > 0) NutraDivider()
                            RecommendedPortionRow(
                                portion = portion,
                                isEditing = true,
                                onAdjustGrams = { delta -> viewModel.adjustBuilderItemGrams(portion, delta) },
                            )
                        }
                    }

                    Text(
                        text = optimized.note,
                        style = NutraTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        NutraButton(
                            text = "${state.selectedMealType.title} ekle",
                            onClick = { viewModel.logBuilderCombo(onLogged = onLoggedMeal) },
                            style = NutraButtonStyle.Primary,
                            size = NutraButtonSize.Medium,
                            leadingIcon = Icons.Rounded.Add,
                            hapticFeedback = HapticFeedbackType.Confirm,
                            modifier = Modifier.weight(1f),
                        )
                        if (state.builderGramOverrides.isNotEmpty()) {
                            NutraIconButton(
                                icon = Icons.Rounded.Refresh,
                                contentDescription = "Yeniden dengele",
                                onClick = viewModel::reoptimizeBuilderPortions,
                                size = 48.dp,
                            )
                        }
                    }
                }
            }
        }

        // Catalog search to add foods (up to 4)
        if (state.builderSelectedFoods.size < 4) {
            item(key = "builder_search") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    NutraSectionHeader(title = "Besin seç")
                    NutraTextField(
                        value = state.builderQuery,
                        onValueChange = viewModel::updateBuilderQuery,
                        placeholder = "Tavuk, bulgur, yoğurt, yumurta ara...",
                        leadingIcon = Icons.Rounded.Search,
                        trailing = if (state.builderQuery.isNotEmpty()) {
                            {
                                NutraIconButton(
                                    icon = Icons.Rounded.Close,
                                    contentDescription = "Temizle",
                                    onClick = { viewModel.updateBuilderQuery("") },
                                    size = 36.dp,
                                    iconSize = 16.dp,
                                )
                            }
                        } else {
                            null
                        },
                    )
                }
            }

            items(state.builderCatalogMatches, key = { "builder_${it.id}" }) { food ->
                val isSelected = state.builderSelectedFoods.any { it.id == food.id }
                BalancerCatalogRow(
                    food = food,
                    isSelected = isSelected,
                    onClick = { viewModel.toggleBuilderFood(food) },
                )
            }
        }

        item(key = "balancer_bottom_space") {
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun BalancerCatalogRow(
    food: FoodItem,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val colors = NutraTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(NutraTheme.shapes.md)
            .background(if (isSelected) colors.accentSoft else colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = food.name,
                style = NutraTheme.typography.label,
                color = if (isSelected) colors.onAccentSoft else colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "100 ${food.displayUnitSuffix} · ${food.caloriesPer100g} kcal · P ${NutraFormatters.formatMacroGrams(food.proteinPer100g)}g",
                style = NutraTheme.typography.caption,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = if (isSelected) Icons.Rounded.Close else Icons.Rounded.Add,
            contentDescription = if (isSelected) "Çıkar" else "Ekle",
            tint = if (isSelected) colors.onAccentSoft else colors.textSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun BudgetMetricPill(
    label: String,
    value: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    val colors = NutraTheme.colors
    Column(
        modifier = modifier
            .clip(NutraTheme.shapes.sm)
            .background(colors.surfaceMuted)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NutraDot(color = accent, size = 5.dp)
            Text(
                text = label,
                style = NutraTheme.typography.caption,
                color = colors.textSecondary,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = value,
            style = NutraTheme.typography.labelSmall,
            color = colors.textPrimary,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MacroInlineTag(
    label: String,
    value: String,
    dotColor: androidx.compose.ui.graphics.Color,
) {
    val colors = NutraTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NutraDot(color = dotColor, size = 6.dp)
        Text(
            text = "$label $value",
            style = NutraTheme.typography.caption,
            color = colors.textPrimary,
            maxLines = 1,
            softWrap = false,
        )
    }
}
