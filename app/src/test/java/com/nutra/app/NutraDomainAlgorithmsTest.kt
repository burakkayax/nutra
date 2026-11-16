package com.nutra.app

import com.nutra.app.core.database.TurkishFoodSeedCatalog
import com.nutra.app.core.network.GeminiAiService
import com.nutra.app.domain.model.ActivityLevel
import com.nutra.app.domain.model.BiologicalSex
import com.nutra.app.domain.model.FoodCategory
import com.nutra.app.domain.model.GoalType
import com.nutra.app.domain.model.MacroGoals
import com.nutra.app.domain.model.MealLogItem
import com.nutra.app.domain.model.MealType
import com.nutra.app.domain.model.WeightEntry
import com.nutra.app.domain.usecase.ContextualMealRankerAndComboEngine
import com.nutra.app.domain.usecase.MacroGoalBounds
import com.nutra.app.domain.usecase.NutritionCalculatorUseCase
import com.nutra.app.domain.usecase.TurkishTextNormalizer
import com.nutra.app.domain.usecase.WeightTrendCalculator
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NutraDomainAlgorithmsTest {

    @Test
    fun turkishTextNormalizer_matchesTurkishCharactersIgnoringDiacritics() {
        assertTrue(TurkishTextNormalizer.matchesQuery("Izgara Köfte", "kofte"))
        assertTrue(TurkishTextNormalizer.matchesQuery("Mercimek Çorbası", "mercimek corbasi"))
        assertTrue(TurkishTextNormalizer.matchesQuery("Süzme Yoğurt", "suzme yogurt"))
        assertTrue(TurkishTextNormalizer.matchesQuery("Tam Buğday Ekmeği", "bugday ekmegi"))
    }

    @Test
    fun nutritionCalculator_calculatesMifflinStJeorTargetsAccurately() {
        val useCase = NutritionCalculatorUseCase()
        val goals = useCase.calculateGoals(
            sex = BiologicalSex.MALE,
            age = 28,
            heightCm = 178,
            weightKg = 78f,
            activityLevel = ActivityLevel.MODERATE,
            goalType = GoalType.LOSE_WEIGHT,
        )

        // BMR = 10*78 + 6.25*178 - 5*28 + 5 = 780 + 1112.5 - 140 + 5 = 1757.5
        // TDEE = 1757.5 * 1.55 = 2724.1 -> -400 = ~2320 kcal
        assertEquals(2320, goals.calories)
        assertTrue(goals.proteinGrams >= 160)
        assertTrue(goals.waterMl in 2500..3000)
    }

    @Test
    fun nutritionCalculator_recalculatesCaloriesWhenAnyMacroChanges() {
        val useCase = NutritionCalculatorUseCase()
        val initial = MacroGoals(
            calories = 2320,
            proteinGrams = 186,
            carbsGrams = 232,
            fatGrams = 72,
            waterMl = 2700,
        )

        val higherProtein = useCase.recalculateCaloriesFromMacros(
            initial.copy(proteinGrams = 196),
        )
        assertEquals(2360, higherProtein.calories)
        assertEquals(232, higherProtein.carbsGrams)
        assertEquals(72, higherProtein.fatGrams)
        assertEquals(2700, higherProtein.waterMl)

        val lowerFat = useCase.recalculateCaloriesFromMacros(
            higherProtein.copy(fatGrams = 62),
        )
        assertEquals(2270, lowerFat.calories)
        assertEquals(196, lowerFat.proteinGrams)
        assertEquals(232, lowerFat.carbsGrams)

        val higherCarbs = useCase.recalculateCaloriesFromMacros(
            lowerFat.copy(carbsGrams = 250),
        )
        assertEquals(2342, higherCarbs.calories)
    }

    @Test
    fun nutritionCalculator_scalesAllMacrosProportionallyWhenCalorieGoalChanges() {
        val useCase = NutritionCalculatorUseCase()
        val baseline = MacroGoals(
            calories = 2320,
            proteinGrams = 186,
            carbsGrams = 232,
            fatGrams = 72,
            waterMl = 2800,
        )

        val reduced = useCase.scaleMacrosForCalorieGoal(
            baselineGoals = baseline,
            targetCalories = 1800,
        )
        assertEquals(1800, reduced.calories)
        assertTrue(reduced.proteinGrams < baseline.proteinGrams)
        assertTrue(reduced.carbsGrams < baseline.carbsGrams)
        assertTrue(reduced.fatGrams < baseline.fatGrams)
        assertEquals(2800, reduced.waterMl)

        val macroSumKcal = reduced.proteinGrams * 4 + reduced.carbsGrams * 4 + reduced.fatGrams * 9
        assertTrue(kotlin.math.abs(macroSumKcal - 1800) <= 4)

        val basePShare = (baseline.proteinGrams * 4f) / 2320f
        val newPShare = (reduced.proteinGrams * 4f) / macroSumKcal.toFloat()
        assertEquals(basePShare, newPShare, 0.015f)

        val restored = useCase.scaleMacrosForCalorieGoal(
            baselineGoals = baseline,
            targetCalories = 2320,
        )
        assertEquals(baseline, restored)

        val cappedHigh = useCase.scaleMacrosForCalorieGoal(
            baselineGoals = baseline,
            targetCalories = 6000,
            bounds = MacroGoalBounds(),
        )
        assertEquals(6000, cappedHigh.calories)
        assertEquals(400, cappedHigh.proteinGrams)
        assertEquals(600, cappedHigh.carbsGrams)
        assertTrue(cappedHigh.fatGrams in 20..250)
    }

    @Test
    fun weightTrendCalculator_computesSmoothExponentialMovingAverage() {
        val calculator = WeightTrendCalculator()
        val entries = listOf(
            WeightEntry(1, "2026-11-10", 76.0f, 1000L),
            WeightEntry(2, "2026-11-11", 77.2f, 2000L), // Sudden water spike
            WeightEntry(3, "2026-11-12", 75.8f, 3000L),
        )

        val trend = calculator.calculateTrend(entries, smoothingFactor = 0.25f)
        assertEquals(3, trend.size)
        assertEquals(76.0f, trend[0].emaTrendKg, 0.01f)
        // Day 2 EMA should filter out most of the +1.2kg spike (76.0 * 0.75 + 77.2 * 0.25 = 76.3)
        assertEquals(76.3f, trend[1].emaTrendKg, 0.05f)
    }

    @Test
    fun contextualMealRanker_prioritizesMealSpecificFrequentFoodsAndCombos() {
        val engine = ContextualMealRankerAndComboEngine()
        val catalog = TurkishFoodSeedCatalog.initialFoods
        val egg = catalog.first { it.name.contains("Yumurta") }.copy(id = 1L)
        val cheese = catalog.first { it.name.contains("Ezine") }.copy(id = 2L)
        val soup = catalog.first { it.name.contains("Mercimek") }.copy(id = 3L)
        val testCatalog = listOf(soup, egg, cheese)

        val historicalLogs = listOf(
            MealLogItem(1, "2026-11-13", MealType.BREAKFAST, 1L, egg.name, "1 Adet", 2f, 100f, 155, 12.6f, 1.1f, 10.6f, 100L),
            MealLogItem(2, "2026-11-13", MealType.BREAKFAST, 2L, cheese.name, "1 Dilim", 1f, 30f, 87, 5.2f, 0.5f, 7.0f, 101L),
            MealLogItem(3, "2026-11-14", MealType.BREAKFAST, 1L, egg.name, "1 Adet", 2f, 100f, 155, 12.6f, 1.1f, 10.6f, 200L),
            MealLogItem(4, "2026-11-14", MealType.BREAKFAST, 2L, cheese.name, "1 Dilim", 1f, 30f, 87, 5.2f, 0.5f, 7.0f, 201L),
            MealLogItem(5, "2026-11-15", MealType.BREAKFAST, 1L, egg.name, "1 Adet", 2f, 100f, 155, 12.6f, 1.1f, 10.6f, 300L),
        )

        val rankedForBreakfast = engine.rankFoodsForMeal(
            allFoods = testCatalog,
            historicalLogs = historicalLogs,
            mealType = MealType.BREAKFAST,
            query = "",
            selectedCategory = FoodCategory.ALL,
        )
        assertEquals(egg.name, rankedForBreakfast[0].name)
        assertEquals(cheese.name, rankedForBreakfast[1].name)
        assertEquals(soup.name, rankedForBreakfast[2].name)

        val combos = engine.buildSmartComboSuggestions(
            mealType = MealType.BREAKFAST,
            currentDateMealItems = emptyList(),
            historicalLogs = historicalLogs,
            catalogFoods = testCatalog,
        )
        assertTrue(combos.isNotEmpty())
        assertEquals(2, combos.first().coOccurrenceCount)
    }

    @Test
    fun onDeviceNluParser_splitsNaturalLanguageIntoAccurateFoodItems() {
        val aiService = GeminiAiService(OkHttpClient())
        val catalog = TurkishFoodSeedCatalog.initialFoods

        val items = aiService.parseWithOnDeviceNlu(
            text = "2 haşlanmış yumurta, 30g ezine beyaz peynir ve 1 ince dilim tam buğday ekmeği",
            catalogFoods = catalog,
        )

        assertEquals(3, items.size)
        assertTrue(items[0].name.contains("Yumurta"))
        assertEquals(100f, items[0].baseGrams, 0.1f)
        assertTrue(items[1].name.contains("Ezine"))
        assertEquals(30f, items[1].baseGrams, 0.1f)
        assertTrue(items[2].name.contains("Tam Buğday"))
    }
}
