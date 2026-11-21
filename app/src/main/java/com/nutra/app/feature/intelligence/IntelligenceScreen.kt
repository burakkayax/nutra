package com.nutra.app.feature.intelligence

import androidx.activity.compose.BackHandler
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
import com.nutra.app.core.designsystem.NutraIconButton
import com.nutra.app.core.designsystem.NutraIcons
import com.nutra.app.core.designsystem.NutraLinearProgress
import com.nutra.app.core.designsystem.NutraSnackbarHost
import com.nutra.app.core.designsystem.NutraSnackbarTone
import com.nutra.app.core.designsystem.NutraStepperButton
import com.nutra.app.core.designsystem.NutraTheme
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
    onOpenStudio: () -> Unit,
    onQuickLogTopRecommendation: (MealRecommendation) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NutraTheme.colors
    val topRec = cardState.topRecommendation

    NutraCard(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "Nutra Intelligence",
                style = NutraTheme.typography.titleSmall,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Size özel öğün önerisi",
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
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = cardState.mealType.title,
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
        } else {
            Text(
                text = cardState.fallbackNote,
                style = NutraTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }

        NutraButton(
            text = "Önerileri gör",
            onClick = onOpenStudio,
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
            }

            // Meal selector chips
            val mealChipsListState = rememberLazyListState()
            LaunchedEffect(state.selectedMealType) {
                val targetIdx = MealType.entries.indexOf(state.selectedMealType).coerceAtLeast(0)
                mealChipsListState.animateScrollToItem(targetIdx)
            }
            LazyRow(
                state = mealChipsListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NutraTheme.spacing.screen),
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

            Spacer(Modifier.height(12.dp))

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                RecommendationsTabContent(
                    state = state,
                    viewModel = viewModel,
                    onLoggedMeal = onLoggedMeal,
                )
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
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Compact single-line target budget strip (without maturity badge)
        item(key = "budget_overview") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(NutraTheme.shapes.md)
                    .background(colors.surface)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${report.mealType.title} hedefi:",
                    style = NutraTheme.typography.caption,
                    color = colors.textSecondary,
                    maxLines = 1,
                )
                Text(
                    text = "~${NutraFormatters.formatInt(report.budget.targetCalories)} kcal · ${NutraFormatters.formatInt(report.budget.targetProteinGrams.roundToInt())} g protein",
                    style = NutraTheme.typography.labelSmall,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
                    hasManualEdits = !state.portionOverrides[effective.id].isNullOrEmpty(),
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
        // 1. Clean single-row header: Style title on left, Calories + Protein badge on right
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = recommendation.styleTag.label,
                style = NutraTheme.typography.titleSmall,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
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

        // 3. Collapsible "Detaylar" trigger strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(NutraTheme.shapes.sm)
                .background(colors.surfaceMuted)
                .clickable { isDetailsExpanded = !isDetailsExpanded }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
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
    val quantityText = if (ServingUnitLabels.isRawUnitLabel(portion.unitLabel)) {
        "${portion.totalGrams.roundToInt()} $suffix"
    } else {
        "${NutraFormatters.formatMultiplier(portion.multiplier)}x ${portion.unitLabel} (${portion.totalGrams.roundToInt()} $suffix)"
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
