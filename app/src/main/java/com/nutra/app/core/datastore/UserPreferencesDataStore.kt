package com.nutra.app.core.datastore

import android.app.UiModeManager
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nutra.app.core.notification.WaterReminderScheduleCalculator
import com.nutra.app.domain.model.ActivityLevel
import com.nutra.app.domain.model.BiologicalSex
import com.nutra.app.domain.model.DietaryProfile
import com.nutra.app.domain.model.GoalType
import com.nutra.app.domain.model.MacroGoals
import com.nutra.app.domain.model.ThemePreference
import com.nutra.app.domain.model.UserProfile
import com.nutra.app.domain.model.WaterReminderSettings
import com.nutra.app.domain.usecase.TurkishTextNormalizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.nutraDataStore: DataStore<Preferences> by preferencesDataStore(name = "nutra_preferences")

internal const val MAX_EXCLUDED_KEYWORDS = 40

sealed interface ProfileStorageState {
    data object Loading : ProfileStorageState
    data class Loaded(val profile: UserProfile) : ProfileStorageState
    data class ReadError(val message: String) : ProfileStorageState
}

internal fun sanitizeDietaryProfileData(profile: DietaryProfile): DietaryProfile = profile.copy(
    excludedKeywords = profile.excludedKeywords
        .flatMap { it.split(',', ';') }
        .map { it.trim() }
        .filter { TurkishTextNormalizer.normalize(it).length >= 2 }
        .distinctBy { TurkishTextNormalizer.normalize(it) }
        .take(MAX_EXCLUDED_KEYWORDS),
)

internal fun sanitizeMacroGoals(goals: MacroGoals): MacroGoals = MacroGoals(
    calories = goals.calories.coerceIn(1000, 6000),
    proteinGrams = goals.proteinGrams.coerceIn(30, 400),
    carbsGrams = goals.carbsGrams.coerceIn(20, 600),
    fatGrams = goals.fatGrams.coerceIn(20, 250),
    waterMl = goals.waterMl.coerceIn(1000, 7000),
)

@Singleton
open class UserPreferencesDataStore internal constructor(
    private val context: Context,
    private val dataStore: DataStore<Preferences>,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context, context.nutraDataStore)

    private val dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private object Keys {
        val SEX = stringPreferencesKey("sex")
        val AGE = intPreferencesKey("age")
        val HEIGHT_CM = intPreferencesKey("height_cm")
        val CURRENT_WEIGHT_KG = floatPreferencesKey("current_weight_kg")
        val TARGET_WEIGHT_KG = floatPreferencesKey("target_weight_kg")
        val ACTIVITY_LEVEL = stringPreferencesKey("activity_level")
        val GOAL_TYPE = stringPreferencesKey("goal_type")

        val GOAL_CALORIES = intPreferencesKey("goal_calories")
        val GOAL_PROTEIN = intPreferencesKey("goal_protein")
        val GOAL_CARBS = intPreferencesKey("goal_carbs")
        val GOAL_FAT = intPreferencesKey("goal_fat")
        val GOAL_WATER_ML = intPreferencesKey("goal_water_ml")

        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val THEME_PREFERENCE = stringPreferencesKey("theme_preference")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")

        val WATER_REMINDER_ENABLED = booleanPreferencesKey("water_reminder_enabled")
        val WATER_REMINDER_INTERVAL_MINUTES = intPreferencesKey("water_reminder_interval_minutes")
        val WATER_REMINDER_START_HOUR = intPreferencesKey("water_reminder_start_hour")
        val WATER_REMINDER_START_MINUTE = intPreferencesKey("water_reminder_start_minute")
        val WATER_REMINDER_END_HOUR = intPreferencesKey("water_reminder_end_hour")
        val WATER_REMINDER_END_MINUTE = intPreferencesKey("water_reminder_end_minute")
        val WATER_REMINDER_STOP_ON_GOAL = booleanPreferencesKey("water_reminder_stop_on_goal")

        val DIETARY_PROFILE_JSON = stringPreferencesKey("dietary_profile_json")
        val ADAPTIVE_METABOLISM_ENABLED = booleanPreferencesKey("adaptive_metabolism_enabled")
        val LAST_ADAPTIVE_REVIEW_DATE_ISO = stringPreferencesKey("last_adaptive_review_date_iso")
    }

    private companion object {
        val SEX_BY_NAME: Map<String, BiologicalSex> = BiologicalSex.entries.associateBy { it.name }
        val ACTIVITY_BY_NAME: Map<String, ActivityLevel> = ActivityLevel.entries.associateBy { it.name }
        val GOAL_BY_NAME: Map<String, GoalType> = GoalType.entries.associateBy { it.name }
        val THEME_BY_NAME: Map<String, ThemePreference> = ThemePreference.entries.associateBy { it.name }
        val prefsJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    }

    private val _storageState = MutableStateFlow<ProfileStorageState>(ProfileStorageState.Loading)
    val profileStorageState: StateFlow<ProfileStorageState> = _storageState.asStateFlow()

    @Volatile
    private var lastKnownGoodProfile: UserProfile? = null

    private val sharedProfileFlow: SharedFlow<UserProfile> = dataStore.data
        .retryWhen { cause, attempt ->
            if (cause is IOException && attempt < 3L) {
                delay(120L * (1L shl attempt.toInt()))
                true
            } else {
                false
            }
        }
        .map { prefs -> parseUserProfile(prefs) }
        .onEach { profile ->
            lastKnownGoodProfile = profile
            _storageState.value = ProfileStorageState.Loaded(profile)
        }
        .catch { exception ->
            if (exception is IOException) {
                val cached = lastKnownGoodProfile
                if (cached != null) {
                    // Never downgrade an already loaded user back to onboarding on a transient I/O blip
                    _storageState.value = ProfileStorageState.Loaded(cached)
                    emit(cached)
                } else {
                    _storageState.value = ProfileStorageState.ReadError(
                        "Profil tercihleri okunamadı. Depolama alanını kontrol edip tekrar dene.",
                    )
                }
            } else {
                throw exception
            }
        }
        .distinctUntilChanged()
        .shareIn(
            scope = dataStoreScope,
            started = SharingStarted.Eagerly,
            replay = 1,
        )

    val userProfileFlow: Flow<UserProfile> = sharedProfileFlow

    fun currentCachedProfile(): UserProfile? = sharedProfileFlow.replayCache.firstOrNull() ?: lastKnownGoodProfile

    suspend fun retryReadProfile(): Boolean = runCatching {
        val prefs = dataStore.data.first()
        val parsed = parseUserProfile(prefs)
        lastKnownGoodProfile = parsed
        _storageState.value = ProfileStorageState.Loaded(parsed)
        true
    }.getOrDefault(false)

    private fun parseUserProfile(prefs: Preferences): UserProfile {
        val sex = prefs[Keys.SEX]?.let { SEX_BY_NAME[it] } ?: BiologicalSex.MALE
        val activityLevel = prefs[Keys.ACTIVITY_LEVEL]?.let { ACTIVITY_BY_NAME[it] } ?: ActivityLevel.MODERATE
        val goalType = prefs[Keys.GOAL_TYPE]?.let { GOAL_BY_NAME[it] } ?: GoalType.LOSE_WEIGHT
        val themePref = prefs[Keys.THEME_PREFERENCE]?.let { THEME_BY_NAME[it] } ?: ThemePreference.SYSTEM

        val goals = sanitizeMacroGoals(
            MacroGoals(
                calories = prefs[Keys.GOAL_CALORIES] ?: 2100,
                proteinGrams = prefs[Keys.GOAL_PROTEIN] ?: 140,
                carbsGrams = prefs[Keys.GOAL_CARBS] ?: 220,
                fatGrams = prefs[Keys.GOAL_FAT] ?: 70,
                waterMl = prefs[Keys.GOAL_WATER_ML] ?: 2500,
            ),
        )

        val waterReminder = WaterReminderScheduleCalculator.sanitize(
            WaterReminderSettings(
                enabled = prefs[Keys.WATER_REMINDER_ENABLED] ?: false,
                intervalMinutes = prefs[Keys.WATER_REMINDER_INTERVAL_MINUTES] ?: 120,
                startHour = prefs[Keys.WATER_REMINDER_START_HOUR] ?: 9,
                startMinute = prefs[Keys.WATER_REMINDER_START_MINUTE] ?: 0,
                endHour = prefs[Keys.WATER_REMINDER_END_HOUR] ?: 22,
                endMinute = prefs[Keys.WATER_REMINDER_END_MINUTE] ?: 0,
                stopWhenGoalReached = prefs[Keys.WATER_REMINDER_STOP_ON_GOAL] ?: true,
            ),
        )

        val dietaryProfile = prefs[Keys.DIETARY_PROFILE_JSON]
            ?.takeIf { it.isNotBlank() }
            ?.let { raw -> runCatching { sanitizeDietaryProfileData(prefsJson.decodeFromString<DietaryProfile>(raw)) }.getOrNull() }
            ?: DietaryProfile()

        return UserProfile(
            sex = sex,
            age = (prefs[Keys.AGE] ?: 28).coerceIn(14, 100),
            heightCm = (prefs[Keys.HEIGHT_CM] ?: 176).coerceIn(120, 230),
            currentWeightKg = sanitizeWeightKg(prefs[Keys.CURRENT_WEIGHT_KG] ?: 75f, 75f),
            targetWeightKg = sanitizeWeightKg(prefs[Keys.TARGET_WEIGHT_KG] ?: 70f, 70f),
            activityLevel = activityLevel,
            goalType = goalType,
            goals = goals,
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            themePreference = themePref,
            useDynamicColor = prefs[Keys.USE_DYNAMIC_COLOR] ?: false,
            waterReminder = waterReminder,
            dietaryProfile = dietaryProfile,
            adaptiveMetabolismEnabled = prefs[Keys.ADAPTIVE_METABOLISM_ENABLED] ?: true,
            lastAdaptiveReviewDateIso = prefs[Keys.LAST_ADAPTIVE_REVIEW_DATE_ISO]?.takeIf { it.isNotBlank() },
        )
    }

    suspend fun getSnapshot(): UserProfile = sharedProfileFlow.first()

    suspend fun saveOnboardingProfile(profile: UserProfile) {
        val safeGoals = sanitizeMacroGoals(profile.goals)
        dataStore.edit { prefs ->
            prefs[Keys.SEX] = profile.sex.name
            prefs[Keys.AGE] = profile.age.coerceIn(14, 100)
            prefs[Keys.HEIGHT_CM] = profile.heightCm.coerceIn(120, 230)
            prefs[Keys.CURRENT_WEIGHT_KG] = sanitizeWeightKg(profile.currentWeightKg)
            prefs[Keys.TARGET_WEIGHT_KG] = sanitizeWeightKg(profile.targetWeightKg)
            prefs[Keys.ACTIVITY_LEVEL] = profile.activityLevel.name
            prefs[Keys.GOAL_TYPE] = profile.goalType.name
            prefs[Keys.GOAL_CALORIES] = safeGoals.calories
            prefs[Keys.GOAL_PROTEIN] = safeGoals.proteinGrams
            prefs[Keys.GOAL_CARBS] = safeGoals.carbsGrams
            prefs[Keys.GOAL_FAT] = safeGoals.fatGrams
            prefs[Keys.GOAL_WATER_ML] = safeGoals.waterMl
            prefs[Keys.ONBOARDING_COMPLETED] = true
        }
    }

    suspend fun updateGoals(goals: MacroGoals) {
        val safeGoals = sanitizeMacroGoals(goals)
        dataStore.edit { prefs ->
            prefs[Keys.GOAL_CALORIES] = safeGoals.calories
            prefs[Keys.GOAL_PROTEIN] = safeGoals.proteinGrams
            prefs[Keys.GOAL_CARBS] = safeGoals.carbsGrams
            prefs[Keys.GOAL_FAT] = safeGoals.fatGrams
            prefs[Keys.GOAL_WATER_ML] = safeGoals.waterMl
        }
    }

    suspend fun updateGoalsAndAdaptiveReview(goals: MacroGoals, reviewDateIso: String) {
        val safeGoals = sanitizeMacroGoals(goals)
        dataStore.edit { prefs ->
            prefs[Keys.GOAL_CALORIES] = safeGoals.calories
            prefs[Keys.GOAL_PROTEIN] = safeGoals.proteinGrams
            prefs[Keys.GOAL_CARBS] = safeGoals.carbsGrams
            prefs[Keys.GOAL_FAT] = safeGoals.fatGrams
            prefs[Keys.GOAL_WATER_ML] = safeGoals.waterMl
            prefs[Keys.LAST_ADAPTIVE_REVIEW_DATE_ISO] = reviewDateIso
        }
    }

    suspend fun updateCurrentWeight(weightKg: Float) {
        val safeWeight = sanitizeWeightKg(weightKg)
        dataStore.edit { prefs ->
            prefs[Keys.CURRENT_WEIGHT_KG] = safeWeight
        }
    }

    suspend fun updateBodyMetricsAndGoal(
        sex: BiologicalSex,
        age: Int,
        heightCm: Int,
        currentWeightKg: Float,
        targetWeightKg: Float,
        activityLevel: ActivityLevel,
        goalType: GoalType,
        updatedGoals: MacroGoals? = null,
    ) {
        val safeCurrentWeight = sanitizeWeightKg(currentWeightKg)
        val safeTargetWeight = sanitizeWeightKg(targetWeightKg)
        dataStore.edit { prefs ->
            prefs[Keys.SEX] = sex.name
            prefs[Keys.AGE] = age.coerceIn(14, 100)
            prefs[Keys.HEIGHT_CM] = heightCm.coerceIn(120, 230)
            prefs[Keys.CURRENT_WEIGHT_KG] = safeCurrentWeight
            prefs[Keys.TARGET_WEIGHT_KG] = safeTargetWeight
            prefs[Keys.ACTIVITY_LEVEL] = activityLevel.name
            prefs[Keys.GOAL_TYPE] = goalType.name
            if (updatedGoals != null) {
                val safeGoals = sanitizeMacroGoals(updatedGoals)
                prefs[Keys.GOAL_CALORIES] = safeGoals.calories
                prefs[Keys.GOAL_PROTEIN] = safeGoals.proteinGrams
                prefs[Keys.GOAL_CARBS] = safeGoals.carbsGrams
                prefs[Keys.GOAL_FAT] = safeGoals.fatGrams
                prefs[Keys.GOAL_WATER_ML] = safeGoals.waterMl
            }
        }
    }

    @Volatile
    private var lastAppliedNightMode: Int? = null

    suspend fun applySystemNightMode(themePreference: ThemePreference) {
        val targetMode = when (themePreference) {
            ThemePreference.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
            ThemePreference.LIGHT -> UiModeManager.MODE_NIGHT_NO
            ThemePreference.DARK -> UiModeManager.MODE_NIGHT_YES
        }
        if (lastAppliedNightMode == targetMode) return
        withContext(Dispatchers.Main.immediate) {
            if (lastAppliedNightMode == targetMode) return@withContext
            context.getSystemService(UiModeManager::class.java)?.setApplicationNightMode(targetMode)
            lastAppliedNightMode = targetMode
        }
    }

    suspend fun updateAppearance(themePreference: ThemePreference, useDynamicColor: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.THEME_PREFERENCE] = themePreference.name
            prefs[Keys.USE_DYNAMIC_COLOR] = useDynamicColor
        }
        applySystemNightMode(themePreference)
    }

    open suspend fun restoreProfile(profile: UserProfile) {
        val safeGoals = MacroGoals(
            calories = profile.goals.calories.coerceIn(1000, 6000),
            proteinGrams = profile.goals.proteinGrams.coerceIn(30, 400),
            carbsGrams = profile.goals.carbsGrams.coerceIn(20, 600),
            fatGrams = profile.goals.fatGrams.coerceIn(20, 250),
            waterMl = profile.goals.waterMl.coerceIn(1000, 7000),
        )
        dataStore.edit { prefs ->
            prefs[Keys.SEX] = profile.sex.name
            prefs[Keys.AGE] = profile.age.coerceIn(14, 100)
            prefs[Keys.HEIGHT_CM] = profile.heightCm.coerceIn(120, 230)
            prefs[Keys.CURRENT_WEIGHT_KG] = sanitizeWeightKg(profile.currentWeightKg)
            prefs[Keys.TARGET_WEIGHT_KG] = sanitizeWeightKg(profile.targetWeightKg)
            prefs[Keys.ACTIVITY_LEVEL] = profile.activityLevel.name
            prefs[Keys.GOAL_TYPE] = profile.goalType.name
            prefs[Keys.GOAL_CALORIES] = safeGoals.calories
            prefs[Keys.GOAL_PROTEIN] = safeGoals.proteinGrams
            prefs[Keys.GOAL_CARBS] = safeGoals.carbsGrams
            prefs[Keys.GOAL_FAT] = safeGoals.fatGrams
            prefs[Keys.GOAL_WATER_ML] = safeGoals.waterMl
            prefs[Keys.ONBOARDING_COMPLETED] = true
            prefs[Keys.THEME_PREFERENCE] = profile.themePreference.name
            prefs[Keys.USE_DYNAMIC_COLOR] = profile.useDynamicColor
            val safeReminder = WaterReminderScheduleCalculator.sanitize(profile.waterReminder)
            prefs[Keys.WATER_REMINDER_ENABLED] = safeReminder.enabled
            prefs[Keys.WATER_REMINDER_INTERVAL_MINUTES] = safeReminder.intervalMinutes
            prefs[Keys.WATER_REMINDER_START_HOUR] = safeReminder.startHour
            prefs[Keys.WATER_REMINDER_START_MINUTE] = safeReminder.startMinute
            prefs[Keys.WATER_REMINDER_END_HOUR] = safeReminder.endHour
            prefs[Keys.WATER_REMINDER_END_MINUTE] = safeReminder.endMinute
            prefs[Keys.WATER_REMINDER_STOP_ON_GOAL] = safeReminder.stopWhenGoalReached
            prefs[Keys.DIETARY_PROFILE_JSON] = prefsJson.encodeToString(sanitizeDietaryProfile(profile.dietaryProfile))
            prefs[Keys.ADAPTIVE_METABOLISM_ENABLED] = profile.adaptiveMetabolismEnabled
            val reviewDate = profile.lastAdaptiveReviewDateIso?.trim().orEmpty()
            if (reviewDate.isNotEmpty()) {
                prefs[Keys.LAST_ADAPTIVE_REVIEW_DATE_ISO] = reviewDate
            } else {
                prefs.remove(Keys.LAST_ADAPTIVE_REVIEW_DATE_ISO)
            }
        }
        applySystemNightMode(profile.themePreference)
    }

    suspend fun updateDietaryProfile(dietaryProfile: DietaryProfile) {
        val sanitized = sanitizeDietaryProfile(dietaryProfile)
        dataStore.edit { prefs ->
            prefs[Keys.DIETARY_PROFILE_JSON] = prefsJson.encodeToString(sanitized)
        }
    }

    suspend fun updateAdaptiveMetabolismEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.ADAPTIVE_METABOLISM_ENABLED] = enabled
        }
    }

    suspend fun recordAdaptiveReviewDate(dateIso: String) {
        dataStore.edit { prefs ->
            prefs[Keys.LAST_ADAPTIVE_REVIEW_DATE_ISO] = dateIso
        }
    }

    private fun sanitizeWeightKg(weightKg: Float, fallback: Float = 75f): Float =
        (if (weightKg.isFinite()) weightKg else fallback).coerceIn(30f, 300f)

    private fun sanitizeDietaryProfile(profile: DietaryProfile): DietaryProfile = sanitizeDietaryProfileData(profile)

    suspend fun updateWaterReminder(settings: WaterReminderSettings) {
        val safe = WaterReminderScheduleCalculator.sanitize(settings)
        dataStore.edit { prefs ->
            prefs[Keys.WATER_REMINDER_ENABLED] = safe.enabled
            prefs[Keys.WATER_REMINDER_INTERVAL_MINUTES] = safe.intervalMinutes
            prefs[Keys.WATER_REMINDER_START_HOUR] = safe.startHour
            prefs[Keys.WATER_REMINDER_START_MINUTE] = safe.startMinute
            prefs[Keys.WATER_REMINDER_END_HOUR] = safe.endHour
            prefs[Keys.WATER_REMINDER_END_MINUTE] = safe.endMinute
            prefs[Keys.WATER_REMINDER_STOP_ON_GOAL] = safe.stopWhenGoalReached
        }
    }
}
