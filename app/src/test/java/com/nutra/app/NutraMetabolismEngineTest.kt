package com.nutra.app

import com.nutra.app.core.database.TurkishFoodSeedCatalog
import com.nutra.app.domain.intelligence.FoodCulinaryClassifier
import com.nutra.app.domain.intelligence.HabitMaturity
import com.nutra.app.domain.intelligence.MealRecommendationEngine
import com.nutra.app.domain.intelligence.PortionOptimizer
import com.nutra.app.domain.intelligence.UserEatingProfileLearner
import com.nutra.app.domain.metabolism.AdaptiveMetabolismEngine
import com.nutra.app.domain.metabolism.DailyIntakeObservation
import com.nutra.app.domain.metabolism.DataQualityLevel
import com.nutra.app.domain.metabolism.MetabolismGuardrailState
import com.nutra.app.domain.metabolism.PlateauFactorType
import com.nutra.app.domain.model.ActivityLevel
import com.nutra.app.domain.model.BiologicalSex
import com.nutra.app.domain.model.DailyNutritionSummary
import com.nutra.app.domain.model.DietaryProfile
import com.nutra.app.domain.model.GoalType
import com.nutra.app.domain.model.MacroGoals
import com.nutra.app.domain.model.MealSummary
import com.nutra.app.domain.model.MealType
import com.nutra.app.domain.model.UserProfile
import com.nutra.app.domain.model.WeightEntry
import com.nutra.app.domain.repository.NutraBackupPayload
import com.nutra.app.domain.usecase.NutritionCalculatorUseCase
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.math.abs

class NutraMetabolismEngineTest {

    private val calculator = NutritionCalculatorUseCase()
    private val engine = AdaptiveMetabolismEngine(calculator)

    @Test
    fun userScenario_initial2700TdeeEating2250Losing300gPerWeekCalibratesDownward() {
        val today = LocalDate.of(2026, 11, 21)
        // Profile calibrated so Mifflin-St Jeor TDEE is ~2700 kcal (78kg, 178cm, 29M, MODERATE -> ~2716 kcal)
        val profile = UserProfile(
            sex = BiologicalSex.MALE,
            age = 29,
            heightCm = 178,
            currentWeightKg = 78.0f,
            targetWeightKg = 72.0f,
            activityLevel = ActivityLevel.MODERATE,
            goalType = GoalType.LOSE_WEIGHT,
            goals = MacroGoals(calories = 2300, proteinGrams = 175, carbsGrams = 230, fatGrams = 75),
        )

        // 4 weeks (28 days): consistent 2250 kcal/day intake and 0.30 kg/week steady loss (79.2 -> 78.0 kg)
        val intakes = (1..28).map { daysAgo ->
            DailyIntakeObservation(
                dateIso = today.minusDays(daysAgo.toLong()).toString(),
                calories = 2250,
                proteinGrams = 165f,
                carbsGrams = 230f,
                fatGrams = 72f,
                mealTypeCount = 3,
                itemCount = 8,
            )
        }
        val weights = (28 downTo 0 step 2).mapIndexed { idx, daysAgo ->
            val w = 79.2f - (28 - daysAgo) * (0.30f / 7f)
            WeightEntry(
                id = idx + 1L,
                dateIso = today.minusDays(daysAgo.toLong()).toString(),
                weightKg = w,
                timestampMillis = 1000L + idx,
            )
        }

        val result = engine.analyze(
            profile = profile,
            intakeObservations = intakes,
            weightEntries = weights,
            today = today,
        )

        assertTrue("Expected adaptive calibration after 28 solid days", result.isAdaptiveCalibrated)
        assertTrue("Expected HIGH or MODERATE quality", result.dataQuality.level in setOf(DataQualityLevel.MODERATE, DataQualityLevel.HIGH))
        assertTrue("Prior TDEE ~2700, got ${result.priorTdeeKcal}", result.priorTdeeKcal in 2650..2780)
        // Implied true TDEE = 2250 + (0.30 * ~7600 / 7) ≈ 2575 kcal -> Kalman should move meaningfully below prior!
        assertTrue(
            "Estimated TDEE (${result.estimatedTdeeKcal}) should adapt below prior (${result.priorTdeeKcal})",
            result.estimatedTdeeKcal < result.priorTdeeKcal - 40,
        )
        assertTrue(
            "Estimated TDEE (${result.estimatedTdeeKcal}) should converge toward ~2550-2650",
            result.estimatedTdeeKcal in 2480..2670,
        )
        assertTrue("Uncertainty should narrow below initial ~340", result.uncertaintyKcal < 240)
        assertTrue("Confidence should be >= 60, got ${result.confidenceScore}", result.confidenceScore >= 60)
        assertTrue("Weekly weight rate should be around -0.30 kg/wk, got ${result.weeklyWeightChangeKg}", result.weeklyWeightChangeKg in -0.45f..-0.18f)
    }

    @Test
    fun sparseOrInsufficientData_reportsLowReliabilityAndNeverProposesTargetChange() {
        val today = LocalDate.of(2026, 11, 21)
        val profile = UserProfile()
        val intakes = listOf(
            DailyIntakeObservation(today.minusDays(3).toString(), 1800, 110f, 180f, 60f, 3, 6),
            DailyIntakeObservation(today.minusDays(2).toString(), 2000, 120f, 200f, 65f, 3, 7),
        )
        val weights = listOf(
            WeightEntry(1L, today.minusDays(4).toString(), 75.5f, 100L),
            WeightEntry(2L, today.minusDays(1).toString(), 74.8f, 200L),
        )

        val result = engine.analyze(profile, intakes, weights, today = today)

        assertEquals(DataQualityLevel.INSUFFICIENT, result.dataQuality.level)
        assertFalse(result.isAdaptiveCalibrated)
        assertNull("Must never propose calorie change on insufficient data", result.calorieProposal)
        assertTrue(result.confidenceScore < 40)
    }

    @Test
    fun incompleteLogsAndWeightTypoOutliers_doNotCorruptEstimatedTdee() {
        val today = LocalDate.of(2026, 11, 21)
        val profile = UserProfile(
            sex = BiologicalSex.MALE,
            age = 30,
            heightCm = 176,
            currentWeightKg = 76f,
            targetWeightKg = 72f,
            activityLevel = ActivityLevel.MODERATE,
            goalType = GoalType.LOSE_WEIGHT,
        )

        // Mix complete days (~2100 kcal) with forgotten single-meal days (300 kcal) and 0 kcal days
        val intakes = (1..28).map { daysAgo ->
            when {
                daysAgo % 7 == 0 -> DailyIntakeObservation(today.minusDays(daysAgo.toLong()).toString(), 0, 0f, 0f, 0f, 0, 0)
                daysAgo % 5 == 0 -> DailyIntakeObservation(today.minusDays(daysAgo.toLong()).toString(), 320, 15f, 35f, 10f, 1, 1)
                else -> DailyIntakeObservation(today.minusDays(daysAgo.toLong()).toString(), 2100, 150f, 210f, 65f, 3, 7)
            }
        }
        // Stable slow loss with one huge typo weigh-in (+12 kg overnight)
        val weights = (28 downTo 0 step 2).mapIndexed { idx, daysAgo ->
            val trueW = 77.2f - (28 - daysAgo) * (0.3f / 7f)
            val measuredW = if (daysAgo == 10) trueW + 12.0f else trueW
            WeightEntry(idx + 1L, today.minusDays(daysAgo.toLong()).toString(), measuredW, 1000L + idx)
        }

        val result = engine.analyze(profile, intakes, weights, today = today)

        assertTrue("Typo outlier weigh-in should be flagged & ignored", result.dataQuality.ignoredOutlierWeighIns >= 1)
        assertTrue("Incomplete single-meal days should be detected", result.dataQuality.suspectedIncompleteDaysLast28 >= 3)
        // Average intake should stay near 2100 (NOT dragged down by 0 or 320 kcal incomplete days!)
        assertNotNull(result.averageDailyIntakeLast28Kcal)
        assertTrue("Avg intake should ignore incomplete/0 days, got ${result.averageDailyIntakeLast28Kcal}", abs((result.averageDailyIntakeLast28Kcal ?: 0) - 2100) <= 50)
        // TDEE should stay physiological (not blown up by the +12kg typo)
        assertTrue("Estimated TDEE should remain physiological, got ${result.estimatedTdeeKcal}", result.estimatedTdeeKcal in 2100..2900)
    }

    @Test
    fun dynamicWeightProjection_isNonLinearAndWidensUncertaintyOverTime() {
        val today = LocalDate.of(2026, 11, 21)
        val profile = UserProfile(
            sex = BiologicalSex.MALE,
            age = 28,
            heightCm = 178,
            currentWeightKg = 84f,
            targetWeightKg = 74f,
            activityLevel = ActivityLevel.MODERATE,
            goalType = GoalType.LOSE_WEIGHT,
            goals = MacroGoals(calories = 2100, proteinGrams = 165, carbsGrams = 210, fatGrams = 65),
        )
        val weights = listOf(WeightEntry(1L, today.toString(), 84f, 1000L))
        val result = engine.analyze(profile, emptyList(), weights, today = today)

        val curve = result.projectionCurve
        assertTrue(curve.size >= 16)
        // Check non-linear deceleration: week 1-4 drop should exceed week 12-16 drop!
        val earlyDrop = curve[0].expectedKg - curve[4].expectedKg
        val lateDrop = curve[12].expectedKg - curve[16].expectedKg
        assertTrue("Expected positive weight loss early ($earlyDrop)", earlyDrop > 0.5f)
        assertTrue("Dynamic model should show gradual deceleration ($earlyDrop >= $lateDrop)", earlyDrop >= lateDrop)

        // Uncertainty fan width should widen over time
        val spreadWeek2 = abs(curve[2].conservativeKg - curve[2].optimisticKg)
        val spreadWeek16 = abs(curve[16].conservativeKg - curve[16].optimisticKg)
        assertTrue("Uncertainty band must grow with horizon ($spreadWeek16 > $spreadWeek2)", spreadWeek16 > spreadWeek2)
    }

    @Test
    fun plateauEvaluator_distinguishesWaterNoiseAndIncompleteLogsFromConfirmedPlateau() {
        val today = LocalDate.of(2026, 11, 21)
        val profile = UserProfile(
            sex = BiologicalSex.MALE,
            age = 30,
            heightCm = 178,
            currentWeightKg = 80f,
            targetWeightKg = 72f,
            activityLevel = ActivityLevel.MODERATE,
            goalType = GoalType.LOSE_WEIGHT,
            goals = MacroGoals(calories = 2100, proteinGrams = 160, carbsGrams = 210, fatGrams = 65),
        )

        // Case A: 16-day stall with sparse weigh-ins and missing logs -> must recommend WAITING, NOT cut calories!
        val sparseIntakes = (1..16).filter { it % 2 == 0 }.map { daysAgo ->
            DailyIntakeObservation(today.minusDays(daysAgo.toLong()).toString(), 2100, 150f, 200f, 65f, 3, 7)
        }
        val noisyFlatWeights = listOf(16, 11, 6, 1).mapIndexed { i, d ->
            WeightEntry(i + 1L, today.minusDays(d.toLong()).toString(), 80.0f + (if (i % 2 == 0) 0.4f else -0.2f), 1000L + i)
        }
        val noisyResult = engine.analyze(profile, sparseIntakes, noisyFlatWeights, today = today)
        assertTrue("Stall should be recognized", noisyResult.plateauAssessment.isStalled)
        assertTrue("Must recommend waiting when data is sparse/short", noisyResult.plateauAssessment.recommendsWaiting)
        assertNull("Must NOT propose calorie reduction during inconclusive plateau", noisyResult.calorieProposal)
        assertTrue(
            noisyResult.plateauAssessment.factors.any {
                it.type == PlateauFactorType.INCOMPLETE_LOGGING || it.type == PlateauFactorType.SPARSE_WEIGH_INS || it.type == PlateauFactorType.WATER_RETENTION_NOISE
            },
        )

        // Case B: 28-day clean, consistent logging at 2100 kcal + frequent weigh-ins with completely flat weight 80.0kg
        val cleanIntakes = (1..28).map { daysAgo ->
            DailyIntakeObservation(today.minusDays(daysAgo.toLong()).toString(), 2100, 160f, 210f, 65f, 3, 8)
        }
        val cleanFlatWeights = (28 downTo 0 step 2).mapIndexed { i, d ->
            WeightEntry(i + 1L, today.minusDays(d.toLong()).toString(), 80.0f, 1000L + i)
        }
        val confirmedResult = engine.analyze(profile, cleanIntakes, cleanFlatWeights, today = today)
        assertTrue(confirmedResult.plateauAssessment.isStalled)
        assertFalse("Confirmed 28-day clean plateau should allow measured review", confirmedResult.plateauAssessment.recommendsWaiting)
        assertEquals(PlateauFactorType.METABOLIC_OR_ACTIVITY_SHIFT, confirmedResult.plateauAssessment.factors.first().type)
        assertNotNull("Confirmed plateau with strong evidence should offer measured calorie adjustment", confirmedResult.calorieProposal)
        assertTrue(
            "Adjustment step must never exceed 150 kcal",
            abs(confirmedResult.calorieProposal!!.deltaKcal) <= AdaptiveMetabolismEngine.MAX_SINGLE_STEP_KCAL,
        )
    }

    @Test
    fun clinicalSafetyGuardrails_protectAdolescentsSpecialHealthAndCalorieFloors() {
        val today = LocalDate.of(2026, 11, 21)
        val cleanIntakes = (1..28).map { d ->
            DailyIntakeObservation(today.minusDays(d.toLong()).toString(), 1800, 120f, 200f, 55f, 3, 7)
        }
        val flatWeights = (28 downTo 0 step 2).mapIndexed { i, d ->
            WeightEntry(i + 1L, today.minusDays(d.toLong()).toString(), 70.0f, 1000L + i)
        }

        // 1. Adolescent (< 18) MUST be protected
        val teenProfile = UserProfile(
            sex = BiologicalSex.FEMALE,
            age = 16,
            heightCm = 165,
            currentWeightKg = 70f,
            targetWeightKg = 60f,
            goalType = GoalType.LOSE_WEIGHT,
            goals = MacroGoals(calories = 1800, proteinGrams = 110, carbsGrams = 200, fatGrams = 60),
        )
        val teenResult = engine.analyze(teenProfile, cleanIntakes, flatWeights, today = today)
        assertEquals(MetabolismGuardrailState.ADOLESCENT_PROTECTED, teenResult.guardrailState)
        assertNotNull(teenResult.guardrailNotice)
        assertNull("Adolescent must NEVER receive calorie deficit proposal", teenResult.calorieProposal)

        // 2. Adult with Special Health Guardrail MUST be protected
        val specialHealthProfile = teenProfile.copy(
            age = 29,
            dietaryProfile = DietaryProfile(specialHealthGuardrail = true),
        )
        val specialResult = engine.analyze(specialHealthProfile, cleanIntakes, flatWeights, today = today)
        assertEquals(MetabolismGuardrailState.SPECIAL_HEALTH_PROTECTED, specialResult.guardrailState)
        assertNull("Special health guardrail must disable calorie proposals", specialResult.calorieProposal)

        // 3. Adult female calorie floor (>= 1200 kcal)
        val petiteFemale = UserProfile(
            sex = BiologicalSex.FEMALE,
            age = 35,
            heightCm = 156,
            currentWeightKg = 55f,
            targetWeightKg = 50f,
            activityLevel = ActivityLevel.SEDENTARY,
            goalType = GoalType.LOSE_WEIGHT,
            goals = MacroGoals(calories = 1320, proteinGrams = 100, carbsGrams = 130, fatGrams = 42),
        )
        val lowIntakes = (1..28).map { d ->
            DailyIntakeObservation(today.minusDays(d.toLong()).toString(), 1320, 100f, 130f, 42f, 3, 6)
        }
        val flatLowWeights = (28 downTo 0 step 2).mapIndexed { i, d ->
            WeightEntry(i + 1L, today.minusDays(d.toLong()).toString(), 55.0f, 1000L + i)
        }
        val floorResult = engine.analyze(petiteFemale, lowIntakes, flatLowWeights, today = today)
        assertNotNull("Expected a bounded calorie proposal for confirmed 28-day plateau at 1320 kcal", floorResult.calorieProposal)
        val prop = floorResult.calorieProposal!!
        assertTrue(
            "Female calorie proposal must respect >= 1200 floor, got ${prop.proposedGoals.calories}",
            prop.proposedGoals.calories >= AdaptiveMetabolismEngine.FEMALE_MIN_CALORIES,
        )
    }

    @Test
    fun closedLoopIntegration_runsEndToEndOnCommitted30DaySyntheticBackupFixture() {
        val stream = javaClass.classLoader?.getResourceAsStream("synthetic_metabolism_30d.json")
        assertNotNull("Committed fixture synthetic_metabolism_30d.json must exist on test classpath", stream)
        val rawJson = stream!!.bufferedReader(Charsets.UTF_8).use { it.readText() }

        val json = Json { ignoreUnknownKeys = true }
        val payload = json.decodeFromString<NutraBackupPayload>(rawJson)

        // 1. Aggregate daily intakes from 30-day synthetic logs
        val dailyIntakes = payload.mealLogs
            .groupBy { it.dateIso }
            .map { (dateIso, logs) ->
                DailyIntakeObservation(
                    dateIso = dateIso,
                    calories = logs.sumOf { it.calories },
                    proteinGrams = logs.sumOf { it.proteinGrams.toDouble() }.toFloat(),
                    carbsGrams = logs.sumOf { it.carbsGrams.toDouble() }.toFloat(),
                    fatGrams = logs.sumOf { it.fatGrams.toDouble() }.toFloat(),
                    mealTypeCount = logs.map { it.mealType }.distinct().size,
                    itemCount = logs.size,
                )
            }

        val today = LocalDate.parse(payload.exportedAtDateIso)
        val metabolismResult = engine.analyze(
            profile = payload.userProfile,
            intakeObservations = dailyIntakes,
            weightEntries = payload.weightEntries,
            today = today,
        )

        assertTrue("30-day fixture should calibrate TDEE", metabolismResult.isAdaptiveCalibrated)
        assertTrue("30-day fixture should show real weight loss velocity", metabolismResult.weeklyWeightChangeKg < -0.15f)
        assertTrue("Estimated TDEE should be reasonable", metabolismResult.estimatedTdeeKcal in 1800..3600)
        assertTrue(metabolismResult.tdeeHistory.size >= 2)
        assertTrue(metabolismResult.projectionCurve.size >= 16)

        // 2. Feed active (or proposed) goals into Nutra Intelligence on the same user's history!
        val effectiveGoals = metabolismResult.calorieProposal?.proposedGoals ?: payload.userProfile.goals
        val catalog = TurkishFoodSeedCatalog.initialFoods.mapIndexed { idx, f -> f.copy(id = idx + 1L) }
        val intelEngine = MealRecommendationEngine(
            FoodCulinaryClassifier(),
            UserEatingProfileLearner(),
            PortionOptimizer(),
        )
        val todayLogs = payload.mealLogs.filter { it.dateIso == today.toString() && it.mealType != MealType.DINNER }
        val summaryBeforeDinner = DailyNutritionSummary(
            dateIso = today.toString(),
            goals = effectiveGoals,
            meals = MealType.entries.map { mt -> MealSummary(mt, todayLogs.filter { it.mealType == mt }) },
            waterMl = payload.waterLogs[today.toString()] ?: 0,
        )

        val intelReport = intelEngine.generateRecommendations(
            profile = payload.userProfile.copy(goals = effectiveGoals),
            dailySummary = summaryBeforeDinner,
            mealType = MealType.DINNER,
            catalogFoods = catalog,
            historicalLogs = payload.mealLogs,
            today = today,
        )

        assertEquals(HabitMaturity.ESTABLISHED, intelReport.eatingProfile.maturity)
        assertTrue("Closed-loop Nutra Intelligence must produce dinner recommendations", intelReport.recommendations.isNotEmpty())
        assertTrue(intelReport.recommendations.all { it.totalCalories > 0 && it.items.size >= 2 })
    }
}
