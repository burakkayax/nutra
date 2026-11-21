package com.nutra.app

import com.nutra.app.core.database.TurkishFoodSeedCatalog
import com.nutra.app.domain.intelligence.FoodCulinaryClassifier
import com.nutra.app.domain.intelligence.HabitMaturity
import com.nutra.app.domain.intelligence.MealBudgetMode
import com.nutra.app.domain.intelligence.MealRecommendationEngine
import com.nutra.app.domain.intelligence.MicronutrientType
import com.nutra.app.domain.intelligence.NutrientSignalStatus
import com.nutra.app.domain.intelligence.PortionOptimizer
import com.nutra.app.domain.intelligence.UserEatingProfileLearner
import com.nutra.app.domain.model.AllergenTag
import com.nutra.app.domain.model.BiologicalSex
import com.nutra.app.domain.model.DailyNutritionSummary
import com.nutra.app.domain.model.DietStyle
import com.nutra.app.domain.model.DietaryProfile
import com.nutra.app.domain.model.FoodCategory
import com.nutra.app.domain.model.FoodFeedbackRecord
import com.nutra.app.domain.model.FoodFeedbackStatus
import com.nutra.app.domain.model.FoodItem
import com.nutra.app.domain.model.GoalType
import com.nutra.app.domain.model.MacroGoals
import com.nutra.app.domain.model.MealLogItem
import com.nutra.app.domain.model.MealSummary
import com.nutra.app.domain.model.MealType
import com.nutra.app.domain.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class NutraIntelligenceExhaustiveTest {

    private val classifier = FoodCulinaryClassifier()
    private val learner = UserEatingProfileLearner()
    private val optimizer = PortionOptimizer()
    private val engine = MealRecommendationEngine(classifier, learner, optimizer)

    private fun indexedCatalog(): List<FoodItem> =
        TurkishFoodSeedCatalog.initialFoods.mapIndexed { idx, item -> item.copy(id = idx + 1L) }

    private fun summaryWithLoggedMeals(
        dateIso: String,
        goals: MacroGoals,
        loggedByMeal: Map<MealType, List<MealLogItem>>,
    ): DailyNutritionSummary = DailyNutritionSummary(
        dateIso = dateIso,
        goals = goals,
        meals = MealType.entries.map { mt -> MealSummary(mt, loggedByMeal[mt].orEmpty()) },
        waterMl = 1200,
    )

    private fun dummyLog(
        id: Long,
        dateIso: String,
        mealType: MealType,
        food: FoodItem,
        grams: Float = 150f,
        ts: Long = id * 1000L,
    ): MealLogItem = MealLogItem(
        id = id,
        dateIso = dateIso,
        mealType = mealType,
        foodId = food.id.takeIf { it > 0L },
        foodName = food.name,
        unitLabel = "1 Porsiyon",
        multiplier = 1f,
        totalGrams = grams,
        calories = food.caloriesForGrams(grams),
        proteinGrams = food.proteinForGrams(grams),
        carbsGrams = food.carbsForGrams(grams),
        fatGrams = food.fatForGrams(grams),
        timestampMillis = ts,
        baseUnit = food.displayUnitSuffix,
    )

    @Test
    fun inferActiveMealType_exhaustive384CombinationsOf24HoursAnd16MealSubsets() {
        val sampleFood = indexedCatalog().first()
        val goals = MacroGoals()
        val mealTypes = MealType.entries

        // Test every hour 0..23 and every subset mask 0..15
        for (hour in 0..23) {
            val now = LocalTime.of(hour, 30)
            for (mask in 0 until 16) {
                val loggedSet = mealTypes.filterIndexed { idx, _ -> (mask and (1 shl idx)) != 0 }.toSet()
                val summary = summaryWithLoggedMeals(
                    dateIso = "2026-11-21",
                    goals = goals,
                    loggedByMeal = loggedSet.associateWith { mt ->
                        listOf(dummyLog(1L, "2026-11-21", mt, sampleFood))
                    },
                )
                val inferred = engine.inferActiveMealType(summary, now)

                val natural = when {
                    hour < 11 -> MealType.BREAKFAST
                    hour < 15 -> MealType.LUNCH
                    hour < 17 -> MealType.SNACK
                    hour < 22 -> MealType.DINNER
                    else -> MealType.SNACK
                }
                if (natural !in loggedSet) {
                    assertEquals("hour=$hour mask=$mask should choose natural meal $natural", natural, inferred)
                } else if (loggedSet.size == 4) {
                    assertEquals("All 4 meals logged -> fallback SNACK", MealType.SNACK, inferred)
                } else {
                    // When natural meal is already logged, fallback must respect deterministic priority
                    val expectedFallback = when {
                        MealType.BREAKFAST !in loggedSet && hour < 13 -> MealType.BREAKFAST
                        MealType.LUNCH !in loggedSet && hour < 17 -> MealType.LUNCH
                        MealType.DINNER !in loggedSet -> MealType.DINNER
                        else -> MealType.SNACK
                    }
                    assertEquals("hour=$hour mask=$mask fallback mismatch", expectedFallback, inferred)
                }
            }
        }
    }

    @Test
    fun calculateMealBudget_enforcesPhysicalMacroFeasibilityAndAllThreeBudgetModes() {
        val catalog = indexedCatalog()
        val emptyEatingProfile = learner.learn(
            profile = UserProfile(),
            classifiedCatalog = classifier.classifyCatalog(catalog),
            historicalLogs = emptyList(),
            feedbackRecords = emptyList(),
            today = LocalDate.of(2026, 11, 21),
        )
        val goals = MacroGoals(calories = 2000, proteinGrams = 160, carbsGrams = 200, fatGrams = 60)

        // 1. Over budget / < 140 kcal remaining -> LIGHT_OVER_BUDGET
        val overLog = MealLogItem(1L, "2026-11-21", MealType.LUNCH, 1L, "Öğün", "1", 1f, 500f, 1920, 40f, 220f, 70f, 1L)
        val lightBudget = engine.calculateMealBudget(
            dailySummary = summaryWithLoggedMeals("2026-11-21", goals, mapOf(MealType.LUNCH to listOf(overLog))),
            mealType = MealType.DINNER,
            eatingProfile = emptyEatingProfile,
        )
        assertEquals(MealBudgetMode.LIGHT_OVER_BUDGET, lightBudget.mode)
        assertTrue(lightBudget.targetCalories <= 280)
        assertTrue(lightBudget.targetProteinGrams * 4f <= lightBudget.targetCalories * 0.65f)

        // 2. Physically tight remaining budget: 200 kcal left, 140g protein left -> MUST cap protein to <= 60% of cal!
        val tightLog = MealLogItem(2L, "2026-11-21", MealType.LUNCH, 1L, "Karb Ağırlıklı", "1", 1f, 500f, 1800, 20f, 340f, 35f, 2L)
        val tightBudget = engine.calculateMealBudget(
            dailySummary = summaryWithLoggedMeals(
                "2026-11-21",
                goals,
                mapOf(
                    MealType.BREAKFAST to listOf(tightLog.copy(id = 10L, mealType = MealType.BREAKFAST, calories = 900, proteinGrams = 10f)),
                    MealType.LUNCH to listOf(tightLog.copy(id = 11L, mealType = MealType.LUNCH, calories = 900, proteinGrams = 10f)),
                    MealType.SNACK to listOf(tightLog.copy(id = 12L, mealType = MealType.SNACK, calories = 0, proteinGrams = 0f)),
                ),
            ),
            mealType = MealType.DINNER,
            eatingProfile = emptyEatingProfile,
        )
        assertEquals(MealBudgetMode.REMAINING_FOR_DAY, tightBudget.mode)
        assertTrue(
            "Protein target (${tightBudget.targetProteinGrams}g) exceeds physical 60% cap of ${tightBudget.targetCalories} kcal",
            tightBudget.targetProteinGrams * 4f <= tightBudget.targetCalories * 0.61f,
        )
        assertTrue(
            "Carbs target (${tightBudget.targetCarbsGrams}g) exceeds physical 75% cap of ${tightBudget.targetCalories} kcal",
            tightBudget.targetCarbsGrams * 4f <= tightBudget.targetCalories * 0.76f,
        )
        assertTrue(
            "Fat target (${tightBudget.targetFatGrams}g) exceeds physical 55% cap of ${tightBudget.targetCalories} kcal",
            tightBudget.targetFatGrams * 9f <= tightBudget.targetCalories * 0.56f,
        )

        // 3. Start of day (all meals open) -> PROPORTIONAL_MEAL_SHARE
        val freshSummary = summaryWithLoggedMeals("2026-11-21", goals, emptyMap())
        val shareBudget = engine.calculateMealBudget(freshSummary, MealType.BREAKFAST, emptyEatingProfile)
        assertEquals(MealBudgetMode.PROPORTIONAL_MEAL_SHARE, shareBudget.mode)
        assertTrue("Breakfast share should be realistic (350..850 kcal), got ${shareBudget.targetCalories}", shareBudget.targetCalories in 350..850)
    }

    @Test
    fun userEatingProfileLearner_verifiesMaturityBoundariesDecayHabitDriftAndFeedback() {
        val today = LocalDate.of(2026, 11, 21)
        val catalog = indexedCatalog()
        val classified = classifier.classifyCatalog(catalog)
        val egg = catalog.first { it.catalogCode == "TR0460" }
        val chicken = catalog.first { it.catalogCode == "TR0186" }
        val bulgur = catalog.first { it.catalogCode == "TR0562" }
        val burger = catalog.first { it.name.contains("Hamburger", ignoreCase = true) }

        // Maturity boundaries: 0, 2 -> NONE; 3, 9 -> EARLY; 10 -> ESTABLISHED
        fun logsForDays(days: Int): List<MealLogItem> = (1..days).map { d ->
            dummyLog(d.toLong(), today.minusDays(d.toLong()).toString(), MealType.BREAKFAST, egg)
        }
        assertEquals(HabitMaturity.NONE, learner.learn(UserProfile(), classified, logsForDays(0), emptyList(), today).maturity)
        assertEquals(HabitMaturity.NONE, learner.learn(UserProfile(), classified, logsForDays(2), emptyList(), today).maturity)
        assertEquals(HabitMaturity.EARLY, learner.learn(UserProfile(), classified, logsForDays(3), emptyList(), today).maturity)
        assertEquals(HabitMaturity.EARLY, learner.learn(UserProfile(), classified, logsForDays(9), emptyList(), today).maturity)
        assertEquals(HabitMaturity.ESTABLISHED, learner.learn(UserProfile(), classified, logsForDays(10), emptyList(), today).maturity)

        // Filtering: >90d, future date, malformed date, non-finite grams ignored; recency decay & co-occurrence >= 2
        val mixedLogs = buildList {
            var id = 1L
            // Old logs (days 30..40): eat burger
            for (d in 30..39) {
                add(dummyLog(id++, today.minusDays(d.toLong()).toString(), MealType.DINNER, burger, 220f))
            }
            // Recent logs (days 1..10): eat chicken + bulgur together (co-occurrence = 10 >= 2)
            for (d in 1..10) {
                add(dummyLog(id++, today.minusDays(d.toLong()).toString(), MealType.DINNER, chicken, 180f))
                add(dummyLog(id++, today.minusDays(d.toLong()).toString(), MealType.DINNER, bulgur, 160f))
            }
            // Corrupt / out-of-window logs
            add(dummyLog(id++, today.minusDays(95).toString(), MealType.LUNCH, egg, 100f))
            add(dummyLog(id++, today.plusDays(2).toString(), MealType.LUNCH, egg, 100f))
            add(dummyLog(id++, "bad-date", MealType.LUNCH, egg, 100f))
            add(dummyLog(id++, today.minusDays(1).toString(), MealType.LUNCH, egg, Float.NaN))
        }

        val feedback = listOf(
            FoodFeedbackRecord(
                foodKey = classifier.foodFeedbackKey(chicken),
                foodId = chicken.id,
                foodName = chicken.name,
                status = FoodFeedbackStatus.PINNED,
                acceptedCount = 5,
            ),
            FoodFeedbackRecord(
                foodKey = classifier.foodFeedbackKey(burger),
                foodId = burger.id,
                foodName = burger.name,
                status = FoodFeedbackStatus.NEUTRAL,
                dismissedCount = 4,
            ),
        )

        val profile = learner.learn(UserProfile(), classified, mixedLogs, feedback, today)
        assertEquals(HabitMaturity.ESTABLISHED, profile.maturity)
        assertEquals(20, profile.loggedDaysCount)
        // Recency + PINNED boost means chicken affinity >> burger affinity
        val chickenAff = profile.foodAffinityById[chicken.id] ?: 0f
        val burgerAff = profile.foodAffinityById[burger.id] ?: 0f
        assertTrue("Expected chicken ($chickenAff) > burger ($burgerAff)", chickenAff > burgerAff)
        assertTrue("Chicken & bulgur should be co-occurring partners", bulgur.id in (profile.coOccurrencePairs[chicken.id] ?: emptySet()))
        assertTrue("24h recency set should contain chicken", chicken.id in profile.recentlyEatenFoodIds24h)
        assertEquals(180f, profile.usualGramsByFoodId[chicken.id] ?: 0f, 1f)
        // Habit drift note should notice shift from packaged/fast-food to home-cooked / higher protein!
        assertNotNull("Expected habit shift note for clear 14d vs prior drift", profile.habitShiftNote)
    }

    @Test
    fun micronutrientLearner_respectsExactCoverageBoundaryAndSexAgeReferenceValues() {
        val today = LocalDate.of(2026, 11, 21)

        val highSodiumLowFiber = FoodItem(
            id = 6001L,
            name = "Tuzlu Atıştırmalık",
            subtitle = "Test",
            category = FoodCategory.SNACKS_SWEETS,
            caloriesPer100g = 400,
            proteinPer100g = 8f,
            carbsPer100g = 50f,
            fatPer100g = 18f,
            defaultUnitLabel = "1 Paket",
            defaultUnitGrams = 100f,
            fiberPer100g = 1.0f,
            sodiumMgPer100g = 1800f,
            calciumMgPer100g = 120f,
            potassiumMgPer100g = 200f,
            ironMgPer100g = 2.0f,
            vitaminCMgPer100g = 2.0f,
        )
        val noMicroFood = highSodiumLowFiber.copy(
            id = 6002L,
            name = "Bilinmeyen Yemek",
            fiberPer100g = null,
            sodiumMgPer100g = null,
            calciumMgPer100g = null,
            potassiumMgPer100g = null,
            ironMgPer100g = null,
            vitaminCMgPer100g = null,
        )
        val classified = classifier.classifyCatalog(listOf(highSodiumLowFiber, noMicroFood))

        // 1. Only 1 covered day out of 3 (< 2 covered days & < 50% coverage) -> INSUFFICIENT_DATA
        val belowBoundaryLogs = listOf(
            dummyLog(1L, today.minusDays(1).toString(), MealType.LUNCH, highSodiumLowFiber, 200f),
            dummyLog(2L, today.minusDays(2).toString(), MealType.LUNCH, noMicroFood, 200f),
            dummyLog(3L, today.minusDays(3).toString(), MealType.LUNCH, noMicroFood, 200f),
        )
        val insufficientProfile = learner.learn(UserProfile(), classified, belowBoundaryLogs, emptyList(), today)
        assertTrue(
            "All micros must be INSUFFICIENT_DATA with only 1 covered day",
            insufficientProfile.micronutrientSignals.all { it.status == NutrientSignalStatus.INSUFFICIENT_DATA },
        )

        // 2. 4 covered days with high sodium (3600mg/day) and low fiber (2g/day) -> evaluated!
        val coveredLogs = (1..4).map { d ->
            dummyLog(d.toLong(), today.minusDays(d.toLong()).toString(), MealType.LUNCH, highSodiumLowFiber, 200f)
        }
        val femaleYoung = learner.learn(
            UserProfile(sex = BiologicalSex.FEMALE, age = 28),
            classified,
            coveredLogs,
            emptyList(),
            today,
        )
        val femaleSenior = learner.learn(
            UserProfile(sex = BiologicalSex.FEMALE, age = 58),
            classified,
            coveredLogs,
            emptyList(),
            today,
        )
        val maleSenior = learner.learn(
            UserProfile(sex = BiologicalSex.MALE, age = 72),
            classified,
            coveredLogs,
            emptyList(),
            today,
        )

        val sodiumSig = femaleYoung.micronutrientSignals.first { it.type == MicronutrientType.SODIUM }
        assertEquals(NutrientSignalStatus.ELEVATED_RECORDED, sodiumSig.status)
        val fiberSig = femaleYoung.micronutrientSignals.first { it.type == MicronutrientType.FIBER }
        assertEquals(NutrientSignalStatus.LOW_RECORDED, fiberSig.status)

        // Sex/age-specific clinical references: premenopausal female iron (18mg) vs male/postmenopausal (8mg);
        // adult calcium (1000mg) vs postmenopausal female / >70 senior (1200mg)
        val femaleIronRef = femaleYoung.micronutrientSignals.first { it.type == MicronutrientType.IRON }.referenceValue
        val maleIronRef = maleSenior.micronutrientSignals.first { it.type == MicronutrientType.IRON }.referenceValue
        assertEquals(18f, femaleIronRef, 0.1f)
        assertEquals(8f, maleIronRef, 0.1f)

        val youngCalciumRef = femaleYoung.micronutrientSignals.first { it.type == MicronutrientType.CALCIUM }.referenceValue
        val femaleSeniorCalciumRef = femaleSenior.micronutrientSignals.first { it.type == MicronutrientType.CALCIUM }.referenceValue
        val maleSeniorCalciumRef = maleSenior.micronutrientSignals.first { it.type == MicronutrientType.CALCIUM }.referenceValue
        assertEquals(1000f, youngCalciumRef, 0.1f)
        assertEquals(1200f, femaleSeniorCalciumRef, 0.1f)
        assertEquals(1200f, maleSeniorCalciumRef, 0.1f)
    }

    @Test
    fun highSodiumAndLowMicronutrients_evaluatedOnFullMapNotTruncatedHighlights() {
        val today = LocalDate.of(2026, 11, 21)

        // Construct a controlled catalog where EVERY food has all 6 micronutrients populated
        // (so Sodium would be item #5 or #6 if truncated to take(4)!)
        fun makeFood(id: Long, name: String, category: FoodCategory, sodiumMg: Float, fiberG: Float): FoodItem = FoodItem(
            id = id,
            name = name,
            subtitle = "Ev yemeği",
            category = category,
            caloriesPer100g = 155,
            proteinPer100g = 22f,
            carbsPer100g = 8f,
            fatPer100g = 4f,
            defaultUnitLabel = "1 Porsiyon",
            defaultUnitGrams = 180f,
            preferredMealTypes = setOf(MealType.DINNER),
            fiberPer100g = fiberG,
            sodiumMgPer100g = sodiumMg,
            calciumMgPer100g = 180f,
            potassiumMgPer100g = 520f,
            ironMgPer100g = 3.5f,
            vitaminCMgPer100g = 35f,
        )

        val lowNaChicken = makeFood(101L, "Izgara Tavuk Göğsü (Tuzsuz)", FoodCategory.MEAT_POULTRY, sodiumMg = 70f, fiberG = 1f)
        val highNaChicken = makeFood(102L, "Salamura Tavuk Göğsü", FoodCategory.MEAT_POULTRY, sodiumMg = 1200f, fiberG = 1f)
        val bulgur = makeFood(103L, "Bulgur Pilavı", FoodCategory.GRAINS_BAKERY, sodiumMg = 80f, fiberG = 5.5f).copy(
            proteinPer100g = 4f, carbsPer100g = 28f, fatPer100g = 3f, caloriesPer100g = 155,
        )
        val salad = makeFood(104L, "Çoban Salata", FoodCategory.SALAD_MEZE, sodiumMg = 40f, fiberG = 3.5f).copy(
            proteinPer100g = 1.5f, carbsPer100g = 6f, fatPer100g = 3f, caloriesPer100g = 58,
        )
        val catalog = listOf(lowNaChicken, highNaChicken, bulgur, salad)

        // History with elevated sodium (> 2600mg/day over 5 days) and adequate fiber (> 25g)
        val history = (1..5).flatMap { d ->
            val dateIso = today.minusDays(d.toLong()).toString()
            listOf(
                dummyLog(d * 10L + 1, dateIso, MealType.LUNCH, highNaChicken, 250f), // 3000 mg Na!
                dummyLog(d * 10L + 2, dateIso, MealType.LUNCH, bulgur, 500f),        // 27.5g fiber (adequate)
            )
        }

        val goals = MacroGoals(calories = 2000, proteinGrams = 140, carbsGrams = 210, fatGrams = 65)
        val summary = summaryWithLoggedMeals("2026-11-21", goals, emptyMap())
        val report = engine.generateRecommendations(
            profile = UserProfile(goals = goals),
            dailySummary = summary,
            mealType = MealType.DINNER,
            catalogFoods = catalog,
            historicalLogs = history,
            today = today,
        )

        assertTrue(report.recommendations.isNotEmpty())
        assertNotNull(report.prioritizedMicronutrientNote)
        assertTrue(report.prioritizedMicronutrientNote!!.contains("sodyum", ignoreCase = true))

        // Top recommendation MUST prefer low-sodium chicken over 1200mg/100g salamura chicken!
        val topFoodIds = report.recommendations.first().items.map { it.food.id }
        assertTrue("Expected low-sodium chicken in top recommendation", lowNaChicken.id in topFoodIds)
        assertFalse("High-sodium chicken should be ranked below low-sodium chicken", highNaChicken.id in topFoodIds)
    }

    @Test
    fun restrictiveMultiAllergenVeganDietAndNegativeVariationSeeds_produceSafeResultsWithoutCrashing() {
        val catalog = indexedCatalog()
        val today = LocalDate.of(2026, 11, 21)
        val extremeProfile = UserProfile(
            goals = MacroGoals(calories = 1900, proteinGrams = 90, carbsGrams = 240, fatGrams = 60),
            dietaryProfile = DietaryProfile(
                dietStyle = DietStyle.VEGAN,
                excludedAllergens = setOf(
                    AllergenTag.GLUTEN,
                    AllergenTag.DAIRY,
                    AllergenTag.TREE_NUTS,
                    AllergenTag.PEANUT,
                    AllergenTag.EGG,
                    AllergenTag.FISH_SEAFOOD,
                ),
            ),
        )
        val summary = summaryWithLoggedMeals(today.toString(), extremeProfile.goals, emptyMap())

        // Test all meals + negative & extreme variationSeed values (Int.MIN_VALUE, -99, 0, Int.MAX_VALUE)
        for (meal in MealType.entries) {
            for (seed in listOf(Int.MIN_VALUE, -99, -1, 0, 7, Int.MAX_VALUE)) {
                val report = engine.generateRecommendations(
                    profile = extremeProfile,
                    dailySummary = summary,
                    mealType = meal,
                    catalogFoods = catalog,
                    historicalLogs = emptyList(),
                    today = today,
                    variationSeed = seed,
                )
                assertTrue("Expected safe recommendations for extreme vegan filter in $meal (seed=$seed)", report.recommendations.isNotEmpty())
                for (rec in report.recommendations) {
                    for (item in rec.items) {
                        val cf = classifier.classifySingle(item.food)
                        assertTrue(
                            "Forbidden food leaked under extreme vegan filter: ${item.food.name}",
                            classifier.isAllowedByPreferences(cf, extremeProfile.dietaryProfile, emptyMap()),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun recomputeRecommendationWithPortions_updatesCaloriesMacrosFitScoreMicrosAndGoalImpact() {
        val catalog = indexedCatalog()
        val today = LocalDate.of(2026, 11, 21)
        val profile = UserProfile(goals = MacroGoals(calories = 2000, proteinGrams = 140, carbsGrams = 210, fatGrams = 65))
        val summary = summaryWithLoggedMeals(today.toString(), profile.goals, emptyMap())

        val report = engine.generateRecommendations(
            profile = profile,
            dailySummary = summary,
            mealType = MealType.DINNER,
            catalogFoods = catalog,
            historicalLogs = emptyList(),
            today = today,
        )
        val baseRec = report.recommendations.first()

        // Empty overrides returns exact same object
        assertEquals(baseRec, engine.recomputeRecommendationWithPortions(baseRec, emptyMap(), report.budget))

        // Increase first item's grams by +50g
        val firstItem = baseRec.items.first()
        val targetGrams = (firstItem.totalGrams + 50f).coerceAtMost(firstItem.maxGrams)
        val updated = engine.recomputeRecommendationWithPortions(
            recommendation = baseRec,
            gramOverridesByFoodId = mapOf(firstItem.food.id to targetGrams),
            budget = report.budget,
        )

        if (targetGrams > firstItem.totalGrams + 1f) {
            assertTrue("Expected updated totalGrams to increase", updated.items.first().totalGrams > firstItem.totalGrams)
            assertNotEquals(baseRec.totalCalories, updated.totalCalories)
        }
        assertEquals(updated.items.sumOf { it.calories }, updated.totalCalories)
        assertEquals(updated.goalImpact.caloriesBefore + updated.totalCalories, updated.goalImpact.caloriesAfter)
        assertTrue(updated.fitScore in 20..100)
        assertTrue(updated.goalImpact.impactSummary.contains("%"))
    }

    @Test
    fun zeroIdUnsavedFoods_remainDistinctInRecommendationAssembly() {
        val today = LocalDate.of(2026, 11, 21)
        val unsavedFoods = listOf(
            FoodItem(0L, "Izgara Somon", "Balık", FoodCategory.MEAT_POULTRY, 208, 20f, 0f, 13f, "1 Porsiyon", 160f, preferredMealTypes = setOf(MealType.DINNER)),
            FoodItem(0L, "Bulgur Pilavı", "Tahıl", FoodCategory.GRAINS_BAKERY, 155, 4f, 28f, 3f, "1 Porsiyon", 180f, preferredMealTypes = setOf(MealType.DINNER)),
            FoodItem(0L, "Çoban Salata", "Sebze", FoodCategory.SALAD_MEZE, 55, 1.5f, 6f, 2.5f, "1 Kase", 180f, preferredMealTypes = setOf(MealType.DINNER)),
            FoodItem(0L, "Süzme Yoğurt", "Süt", FoodCategory.BREAKFAST, 96, 5f, 4f, 6.5f, "1 Kase", 150f, preferredMealTypes = setOf(MealType.DINNER)),
        )
        val goals = MacroGoals(calories = 2000, proteinGrams = 130, carbsGrams = 210, fatGrams = 65)
        val report = engine.generateRecommendations(
            profile = UserProfile(goals = goals),
            dailySummary = summaryWithLoggedMeals(today.toString(), goals, emptyMap()),
            mealType = MealType.DINNER,
            catalogFoods = unsavedFoods,
            historicalLogs = emptyList(),
            today = today,
        )
        assertTrue("Expected recommendations even when all food IDs are 0L", report.recommendations.isNotEmpty())
        for (rec in report.recommendations) {
            assertTrue("Expected >= 2 distinct foods per recommendation", rec.items.size >= 2)
            assertEquals(rec.items.size, rec.items.map { it.feedbackKey }.distinct().size)
        }
    }

    @Test
    fun inferDefaultMealType_followsSequentialNextMealAndGoalCalorieRules() {
        val sampleFood = indexedCatalog().first()
        val goals = MacroGoals(calories = 2000, proteinGrams = 140, carbsGrams = 210, fatGrams = 65)
        val loseProfile = UserProfile(
            goalType = GoalType.LOSE_WEIGHT,
            currentWeightKg = 80f,
            targetWeightKg = 72f,
            goals = goals,
        )
        val gainProfile = UserProfile(
            goalType = GoalType.GAIN_MUSCLE,
            currentWeightKg = 70f,
            targetWeightKg = 76f,
            goals = goals,
        )

        // 1. Nothing logged -> BREAKFAST
        val emptySummary = summaryWithLoggedMeals("2026-11-21", goals, emptyMap())
        assertEquals(MealType.BREAKFAST, engine.inferDefaultMealType(emptySummary, loseProfile))

        // 2. Breakfast logged -> LUNCH
        val afterBreakfast = summaryWithLoggedMeals(
            "2026-11-21",
            goals,
            mapOf(MealType.BREAKFAST to listOf(dummyLog(1L, "2026-11-21", MealType.BREAKFAST, sampleFood, grams = 100f, ts = 1000L))),
        )
        assertEquals(MealType.LUNCH, engine.inferDefaultMealType(afterBreakfast, loseProfile))

        // 3. Breakfast + Lunch logged -> DINNER
        val afterLunch = summaryWithLoggedMeals(
            "2026-11-21",
            goals,
            mapOf(
                MealType.BREAKFAST to listOf(dummyLog(1L, "2026-11-21", MealType.BREAKFAST, sampleFood, grams = 100f, ts = 1000L)),
                MealType.LUNCH to listOf(dummyLog(2L, "2026-11-21", MealType.LUNCH, sampleFood, grams = 100f, ts = 2000L)),
            ),
        )
        assertEquals(MealType.DINNER, engine.inferDefaultMealType(afterLunch, loseProfile))

        // 4. Breakfast + Lunch + Dinner logged (calories still < 2000) -> SNACK
        val afterDinner = summaryWithLoggedMeals(
            "2026-11-21",
            goals,
            mapOf(
                MealType.BREAKFAST to listOf(dummyLog(1L, "2026-11-21", MealType.BREAKFAST, sampleFood, grams = 100f, ts = 1000L)),
                MealType.LUNCH to listOf(dummyLog(2L, "2026-11-21", MealType.LUNCH, sampleFood, grams = 100f, ts = 2000L)),
                MealType.DINNER to listOf(dummyLog(3L, "2026-11-21", MealType.DINNER, sampleFood, grams = 100f, ts = 3000L)),
            ),
        )
        assertEquals(MealType.SNACK, engine.inferDefaultMealType(afterDinner, loseProfile))

        // 5. All 4 meals logged, calories still below daily goal -> SNACK
        val allMealsUnderTarget = summaryWithLoggedMeals(
            "2026-11-21",
            goals,
            mapOf(
                MealType.BREAKFAST to listOf(dummyLog(1L, "2026-11-21", MealType.BREAKFAST, sampleFood, grams = 80f, ts = 1000L)),
                MealType.LUNCH to listOf(dummyLog(2L, "2026-11-21", MealType.LUNCH, sampleFood, grams = 80f, ts = 2000L)),
                MealType.DINNER to listOf(dummyLog(3L, "2026-11-21", MealType.DINNER, sampleFood, grams = 80f, ts = 3000L)),
                MealType.SNACK to listOf(dummyLog(4L, "2026-11-21", MealType.SNACK, sampleFood, grams = 50f, ts = 4000L)),
            ),
        )
        assertTrue(allMealsUnderTarget.consumedCalories < goals.calories)
        assertEquals(MealType.SNACK, engine.inferDefaultMealType(allMealsUnderTarget, loseProfile))

        // 6. All 4 meals logged AND daily calorie target reached (>= 2000 kcal):
        //    - For weight loss / maintenance -> BREAKFAST
        //    - For weight gain (GAIN_MUSCLE) -> SNACK
        val allMealsAtOrOverTarget = summaryWithLoggedMeals(
            "2026-11-21",
            goals,
            mapOf(
                MealType.BREAKFAST to listOf(dummyLog(1L, "2026-11-21", MealType.BREAKFAST, sampleFood, grams = 600f, ts = 1000L)),
                MealType.LUNCH to listOf(dummyLog(2L, "2026-11-21", MealType.LUNCH, sampleFood, grams = 600f, ts = 2000L)),
                MealType.DINNER to listOf(dummyLog(3L, "2026-11-21", MealType.DINNER, sampleFood, grams = 600f, ts = 3000L)),
                MealType.SNACK to listOf(dummyLog(4L, "2026-11-21", MealType.SNACK, sampleFood, grams = 400f, ts = 4000L)),
            ),
        )
        assertTrue(allMealsAtOrOverTarget.consumedCalories >= goals.calories)
        assertEquals(MealType.BREAKFAST, engine.inferDefaultMealType(allMealsAtOrOverTarget, loseProfile))
        assertEquals(MealType.SNACK, engine.inferDefaultMealType(allMealsAtOrOverTarget, gainProfile))
    }
}
