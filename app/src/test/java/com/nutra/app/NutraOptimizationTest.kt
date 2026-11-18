package com.nutra.app

import com.nutra.app.core.database.FoodItemEntity
import com.nutra.app.core.designsystem.NutraFormatters
import com.nutra.app.core.network.OpenFoodFactsService
import com.nutra.app.domain.model.AppResult
import com.nutra.app.domain.model.FoodCategory
import com.nutra.app.domain.model.FoodItem
import com.nutra.app.domain.model.MealLogItem
import com.nutra.app.domain.model.MealType
import com.nutra.app.domain.model.ServingUnit
import com.nutra.app.domain.usecase.ContextualMealRankerAndComboEngine
import com.nutra.app.domain.usecase.TurkishTextNormalizer
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
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
}
