package com.nutra.app.feature.profile

import android.content.ContentResolver
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutra.app.core.datastore.UserPreferencesDataStore
import com.nutra.app.core.designsystem.NutraSnackbarTone
import com.nutra.app.core.notification.WaterReminderScheduleCalculator
import com.nutra.app.core.notification.WaterReminderScheduler
import com.nutra.app.domain.model.ActivityLevel
import com.nutra.app.domain.model.AppResult
import com.nutra.app.domain.model.BiologicalSex
import com.nutra.app.domain.model.GoalType
import com.nutra.app.domain.model.MacroGoals
import com.nutra.app.domain.model.ThemePreference
import com.nutra.app.domain.model.UserProfile
import com.nutra.app.domain.model.WaterReminderSettings
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
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlin.math.abs

internal const val MAX_BACKUP_FILE_BYTES = 20 * 1024 * 1024

internal sealed interface BoundedReadResult {
    data class Success(val text: String) : BoundedReadResult
    data object TooLarge : BoundedReadResult
    data object EmptyOrFailed : BoundedReadResult
}

internal fun readBoundedUtf8Stream(
    inputStream: InputStream,
    maxBytes: Int = MAX_BACKUP_FILE_BYTES,
): BoundedReadResult {
    val buffer = ByteArray(16 * 1024)
    val output = ByteArrayOutputStream(8 * 1024)
    var totalRead = 0
    while (true) {
        val count = inputStream.read(buffer)
        if (count < 0) break
        if (count == 0) continue
        totalRead += count
        if (totalRead > maxBytes) {
            return BoundedReadResult.TooLarge
        }
        output.write(buffer, 0, count)
    }
    if (totalRead == 0) return BoundedReadResult.EmptyOrFailed
    val text = output.toString(Charsets.UTF_8.name())
    return if (text.isBlank()) BoundedReadResult.EmptyOrFailed else BoundedReadResult.Success(text)
}

@Immutable
data class ProfileFeedback(
    val message: String,
    val tone: NutraSnackbarTone = NutraSnackbarTone.Success,
    val eventId: Long = 0L,
    val canUndoGoals: Boolean = false,
)

@Immutable
data class ProfileUiState(
    val isLoading: Boolean = true,
    val profile: UserProfile = UserProfile(),
    val isBackupBusy: Boolean = false,
    val feedback: ProfileFeedback? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val preferencesDataStore: UserPreferencesDataStore,
    private val nutritionRepository: NutritionRepository,
    private val calculatorUseCase: NutritionCalculatorUseCase,
    private val waterReminderScheduler: WaterReminderScheduler,
) : ViewModel() {

    private val profileGoalBounds = MacroGoalBounds()
    private val goalsMutex = Mutex()
    private val pendingEditCount = AtomicInteger(0)
    private var latestOptimisticGoals: MacroGoals? = null
    private var calorieScalingBaseline: MacroGoals? = null
    private var lastCalorieScaledGoals: MacroGoals? = null
    private var previousGoalsBeforeRecalc: MacroGoals? = null

    private val pendingGoalsFlow = MutableStateFlow<MacroGoals?>(null)
    private val isBackupBusyFlow = MutableStateFlow(false)
    private val feedbackSeq = AtomicLong(0L)
    private val feedbackFlow = MutableStateFlow<ProfileFeedback?>(null)

    private val initialUiState: ProfileUiState =
        preferencesDataStore.currentCachedProfile()?.let { cached ->
            ProfileUiState(isLoading = false, profile = cached)
        } ?: ProfileUiState()

    val uiState: StateFlow<ProfileUiState> = combine(
        preferencesDataStore.userProfileFlow,
        pendingGoalsFlow,
        isBackupBusyFlow,
        feedbackFlow,
    ) { profile, pendingGoals, isBackupBusy, feedback ->
        ProfileUiState(
            isLoading = false,
            profile = if (pendingGoals != null) profile.copy(goals = pendingGoals) else profile,
            isBackupBusy = isBackupBusy,
            feedback = feedback,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(30_000),
        initialValue = initialUiState,
    )

    private fun emitFeedback(
        message: String,
        tone: NutraSnackbarTone = NutraSnackbarTone.Success,
        canUndoGoals: Boolean = false,
    ) {
        feedbackFlow.value = ProfileFeedback(
            message = message,
            tone = tone,
            eventId = feedbackSeq.incrementAndGet(),
            canUndoGoals = canUndoGoals,
        )
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
    }

    private suspend fun applyWaterGoalLocked(current: MacroGoals, waterMl: Int) {
        val nextWater = waterMl.coerceIn(profileGoalBounds.minWaterMl, profileGoalBounds.maxWaterMl)
        val updated = current.copy(waterMl = nextWater)
        calorieScalingBaseline = calorieScalingBaseline?.copy(waterMl = nextWater)
        lastCalorieScaledGoals = lastCalorieScaledGoals?.copy(waterMl = nextWater)
        persistGoalsLocked(updated)
    }

    fun recalculateWithMifflinStJeor() {
        launchGoalEdit {
            calorieScalingBaseline = null
            lastCalorieScaledGoals = null
            val p = preferencesDataStore.getSnapshot()
            previousGoalsBeforeRecalc = currentGoalsLocked()
            val calculated = calculatorUseCase.calculateGoals(
                sex = p.sex,
                age = p.age,
                heightCm = p.heightCm,
                weightKg = p.currentWeightKg,
                activityLevel = p.activityLevel,
                goalType = p.goalType,
            )
            persistGoalsLocked(calculated)
            emitFeedback(
                message = "Hedefler formüle göre yenilendi",
                tone = NutraSnackbarTone.Success,
                canUndoGoals = true,
            )
        }
    }

    fun undoRecalculateGoals() {
        val prev = previousGoalsBeforeRecalc ?: return
        previousGoalsBeforeRecalc = null
        launchGoalEdit {
            calorieScalingBaseline = null
            lastCalorieScaledGoals = null
            persistGoalsLocked(prev)
            clearFeedback()
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
                nutritionRepository.logWeight(
                    dateIso = LocalDate.now().toString(),
                    weightKg = currentWeightKg,
                    syncProfileWeight = false,
                )
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

    fun updateAppearance(themePreference: ThemePreference, useDynamicColor: Boolean) {
        viewModelScope.launch {
            preferencesDataStore.updateAppearance(themePreference, useDynamicColor)
        }
    }

    fun updateWaterReminder(settings: WaterReminderSettings) {
        viewModelScope.launch {
            val previous = preferencesDataStore.getSnapshot().waterReminder
            val sanitized = WaterReminderScheduleCalculator.sanitize(settings)
            preferencesDataStore.updateWaterReminder(sanitized)
            waterReminderScheduler.sync(sanitized)
            val message = when {
                !sanitized.enabled -> "Su hatırlatıcı kapatıldı"
                !previous.enabled -> "Su hatırlatıcı açıldı"
                else -> "Su hatırlatıcı güncellendi"
            }
            emitFeedback(message)
        }
    }

    fun saveGeminiConfig(apiKey: String, model: String) {
        viewModelScope.launch {
            preferencesDataStore.updateGeminiConfig(apiKey, model)
            emitFeedback("AI yapılandırması kaydedildi")
        }
    }

    fun exportBackupToUri(contentResolver: ContentResolver, uri: Uri) {
        if (!isBackupBusyFlow.compareAndSet(false, true)) return
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
                        if (writeResult.isSuccess) {
                            emitFeedback("Yedek dosyası kaydedildi", NutraSnackbarTone.Success)
                        } else {
                            emitFeedback("Yedek dosyası yazılamadı", NutraSnackbarTone.Error)
                        }
                    }
                    is AppResult.Error -> {
                        emitFeedback(res.message, NutraSnackbarTone.Error)
                    }
                }
            } finally {
                isBackupBusyFlow.value = false
            }
        }
    }

    fun importBackupFromUri(contentResolver: ContentResolver, uri: Uri) {
        if (!isBackupBusyFlow.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                val readResult = withContext(Dispatchers.IO) {
                    runCatching {
                        contentResolver.openInputStream(uri)?.use { stream ->
                            readBoundedUtf8Stream(stream, MAX_BACKUP_FILE_BYTES)
                        } ?: BoundedReadResult.EmptyOrFailed
                    }.getOrDefault(BoundedReadResult.EmptyOrFailed)
                }
                when (readResult) {
                    BoundedReadResult.TooLarge -> {
                        emitFeedback("Yedek dosyası çok büyük (en fazla 20 MB).", NutraSnackbarTone.Error)
                        return@launch
                    }
                    BoundedReadResult.EmptyOrFailed -> {
                        emitFeedback("Yedek dosyası okunamadı", NutraSnackbarTone.Error)
                        return@launch
                    }
                    is BoundedReadResult.Success -> {
                        importBackupJsonInternal(readResult.text)
                    }
                }
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
                    previousGoalsBeforeRecalc = null
                    waterReminderScheduler.sync(preferencesDataStore.getSnapshot().waterReminder)
                    emitFeedback("Yedek başarıyla geri yüklendi", NutraSnackbarTone.Success)
                }
                is AppResult.Error -> {
                    emitFeedback(res.message, NutraSnackbarTone.Error)
                }
            }
        }
    }

    fun clearFeedback() {
        feedbackFlow.value = null
    }
}
