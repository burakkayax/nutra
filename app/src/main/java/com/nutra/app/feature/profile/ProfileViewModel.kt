package com.nutra.app.feature.profile

import android.content.ContentResolver
import android.net.Uri
import androidx.compose.runtime.Immutable
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
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlin.math.abs

@Immutable
data class ProfileUiState(
    val isLoading: Boolean = true,
    val profile: UserProfile = UserProfile(),
    val isBackupBusy: Boolean = false,
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
    private val pendingEditCount = AtomicInteger(0)
    private var latestOptimisticGoals: MacroGoals? = null
    private var calorieScalingBaseline: MacroGoals? = null
    private var lastCalorieScaledGoals: MacroGoals? = null

    private val pendingGoalsFlow = MutableStateFlow<MacroGoals?>(null)
    private val isBackupBusyFlow = MutableStateFlow(false)
    private val feedbackSeq = AtomicLong(0L)
    private val feedbackFlow = MutableStateFlow<Pair<String?, Long>>(null to 0L)

    val uiState: StateFlow<ProfileUiState> = combine(
        preferencesDataStore.userProfileFlow,
        pendingGoalsFlow,
        isBackupBusyFlow,
        feedbackFlow,
    ) { profile, pendingGoals, isBackupBusy, (message, eventId) ->
        ProfileUiState(
            isLoading = false,
            profile = if (pendingGoals != null) profile.copy(goals = pendingGoals) else profile,
            isBackupBusy = isBackupBusy,
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
        launchGoalEdit {
            val current = currentGoalsLocked()
            applyCalorieGoalLocked(current, current.calories + delta)
        }
    }

    fun setCalorieGoal(calories: Int) {
        launchGoalEdit {
            val current = currentGoalsLocked()
            applyCalorieGoalLocked(current, calories)
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
        launchGoalEdit {
            val current = currentGoalsLocked()
            applyWaterGoalLocked(current, current.waterMl + delta)
        }
    }

    fun setWaterGoal(waterMl: Int) {
        launchGoalEdit {
            val current = currentGoalsLocked()
            applyWaterGoalLocked(current, waterMl)
        }
    }

    private fun launchGoalEdit(block: suspend () -> Unit) {
        pendingEditCount.incrementAndGet()
        viewModelScope.launch {
            try {
                goalsMutex.withLock {
                    block()
                }
            } finally {
                if (pendingEditCount.decrementAndGet() == 0) {
                    latestOptimisticGoals = null
                    pendingGoalsFlow.value = null
                }
            }
        }
    }

    private suspend fun currentGoalsLocked(): MacroGoals =
        latestOptimisticGoals ?: preferencesDataStore.getSnapshot().goals

    private suspend fun persistGoalsLocked(updated: MacroGoals) {
        latestOptimisticGoals = updated
        pendingGoalsFlow.value = updated
        preferencesDataStore.updateGoals(updated)
    }

    private fun updateMacroGoal(transform: (MacroGoals) -> MacroGoals) {
        launchGoalEdit {
            calorieScalingBaseline = null
            lastCalorieScaledGoals = null
            val current = currentGoalsLocked()
            val updated = calculatorUseCase.recalculateCaloriesFromMacros(
                goals = transform(current),
                bounds = profileGoalBounds,
            )
            persistGoalsLocked(updated)
            emitFeedback("Kalori hedefi ${formatTrInt(updated.calories)} kcal olarak güncellendi")
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
        persistGoalsLocked(updated)
        emitFeedback("Makro hedefleri ${formatTrInt(updated.calories)} kcal için güncellendi")
    }

    private suspend fun applyWaterGoalLocked(current: MacroGoals, waterMl: Int) {
        val nextWater = waterMl.coerceIn(profileGoalBounds.minWaterMl, profileGoalBounds.maxWaterMl)
        val updated = current.copy(waterMl = nextWater)
        calorieScalingBaseline = calorieScalingBaseline?.copy(waterMl = nextWater)
        lastCalorieScaledGoals = lastCalorieScaledGoals?.copy(waterMl = nextWater)
        persistGoalsLocked(updated)
        emitFeedback("Su hedefi güncellendi")
    }

    fun recalculateWithMifflinStJeor() {
        launchGoalEdit {
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
            persistGoalsLocked(calculated)
            emitFeedback("Mifflin-St Jeor formülüne göre hedefler yenilendi")
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
        launchGoalEdit {
            if (recalculateGoals) {
                calorieScalingBaseline = null
                lastCalorieScaledGoals = null
            }
            val previousWeight = preferencesDataStore.getSnapshot().currentWeightKg
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
            if (updatedGoals != null) {
                latestOptimisticGoals = updatedGoals
                pendingGoalsFlow.value = updatedGoals
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
            if (abs(currentWeightKg - previousWeight) >= 0.05f) {
                nutritionRepository.logWeight(LocalDate.now().toString(), currentWeightKg)
            }
            emitFeedback(
                if (recalculateGoals) {
                    "Vücut profili güncellendi ve hedefler yeniden hesaplandı"
                } else {
                    "Vücut profili güncellendi"
                },
            )
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
        if (isBackupBusyFlow.value) return
        isBackupBusyFlow.value = true
        viewModelScope.launch {
            try {
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
            } finally {
                isBackupBusyFlow.value = false
            }
        }
    }

    fun importBackupFromUri(contentResolver: ContentResolver, uri: Uri) {
        if (isBackupBusyFlow.value) return
        isBackupBusyFlow.value = true
        viewModelScope.launch {
            try {
                val raw = withContext(Dispatchers.IO) {
                    runCatching {
                        contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    }.getOrNull()
                }
                if (raw.isNullOrBlank()) {
                    emitFeedback("Yedek dosyası okunamadı")
                    return@launch
                }
                importBackupJsonInternal(raw)
            } finally {
                isBackupBusyFlow.value = false
            }
        }
    }

    private suspend fun importBackupJsonInternal(rawJson: String) {
        goalsMutex.withLock {
            when (val res = nutritionRepository.importBackupJson(rawJson)) {
                is AppResult.Success -> {
                    latestOptimisticGoals = null
                    pendingGoalsFlow.value = null
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

    fun clearFeedback() {
        feedbackFlow.value = null to feedbackSeq.get()
    }
}
