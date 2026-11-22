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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

enum class IntelligenceStudioTab(val title: String) {
    RECOMMENDATIONS("Öneriler"),
}

@Immutable
data class DashboardMealRecommendationPage(
    val mealType: MealType,
    val targetCalories: Int,
    val targetProteinGrams: Float,
    val topRecommendation: MealRecommendation,
)

@Immutable
data class DashboardIntelligenceCardState(
    val defaultMealType: MealType,
    val maturity: HabitMaturity,
    val mealPages: List<DashboardMealRecommendationPage>,
)

@Immutable
data class IntelligenceUiState(
    val isLoading: Boolean = true,
    val selectedDateIso: String = LocalDate.now().toString(),
    val selectedMealType: MealType = MealType.BREAKFAST,
    val activeTab: IntelligenceStudioTab = IntelligenceStudioTab.RECOMMENDATIONS,
    val report: IntelligenceReport? = null,
    val portionOverrides: Map<String, Map<Long, Float>> = emptyMap(),
    val lastDislikedFood: RecommendedFoodPortion? = null,
    val feedbackMessage: String? = null,
    val feedbackEventId: Long = 0L,
)

private data class StudioControlState(
    val activeTab: IntelligenceStudioTab = IntelligenceStudioTab.RECOMMENDATIONS,
    val manualMealType: MealType? = null,
    val variationSeed: Int = 0,
    val portionOverrides: Map<String, Map<Long, Float>> = emptyMap(),
    val lastDislikedFood: RecommendedFoodPortion? = null,
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
    val dashboardCard: DashboardIntelligenceCardState?,
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

    // Atomic date-scoped pair projected to exclude waterMl so water taps NEVER trigger recommendation recomputation!
    private val dateScopedDataFlow = selectedDateFlow.flatMapLatest { date ->
        val dateIso = date.toString()
        val summaryWithoutWaterFlow = nutritionRepository.observeDailySummary(dateIso)
            .map { s -> s.copy(waterMl = 0, goals = s.goals.copy(waterMl = 2500)) }
            .distinctUntilChanged()
        combine(
            summaryWithoutWaterFlow,
            nutritionRepository.observeMealLogsInRange(date.minusDays(60).toString(), dateIso),
        ) { summary, logs ->
            DateScopedData(
                selectedDate = date,
                summary = summary,
                logs = logs,
            )
        }
    }

    // Project only recommendation-relevant profile fields (excludes water, theme, dynamic color, water reminder, metabolism flags)
    private val recommendationProfileFlow = preferencesDataStore.userProfileFlow
        .map { p ->
            UserProfile(
                sex = p.sex,
                age = p.age,
                heightCm = p.heightCm,
                currentWeightKg = p.currentWeightKg,
                targetWeightKg = p.targetWeightKg,
                activityLevel = p.activityLevel,
                goalType = p.goalType,
                goals = p.goals.copy(waterMl = 2500),
                onboardingCompleted = true,
                dietaryProfile = p.dietaryProfile,
            )
        }
        .distinctUntilChanged()

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
        recommendationProfileFlow,
        dateScopedDataFlow,
        sharedCatalogFlow.filterNotNull(),
        sharedFeedbackFlow,
        studioControlFlow.map { it.manualMealType to it.variationSeed }.distinctUntilChanged(),
    ) { profile, dateData, catalog, feedback, (manualMeal, variationSeed) ->
        val defaultMealType = recommendationEngine.inferDefaultMealType(
            dailySummary = dateData.summary,
            profile = profile,
        )
        val mealType = manualMeal ?: defaultMealType
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
        val dashboardCard = if (report.eatingProfile.maturity == HabitMaturity.NONE) {
            null
        } else {
            val pages = MealType.entries.mapNotNull { candidateMeal ->
                val mealReport = if (candidateMeal == mealType) {
                    report
                } else {
                    recommendationEngine.generateRecommendations(
                        profile = profile,
                        dailySummary = dateData.summary,
                        mealType = candidateMeal,
                        catalogFoods = catalog,
                        historicalLogs = dateData.logs,
                        feedbackRecords = feedback,
                        today = dateData.selectedDate,
                        variationSeed = variationSeed,
                        maxResults = 1,
                        precomputedEatingProfile = report.eatingProfile,
                    )
                }
                val topRec = mealReport.recommendations.firstOrNull() ?: return@mapNotNull null
                DashboardMealRecommendationPage(
                    mealType = candidateMeal,
                    targetCalories = mealReport.budget.targetCalories,
                    targetProteinGrams = mealReport.budget.targetProteinGrams,
                    topRecommendation = topRec,
                )
            }
            if (pages.isEmpty()) {
                null
            } else {
                DashboardIntelligenceCardState(
                    defaultMealType = defaultMealType,
                    maturity = report.eatingProfile.maturity,
                    mealPages = pages,
                )
            }
        }
        SharedReportSnapshot(
            dateIso = dateData.selectedDate.toString(),
            mealType = mealType,
            report = report,
            dashboardCard = dashboardCard,
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
     * NutraAppRoot or DashboardScreen. Emits null when there is insufficient data.
     */
    val dashboardCardState: StateFlow<DashboardIntelligenceCardState?> = sharedReportFlow
        .map { snap -> snap.dashboardCard }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(30_000),
            initialValue = null,
        )

    private fun overrideKey(dateIso: String, rec: MealRecommendation): String {
        val foodHash = rec.items.joinToString(",") { it.feedbackKey }
        return "$dateIso|${rec.id}|$foodHash"
    }

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
                val recOverrides = overrides[overrideKey(reportSnap.dateIso, rec)] ?: overrides[rec.id]
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
            lastDislikedFood = control.lastDislikedFood,
            feedbackMessage = control.feedbackMessage,
            feedbackEventId = control.feedbackEventId,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(30_000),
        initialValue = IntelligenceUiState(),
    )

    fun refreshToday() {
        val now = LocalDate.now()
        if (selectedDateFlow.value != now) {
            selectedDateFlow.value = now
            studioControlFlow.update { it.copy(portionOverrides = emptyMap()) }
        }
    }

    fun syncDate(date: LocalDate) {
        if (selectedDateFlow.value != date) {
            selectedDateFlow.value = date
            studioControlFlow.update { it.copy(portionOverrides = emptyMap()) }
        }
    }

    fun openForMeal(mealType: MealType? = null, initialTab: IntelligenceStudioTab = IntelligenceStudioTab.RECOMMENDATIONS) {
        studioControlFlow.update {
            it.copy(
                activeTab = initialTab,
                manualMealType = mealType,
                portionOverrides = emptyMap(),
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
        val currentRec = uiState.value.report?.recommendations?.firstOrNull { it.id == recId }
        val key = if (currentRec != null) overrideKey(selectedDateFlow.value.toString(), currentRec) else recId
        studioControlFlow.update { state ->
            val currentMap = (state.portionOverrides[key] ?: state.portionOverrides[recId]).orEmpty().toMutableMap()
            val currentGrams = currentMap[portion.food.id] ?: portion.totalGrams
            val nextGrams = (currentGrams + deltaGrams).coerceIn(portion.minGrams, portion.maxGrams)
            currentMap[portion.food.id] = nextGrams
            val nextOverrides = state.portionOverrides.toMutableMap()
            nextOverrides[key] = currentMap
            state.copy(portionOverrides = nextOverrides)
        }
    }

    fun hasManualEdits(recommendation: MealRecommendation): Boolean {
        val overrides = uiState.value.portionOverrides
        val key = overrideKey(selectedDateFlow.value.toString(), recommendation)
        return !overrides[key].isNullOrEmpty() || !overrides[recommendation.id].isNullOrEmpty()
    }

    fun resetRecommendationPortions(recId: String) {
        val currentRec = uiState.value.report?.recommendations?.firstOrNull { it.id == recId }
        val key = if (currentRec != null) overrideKey(selectedDateFlow.value.toString(), currentRec) else recId
        studioControlFlow.update { state ->
            val next = state.portionOverrides.toMutableMap()
            next.remove(key)
            next.remove(recId)
            state.copy(portionOverrides = next)
        }
    }

    fun logRecommendation(
        recommendation: MealRecommendation,
        onLogged: (MealType, String) -> Unit = { _, _ -> },
    ) {
        if (!isLoggingGuard.compareAndSet(false, true)) return
        val dateIso = selectedDateFlow.value.toString()
        val key = overrideKey(dateIso, recommendation)
        val currentOverrides = studioControlFlow.value.portionOverrides[key]
            ?: studioControlFlow.value.portionOverrides[recommendation.id]
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
                studioControlFlow.update { it.copy(portionOverrides = emptyMap(), lastDislikedFood = null) }
                val mealTitle = if (effective.mealType == MealType.SNACK) "Ara öğün" else effective.mealType.title
                val msg = "$mealTitle eklendi (${NutraFormatters.formatInt(effective.totalCalories)} kcal)"
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
            val id = feedbackSeq.incrementAndGet()
            studioControlFlow.update {
                it.copy(
                    portionOverrides = emptyMap(),
                    lastDislikedFood = portion,
                    feedbackMessage = "${portion.food.name} önerilerden gizlendi",
                    feedbackEventId = id,
                )
            }
        }
    }

    fun undoLastDislike() {
        val disliked = studioControlFlow.value.lastDislikedFood ?: return
        studioControlFlow.update { it.copy(lastDislikedFood = null, feedbackMessage = null) }
        viewModelScope.launch {
            nutritionRepository.setFoodDisliked(
                foodKey = disliked.feedbackKey,
                foodId = disliked.food.id.takeIf { it > 0L },
                foodName = disliked.food.name,
                disliked = false,
            )
            emitFeedback("${disliked.food.name} önerilere yeniden eklendi")
        }
    }

    fun clearFeedback() {
        studioControlFlow.update { it.copy(feedbackMessage = null, lastDislikedFood = null) }
    }

    private fun emitFeedback(message: String) {
        val id = feedbackSeq.incrementAndGet()
        studioControlFlow.update { it.copy(feedbackMessage = message, feedbackEventId = id) }
    }
}
