package com.nutra.app.feature.profile

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutra.app.core.datastore.UserPreferencesDataStore
import com.nutra.app.core.designsystem.NutraFormatters
import com.nutra.app.domain.model.ActivityLevel
import com.nutra.app.domain.model.AppResult
import com.nutra.app.domain.model.BiologicalSex
import com.nutra.app.domain.model.GoalType
import com.nutra.app.domain.model.MacroGoals
import com.nutra.app.domain.model.ThemePreference
import com.nutra.app.domain.model.UserProfile
import com.nutra.app.domain.repository.NutritionRepository
import com.nutra.app.domain.usecase.MacroGoalBounds
import com.nutra.app.domain.usecase.NutritionCalculatorUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

data class ProfileUiState(
    val isLoading: Boolean = true,
    val profile: UserProfile = UserProfile(),
    val feedbackMessage: String? = null,
    val feedbackEventId: Long = 0L,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val preferencesDataStore: UserPreferencesDataStore,
    private val nutritionRepository: NutritionRepository,
    private val calculatorUseCase: NutritionCalculatorUseCase,
) : ViewModel() {

    private val profileGoalBounds = MacroGoalBounds()
    private val goalsMutex = Mutex()
    private var calorieScalingBaseline: MacroGoals? = null
    private var lastCalorieScaledGoals: MacroGoals? = null

    private val feedbackSeq = AtomicLong(0L)
    private val feedbackFlow = MutableStateFlow<Pair<String?, Long>>(null to 0L)

    val uiState: StateFlow<ProfileUiState> = combine(
        preferencesDataStore.userProfileFlow,
        feedbackFlow,
    ) { profile, (message, eventId) ->
        ProfileUiState(
            isLoading = false,
            profile = profile,
            feedbackMessage = message,
            feedbackEventId = eventId,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(30_000),
        initialValue = ProfileUiState(),
    )

    private fun emitFeedback(message: String) {
        feedbackFlow.value = message to feedbackSeq.incrementAndGet()
    }

    fun adjustCalorieGoal(delta: Int) {
        viewModelScope.launch {
            goalsMutex.withLock {
                val current = preferencesDataStore.getSnapshot().goals
                applyCalorieGoalLocked(current, current.calories + delta)
            }
        }
    }

    fun setCalorieGoal(calories: Int) {
        viewModelScope.launch {
            goalsMutex.withLock {
                val current = preferencesDataStore.getSnapshot().goals
                applyCalorieGoalLocked(current, calories)
            }
        }
    }

    fun adjustProteinGoal(delta: Int) = updateMacroGoal {
        it.copy(proteinGrams = it.proteinGrams + delta)
    }

    fun setProteinGoal(grams: Int) = updateMacroGoal {
        it.copy(proteinGrams = grams)
    }

    fun adjustCarbsGoal(delta: Int) = updateMacroGoal {
        it.copy(carbsGrams = it.carbsGrams + delta)
    }

    fun setCarbsGoal(grams: Int) = updateMacroGoal {
        it.copy(carbsGrams = grams)
    }

    fun adjustFatGoal(delta: Int) = updateMacroGoal {
        it.copy(fatGrams = it.fatGrams + delta)
    }

    fun setFatGoal(grams: Int) = updateMacroGoal {
        it.copy(fatGrams = grams)
    }

    fun adjustWaterGoal(delta: Int) {
        viewModelScope.launch {
            goalsMutex.withLock {
                val current = preferencesDataStore.getSnapshot().goals
                applyWaterGoalLocked(current, current.waterMl + delta)
            }
        }
    }

    fun setWaterGoal(waterMl: Int) {
        viewModelScope.launch {
            goalsMutex.withLock {
                val current = preferencesDataStore.getSnapshot().goals
                applyWaterGoalLocked(current, waterMl)
            }
        }
    }

    private fun updateMacroGoal(transform: (MacroGoals) -> MacroGoals) {
        viewModelScope.launch {
            goalsMutex.withLock {
                calorieScalingBaseline = null
                lastCalorieScaledGoals = null
                val current = preferencesDataStore.getSnapshot().goals
                val updated = calculatorUseCase.recalculateCaloriesFromMacros(
                    goals = transform(current),
                    bounds = profileGoalBounds,
                )
                preferencesDataStore.updateGoals(updated)
                emitFeedback("Kalori hedefi ${formatTrInt(updated.calories)} kcal olarak güncellendi")
            }
        }
    }

    private suspend fun applyCalorieGoalLocked(current: MacroGoals, targetCalories: Int) {
        if (lastCalorieScaledGoals != current) {
            calorieScalingBaseline = current
        }
        val baseline = calorieScalingBaseline ?: current.also { calorieScalingBaseline = it }
        val updated = calculatorUseCase.scaleMacrosForCalorieGoal(
            baselineGoals = baseline,
            targetCalories = targetCalories,
            bounds = profileGoalBounds,
        ).copy(waterMl = current.waterMl)
        lastCalorieScaledGoals = updated
        preferencesDataStore.updateGoals(updated)
        emitFeedback("Makro hedefleri ${formatTrInt(updated.calories)} kcal için güncellendi")
    }

    private suspend fun applyWaterGoalLocked(current: MacroGoals, waterMl: Int) {
        val nextWater = waterMl.coerceIn(profileGoalBounds.minWaterMl, profileGoalBounds.maxWaterMl)
        val updated = current.copy(waterMl = nextWater)
        calorieScalingBaseline = calorieScalingBaseline?.copy(waterMl = nextWater)
        lastCalorieScaledGoals = lastCalorieScaledGoals?.copy(waterMl = nextWater)
        preferencesDataStore.updateGoals(updated)
        emitFeedback("Su hedefi güncellendi")
    }

    fun recalculateWithMifflinStJeor() {
        viewModelScope.launch {
            goalsMutex.withLock {
                calorieScalingBaseline = null
                lastCalorieScaledGoals = null
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
                emitFeedback("Mifflin-St Jeor formülüne göre hedefler yenilendi")
            }
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
            goalsMutex.withLock {
                if (recalculateGoals) {
                    calorieScalingBaseline = null
                    lastCalorieScaledGoals = null
                }
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
                emitFeedback(
                    if (recalculateGoals) {
                        "Vücut profili güncellendi ve hedefler yeniden hesaplandı"
                    } else {
                        "Vücut profili güncellendi"
                    },
                )
            }
        }
    }

    private fun formatTrInt(value: Int): String = NutraFormatters.formatInt(value)

    fun updateAppearance(themePreference: ThemePreference, useDynamicColor: Boolean) {
        viewModelScope.launch {
            preferencesDataStore.updateAppearance(themePreference, useDynamicColor)
        }
    }

    fun saveGeminiConfig(apiKey: String, model: String) {
        viewModelScope.launch {
            preferencesDataStore.updateGeminiConfig(apiKey, model)
            emitFeedback("AI yapılandırması kaydedildi")
        }
    }

    fun exportBackupToUri(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            when (val res = nutritionRepository.exportBackupJson()) {
                is AppResult.Success -> {
                    val writeResult = withContext(Dispatchers.IO) {
                        runCatching {
                            val stream = runCatching { contentResolver.openOutputStream(uri, "wt") }.getOrNull()
                                ?: contentResolver.openOutputStream(uri)
                                ?: error("Dosya açılamadı")
                            stream.use { out ->
                                out.write(res.data.toByteArray(Charsets.UTF_8))
                            }
                        }
                    }
                    emitFeedback(
                        if (writeResult.isSuccess) {
                            "Yedek dosyası kaydedildi"
                        } else {
                            "Yedek dosyası yazılamadı"
                        },
                    )
                }
                is AppResult.Error -> {
                    emitFeedback(res.message)
                }
            }
        }
    }

    fun importBackupFromUri(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            val raw = withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
            }
            if (raw.isNullOrBlank()) {
                emitFeedback("Yedek dosyası okunamadı")
                return@launch
            }
            importBackupJson(raw)
        }
    }

    fun importBackupJson(rawJson: String) {
        viewModelScope.launch {
            goalsMutex.withLock {
                when (val res = nutritionRepository.importBackupJson(rawJson)) {
                    is AppResult.Success -> {
                        calorieScalingBaseline = null
                        lastCalorieScaledGoals = null
                        emitFeedback("Yedek başarıyla geri yüklendi")
                    }
                    is AppResult.Error -> {
                        emitFeedback(res.message)
                    }
                }
            }
        }
    }

    fun clearFeedback() {
        feedbackFlow.value = null to feedbackSeq.get()
    }
}
