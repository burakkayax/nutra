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

    fun adjustAge(delta: Int) {
        _uiState.update { it.copy(age = (it.age + delta).coerceIn(14, 95)) }
        recalculateGoals()
    }

    fun adjustHeight(delta: Int) {
        _uiState.update { it.copy(heightCm = (it.heightCm + delta).coerceIn(130, 225)) }
        recalculateGoals()
    }

    fun adjustCurrentWeight(delta: Float) {
        _uiState.update {
            val next = ((it.currentWeightKg + delta) * 10f).toInt() / 10f
            it.copy(currentWeightKg = next.coerceIn(38f, 220f))
        }
        recalculateGoals()
    }

    fun adjustTargetWeight(delta: Float) {
        _uiState.update {
            val next = ((it.targetWeightKg + delta) * 10f).toInt() / 10f
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

    fun adjustCustomCalories(delta: Int) {
        _uiState.update { state ->
            val updated = state.customGoals.copy(
                calories = (state.customGoals.calories + delta).coerceIn(1100, 5000),
            )
            state.copy(customGoals = updated, isCustomized = true)
        }
    }

    fun adjustCustomProtein(delta: Int) {
        _uiState.update { state ->
            val updated = state.customGoals.copy(
                proteinGrams = (state.customGoals.proteinGrams + delta).coerceIn(40, 350),
            )
            state.copy(customGoals = updated, isCustomized = true)
        }
    }

    fun adjustCustomCarbs(delta: Int) {
        _uiState.update { state ->
            val updated = state.customGoals.copy(
                carbsGrams = (state.customGoals.carbsGrams + delta).coerceIn(30, 550),
            )
            state.copy(customGoals = updated, isCustomized = true)
        }
    }

    fun adjustCustomFat(delta: Int) {
        _uiState.update { state ->
            val updated = state.customGoals.copy(
                fatGrams = (state.customGoals.fatGrams + delta).coerceIn(25, 220),
            )
            state.copy(customGoals = updated, isCustomized = true)
        }
    }

    fun adjustCustomWater(delta: Int) {
        _uiState.update { state ->
            val updated = state.customGoals.copy(
                waterMl = (state.customGoals.waterMl + delta).coerceIn(1200, 6000),
            )
            state.copy(customGoals = updated, isCustomized = true)
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
