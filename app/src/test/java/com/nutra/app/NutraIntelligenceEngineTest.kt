package com.nutra.app

import com.nutra.app.core.database.TurkishFoodSeedCatalog
import com.nutra.app.domain.intelligence.EdibilityStatus
import com.nutra.app.domain.intelligence.FoodCulinaryClassifier
import com.nutra.app.domain.intelligence.HabitMaturity
import com.nutra.app.domain.intelligence.MealBudget
import com.nutra.app.domain.intelligence.MealBudgetMode
import com.nutra.app.domain.intelligence.MealRecommendationEngine
import com.nutra.app.domain.intelligence.MicronutrientType
import com.nutra.app.domain.intelligence.NutrientSignalStatus
import com.nutra.app.domain.intelligence.PortionOptimizer
import com.nutra.app.domain.intelligence.UserEatingProfileLearner
import com.nutra.app.domain.model.AllergenTag
import com.nutra.app.domain.model.DailyNutritionSummary
import com.nutra.app.domain.model.DietStyle
import com.nutra.app.domain.model.DietaryProfile
import com.nutra.app.domain.model.FoodFeedbackRecord
import com.nutra.app.domain.model.FoodFeedbackStatus
import com.nutra.app.domain.model.FoodItem
import com.nutra.app.domain.model.MacroGoals
import com.nutra.app.domain.model.MealLogItem
import com.nutra.app.domain.model.MealSummary
import com.nutra.app.domain.model.MealType
import com.nutra.app.domain.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.math.abs

class NutraIntelligenceEngineTest {

    private val classifier = FoodCulinaryClassifier()
    private val learner = UserEatingProfileLearner()
    private val optimizer = PortionOptimizer()
    private val engine = MealRecommendationEngine(classifier, learner, optimizer)

    private fun loadIndexedCatalog(): List<FoodItem> =
        TurkishFoodSeedCatalog.initialFoods.mapIndexed { index, item ->
            item.copy(id = index + 1L)
        }

    @Test
    fun culinaryClassifier_filtersCookingOnlyRawAndOilItemsFromReadyPool() {
        val catalog = loadIndexedCatalog()
        val classified = classifier.classifyCatalog(catalog)

        val sunflowerOil = classified.first { it.food.name.contains("Ayçiçek Yağı") }
        assertEquals(EdibilityStatus.COOKING_INGREDIENT_ONLY, sunflowerOil.edibility)

        val rawChicken = classified.first { it.food.catalogCode == "TR0185" }
        assertEquals(EdibilityStatus.COOKING_INGREDIENT_ONLY, rawChicken.edibility)

        val grilledChicken = classified.first { it.food.catalogCode == "TR0186" }
        assertEquals(EdibilityStatus.READY_TO_EAT, grilledChicken.edibility)

        val rawPotato = classified.first { it.food.catalogCode == "TR0385" }
        assertEquals(EdibilityStatus.COOKING_INGREDIENT_ONLY, rawPotato.edibility)

        val dryLentil = classified.first { it.food.catalogCode == "TR0516" }
        assertEquals(EdibilityStatus.COOKING_INGREDIENT_ONLY, dryLentil.edibility)

        val bulgurPilav = classified.first { it.food.catalogCode == "TR0562" }
        assertEquals(EdibilityStatus.READY_TO_EAT, bulgurPilav.edibility)

        val tomato = classified.first { it.food.catalogCode == "TR0371" }
        assertEquals(EdibilityStatus.READY_TO_EAT, tomato.edibility)

        // Realistic portion constraints tame catalog anomalies like 504g coban salata or 150g pistachio
        val coban = classified.first { it.food.catalogCode == "TR0565" }
        assertTrue(coban.portionConstraint.maxGrams <= 320f)
        val pistachio = classified.first { it.food.catalogCode == "TR0317" }
        assertTrue(pistachio.portionConstraint.maxGrams <= 50f)
    }

    @Test
    fun coldStart_staysHonestAndStillGeneratesDiverseTurkishRecommendations() {
        val catalog = loadIndexedCatalog()
        val profile = UserProfile(
            goals = MacroGoals(calories = 2100, proteinGrams = 140, carbsGrams = 220, fatGrams = 70),
        )
        val summary = DailyNutritionSummary(
            dateIso = "2026-11-21",
            goals = profile.goals,
            meals = MealType.entries.map { MealSummary(it, emptyList()) },
            waterMl = 0,
        )

        val report = engine.generateRecommendations(
            profile = profile,
            dailySummary = summary,
            mealType = MealType.BREAKFAST,
            catalogFoods = catalog,
            historicalLogs = emptyList(),
            today = LocalDate.of(2026, 11, 21),
        )

        assertEquals(HabitMaturity.NONE, report.eatingProfile.maturity)
        assertNotNull(report.coldStartNote)
        assertTrue(report.recommendations.size >= 2)
        assertTrue(report.recommendations.none { it.isPersonalized })
        assertTrue(report.recommendations.all { it.items.size >= 2 && it.reasons.isNotEmpty() })
    }

    @Test
    fun userScenario_dinnerWith650Kcal45gProteinLowFiberTurkishHomePreference() {
        val catalog = loadIndexedCatalog()
        val today = LocalDate.of(2026, 11, 21)

        // Build 12 days of low-fiber Turkish home-cooked meal history
        val chicken = catalog.first { it.catalogCode == "TR0186" }
        val whiteRice = catalog.first { it.catalogCode == "TR0619" }
        val yogurt = catalog.first { it.catalogCode == "TR0853" }
        val egg = catalog.first { it.catalogCode == "TR0460" }

        val history = buildList {
            var idSeq = 1L
            for (dayOffset in 1..12) {
                val dateIso = today.minusDays(dayOffset.toLong()).toString()
                val ts = dayOffset * 100_000L
                add(MealLogItem(idSeq++, dateIso, MealType.BREAKFAST, egg.id, egg.name, "1 Adet", 2f, 100f, 155, 12.6f, 1.1f, 10.6f, ts))
                add(MealLogItem(idSeq++, dateIso, MealType.LUNCH, chicken.id, chicken.name, "1 Porsiyon", 1.5f, 150f, 173, 40.2f, 0f, 1.1f, ts + 1))
                add(MealLogItem(idSeq++, dateIso, MealType.LUNCH, whiteRice.id, whiteRice.name, "1 Porsiyon", 1f, 180f, 310, 4.5f, 52f, 8.5f, ts + 2))
                add(MealLogItem(idSeq++, dateIso, MealType.DINNER, yogurt.id, yogurt.name, "1 Kase", 1f, 150f, 145, 7.5f, 6.0f, 10.2f, ts + 3))
            }
        }

        val goals = MacroGoals(calories = 2200, proteinGrams = 140, carbsGrams = 230, fatGrams = 70)
        val profile = UserProfile(goals = goals)
        // Before dinner: consumed 1550 kcal and 95g protein -> 650 kcal & 45g protein remaining!
        val todaySummary = DailyNutritionSummary(
            dateIso = today.toString(),
            goals = goals,
            meals = listOf(
                MealSummary(
                    MealType.BREAKFAST,
                    listOf(MealLogItem(1000L, today.toString(), MealType.BREAKFAST, egg.id, egg.name, "1 Adet", 3f, 150f, 650, 35f, 70f, 24f, 1L)),
                ),
                MealSummary(
                    MealType.LUNCH,
                    listOf(MealLogItem(1001L, today.toString(), MealType.LUNCH, chicken.id, chicken.name, "1 Porsiyon", 2f, 200f, 900, 60f, 100f, 26f, 2L)),
                ),
                MealSummary(MealType.DINNER, emptyList()),
                MealSummary(MealType.SNACK, emptyList()),
            ),
            waterMl = 1600,
        )

        val report = engine.generateRecommendations(
            profile = profile,
            dailySummary = todaySummary,
            mealType = MealType.DINNER,
            catalogFoods = catalog,
            historicalLogs = history,
            today = today,
        )

        assertEquals(HabitMaturity.ESTABLISHED, report.eatingProfile.maturity)
        assertEquals(650, report.budget.targetCalories)
        assertEquals(45f, report.budget.targetProteinGrams, 1f)
        val fiberSignal = report.eatingProfile.micronutrientSignals.first { it.type == MicronutrientType.FIBER }
        assertEquals(NutrientSignalStatus.LOW_RECORDED, fiberSignal.status)
        assertNotNull(report.prioritizedMicronutrientNote)

        assertTrue("Expected at least 3 distinct dinner recommendations", report.recommendations.size >= 3)
        val top = report.recommendations.first()
        // Calories and protein should closely target 650 kcal & 45g protein
        assertTrue("Expected calories near 650, got ${top.totalCalories}", abs(top.totalCalories - 650) <= 130)
        assertTrue("Expected solid protein contribution, got ${top.totalProtein}", top.totalProtein >= 30f)
        assertTrue("Expected explanations", top.reasons.isNotEmpty())
        assertEquals(1550 + top.totalCalories, top.goalImpact.caloriesAfter)

        // Diversity check: recommendations must not all share identical primary dish
        val primaryIds = report.recommendations.map { it.items.first().food.id }.toSet()
        assertTrue("Expected varied primary dishes across recommendations", primaryIds.size >= 2)
    }

    @Test
    fun portionOptimizer_balancesChickenBulgurYogurtToRemainingTargetWithinRealisticBounds() {
        val catalog = loadIndexedCatalog()
        val chicken = catalog.first { it.catalogCode == "TR0186" } // Tavuk Göğüs Izgara
        val bulgur = catalog.first { it.catalogCode == "TR0562" } // Bulgur Pilavı
        val yogurt = catalog.first { it.catalogCode == "TR0853" } // Süzme Yoğurt

        val budget = MealBudget(
            mealType = MealType.DINNER,
            targetCalories = 650,
            targetProteinGrams = 50f,
            targetCarbsGrams = 65f,
            targetFatGrams = 21f,
            dailyRemainingCalories = 650,
            dailyRemainingProtein = 50f,
            dailyRemainingCarbs = 65f,
            dailyRemainingFat = 21f,
            mode = MealBudgetMode.REMAINING_FOR_DAY,
            budgetSummaryText = "",
        )

        val result = engine.optimizeCustomCombo(
            selectedFoods = listOf(chicken, bulgur, yogurt),
            budget = budget,
        )

        assertEquals(3, result.items.size)
        assertTrue("Calories ${result.totalCalories} should be close to 650", abs(result.totalCalories - 650) <= 70)
        assertTrue("Protein ${result.totalProtein} should be close to 50g", abs(result.totalProtein - 50f) <= 10f)
        assertTrue("Fit score should be high, got ${result.fitScore}", result.fitScore >= 82)
        // Verify realistic portion bounds
        for (item in result.items) {
            assertTrue("${item.food.name} grams ${item.totalGrams} >= min ${item.minGrams}", item.totalGrams >= item.minGrams - 5f)
            assertTrue("${item.food.name} grams ${item.totalGrams} <= max ${item.maxGrams}", item.totalGrams <= item.maxGrams + 5f)
        }
    }

    @Test
    fun missingMicronutrientData_isNeverTreatedAsZero() {
        val today = LocalDate.of(2026, 11, 21)
        val foodWithoutMicros = FoodItem(
            id = 9001L,
            name = "Özel Ev Yemeği",
            subtitle = "Test",
            category = com.nutra.app.domain.model.FoodCategory.HOME_COOKED,
            caloriesPer100g = 150,
            proteinPer100g = 12f,
            carbsPer100g = 15f,
            fatPer100g = 4f,
            defaultUnitLabel = "1 Porsiyon",
            defaultUnitGrams = 200f,
            fiberPer100g = null,
            sodiumMgPer100g = null,
            calciumMgPer100g = null,
            potassiumMgPer100g = null,
            ironMgPer100g = null,
            vitaminCMgPer100g = null,
        )
        val logs = (0..5).map { d ->
            MealLogItem(
                id = d + 1L,
                dateIso = today.minusDays(d.toLong()).toString(),
                mealType = MealType.LUNCH,
                foodId = foodWithoutMicros.id,
                foodName = foodWithoutMicros.name,
                unitLabel = "1 Porsiyon",
                multiplier = 2f,
                totalGrams = 400f,
                calories = 600,
                proteinGrams = 48f,
                carbsGrams = 60f,
                fatGrams = 16f,
                timestampMillis = 1000L + d,
            )
        }

        val profile = learner.learn(
            profile = UserProfile(),
            classifiedCatalog = listOf(classifier.classifySingle(foodWithoutMicros)),
            historicalLogs = logs,
            feedbackRecords = emptyList(),
            today = today,
        )

        // Every micronutrient MUST be INSUFFICIENT_DATA (never LOW_RECORDED / 0!)
        for (sig in profile.micronutrientSignals) {
            assertEquals("Expected INSUFFICIENT_DATA for ${sig.type}", NutrientSignalStatus.INSUFFICIENT_DATA, sig.status)
            assertEquals(0f, sig.coverageRatio, 0.001f)
        }
    }

    @Test
    fun dietaryAllergenAndDislikedFilters_strictlyExcludeForbiddenFoods() {
        val catalog = loadIndexedCatalog()
        val today = LocalDate.of(2026, 11, 21)
        val chicken = catalog.first { it.catalogCode == "TR0186" }
        val dislikedFeedback = listOf(
            FoodFeedbackRecord(
                foodKey = classifier.foodFeedbackKey(chicken),
                foodId = chicken.id,
                foodName = chicken.name,
                status = FoodFeedbackStatus.DISLIKED,
            ),
        )

        // Vegan + Gluten-free user
        val veganGlutenFreeProfile = UserProfile(
            goals = MacroGoals(calories = 2000, proteinGrams = 100, carbsGrams = 250, fatGrams = 65),
            dietaryProfile = DietaryProfile(
                dietStyle = DietStyle.VEGAN,
                excludedAllergens = setOf(AllergenTag.GLUTEN, AllergenTag.DAIRY),
                excludedKeywords = listOf("mantar"),
            ),
        )
        val summary = DailyNutritionSummary(
            dateIso = today.toString(),
            goals = veganGlutenFreeProfile.goals,
            meals = MealType.entries.map { MealSummary(it, emptyList()) },
            waterMl = 0,
        )

        val report = engine.generateRecommendations(
            profile = veganGlutenFreeProfile,
            dailySummary = summary,
            mealType = MealType.DINNER,
            catalogFoods = catalog,
            historicalLogs = emptyList(),
            feedbackRecords = dislikedFeedback,
            today = today,
        )

        for (rec in report.recommendations) {
            for (portion in rec.items) {
                val cf = classifier.classifySingle(portion.food)
                assertFalse("Vegan meal must not contain animal product: ${portion.food.name}", cf.containsAnimalProduct)
                assertFalse("Gluten-free meal must not contain gluten: ${portion.food.name}", AllergenTag.GLUTEN in cf.allergens)
                assertFalse("Dairy-free meal must not contain dairy: ${portion.food.name}", AllergenTag.DAIRY in cf.allergens)
                assertFalse("Excluded keyword 'mantar' leaked: ${portion.food.name}", portion.food.name.lowercase().contains("mantar"))
                assertFalse("Disliked food leaked: ${portion.food.name}", portion.food.id == chicken.id)
            }
        }
    }

    @Test
    fun fullCatalogPerformance_generatesRecommendationsFastAndOffline() {
        val catalog = loadIndexedCatalog()
        val profile = UserProfile()
        val today = LocalDate.of(2026, 11, 21)
        val summary = DailyNutritionSummary(
            dateIso = today.toString(),
            goals = profile.goals,
            meals = MealType.entries.map { MealSummary(it, emptyList()) },
            waterMl = 1000,
        )

        // Warm up classifier cache once
        engine.generateRecommendations(profile, summary, MealType.LUNCH, catalog, emptyList(), today = today)

        val startNs = System.nanoTime()
        val report = engine.generateRecommendations(profile, summary, MealType.DINNER, catalog, emptyList(), today = today)
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000.0

        assertTrue(report.recommendations.isNotEmpty())
        assertTrue("Expected recommendation generation under 250ms, took ${elapsedMs}ms", elapsedMs < 250.0)
    }
}
