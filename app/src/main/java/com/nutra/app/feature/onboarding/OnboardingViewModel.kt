package com.nutra.app.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutra.app.core.datastore.UserPreferencesDataStore
import com.nutra.app.domain.model.ActivityLevel
import com.nutra.app.domain.model.BiologicalSex
import com.nutra.app.domain.model.GoalType
import com.nutra.app.domain.model.MacroGoals
import com.nutra.app.domain.model.UserProfile
import com.nutra.app.domain.repository.NutritionRepository
import com.nutra.app.domain.usecase.NutritionCalculatorUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToInt

data class OnboardingUiState(
    val stepIndex: Int = 0,
    val goalType: GoalType = GoalType.LOSE_WEIGHT,
    val sex: BiologicalSex = BiologicalSex.MALE,
    val age: Int = 28,
    val heightCm: Int = 176,
    val currentWeightKg: Float = 76f,
    val targetWeightKg: Float = 70f,
    val activityLevel: ActivityLevel = ActivityLevel.MODERATE,
    val calculatedGoals: MacroGoals = MacroGoals(),
    val customGoals: MacroGoals = MacroGoals(),
    val isCustomized: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val preferencesDataStore: UserPreferencesDataStore,
    private val nutritionRepository: NutritionRepository,
    private val calculatorUseCase: NutritionCalculatorUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        recalculateGoals()
    }

    fun selectGoalType(goalType: GoalType) {
        _uiState.update { it.copy(goalType = goalType) }
        recalculateGoals()
    }

    fun selectSex(sex: BiologicalSex) {
        _uiState.update { it.copy(sex = sex) }
        recalculateGoals()
    }

    fun adjustAge(delta: Int) = setAge(_uiState.value.age + delta)

    fun setAge(age: Int) {
        _uiState.update { it.copy(age = age.coerceIn(14, 95)) }
        recalculateGoals()
    }

    fun adjustHeight(delta: Int) = setHeight(_uiState.value.heightCm + delta)

    fun setHeight(heightCm: Int) {
        _uiState.update { it.copy(heightCm = heightCm.coerceIn(130, 225)) }
        recalculateGoals()
    }

    fun adjustCurrentWeight(delta: Float) = setCurrentWeight(_uiState.value.currentWeightKg + delta)

    fun setCurrentWeight(weightKg: Float) {
        _uiState.update {
            val next = (weightKg * 10f).roundToInt() / 10f
            it.copy(currentWeightKg = next.coerceIn(38f, 220f))
        }
        recalculateGoals()
    }

    fun adjustTargetWeight(delta: Float) = setTargetWeight(_uiState.value.targetWeightKg + delta)

    fun setTargetWeight(weightKg: Float) {
        _uiState.update {
            val next = (weightKg * 10f).roundToInt() / 10f
            it.copy(targetWeightKg = next.coerceIn(38f, 220f))
        }
    }

    fun selectActivityLevel(level: ActivityLevel) {
        _uiState.update { it.copy(activityLevel = level) }
        recalculateGoals()
    }

    fun nextStep() {
        _uiState.update { it.copy(stepIndex = (it.stepIndex + 1).coerceAtMost(2)) }
    }

    fun previousStep() {
        _uiState.update { it.copy(stepIndex = (it.stepIndex - 1).coerceAtLeast(0)) }
    }

    fun adjustCustomCalories(delta: Int) = setCustomCalories(_uiState.value.customGoals.calories + delta)

    fun setCustomCalories(calories: Int) = updateCustomGoals {
        it.copy(calories = calories.coerceIn(1100, 5000))
    }

    fun adjustCustomProtein(delta: Int) = setCustomProtein(_uiState.value.customGoals.proteinGrams + delta)

    fun setCustomProtein(grams: Int) = updateCustomGoals {
        it.copy(proteinGrams = grams.coerceIn(40, 350))
    }

    fun adjustCustomCarbs(delta: Int) = setCustomCarbs(_uiState.value.customGoals.carbsGrams + delta)

    fun setCustomCarbs(grams: Int) = updateCustomGoals {
        it.copy(carbsGrams = grams.coerceIn(30, 550))
    }

    fun adjustCustomFat(delta: Int) = setCustomFat(_uiState.value.customGoals.fatGrams + delta)

    fun setCustomFat(grams: Int) = updateCustomGoals {
        it.copy(fatGrams = grams.coerceIn(25, 220))
    }

    fun adjustCustomWater(delta: Int) = setCustomWater(_uiState.value.customGoals.waterMl + delta)

    fun setCustomWater(waterMl: Int) = updateCustomGoals {
        it.copy(waterMl = waterMl.coerceIn(1200, 6000))
    }

    private inline fun updateCustomGoals(transform: (MacroGoals) -> MacroGoals) {
        _uiState.update { state ->
            state.copy(customGoals = transform(state.customGoals), isCustomized = true)
        }
    }

    fun resetToMifflinFormula() {
        _uiState.update { state ->
            state.copy(customGoals = state.calculatedGoals, isCustomized = false)
        }
    }

    fun completeOnboarding(onFinished: () -> Unit) {
        val state = _uiState.value
        viewModelScope.launch {
            val profile = UserProfile(
                sex = state.sex,
                age = state.age,
                heightCm = state.heightCm,
                currentWeightKg = state.currentWeightKg,
                targetWeightKg = state.targetWeightKg,
                activityLevel = state.activityLevel,
                goalType = state.goalType,
                goals = state.customGoals,
                onboardingCompleted = true,
            )
            preferencesDataStore.saveOnboardingProfile(profile)
            nutritionRepository.logWeight(LocalDate.now().toString(), state.currentWeightKg)
            onFinished()
        }
    }

    private fun recalculateGoals() {
        val s = _uiState.value
        val calc = calculatorUseCase.calculateGoals(
            sex = s.sex,
            age = s.age,
            heightCm = s.heightCm,
            weightKg = s.currentWeightKg,
            activityLevel = s.activityLevel,
            goalType = s.goalType,
        )
        _uiState.update {
            it.copy(
                calculatedGoals = calc,
                customGoals = if (it.isCustomized) it.customGoals else calc,
            )
        }
    }
}
