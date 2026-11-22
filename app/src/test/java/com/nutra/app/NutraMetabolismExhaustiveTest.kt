package com.nutra.app

import com.nutra.app.domain.metabolism.AdaptiveMetabolismEngine
import com.nutra.app.domain.metabolism.DailyIntakeObservation
import com.nutra.app.domain.metabolism.MetabolismGuardrailState
import com.nutra.app.domain.metabolism.PlateauFactorType
import com.nutra.app.domain.model.ActivityLevel
import com.nutra.app.domain.model.BiologicalSex
import com.nutra.app.domain.model.GoalChangeSource
import com.nutra.app.domain.model.GoalHistoryEntry
import com.nutra.app.domain.model.GoalType
import com.nutra.app.domain.model.MacroGoals
import com.nutra.app.domain.model.UserProfile
import com.nutra.app.domain.model.WeightEntry
import com.nutra.app.domain.usecase.NutritionCalculatorUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.math.abs

class NutraMetabolismExhaustiveTest {

    private val calculator = NutritionCalculatorUseCase()
    private val engine = AdaptiveMetabolismEngine(calculator)
    private val today = LocalDate.of(2026, 11, 21)

    @Test
    fun allSexActivityAndGoalCombinations_produceValidFinitePhysiologicalOutputs() {
        for (sex in BiologicalSex.entries) {
            for (activity in ActivityLevel.entries) {
                for (goalType in GoalType.entries) {
                    val currentW = 76f
                    val targetW = when (goalType) {
                        GoalType.LOSE_WEIGHT -> 70f
                        GoalType.MAINTAIN -> 76f
                        GoalType.GAIN_MUSCLE -> 82f
                    }
                    val goals = calculator.calculateGoals(sex, 30, 174, currentW, activity, goalType)
                    val profile = UserProfile(
                        sex = sex,
                        age = 30,
                        heightCm = 174,
                        currentWeightKg = currentW,
                        targetWeightKg = targetW,
                        activityLevel = activity,
                        goalType = goalType,
                        goals = goals,
                    )
                    val result = engine.analyze(profile, emptyList(), emptyList(), today = today)

                    assertEquals(MetabolismGuardrailState.NORMAL_ADULT, result.guardrailState)
                    assertTrue("BMR physiological: ${result.priorBmrKcal}", result.priorBmrKcal in 1000..3000)
                    assertTrue("Prior TDEE physiological: ${result.priorTdeeKcal}", result.priorTdeeKcal in 1100..5200)
                    assertEquals("Zero updates must have zero TDEE drift from prior", result.priorTdeeKcal, result.estimatedTdeeKcal)
                    assertEquals(0, result.tdeeDeltaFromPriorKcal)
                    assertTrue(result.confidenceScore in 0..100)
                    assertTrue(result.currentTrendWeightKg.isFinite() && result.currentTrendWeightKg > 0f)
                    assertTrue(result.projectionCurve.size >= 16)
                    assertTrue(result.projectionCurve.all { it.expectedKg.isFinite() && it.optimisticKg.isFinite() && it.conservativeKg.isFinite() })
                }
            }
        }
    }

    @Test
    fun openingWeightTypoOutlierAndCorruptEntries_areCleanedAccurately() {
        val profile = UserProfile(
            sex = BiologicalSex.MALE,
            age = 29,
            heightCm = 178,
            currentWeightKg = 78f,
            targetWeightKg = 73f,
            activityLevel = ActivityLevel.MODERATE,
            goalType = GoalType.LOSE_WEIGHT,
        )

        // Opening entry (i == 0) is a massive typo (145 kg) followed by normal ~78 kg weigh-ins,
        // plus unsorted entries, future date, malformed date, NaN, out-of-range, and same-day tie-breakers!
        val messyWeights = listOf(
            WeightEntry(1L, today.minusDays(14).toString(), 145.0f, 100L), // Opening typo!
            WeightEntry(2L, today.minusDays(12).toString(), 78.4f, 200L),
            WeightEntry(3L, today.minusDays(10).toString(), 78.2f, 300L),
            WeightEntry(4L, today.minusDays(8).toString(), 92.0f, 400L),   // Mid-series typo!
            WeightEntry(5L, today.minusDays(6).toString(), 78.0f, 500L),
            WeightEntry(6L, today.minusDays(4).toString(), 77.9f, 600L),
            WeightEntry(7L, today.minusDays(2).toString(), 77.7f, 700L),
            WeightEntry(8L, today.toString(), 77.5f, 800L),
            WeightEntry(9L, today.toString(), 77.4f, 800L),                // Same day & ts, higher id wins -> 77.4
            WeightEntry(10L, today.plusDays(3).toString(), 70.0f, 900L),   // Future -> ignored
            WeightEntry(11L, "not-a-date", 77.0f, 910L),                   // Bad ISO -> ignored
            WeightEntry(12L, today.minusDays(3).toString(), Float.NaN, 920L),
            WeightEntry(13L, today.minusDays(5).toString(), 12.0f, 930L),  // < 25kg -> ignored
            WeightEntry(14L, today.minusDays(7).toString(), 500.0f, 940L), // > 350kg -> ignored
        ).shuffled()

        val result = engine.analyze(profile, emptyList(), messyWeights, today = today)

        assertTrue("Both opening and mid-series typos must be rejected (>=2), got ${result.dataQuality.ignoredOutlierWeighIns}", result.dataQuality.ignoredOutlierWeighIns >= 2)
        assertEquals(77.4f, result.currentRawWeightKg!!, 0.01f)
        assertTrue("Trend weight must not be pulled toward 145kg typo, got ${result.currentTrendWeightKg}", result.currentTrendWeightKg in 77.0f..79.0f)
    }

    @Test
    fun zeroUpdateKalmanInvariant_preventsDriftWhenOnlyWeighInsExistWithoutCompleteIntakeWindows() {
        val profile = UserProfile(
            sex = BiologicalSex.MALE,
            age = 32,
            heightCm = 180,
            currentWeightKg = 80f,
            targetWeightKg = 74f,
            activityLevel = ActivityLevel.MODERATE,
            goalType = GoalType.LOSE_WEIGHT,
        )
        // Weight drops from 83 -> 80 over 21 days, but NO meal logs at all!
        val weights = (21 downTo 0 step 3).mapIndexed { i, d ->
            WeightEntry(i + 1L, today.minusDays(d.toLong()).toString(), 83f - (21 - d) * (3f / 21f), 1000L + i)
        }
        val result = engine.analyze(profile, emptyList(), weights, today = today)
        assertFalse(result.isAdaptiveCalibrated)
        assertEquals("Zero Kalman updates must keep estimatedTdee == priorTdee", result.priorTdeeKcal, result.estimatedTdeeKcal)
        assertEquals(0, result.tdeeDeltaFromPriorKcal)
        assertNull(result.calorieProposal)
    }

    @Test
    fun kalmanFilter_calibratesUpwardWhenEatingHighCaloriesAndStillLosingWeight() {
        // Prior TDEE ~2450 (LIGHT activity, 75kg, 175cm, 30M)
        val profile = UserProfile(
            sex = BiologicalSex.MALE,
            age = 30,
            heightCm = 175,
            currentWeightKg = 75f,
            targetWeightKg = 70f,
            activityLevel = ActivityLevel.LIGHT,
            goalType = GoalType.LOSE_WEIGHT,
            goals = MacroGoals(calories = 2000, proteinGrams = 150, carbsGrams = 200, fatGrams = 65),
        )
        // User actually has high NEAT: eats 2700 kcal/day for 35 days AND loses 0.35 kg/week!
        val intakes = (1..35).map { d ->
            DailyIntakeObservation(today.minusDays(d.toLong()).toString(), 2700, 170f, 300f, 80f, 3, 8)
        }
        val weights = (35 downTo 0 step 2).mapIndexed { i, d ->
            WeightEntry(i + 1L, today.minusDays(d.toLong()).toString(), 76.75f - (35 - d) * (0.35f / 7f), 1000L + i)
        }

        val result = engine.analyze(profile, intakes, weights, today = today)
        assertTrue(result.isAdaptiveCalibrated)
        assertTrue(
            "Estimated TDEE (${result.estimatedTdeeKcal}) should adapt UPWARD above prior (${result.priorTdeeKcal})",
            result.estimatedTdeeKcal > result.priorTdeeKcal + 150,
        )
        assertTrue("tdeeDeltaFromPriorKcal should be positive", result.tdeeDeltaFromPriorKcal > 150)

        // Check proposal rationale wording is never contradictory (explicitly assert non-null!)
        val prop = result.calorieProposal
        assertNotNull("Expected upward calorie proposal when TDEE is well above active goal", prop)
        assertTrue("Expected upward calorie proposal when TDEE >> active goal", prop!!.deltaKcal > 0)
        assertFalse("Rationale must not claim TDEE is lower than goal when TDEE > goal", prop.rationale.contains("daha düşük"))
    }

    @Test
    fun alreadyAtOrPastTargetWeight_neverShowsFalsePlateauOrBogusOneWeekEta() {
        val intakes = (1..28).map { d ->
            DailyIntakeObservation(today.minusDays(d.toLong()).toString(), 2100, 150f, 220f, 65f, 3, 7)
        }

        // Case 1: LOSE_WEIGHT user already PAST target (current 68kg, target 70kg)
        val pastLossProfile = UserProfile(
            sex = BiologicalSex.MALE,
            age = 28,
            heightCm = 178,
            currentWeightKg = 68.0f,
            targetWeightKg = 70.0f,
            activityLevel = ActivityLevel.MODERATE,
            goalType = GoalType.LOSE_WEIGHT,
            goals = MacroGoals(calories = 2100, proteinGrams = 150, carbsGrams = 220, fatGrams = 65),
        )
        val flat68 = (28 downTo 0 step 2).mapIndexed { i, d ->
            WeightEntry(i + 1L, today.minusDays(d.toLong()).toString(), 68.0f, 1000L + i)
        }
        val pastLossRes = engine.analyze(pastLossProfile, intakes, flat68, today = today)
        assertFalse("Must NOT report plateau when user already passed weight-loss target", pastLossRes.plateauAssessment.isStalled)
        val pastLossEta = pastLossRes.targetEta
        assertNotNull(pastLossEta)
        assertTrue("ETA must report alreadyAtTarget=true when past loss target", pastLossEta!!.alreadyAtTarget)
        assertEquals(0, pastLossEta.expectedWeeks)

        // Case 2: GAIN_MUSCLE user already PAST target (current 84kg, target 82kg)
        val pastGainProfile = pastLossProfile.copy(
            currentWeightKg = 84.0f,
            targetWeightKg = 82.0f,
            goalType = GoalType.GAIN_MUSCLE,
            goals = MacroGoals(calories = 2800, proteinGrams = 170, carbsGrams = 340, fatGrams = 80),
        )
        val flat84 = (28 downTo 0 step 2).mapIndexed { i, d ->
            WeightEntry(i + 1L, today.minusDays(d.toLong()).toString(), 84.0f, 1000L + i)
        }
        val pastGainRes = engine.analyze(pastGainProfile, intakes, flat84, today = today)
        assertFalse("Must NOT report plateau when user already passed gain target", pastGainRes.plateauAssessment.isStalled)
        assertNotNull(pastGainRes.targetEta)
        assertTrue("ETA must report alreadyAtTarget=true when past gain target", pastGainRes.targetEta!!.alreadyAtTarget)
    }

    @Test
    fun gainMuscleGoal_projectsUpwardNonLinearCurveAndDetectsUnderEatingMismatch() {
        val profile = UserProfile(
            sex = BiologicalSex.MALE,
            age = 26,
            heightCm = 180,
            currentWeightKg = 72.0f,
            targetWeightKg = 78.0f,
            activityLevel = ActivityLevel.MODERATE,
            goalType = GoalType.GAIN_MUSCLE,
            goals = MacroGoals(calories = 2900, proteinGrams = 160, carbsGrams = 370, fatGrams = 85),
        )

        // User wants to gain muscle (goal 2900) but actually eats only 2400 -> flat weight!
        val underEatingIntakes = (1..28).map { d ->
            DailyIntakeObservation(today.minusDays(d.toLong()).toString(), 2400, 140f, 290f, 70f, 3, 7)
        }
        val flatWeights = (28 downTo 0 step 2).mapIndexed { i, d ->
            WeightEntry(i + 1L, today.minusDays(d.toLong()).toString(), 72.0f, 1000L + i)
        }
        val res = engine.analyze(profile, underEatingIntakes, flatWeights, today = today)
        assertTrue(res.plateauAssessment.isStalled)
        assertTrue(
            "Should flag INTAKE_GOAL_MISMATCH when under-eating relative to GAIN_MUSCLE target",
            res.plateauAssessment.factors.any { it.type == PlateauFactorType.INTAKE_GOAL_MISMATCH },
        )

        // Now test clean surplus projection (3000 kcal intake on ~2600 TDEE) -> upward curve!
        val surplusIntakes = (1..28).map { d ->
            DailyIntakeObservation(today.minusDays(d.toLong()).toString(), 3000, 165f, 390f, 85f, 3, 8)
        }
        val risingWeights = (28 downTo 0 step 2).mapIndexed { i, d ->
            WeightEntry(i + 1L, today.minusDays(d.toLong()).toString(), 71.0f + (28 - d) * (0.25f / 7f), 1000L + i)
        }
        val gainRes = engine.analyze(profile, surplusIntakes, risingWeights, today = today)
        assertTrue("Projection curve should rise for clean surplus", gainRes.projectionCurve.last().expectedKg > gainRes.projectionCurve.first().expectedKg + 0.5f)
        assertFalse(gainRes.plateauAssessment.isStalled)
    }

    @Test
    fun proposalCooldownAndDisableSwitchAndUnsortedGoalHistory_areStrictlyEnforced() {
        val baseProfile = UserProfile(
            sex = BiologicalSex.MALE,
            age = 30,
            heightCm = 178,
            currentWeightKg = 80f,
            targetWeightKg = 72f,
            activityLevel = ActivityLevel.MODERATE,
            goalType = GoalType.LOSE_WEIGHT,
            goals = MacroGoals(calories = 2100, proteinGrams = 160, carbsGrams = 210, fatGrams = 65),
            adaptiveMetabolismEnabled = true,
        )
        val cleanIntakes = (1..28).map { d ->
            DailyIntakeObservation(today.minusDays(d.toLong()).toString(), 2100, 160f, 210f, 65f, 3, 8)
        }
        val flatWeights = (28 downTo 0 step 2).mapIndexed { i, d ->
            WeightEntry(i + 1L, today.minusDays(d.toLong()).toString(), 80.0f, 1000L + i)
        }

        // Baseline confirms a proposal WOULD be generated
        val baseline = engine.analyze(baseProfile, cleanIntakes, flatWeights, today = today)
        assertNotNull("Expected proposal on clean 28-day plateau", baseline.calorieProposal)

        // 1. adaptiveMetabolismEnabled == false -> suppressed
        val disabled = engine.analyze(baseProfile.copy(adaptiveMetabolismEnabled = false), cleanIntakes, flatWeights, today = today)
        assertNull("Proposal must be suppressed when adaptiveMetabolismEnabled=false", disabled.calorieProposal)

        // 2. lastAdaptiveReviewDateIso 3 days ago (< 7 day cooldown) -> suppressed
        val recentlyDismissed = engine.analyze(
            baseProfile.copy(lastAdaptiveReviewDateIso = today.minusDays(3).toString()),
            cleanIntakes,
            flatWeights,
            today = today,
        )
        assertNull("Proposal must be suppressed during 7-day dismiss cooldown", recentlyDismissed.calorieProposal)

        // 3. Unsorted goalHistory where the NEWEST adaptive entry is NOT last in list -> still enforces cooldown!
        val unsortedHistory = listOf(
            GoalHistoryEntry(
                id = 2L,
                effectiveDateIso = today.minusDays(2).toString(), // Recent! Placed FIRST (unsorted)
                calories = 2100,
                proteinGrams = 160,
                carbsGrams = 210,
                fatGrams = 65,
                goalType = GoalType.LOSE_WEIGHT,
                targetWeightKg = 72f,
                source = GoalChangeSource.ADAPTIVE_APPROVED,
                timestampMillis = 2000L,
            ),
            GoalHistoryEntry(
                id = 1L,
                effectiveDateIso = today.minusDays(25).toString(), // Older! Placed LAST
                calories = 2250,
                proteinGrams = 165,
                carbsGrams = 235,
                fatGrams = 70,
                goalType = GoalType.LOSE_WEIGHT,
                targetWeightKg = 72f,
                source = GoalChangeSource.ADAPTIVE_APPROVED,
                timestampMillis = 1000L,
            ),
        )
        val historyCooldown = engine.analyze(baseProfile, cleanIntakes, flatWeights, goalHistory = unsortedHistory, today = today)
        assertNull("Unsorted goalHistory with 2-day-old adaptive change must enforce cooldown", historyCooldown.calorieProposal)

        // 4. Proposal after 8 days -> allowed again
        val expiredCooldown = engine.analyze(
            baseProfile.copy(lastAdaptiveReviewDateIso = today.minusDays(8).toString()),
            cleanIntakes,
            flatWeights,
            today = today,
        )
        assertNotNull("Proposal should resume after 7-day cooldown expires", expiredCooldown.calorieProposal)
    }

    @Test
    fun protectiveProteinMacroRebalanceAndMaleFloorAndNaNProfileWeights_stayConsistent() {
        // Test male floor >= 1500 kcal & macro calorie energy consistency even when carbs approach 60g floor
        val maleProfile = UserProfile(
            sex = BiologicalSex.MALE,
            age = 40,
            heightCm = 170,
            currentWeightKg = 92f,
            targetWeightKg = 78f,
            activityLevel = ActivityLevel.SEDENTARY,
            goalType = GoalType.LOSE_WEIGHT,
            goals = MacroGoals(calories = 1600, proteinGrams = 175, carbsGrams = 95, fatGrams = 58),
        )
        val intakes = (1..28).map { d ->
            DailyIntakeObservation(today.minusDays(d.toLong()).toString(), 1600, 175f, 95f, 58f, 3, 7)
        }
        val flatWeights = (28 downTo 0 step 2).mapIndexed { i, d ->
            WeightEntry(i + 1L, today.minusDays(d.toLong()).toString(), 92.0f, 1000L + i)
        }
        val res = engine.analyze(maleProfile, intakes, flatWeights, today = today)
        assertNotNull("Expected bounded calorie proposal for 28-day male plateau at 1600 kcal", res.calorieProposal)
        val prop = res.calorieProposal!!
        assertTrue("Male calorie floor >= 1500, got ${prop.proposedGoals.calories}", prop.proposedGoals.calories >= AdaptiveMetabolismEngine.MALE_MIN_CALORIES)
        val macroKcal = prop.proposedGoals.proteinGrams * 4 + prop.proposedGoals.carbsGrams * 4 + prop.proposedGoals.fatGrams * 9
        assertTrue(
            "Proposed macros ($macroKcal kcal) should closely align with proposed calories (${prop.proposedGoals.calories} kcal)",
            abs(macroKcal - prop.proposedGoals.calories) <= 95,
        )
        assertTrue("Fat floor >= 35g", prop.proposedGoals.fatGrams >= 35)
        assertTrue("Carbs floor >= 60g", prop.proposedGoals.carbsGrams >= 60)

        // Adversarial NaN / Infinity profile weights must not throw or produce NaN!
        val nanProfile = UserProfile(
            currentWeightKg = Float.NaN,
            targetWeightKg = Float.POSITIVE_INFINITY,
        )
        val nanRes = engine.analyze(nanProfile, emptyList(), emptyList(), today = today)
        assertTrue(nanRes.currentTrendWeightKg.isFinite() && nanRes.currentTrendWeightKg > 0f)
        assertTrue(nanRes.targetWeightKg.isFinite() && nanRes.targetWeightKg > 0f)
        assertTrue(nanRes.estimatedTdeeKcal in 1100..5200)
    }

    @Test
    fun phase3MetabolismInvariants_volatilityDetrendingLongGapScalingInsufficientEtaAndAdolescentCalculator() {
        val profile = UserProfile(
            sex = BiologicalSex.MALE,
            age = 29,
            heightCm = 178,
            currentWeightKg = 80f,
            targetWeightKg = 72f,
            activityLevel = ActivityLevel.MODERATE,
            goalType = GoalType.LOSE_WEIGHT,
            goals = MacroGoals(calories = 2000, proteinGrams = 160, carbsGrams = 200, fatGrams = 62),
        )

        // 1. Detrended MAD volatility stays low even during steep linear loss (-0.70 kg/week)
        val cleanIntakes = (1..28).map { d ->
            DailyIntakeObservation(today.minusDays(d.toLong()).toString(), 2000, 160f, 200f, 62f, 3, 7)
        }
        val steepCleanWeights = (28 downTo 0 step 2).mapIndexed { i, d ->
            WeightEntry(i + 1L, today.minusDays(d.toLong()).toString(), 82.8f - (28 - d) * (0.70f / 7f), 1000L + i)
        }
        val steepRes = engine.analyze(profile, cleanIntakes, steepCleanWeights, today = today)
        assertTrue(
            "Detrended scaleVolatilityKg should stay low (< 0.30 kg) on clean linear loss, got ${steepRes.scaleVolatilityKg}",
            steepRes.scaleVolatilityKg < 0.30f,
        )

        // 2. Multi-week weigh-in gap (32 days, legitimate 2.8 kg drop) must NOT be rejected as an outlier
        val longGapWeights = listOf(
            WeightEntry(1L, today.minusDays(40).toString(), 82.8f, 1000L),
            WeightEntry(2L, today.minusDays(8).toString(), 80.0f, 2000L),
            WeightEntry(3L, today.minusDays(4).toString(), 79.8f, 3000L),
            WeightEntry(4L, today.toString(), 79.6f, 4000L),
        )
        val longGapRes = engine.analyze(profile, emptyList(), longGapWeights, today = today)
        assertEquals(
            "Valid 2.8 kg loss across a 32-day gap must not be dropped as an outlier",
            0,
            longGapRes.dataQuality.ignoredOutlierWeighIns,
        )

        // 3. INSUFFICIENT data quality must NOT emit a speculative targetEta
        val sparseRes = engine.analyze(
            profile = profile,
            intakeObservations = listOf(DailyIntakeObservation(today.minusDays(1).toString(), 2000, 140f, 200f, 65f, 3, 6)),
            weightEntries = listOf(WeightEntry(1L, today.toString(), 80f, 1000L)),
            today = today,
        )
        assertNull("targetEta must be null when data quality is INSUFFICIENT", sparseRes.targetEta)

        // 4. Incomplete-day < 500 kcal guard: days logged at 420 kcal with 3 meals still count as incomplete
        val sub500Intakes = (1..28).map { d ->
            if (d % 4 == 0) {
                DailyIntakeObservation(today.minusDays(d.toLong()).toString(), 420, 30f, 45f, 12f, 3, 4)
            } else {
                DailyIntakeObservation(today.minusDays(d.toLong()).toString(), 2150, 155f, 215f, 68f, 3, 7)
            }
        }
        val sub500Res = engine.analyze(profile, sub500Intakes, steepCleanWeights, today = today)
        assertTrue(
            "Days with < 500 kcal must be flagged as suspected incomplete (>= 6), got ${sub500Res.dataQuality.suspectedIncompleteDaysLast28}",
            sub500Res.dataQuality.suspectedIncompleteDaysLast28 >= 6,
        )

        // 5. Adolescent (14..17) NutritionCalculatorUseCase forces Observation Mode (zero deficit/surplus)
        val teenLose = calculator.calculateGoals(BiologicalSex.FEMALE, 16, 165, 65f, ActivityLevel.MODERATE, GoalType.LOSE_WEIGHT)
        val teenMaintain = calculator.calculateGoals(BiologicalSex.FEMALE, 16, 165, 65f, ActivityLevel.MODERATE, GoalType.MAINTAIN)
        val teenGain = calculator.calculateGoals(BiologicalSex.FEMALE, 16, 165, 65f, ActivityLevel.MODERATE, GoalType.GAIN_MUSCLE)
        assertEquals("Adolescent LOSE_WEIGHT must equal MAINTAIN (zero deficit)", teenMaintain.calories, teenLose.calories)
        assertEquals("Adolescent GAIN_MUSCLE must equal MAINTAIN (zero surplus)", teenMaintain.calories, teenGain.calories)
    }
}
