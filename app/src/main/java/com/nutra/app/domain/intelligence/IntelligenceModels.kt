package com.nutra.app.domain.intelligence

import androidx.compose.runtime.Immutable
import com.nutra.app.domain.model.AllergenTag
import com.nutra.app.domain.model.ComboFoodEntry
import com.nutra.app.domain.model.FoodItem
import com.nutra.app.domain.model.MealType

enum class MealRole {
    MAIN_PROTEIN,
    MAIN_COMPOSITE_DISH,
    CARB_BASE,
    SOUP,
    DAIRY_PROBIOTIC,
    VEG_SALAD_SIDE,
    FRUIT,
    HEALTHY_FAT_NUT,
    SNACK_SWEET,
    BEVERAGE,
    INGREDIENT_ONLY,
}

enum class EdibilityStatus {
    READY_TO_EAT,
    COOKING_INGREDIENT_ONLY,
}

@Immutable
data class PortionConstraint(
    val minGrams: Float,
    val maxGrams: Float,
    val typicalGrams: Float,
    val gramStep: Float = 10f,
)

@Immutable
data class ClassifiedFood(
    val food: FoodItem,
    val feedbackKey: String,
    val edibility: EdibilityStatus,
    val role: MealRole,
    val allergens: Set<AllergenTag>,
    val containsRedMeat: Boolean,
    val containsPoultry: Boolean,
    val containsFish: Boolean,
    val containsAnimalProduct: Boolean,
    val isTurkishHomeCooked: Boolean,
    val isWholeFood: Boolean,
    val portionConstraint: PortionConstraint,
    val effectiveSodiumMgPer100g: Float?,
)

enum class HabitMaturity(val title: String) {
    NONE("Yeni profil"),
    EARLY("Öğreniliyor"),
    ESTABLISHED("Kişiselleştirildi"),
}

enum class MicronutrientType(
    val title: String,
    val unit: String,
    val isUpperLimit: Boolean = false,
) {
    FIBER("Lif", "g", isUpperLimit = false),
    SODIUM("Sodyum", "mg", isUpperLimit = true),
    POTASSIUM("Potasyum", "mg", isUpperLimit = false),
    CALCIUM("Kalsiyum", "mg", isUpperLimit = false),
    IRON("Demir", "mg", isUpperLimit = false),
    VITAMIN_C("C Vitamini", "mg", isUpperLimit = false),
}

enum class NutrientSignalStatus {
    INSUFFICIENT_DATA,
    LOW_RECORDED,
    ADEQUATE,
    ELEVATED_RECORDED,
}

@Immutable
data class MicronutrientSignal(
    val type: MicronutrientType,
    val status: NutrientSignalStatus,
    val dailyAverageKnown: Float?,
    val referenceValue: Float,
    val coverageRatio: Float,
    val plainNote: String,
)

@Immutable
data class UserEatingProfile(
    val maturity: HabitMaturity,
    val loggedDaysCount: Int,
    val homeCookedAffinity: Float, // 0f..1f (0.5f = neutral prior)
    val foodAffinityById: Map<Long, Float>,
    val mealFoodAffinity: Map<MealType, Map<Long, Float>>,
    val usualGramsByFoodId: Map<Long, Float>,
    val recentlyEatenFoodIds24h: Set<Long>,
    val recentlyEatenFoodIds48h: Set<Long>,
    val mealCalorieShares: Map<MealType, Float>,
    val coOccurrencePairs: Map<Long, Set<Long>>,
    val micronutrientSignals: List<MicronutrientSignal>,
    val habitShiftNote: String?,
)

enum class MealBudgetMode {
    REMAINING_FOR_DAY,
    PROPORTIONAL_MEAL_SHARE,
    LIGHT_OVER_BUDGET,
}

@Immutable
data class MealBudget(
    val mealType: MealType,
    val targetCalories: Int,
    val targetProteinGrams: Float,
    val targetCarbsGrams: Float,
    val targetFatGrams: Float,
    val dailyRemainingCalories: Int,
    val dailyRemainingProtein: Float,
    val dailyRemainingCarbs: Float,
    val dailyRemainingFat: Float,
    val mode: MealBudgetMode,
    val budgetSummaryText: String,
)

@Immutable
data class MicronutrientContribution(
    val type: MicronutrientType,
    val amount: Float,
    val formattedValue: String,
    val knownItemCount: Int,
    val totalItemCount: Int,
)

@Immutable
data class RecommendedFoodPortion(
    val food: FoodItem,
    val feedbackKey: String,
    val role: MealRole,
    val unitLabel: String,
    val unitGrams: Float,
    val multiplier: Float,
    val totalGrams: Float,
    val calories: Int,
    val proteinGrams: Float,
    val carbsGrams: Float,
    val fatGrams: Float,
    val minGrams: Float,
    val maxGrams: Float,
) {
    fun toComboEntry(): ComboFoodEntry = ComboFoodEntry(
        foodId = food.id.takeIf { it > 0L },
        foodName = food.name,
        unitLabel = unitLabel,
        multiplier = multiplier,
        totalGrams = totalGrams,
        calories = calories,
        proteinGrams = proteinGrams,
        carbsGrams = carbsGrams,
        fatGrams = fatGrams,
        baseUnit = food.displayUnitSuffix,
    )
}

@Immutable
data class DailyGoalImpactPreview(
    val caloriesBefore: Int,
    val caloriesAfter: Int,
    val calorieGoal: Int,
    val proteinBefore: Float,
    val proteinAfter: Float,
    val proteinGoal: Int,
    val carbsBefore: Float,
    val carbsAfter: Float,
    val carbsGoal: Int,
    val fatBefore: Float,
    val fatAfter: Float,
    val fatGoal: Int,
    val impactSummary: String,
) {
    val caloriePercentAfter: Int
        get() = ((caloriesAfter.toFloat() / calorieGoal.coerceAtLeast(1)) * 100f).toInt().coerceAtLeast(0)

    val proteinPercentAfter: Int
        get() = ((proteinAfter / proteinGoal.coerceAtLeast(1)) * 100f).toInt().coerceAtLeast(0)
}

enum class RecommendationStyleTag(val label: String) {
    BALANCED_TURKISH("Dengeli ev öğünü"),
    HIGH_PROTEIN("Yüksek protein"),
    FIBER_RICH("Lif destekli"),
    HABIT_ADAPTED("Alışkanlığına uygun"),
    LIGHT_OPTION("Hafif seçenek"),
    DISCOVERY("Yeni kombinasyon"),
}

@Immutable
data class MealRecommendation(
    val id: String,
    val mealType: MealType,
    val title: String,
    val styleTag: RecommendationStyleTag,
    val items: List<RecommendedFoodPortion>,
    val totalCalories: Int,
    val totalProtein: Float,
    val totalCarbs: Float,
    val totalFat: Float,
    val micronutrientHighlights: List<MicronutrientContribution>,
    val missingMicronutrientNote: String?,
    val reasons: List<String>,
    val goalImpact: DailyGoalImpactPreview,
    val fitScore: Int, // 0..100
    val isPersonalized: Boolean,
)

@Immutable
data class IntelligenceReport(
    val mealType: MealType,
    val budget: MealBudget,
    val eatingProfile: UserEatingProfile,
    val recommendations: List<MealRecommendation>,
    val prioritizedMicronutrientNote: String?,
    val coldStartNote: String?,
)

@Immutable
data class CustomPortionOptimizationResult(
    val items: List<RecommendedFoodPortion>,
    val totalCalories: Int,
    val totalProtein: Float,
    val totalCarbs: Float,
    val totalFat: Float,
    val fitScore: Int,
    val note: String,
)
