package com.nutra.app.feature.intelligence

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import com.nutra.app.core.designsystem.NutraIconButton
import com.nutra.app.core.designsystem.NutraIcons
import com.nutra.app.core.designsystem.NutraLinearProgress
import com.nutra.app.core.designsystem.NutraSnackbarHost
import com.nutra.app.core.designsystem.NutraSnackbarTone
import com.nutra.app.core.designsystem.NutraStepperButton
import com.nutra.app.core.designsystem.NutraStickyScreenHeader
import com.nutra.app.core.designsystem.NutraStickySubScreenHeaderTopReservedPadding
import com.nutra.app.core.designsystem.NutraTheme
import com.nutra.app.core.designsystem.rememberAccessibleSnackbarTimeout
import com.nutra.app.core.designsystem.rememberHeaderCollapseFraction
import com.nutra.app.domain.intelligence.MealRecommendation
import com.nutra.app.domain.intelligence.RecommendedFoodPortion
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
    onOpenStudio: (MealType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pages = cardState.mealPages
    if (pages.isEmpty()) return
    val colors = NutraTheme.colors
    val defaultPageIndex = remember(cardState.defaultMealType, pages) {
        pages.indexOfFirst { it.mealType == cardState.defaultMealType }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(initialPage = defaultPageIndex) { pages.size }

    LaunchedEffect(cardState.defaultMealType, pages.size) {
        val targetIdx = pages.indexOfFirst { it.mealType == cardState.defaultMealType }
        if (targetIdx >= 0 && targetIdx != pagerState.currentPage && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(targetIdx)
        }
    }

    val currentMealType = pages.getOrNull(pagerState.currentPage)?.mealType ?: cardState.defaultMealType

    NutraCard(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Önerilen öğünler",
                    style = NutraTheme.typography.titleSmall,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Alışkanlıklarına göre kişiselleştirildi",
                    style = NutraTheme.typography.caption,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (pages.size > 1) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    pages.indices.forEach { idx ->
                        val isSelected = idx == pagerState.currentPage
                        NutraDot(
                            color = if (isSelected) colors.accent else colors.controlBorder,
                            size = if (isSelected) 7.dp else 5.dp,
                        )
                    }
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            pageSpacing = 10.dp,
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth(),
        ) { pageIndex ->
            val page = pages[pageIndex]
            val topRec = page.topRecommendation
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(NutraTheme.shapes.md)
                    .background(colors.surfaceMuted)
                    .clickable { onOpenStudio(page.mealType) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = page.mealType.title,
                        style = NutraTheme.typography.labelSmall,
                        color = colors.accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${NutraFormatters.formatInt(topRec.totalCalories)} kcal · ${NutraFormatters.formatInt(topRec.totalProtein.roundToInt())} g protein",
                        style = NutraTheme.typography.caption,
                        color = colors.textSecondary,
                        maxLines = 1,
                    )
                }

                NutraDivider()

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    topRec.items.forEach { portion ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = portion.food.name,
                                style = NutraTheme.typography.body,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(end = 12.dp),
                            )
                            Text(
                                text = "${portion.totalGrams.roundToInt()} ${portion.food.displayUnitSuffix}",
                                style = NutraTheme.typography.caption,
                                color = colors.textSecondary,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        NutraButton(
            text = "Önerileri gör",
            onClick = { onOpenStudio(currentMealType) },
            style = NutraButtonStyle.Secondary,
            size = NutraButtonSize.Small,
            trailingIcon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            modifier = Modifier.fillMaxWidth(),
        )
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
    val listState = rememberLazyListState()
    val headerCollapseFraction = rememberHeaderCollapseFraction(listState)
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val defaultTopPadding = statusBarTop + NutraStickySubScreenHeaderTopReservedPadding + 56.dp
    var measuredHeaderHeight by remember { mutableStateOf(defaultTopPadding) }
    val mealChipsListState = rememberLazyListState()
    val hasUndoDislike = state.lastDislikedFood != null
    val snackbarTimeoutMs = rememberAccessibleSnackbarTimeout(
        baseTimeoutMillis = if (hasUndoDislike) 4500L else 3000L,
        hasAction = hasUndoDislike,
    )

    LaunchedEffect(state.selectedMealType) {
        val targetIdx = MealType.entries.indexOf(state.selectedMealType).coerceAtLeast(0)
        mealChipsListState.animateScrollToItem(targetIdx)
    }

    LaunchedEffect(state.feedbackEventId, state.feedbackMessage) {
        if (state.feedbackMessage != null) {
            delay(snackbarTimeoutMs)
            viewModel.clearFeedback()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = NutraContentMaxWidth)
                .fillMaxSize(),
        ) {
            RecommendationsTabContent(
                state = state,
                viewModel = viewModel,
                listState = listState,
                topPadding = maxOf(defaultTopPadding, measuredHeaderHeight + 6.dp),
                onLoggedMeal = onLoggedMeal,
            )
        }

        NutraStickyScreenHeader(
            title = "Nutra Intelligence",
            eyebrow = "Kişiselleştirilmiş öğün ve porsiyon",
            collapseFractionState = headerCollapseFraction,
            onNavigateBack = onNavigateBack,
            onHeaderHeightMeasured = { measuredHeaderHeight = it },
            modifier = Modifier.align(Alignment.TopCenter),
            bottomContent = {
                LazyRow(
                    state = mealChipsListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(MealType.entries, key = { it.name }) { meal ->
                        NutraChip(
                            text = meal.title,
                            selected = meal == state.selectedMealType,
                            onClick = { viewModel.selectMealType(meal) },
                            role = Role.Tab,
                        )
                    }
                }
            },
        )

        NutraSnackbarHost(
            visible = state.feedbackMessage != null,
            message = state.feedbackMessage.orEmpty(),
            actionLabel = if (hasUndoDislike) "Geri al" else null,
            onAction = if (hasUndoDislike) viewModel::undoLastDislike else null,
            tone = if (hasUndoDislike) NutraSnackbarTone.Neutral else NutraSnackbarTone.Success,
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
    listState: LazyListState,
    topPadding: Dp,
    onLoggedMeal: (MealType, String) -> Unit,
) {
    val colors = NutraTheme.colors
    val report = state.report

    if (report == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Öneriler hazırlanıyor...",
                style = NutraTheme.typography.body,
                color = colors.textSecondary,
            )
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = NutraTheme.spacing.screen,
            end = NutraTheme.spacing.screen,
            top = topPadding,
            bottom = 36.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Budget summary + contextual guidance notes (cold start / micronutrient signal)
        item(key = "budget_overview") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(NutraTheme.shapes.md)
                    .background(colors.surface)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = report.budget.budgetSummaryText,
                    style = NutraTheme.typography.labelSmall,
                    color = colors.textPrimary,
                )
                report.prioritizedMicronutrientNote?.let { microNote ->
                    Text(
                        text = microNote,
                        style = NutraTheme.typography.caption,
                        color = colors.onAccentSoft,
                    )
                }
                report.coldStartNote?.let { coldNote ->
                    Text(
                        text = coldNote,
                        style = NutraTheme.typography.caption,
                        color = colors.textSecondary,
                    )
                }
            }
        }

        if (report.recommendations.isEmpty()) {
            item(key = "empty_recs") {
                NutraEmptyState(
                    icon = NutraIcons.Restaurant,
                    title = "Uygun öneri oluşturulamadı",
                    message = "Alerjen veya beslenme kısıtların tüm adayları elemiş olabilir. Profil → Beslenme tercihlerinden filtrelerini kontrol edebilirsin.",
                )
            }
        } else {
            items(report.recommendations, key = { it.id }) { effective ->
                RecommendationDetailCard(
                    recommendation = effective,
                    hasManualEdits = viewModel.hasManualEdits(effective),
                    onAdjustPortion = { portion, delta ->
                        viewModel.adjustRecommendationItemGrams(effective.id, portion, delta)
                    },
                    onResetPortions = { viewModel.resetRecommendationPortions(effective.id) },
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
    onDislikeFood: (RecommendedFoodPortion) -> Unit,
    onAcceptAndLog: () -> Unit,
) {
    val colors = NutraTheme.colors
    var isEditingPortions by remember(recommendation.id) { mutableStateOf(false) }
    var isDetailsExpanded by remember(recommendation.id) { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (isDetailsExpanded) 180f else 0f,
        animationSpec = tween(180),
        label = "detailsChevronRotation",
    )

    NutraCard(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 1. Clean header: recommendation title + style label on left, Calories + Protein badge on right
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = recommendation.title.ifBlank { recommendation.styleTag.label },
                    style = NutraTheme.typography.titleSmall,
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (recommendation.title.isNotBlank() && recommendation.title != recommendation.styleTag.label) {
                    Text(
                        text = recommendation.styleTag.label,
                        style = NutraTheme.typography.caption,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            NutraBadge(
                text = "${NutraFormatters.formatInt(recommendation.totalCalories)} kcal · ${NutraFormatters.formatInt(recommendation.totalProtein.roundToInt())} g P",
                color = colors.onAccentSoft,
                container = colors.accentSoft,
            )
        }

        NutraDivider()

        // 2. Scannable food items list (with optional inline portion editing)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            recommendation.items.forEachIndexed { idx, portion ->
                if (idx > 0 && isEditingPortions) NutraDivider()
                RecommendedPortionRow(
                    portion = portion,
                    isEditing = isEditingPortions,
                    onAdjustGrams = { delta -> onAdjustPortion(portion, delta) },
                    onDislike = { onDislikeFood(portion) },
                )
            }
        }

        // 3. Collapsible "Detaylar" trigger strip (FlowRow prevents 0px macro-label collisions on narrow screens)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(NutraTheme.shapes.sm)
                .background(colors.surfaceMuted)
                .clickable(role = Role.Button) { isDetailsExpanded = !isDetailsExpanded }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FlowRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MacroInlineTag("K", "${NutraFormatters.formatMacroGrams(recommendation.totalCarbs)}g", colors.carbs)
                MacroInlineTag("Y", "${NutraFormatters.formatMacroGrams(recommendation.totalFat)}g", colors.fat)
                MacroInlineTag("Uyum", "%${recommendation.fitScore}", colors.accent)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (isDetailsExpanded) "Gizle" else "Detaylar",
                    style = NutraTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    softWrap = false,
                )
                Icon(
                    imageVector = NutraIcons.ExpandMore,
                    contentDescription = if (isDetailsExpanded) "Detayları gizle" else "Detayları göster",
                    tint = colors.textSecondary,
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer { rotationZ = chevronRotation },
                )
            }
        }

        // 4. Expanded details: Daily goal impact, reasons, and micronutrients
        AnimatedVisibility(
            visible = isDetailsExpanded,
            enter = expandVertically(tween(200)) + fadeIn(tween(160)),
            exit = shrinkVertically(tween(160)) + fadeOut(tween(120)),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Daily goal impact preview
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

                // Reasons ("Neden bu öneri?")
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

                // Micronutrients
                if (recommendation.micronutrientHighlights.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                }
            }
        }

        // 5. Action row
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

        AnimatedVisibility(visible = isEditingPortions && hasManualEdits) {
            NutraButton(
                text = "Otomatik porsiyona dön",
                onClick = onResetPortions,
                style = NutraButtonStyle.Secondary,
                size = NutraButtonSize.Small,
                modifier = Modifier.fillMaxWidth(),
            )
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
    val cleanLabel = portion.unitLabel.trim()
    val quantityText = when {
        ServingUnitLabels.isRawUnitLabel(cleanLabel) -> "${portion.totalGrams.roundToInt()} $suffix"
        kotlin.math.abs(portion.multiplier - 1f) < 0.01f && cleanLabel.startsWith("1 ") ->
            "$cleanLabel (${portion.totalGrams.roundToInt()} $suffix)"
        cleanLabel.startsWith("1 ") ->
            "${NutraFormatters.formatMultiplier(portion.multiplier)} ${cleanLabel.removePrefix("1 ")} (${portion.totalGrams.roundToInt()} $suffix)"
        else ->
            "${NutraFormatters.formatMultiplier(portion.multiplier)}x $cleanLabel (${portion.totalGrams.roundToInt()} $suffix)"
    }
    val step = if (portion.maxGrams <= 60f) 5f else if (portion.maxGrams <= 150f) 10f else 20f

    if (!isEditing) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = portion.food.name,
                style = NutraTheme.typography.body,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 12.dp),
            )
            Text(
                text = quantityText,
                style = NutraTheme.typography.caption,
                color = colors.textSecondary,
                maxLines = 1,
            )
        }
    } else {
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
                        contentDescription = "${portion.food.name} besinini bir daha önerme",
                        onClick = onDislike,
                        size = 44.dp,
                        iconSize = 18.dp,
                        containerColor = colors.dangerSoft,
                        contentColor = colors.dangerText,
                    )
                }
            }
        }
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
