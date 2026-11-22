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
    val currentWeightKg: Float = 75f,
    val targetWeightKg: Float = 70f,
    val activityLevel: ActivityLevel = ActivityLevel.MODERATE,
    val calculatedGoals: MacroGoals = MacroGoals(),
    val customGoals: MacroGoals = MacroGoals(),
    val isCustomized: Boolean = false,
    val isCompleting: Boolean = false,
) {
    val isAdolescentObservationMode: Boolean
        get() = age < BodyMetricBounds.ADULT_MIN_AGE
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val preferencesDataStore: UserPreferencesDataStore,
    private val nutritionRepository: NutritionRepository,
    private val calculatorUseCase: NutritionCalculatorUseCase,
) : ViewModel() {

    private companion object {
        const val KEY_STEP_INDEX = "onboarding_step_index"
        const val KEY_GOAL_TYPE = "onboarding_goal_type"
        const val KEY_SEX = "onboarding_sex"
        const val KEY_AGE = "onboarding_age"
        const val KEY_HEIGHT = "onboarding_height"
        const val KEY_CURRENT_WEIGHT = "onboarding_current_weight"
        const val KEY_TARGET_WEIGHT = "onboarding_target_weight"
        const val KEY_ACTIVITY = "onboarding_activity"
        const val KEY_IS_CUSTOMIZED = "onboarding_is_customized"
        const val DEFAULT_WEIGHT_KG = 75f
    }

    private val onboardingGoalBounds = MacroGoalBounds()

    private var calorieScalingBaseline: MacroGoals? = null

    private val _uiState = MutableStateFlow(
        OnboardingUiState(
            stepIndex = (savedStateHandle.get<Int>(KEY_STEP_INDEX) ?: 0).coerceIn(0, 2),
            goalType = savedStateHandle.get<String>(KEY_GOAL_TYPE)
                ?.let { runCatching { GoalType.valueOf(it) }.getOrNull() }
                ?: GoalType.LOSE_WEIGHT,
            sex = savedStateHandle.get<String>(KEY_SEX)
                ?.let { runCatching { BiologicalSex.valueOf(it) }.getOrNull() }
                ?: BiologicalSex.MALE,
            age = (savedStateHandle.get<Int>(KEY_AGE) ?: 28)
                .coerceIn(BodyMetricBounds.MIN_AGE, BodyMetricBounds.MAX_AGE),
            heightCm = (savedStateHandle.get<Int>(KEY_HEIGHT) ?: 176)
                .coerceIn(BodyMetricBounds.MIN_HEIGHT_CM, BodyMetricBounds.MAX_HEIGHT_CM),
            currentWeightKg = (savedStateHandle.get<Float>(KEY_CURRENT_WEIGHT) ?: DEFAULT_WEIGHT_KG)
                .takeIf { it.isFinite() }
                ?.coerceIn(BodyMetricBounds.MIN_WEIGHT_KG, BodyMetricBounds.MAX_WEIGHT_KG)
                ?: DEFAULT_WEIGHT_KG,
            targetWeightKg = (savedStateHandle.get<Float>(KEY_TARGET_WEIGHT) ?: 70f)
                .takeIf { it.isFinite() }
                ?.coerceIn(BodyMetricBounds.MIN_WEIGHT_KG, BodyMetricBounds.MAX_WEIGHT_KG)
                ?: 70f,
            activityLevel = savedStateHandle.get<String>(KEY_ACTIVITY)
                ?.let { runCatching { ActivityLevel.valueOf(it) }.getOrNull() }
                ?: ActivityLevel.MODERATE,
            isCustomized = savedStateHandle.get<Boolean>(KEY_IS_CUSTOMIZED) ?: false,
        ),
    )
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        recalculateGoals()
    }

    private fun persistDraft(state: OnboardingUiState) {
        savedStateHandle[KEY_STEP_INDEX] = state.stepIndex
        savedStateHandle[KEY_GOAL_TYPE] = state.goalType.name
        savedStateHandle[KEY_SEX] = state.sex.name
        savedStateHandle[KEY_AGE] = state.age
        savedStateHandle[KEY_HEIGHT] = state.heightCm
        savedStateHandle[KEY_CURRENT_WEIGHT] = state.currentWeightKg
        savedStateHandle[KEY_TARGET_WEIGHT] = state.targetWeightKg
        savedStateHandle[KEY_ACTIVITY] = state.activityLevel.name
        savedStateHandle[KEY_IS_CUSTOMIZED] = state.isCustomized
    }

    fun selectGoalType(goalType: GoalType) = updateAndRecalculate {
        val effectiveGoal = if (it.isAdolescentObservationMode) GoalType.MAINTAIN else goalType
        it.copy(
            goalType = effectiveGoal,
            targetWeightKg = if (effectiveGoal == GoalType.MAINTAIN) it.currentWeightKg else it.targetWeightKg,
        )
    }

    fun selectSex(sex: BiologicalSex) = updateAndRecalculate {
        it.copy(sex = sex)
    }

    fun adjustAge(delta: Int) = setAge(_uiState.value.age + delta)

    fun setAge(age: Int) = updateAndRecalculate {
        val safeAge = age.coerceIn(BodyMetricBounds.MIN_AGE, BodyMetricBounds.MAX_AGE)
        val isAdolescent = safeAge < BodyMetricBounds.ADULT_MIN_AGE
        it.copy(
            age = safeAge,
            goalType = if (isAdolescent) GoalType.MAINTAIN else it.goalType,
            targetWeightKg = if (isAdolescent) it.currentWeightKg else it.targetWeightKg,
            isCustomized = if (isAdolescent) false else it.isCustomized,
        )
    }

    fun adjustHeight(delta: Int) = setHeight(_uiState.value.heightCm + delta)

    fun setHeight(heightCm: Int) = updateAndRecalculate {
        it.copy(heightCm = heightCm.coerceIn(BodyMetricBounds.MIN_HEIGHT_CM, BodyMetricBounds.MAX_HEIGHT_CM))
    }

    fun adjustCurrentWeight(delta: Float) = setCurrentWeight(_uiState.value.currentWeightKg + delta)

    fun setCurrentWeight(weightKg: Float) {
        if (!weightKg.isFinite()) return
        updateAndRecalculate {
            val next = ((weightKg * 10f).roundToInt() / 10f)
                .coerceIn(BodyMetricBounds.MIN_WEIGHT_KG, BodyMetricBounds.MAX_WEIGHT_KG)
            it.copy(
                currentWeightKg = next,
                targetWeightKg = if (it.goalType == GoalType.MAINTAIN || it.isAdolescentObservationMode) next else it.targetWeightKg,
            )
        }
    }

    fun adjustTargetWeight(delta: Float) = setTargetWeight(_uiState.value.targetWeightKg + delta)

    fun setTargetWeight(weightKg: Float) {
        if (!weightKg.isFinite()) return
        _uiState.update {
            val next = ((weightKg * 10f).roundToInt() / 10f)
                .coerceIn(BodyMetricBounds.MIN_WEIGHT_KG, BodyMetricBounds.MAX_WEIGHT_KG)
            it.copy(targetWeightKg = next).also(::persistDraft)
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

    fun skipOnboarding(onFinished: () -> Unit) {
        val state = _uiState.value
        if (state.isCompleting) return
        _uiState.update { it.copy(isCompleting = true) }
        viewModelScope.launch {
            try {
                // Skipping onboarding MUST NOT insert a phantom default WeightLogEntity into Room!
                val effectiveGoalType = if (state.isAdolescentObservationMode) GoalType.MAINTAIN else state.goalType
                val profile = UserProfile(
                    sex = state.sex,
                    age = state.age,
                    heightCm = state.heightCm,
                    currentWeightKg = state.currentWeightKg,
                    targetWeightKg = if (state.isAdolescentObservationMode) state.currentWeightKg else state.targetWeightKg,
                    activityLevel = state.activityLevel,
                    goalType = effectiveGoalType,
                    goals = state.calculatedGoals,
                    onboardingCompleted = true,
                )
                preferencesDataStore.saveOnboardingProfile(profile)
                nutritionRepository.recordGoalHistory(
                    goals = profile.goals,
                    goalType = profile.goalType,
                    targetWeightKg = profile.targetWeightKg,
                    source = com.nutra.app.domain.model.GoalChangeSource.ONBOARDING,
                )
                onFinished()
            } finally {
                _uiState.update { it.copy(isCompleting = false) }
            }
        }
    }

    fun completeOnboarding(onFinished: () -> Unit) {
        val state = _uiState.value
        if (state.isCompleting) return
        _uiState.update { it.copy(isCompleting = true) }
        viewModelScope.launch {
            try {
                val effectiveGoalType = if (state.isAdolescentObservationMode) GoalType.MAINTAIN else state.goalType
                val effectiveGoals = if (state.isAdolescentObservationMode) state.calculatedGoals else state.customGoals
                val effectiveTargetWeight = if (state.isAdolescentObservationMode) state.currentWeightKg else state.targetWeightKg

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
                    targetWeightKg = effectiveTargetWeight,
                    activityLevel = state.activityLevel,
                    goalType = effectiveGoalType,
                    goals = effectiveGoals,
                    onboardingCompleted = true,
                )
                preferencesDataStore.saveOnboardingProfile(profile)
                nutritionRepository.recordGoalHistory(
                    goals = profile.goals,
                    goalType = profile.goalType,
                    targetWeightKg = profile.targetWeightKg,
                    source = com.nutra.app.domain.model.GoalChangeSource.ONBOARDING,
                )
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
                customGoals = if (updated.isCustomized && !updated.isAdolescentObservationMode) updated.customGoals else calc,
            ).also(::persistDraft)
        }
    }

    private fun recalculateGoals() {
        updateAndRecalculate { it }
    }
}
