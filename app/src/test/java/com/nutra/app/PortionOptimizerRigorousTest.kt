package com.nutra.app

import com.nutra.app.core.database.TurkishFoodSeedCatalog
import com.nutra.app.domain.intelligence.ClassifiedFood
import com.nutra.app.domain.intelligence.EdibilityStatus
import com.nutra.app.domain.intelligence.FoodCulinaryClassifier
import com.nutra.app.domain.intelligence.MealBudget
import com.nutra.app.domain.intelligence.MealBudgetMode
import com.nutra.app.domain.intelligence.MealRole
import com.nutra.app.domain.intelligence.PortionConstraint
import com.nutra.app.domain.intelligence.PortionOptimizer
import com.nutra.app.domain.model.FoodCategory
import com.nutra.app.domain.model.FoodItem
import com.nutra.app.domain.model.MealType
import com.nutra.app.domain.model.ServingUnitLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PortionOptimizerRigorousTest {

    private val classifier = FoodCulinaryClassifier()
    private val optimizer = PortionOptimizer()

    private fun indexedCatalog(): List<FoodItem> =
        TurkishFoodSeedCatalog.initialFoods.mapIndexed { idx, item -> item.copy(id = idx + 1L) }

    private fun makeBudget(
        calories: Int = 650,
        protein: Float = 45f,
        carbs: Float = 65f,
        fat: Float = 20f,
        mealType: MealType = MealType.DINNER,
    ): MealBudget = MealBudget(
        mealType = mealType,
        targetCalories = calories,
        targetProteinGrams = protein,
        targetCarbsGrams = carbs,
        targetFatGrams = fat,
        dailyRemainingCalories = calories,
        dailyRemainingProtein = protein,
        dailyRemainingCarbs = carbs,
        dailyRemainingFat = fat,
        mode = MealBudgetMode.REMAINING_FOR_DAY,
        budgetSummaryText = "",
    )

    @Test
    fun optimizePortions_handlesEmptyOneThroughSixFoodsIncludingDiscreteRefinementAboveFour() {
        val catalog = indexedCatalog()
        val byCode = catalog.associateBy { it.catalogCode }
        val pool = listOf("TR0186", "TR0562", "TR0853", "TR0565", "TR0460", "TR0122")
            .map { classifier.classifySingle(byCode.getValue(it)) }

        // k = 0
        val emptyRes = optimizer.optimizePortions(emptyList(), makeBudget())
        assertTrue(emptyRes.items.isEmpty())
        assertEquals(0, emptyRes.totalCalories)
        assertEquals(0, emptyRes.fitScore)

        // k = 1..6 (explicitly exercises exact DFS for k<=4 and discrete coordinate descent for k=5,6!)
        for (k in 1..6) {
            val selected = pool.take(k)
            val budget = makeBudget(
                calories = if (k == 1) 350 else if (k >= 5) 850 else 650,
                protein = if (k == 1) 35f else 50f,
                carbs = if (k == 1) 15f else 75f,
                fat = if (k == 1) 10f else 24f,
            )
            val result = optimizer.optimizePortions(selected, budget)

            assertEquals("Expected $k items", k, result.items.size)
            assertTrue("fitScore out of range: ${result.fitScore}", result.fitScore in 20..100)
            assertTrue("totalCalories must be positive", result.totalCalories > 0)
            assertTrue(result.totalProtein.isFinite() && result.totalProtein >= 0f)
            assertTrue(result.totalCarbs.isFinite() && result.totalCarbs >= 0f)
            assertTrue(result.totalFat.isFinite() && result.totalFat >= 0f)

            for (item in result.items) {
                assertTrue(
                    "k=$k ${item.food.name}: totalGrams=${item.totalGrams} < minGrams=${item.minGrams}",
                    item.totalGrams >= item.minGrams - 0.01f,
                )
                assertTrue(
                    "k=$k ${item.food.name}: totalGrams=${item.totalGrams} > maxGrams=${item.maxGrams}",
                    item.totalGrams <= item.maxGrams + 0.01f,
                )
            }
        }
    }

    @Test
    fun strictBoxConstraintInvariant_holdsAcrossExtremeBudgetsAndAdversarialInputs() {
        val catalog = indexedCatalog()
        val classified = classifier.classifyCatalog(catalog).filter { it.edibility == EdibilityStatus.READY_TO_EAT }

        val extremeBudgets = listOf(
            makeBudget(calories = 100, protein = 5f, carbs = 10f, fat = 3f, mealType = MealType.SNACK),
            makeBudget(calories = 250, protein = 35f, carbs = 12f, fat = 6f, mealType = MealType.SNACK),
            makeBudget(calories = 600, protein = 45f, carbs = 60f, fat = 18f, mealType = MealType.LUNCH),
            makeBudget(calories = 1400, protein = 90f, carbs = 160f, fat = 45f, mealType = MealType.DINNER),
            makeBudget(calories = 2400, protein = 160f, carbs = 250f, fat = 80f, mealType = MealType.DINNER),
            // Adversarial non-finite/negative budget targets
            makeBudget(calories = -500, protein = Float.NaN, carbs = Float.POSITIVE_INFINITY, fat = -10f),
        )

        // Pick 20 diverse triplets across catalog (solids, liquids, nuts, spreads, salads, meats)
        for (start in 0 until 100 step 5) {
            val triplet = listOf(
                classified[start % classified.size],
                classified[(start + 73) % classified.size],
                classified[(start + 191) % classified.size],
            )
            for (budget in extremeBudgets) {
                val res = optimizer.optimizePortions(
                    foods = triplet,
                    budget = budget,
                    usualGramsByFoodId = mapOf(
                        triplet[0].food.id to Float.NaN,
                        triplet[1].food.id to -40f,
                        triplet[2].food.id to 99999f,
                    ),
                )
                assertEquals(3, res.items.size)
                assertTrue(res.fitScore in 0..100)
                for (item in res.items) {
                    assertTrue(item.totalGrams.isFinite())
                    assertTrue(
                        "Invariant violated (min): ${item.food.name} got ${item.totalGrams} < ${item.minGrams}",
                        item.totalGrams >= item.minGrams - 0.01f,
                    )
                    assertTrue(
                        "Invariant violated (max): ${item.food.name} got ${item.totalGrams} > ${item.maxGrams}",
                        item.totalGrams <= item.maxGrams + 0.01f,
                    )
                }
            }
        }
    }

    @Test
    fun manualPortionStepping_withPreferHouseholdSnapFalseNeverGetsStuck() {
        val catalog = indexedCatalog()
        // Foods with large default household units (200g / 250g / 150g) that previously snapped back on +/-10g or +/-20g
        val largeUnitCodes = listOf("TR0853", "TR0447", "TR0562", "TR0565", "TR0186")
        val foods = largeUnitCodes.map { code -> classifier.classifySingle(catalog.first { it.catalogCode == code }) }

        for (cf in foods) {
            val c = cf.portionConstraint
            val mid = ((c.minGrams + c.maxGrams) / 2f).coerceIn(c.minGrams + c.gramStep, c.maxGrams - c.gramStep)
            val base = optimizer.buildPortionWithGrams(cf, mid, preferHouseholdSnap = false)

            // Stepping up by gramStep MUST strictly increase totalGrams!
            val steppedUp = optimizer.buildPortionWithGrams(cf, base.totalGrams + c.gramStep, preferHouseholdSnap = false)
            assertTrue(
                "${cf.food.name}: stepping UP from ${base.totalGrams} by +${c.gramStep} stuck at ${steppedUp.totalGrams}",
                steppedUp.totalGrams > base.totalGrams + 0.5f,
            )

            // Stepping down by gramStep MUST strictly decrease totalGrams!
            val steppedDown = optimizer.buildPortionWithGrams(cf, base.totalGrams - c.gramStep, preferHouseholdSnap = false)
            assertTrue(
                "${cf.food.name}: stepping DOWN from ${base.totalGrams} by -${c.gramStep} stuck at ${steppedDown.totalGrams}",
                steppedDown.totalGrams < base.totalGrams - 0.5f,
            )
        }
    }

    @Test
    fun buildPortionWithGrams_clampsOutOfBoundsAndHandlesZeroCalorieAndLiquidFoods() {
        val waterLikeDrink = FoodItem(
            id = 7001L,
            name = "Maden Suyu",
            subtitle = "Doğal mineralli içecek",
            category = FoodCategory.DRINKS,
            caloriesPer100g = 0,
            proteinPer100g = 0f,
            carbsPer100g = 0f,
            fatPer100g = 0f,
            defaultUnitLabel = "1 Şişe",
            defaultUnitGrams = 200f,
            baseUnit = "ml",
        )
        val cf = classifier.classifySingle(waterLikeDrink)

        val underMin = optimizer.buildPortionWithGrams(cf, -50f, preferHouseholdSnap = false)
        assertEquals(cf.portionConstraint.minGrams, underMin.totalGrams, 0.01f)
        assertEquals(0, underMin.calories)
        assertEquals("ml", underMin.food.displayUnitSuffix)

        val overMax = optimizer.buildPortionWithGrams(cf, 5000f, preferHouseholdSnap = false)
        assertEquals(cf.portionConstraint.maxGrams, overMax.totalGrams, 0.01f)

        val nanInput = optimizer.buildPortionWithGrams(cf, Float.NaN, preferHouseholdSnap = true)
        assertTrue(nanInput.totalGrams.isFinite())
        assertTrue(nanInput.totalGrams in cf.portionConstraint.minGrams..cf.portionConstraint.maxGrams)

        // Exact raw unit label for liquid when non-household grams are requested
        val oddMl = optimizer.buildPortionWithGrams(cf, 175f, preferHouseholdSnap = false)
        assertEquals(ServingUnitLabels.RAW_ML, oddMl.unitLabel)
        assertEquals(175f, oddMl.totalGrams, 0.5f)
    }

    @Test
    fun adversarialCorruptedFoodAndConstraint_neverCrashesOrProducesNaN() {
        val corruptedFood = FoodItem(
            id = 8888L,
            name = "Bozuk Besin",
            subtitle = "",
            category = FoodCategory.CUSTOM,
            caloriesPer100g = -100,
            proteinPer100g = Float.NaN,
            carbsPer100g = Float.POSITIVE_INFINITY,
            fatPer100g = -5f,
            defaultUnitLabel = "1 Porsiyon",
            defaultUnitGrams = Float.NaN,
        )
        val corruptedClassified = ClassifiedFood(
            food = corruptedFood,
            feedbackKey = "custom:8888:bozuk besin",
            edibility = EdibilityStatus.READY_TO_EAT,
            role = MealRole.MAIN_COMPOSITE_DISH,
            portionConstraint = PortionConstraint(
                minGrams = Float.NaN,
                typicalGrams = Float.NEGATIVE_INFINITY,
                maxGrams = -10f,
                gramStep = 0f,
            ),
            allergens = emptySet(),
            containsAnimalProduct = false,
            containsRedMeat = false,
            containsPoultry = false,
            containsFish = false,
            isTurkishHomeCooked = false,
            isWholeFood = false,
            effectiveSodiumMgPer100g = null,
        )

        val res = optimizer.optimizePortions(listOf(corruptedClassified), makeBudget())
        assertEquals(1, res.items.size)
        val item = res.items.first()
        assertTrue(item.totalGrams.isFinite() && item.totalGrams > 0f)
        assertEquals(0, item.calories)
        assertEquals(0f, item.proteinGrams, 0.001f)
        assertEquals(0f, item.carbsGrams, 0.001f)
        assertEquals(0f, item.fatGrams, 0.001f)
        assertTrue(res.fitScore in 0..100)
    }

    @Test
    fun computeFitScore_rewardsExactMatchAndDegradesMonotonically() {
        val budget = makeBudget(calories = 600, protein = 45f, carbs = 60f, fat = 20f)
        val perfect = optimizer.computeFitScore(600, 45f, 60f, 20f, budget)
        val close = optimizer.computeFitScore(640, 42f, 65f, 22f, budget)
        val medium = optimizer.computeFitScore(450, 25f, 50f, 15f, budget)
        val poor = optimizer.computeFitScore(1200, 5f, 180f, 55f, budget)

        assertEquals(100, perfect)
        assertTrue("Expected perfect ($perfect) > close ($close)", perfect > close)
        assertTrue("Expected close ($close) > medium ($medium)", close > medium)
        assertTrue("Expected medium ($medium) > poor ($poor)", medium > poor)
        assertTrue("Fit score must stay within 0..100, got $poor", poor in 0..100)

        // Non-finite inputs must remain safely within 0..100
        val nanScore = optimizer.computeFitScore(
            actualCal = 500,
            actualP = Float.NaN,
            actualC = Float.POSITIVE_INFINITY,
            actualF = -5f,
            budget = makeBudget(calories = 0, protein = Float.NaN, carbs = Float.NaN, fat = Float.NaN),
        )
        assertTrue(nanScore in 0..100)
        assertTrue(perfect - close > 0)
    }
}
