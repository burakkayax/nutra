package com.nutra.app.feature.analytics

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutra.app.core.datastore.UserPreferencesDataStore
import com.nutra.app.core.designsystem.NutraFormatters
import com.nutra.app.domain.model.DailyAnalyticsPoint
import com.nutra.app.domain.model.GoalType
import com.nutra.app.domain.model.MacroGoals
import com.nutra.app.domain.model.WeightTrendPoint
import com.nutra.app.domain.repository.NutritionRepository
import com.nutra.app.domain.usecase.WeightTrendCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlin.math.roundToInt

enum class AnalyticsRangeOption(val days: Int, val title: String) {
    DAYS_7(7, "7 Gün"),
    DAYS_14(14, "14 Gün"),
    DAYS_30(30, "30 Gün"),
}

@Immutable
data class AnalyticsProfileSlice(
    val goals: MacroGoals = MacroGoals(),
    val currentWeightKg: Float = 75f,
    val targetWeightKg: Float = 70f,
    val goalType: GoalType = GoalType.LOSE_WEIGHT,
)

@Immutable
data class AnalyticsUiState(
    val isLoading: Boolean = true,
    val todayIso: String = LocalDate.now().toString(),
    val selectedRange: AnalyticsRangeOption = AnalyticsRangeOption.DAYS_7,
    val dailyPoints: List<DailyAnalyticsPoint> = emptyList(),
    val averageCalories: Int = 0,
    val averageWaterMl: Int = 0,
    val averageProteinGrams: Float = 0f,
    val averageCarbsGrams: Float = 0f,
    val averageFatGrams: Float = 0f,
    val scrubbedDayIndex: Int? = null,
    val scrubbedWaterIndex: Int? = null,
    val weightTrendPoints: List<WeightTrendPoint> = emptyList(),
    val latestRecordedWeightKg: Float? = null,
    val hasAnyWeightHistory: Boolean = false,
    val scrubbedWeightIndex: Int? = null,
    val profile: AnalyticsProfileSlice = AnalyticsProfileSlice(),
    val showAddWeightDialog: Boolean = false,
    val feedbackMessage: String? = null,
    val feedbackEventId: Long = 0L,
) {
    val activeDayPoint: DailyAnalyticsPoint?
        get() = scrubbedDayIndex?.let { dailyPoints.getOrNull(it) } ?: dailyPoints.lastOrNull()

    val activeWaterDayPoint: DailyAnalyticsPoint?
        get() = scrubbedWaterIndex?.let { dailyPoints.getOrNull(it) } ?: dailyPoints.lastOrNull()
}

internal data class AnalyticsRangeSummary(
    val points: List<DailyAnalyticsPoint>,
    val averageCalories: Int,
    val averageWaterMl: Int,
    val averageProteinGrams: Float,
    val averageCarbsGrams: Float,
    val averageFatGrams: Float,
    val option: AnalyticsRangeOption = AnalyticsRangeOption.DAYS_7,
    val endDateIso: String = "",
)

internal data class WeightRangeSummary(
    val pointsInRange: List<WeightTrendPoint>,
    val latestRecordedWeightKg: Float?,
    val hasAnyHistory: Boolean,
    val option: AnalyticsRangeOption = AnalyticsRangeOption.DAYS_7,
    val endDateIso: String = "",
)

internal fun summarizeAnalyticsRange(
    points: List<DailyAnalyticsPoint>,
    option: AnalyticsRangeOption = AnalyticsRangeOption.DAYS_7,
    endDateIso: String = "",
): AnalyticsRangeSummary {
    var calSum = 0
    var calCount = 0
    var waterSum = 0
    var waterCount = 0
    var proteinSum = 0.0
    var carbsSum = 0.0
    var fatSum = 0.0
    var macroDayCount = 0
    for (i in points.indices) {
        val pt = points[i]
        if (pt.calories > 0) {
            calSum += pt.calories
            calCount++
        }
        if (pt.waterMl > 0) {
            waterSum += pt.waterMl
            waterCount++
        }
        if (pt.proteinGrams > 0f || pt.carbsGrams > 0f || pt.fatGrams > 0f) {
            proteinSum += pt.proteinGrams
            carbsSum += pt.carbsGrams
            fatSum += pt.fatGrams
            macroDayCount++
        }
    }
    return AnalyticsRangeSummary(
        points = points,
        averageCalories = if (calCount == 0) 0 else (calSum.toFloat() / calCount).roundToInt(),
        averageWaterMl = if (waterCount == 0) 0 else (waterSum.toFloat() / waterCount).roundToInt(),
        averageProteinGrams = if (macroDayCount == 0) 0f else (proteinSum / macroDayCount).toFloat(),
        averageCarbsGrams = if (macroDayCount == 0) 0f else (carbsSum / macroDayCount).toFloat(),
        averageFatGrams = if (macroDayCount == 0) 0f else (fatSum / macroDayCount).toFloat(),
        option = option,
        endDateIso = endDateIso,
    )
}

internal fun filterWeightTrendForRange(
    allTrendPoints: List<WeightTrendPoint>,
    option: AnalyticsRangeOption,
    endDate: LocalDate,
): WeightRangeSummary {
    val startIso = endDate.minusDays((option.days - 1).toLong()).toString()
    val endIso = endDate.toString()
    return WeightRangeSummary(
        pointsInRange = allTrendPoints.filter { it.dateIso in startIso..endIso },
        latestRecordedWeightKg = allTrendPoints.lastOrNull()?.rawWeightKg,
        hasAnyHistory = allTrendPoints.isNotEmpty(),
        option = option,
        endDateIso = endIso,
    )
}

private data class AnalyticsDialogFeedbackState(
    val showAddWeightDialog: Boolean,
    val feedbackMessage: String?,
    val feedbackEventId: Long,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val nutritionRepository: NutritionRepository,
    preferencesDataStore: UserPreferencesDataStore,
    private val weightTrendCalculator: WeightTrendCalculator,
) : ViewModel() {

    private companion object {
        const val KEY_SELECTED_RANGE = "analytics_selected_range"
        val RANGE_BY_NAME: Map<String, AnalyticsRangeOption> = AnalyticsRangeOption.entries.associateBy { it.name }
    }

    private val todayFlow = MutableStateFlow(LocalDate.now())
    private val rangeFlow = MutableStateFlow(
        savedStateHandle.get<String>(KEY_SELECTED_RANGE)?.let { RANGE_BY_NAME[it] }
            ?: AnalyticsRangeOption.DAYS_7,
    )
    private val scrubbedDayIndexFlow = MutableStateFlow<Int?>(null)
    private val scrubbedWaterIndexFlow = MutableStateFlow<Int?>(null)
    private val scrubbedWeightIndexFlow = MutableStateFlow<Int?>(null)

    // Exposed separately from uiState so chart scrubbing never recomposes AnalyticsScreen or sibling cards.
    val scrubbedDayIndex: StateFlow<Int?> = scrubbedDayIndexFlow.asStateFlow()
    val scrubbedWaterIndex: StateFlow<Int?> = scrubbedWaterIndexFlow.asStateFlow()
    val scrubbedWeightIndex: StateFlow<Int?> = scrubbedWeightIndexFlow.asStateFlow()

    private val showWeightDialogFlow = MutableStateFlow(false)
    private val feedbackSeq = AtomicLong(0L)
    private val feedbackFlow = MutableStateFlow<Pair<String?, Long>>(null to 0L)

    private val rangeSummaryFlow = combine(rangeFlow, todayFlow) { option, end ->
        option to end
    }.flatMapLatest { (option, end) ->
        val start = end.minusDays((option.days - 1).toLong())
        val endIso = end.toString()
        nutritionRepository.observeAnalyticsRange(start, end)
            .map { points -> summarizeAnalyticsRange(points, option = option, endDateIso = endIso) }
            .flowOn(Dispatchers.Default)
    }

    private val allWeightTrendPointsFlow = nutritionRepository.observeWeightEntries()
        .map { entries -> weightTrendCalculator.calculateTrend(entries) }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    private val weightRangeSummaryFlow = combine(
        allWeightTrendPointsFlow,
        rangeFlow,
        todayFlow,
    ) { allPoints, option, end ->
        filterWeightTrendForRange(allPoints, option, end)
    }.flowOn(Dispatchers.Default)

    private val dialogFeedbackStateFlow = combine(
        showWeightDialogFlow,
        feedbackFlow,
    ) { showDialog, (feedbackMsg, feedbackId) ->
        AnalyticsDialogFeedbackState(
            showAddWeightDialog = showDialog,
            feedbackMessage = feedbackMsg,
            feedbackEventId = feedbackId,
        )
    }

    private val analyticsProfileFlow = preferencesDataStore.userProfileFlow
        .map { profile ->
            AnalyticsProfileSlice(
                goals = profile.goals,
                currentWeightKg = profile.currentWeightKg,
                targetWeightKg = profile.targetWeightKg,
                goalType = profile.goalType,
            )
        }
        .distinctUntilChanged()

    // Pair rangeSummary and weightRangeSummary atomically so rapid 7 -> 30 -> 14 switches never mix periods.
    val uiState: StateFlow<AnalyticsUiState> = combine(
        rangeSummaryFlow,
        weightRangeSummaryFlow,
        analyticsProfileFlow,
        dialogFeedbackStateFlow,
    ) { summary, weightSummary, profile, dialogFeedback ->
        if (summary.option != weightSummary.option || summary.endDateIso != weightSummary.endDateIso) {
            return@combine null
        }
        AnalyticsUiState(
            isLoading = false,
            todayIso = summary.endDateIso.ifBlank { todayFlow.value.toString() },
            selectedRange = summary.option,
            dailyPoints = summary.points,
            averageCalories = summary.averageCalories,
            averageWaterMl = summary.averageWaterMl,
            averageProteinGrams = summary.averageProteinGrams,
            averageCarbsGrams = summary.averageCarbsGrams,
            averageFatGrams = summary.averageFatGrams,
            weightTrendPoints = weightSummary.pointsInRange,
            latestRecordedWeightKg = weightSummary.latestRecordedWeightKg,
            hasAnyWeightHistory = weightSummary.hasAnyHistory,
            profile = profile,
            showAddWeightDialog = dialogFeedback.showAddWeightDialog,
            feedbackMessage = dialogFeedback.feedbackMessage,
            feedbackEventId = dialogFeedback.feedbackEventId,
        )
    }.filterNotNull().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(30_000),
        initialValue = AnalyticsUiState(selectedRange = rangeFlow.value),
    )

    fun refreshToday() {
        val now = LocalDate.now()
        if (todayFlow.value != now) {
            todayFlow.value = now
        }
    }

    fun selectRange(option: AnalyticsRangeOption) {
        if (rangeFlow.value == option) return
        rangeFlow.value = option
        savedStateHandle[KEY_SELECTED_RANGE] = option.name
        scrubbedDayIndexFlow.value = null
        scrubbedWaterIndexFlow.value = null
        scrubbedWeightIndexFlow.value = null
    }

    fun updateScrubbedDayIndex(index: Int?) {
        scrubbedDayIndexFlow.value = index
    }

    fun updateScrubbedWaterIndex(index: Int?) {
        scrubbedWaterIndexFlow.value = index
    }

    fun updateScrubbedWeightIndex(index: Int?) {
        scrubbedWeightIndexFlow.value = index
    }

    fun openAddWeightDialog() {
        showWeightDialogFlow.value = true
    }

    fun closeAddWeightDialog() {
        showWeightDialogFlow.value = false
    }

    fun logNewWeight(weightKg: Float) {
        viewModelScope.launch {
            val today = LocalDate.now()
            todayFlow.value = today
            nutritionRepository.logWeight(today.toString(), weightKg)
            showWeightDialogFlow.value = false
            feedbackFlow.value = "${NutraFormatters.formatKg(weightKg)} kg tartı kaydedildi" to feedbackSeq.incrementAndGet()
        }
    }

    fun clearFeedback() {
        feedbackFlow.value = null to feedbackSeq.get()
    }
}
