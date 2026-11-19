package com.nutra.app.core.datastore

import android.app.UiModeManager
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nutra.app.core.notification.WaterReminderScheduleCalculator
import com.nutra.app.domain.model.ActivityLevel
import com.nutra.app.domain.model.BiologicalSex
import com.nutra.app.domain.model.GoalType
import com.nutra.app.domain.model.MacroGoals
import com.nutra.app.domain.model.ThemePreference
import com.nutra.app.domain.model.UserProfile
import com.nutra.app.domain.model.WaterReminderSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.nutraDataStore: DataStore<Preferences> by preferencesDataStore(name = "nutra_preferences")

@Singleton
class UserPreferencesDataStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
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
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val GEMINI_MODEL = stringPreferencesKey("gemini_model")

        val WATER_REMINDER_ENABLED = booleanPreferencesKey("water_reminder_enabled")
        val WATER_REMINDER_INTERVAL_MINUTES = intPreferencesKey("water_reminder_interval_minutes")
        val WATER_REMINDER_START_HOUR = intPreferencesKey("water_reminder_start_hour")
        val WATER_REMINDER_START_MINUTE = intPreferencesKey("water_reminder_start_minute")
        val WATER_REMINDER_END_HOUR = intPreferencesKey("water_reminder_end_hour")
        val WATER_REMINDER_END_MINUTE = intPreferencesKey("water_reminder_end_minute")
        val WATER_REMINDER_STOP_ON_GOAL = booleanPreferencesKey("water_reminder_stop_on_goal")
    }

    private companion object {
        val SEX_BY_NAME: Map<String, BiologicalSex> = BiologicalSex.entries.associateBy { it.name }
        val ACTIVITY_BY_NAME: Map<String, ActivityLevel> = ActivityLevel.entries.associateBy { it.name }
        val GOAL_BY_NAME: Map<String, GoalType> = GoalType.entries.associateBy { it.name }
        val THEME_BY_NAME: Map<String, ThemePreference> = ThemePreference.entries.associateBy { it.name }
    }

    private val sharedProfileFlow: SharedFlow<UserProfile> = context.nutraDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs -> parseUserProfile(prefs) }
        .distinctUntilChanged()
        .shareIn(
            scope = dataStoreScope,
            started = SharingStarted.Eagerly,
            replay = 1,
        )

    val userProfileFlow: Flow<UserProfile> = sharedProfileFlow

    fun currentCachedProfile(): UserProfile? = sharedProfileFlow.replayCache.firstOrNull()

    private fun parseUserProfile(prefs: Preferences): UserProfile {
        val sex = prefs[Keys.SEX]?.let { SEX_BY_NAME[it] } ?: BiologicalSex.MALE
        val activityLevel = prefs[Keys.ACTIVITY_LEVEL]?.let { ACTIVITY_BY_NAME[it] } ?: ActivityLevel.MODERATE
        val goalType = prefs[Keys.GOAL_TYPE]?.let { GOAL_BY_NAME[it] } ?: GoalType.LOSE_WEIGHT
        val themePref = prefs[Keys.THEME_PREFERENCE]?.let { THEME_BY_NAME[it] } ?: ThemePreference.SYSTEM

        val goals = MacroGoals(
            calories = prefs[Keys.GOAL_CALORIES] ?: 2100,
            proteinGrams = prefs[Keys.GOAL_PROTEIN] ?: 140,
            carbsGrams = prefs[Keys.GOAL_CARBS] ?: 220,
            fatGrams = prefs[Keys.GOAL_FAT] ?: 70,
            waterMl = prefs[Keys.GOAL_WATER_ML] ?: 2500,
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

        return UserProfile(
            sex = sex,
            age = prefs[Keys.AGE] ?: 28,
            heightCm = prefs[Keys.HEIGHT_CM] ?: 176,
            currentWeightKg = prefs[Keys.CURRENT_WEIGHT_KG] ?: 75f,
            targetWeightKg = prefs[Keys.TARGET_WEIGHT_KG] ?: 70f,
            activityLevel = activityLevel,
            goalType = goalType,
            goals = goals,
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            themePreference = themePref,
            useDynamicColor = prefs[Keys.USE_DYNAMIC_COLOR] ?: false,
            geminiApiKey = prefs[Keys.GEMINI_API_KEY] ?: "",
            geminiModel = prefs[Keys.GEMINI_MODEL] ?: "gemini-2.5-flash",
            waterReminder = waterReminder,
        )
    }

    suspend fun getSnapshot(): UserProfile = sharedProfileFlow.first()

    suspend fun saveOnboardingProfile(profile: UserProfile) {
        context.nutraDataStore.edit { prefs ->
            prefs[Keys.SEX] = profile.sex.name
            prefs[Keys.AGE] = profile.age
            prefs[Keys.HEIGHT_CM] = profile.heightCm
            prefs[Keys.CURRENT_WEIGHT_KG] = profile.currentWeightKg
            prefs[Keys.TARGET_WEIGHT_KG] = profile.targetWeightKg
            prefs[Keys.ACTIVITY_LEVEL] = profile.activityLevel.name
            prefs[Keys.GOAL_TYPE] = profile.goalType.name
            prefs[Keys.GOAL_CALORIES] = profile.goals.calories
            prefs[Keys.GOAL_PROTEIN] = profile.goals.proteinGrams
            prefs[Keys.GOAL_CARBS] = profile.goals.carbsGrams
            prefs[Keys.GOAL_FAT] = profile.goals.fatGrams
            prefs[Keys.GOAL_WATER_ML] = profile.goals.waterMl
            prefs[Keys.ONBOARDING_COMPLETED] = true
        }
    }

    suspend fun updateGoals(goals: MacroGoals) {
        context.nutraDataStore.edit { prefs ->
            prefs[Keys.GOAL_CALORIES] = goals.calories
            prefs[Keys.GOAL_PROTEIN] = goals.proteinGrams
            prefs[Keys.GOAL_CARBS] = goals.carbsGrams
            prefs[Keys.GOAL_FAT] = goals.fatGrams
            prefs[Keys.GOAL_WATER_ML] = goals.waterMl
        }
    }

    suspend fun updateCurrentWeight(weightKg: Float) {
        context.nutraDataStore.edit { prefs ->
            prefs[Keys.CURRENT_WEIGHT_KG] = weightKg
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
        context.nutraDataStore.edit { prefs ->
            prefs[Keys.SEX] = sex.name
            prefs[Keys.AGE] = age
            prefs[Keys.HEIGHT_CM] = heightCm
            prefs[Keys.CURRENT_WEIGHT_KG] = currentWeightKg
            prefs[Keys.TARGET_WEIGHT_KG] = targetWeightKg
            prefs[Keys.ACTIVITY_LEVEL] = activityLevel.name
            prefs[Keys.GOAL_TYPE] = goalType.name
            if (updatedGoals != null) {
                prefs[Keys.GOAL_CALORIES] = updatedGoals.calories
                prefs[Keys.GOAL_PROTEIN] = updatedGoals.proteinGrams
                prefs[Keys.GOAL_CARBS] = updatedGoals.carbsGrams
                prefs[Keys.GOAL_FAT] = updatedGoals.fatGrams
                prefs[Keys.GOAL_WATER_ML] = updatedGoals.waterMl
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
        context.nutraDataStore.edit { prefs ->
            prefs[Keys.THEME_PREFERENCE] = themePreference.name
            prefs[Keys.USE_DYNAMIC_COLOR] = useDynamicColor
        }
        applySystemNightMode(themePreference)
    }

    suspend fun restoreProfile(profile: UserProfile) {
        val safeGoals = MacroGoals(
            calories = profile.goals.calories.coerceIn(1000, 6000),
            proteinGrams = profile.goals.proteinGrams.coerceIn(30, 400),
            carbsGrams = profile.goals.carbsGrams.coerceIn(20, 600),
            fatGrams = profile.goals.fatGrams.coerceIn(20, 250),
            waterMl = profile.goals.waterMl.coerceIn(1000, 7000),
        )
        context.nutraDataStore.edit { prefs ->
            prefs[Keys.SEX] = profile.sex.name
            prefs[Keys.AGE] = profile.age.coerceIn(14, 100)
            prefs[Keys.HEIGHT_CM] = profile.heightCm.coerceIn(120, 230)
            prefs[Keys.CURRENT_WEIGHT_KG] = profile.currentWeightKg.coerceIn(30f, 300f)
            prefs[Keys.TARGET_WEIGHT_KG] = profile.targetWeightKg.coerceIn(30f, 300f)
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
            prefs[Keys.GEMINI_API_KEY] = profile.geminiApiKey.trim()
            prefs[Keys.GEMINI_MODEL] = profile.geminiModel.trim().ifEmpty { "gemini-2.5-flash" }
            val safeReminder = WaterReminderScheduleCalculator.sanitize(profile.waterReminder)
            prefs[Keys.WATER_REMINDER_ENABLED] = safeReminder.enabled
            prefs[Keys.WATER_REMINDER_INTERVAL_MINUTES] = safeReminder.intervalMinutes
            prefs[Keys.WATER_REMINDER_START_HOUR] = safeReminder.startHour
            prefs[Keys.WATER_REMINDER_START_MINUTE] = safeReminder.startMinute
            prefs[Keys.WATER_REMINDER_END_HOUR] = safeReminder.endHour
            prefs[Keys.WATER_REMINDER_END_MINUTE] = safeReminder.endMinute
            prefs[Keys.WATER_REMINDER_STOP_ON_GOAL] = safeReminder.stopWhenGoalReached
        }
        applySystemNightMode(profile.themePreference)
    }

    suspend fun updateGeminiConfig(apiKey: String, model: String) {
        context.nutraDataStore.edit { prefs ->
            prefs[Keys.GEMINI_API_KEY] = apiKey.trim()
            prefs[Keys.GEMINI_MODEL] = model.trim().ifEmpty { "gemini-2.5-flash" }
        }
    }

    suspend fun updateWaterReminder(settings: WaterReminderSettings) {
        val safe = WaterReminderScheduleCalculator.sanitize(settings)
        context.nutraDataStore.edit { prefs ->
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
