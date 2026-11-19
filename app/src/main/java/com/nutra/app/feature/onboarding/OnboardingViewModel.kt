package com.nutra.app.feature.onboarding

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutra.app.core.datastore.UserPreferencesDataStore
import com.nutra.app.domain.model.ActivityLevel
import com.nutra.app.domain.model.BiologicalSex
import com.nutra.app.domain.model.GoalType
import com.nutra.app.domain.model.MacroGoals
import com.nutra.app.domain.model.UserProfile
import com.nutra.app.domain.repository.NutritionRepository
import com.nutra.app.domain.usecase.BodyMetricBounds
import com.nutra.app.domain.usecase.MacroGoalBounds
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

@Immutable
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
    val isCompleting: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val preferencesDataStore: UserPreferencesDataStore,
    private val nutritionRepository: NutritionRepository,
    private val calculatorUseCase: NutritionCalculatorUseCase,
) : ViewModel() {

    private companion object {
        const val KEY_STEP_INDEX = "onboarding_step_index"
    }

    private val onboardingGoalBounds = MacroGoalBounds()

    private var calorieScalingBaseline: MacroGoals? = null

    private val _uiState = MutableStateFlow(
        OnboardingUiState(
            stepIndex = (savedStateHandle.get<Int>(KEY_STEP_INDEX) ?: 0).coerceIn(0, 2),
        ),
    )
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        recalculateGoals()
    }

    fun selectGoalType(goalType: GoalType) = updateAndRecalculate {
        it.copy(
            goalType = goalType,
            targetWeightKg = if (goalType == GoalType.MAINTAIN) it.currentWeightKg else it.targetWeightKg,
        )
    }

    fun selectSex(sex: BiologicalSex) = updateAndRecalculate {
        it.copy(sex = sex)
    }

    fun adjustAge(delta: Int) = setAge(_uiState.value.age + delta)

    fun setAge(age: Int) = updateAndRecalculate {
        it.copy(age = age.coerceIn(BodyMetricBounds.MIN_AGE, BodyMetricBounds.MAX_AGE))
    }

    fun adjustHeight(delta: Int) = setHeight(_uiState.value.heightCm + delta)

    fun setHeight(heightCm: Int) = updateAndRecalculate {
        it.copy(heightCm = heightCm.coerceIn(BodyMetricBounds.MIN_HEIGHT_CM, BodyMetricBounds.MAX_HEIGHT_CM))
    }

    fun adjustCurrentWeight(delta: Float) = setCurrentWeight(_uiState.value.currentWeightKg + delta)

    fun setCurrentWeight(weightKg: Float) = updateAndRecalculate {
        val next = ((weightKg * 10f).roundToInt() / 10f)
            .coerceIn(BodyMetricBounds.MIN_WEIGHT_KG, BodyMetricBounds.MAX_WEIGHT_KG)
        it.copy(
            currentWeightKg = next,
            targetWeightKg = if (it.goalType == GoalType.MAINTAIN) next else it.targetWeightKg,
        )
    }

    fun adjustTargetWeight(delta: Float) = setTargetWeight(_uiState.value.targetWeightKg + delta)

    fun setTargetWeight(weightKg: Float) {
        _uiState.update {
            val next = ((weightKg * 10f).roundToInt() / 10f)
                .coerceIn(BodyMetricBounds.MIN_WEIGHT_KG, BodyMetricBounds.MAX_WEIGHT_KG)
            it.copy(targetWeightKg = next)
        }
    }

    fun selectActivityLevel(level: ActivityLevel) = updateAndRecalculate {
        it.copy(activityLevel = level)
    }

    fun nextStep() {
        _uiState.update { state ->
            val next = (state.stepIndex + 1).coerceAtMost(2)
            savedStateHandle[KEY_STEP_INDEX] = next
            state.copy(stepIndex = next)
        }
    }

    fun previousStep() {
        _uiState.update { state ->
            val prev = (state.stepIndex - 1).coerceAtLeast(0)
            savedStateHandle[KEY_STEP_INDEX] = prev
            state.copy(stepIndex = prev)
        }
    }

    fun adjustCustomCalories(delta: Int) = setCustomCalories(_uiState.value.customGoals.calories + delta)

    fun setCustomCalories(calories: Int) {
        _uiState.update { state ->
            val baseline = calorieScalingBaseline ?: state.customGoals.also { calorieScalingBaseline = it }
            val updated = calculatorUseCase.scaleMacrosForCalorieGoal(
                baselineGoals = baseline,
                targetCalories = calories,
                bounds = onboardingGoalBounds,
            ).copy(waterMl = state.customGoals.waterMl)
            state.copy(customGoals = updated, isCustomized = true)
        }
    }

    fun adjustCustomProtein(delta: Int) = setCustomProtein(_uiState.value.customGoals.proteinGrams + delta)

    fun setCustomProtein(grams: Int) = updateMacrosAndRecalculateCalories {
        it.copy(proteinGrams = grams)
    }

    fun adjustCustomCarbs(delta: Int) = setCustomCarbs(_uiState.value.customGoals.carbsGrams + delta)

    fun setCustomCarbs(grams: Int) = updateMacrosAndRecalculateCalories {
        it.copy(carbsGrams = grams)
    }

    fun adjustCustomFat(delta: Int) = setCustomFat(_uiState.value.customGoals.fatGrams + delta)

    fun setCustomFat(grams: Int) = updateMacrosAndRecalculateCalories {
        it.copy(fatGrams = grams)
    }

    fun adjustCustomWater(delta: Int) = setCustomWater(_uiState.value.customGoals.waterMl + delta)

    fun setCustomWater(waterMl: Int) {
        _uiState.update { state ->
            val nextWater = waterMl.coerceIn(onboardingGoalBounds.minWaterMl, onboardingGoalBounds.maxWaterMl)
            calorieScalingBaseline = calorieScalingBaseline?.copy(waterMl = nextWater)
            state.copy(
                customGoals = state.customGoals.copy(waterMl = nextWater),
                isCustomized = true,
            )
        }
    }

    private inline fun updateMacrosAndRecalculateCalories(transform: (MacroGoals) -> MacroGoals) {
        calorieScalingBaseline = null
        _uiState.update { state ->
            val updated = calculatorUseCase.recalculateCaloriesFromMacros(
                goals = transform(state.customGoals),
                bounds = onboardingGoalBounds,
            )
            state.copy(customGoals = updated, isCustomized = true)
        }
    }

    fun resetToMifflinFormula() {
        calorieScalingBaseline = null
        _uiState.update { state ->
            state.copy(customGoals = state.calculatedGoals, isCustomized = false)
        }
    }

    fun completeOnboarding(onFinished: () -> Unit) {
        val state = _uiState.value
        if (state.isCompleting) return
        _uiState.update { it.copy(isCompleting = true) }
        viewModelScope.launch {
            try {
                nutritionRepository.logWeight(
                    dateIso = LocalDate.now().toString(),
                    weightKg = state.currentWeightKg,
                    syncProfileWeight = false,
                )
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
                onFinished()
            } finally {
                _uiState.update { it.copy(isCompleting = false) }
            }
        }
    }

    private inline fun updateAndRecalculate(transform: (OnboardingUiState) -> OnboardingUiState) {
        _uiState.update { current ->
            val updated = transform(current)
            val calc = calculatorUseCase.calculateGoals(
                sex = updated.sex,
                age = updated.age,
                heightCm = updated.heightCm,
                weightKg = updated.currentWeightKg,
                activityLevel = updated.activityLevel,
                goalType = updated.goalType,
            )
            updated.copy(
                calculatedGoals = calc,
                customGoals = if (updated.isCustomized) updated.customGoals else calc,
            )
        }
    }

    private fun recalculateGoals() {
        updateAndRecalculate { it }
    }
}
