package com.nutra.app.feature.analytics

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutra.app.core.datastore.UserPreferencesDataStore
import com.nutra.app.domain.model.DailyAnalyticsPoint
import com.nutra.app.domain.model.UserProfile
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class AnalyticsRangeOption(val days: Int, val title: String) {
    DAYS_7(7, "7 Gün"),
    DAYS_14(14, "14 Gün"),
    DAYS_30(30, "30 Gün"),
}

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
    val profile: UserProfile = UserProfile(),
    val showAddWeightDialog: Boolean = false,
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
    ) { dayIdx, waterIdx, weightIdx, showDialog ->
        AnalyticsInteractionState(
            scrubbedDayIndex = dayIdx,
            scrubbedWaterIndex = waterIdx,
            scrubbedWeightIndex = weightIdx,
            showAddWeightDialog = showDialog,
        )
    }

    val uiState: StateFlow<AnalyticsUiState> = combine(
        rangeFlow,
        rangeSummaryFlow,
        weightTrendPointsFlow,
        preferencesDataStore.userProfileFlow,
        interactionStateFlow,
    ) { range, summary, trendPoints, profile, interaction ->
        val points = summary.points
        val maxDayIdx = (points.size - 1).coerceAtLeast(0)
        AnalyticsUiState(
            isLoading = false,
            selectedRange = range,
            dailyPoints = points,
            averageCalories = summary.averageCalories,
            averageWaterMl = summary.averageWaterMl,
            scrubbedDayIndex = interaction.scrubbedDayIndex?.coerceIn(0, maxDayIdx),
            scrubbedWaterIndex = interaction.scrubbedWaterIndex?.coerceIn(0, maxDayIdx),
            weightTrendPoints = trendPoints,
            scrubbedWeightIndex = interaction.scrubbedWeightIndex?.coerceIn(0, (trendPoints.size - 1).coerceAtLeast(0)),
            profile = profile,
            showAddWeightDialog = interaction.showAddWeightDialog,
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
        }
    }
}
