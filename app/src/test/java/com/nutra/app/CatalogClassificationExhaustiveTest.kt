package com.nutra.app

import com.nutra.app.core.database.TurkishFoodSeedCatalog
import com.nutra.app.domain.intelligence.EdibilityStatus
import com.nutra.app.domain.intelligence.FoodCulinaryClassifier
import com.nutra.app.domain.intelligence.MealRecommendationEngine
import com.nutra.app.domain.intelligence.MealRole
import com.nutra.app.domain.intelligence.PortionOptimizer
import com.nutra.app.domain.intelligence.UserEatingProfileLearner
import com.nutra.app.domain.model.AllergenTag
import com.nutra.app.domain.model.DailyNutritionSummary
import com.nutra.app.domain.model.DietStyle
import com.nutra.app.domain.model.DietaryProfile
import com.nutra.app.domain.model.FoodCategory
import com.nutra.app.domain.model.FoodFeedbackRecord
import com.nutra.app.domain.model.FoodFeedbackStatus
import com.nutra.app.domain.model.FoodItem
import com.nutra.app.domain.model.MacroGoals
import com.nutra.app.domain.model.MealSummary
import com.nutra.app.domain.model.MealType
import com.nutra.app.domain.model.UserProfile
import com.nutra.app.domain.usecase.TurkishTextNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class CatalogClassificationExhaustiveTest {

    private val classifier = FoodCulinaryClassifier()

    private fun indexedCatalog(): List<FoodItem> =
        TurkishFoodSeedCatalog.initialFoods.mapIndexed { idx, item -> item.copy(id = idx + 1L) }

    @Test
    fun all621CatalogFoods_classifyWithValidFiniteBoundsAndUniqueFeedbackKeys() {
        val catalog = indexedCatalog()
        assertEquals(621, catalog.size)

        val classified = classifier.classifyCatalog(catalog)
        assertEquals(621, classified.size)

        val keys = HashSet<String>(621)
        var readyCount = 0
        var cookingOnlyCount = 0

        for (cf in classified) {
            assertTrue("Blank feedbackKey for ${cf.food.catalogCode}", cf.feedbackKey.isNotBlank())
            assertTrue("Duplicate feedbackKey ${cf.feedbackKey}", keys.add(cf.feedbackKey))

            val pc = cf.portionConstraint
            assertTrue("Non-finite minGrams for ${cf.food.catalogCode}", pc.minGrams.isFinite() && pc.minGrams > 0f)
            assertTrue("Non-finite typicalGrams for ${cf.food.catalogCode}", pc.typicalGrams.isFinite() && pc.typicalGrams > 0f)
            assertTrue("Non-finite maxGrams for ${cf.food.catalogCode}", pc.maxGrams.isFinite() && pc.maxGrams > 0f)
            assertTrue("Non-finite gramStep for ${cf.food.catalogCode}", pc.gramStep.isFinite() && pc.gramStep > 0f)
            assertTrue(
                "Bounds inverted for ${cf.food.catalogCode} (${cf.food.name}): min=${pc.minGrams} typical=${pc.typicalGrams} max=${pc.maxGrams}",
                pc.minGrams <= pc.typicalGrams && pc.typicalGrams <= pc.maxGrams,
            )

            if (cf.edibility == EdibilityStatus.READY_TO_EAT) readyCount++ else cookingOnlyCount++
        }

        assertTrue("Expected at least 480 ready-to-eat foods, got $readyCount", readyCount >= 480)
        assertTrue("Expected at least 35 cooking-only / raw / excluded foods, got $cookingOnlyCount", cookingOnlyCount >= 35)
    }

    @Test
    fun catalogRegressions_honeyTunaPeanutsCookingIngredientsAndTurkishDishesClassifiedAccurately() {
        val byCode = classifier.classifyCatalog(indexedCatalog()).associateBy { it.food.catalogCode }

        // 1. Honey is animal-derived (non-vegan), while pumpkin ("Balkabağı") must NOT match "bal"
        for (code in listOf("TR0321", "TR0248", "TR0882", "TR0910")) {
            val item = byCode.getValue(code)
            assertTrue("$code (${item.food.name}) must be containsAnimalProduct=true", item.containsAnimalProduct)
            assertFalse(
                "$code (${item.food.name}) must be excluded for VEGAN",
                classifier.isAllowedByPreferences(item, DietaryProfile(dietStyle = DietStyle.VEGAN), emptyMap()),
            )
        }
        val pumpkin = byCode.getValue("TR0798")
        assertFalse("Balkabağı must NOT be marked as animal product", pumpkin.containsAnimalProduct)

        // 2. Canned tuna in oil must be READY_TO_EAT fish, NOT cooking oil or red meat
        for (code in listOf("TR0001", "TR0002", "TR0004")) {
            val tuna = byCode.getValue(code)
            assertEquals("$code (${tuna.food.name}) must be READY_TO_EAT", EdibilityStatus.READY_TO_EAT, tuna.edibility)
            assertTrue("$code must have containsFish=true", tuna.containsFish)
            assertTrue("$code must have FISH_SEAFOOD allergen", AllergenTag.FISH_SEAFOOD in tuna.allergens)
            assertFalse("$code must NOT be red meat", tuna.containsRedMeat)
        }
        val customOilTuna = classifier.classifySingle(
            FoodItem(
                id = 5001L,
                name = "Zeytinyağlı Ton Balığı Konservesi",
                subtitle = "Konserve balık",
                category = FoodCategory.MEAT_POULTRY,
                caloriesPer100g = 185,
                proteinPer100g = 24f,
                carbsPer100g = 0f,
                fatPer100g = 9.5f,
                defaultUnitLabel = "1 Kutu",
                defaultUnitGrams = 104f,
            ),
        )
        assertEquals(EdibilityStatus.READY_TO_EAT, customOilTuna.edibility)
        assertTrue(customOilTuna.containsFish)
        assertTrue(AllergenTag.FISH_SEAFOOD in customOilTuna.allergens)

        // 3. Word-boundary cooking ingredient guards must NOT falsely exclude ready-to-eat dishes
        assertEquals(EdibilityStatus.READY_TO_EAT, byCode.getValue("TR0131").edibility) // Ekşi Mayalı Ekmek
        assertEquals(EdibilityStatus.READY_TO_EAT, byCode.getValue("TR0676").edibility) // Kremalı Mantar Çorbası
        assertEquals(EdibilityStatus.READY_TO_EAT, byCode.getValue("TR0066").edibility) // Ege Baharatlı Nohut Cipsi

        // 4. Peanuts vs Tree nuts safety
        for (code in listOf("TR0227", "TR0315")) {
            val peanutItem = byCode.getValue(code)
            assertTrue("$code (${peanutItem.food.name}) MUST carry PEANUT allergen", AllergenTag.PEANUT in peanutItem.allergens)
            assertFalse(
                "$code must be blocked for peanut-allergic users",
                classifier.isAllowedByPreferences(
                    peanutItem,
                    DietaryProfile(excludedAllergens = setOf(AllergenTag.PEANUT)),
                    emptyMap(),
                ),
            )
        }
        val customCoconut = classifier.classifySingle(
            FoodItem(
                id = 5002L,
                name = "Hindistan Cevizi Dilimleri",
                subtitle = "Tropik meyve",
                category = FoodCategory.FRUIT_NUTS,
                caloriesPer100g = 354,
                proteinPer100g = 3.3f,
                carbsPer100g = 15.2f,
                fatPer100g = 33.5f,
                defaultUnitLabel = "1 Porsiyon",
                defaultUnitGrams = 30f,
            ),
        )
        assertFalse("Hindistan cevizi should not falsely match 'ceviz' tree nut", AllergenTag.TREE_NUTS in customCoconut.allergens)

        // 5. Dairy / Tree-nut / Sesame allergen accuracy
        for (code in listOf("TR0446", "TR0916", "TR0917")) {
            val spread = byCode.getValue(code)
            assertTrue("$code (${spread.food.name}) should have DAIRY or TREE_NUTS", AllergenTag.DAIRY in spread.allergens || AllergenTag.TREE_NUTS in spread.allergens)
            assertEquals("$code (${spread.food.name}) should have SNACK_SWEET role", MealRole.SNACK_SWEET, spread.role)
            assertTrue("$code maxGrams (${spread.portionConstraint.maxGrams}) should be <= 50g", spread.portionConstraint.maxGrams <= 50f)
        }
        val flourHalva = classifier.classifySingle(
            FoodItem(
                id = 5003L,
                name = "Un Helvası",
                subtitle = "Geleneksel tatlı",
                category = FoodCategory.SNACKS_SWEETS,
                caloriesPer100g = 380,
                proteinPer100g = 5f,
                carbsPer100g = 52f,
                fatPer100g = 16f,
                defaultUnitLabel = "1 Porsiyon",
                defaultUnitGrams = 70f,
            ),
        )
        assertTrue("Un helvası contains gluten", AllergenTag.GLUTEN in flourHalva.allergens)
        assertFalse("Un helvası should NOT be flagged for sesame", AllergenTag.SESAME in flourHalva.allergens)

        val tahiniHalva = classifier.classifySingle(
            flourHalva.food.copy(id = 5004L, name = "Tahin Helvası", subtitle = "Susam ezmeli helva"),
        )
        assertTrue("Tahin helvası MUST be flagged for sesame", AllergenTag.SESAME in tahiniHalva.allergens)

        // 6. Meatless Turkish dishes & chicken doner must NOT be flagged as red meat
        val cigKofte = byCode.getValue("TR0564")
        assertFalse("Çiğ Köfte (Etsiz) must not be red meat", cigKofte.containsRedMeat)
        assertFalse("Çiğ Köfte (Etsiz) must not contain animal product", cigKofte.containsAnimalProduct)

        val mercimekKofte = byCode.getValue("TR0601")
        assertFalse("Mercimek Köftesi must not be red meat", mercimekKofte.containsRedMeat)
        assertFalse("Mercimek Köftesi must not contain animal product", mercimekKofte.containsAnimalProduct)

        val tavukDoner = byCode.getValue("TR0625")
        assertFalse("Tavuk Döner must not be red meat", tavukDoner.containsRedMeat)
        assertTrue("Tavuk Döner must be poultry", tavukDoner.containsPoultry)

        // 7. Raw eggs, raw frozen fish, raw lemons, alcohol, culinary herbs & hot chilies excluded
        for (code in listOf("TR0475", "TR0839", "TR0900", "TR0358", "TR0756", "TR0686", "TR0370", "TR0383", "TR0384", "TR0391")) {
            val excluded = byCode.getValue(code)
            assertEquals(
                "$code (${excluded.food.name}) must be COOKING_INGREDIENT_ONLY",
                EdibilityStatus.COOKING_INGREDIENT_ONLY,
                excluded.edibility,
            )
        }

        // 8. Comma-reversed raw salad veggies must stay READY_TO_EAT
        for (code in listOf("TR0785", "TR0783", "TR0782")) {
            val veg = byCode.getValue(code)
            assertEquals("$code (${veg.food.name}) must be READY_TO_EAT", EdibilityStatus.READY_TO_EAT, veg.edibility)
            assertEquals("$code should be VEG_SALAD_SIDE", MealRole.VEG_SALAD_SIDE, veg.role)
        }

        // 9. Kaymak vs Kaymaksız Yoğurt & small condiment portions
        val kaymak = byCode.getValue("TR0463")
        assertEquals(MealRole.HEALTHY_FAT_NUT, kaymak.role)
        assertTrue("Kaymak maxGrams (${kaymak.portionConstraint.maxGrams}) <= 50g", kaymak.portionConstraint.maxGrams <= 50f)

        val kaymaksizYogurt = byCode.getValue("TR0456")
        assertEquals(MealRole.DAIRY_PROBIOTIC, kaymaksizYogurt.role)

        val pickle = byCode.getValue("TR0907")
        assertTrue("Pickle maxGrams (${pickle.portionConstraint.maxGrams}) <= 95g", pickle.portionConstraint.maxGrams <= 95f)
        val humus = byCode.getValue("TR0575")
        assertTrue("Humus maxGrams (${humus.portionConstraint.maxGrams}) <= 135g", humus.portionConstraint.maxGrams <= 135f)
    }

    @Test
    fun dietAllergenAndMealMatrix_neverLeaksForbiddenItemsAcrossAllCombinations() {
        val catalog = indexedCatalog()
        val engine = MealRecommendationEngine(classifier, UserEatingProfileLearner(), PortionOptimizer())
        val today = LocalDate.of(2026, 11, 21)
        val goals = MacroGoals(calories = 2100, proteinGrams = 130, carbsGrams = 220, fatGrams = 70)
        val emptySummary = DailyNutritionSummary(
            dateIso = today.toString(),
            goals = goals,
            meals = MealType.entries.map { MealSummary(it, emptyList()) },
            waterMl = 500,
        )

        // Test every DietStyle across all 4 meals
        for (style in DietStyle.entries) {
            val profile = UserProfile(
                goals = goals,
                dietaryProfile = DietaryProfile(
                    dietStyle = style,
                    excludedKeywords = listOf("Çilek", "pırasa"),
                ),
            )
            for (meal in MealType.entries) {
                val report = engine.generateRecommendations(
                    profile = profile,
                    dailySummary = emptySummary,
                    mealType = meal,
                    catalogFoods = catalog,
                    historicalLogs = emptyList(),
                    today = today,
                )
                assertTrue(
                    "Expected recommendations for $style / $meal",
                    report.recommendations.isNotEmpty(),
                )
                for (rec in report.recommendations) {
                    for (item in rec.items) {
                        val cf = classifier.classifySingle(item.food)
                        assertEquals("Cooking ingredient leaked for $style/$meal: ${item.food.name}", EdibilityStatus.READY_TO_EAT, cf.edibility)
                        assertTrue(
                            "Diet preference violated for $style/$meal: ${item.food.name}",
                            classifier.isAllowedByPreferences(cf, profile.dietaryProfile, emptyMap()),
                        )
                        val norm = TurkishTextNormalizer.normalize("${item.food.name} ${item.food.subtitle}")
                        assertFalse("Excluded Turkish keyword 'cilek' leaked: ${item.food.name}", norm.contains("cilek"))
                        assertFalse("Excluded Turkish keyword 'pirasa' leaked: ${item.food.name}", norm.contains("pirasa"))
                    }
                }
            }
        }

        // Test every individual AllergenTag across DINNER & BREAKFAST
        for (allergen in AllergenTag.entries) {
            val profile = UserProfile(
                goals = goals,
                dietaryProfile = DietaryProfile(excludedAllergens = setOf(allergen)),
            )
            for (meal in listOf(MealType.BREAKFAST, MealType.DINNER)) {
                val report = engine.generateRecommendations(
                    profile = profile,
                    dailySummary = emptySummary,
                    mealType = meal,
                    catalogFoods = catalog,
                    historicalLogs = emptyList(),
                    today = today,
                )
                assertTrue("Expected recommendations excluding $allergen for $meal", report.recommendations.isNotEmpty())
                for (rec in report.recommendations) {
                    for (item in rec.items) {
                        val cf = classifier.classifySingle(item.food)
                        assertFalse(
                            "Allergen $allergen leaked into $meal: ${item.food.name}",
                            allergen in cf.allergens,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun classifyCatalog_isThreadSafeAndDistinguishesUnsavedZeroIdFoods() {
        val catalog = indexedCatalog()
        val zeroIdA = FoodItem(
            id = 0L,
            name = "Ev Yapımı Izgara Köfte",
            subtitle = "Dana kıyma",
            category = FoodCategory.MEAT_POULTRY,
            caloriesPer100g = 240,
            proteinPer100g = 20f,
            carbsPer100g = 4f,
            fatPer100g = 16f,
            defaultUnitLabel = "1 Porsiyon",
            defaultUnitGrams = 150f,
        )
        val zeroIdB = zeroIdA.copy(
            id = 0L,
            name = "Ev Yapımı Mercimek Çorbası",
            subtitle = "Bakliyat çorba",
            category = FoodCategory.SOUP,
            caloriesPer100g = 62,
            proteinPer100g = 4.2f,
            carbsPer100g = 9.5f,
            fatPer100g = 0.8f,
        )
        val zeroClassified = classifier.classifyCatalog(listOf(zeroIdA, zeroIdB))
        assertEquals(2, zeroClassified.size)
        assertNotEquals(zeroClassified[0].feedbackKey, zeroClassified[1].feedbackKey)
        assertTrue(zeroClassified[0].containsRedMeat)
        assertFalse(zeroClassified[1].containsRedMeat)

        // Concurrent stress across 8 threads with varying catalog slices
        val pool = Executors.newFixedThreadPool(8)
        try {
            val referenceKeys = classifier.classifyCatalog(catalog).map { it.feedbackKey }
            val tasks = (0 until 8).map { threadIdx ->
                Callable {
                    repeat(25) { iter ->
                        val subset = if ((threadIdx + iter) % 2 == 0) catalog else catalog.take(250 + (iter * 7) % 300)
                        val result = classifier.classifyCatalog(subset)
                        assertEquals(subset.size, result.size)
                        if (subset.size == catalog.size) {
                            assertEquals(referenceKeys, result.map { it.feedbackKey })
                        }
                    }
                    true
                }
            }
            val futures = pool.invokeAll(tasks)
            for (f in futures) {
                assertTrue(f.get())
            }
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun dislikedAndCustomKeyFeedback_strictlyExcludesBothCatalogAndCustomFoods() {
        val catalog = indexedCatalog()
        val classified = classifier.classifyCatalog(catalog)
        val target = classified.first { it.food.catalogCode == "TR0186" }
        val dislikedMap = mapOf(
            target.feedbackKey to FoodFeedbackRecord(
                foodKey = target.feedbackKey,
                foodId = target.food.id,
                foodName = target.food.name,
                status = FoodFeedbackStatus.DISLIKED,
            ),
        )
        assertFalse(classifier.isAllowedByPreferences(target, DietaryProfile(), dislikedMap))

        val neutralMap = mapOf(
            target.feedbackKey to FoodFeedbackRecord(
                foodKey = target.feedbackKey,
                foodId = target.food.id,
                foodName = target.food.name,
                status = FoodFeedbackStatus.PINNED,
            ),
        )
        assertTrue(classifier.isAllowedByPreferences(target, DietaryProfile(), neutralMap))
    }
}
