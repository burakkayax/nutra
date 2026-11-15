package com.nutra.app.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutra.app.core.datastore.UserPreferencesDataStore
import com.nutra.app.domain.model.ActivityLevel
import com.nutra.app.domain.model.AppResult
import com.nutra.app.domain.model.BiologicalSex
import com.nutra.app.domain.model.GoalType
import com.nutra.app.domain.model.MacroGoals
import com.nutra.app.domain.model.ThemePreference
import com.nutra.app.domain.model.UserProfile
import com.nutra.app.domain.repository.NutritionRepository
import com.nutra.app.domain.usecase.NutritionCalculatorUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val profile: UserProfile = UserProfile(),
    val feedbackMessage: String? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val preferencesDataStore: UserPreferencesDataStore,
    private val nutritionRepository: NutritionRepository,
    private val calculatorUseCase: NutritionCalculatorUseCase,
) : ViewModel() {

    private val feedbackFlow = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ProfileUiState> = combine(
        preferencesDataStore.userProfileFlow,
        feedbackFlow,
    ) { profile, message ->
        ProfileUiState(
            profile = profile,
            feedbackMessage = message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ProfileUiState(),
    )

    fun updateGoals(goals: MacroGoals) {
        viewModelScope.launch {
            preferencesDataStore.updateGoals(goals)
            feedbackFlow.value = "Günlük hedefleriniz güncellendi"
        }
    }

    fun recalculateWithMifflinStJeor() {
        viewModelScope.launch {
            val p = preferencesDataStore.getSnapshot()
            val calculated = calculatorUseCase.calculateGoals(
                sex = p.sex,
                age = p.age,
                heightCm = p.heightCm,
                weightKg = p.currentWeightKg,
                activityLevel = p.activityLevel,
                goalType = p.goalType,
            )
            preferencesDataStore.updateGoals(calculated)
            feedbackFlow.value = "Mifflin-St Jeor formülüne göre hedefler yenilendi"
        }
    }

    fun updateBodyProfile(
        sex: BiologicalSex,
        age: Int,
        heightCm: Int,
        currentWeightKg: Float,
        targetWeightKg: Float,
        activityLevel: ActivityLevel,
        goalType: GoalType,
        recalculateGoals: Boolean,
    ) {
        viewModelScope.launch {
            val updatedGoals = if (recalculateGoals) {
                calculatorUseCase.calculateGoals(
                    sex = sex,
                    age = age,
                    heightCm = heightCm,
                    weightKg = currentWeightKg,
                    activityLevel = activityLevel,
                    goalType = goalType,
                )
            } else {
                null
            }
            preferencesDataStore.updateBodyMetricsAndGoal(
                sex = sex,
                age = age,
                heightCm = heightCm,
                currentWeightKg = currentWeightKg,
                targetWeightKg = targetWeightKg,
                activityLevel = activityLevel,
                goalType = goalType,
                updatedGoals = updatedGoals,
            )
            feedbackFlow.value = if (recalculateGoals) {
                "Vücut profili güncellendi ve hedefler yeniden hesaplandı"
            } else {
                "Vücut profili güncellendi"
            }
        }
    }

    fun updateAppearance(themePreference: ThemePreference, useDynamicColor: Boolean) {
        viewModelScope.launch {
            preferencesDataStore.updateAppearance(themePreference, useDynamicColor)
        }
    }

    fun saveGeminiConfig(apiKey: String, model: String) {
        viewModelScope.launch {
            preferencesDataStore.updateGeminiConfig(apiKey, model)
            feedbackFlow.value = "AI yapılandırması kaydedildi"
        }
    }

    fun exportBackupToCallback(onJsonReady: (String) -> Unit) {
        viewModelScope.launch {
            when (val res = nutritionRepository.exportBackupJson()) {
                is AppResult.Success -> {
                    onJsonReady(res.data)
                    feedbackFlow.value = "Yedek verisi hazırlandı"
                }
                is AppResult.Error -> {
                    feedbackFlow.value = res.message
                }
            }
        }
    }

    fun importBackupJson(rawJson: String) {
        viewModelScope.launch {
            when (val res = nutritionRepository.importBackupJson(rawJson)) {
                is AppResult.Success -> {
                    feedbackFlow.value = "Yedek başarıyla geri yüklendi"
                }
                is AppResult.Error -> {
                    feedbackFlow.value = res.message
                }
            }
        }
    }

    fun clearFeedback() {
        feedbackFlow.value = null
    }
}
