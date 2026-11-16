package com.nutra.app.feature.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutra.app.core.datastore.UserPreferencesDataStore
import com.nutra.app.domain.model.DailyAnalyticsPoint
import com.nutra.app.domain.model.UserProfile
import com.nutra.app.domain.model.WeightTrendPoint
import com.nutra.app.domain.repository.NutritionRepository
import com.nutra.app.domain.usecase.WeightTrendCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class AnalyticsRangeOption(val days: Int, val title: String) {
    DAYS_7(7, "7 Gün"),
    DAYS_14(14, "14 Gün"),
    DAYS_30(30, "30 Gün"),
}

data class AnalyticsUiState(
    val selectedRange: AnalyticsRangeOption = AnalyticsRangeOption.DAYS_7,
    val dailyPoints: List<DailyAnalyticsPoint> = emptyList(),
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

    val averageCalories: Int
        get() {
            val nonZero = dailyPoints.filter { it.calories > 0 }
            return if (nonZero.isEmpty()) 0 else nonZero.sumOf { it.calories } / nonZero.size
        }

    val averageWaterMl: Int
        get() {
            val nonZero = dailyPoints.filter { it.waterMl > 0 }
            return if (nonZero.isEmpty()) 0 else nonZero.sumOf { it.waterMl } / nonZero.size
        }
}

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
    private val preferencesDataStore: UserPreferencesDataStore,
    private val weightTrendCalculator: WeightTrendCalculator,
) : ViewModel() {

    private val rangeFlow = MutableStateFlow(AnalyticsRangeOption.DAYS_7)
    private val scrubbedDayIndexFlow = MutableStateFlow<Int?>(null)
    private val scrubbedWaterIndexFlow = MutableStateFlow<Int?>(null)
    private val scrubbedWeightIndexFlow = MutableStateFlow<Int?>(null)
    private val showWeightDialogFlow = MutableStateFlow(false)

    private val rangePointsFlow = rangeFlow.flatMapLatest { option ->
        val end = LocalDate.now()
        val start = end.minusDays((option.days - 1).toLong())
        nutritionRepository.observeAnalyticsRange(start, end)
    }

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
        rangePointsFlow,
        nutritionRepository.observeWeightEntries(),
        preferencesDataStore.userProfileFlow,
        interactionStateFlow,
    ) { range, points, weightEntries, profile, interaction ->
        val trendPoints = weightTrendCalculator.calculateTrend(weightEntries)
        val maxDayIdx = (points.size - 1).coerceAtLeast(0)
        AnalyticsUiState(
            selectedRange = range,
            dailyPoints = points,
            scrubbedDayIndex = interaction.scrubbedDayIndex?.coerceIn(0, maxDayIdx),
            scrubbedWaterIndex = interaction.scrubbedWaterIndex?.coerceIn(0, maxDayIdx),
            weightTrendPoints = trendPoints,
            scrubbedWeightIndex = interaction.scrubbedWeightIndex?.coerceIn(0, (trendPoints.size - 1).coerceAtLeast(0)),
            profile = profile,
            showAddWeightDialog = interaction.showAddWeightDialog,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AnalyticsUiState(),
    )

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
            nutritionRepository.logWeight(LocalDate.now().toString(), weightKg)
            showWeightDialogFlow.value = false
        }
    }
}
