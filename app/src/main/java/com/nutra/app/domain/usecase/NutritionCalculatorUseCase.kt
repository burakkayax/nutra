package com.nutra.app.domain.usecase

import com.nutra.app.domain.model.ActivityLevel
import com.nutra.app.domain.model.BiologicalSex
import com.nutra.app.domain.model.GoalType
import com.nutra.app.domain.model.MacroGoals
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt

object BodyMetricBounds {
    const val MIN_AGE = 14
    const val ADULT_MIN_AGE = 18
    const val MAX_AGE = 95
    const val MIN_HEIGHT_CM = 130
    const val MAX_HEIGHT_CM = 225
    const val MIN_WEIGHT_KG = 30f
    const val MAX_WEIGHT_KG = 300f
    const val MIN_WEIGHT_TENTHS = 300
    const val MAX_WEIGHT_TENTHS = 3000
}

data class MacroGoalBounds(
    val minCalories: Int = 1000,
    val maxCalories: Int = 6000,
    val minProtein: Int = 30,
    val maxProtein: Int = 400,
    val minCarbs: Int = 20,
    val maxCarbs: Int = 600,
    val minFat: Int = 20,
    val maxFat: Int = 250,
    val minWaterMl: Int = 1000,
    val maxWaterMl: Int = 7000,
)

class NutritionCalculatorUseCase @Inject constructor() {

    /**
     * Calculates daily Calorie, Protein, Carbohydrate, Fat, and Water targets
     * using the Mifflin-St Jeor equation.
     *
     * For ages 14-17 (adolescent observation mode), automatic calorie deficit/surplus
     * adjustments are disabled and maintenance-band reference targets are returned.
     */
    fun calculateGoals(
        sex: BiologicalSex,
        age: Int,
        heightCm: Int,
        weightKg: Float,
        activityLevel: ActivityLevel,
        goalType: GoalType,
    ): MacroGoals {
        val safeWeight = (if (weightKg.isFinite()) weightKg else 75f)
            .coerceIn(BodyMetricBounds.MIN_WEIGHT_KG, BodyMetricBounds.MAX_WEIGHT_KG)
        val safeHeight = heightCm.coerceIn(120, 230)
        val safeAge = age.coerceIn(BodyMetricBounds.MIN_AGE, 100)
        val isAdolescent = safeAge < BodyMetricBounds.ADULT_MIN_AGE
        val effectiveGoalType = if (isAdolescent) GoalType.MAINTAIN else goalType

        val sexConstant = when (sex) {
            BiologicalSex.MALE -> 5f
            BiologicalSex.FEMALE -> -161f
        }

        val bmr = (10f * safeWeight) + (6.25f * safeHeight) - (5f * safeAge) + sexConstant
        val tdee = bmr * activityLevel.multiplier
        val effectiveDelta = if (isAdolescent) 0 else effectiveGoalType.calorieDelta
        val rawCalories = (tdee + effectiveDelta).roundToInt()

        val clinicalMinCalories = when {
            isAdolescent -> 1500
            sex == BiologicalSex.MALE -> 1500
            else -> 1200
        }

        // Round to nearest 10 kcal for clean display
        val targetCalories = ((rawCalories + 5) / 10 * 10).coerceIn(clinicalMinCalories, 4800)

        val proteinGrams = ((targetCalories * effectiveGoalType.proteinRatio) / 4f).roundToInt().coerceAtLeast(60)
        val fatGrams = ((targetCalories * effectiveGoalType.fatRatio) / 9f).roundToInt().coerceAtLeast(40)
        val remainingCalories = (targetCalories - (proteinGrams * 4) - (fatGrams * 9)).coerceAtLeast(200)
        val carbsGrams = (remainingCalories / 4f).roundToInt()

        val rawWaterMl = (safeWeight * 35f).roundToInt()
        val waterMl = ((rawWaterMl + 50) / 100 * 100).coerceIn(1800, 4500)

        return MacroGoals(
            calories = targetCalories,
            proteinGrams = proteinGrams,
            carbsGrams = carbsGrams,
            fatGrams = fatGrams,
            waterMl = waterMl,
        )
    }

    /**
     * Recalculates the daily calorie goal directly from macro targets (4 kcal/g protein & carbs, 9 kcal/g fat).
     */
    fun recalculateCaloriesFromMacros(
        goals: MacroGoals,
        bounds: MacroGoalBounds = MacroGoalBounds(),
    ): MacroGoals {
        val protein = goals.proteinGrams.coerceIn(bounds.minProtein, bounds.maxProtein)
        val carbs = goals.carbsGrams.coerceIn(bounds.minCarbs, bounds.maxCarbs)
        val fat = goals.fatGrams.coerceIn(bounds.minFat, bounds.maxFat)
        val calories = ((protein * 4) + (carbs * 4) + (fat * 9))
            .coerceIn(bounds.minCalories, bounds.maxCalories)
        return goals.copy(
            calories = calories,
            proteinGrams = protein,
            carbsGrams = carbs,
            fatGrams = fat,
            waterMl = goals.waterMl.coerceIn(bounds.minWaterMl, bounds.maxWaterMl),
        )
    }

    /**
     * Scales protein, carbohydrate, and fat targets proportionally to match [targetCalories]
     * while preserving the macro energy ratios of [baselineGoals].
     */
    fun scaleMacrosForCalorieGoal(
        baselineGoals: MacroGoals,
        targetCalories: Int,
        bounds: MacroGoalBounds = MacroGoalBounds(),
    ): MacroGoals {
        val clampedCalories = targetCalories.coerceIn(bounds.minCalories, bounds.maxCalories)
        val baseProteinKcal = baselineGoals.proteinGrams.coerceAtLeast(0) * 4f
        val baseCarbsKcal = baselineGoals.carbsGrams.coerceAtLeast(0) * 4f
        val baseFatKcal = baselineGoals.fatGrams.coerceAtLeast(0) * 9f
        val totalMacroKcal = baseProteinKcal + baseCarbsKcal + baseFatKcal

        val (proteinShare, carbsShare, fatShare) = if (totalMacroKcal > 0f) {
            Triple(
                baseProteinKcal / totalMacroKcal,
                baseCarbsKcal / totalMacroKcal,
                baseFatKcal / totalMacroKcal,
            )
        } else {
            Triple(0.30f, 0.45f, 0.25f)
        }

        val pcShareSum = proteinShare + carbsShare
        val proteinOfPcShare = if (pcShareSum > 0f) proteinShare / pcShareSum else 0.5f

        var newFat = ((clampedCalories * fatShare) / 9f)
            .roundToInt()
            .coerceIn(bounds.minFat, bounds.maxFat)

        val remainingForPc = (clampedCalories - (newFat * 9)).coerceAtLeast(0)
        val idealProtein = (remainingForPc * proteinOfPcShare) / 4f
        val idealCarbs = (remainingForPc * (1f - proteinOfPcShare)) / 4f

        var newProtein = idealProtein.roundToInt().coerceIn(bounds.minProtein, bounds.maxProtein)
        val remainingAfterProtein = (remainingForPc - (newProtein * 4)).coerceAtLeast(0)
        val rawCarbs = if (remainingAfterProtein % 4 == 2 && idealCarbs < (remainingAfterProtein / 4) + 0.5f) {
            remainingAfterProtein / 4
        } else {
            (remainingAfterProtein / 4f).roundToInt()
        }
        val newCarbs = rawCarbs.coerceIn(bounds.minCarbs, bounds.maxCarbs)

        if (newCarbs != rawCarbs) {
            newProtein = ((remainingForPc - (newCarbs * 4)) / 4f)
                .roundToInt()
                .coerceIn(bounds.minProtein, bounds.maxProtein)
        }

        val remainingForFat = clampedCalories - (newProtein * 4) - (newCarbs * 4)
        if (abs(remainingForFat - (newFat * 9)) >= 9) {
            newFat = (remainingForFat / 9f).roundToInt().coerceIn(bounds.minFat, bounds.maxFat)
        }

        return baselineGoals.copy(
            calories = clampedCalories,
            proteinGrams = newProtein,
            carbsGrams = newCarbs,
            fatGrams = newFat,
            waterMl = baselineGoals.waterMl.coerceIn(bounds.minWaterMl, bounds.maxWaterMl),
        )
    }
}
