package com.nutra.app

import com.nutra.app.core.database.FoodItemEntity
import com.nutra.app.core.designsystem.NutraFormatters
import com.nutra.app.core.network.GeminiAiService
import com.nutra.app.core.network.OpenFoodFactsService
import com.nutra.app.domain.model.AppResult
import com.nutra.app.domain.model.ComboFoodEntry
import com.nutra.app.domain.model.DailyNutritionSummary
import com.nutra.app.domain.model.FoodCategory
import com.nutra.app.domain.model.FoodItem
import com.nutra.app.domain.model.MacroGoals
import com.nutra.app.domain.model.MealLogItem
import com.nutra.app.domain.model.MealSummary
import com.nutra.app.domain.model.MealType
import com.nutra.app.domain.model.ServingUnit
import com.nutra.app.domain.model.SmartComboSuggestion
import com.nutra.app.domain.usecase.ContextualMealRankerAndComboEngine
import com.nutra.app.domain.usecase.TurkishTextNormalizer
import com.nutra.app.feature.analytics.AnalyticsUiState
import com.nutra.app.feature.dashboard.DashboardUiState
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class NutraOptimizationTest {

    @Test
    fun turkishTextNormalizer_collapsesWhitespaceAndNormalizesTurkishChars() {
        assertEquals(
            "suzme yogurt 100g",
            TurkishTextNormalizer.normalize("  Süzme   Yoğurt\t\n 100g  "),
        )
        assertEquals("", TurkishTextNormalizer.normalize("   \n\t  "))
        assertEquals("igdir sicagi", TurkishTextNormalizer.normalize("Iğdır Sıcağı"))

        val tokens = TurkishTextNormalizer.tokenizeNormalized("tam bugday")
        assertTrue(TurkishTextNormalizer.matchesNormalizedTokens("tam bugday ekmegi lifli", tokens))
        assertFalse(TurkishTextNormalizer.matchesNormalizedTokens("beyaz ekmek", tokens))
    }

    @Test
    fun foodItemEntity_fastPathAndJsonRoundtripProduceEquivalentDomainModels() {
        val noAlternates = FoodItem(
            id = 10L,
            name = "Sade Kahve",
            subtitle = "Şekersiz filtre kahve",
            category = FoodCategory.DRINKS,
            caloriesPer100g = 2,
            proteinPer100g = 0.2f,
            carbsPer100g = 0f,
            fatPer100g = 0f,
            defaultUnitLabel = "1 Fincan",
            defaultUnitGrams = 200f,
            alternateUnits = emptyList(),
            preferredMealTypes = emptySet(),
        )
        val emptyEntity = FoodItemEntity.fromDomain(noAlternates, "sade kahve sekersiz filtre kahve")
        val decodedEmpty = emptyEntity.toDomain()
        assertTrue(decodedEmpty.alternateUnits.isEmpty())
        assertTrue(decodedEmpty.preferredMealTypes.isEmpty())
        assertEquals("sade kahve", decodedEmpty.normalizedName)
        assertEquals("sade kahve sekersiz filtre kahve", decodedEmpty.normalizedSearchText)

        val withAlternates = noAlternates.copy(
            alternateUnits = listOf(ServingUnit("Büyük Kupa", 350f)),
            preferredMealTypes = setOf(MealType.BREAKFAST, MealType.SNACK),
        )
        val fullDecoded = FoodItemEntity.fromDomain(withAlternates, "sade kahve").toDomain()
        assertEquals(1, fullDecoded.alternateUnits.size)
        assertEquals("Büyük Kupa", fullDecoded.alternateUnits.first().label)
        assertEquals(setOf(MealType.BREAKFAST, MealType.SNACK), fullDecoded.preferredMealTypes)
    }

    @Test
    fun nutraFormatters_formatsTurkishNumbersDatesAndWaterConsistently() {
        assertEquals("2.250", NutraFormatters.formatInt(2250))
        assertEquals("75,4", NutraFormatters.formatKg(75.4f))
        assertEquals("750 ml", NutraFormatters.formatWaterLitres(750))
        assertEquals("2,1 L", NutraFormatters.formatWaterLitres(2100))

        val today = LocalDate.now().toString()
        assertEquals("Bugün", NutraFormatters.formatShortDate(today, todayIso = today))
        assertEquals("10 Kas", NutraFormatters.formatShortDate("2026-11-10", todayIso = "2026-11-16"))
        assertFalse(NutraFormatters.formatShortWeekday("2026-11-16").isBlank())
    }

    @Test
    fun openFoodFactsParser_handlesJsonNullAndUnexpectedTypesWithoutCrashing() {
        val service = OpenFoodFactsService(OkHttpClient())
        val payload = """
            {
              "status": 1,
              "product": {
                "product_name_tr": null,
                "product_name": "Yulaf Sütü",
                "brands": ["Organik", "Test"],
                "serving_quantity": null,
                "nutriments": {
                  "energy-kcal_100g": 45.2,
                  "proteins_100g": 1.1,
                  "carbohydrates_100g": null,
                  "fat_100g": 1.5
                }
              }
            }
        """.trimIndent()

        val result = service.parseProductResponse("8690000000001", payload)
        assertTrue(result is AppResult.Success)
        val food = (result as AppResult.Success).data
        assertEquals("Yulaf Sütü", food.name)
        assertEquals("Paketli Ürün", food.subtitle)
        assertEquals(45, food.caloriesPer100g)
        assertEquals(1.1f, food.proteinPer100g, 0.01f)
        assertEquals(20f, food.carbsPer100g, 0.01f)
        assertEquals(1.5f, food.fatPer100g, 0.01f)
    }

    @Test
    fun contextualMealRanker_preparedStatsProduceIdenticalOrdering() {
        val engine = ContextualMealRankerAndComboEngine()
        val foods = listOf(
            FoodItem(
                id = 1L,
                name = "Haşlanmış Yumurta",
                subtitle = "Kahvaltılık",
                category = FoodCategory.BREAKFAST,
                caloriesPer100g = 155,
                proteinPer100g = 12.6f,
                carbsPer100g = 1.1f,
                fatPer100g = 10.6f,
                defaultUnitLabel = "1 Adet",
                defaultUnitGrams = 50f,
                preferredMealTypes = setOf(MealType.BREAKFAST),
                isFavorite = true,
            ),
            FoodItem(
                id = 2L,
                name = "Izgara Tavuk Göğsü",
                subtitle = "Protein",
                category = FoodCategory.MEAT_POULTRY,
                caloriesPer100g = 165,
                proteinPer100g = 31f,
                carbsPer100g = 0f,
                fatPer100g = 3.6f,
                defaultUnitLabel = "1 Porsiyon",
                defaultUnitGrams = 150f,
                preferredMealTypes = setOf(MealType.LUNCH, MealType.DINNER),
            ),
        )
        val logs = listOf(
            MealLogItem(1L, "2026-11-16", MealType.BREAKFAST, 1L, "Haşlanmış Yumurta", "1 Adet", 2f, 100f, 155, 12.6f, 1.1f, 10.6f, 1000L),
            MealLogItem(2L, "2026-11-16", MealType.LUNCH, 2L, "Izgara Tavuk Göğsü", "1 Porsiyon", 1f, 150f, 248, 46.5f, 0f, 5.4f, 2000L),
        )

        val stats = engine.prepareMealUsageStats(logs, MealType.BREAKFAST)
        assertEquals(
            engine.rankFoodsForMeal(foods, logs, MealType.BREAKFAST, "", FoodCategory.ALL),
            engine.rankFoodsWithStats(foods, stats, MealType.BREAKFAST, "", FoodCategory.ALL),
        )
        assertEquals(
            engine.rankFoodsForMeal(foods, logs, MealType.BREAKFAST, "tavuk", FoodCategory.ALL),
            engine.rankFoodsWithStats(foods, stats, MealType.BREAKFAST, "tavuk", FoodCategory.ALL),
        )
    }

    @Test
    fun geminiService_extractStructuredTextSkipsThoughtPartsAndStripsMarkdownFences() {
        val service = GeminiAiService(OkHttpClient())
        val responseWithThought = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      { "thought": true, "text": "Let me estimate the plate macros carefully..." },
                      { "text": "```json\n{\"items\":[{\"name\":\"Yumurta\"}]}\n```" }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        assertEquals(
            "{\"items\":[{\"name\":\"Yumurta\"}]}",
            service.extractStructuredText(responseWithThought),
        )

        val thoughtOnlyResponse = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      { "thought": true, "text": "Internal reasoning only" }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()
        assertNull(service.extractStructuredText(thoughtOnlyResponse))
    }

    @Test
    fun precomputedNutritionSummariesAndSmartComboTotalsAreAccurate() {
        val log1 = MealLogItem(1L, "2026-11-19", MealType.BREAKFAST, 1L, "Yumurta", "1 Adet", 2f, 100f, 155, 12.6f, 1.1f, 10.6f, 100L)
        val log2 = MealLogItem(2L, "2026-11-19", MealType.BREAKFAST, 2L, "Peynir", "1 Dilim", 1f, 30f, 87, 5.2f, 0.5f, 7.0f, 200L)
        val breakfast = MealSummary(MealType.BREAKFAST, listOf(log1, log2))
        val lunch = MealSummary(
            MealType.LUNCH,
            listOf(MealLogItem(3L, "2026-11-19", MealType.LUNCH, 3L, "Tavuk", "1 Gram", 150f, 150f, 248, 46.5f, 0f, 5.4f, 300L)),
        )
        val daily = DailyNutritionSummary("2026-11-19", MacroGoals(), listOf(breakfast, lunch), 1200)

        assertEquals(242, breakfast.totalCalories)
        assertEquals(17.8f, breakfast.totalProtein, 0.01f)
        assertEquals(490, daily.consumedCalories)
        assertEquals(64.3f, daily.consumedProtein, 0.01f)
        assertEquals(1.6f, daily.consumedCarbs, 0.01f)
        assertEquals(23.0f, daily.consumedFat, 0.01f)

        val combo = SmartComboSuggestion(
            id = "c1",
            title = "Kahvaltı",
            subtitle = "Sık",
            items = listOf(
                ComboFoodEntry(1L, "Yumurta", "1 Adet", 1f, 50f, 78, 6.3f, 0.6f, 5.3f),
                ComboFoodEntry(2L, "Peynir", "1 Dilim", 1f, 30f, 87, 5.2f, 0.5f, 7.0f),
            ),
            coOccurrenceCount = 3,
        )
        assertEquals(165, combo.totalCalories)
        assertEquals("Yumurta · Peynir", combo.itemsPreview)
    }

    @Test
    fun uiStates_haveValidDefaultsForWeekStartAndEmptyAnalytics() {
        val dashboard = DashboardUiState()
        assertEquals(DayOfWeek.MONDAY, dashboard.weekStart.dayOfWeek)

        val analytics = AnalyticsUiState()
        assertNull(analytics.activeDayPoint)
        assertNull(analytics.activeWaterDayPoint)
    }
}
