package com.nutra.app.feature.analytics

import androidx.compose.runtime.Immutable
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

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
    val selectedRange: AnalyticsRangeOption = AnalyticsRangeOption.DAYS_7,
    val dailyPoints: List<DailyAnalyticsPoint> = emptyList(),
    val averageCalories: Int = 0,
    val averageWaterMl: Int = 0,
    val scrubbedDayIndex: Int? = null,
    val scrubbedWaterIndex: Int? = null,
    val weightTrendPoints: List<WeightTrendPoint> = emptyList(),
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

private data class AnalyticsRangeSummary(
    val points: List<DailyAnalyticsPoint>,
    val averageCalories: Int,
    val averageWaterMl: Int,
)

private data class AnalyticsInteractionState(
    val scrubbedDayIndex: Int?,
    val scrubbedWaterIndex: Int?,
    val scrubbedWeightIndex: Int?,
    val showAddWeightDialog: Boolean,
    val feedbackMessage: String?,
    val feedbackEventId: Long,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val nutritionRepository: NutritionRepository,
    preferencesDataStore: UserPreferencesDataStore,
    private val weightTrendCalculator: WeightTrendCalculator,
) : ViewModel() {

    private val todayFlow = MutableStateFlow(LocalDate.now())
    private val rangeFlow = MutableStateFlow(AnalyticsRangeOption.DAYS_7)
    private val scrubbedDayIndexFlow = MutableStateFlow<Int?>(null)
    private val scrubbedWaterIndexFlow = MutableStateFlow<Int?>(null)
    private val scrubbedWeightIndexFlow = MutableStateFlow<Int?>(null)
    private val showWeightDialogFlow = MutableStateFlow(false)
    private val feedbackSeq = AtomicLong(0L)
    private val feedbackFlow = MutableStateFlow<Pair<String?, Long>>(null to 0L)

    private val rangeSummaryFlow = combine(rangeFlow, todayFlow) { option, end ->
        option to end
    }.flatMapLatest { (option, end) ->
        val start = end.minusDays((option.days - 1).toLong())
        nutritionRepository.observeAnalyticsRange(start, end)
            .map { points ->
                var calSum = 0
                var calCount = 0
                var waterSum = 0
                var waterCount = 0
                for (pt in points) {
                    if (pt.calories > 0) {
                        calSum += pt.calories
                        calCount++
                    }
                    if (pt.waterMl > 0) {
                        waterSum += pt.waterMl
                        waterCount++
                    }
                }
                AnalyticsRangeSummary(
                    points = points,
                    averageCalories = if (calCount == 0) 0 else calSum / calCount,
                    averageWaterMl = if (waterCount == 0) 0 else waterSum / waterCount,
                )
            }
            .flowOn(Dispatchers.Default)
    }

    private val weightTrendPointsFlow = nutritionRepository.observeWeightEntries()
        .map { entries -> weightTrendCalculator.calculateTrend(entries) }
        .flowOn(Dispatchers.Default)

    private val interactionStateFlow = combine(
        scrubbedDayIndexFlow,
        scrubbedWaterIndexFlow,
        scrubbedWeightIndexFlow,
        showWeightDialogFlow,
        feedbackFlow,
    ) { dayIdx, waterIdx, weightIdx, showDialog, (feedbackMsg, feedbackId) ->
        AnalyticsInteractionState(
            scrubbedDayIndex = dayIdx,
            scrubbedWaterIndex = waterIdx,
            scrubbedWeightIndex = weightIdx,
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

    val uiState: StateFlow<AnalyticsUiState> = combine(
        rangeFlow,
        rangeSummaryFlow,
        weightTrendPointsFlow,
        analyticsProfileFlow,
        interactionStateFlow,
    ) { range, summary, trendPoints, profile, interaction ->
        val points = summary.points
        AnalyticsUiState(
            isLoading = false,
            selectedRange = range,
            dailyPoints = points,
            averageCalories = summary.averageCalories,
            averageWaterMl = summary.averageWaterMl,
            scrubbedDayIndex = if (points.isEmpty()) null else interaction.scrubbedDayIndex?.coerceIn(0, points.lastIndex),
            scrubbedWaterIndex = if (points.isEmpty()) null else interaction.scrubbedWaterIndex?.coerceIn(0, points.lastIndex),
            weightTrendPoints = trendPoints,
            scrubbedWeightIndex = if (trendPoints.isEmpty()) null else interaction.scrubbedWeightIndex?.coerceIn(0, trendPoints.lastIndex),
            profile = profile,
            showAddWeightDialog = interaction.showAddWeightDialog,
            feedbackMessage = interaction.feedbackMessage,
            feedbackEventId = interaction.feedbackEventId,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(30_000),
        initialValue = AnalyticsUiState(),
    )

    fun refreshToday() {
        val now = LocalDate.now()
        if (todayFlow.value != now) {
            todayFlow.value = now
        }
    }

    fun selectRange(option: AnalyticsRangeOption) {
        rangeFlow.value = option
        scrubbedDayIndexFlow.value = null
        scrubbedWaterIndexFlow.value = null
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
