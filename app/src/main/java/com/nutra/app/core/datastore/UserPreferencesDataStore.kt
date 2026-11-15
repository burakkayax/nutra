package com.nutra.app.core.datastore

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
import com.nutra.app.domain.model.ActivityLevel
import com.nutra.app.domain.model.BiologicalSex
import com.nutra.app.domain.model.GoalType
import com.nutra.app.domain.model.MacroGoals
import com.nutra.app.domain.model.ThemePreference
import com.nutra.app.domain.model.UserProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.nutraDataStore: DataStore<Preferences> by preferencesDataStore(name = "nutra_preferences")

@Singleton
class UserPreferencesDataStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
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
    }

    val userProfileFlow: Flow<UserProfile> = context.nutraDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val sex = prefs[Keys.SEX]?.let {
                runCatching { BiologicalSex.valueOf(it) }.getOrNull()
            } ?: BiologicalSex.MALE

            val activityLevel = prefs[Keys.ACTIVITY_LEVEL]?.let {
                runCatching { ActivityLevel.valueOf(it) }.getOrNull()
            } ?: ActivityLevel.MODERATE

            val goalType = prefs[Keys.GOAL_TYPE]?.let {
                runCatching { GoalType.valueOf(it) }.getOrNull()
            } ?: GoalType.LOSE_WEIGHT

            val themePref = prefs[Keys.THEME_PREFERENCE]?.let {
                runCatching { ThemePreference.valueOf(it) }.getOrNull()
            } ?: ThemePreference.SYSTEM

            val goals = MacroGoals(
                calories = prefs[Keys.GOAL_CALORIES] ?: 2100,
                proteinGrams = prefs[Keys.GOAL_PROTEIN] ?: 140,
                carbsGrams = prefs[Keys.GOAL_CARBS] ?: 220,
                fatGrams = prefs[Keys.GOAL_FAT] ?: 70,
                waterMl = prefs[Keys.GOAL_WATER_ML] ?: 2500,
            )

            UserProfile(
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
            )
        }

    suspend fun getSnapshot(): UserProfile = userProfileFlow.first()

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

    suspend fun updateAppearance(themePreference: ThemePreference, useDynamicColor: Boolean) {
        context.nutraDataStore.edit { prefs ->
            prefs[Keys.THEME_PREFERENCE] = themePreference.name
            prefs[Keys.USE_DYNAMIC_COLOR] = useDynamicColor
        }
    }

    suspend fun updateGeminiConfig(apiKey: String, model: String) {
        context.nutraDataStore.edit { prefs ->
            prefs[Keys.GEMINI_API_KEY] = apiKey.trim()
            prefs[Keys.GEMINI_MODEL] = model.trim().ifEmpty { "gemini-2.5-flash" }
        }
    }
}
