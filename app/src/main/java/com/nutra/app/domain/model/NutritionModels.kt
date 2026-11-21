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
    BREAKFAST("Kahvaltılık & Süt"),
    SOUP("Çorbalar"),
    HOME_COOKED("Ev Yemekleri"),
    MEAT_POULTRY("Et, Tavuk & Balık"),
    SALAD_MEZE("Salata & Meze"),
    VEGETABLES("Sebzeler"),
    FRUIT_NUTS("Meyve & Kuruyemiş"),
    GRAINS_BAKERY("Ekmek & Tahıl"),
    SNACKS_SWEETS("Atıştırmalık & Tatlı"),
    OILS_SAUCES("Yağ & Soslar"),
    DRINKS("İçecekler"),
    CUSTOM("Özel"),
}

object ServingUnitLabels {
    const val RAW_GRAM = "1 Gram"
    const val RAW_ML = "1 ml"
    const val BASE_100G = "100 Gram"
    const val BASE_100ML = "100 ml"

    fun normalizedBaseUnit(baseUnit: String?): String =
        if (baseUnit.equals("ml", ignoreCase = true)) "ml" else "g"

    fun rawUnitFor(baseUnit: String?): String =
        if (normalizedBaseUnit(baseUnit) == "ml") RAW_ML else RAW_GRAM

    fun base100UnitFor(baseUnit: String?): String =
        if (normalizedBaseUnit(baseUnit) == "ml") BASE_100ML else BASE_100G

    fun isRawUnitLabel(unitLabel: String): Boolean =
        unitLabel == RAW_GRAM || unitLabel == RAW_ML
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
    val catalogCode: String? = null,
    val brand: String? = null,
    val baseUnit: String = "g",
    val saturatedFatPer100g: Float? = null,
    val sugarPer100g: Float? = null,
    val fiberPer100g: Float? = null,
    val saltPer100g: Float? = null,
    val sodiumMgPer100g: Float? = null,
    val calciumMgPer100g: Float? = null,
    val potassiumMgPer100g: Float? = null,
    val ironMgPer100g: Float? = null,
    val vitaminCMgPer100g: Float? = null,
    val preparationState: String? = null,
    @Transient val normalizedName: String = "",
    @Transient val normalizedSearchText: String = "",
) {
    val isLiquid: Boolean
        get() = baseUnit.equals("ml", ignoreCase = true)

    val displayUnitSuffix: String
        get() = ServingUnitLabels.normalizedBaseUnit(baseUnit)

    val hasExtendedDetails: Boolean
        get() = saturatedFatPer100g != null ||
            sugarPer100g != null ||
            fiberPer100g != null ||
            saltPer100g != null ||
            sodiumMgPer100g != null ||
            calciumMgPer100g != null ||
            potassiumMgPer100g != null ||
            ironMgPer100g != null ||
            vitaminCMgPer100g != null

    val allUnits: List<ServingUnit>
        get() {
            val base = ServingUnit(defaultUnitLabel, defaultUnitGrams)
            val base100 = ServingUnit(ServingUnitLabels.base100UnitFor(baseUnit), 100f)
            val raw1 = ServingUnit(ServingUnitLabels.rawUnitFor(baseUnit), 1f)
            return buildList {
                add(base)
                alternateUnits.forEach { unit ->
                    if (none { it.label.equals(unit.label, ignoreCase = true) }) {
                        add(unit)
                    }
                }
                if (none { it.grams == 100f && (it.label == base100.label || it.label.equals("100 g", ignoreCase = true) || it.label.equals("100 ml", ignoreCase = true)) }) {
                    if (none { it.grams == 100f }) add(base100)
                }
                if (none { ServingUnitLabels.isRawUnitLabel(it.label) }) add(raw1)
            }
        }

    fun caloriesForGrams(grams: Float): Int {
        val safeGrams = if (grams.isFinite() && grams > 0f) grams else 0f
        val safePer100 = caloriesPer100g.coerceAtLeast(0)
        return ((safePer100.toDouble() * safeGrams.toDouble()) / 100.0).roundToInt().coerceAtLeast(0)
    }

    fun proteinForGrams(grams: Float): Float = scaleMacroForGrams(proteinPer100g, grams)

    fun carbsForGrams(grams: Float): Float = scaleMacroForGrams(carbsPer100g, grams)

    fun fatForGrams(grams: Float): Float = scaleMacroForGrams(fatPer100g, grams)

    fun scaledNutrient(per100: Float?, amount: Float): Float? {
        if (per100 == null || !per100.isFinite()) return null
        return scaleMacroForGrams(per100, amount)
    }

    private fun scaleMacroForGrams(per100: Float, grams: Float): Float {
        if (!per100.isFinite() || !grams.isFinite() || per100 <= 0f || grams <= 0f) return 0f
        val scaled = (per100.toDouble() * grams.toDouble()) / 100.0
        if (!scaled.isFinite() || scaled <= 0.0) return 0f
        return ((scaled * 10.0).roundToInt() / 10f).coerceAtLeast(0f)
    }
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
    val baseUnit: String = "g",
) {
    @Transient
    val formattedQuantity: String = run {
        val unitSuffix = ServingUnitLabels.normalizedBaseUnit(baseUnit)
        val safeGrams = if (totalGrams.isFinite() && totalGrams > 0f) totalGrams.roundToInt() else 0
        if (ServingUnitLabels.isRawUnitLabel(unitLabel)) {
            "$safeGrams $unitSuffix"
        } else {
            "${NutraFormatters.formatMultiplier(multiplier)}x $unitLabel ($safeGrams $unitSuffix)"
        }
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

enum class DietStyle(
    val title: String,
    val subtitle: String,
) {
    STANDARD("Standart", "Tüm besin gruplarını dengeli biçimde içerir"),
    MEDITERRANEAN("Akdeniz & Ev Yemeği", "Zeytinyağlı, sebze, baklagil, balık ve tam tahıl ağırlıklı"),
    VEGETARIAN("Vejetaryen", "Et, tavuk ve balık içermez; süt ve yumurta içerir"),
    VEGAN("Vegan", "Yalnızca bitkisel besinlerden oluşur"),
    PESCATARIAN("Pesketaryen", "Et ve tavuk içermez; balık ve deniz ürünleri içerir"),
    LOW_CARB("Düşük Karbonhidrat", "Protein ve sağlıklı yağ odaklı, daha düşük karbonhidrat"),
}

enum class AllergenTag(val title: String) {
    GLUTEN("Gluten"),
    DAIRY("Süt ürünleri"),
    EGG("Yumurta"),
    TREE_NUTS("Kuruyemiş"),
    PEANUT("Yer fıstığı"),
    FISH_SEAFOOD("Balık & Deniz ürünü"),
    SOY("Soya"),
    SESAME("Susam & Tahin"),
    LEGUMES("Baklagiller"),
}

@Immutable
@Serializable
data class DietaryProfile(
    val dietStyle: DietStyle = DietStyle.STANDARD,
    val excludedAllergens: Set<AllergenTag> = emptySet(),
    val excludedKeywords: List<String> = emptyList(),
    val specialHealthGuardrail: Boolean = false,
)

enum class GoalChangeSource(val title: String) {
    ONBOARDING("Başlangıç"),
    MANUAL("Manuel"),
    FORMULA("Formül"),
    ADAPTIVE_APPROVED("Adaptif onaylı"),
}

@Immutable
@Serializable
data class GoalHistoryEntry(
    val id: Long = 0L,
    val effectiveDateIso: String,
    val calories: Int,
    val proteinGrams: Int,
    val carbsGrams: Int,
    val fatGrams: Int,
    val goalType: GoalType,
    val targetWeightKg: Float,
    val source: GoalChangeSource = GoalChangeSource.MANUAL,
    val timestampMillis: Long = 0L,
)

enum class FoodFeedbackStatus {
    NEUTRAL,
    DISLIKED,
    PINNED,
}

@Immutable
@Serializable
data class FoodFeedbackRecord(
    val foodKey: String,
    val foodId: Long? = null,
    val foodName: String = "",
    val status: FoodFeedbackStatus = FoodFeedbackStatus.NEUTRAL,
    val acceptedCount: Int = 0,
    val dismissedCount: Int = 0,
    val lastSuggestedMillis: Long = 0L,
    val lastAcceptedMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
)

@Immutable
@Serializable
data class MetabolismSnapshotRecord(
    val dateIso: String,
    val estimatedTdeeKcal: Int,
    val uncertaintyKcal: Int,
    val priorTdeeKcal: Int,
    val trendWeightKg: Float,
    val confidenceScore: Int,
    val qualityLevel: String,
    val updatedAtMillis: Long = 0L,
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
    val waterReminder: WaterReminderSettings = WaterReminderSettings(),
    val dietaryProfile: DietaryProfile = DietaryProfile(),
    val adaptiveMetabolismEnabled: Boolean = true,
    val lastAdaptiveReviewDateIso: String? = null,
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
    val baseUnit: String = "g",
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
