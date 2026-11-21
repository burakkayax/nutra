package com.nutra.app.feature.intelligence

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutra.app.core.datastore.UserPreferencesDataStore
import com.nutra.app.core.designsystem.NutraFormatters
import com.nutra.app.domain.intelligence.HabitMaturity
import com.nutra.app.domain.intelligence.IntelligenceReport
import com.nutra.app.domain.intelligence.MealRecommendation
import com.nutra.app.domain.intelligence.MealRecommendationEngine
import com.nutra.app.domain.intelligence.MicronutrientType
import com.nutra.app.domain.intelligence.NutrientSignalStatus
import com.nutra.app.domain.intelligence.RecommendedFoodPortion
import com.nutra.app.domain.model.ComboFoodEntry
import com.nutra.app.domain.model.DailyNutritionSummary
import com.nutra.app.domain.model.FoodFeedbackRecord
import com.nutra.app.domain.model.MealLogItem
import com.nutra.app.domain.model.MealType
import com.nutra.app.domain.model.UserProfile
import com.nutra.app.domain.repository.NutritionRepository
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

enum class IntelligenceStudioTab(val title: String) {
    RECOMMENDATIONS("Öneriler"),
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
    val feedbackMessage: String? = null,
    val feedbackEventId: Long = 0L,
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
) : ViewModel() {

    private val selectedDateFlow = MutableStateFlow(LocalDate.now())
    private val studioControlFlow = MutableStateFlow(StudioControlState())
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

    // Shared catalog & feedback flows eliminate duplicate Room queries
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

    // Single shared computation of recommendations
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
     * Compact, isolated slice for DashboardScreen so editing studio portions never recomposes
     * NutraAppRoot or DashboardScreen.
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
    ) { reportSnap, control ->
        IntelligenceUiState(
            isLoading = false,
            selectedDateIso = reportSnap.dateIso,
            selectedMealType = reportSnap.mealType,
            activeTab = control.activeTab,
            report = reportSnap.report,
            portionOverrides = control.portionOverrides,
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

    fun clearFeedback() {
        studioControlFlow.update { it.copy(feedbackMessage = null) }
    }

    private fun emitFeedback(message: String) {
        val id = feedbackSeq.incrementAndGet()
        studioControlFlow.update { it.copy(feedbackMessage = message, feedbackEventId = id) }
    }
}
