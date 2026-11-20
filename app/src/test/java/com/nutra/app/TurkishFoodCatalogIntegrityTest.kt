package com.nutra.app

import com.nutra.app.core.database.FoodItemEntity
import com.nutra.app.core.database.TurkishFoodSeedCatalog
import com.nutra.app.domain.model.FoodCategory
import com.nutra.app.domain.model.MealLogItem
import com.nutra.app.domain.model.MealType
import com.nutra.app.domain.model.ServingUnitLabels
import com.nutra.app.domain.repository.NutraBackupPayload
import com.nutra.app.domain.repository.validateBackupPayload
import com.nutra.app.domain.usecase.ContextualMealRankerAndComboEngine
import com.nutra.app.domain.usecase.TurkishTextNormalizer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TurkishFoodCatalogIntegrityTest {

    @Test
    fun catalogAsset_loadsExact621UniqueFoodsWithValidMacrosUnitsAndBarcodes() {
        val foods = TurkishFoodSeedCatalog.initialFoods
        assertEquals(TurkishFoodSeedCatalog.EXPECTED_CATALOG_SIZE, foods.size)

        val codes = foods.mapNotNull { it.catalogCode }
        assertEquals(621, codes.size)
        assertEquals(621, codes.toSet().size)
        assertTrue(codes.all { it.matches(Regex("^TR\\d{4}$")) })

        val mlFoods = foods.filter { it.isLiquid }
        val gramFoods = foods.filter { !it.isLiquid }
        assertEquals(28, mlFoods.size)
        assertEquals(593, gramFoods.size)

        val barcodes = foods.mapNotNull { it.barcode }
        assertEquals(100, barcodes.size)
        assertEquals(100, barcodes.toSet().size)
        assertTrue(barcodes.all { it.length in 8..14 && it.all(Char::isDigit) })

        for (food in foods) {
            assertTrue("Blank name for ${food.catalogCode}", food.name.isNotBlank())
            assertTrue("Negative kcal for ${food.catalogCode}", food.caloriesPer100g >= 0)
            assertTrue("Invalid protein for ${food.catalogCode}", food.proteinPer100g.isFinite() && food.proteinPer100g >= 0f)
            assertTrue("Invalid carbs for ${food.catalogCode}", food.carbsPer100g.isFinite() && food.carbsPer100g >= 0f)
            assertTrue("Invalid fat for ${food.catalogCode}", food.fatPer100g.isFinite() && food.fatPer100g >= 0f)
            assertTrue("Blank default unit for ${food.catalogCode}", food.defaultUnitLabel.isNotBlank())
            assertTrue("Non-positive default unit amount for ${food.catalogCode}", food.defaultUnitGrams > 0f)
            val redundantSelfUnit = Regex(
                "\\(\\s*${kotlin.math.round(food.defaultUnitGrams).toInt()}\\s*${food.displayUnitSuffix}\\s*\\)\\s*$",
                RegexOption.IGNORE_CASE,
            )
            assertFalse(
                "Default label should not duplicate UI amount suffix: ${food.catalogCode} '${food.defaultUnitLabel}'",
                redundantSelfUnit.containsMatchIn(food.defaultUnitLabel),
            )
            for (alt in food.alternateUnits) {
                assertTrue("Blank alt unit for ${food.catalogCode}", alt.label.isNotBlank())
                assertTrue("Non-positive alt unit amount for ${food.catalogCode}", alt.grams > 0f)
            }
            val extendedValues = listOfNotNull(
                food.saturatedFatPer100g,
                food.sugarPer100g,
                food.fiberPer100g,
                food.saltPer100g,
                food.sodiumMgPer100g,
                food.calciumMgPer100g,
                food.potassiumMgPer100g,
                food.ironMgPer100g,
                food.vitaminCMgPer100g,
            )
            assertTrue("Invalid extended nutrient for ${food.catalogCode}", extendedValues.all { it.isFinite() && it >= 0f })
        }

        val browsingCategories = FoodCategory.entries.filter { it != FoodCategory.ALL && it != FoodCategory.CUSTOM }
        for (cat in browsingCategories) {
            val count = foods.count { it.category == cat }
            assertTrue("Expected at least 15 foods in category $cat, found $count", count >= 15)
        }
    }

    @Test
    fun catalogSearch_findsBrandsTurkishCharsCommaTitlesAndApostrophes() {
        val catalog = TurkishFoodSeedCatalog.initialFoods.mapIndexed { idx, f -> f.copy(id = idx + 1L) }
        val engine = ContextualMealRankerAndComboEngine()

        val elmaResults = engine.rankFoodsForMeal(catalog, emptyList(), MealType.SNACK, "elma", FoodCategory.ALL)
        assertTrue(elmaResults.isNotEmpty())
        assertEquals("Elma", elmaResults.first().name)

        val ayranResults = engine.rankFoodsForMeal(catalog, emptyList(), MealType.LUNCH, "ayran", FoodCategory.ALL)
        assertTrue(ayranResults.isNotEmpty())
        assertEquals("Ayran", ayranResults.first().name)

        val laysResults = engine.rankFoodsForMeal(catalog, emptyList(), MealType.SNACK, "lays", FoodCategory.ALL)
        assertTrue("Expected Lay's / Lay’s products for 'lays'", laysResults.size >= 2)

        val breadResults = engine.rankFoodsForMeal(catalog, emptyList(), MealType.BREAKFAST, "ekmek tam bugday", FoodCategory.ALL)
        assertTrue(breadResults.any { it.catalogCode == "TR0122" })

        val etiResults = engine.rankFoodsForMeal(catalog, emptyList(), MealType.SNACK, "eti", FoodCategory.ALL)
        assertTrue("Expected Eti branded products", etiResults.any { it.brand.equals("Eti", ignoreCase = true) })

        val dardanelResults = engine.rankFoodsForMeal(catalog, emptyList(), MealType.LUNCH, "dardanel", FoodCategory.ALL)
        assertTrue("Expected Dardanel branded products", dardanelResults.any { it.brand.equals("Dardanel", ignoreCase = true) })
    }

    @Test
    fun liquidFoods_useMlServingUnitsAndFormatQuantitiesWithMl() {
        val milk = TurkishFoodSeedCatalog.initialFoods.first { it.catalogCode == "TR0447" }
        assertTrue(milk.isLiquid)
        assertEquals("ml", milk.displayUnitSuffix)
        val unitLabels = milk.allUnits.map { it.label }
        assertTrue(unitLabels.contains(ServingUnitLabels.BASE_100ML))
        assertTrue(unitLabels.contains(ServingUnitLabels.RAW_ML))
        assertFalse(unitLabels.contains(ServingUnitLabels.RAW_GRAM))

        val rawMlLog = MealLogItem(
            id = 1L,
            dateIso = "2026-11-21",
            mealType = MealType.BREAKFAST,
            foodId = 10L,
            foodName = milk.name,
            unitLabel = ServingUnitLabels.RAW_ML,
            multiplier = 250f,
            totalGrams = 250f,
            calories = milk.caloriesForGrams(250f),
            proteinGrams = milk.proteinForGrams(250f),
            carbsGrams = milk.carbsForGrams(250f),
            fatGrams = milk.fatForGrams(250f),
            timestampMillis = 1000L,
            baseUnit = "ml",
        )
        assertEquals("250 ml", rawMlLog.formattedQuantity)

        val glassLog = rawMlLog.copy(unitLabel = "1 Su Bardağı", multiplier = 1f, totalGrams = 200f)
        assertEquals("1x 1 Su Bardağı (200 ml)", glassLog.formattedQuantity)
    }

    @Test
    fun foodItemEntity_roundTripPreservesExtendedNutrientsAndCatalogMetadata() {
        val tuna = TurkishFoodSeedCatalog.initialFoods.first { it.catalogCode == "TR0001" }
        val entity = FoodItemEntity.fromDomain(tuna, tuna.normalizedSearchText)
        val decoded = entity.toDomain()

        assertEquals(tuna.catalogCode, decoded.catalogCode)
        assertEquals(tuna.brand, decoded.brand)
        assertEquals(tuna.barcode, decoded.barcode)
        assertEquals(tuna.baseUnit, decoded.baseUnit)
        assertEquals(tuna.saturatedFatPer100g, decoded.saturatedFatPer100g)
        assertEquals(tuna.sugarPer100g, decoded.sugarPer100g)
        assertEquals(tuna.fiberPer100g, decoded.fiberPer100g)
        assertEquals(tuna.saltPer100g, decoded.saltPer100g)
        assertEquals(tuna.sodiumMgPer100g, decoded.sodiumMgPer100g)
        assertEquals(tuna.calciumMgPer100g, decoded.calciumMgPer100g)
        assertEquals(tuna.potassiumMgPer100g, decoded.potassiumMgPer100g)
        assertEquals(tuna.ironMgPer100g, decoded.ironMgPer100g)
        assertEquals(tuna.vitaminCMgPer100g, decoded.vitaminCMgPer100g)
        assertTrue(decoded.hasExtendedDetails)

        // Check scaled nutrient helper
        val scaledSalt = decoded.scaledNutrient(decoded.saltPer100g, 160f)
        assertNotNull(scaledSalt)
        assertEquals(2.7f, scaledSalt!!, 0.1f)
    }

    @Test
    fun curatedCombos_workAcrossAllFourMealsOnReal621Catalog() {
        val catalog = TurkishFoodSeedCatalog.initialFoods.mapIndexed { idx, f -> f.copy(id = idx + 1L) }
        val engine = ContextualMealRankerAndComboEngine()

        for (meal in MealType.entries) {
            val combos = engine.buildSmartComboSuggestions(
                mealType = meal,
                currentDateMealItems = emptyList(),
                historicalLogs = emptyList(),
                catalogFoods = catalog,
            )
            assertEquals("Expected 2 curated combos for $meal", 2, combos.size)
            assertTrue(combos.all { it.items.size >= 2 })
        }

        val snackCombos = engine.buildSmartComboSuggestions(
            mealType = MealType.SNACK,
            currentDateMealItems = emptyList(),
            historicalLogs = emptyList(),
            catalogFoods = catalog,
        )
        assertEquals(listOf("Elma", "Badem"), snackCombos.first().items.map { it.foodName })
    }

    @Test
    fun legacyBackupV1Json_deserializesCleanlyWithNewFieldsDefaulted() {
        val json = Json { ignoreUnknownKeys = true }
        val inlineLegacyV1Json = """
            {
              "version": 1,
              "exportedAtDateIso": "2026-11-17",
              "userProfile": {
                "sex": "MALE",
                "age": 29,
                "heightCm": 178,
                "currentWeightKg": 78.4,
                "targetWeightKg": 74.0,
                "activityLevel": "MODERATE",
                "goalType": "LOSE_WEIGHT",
                "goals": {
                  "calories": 2320,
                  "proteinGrams": 186,
                  "carbsGrams": 232,
                  "fatGrams": 72,
                  "waterMl": 2700
                },
                "onboardingCompleted": true,
                "themePreference": "SYSTEM",
                "useDynamicColor": false,
                "geminiApiKey": "",
                "geminiModel": "gemini-2.5-flash"
              },
              "customFoods": [
                {
                  "id": 1001,
                  "name": "Ev Yapımı Yulaf Lapası",
                  "subtitle": "Yarım yağlı süt ve tarçın",
                  "category": "BREAKFAST",
                  "caloriesPer100g": 118,
                  "proteinPer100g": 5.8,
                  "carbsPer100g": 15.2,
                  "fatPer100g": 3.4,
                  "defaultUnitLabel": "1 Kase",
                  "defaultUnitGrams": 260,
                  "alternateUnits": [],
                  "preferredMealTypes": ["BREAKFAST"],
                  "barcode": null,
                  "isFavorite": true,
                  "isCustom": true
                }
              ],
              "mealLogs": [
                {
                  "id": 1,
                  "dateIso": "2026-10-19",
                  "mealType": "BREAKFAST",
                  "foodId": null,
                  "foodName": "Haşlanmış Yumurta",
                  "unitLabel": "2 Adet (Orta)",
                  "multiplier": 1,
                  "totalGrams": 100,
                  "calories": 155,
                  "proteinGrams": 12.6,
                  "carbsGrams": 1.1,
                  "fatGrams": 10.6,
                  "timestampMillis": 1792387800000
                }
              ],
              "waterLogs": { "2026-10-19": 2500 },
              "weightEntries": [
                { "id": 1, "dateIso": "2026-10-19", "weightKg": 78.4, "timestampMillis": 1792387800000 }
              ]
            }
        """.trimIndent()

        val payload = json.decodeFromString<NutraBackupPayload>(inlineLegacyV1Json)
        assertNull(validateBackupPayload(payload))
        assertEquals(1, payload.mealLogs.size)
        assertTrue(payload.mealLogs.all { it.baseUnit == "g" })
        assertTrue(payload.customFoods.all { it.baseUnit == "g" && it.catalogCode == null && it.fiberPer100g == null })

        // Also validate local sample backup if available in workspace
        val localBackup = listOf(File("nutra_test_yedek.json"), File("../nutra_test_yedek.json")).firstOrNull { it.exists() }
        if (localBackup != null) {
            val localPayload = json.decodeFromString<NutraBackupPayload>(localBackup.readText(Charsets.UTF_8))
            assertNull(validateBackupPayload(localPayload))
            assertTrue(localPayload.mealLogs.all { it.baseUnit == "g" })
        }

        // Verify TurkishTextNormalizer keeps comma decimals intact while cleaning comma-separated food names
        assertEquals("1,5 porsiyon bulgur pilavi", TurkishTextNormalizer.normalize("1,5 porsiyon bulgur pilavı"))
        assertEquals("ekmek tam bugday unlu", TurkishTextNormalizer.normalize("Ekmek, tam buğday unlu"))
        assertEquals("lays firindan", TurkishTextNormalizer.normalize("Lay’s Fırından"))
    }
}
