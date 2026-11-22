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
import com.nutra.app.domain.model.DietaryProfile
import com.nutra.app.domain.model.FoodFeedbackRecord
import com.nutra.app.domain.model.FoodFeedbackStatus
import com.nutra.app.domain.model.GoalChangeSource
import com.nutra.app.domain.model.GoalType
import com.nutra.app.domain.model.MacroGoals
import com.nutra.app.domain.model.ThemePreference
import com.nutra.app.domain.model.UserProfile
import com.nutra.app.domain.model.WaterReminderSettings
import com.nutra.app.domain.repository.BackupValidationReport
import com.nutra.app.domain.repository.NutritionRepository
import com.nutra.app.domain.usecase.BodyMetricBounds
import com.nutra.app.domain.usecase.MacroGoalBounds
import com.nutra.app.domain.usecase.NutritionCalculatorUseCase
import com.nutra.app.domain.usecase.TurkishTextNormalizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
import java.util.concurrent.atomic.AtomicReference
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
    val dislikedFoods: List<FoodFeedbackRecord> = emptyList(),
    val isExportingBackup: Boolean = false,
    val isImportingBackup: Boolean = false,
    val pendingImportReport: BackupValidationReport? = null,
    val feedback: ProfileFeedback? = null,
) {
    val isBackupBusy: Boolean
        get() = isExportingBackup || isImportingBackup
}

@Immutable
private data class PendingBodyProfile(
    val sex: BiologicalSex,
    val age: Int,
    val heightCm: Int,
    val currentWeightKg: Float,
    val targetWeightKg: Float,
    val activityLevel: ActivityLevel,
    val goalType: GoalType,
)

@Immutable
private data class PendingProfileOverrides(
    val goals: MacroGoals? = null,
    val body: PendingBodyProfile? = null,
    val waterReminder: WaterReminderSettings? = null,
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
    private val waterReminderMutex = Mutex()
    private val pendingEditCount = AtomicInteger(0)
    private val pendingBodyEditCount = AtomicInteger(0)
    private val pendingWaterReminderEditCount = AtomicInteger(0)
    private val pendingDailyGoalsTarget = AtomicReference<Pair<MacroGoals, GoalChangeSource>?>(null)
    private var latestOptimisticGoals: MacroGoals? = null
    private var calorieScalingBaseline: MacroGoals? = null
    private var lastCalorieScaledGoals: MacroGoals? = null
    private var previousGoalsBeforeRecalc: MacroGoals? = null

    private val pendingGoalsFlow = MutableStateFlow<MacroGoals?>(null)
    private val pendingBodyProfileFlow = MutableStateFlow<PendingBodyProfile?>(null)
    private val pendingWaterReminderFlow = MutableStateFlow<WaterReminderSettings?>(null)
    private val isExportingBackupFlow = MutableStateFlow(false)
    private val isImportingBackupFlow = MutableStateFlow(false)
    private val pendingImportReportFlow = MutableStateFlow<BackupValidationReport?>(null)
    private val feedbackSeq = AtomicLong(0L)
    private val feedbackFlow = MutableStateFlow<ProfileFeedback?>(null)

    private val initialUiState: ProfileUiState =
        preferencesDataStore.currentCachedProfile()?.let { cached ->
            ProfileUiState(isLoading = false, profile = cached)
        } ?: ProfileUiState()

    private val pendingOverridesFlow = combine(
        pendingGoalsFlow,
        pendingBodyProfileFlow,
        pendingWaterReminderFlow,
    ) { goals, body, waterReminder ->
        PendingProfileOverrides(
            goals = goals,
            body = body,
            waterReminder = waterReminder,
        )
    }

    private val backupStateFlow = combine(
        isExportingBackupFlow,
        isImportingBackupFlow,
        pendingImportReportFlow,
    ) { exporting, importing, report ->
        Triple(exporting, importing, report)
    }

    private val dislikedFoodsFlow = nutritionRepository.observeFoodFeedback()
        .map { list ->
            list.filter { it.status == FoodFeedbackStatus.DISLIKED }
                .sortedBy { TurkishTextNormalizer.normalize(it.foodName) }
        }
        .distinctUntilChanged()

    val uiState: StateFlow<ProfileUiState> = combine(
        preferencesDataStore.userProfileFlow,
        pendingOverridesFlow,
        dislikedFoodsFlow,
        backupStateFlow,
        feedbackFlow,
    ) { profile, overrides, dislikedFoods, (isExporting, isImporting, pendingReport), feedback ->
        val resolvedProfile = profile.let { base ->
            val withBody = if (overrides.body != null) {
                base.copy(
                    sex = overrides.body.sex,
                    age = overrides.body.age,
                    heightCm = overrides.body.heightCm,
                    currentWeightKg = overrides.body.currentWeightKg,
                    targetWeightKg = overrides.body.targetWeightKg,
                    activityLevel = overrides.body.activityLevel,
                    goalType = overrides.body.goalType,
                )
            } else {
                base
            }
            val withGoals = if (overrides.goals != null) withBody.copy(goals = overrides.goals) else withBody
            if (overrides.waterReminder != null) {
                withGoals.copy(waterReminder = overrides.waterReminder)
            } else {
                withGoals
            }
        }
        ProfileUiState(
            isLoading = false,
            profile = resolvedProfile,
            dislikedFoods = dislikedFoods,
            isExportingBackup = isExporting,
            isImportingBackup = isImporting,
            pendingImportReport = pendingReport,
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

    fun updateDailyGoals(
        goals: MacroGoals,
        source: GoalChangeSource = GoalChangeSource.MANUAL,
    ) {
        val safeGoals = MacroGoals(
            calories = goals.calories.coerceIn(profileGoalBounds.minCalories, profileGoalBounds.maxCalories),
            proteinGrams = goals.proteinGrams.coerceIn(profileGoalBounds.minProtein, profileGoalBounds.maxProtein),
            carbsGrams = goals.carbsGrams.coerceIn(profileGoalBounds.minCarbs, profileGoalBounds.maxCarbs),
            fatGrams = goals.fatGrams.coerceIn(profileGoalBounds.minFat, profileGoalBounds.maxFat),
            waterMl = goals.waterMl.coerceIn(profileGoalBounds.minWaterMl, profileGoalBounds.maxWaterMl),
        )
        pendingGoalsFlow.value = safeGoals
        pendingDailyGoalsTarget.set(safeGoals to source)
        launchGoalEdit {
            calorieScalingBaseline = null
            lastCalorieScaledGoals = null
            val (target, targetSource) = pendingDailyGoalsTarget.getAndSet(null) ?: return@launchGoalEdit
            val previousSnapshot = preferencesDataStore.getSnapshot()
            val prevGoals = previousSnapshot.goals
            val energyOrMacrosChanged = prevGoals.calories != target.calories ||
                prevGoals.proteinGrams != target.proteinGrams ||
                prevGoals.carbsGrams != target.carbsGrams ||
                prevGoals.fatGrams != target.fatGrams
            latestOptimisticGoals = target
            preferencesDataStore.updateGoals(target)
            // Do not pollute calorie goal_history when the user only edited their daily water target
            if (energyOrMacrosChanged) {
                nutritionRepository.recordGoalHistory(
                    goals = target,
                    goalType = previousSnapshot.goalType,
                    targetWeightKg = previousSnapshot.targetWeightKg,
                    source = targetSource,
                )
            }
        }
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

    private suspend fun persistGoalsLocked(
        updated: MacroGoals,
        historySource: GoalChangeSource? = null,
    ) {
        latestOptimisticGoals = updated
        pendingGoalsFlow.value = updated
        preferencesDataStore.updateGoals(updated)
        if (historySource != null) {
            val snapshot = preferencesDataStore.getSnapshot()
            nutritionRepository.recordGoalHistory(
                goals = updated,
                goalType = snapshot.goalType,
                targetWeightKg = snapshot.targetWeightKg,
                source = historySource,
            )
        }
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
            persistGoalsLocked(updated, historySource = GoalChangeSource.MANUAL)
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
        persistGoalsLocked(updated, historySource = GoalChangeSource.MANUAL)
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
            persistGoalsLocked(calculated, historySource = GoalChangeSource.FORMULA)
            val msg = if (p.isAdolescentObservationMode) {
                "Gözlem modu referans hedefleri yenilendi (kalori açığı kapalı)"
            } else {
                "Hedefler formüle göre yenilendi"
            }
            emitFeedback(
                message = msg,
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
            persistGoalsLocked(prev, historySource = GoalChangeSource.MANUAL)
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
        val safeAge = age.coerceIn(BodyMetricBounds.MIN_AGE, BodyMetricBounds.MAX_AGE)
        val isAdolescent = safeAge < BodyMetricBounds.ADULT_MIN_AGE
        val effectiveGoalType = if (isAdolescent) GoalType.MAINTAIN else goalType
        val safeCurrentWeight = (if (currentWeightKg.isFinite()) currentWeightKg else 75f)
            .coerceIn(BodyMetricBounds.MIN_WEIGHT_KG, BodyMetricBounds.MAX_WEIGHT_KG)
        val safeTargetWeight = if (isAdolescent) {
            safeCurrentWeight
        } else {
            (if (targetWeightKg.isFinite()) targetWeightKg else 70f)
                .coerceIn(BodyMetricBounds.MIN_WEIGHT_KG, BodyMetricBounds.MAX_WEIGHT_KG)
        }
        val optimisticBody = PendingBodyProfile(
            sex = sex,
            age = safeAge,
            heightCm = heightCm.coerceIn(BodyMetricBounds.MIN_HEIGHT_CM, BodyMetricBounds.MAX_HEIGHT_CM),
            currentWeightKg = safeCurrentWeight,
            targetWeightKg = safeTargetWeight,
            activityLevel = activityLevel,
            goalType = effectiveGoalType,
        )
        val updatedGoals = if (recalculateGoals) {
            calculatorUseCase.calculateGoals(
                sex = sex,
                age = safeAge,
                heightCm = optimisticBody.heightCm,
                weightKg = safeCurrentWeight,
                activityLevel = activityLevel,
                goalType = effectiveGoalType,
            )
        } else {
            null
        }
        pendingBodyEditCount.incrementAndGet()
        pendingBodyProfileFlow.value = optimisticBody
        if (updatedGoals != null) {
            pendingGoalsFlow.value = updatedGoals
        }
        launchGoalEdit {
            try {
                if (recalculateGoals) {
                    calorieScalingBaseline = null
                    lastCalorieScaledGoals = null
                }
                val previousWeight = preferencesDataStore.getSnapshot().currentWeightKg
                if (updatedGoals != null) {
                    latestOptimisticGoals = updatedGoals
                }
                preferencesDataStore.updateBodyMetricsAndGoal(
                    sex = sex,
                    age = safeAge,
                    heightCm = optimisticBody.heightCm,
                    currentWeightKg = safeCurrentWeight,
                    targetWeightKg = safeTargetWeight,
                    activityLevel = activityLevel,
                    goalType = effectiveGoalType,
                    updatedGoals = updatedGoals,
                )
                if (updatedGoals != null) {
                    nutritionRepository.recordGoalHistory(
                        goals = updatedGoals,
                        goalType = effectiveGoalType,
                        targetWeightKg = safeTargetWeight,
                        source = GoalChangeSource.FORMULA,
                    )
                }
                if (abs(safeCurrentWeight - previousWeight) >= 0.05f) {
                    nutritionRepository.upsertDailyWeight(
                        dateIso = LocalDate.now().toString(),
                        weightKg = safeCurrentWeight,
                        syncProfileWeight = false,
                    )
                }
            } finally {
                if (pendingBodyEditCount.decrementAndGet() == 0) {
                    pendingBodyProfileFlow.value = null
                }
            }
        }
    }

    fun updateAppearance(themePreference: ThemePreference, useDynamicColor: Boolean) {
        viewModelScope.launch {
            preferencesDataStore.updateAppearance(themePreference, useDynamicColor)
        }
    }

    fun updateDietaryProfile(dietaryProfile: DietaryProfile) {
        viewModelScope.launch {
            preferencesDataStore.updateDietaryProfile(dietaryProfile)
        }
    }

    fun updateAdaptiveMetabolismEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesDataStore.updateAdaptiveMetabolismEnabled(enabled)
        }
    }

    fun restoreDislikedFood(record: FoodFeedbackRecord) {
        viewModelScope.launch {
            nutritionRepository.setFoodDisliked(
                foodKey = record.foodKey,
                foodId = record.foodId,
                foodName = record.foodName,
                disliked = false,
            )
            emitFeedback("${record.foodName.ifBlank { "Besin" }} önerilere yeniden açıldı")
        }
    }

    fun updateWaterReminder(settings: WaterReminderSettings) {
        val sanitized = WaterReminderScheduleCalculator.sanitize(settings)
        pendingWaterReminderEditCount.incrementAndGet()
        pendingWaterReminderFlow.value = sanitized
        viewModelScope.launch {
            try {
                waterReminderMutex.withLock {
                    preferencesDataStore.updateWaterReminder(sanitized)
                    waterReminderScheduler.sync(sanitized)
                }
            } finally {
                if (pendingWaterReminderEditCount.decrementAndGet() == 0) {
                    pendingWaterReminderFlow.value = null
                }
            }
        }
    }

    fun exportBackupToUri(contentResolver: ContentResolver, uri: Uri) {
        if (isImportingBackupFlow.value || !isExportingBackupFlow.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val stream = runCatching { contentResolver.openOutputStream(uri, "wt") }.getOrNull()
                        ?: runCatching { contentResolver.openOutputStream(uri) }.getOrNull()
                    if (stream == null) {
                        AppResult.Error("Yedek dosyası açılamadı")
                    } else {
                        stream.use { out ->
                            nutritionRepository.exportBackupToStream(out)
                        }
                    }
                }
                when (result) {
                    is AppResult.Success -> emitFeedback("Yedek dosyası kaydedildi", NutraSnackbarTone.Success)
                    is AppResult.Error -> emitFeedback(result.message, NutraSnackbarTone.Error)
                }
            } finally {
                isExportingBackupFlow.value = false
            }
        }
    }

    fun prepareImportFromUri(contentResolver: ContentResolver, uri: Uri) {
        if (isExportingBackupFlow.value || !isImportingBackupFlow.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val stream = runCatching { contentResolver.openInputStream(uri) }.getOrNull()
                    if (stream == null) {
                        AppResult.Error("Yedek dosyası açılamadı")
                    } else {
                        stream.use { input ->
                            nutritionRepository.inspectBackupStream(input)
                        }
                    }
                }
                when (result) {
                    is AppResult.Success -> pendingImportReportFlow.value = result.data
                    is AppResult.Error -> emitFeedback(result.message, NutraSnackbarTone.Error)
                }
            } finally {
                isImportingBackupFlow.value = false
            }
        }
    }

    fun cancelPendingImport() {
        pendingImportReportFlow.value = null
    }

    fun confirmPendingImport() {
        val report = pendingImportReportFlow.value ?: return
        if (isExportingBackupFlow.value || !isImportingBackupFlow.compareAndSet(false, true)) return
        pendingImportReportFlow.value = null
        viewModelScope.launch {
            try {
                importBackupPayloadInternal(report)
            } finally {
                isImportingBackupFlow.value = false
            }
        }
    }

    fun importBackupFromUri(contentResolver: ContentResolver, uri: Uri) {
        if (isExportingBackupFlow.value || !isImportingBackupFlow.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                val inspected = withContext(Dispatchers.IO) {
                    val stream = runCatching { contentResolver.openInputStream(uri) }.getOrNull()
                    if (stream == null) {
                        AppResult.Error("Yedek dosyası okunamadı")
                    } else {
                        stream.use { input -> nutritionRepository.inspectBackupStream(input) }
                    }
                }
                when (inspected) {
                    is AppResult.Success -> importBackupPayloadInternal(inspected.data)
                    is AppResult.Error -> emitFeedback(inspected.message, NutraSnackbarTone.Error)
                }
            } finally {
                isImportingBackupFlow.value = false
            }
        }
    }

    private suspend fun importBackupPayloadInternal(report: BackupValidationReport) {
        goalsMutex.withLock {
            waterReminderMutex.withLock {
                when (val res = nutritionRepository.importBackupPayload(report.payload)) {
                    is AppResult.Success -> {
                        pendingDailyGoalsTarget.set(null)
                        latestOptimisticGoals = null
                        pendingGoalsFlow.value = null
                        pendingBodyProfileFlow.value = null
                        pendingWaterReminderFlow.value = null
                        calorieScalingBaseline = null
                        lastCalorieScaledGoals = null
                        previousGoalsBeforeRecalc = null
                        waterReminderScheduler.sync(preferencesDataStore.getSnapshot().waterReminder)
                        val msg = if (report.totalSkippedCount > 0) {
                            "Yedek geri yüklendi (${report.totalSkippedCount} geçersiz kayıt atlandı)"
                        } else {
                            "Yedek başarıyla geri yüklendi"
                        }
                        emitFeedback(msg, NutraSnackbarTone.Success)
                    }
                    is AppResult.Error -> {
                        emitFeedback(res.message, NutraSnackbarTone.Error)
                    }
                }
            }
        }
    }

    fun clearFeedback() {
        feedbackFlow.value = null
    }
}
