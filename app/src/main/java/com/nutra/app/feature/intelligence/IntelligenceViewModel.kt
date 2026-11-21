package com.nutra.app.feature.intelligence

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutra.app.core.datastore.UserPreferencesDataStore
import com.nutra.app.core.designsystem.NutraFormatters
import com.nutra.app.domain.intelligence.CustomPortionOptimizationResult
import com.nutra.app.domain.intelligence.FoodCulinaryClassifier
import com.nutra.app.domain.intelligence.IntelligenceReport
import com.nutra.app.domain.intelligence.MealRecommendation
import com.nutra.app.domain.intelligence.MealRecommendationEngine
import com.nutra.app.domain.intelligence.PortionOptimizer
import com.nutra.app.domain.intelligence.RecommendedFoodPortion
import com.nutra.app.domain.model.ComboFoodEntry
import com.nutra.app.domain.model.FoodItem
import com.nutra.app.domain.model.MealType
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
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

    private val dailySummaryFlow = selectedDateFlow.flatMapLatest { date ->
        nutritionRepository.observeDailySummary(date.toString())
    }

    private val historicalLogsFlow = selectedDateFlow.flatMapLatest { date ->
        nutritionRepository.observeMealLogsSince(date.minusDays(60).toString())
    }

    private val catalogFoodsFlow = nutritionRepository.observeAllFoods()
    private val feedbackFlow = nutritionRepository.observeFoodFeedback()

    private val engineInputFlow = combine(
        preferencesDataStore.userProfileFlow,
        dailySummaryFlow,
        catalogFoodsFlow,
        historicalLogsFlow,
        feedbackFlow,
    ) { profile, summary, catalog, logs, feedback ->
        EngineInputs(
            profile = profile,
            summary = summary,
            catalog = catalog,
            logs = logs,
            feedback = feedback,
        )
    }

    private data class EngineInputs(
        val profile: com.nutra.app.domain.model.UserProfile,
        val summary: com.nutra.app.domain.model.DailyNutritionSummary,
        val catalog: List<FoodItem>,
        val logs: List<com.nutra.app.domain.model.MealLogItem>,
        val feedback: List<com.nutra.app.domain.model.FoodFeedbackRecord>,
    )

    private val reportFlow = combine(
        selectedDateFlow,
        engineInputFlow,
        studioControlFlow.map { it.manualMealType to it.variationSeed }.distinctUntilChanged(),
    ) { selectedDate, inputs, (manualMeal, variationSeed) ->
        val mealType = manualMeal ?: recommendationEngine.inferActiveMealType(
            dailySummary = inputs.summary,
            nowTime = LocalTime.now(),
        )
        val report = recommendationEngine.generateRecommendations(
            profile = inputs.profile,
            dailySummary = inputs.summary,
            mealType = mealType,
            catalogFoods = inputs.catalog,
            historicalLogs = inputs.logs,
            feedbackRecords = inputs.feedback,
            today = selectedDate,
            variationSeed = variationSeed,
            maxResults = 4,
        )
        Triple(selectedDate.toString(), mealType, report)
    }.flowOn(Dispatchers.Default)

    private val builderComputedFlow = combine(
        catalogFoodsFlow,
        reportFlow,
        builderStateFlow,
    ) { catalog, (_, _, report), builder ->
        val matches = if (builder.query.isBlank()) {
            // Surface practical staple Turkish foods for quick picking
            catalog.filter { !it.isCustom && it.caloriesPer100g > 10 }.take(18)
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

    val uiState: StateFlow<IntelligenceUiState> = combine(
        reportFlow,
        studioControlFlow,
        builderComputedFlow,
    ) { (dateIso, mealType, report), control, (matches, builder, optimized) ->
        IntelligenceUiState(
            isLoading = false,
            selectedDateIso = dateIso,
            selectedMealType = mealType,
            activeTab = control.activeTab,
            report = report,
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

    fun resolveEffectiveRecommendation(rec: MealRecommendation): MealRecommendation {
        val overrides = studioControlFlow.value.portionOverrides[rec.id]
        if (overrides.isNullOrEmpty()) return rec
        val adjustedItems = rec.items.map { item ->
            val overrideG = overrides[item.food.id] ?: return@map item
            val cf = classifier.classifySingle(item.food)
            portionOptimizer.buildPortionWithGrams(cf, overrideG)
        }
        val cal = adjustedItems.sumOf { it.calories }
        val p = (adjustedItems.sumOf { it.proteinGrams.toDouble() } * 10.0).roundToInt() / 10f
        val c = (adjustedItems.sumOf { it.carbsGrams.toDouble() } * 10.0).roundToInt() / 10f
        val f = (adjustedItems.sumOf { it.fatGrams.toDouble() } * 10.0).roundToInt() / 10f
        val impact = rec.goalImpact.copy(
            caloriesAfter = rec.goalImpact.caloriesBefore + cal,
            proteinAfter = ((rec.goalImpact.proteinBefore + p) * 10f).roundToInt() / 10f,
            carbsAfter = ((rec.goalImpact.carbsBefore + c) * 10f).roundToInt() / 10f,
            fatAfter = ((rec.goalImpact.fatBefore + f) * 10f).roundToInt() / 10f,
        )
        return rec.copy(
            items = adjustedItems,
            totalCalories = cal,
            totalProtein = p,
            totalCarbs = c,
            totalFat = f,
            goalImpact = impact,
        )
    }

    fun logRecommendation(
        recommendation: MealRecommendation,
        onLogged: (String) -> Unit = {},
    ) {
        if (!isLoggingGuard.compareAndSet(false, true)) return
        val effective = resolveEffectiveRecommendation(recommendation)
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
                onLogged(msg)
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

    fun logBuilderCombo(onLogged: (String) -> Unit = {}) {
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
                val msg = "Dengelenmiş ${mealType.title.lowercase()} eklendi (${NutraFormatters.formatInt(opt.totalCalories)} kcal)"
                emitFeedback(msg)
                onLogged(msg)
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
            portionOptimizer.buildPortionWithGrams(classifier.classifySingle(item.food), g)
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
