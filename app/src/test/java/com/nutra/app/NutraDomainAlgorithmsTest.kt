package com.nutra.app

import com.nutra.app.core.database.TurkishFoodSeedCatalog
import com.nutra.app.core.network.GeminiAiService
import com.nutra.app.domain.model.ActivityLevel
import com.nutra.app.domain.model.BiologicalSex
import com.nutra.app.domain.model.FoodCategory
import com.nutra.app.domain.model.GoalType
import com.nutra.app.domain.model.MealLogItem
import com.nutra.app.domain.model.MealType
import com.nutra.app.domain.model.WeightEntry
import com.nutra.app.domain.usecase.ContextualMealRankerAndComboEngine
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
