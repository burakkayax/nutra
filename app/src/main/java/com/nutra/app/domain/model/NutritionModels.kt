package com.nutra.app.domain.model

import androidx.compose.runtime.Immutable
import com.nutra.app.core.designsystem.NutraFormatters
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.math.roundToInt

enum class ThemePreference(val title: String) {
    SYSTEM("Sistem"),
    LIGHT("Açık"),
    DARK("Koyu"),
}

enum class GoalType(
    val title: String,
    val subtitle: String,
    val calorieDelta: Int,
    val proteinRatio: Float,
    val carbsRatio: Float,
    val fatRatio: Float,
) {
    LOSE_WEIGHT(
        title = "Kilo Verme",
        subtitle = "Kas kütlesini koruyarak sağlıklı yağ yakımı (-400 kcal)",
        calorieDelta = -400,
        proteinRatio = 0.32f,
        carbsRatio = 0.40f,
        fatRatio = 0.28f,
    ),
    MAINTAIN(
        title = "Kilosunu Koruma",
        subtitle = "Mevcut formu, enerjiyi ve metabolik dengeyi koruma",
        calorieDelta = 0,
        proteinRatio = 0.28f,
        carbsRatio = 0.44f,
        fatRatio = 0.28f,
    ),
    GAIN_MUSCLE(
        title = "Kas & Hacim Kazanımı",
        subtitle = "Temiz kas gelişimi ve güç artışı (+300 kcal)",
        calorieDelta = 300,
        proteinRatio = 0.30f,
        carbsRatio = 0.45f,
        fatRatio = 0.25f,
    ),
}

enum class BiologicalSex(val title: String) {
    FEMALE("Kadın"),
    MALE("Erkek"),
}

enum class ActivityLevel(
    val title: String,
    val subtitle: String,
    val multiplier: Float,
) {
    SEDENTARY("Az Hareketli", "Masa başı iş, günlük hafif yürüyüş", 1.2f),
    LIGHT("Hafif Aktif", "Haftada 1–3 gün egzersiz veya yürüyüş", 1.375f),
    MODERATE("Orta Aktif", "Haftada 3–5 gün düzenli antrenman", 1.55f),
    ACTIVE("Çok Aktif", "Haftada 6–7 gün yoğun spor veya fiziksel iş", 1.725f),
}

enum class MealType(
    val title: String,
    val sortOrder: Int,
) {
    BREAKFAST("Kahvaltı", 0),
    LUNCH("Öğle Yemeği", 1),
    DINNER("Akşam Yemeği", 2),
    SNACK("Ara Öğün", 3),
}

enum class FoodCategory(val title: String) {
    ALL("Tümü"),
    BREAKFAST("Kahvaltılık"),
    SOUP("Çorbalar"),
    HOME_COOKED("Ev Yemekleri"),
    MEAT_POULTRY("Et & Tavuk"),
    SALAD_MEZE("Salata & Meze"),
    FRUIT_NUTS("Meyve & Kuruyemiş"),
    DRINKS("İçecekler"),
    CUSTOM("Özel"),
}

@Immutable
@Serializable
data class ServingUnit(
    val label: String,
    val grams: Float,
)

@Immutable
@Serializable
data class FoodItem(
    val id: Long = 0L,
    val name: String,
    val subtitle: String,
    val category: FoodCategory,
    val caloriesPer100g: Int,
    val proteinPer100g: Float,
    val carbsPer100g: Float,
    val fatPer100g: Float,
    val defaultUnitLabel: String,
    val defaultUnitGrams: Float,
    val alternateUnits: List<ServingUnit> = emptyList(),
    val preferredMealTypes: Set<MealType> = emptySet(),
    val barcode: String? = null,
    val isFavorite: Boolean = false,
    val isCustom: Boolean = false,
    @Transient val normalizedName: String = "",
    @Transient val normalizedSearchText: String = "",
) {
    val allUnits: List<ServingUnit>
        get() {
            val base = ServingUnit(defaultUnitLabel, defaultUnitGrams)
            val gram100 = ServingUnit("100 Gram", 100f)
            val gram1 = ServingUnit("1 Gram", 1f)
            return buildList {
                add(base)
                alternateUnits.forEach { unit ->
                    if (none { it.label.equals(unit.label, ignoreCase = true) }) {
                        add(unit)
                    }
                }
                if (none { it.grams == 100f }) add(gram100)
                if (none { it.grams == 1f }) add(gram1)
            }
        }

    fun caloriesForGrams(grams: Float): Int =
        ((caloriesPer100g * grams) / 100f).roundToInt().coerceAtLeast(0)

    fun proteinForGrams(grams: Float): Float =
        ((proteinPer100g * grams) / 100f * 10f).roundToInt() / 10f

    fun carbsForGrams(grams: Float): Float =
        ((carbsPer100g * grams) / 100f * 10f).roundToInt() / 10f

    fun fatForGrams(grams: Float): Float =
        ((fatPer100g * grams) / 100f * 10f).roundToInt() / 10f
}

@Immutable
@Serializable
data class MealLogItem(
    val id: Long = 0L,
    val dateIso: String,
    val mealType: MealType,
    val foodId: Long?,
    val foodName: String,
    val unitLabel: String,
    val multiplier: Float,
    val totalGrams: Float,
    val calories: Int,
    val proteinGrams: Float,
    val carbsGrams: Float,
    val fatGrams: Float,
    val timestampMillis: Long,
) {
    @Transient
    val formattedQuantity: String = if (unitLabel == "1 Gram") {
        "${totalGrams.roundToInt()} g"
    } else {
        "${NutraFormatters.formatMultiplier(multiplier)}x $unitLabel (${totalGrams.roundToInt()} g)"
    }
}

@Immutable
@Serializable
data class MacroGoals(
    val calories: Int = 2100,
    val proteinGrams: Int = 140,
    val carbsGrams: Int = 220,
    val fatGrams: Int = 70,
    val waterMl: Int = 2500,
)

@Immutable
@Serializable
data class WaterReminderSettings(
    val enabled: Boolean = false,
    val intervalMinutes: Int = 120,
    val startHour: Int = 9,
    val startMinute: Int = 0,
    val endHour: Int = 22,
    val endMinute: Int = 0,
    val stopWhenGoalReached: Boolean = true,
)

@Immutable
@Serializable
data class UserProfile(
    val sex: BiologicalSex = BiologicalSex.MALE,
    val age: Int = 28,
    val heightCm: Int = 176,
    val currentWeightKg: Float = 75f,
    val targetWeightKg: Float = 70f,
    val activityLevel: ActivityLevel = ActivityLevel.MODERATE,
    val goalType: GoalType = GoalType.LOSE_WEIGHT,
    val goals: MacroGoals = MacroGoals(),
    val onboardingCompleted: Boolean = false,
    val themePreference: ThemePreference = ThemePreference.SYSTEM,
    val useDynamicColor: Boolean = false,
    val geminiApiKey: String = "",
    val geminiModel: String = "gemini-2.5-flash",
    val waterReminder: WaterReminderSettings = WaterReminderSettings(),
)

@Immutable
data class MealSummary(
    val mealType: MealType,
    val items: List<MealLogItem>,
) {
    val totalCalories: Int
    val totalProtein: Float
    val totalCarbs: Float
    val totalFat: Float

    init {
        var cal = 0
        var p = 0.0
        var c = 0.0
        var f = 0.0
        for (item in items) {
            cal += item.calories
            p += item.proteinGrams
            c += item.carbsGrams
            f += item.fatGrams
        }
        totalCalories = cal
        totalProtein = p.toFloat()
        totalCarbs = c.toFloat()
        totalFat = f.toFloat()
    }
}

@Immutable
data class DailyNutritionSummary(
    val dateIso: String,
    val goals: MacroGoals,
    val meals: List<MealSummary>,
    val waterMl: Int,
) {
    val consumedCalories: Int
    val consumedProtein: Float
    val consumedCarbs: Float
    val consumedFat: Float

    init {
        var cal = 0
        var p = 0.0
        var c = 0.0
        var f = 0.0
        for (meal in meals) {
            cal += meal.totalCalories
            p += meal.totalProtein
            c += meal.totalCarbs
            f += meal.totalFat
        }
        consumedCalories = cal
        consumedProtein = p.toFloat()
        consumedCarbs = c.toFloat()
        consumedFat = f.toFloat()
    }
}

@Immutable
@Serializable
data class ComboFoodEntry(
    val foodId: Long?,
    val foodName: String,
    val unitLabel: String,
    val multiplier: Float,
    val totalGrams: Float,
    val calories: Int,
    val proteinGrams: Float,
    val carbsGrams: Float,
    val fatGrams: Float,
)

@Immutable
data class SmartComboSuggestion(
    val id: String,
    val title: String,
    val subtitle: String,
    val items: List<ComboFoodEntry>,
    val coOccurrenceCount: Int,
) {
    val totalCalories: Int
    val totalProtein: Float
    val totalCarbs: Float
    val totalFat: Float
    val itemsPreview: String

    init {
        var cal = 0
        var p = 0.0
        var c = 0.0
        var f = 0.0
        for (item in items) {
            cal += item.calories
            p += item.proteinGrams
            c += item.carbsGrams
            f += item.fatGrams
        }
        totalCalories = cal
        totalProtein = p.toFloat()
        totalCarbs = c.toFloat()
        totalFat = f.toFloat()
        itemsPreview = items.joinToString(" · ") { it.foodName }
    }
}

@Immutable
data class AiAnalyzedFoodItem(
    val id: String,
    val name: String,
    val unitLabel: String,
    val baseGrams: Float,
    val multiplier: Float = 1f,
    val baseCalories: Int,
    val baseProtein: Float,
    val baseCarbs: Float,
    val baseFat: Float,
    val confidence: Float = 0.92f,
    val included: Boolean = true,
) {
    val effectiveGrams: Float get() = baseGrams * multiplier
    val effectiveCalories: Int get() = (baseCalories * multiplier).roundToInt()
    val effectiveProtein: Float get() = ((baseProtein * multiplier) * 10f).roundToInt() / 10f
    val effectiveCarbs: Float get() = ((baseCarbs * multiplier) * 10f).roundToInt() / 10f
    val effectiveFat: Float get() = ((baseFat * multiplier) * 10f).roundToInt() / 10f
}

@Immutable
@Serializable
data class WeightEntry(
    val id: Long = 0L,
    val dateIso: String,
    val weightKg: Float,
    val timestampMillis: Long,
)

@Immutable
data class WeightTrendPoint(
    val dateIso: String,
    val rawWeightKg: Float,
    val emaTrendKg: Float,
)

@Immutable
data class DailyAnalyticsPoint(
    val dateIso: String,
    val calories: Int,
    val targetCalories: Int,
    val proteinGrams: Float,
    val carbsGrams: Float,
    val fatGrams: Float,
    val waterMl: Int,
)

sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Error(val message: String, val cause: Throwable? = null) : AppResult<Nothing>
}
