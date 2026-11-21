package com.nutra.app.feature.intelligence

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutra.app.core.datastore.UserPreferencesDataStore
import com.nutra.app.core.designsystem.NutraFormatters
import com.nutra.app.domain.intelligence.CustomPortionOptimizationResult
import com.nutra.app.domain.intelligence.FoodCulinaryClassifier
import com.nutra.app.domain.intelligence.HabitMaturity
import com.nutra.app.domain.intelligence.IntelligenceReport
import com.nutra.app.domain.intelligence.MealRecommendation
import com.nutra.app.domain.intelligence.MealRecommendationEngine
import com.nutra.app.domain.intelligence.MicronutrientType
import com.nutra.app.domain.intelligence.NutrientSignalStatus
import com.nutra.app.domain.intelligence.PortionOptimizer
import com.nutra.app.domain.intelligence.RecommendedFoodPortion
import com.nutra.app.domain.model.ComboFoodEntry
import com.nutra.app.domain.model.DailyNutritionSummary
import com.nutra.app.domain.model.FoodFeedbackRecord
import com.nutra.app.domain.model.FoodItem
import com.nutra.app.domain.model.MealLogItem
import com.nutra.app.domain.model.MealType
import com.nutra.app.domain.model.UserProfile
import com.nutra.app.domain.repository.NutritionRepository
import com.nutra.app.domain.usecase.TurkishTextNormalizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlin.math.roundToInt

enum class IntelligenceStudioTab(val title: String) {
    RECOMMENDATIONS("Öneriler"),
    PORTION_BALANCER("Öğün Dengele"),
}

@Immutable
data class DashboardIntelligenceCardState(
    val mealType: MealType,
    val targetCalories: Int,
    val targetProteinGrams: Float,
    val maturity: HabitMaturity,
    val topRecommendation: MealRecommendation?,
    val fallbackNote: String,
)

@Immutable
data class IntelligenceUiState(
    val isLoading: Boolean = true,
    val selectedDateIso: String = LocalDate.now().toString(),
    val selectedMealType: MealType = MealType.DINNER,
    val activeTab: IntelligenceStudioTab = IntelligenceStudioTab.RECOMMENDATIONS,
    val report: IntelligenceReport? = null,
    val portionOverrides: Map<String, Map<Long, Float>> = emptyMap(),
    val builderQuery: String = "",
    val builderCatalogMatches: List<FoodItem> = emptyList(),
    val builderSelectedFoods: List<FoodItem> = emptyList(),
    val builderOptimized: CustomPortionOptimizationResult? = null,
    val builderGramOverrides: Map<Long, Float> = emptyMap(),
    val feedbackMessage: String? = null,
    val feedbackEventId: Long = 0L,
)

private data class BuilderInternalState(
    val query: String = "",
    val selectedFoods: List<FoodItem> = emptyList(),
    val gramOverrides: Map<Long, Float> = emptyMap(),
)

private data class StudioControlState(
    val activeTab: IntelligenceStudioTab = IntelligenceStudioTab.RECOMMENDATIONS,
    val manualMealType: MealType? = null,
    val variationSeed: Int = 0,
    val portionOverrides: Map<String, Map<Long, Float>> = emptyMap(),
    val feedbackMessage: String? = null,
    val feedbackEventId: Long = 0L,
)

private data class DateScopedData(
    val selectedDate: LocalDate,
    val summary: DailyNutritionSummary,
    val logs: List<MealLogItem>,
)

private data class SharedReportSnapshot(
    val dateIso: String,
    val mealType: MealType,
    val report: IntelligenceReport,
    val profile: UserProfile,
    val feedback: List<FoodFeedbackRecord>,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class IntelligenceViewModel @Inject constructor(
    private val nutritionRepository: NutritionRepository,
    private val preferencesDataStore: UserPreferencesDataStore,
    private val recommendationEngine: MealRecommendationEngine,
    private val classifier: FoodCulinaryClassifier,
    private val portionOptimizer: PortionOptimizer,
) : ViewModel() {

    private val selectedDateFlow = MutableStateFlow(LocalDate.now())
    private val studioControlFlow = MutableStateFlow(StudioControlState())
    private val builderStateFlow = MutableStateFlow(BuilderInternalState())
    private val feedbackSeq = AtomicLong(0L)
    private val isLoggingGuard = AtomicBoolean(false)

    // Atomic date-scoped pair so date changes never transiently mix yesterday's summary with today's logs
    private val dateScopedDataFlow = selectedDateFlow.flatMapLatest { date ->
        combine(
            nutritionRepository.observeDailySummary(date.toString()),
            nutritionRepository.observeMealLogsSince(date.minusDays(60).toString()),
        ) { summary, logs ->
            DateScopedData(
                selectedDate = date,
                summary = summary,
                logs = logs,
            )
        }
    }

    // Shared catalog & feedback flows eliminate duplicate Room queries across report + builder pipelines
    private val sharedCatalogFlow = nutritionRepository.observeAllFoods()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(30_000),
            initialValue = null,
        )

    private val sharedFeedbackFlow = nutritionRepository.observeFoodFeedback()
        .shareIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(30_000),
            replay = 1,
        )

    // Single shared computation of recommendations (previously executed twice per emission!)
    private val sharedReportFlow = combine(
        preferencesDataStore.userProfileFlow,
        dateScopedDataFlow,
        sharedCatalogFlow.filterNotNull(),
        sharedFeedbackFlow,
        studioControlFlow.map { it.manualMealType to it.variationSeed }.distinctUntilChanged(),
    ) { profile, dateData, catalog, feedback, (manualMeal, variationSeed) ->
        val mealType = manualMeal ?: recommendationEngine.inferActiveMealType(
            dailySummary = dateData.summary,
            nowTime = LocalTime.now(),
        )
        val report = recommendationEngine.generateRecommendations(
            profile = profile,
            dailySummary = dateData.summary,
            mealType = mealType,
            catalogFoods = catalog,
            historicalLogs = dateData.logs,
            feedbackRecords = feedback,
            today = dateData.selectedDate,
            variationSeed = variationSeed,
            maxResults = 4,
        )
        SharedReportSnapshot(
            dateIso = dateData.selectedDate.toString(),
            mealType = mealType,
            report = report,
            profile = profile,
            feedback = feedback,
        )
    }.flowOn(Dispatchers.Default)
        .shareIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(30_000),
            replay = 1,
        )

    /**
     * Compact, isolated slice for DashboardScreen so typing in the Portion Balancer or editing
     * studio tabs never recomposes NutraAppRoot or DashboardScreen.
     */
    val dashboardCardState: StateFlow<DashboardIntelligenceCardState?> = sharedReportFlow
        .map { snap ->
            val report = snap.report
            DashboardIntelligenceCardState(
                mealType = report.mealType,
                targetCalories = report.budget.targetCalories,
                targetProteinGrams = report.budget.targetProteinGrams,
                maturity = report.eatingProfile.maturity,
                topRecommendation = report.recommendations.firstOrNull(),
                fallbackNote = report.coldStartNote ?: report.budget.budgetSummaryText,
            )
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(30_000),
            initialValue = null,
        )

    private val builderComputedFlow = combine(
        sharedCatalogFlow.filterNotNull(),
        sharedReportFlow,
        builderStateFlow,
    ) { catalog, reportSnap, builder ->
        val report = reportSnap.report
        val matches = if (builder.query.isBlank()) {
            // Surface practical, preference-safe ready-to-eat Turkish foods for quick picking
            val classified = classifier.classifyCatalog(catalog)
            val feedbackByKey = reportSnap.feedback.associateBy { it.foodKey }
            classified
                .asSequence()
                .filter { cf ->
                    !cf.food.isCustom &&
                        cf.food.caloriesPer100g > 10 &&
                        classifier.isAllowedByPreferences(cf, reportSnap.profile.dietaryProfile, feedbackByKey)
                }
                .map { it.food }
                .take(18)
                .toList()
        } else {
            val normQ = TurkishTextNormalizer.normalize(builder.query)
            val tokens = TurkishTextNormalizer.tokenizeNormalized(normQ)
            catalog.filter { food ->
                val text = food.normalizedSearchText.ifBlank {
                    TurkishTextNormalizer.normalize("${food.name} ${food.subtitle}")
                }
                TurkishTextNormalizer.matchesNormalizedTokens(text, tokens)
            }.take(24)
        }

        val optimized = if (builder.selectedFoods.isNotEmpty()) {
            val baseOpt = recommendationEngine.optimizeCustomCombo(
                selectedFoods = builder.selectedFoods,
                budget = report.budget,
                usualGramsByFoodId = report.eatingProfile.usualGramsByFoodId,
            )
            if (builder.gramOverrides.isEmpty()) {
                baseOpt
            } else {
                applyBuilderGramOverrides(baseOpt, builder.gramOverrides, report.budget)
            }
        } else {
            null
        }
        Triple(matches, builder, optimized)
    }.flowOn(Dispatchers.Default)

    private val resolvedReportFlow = combine(
        sharedReportFlow,
        studioControlFlow.map { it.portionOverrides }.distinctUntilChanged(),
    ) { reportSnap, overrides ->
        if (overrides.isEmpty()) {
            reportSnap
        } else {
            val report = reportSnap.report
            val lowMicros = report.eatingProfile.micronutrientSignals
                .filter { it.status == NutrientSignalStatus.LOW_RECORDED }
                .map { it.type }
                .toSet()
            val highSodium = report.eatingProfile.micronutrientSignals.any {
                it.type == MicronutrientType.SODIUM && it.status == NutrientSignalStatus.ELEVATED_RECORDED
            }
            val resolvedRecs = report.recommendations.map { rec ->
                val recOverrides = overrides[rec.id]
                if (recOverrides.isNullOrEmpty()) {
                    rec
                } else {
                    recommendationEngine.recomputeRecommendationWithPortions(
                        recommendation = rec,
                        gramOverridesByFoodId = recOverrides,
                        budget = report.budget,
                        lowMicronutrients = lowMicros,
                        highSodiumActive = highSodium,
                    )
                }
            }
            reportSnap.copy(report = report.copy(recommendations = resolvedRecs))
        }
    }.flowOn(Dispatchers.Default)

    val uiState: StateFlow<IntelligenceUiState> = combine(
        resolvedReportFlow,
        studioControlFlow,
        builderComputedFlow,
    ) { reportSnap, control, (matches, builder, optimized) ->
        IntelligenceUiState(
            isLoading = false,
            selectedDateIso = reportSnap.dateIso,
            selectedMealType = reportSnap.mealType,
            activeTab = control.activeTab,
            report = reportSnap.report,
            portionOverrides = control.portionOverrides,
            builderQuery = builder.query,
            builderCatalogMatches = matches,
            builderSelectedFoods = builder.selectedFoods,
            builderOptimized = optimized,
            builderGramOverrides = builder.gramOverrides,
            feedbackMessage = control.feedbackMessage,
            feedbackEventId = control.feedbackEventId,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(30_000),
        initialValue = IntelligenceUiState(),
    )

    fun syncDate(date: LocalDate) {
        if (selectedDateFlow.value != date) {
            selectedDateFlow.value = date
        }
    }

    fun openForMeal(mealType: MealType? = null, initialTab: IntelligenceStudioTab = IntelligenceStudioTab.RECOMMENDATIONS) {
        studioControlFlow.update {
            it.copy(
                activeTab = initialTab,
                manualMealType = mealType ?: it.manualMealType,
            )
        }
    }

    fun selectMealType(mealType: MealType) {
        studioControlFlow.update {
            it.copy(
                manualMealType = mealType,
                portionOverrides = emptyMap(),
            )
        }
        builderStateFlow.update { it.copy(gramOverrides = emptyMap()) }
    }

    fun selectTab(tab: IntelligenceStudioTab) {
        studioControlFlow.update { it.copy(activeTab = tab) }
    }

    fun refreshAlternatives() {
        studioControlFlow.update {
            it.copy(
                variationSeed = it.variationSeed + 1,
                portionOverrides = emptyMap(),
            )
        }
    }

    fun adjustRecommendationItemGrams(recId: String, portion: RecommendedFoodPortion, deltaGrams: Float) {
        studioControlFlow.update { state ->
            val currentMap = state.portionOverrides[recId].orEmpty().toMutableMap()
            val currentGrams = currentMap[portion.food.id] ?: portion.totalGrams
            val nextGrams = (currentGrams + deltaGrams).coerceIn(portion.minGrams, portion.maxGrams)
            currentMap[portion.food.id] = nextGrams
            val nextOverrides = state.portionOverrides.toMutableMap()
            nextOverrides[recId] = currentMap
            state.copy(portionOverrides = nextOverrides)
        }
    }

    fun resetRecommendationPortions(recId: String) {
        studioControlFlow.update { state ->
            val next = state.portionOverrides.toMutableMap()
            next.remove(recId)
            state.copy(portionOverrides = next)
        }
    }

    fun logRecommendation(
        recommendation: MealRecommendation,
        onLogged: (MealType, String) -> Unit = { _, _ -> },
    ) {
        if (!isLoggingGuard.compareAndSet(false, true)) return
        val currentOverrides = studioControlFlow.value.portionOverrides[recommendation.id]
        val budget = uiState.value.report?.budget
        val effective = if (!currentOverrides.isNullOrEmpty() && budget != null) {
            recommendationEngine.recomputeRecommendationWithPortions(
                recommendation = recommendation,
                gramOverridesByFoodId = currentOverrides,
                budget = budget,
            )
        } else {
            recommendation
        }
        val dateIso = selectedDateFlow.value.toString()
        viewModelScope.launch {
            try {
                val entries: List<ComboFoodEntry> = effective.items.map { it.toComboEntry() }
                nutritionRepository.logComboItemsToMeal(
                    dateIso = dateIso,
                    mealType = effective.mealType,
                    entries = entries,
                )
                nutritionRepository.recordRecommendationAccepted(
                    effective.items.map { Triple(it.feedbackKey, it.food.id.takeIf { id -> id > 0L }, it.food.name) },
                )
                val msg = "${effective.mealType.title} eklendi (${NutraFormatters.formatInt(effective.totalCalories)} kcal)"
                emitFeedback(msg)
                onLogged(effective.mealType, msg)
            } finally {
                isLoggingGuard.set(false)
            }
        }
    }

    fun dislikeFood(portion: RecommendedFoodPortion) {
        viewModelScope.launch {
            nutritionRepository.setFoodDisliked(
                foodKey = portion.feedbackKey,
                foodId = portion.food.id.takeIf { it > 0L },
                foodName = portion.food.name,
                disliked = true,
            )
            emitFeedback("${portion.food.name} önerilerden gizlendi")
        }
    }

    // --- Custom Portion Balancer ("Öğün Dengele") ---
    fun updateBuilderQuery(query: String) {
        builderStateFlow.update { it.copy(query = query) }
    }

    fun toggleBuilderFood(food: FoodItem) {
        builderStateFlow.update { state ->
            val exists = state.selectedFoods.any { it.id == food.id }
            val nextList = if (exists) {
                state.selectedFoods.filterNot { it.id == food.id }
            } else if (state.selectedFoods.size < 4) {
                state.selectedFoods + food
            } else {
                state.selectedFoods
            }
            state.copy(
                selectedFoods = nextList,
                gramOverrides = emptyMap(),
                query = if (!exists) "" else state.query,
            )
        }
    }

    fun loadRecommendationIntoBalancer(recommendation: MealRecommendation) {
        val foods = recommendation.items.map { it.food }.take(4)
        builderStateFlow.update {
            it.copy(
                selectedFoods = foods,
                gramOverrides = emptyMap(),
                query = "",
            )
        }
        studioControlFlow.update { it.copy(activeTab = IntelligenceStudioTab.PORTION_BALANCER) }
    }

    fun adjustBuilderItemGrams(portion: RecommendedFoodPortion, deltaGrams: Float) {
        builderStateFlow.update { state ->
            val next = state.gramOverrides.toMutableMap()
            val current = next[portion.food.id] ?: portion.totalGrams
            next[portion.food.id] = (current + deltaGrams).coerceIn(portion.minGrams, portion.maxGrams)
            state.copy(gramOverrides = next)
        }
    }

    fun reoptimizeBuilderPortions() {
        builderStateFlow.update { it.copy(gramOverrides = emptyMap()) }
        emitFeedback("Porsiyonlar hedefine göre yeniden dengelendi")
    }

    fun clearBuilderSelection() {
        builderStateFlow.update { BuilderInternalState() }
    }

    fun logBuilderCombo(onLogged: (MealType, String) -> Unit = { _, _ -> }) {
        val opt = uiState.value.builderOptimized ?: return
        if (opt.items.isEmpty()) return
        if (!isLoggingGuard.compareAndSet(false, true)) return
        val mealType = uiState.value.selectedMealType
        val dateIso = selectedDateFlow.value.toString()
        viewModelScope.launch {
            try {
                nutritionRepository.logComboItemsToMeal(
                    dateIso = dateIso,
                    mealType = mealType,
                    entries = opt.items.map { it.toComboEntry() },
                )
                nutritionRepository.recordRecommendationAccepted(
                    opt.items.map { Triple(it.feedbackKey, it.food.id.takeIf { id -> id > 0L }, it.food.name) },
                )
                builderStateFlow.update { BuilderInternalState() }
                val msg = "Dengelenmiş ${NutraFormatters.lowercaseTr(mealType.title)} eklendi (${NutraFormatters.formatInt(opt.totalCalories)} kcal)"
                emitFeedback(msg)
                onLogged(mealType, msg)
            } finally {
                isLoggingGuard.set(false)
            }
        }
    }

    fun clearFeedback() {
        studioControlFlow.update { it.copy(feedbackMessage = null) }
    }

    private fun emitFeedback(message: String) {
        val id = feedbackSeq.incrementAndGet()
        studioControlFlow.update { it.copy(feedbackMessage = message, feedbackEventId = id) }
    }

    private fun applyBuilderGramOverrides(
        base: CustomPortionOptimizationResult,
        overrides: Map<Long, Float>,
        budget: com.nutra.app.domain.intelligence.MealBudget,
    ): CustomPortionOptimizationResult {
        val adjusted = base.items.map { item ->
            val g = overrides[item.food.id] ?: return@map item
            portionOptimizer.buildPortionWithGrams(
                classified = classifier.classifySingle(item.food),
                grams = g,
                preferHouseholdSnap = false,
            )
        }
        val cal = adjusted.sumOf { it.calories }
        val p = (adjusted.sumOf { it.proteinGrams.toDouble() } * 10.0).roundToInt() / 10f
        val c = (adjusted.sumOf { it.carbsGrams.toDouble() } * 10.0).roundToInt() / 10f
        val f = (adjusted.sumOf { it.fatGrams.toDouble() } * 10.0).roundToInt() / 10f
        val fit = portionOptimizer.computeFitScore(cal, p, c, f, budget)
        return base.copy(
            items = adjusted,
            totalCalories = cal,
            totalProtein = p,
            totalCarbs = c,
            totalFat = f,
            fitScore = fit,
        )
    }
}
