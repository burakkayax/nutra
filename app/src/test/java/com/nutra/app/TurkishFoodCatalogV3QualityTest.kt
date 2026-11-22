package com.nutra.app

import com.nutra.app.core.database.TurkishFoodSeedCatalog
import com.nutra.app.domain.model.AllergenTag
import com.nutra.app.domain.model.FoodDietFlags
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class TurkishFoodCatalogV3QualityTest {

    @Test
    fun rawJsonAsset_declaresCatalogVersion3With621StructuredFoods() {
        val stream = javaClass.classLoader?.getResourceAsStream("turkish_food_catalog.json")
        assertNotNull("turkish_food_catalog.json must be available on test classpath", stream)
        val raw = stream!!.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val root = Json.parseToJsonElement(raw).jsonObject

        assertEquals(3, root.getValue("catalogVersion").jsonPrimitive.int)
        val foods = root.getValue("foods").jsonArray
        assertEquals(621, foods.size)

        for (elem in foods) {
            val obj = elem.jsonObject
            assertTrue("Every v3 food must have explicit allergens array", obj.containsKey("allergens"))
            assertTrue("Every v3 food must have explicit dietFlags array", obj.containsKey("dietFlags"))
        }
    }

    @Test
    fun all621Foods_satisfyV3PortionUnitTypoAllergenAndTurKompEnergyInvariants() {
        val foods = TurkishFoodSeedCatalog.initialFoods
        assertEquals(621, foods.size)

        val byCode = foods.associateBy { it.catalogCode }
        var barcodeCount = 0
        for (food in foods) {
            assertTrue("${food.catalogCode} must have allergenVerified=true", food.allergenVerified)
            assertEquals("${food.catalogCode} must have catalogVersion=3", 3, food.catalogVersion)
            assertTrue(
                "${food.catalogCode} (${food.name}) defaultUnitLabel exceeds 20 chars: '${food.defaultUnitLabel}'",
                food.defaultUnitLabel.length <= 20,
            )

            val defaultServingKcal = food.caloriesPer100g * (food.defaultUnitGrams / 100f)
            assertTrue(
                "${food.catalogCode} (${food.name}) default serving too high: $defaultServingKcal kcal (${food.defaultUnitGrams}g)",
                defaultServingKcal < 650f,
            )

            // Animal-derived sub-flags must imply containsAnimalProduct=true
            val df = food.dietFlags
            if (df.containsRedMeat || df.containsPoultry || df.containsFish || df.containsDairy || df.containsEgg || df.containsHoney) {
                assertTrue("${food.catalogCode} animal sub-flag must imply containsAnimalProduct=true", df.containsAnimalProduct)
            }

            // Known typos must remain absent
            assertFalse("${food.catalogCode} still has typo 'kavulmuş'", food.name.contains("kavulmuş", ignoreCase = true))
            assertFalse("${food.catalogCode} still has missing space 'Süt(Yağsız)'", food.name.contains("Süt(Yağsız)"))

            // When fiber > carbs (TürKomp / EU available-carb labeling), 4P + 4C + 9F + 2*Fiber must closely match kcal
            val fiber = food.fiberPer100g ?: 0f
            if (fiber > food.carbsPer100g + 0.1f) {
                val euEnergyKcal = food.proteinPer100g * 4f + food.carbsPer100g * 4f + food.fatPer100g * 9f + fiber * 2f
                assertTrue(
                    "${food.catalogCode} (${food.name}) fiber>carbs must follow TürKomp/EU available-carb energy: " +
                        "declared=${food.caloriesPer100g} vs 4P+4C+9F+2Fib=$euEnergyKcal",
                    abs(euEnergyKcal - food.caloriesPer100g) <= 20f,
                )
            }

            if (!food.barcode.isNullOrBlank()) barcodeCount++
        }

        assertEquals(100, barcodeCount)

        // Spot-check critical v3 corrections
        val oliveOil = byCode.getValue("TR0338")
        assertEquals(10f, oliveOil.defaultUnitGrams, 0.01f)
        assertEquals("1 Yemek Kaşığı", oliveOil.defaultUnitLabel)
        val cupAlt = oliveOil.alternateUnits.firstOrNull { it.label.contains("Su Bardağı") }
        assertNotNull("Olive oil must retain Su Bardağı alternate unit", cupAlt)
        assertEquals(184f, cupAlt!!.grams, 0.5f)

        val turkishCoffee = byCode.getValue("TR0726")
        assertEquals(0f, turkishCoffee.fiberPer100g ?: 0f, 0.01f)

        val kasapKofte = byCode.getValue("TR0838")
        assertTrue("Kasap Köfte fiber should be realistic (<= 2g), got ${kasapKofte.fiberPer100g}", (kasapKofte.fiberPer100g ?: 0f) <= 2f)
        assertTrue(kasapKofte.dietFlags.containsRedMeat)
        assertTrue(AllergenTag.FISH_SEAFOOD !in kasapKofte.allergens)
    }
}
