package com.nutra.app.domain.usecase

import com.nutra.app.domain.model.ActivityLevel
import com.nutra.app.domain.model.BiologicalSex
import com.nutra.app.domain.model.GoalType
import com.nutra.app.domain.model.MacroGoals
import javax.inject.Inject
import kotlin.math.roundToInt

class NutritionCalculatorUseCase @Inject constructor() {

    /**
     * Calculates daily Calorie, Protein, Carbohydrate, Fat, and Water targets
     * using the Mifflin-St Jeor equation.
     */
    fun calculateGoals(
        sex: BiologicalSex,
        age: Int,
        heightCm: Int,
        weightKg: Float,
        activityLevel: ActivityLevel,
        goalType: GoalType,
    ): MacroGoals {
        val safeWeight = weightKg.coerceIn(35f, 250f)
        val safeHeight = heightCm.coerceIn(120, 230)
        val safeAge = age.coerceIn(14, 100)

        val sexConstant = when (sex) {
            BiologicalSex.MALE -> 5f
            BiologicalSex.FEMALE -> -161f
        }

        val bmr = (10f * safeWeight) + (6.25f * safeHeight) - (5f * safeAge) + sexConstant
        val tdee = bmr * activityLevel.multiplier
        val rawCalories = (tdee + goalType.calorieDelta).roundToInt()

        // Round to nearest 10 kcal for clean display
        val targetCalories = ((rawCalories + 5) / 10 * 10).coerceIn(1200, 4800)

        val proteinGrams = ((targetCalories * goalType.proteinRatio) / 4f).roundToInt().coerceAtLeast(60)
        val fatGrams = ((targetCalories * goalType.fatRatio) / 9f).roundToInt().coerceAtLeast(40)
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
}
