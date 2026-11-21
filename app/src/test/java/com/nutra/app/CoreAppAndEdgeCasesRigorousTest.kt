package com.nutra.app

import androidx.sqlite.db.SupportSQLiteDatabase
import com.nutra.app.core.database.FoodFeedbackEntity
import com.nutra.app.core.database.FoodItemEntity
import com.nutra.app.core.database.GoalHistoryEntity
import com.nutra.app.core.database.MealLogEntity
import com.nutra.app.core.database.MetabolismSnapshotEntity
import com.nutra.app.core.database.TurkishFoodSeedCatalog
import com.nutra.app.core.database.WeightLogEntity
import com.nutra.app.core.datastore.MAX_EXCLUDED_KEYWORDS
import com.nutra.app.core.datastore.sanitizeDietaryProfileData
import com.nutra.app.core.designsystem.NutraFormatters
import com.nutra.app.core.di.AppModule
import com.nutra.app.core.notification.WaterReminderBounds
import com.nutra.app.core.notification.WaterReminderScheduleCalculator
import com.nutra.app.domain.intelligence.DailyGoalImpactPreview
import com.nutra.app.domain.model.ActivityLevel
import com.nutra.app.domain.model.BiologicalSex
import com.nutra.app.domain.model.DietaryProfile
import com.nutra.app.domain.model.FoodCategory
import com.nutra.app.domain.model.FoodFeedbackRecord
import com.nutra.app.domain.model.FoodFeedbackStatus
import com.nutra.app.domain.model.FoodItem
import com.nutra.app.domain.model.GoalChangeSource
import com.nutra.app.domain.model.GoalHistoryEntry
import com.nutra.app.domain.model.GoalType
import com.nutra.app.domain.model.MacroGoals
import com.nutra.app.domain.model.MealLogItem
import com.nutra.app.domain.model.MealType
import com.nutra.app.domain.model.MetabolismSnapshotRecord
import com.nutra.app.domain.model.ServingUnit
import com.nutra.app.domain.model.ServingUnitLabels
import com.nutra.app.domain.model.UserProfile
import com.nutra.app.domain.model.WaterReminderSettings
import com.nutra.app.domain.model.WeightEntry
import com.nutra.app.domain.repository.NutraBackupPayload
import com.nutra.app.domain.repository.remapAndSanitizeFoodFeedback
import com.nutra.app.domain.repository.sanitizeImportedGoalHistory
import com.nutra.app.domain.repository.validateBackupPayload
import com.nutra.app.domain.usecase.ContextualMealRankerAndComboEngine
import com.nutra.app.domain.usecase.MacroGoalBounds
import com.nutra.app.domain.usecase.NutritionCalculatorUseCase
import com.nutra.app.domain.usecase.WeightTrendCalculator
import com.nutra.app.feature.metabolism.metabolismChartIndexForX
import com.nutra.app.feature.profile.BoundedReadResult
import com.nutra.app.feature.profile.readBoundedUtf8Stream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.lang.reflect.Proxy
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.roundToInt

class CoreAppAndEdgeCasesRigorousTest {

    @Test
    fun nutritionModels_guardAgainstNaNInfinityAndNegativeInputsEverywhere() {
        val food = FoodItem(
            id = 1L,
            name = "Test Besin",
            subtitle = "Test",
            category = FoodCategory.CUSTOM,
            caloriesPer100g = 200,
            proteinPer100g = 20f,
            carbsPer100g = 15f,
            fatPer100g = 6f,
            defaultUnitLabel = "1 Porsiyon",
            defaultUnitGrams = 100f,
            alternateUnits = listOf(
                ServingUnit("1 Porsiyon", 100f), // duplicate default
                ServingUnit("100 Gram", 100f),
                ServingUnit("1 Kase", 180f),
            ),
            fiberPer100g = 4.5f,
        )

        // NaN, Infinity, negative grams never throw or return NaN
        for (bad in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -50f, 0f)) {
            assertEquals("caloriesForGrams($bad)", 0, food.caloriesForGrams(bad))
            assertEquals("proteinForGrams($bad)", 0f, food.proteinForGrams(bad), 0.001f)
            assertEquals("carbsForGrams($bad)", 0f, food.carbsForGrams(bad), 0.001f)
            assertEquals("fatForGrams($bad)", 0f, food.fatForGrams(bad), 0.001f)
            assertEquals("scaledNutrient($bad)", 0f, food.scaledNutrient(food.fiberPer100g, bad) ?: -1f, 0.001f)
        }
        assertNull(food.scaledNutrient(null, 100f))
        assertNull(food.scaledNutrient(Float.NaN, 100f))
        assertEquals(4.5f, food.scaledNutrient(food.fiberPer100g, 100f)!!, 0.01f)

        // FoodItem with NaN per-100g macros
        val nanFood = food.copy(proteinPer100g = Float.NaN, carbsPer100g = Float.POSITIVE_INFINITY, fatPer100g = -2f, caloriesPer100g = -50)
        assertEquals(0, nanFood.caloriesForGrams(150f))
        assertEquals(0f, nanFood.proteinForGrams(150f), 0.001f)
        assertEquals(0f, nanFood.carbsForGrams(150f), 0.001f)
        assertEquals(0f, nanFood.fatForGrams(150f), 0.001f)

        // allUnits deduplicates & appends 100g and raw unit cleanly
        val labels = food.allUnits.map { it.label }
        assertEquals(labels.size, labels.distinct().size)
        assertTrue(ServingUnitLabels.RAW_GRAM in labels)
        assertTrue(ServingUnitLabels.BASE_100G in labels)
        assertEquals("ml", ServingUnitLabels.normalizedBaseUnit("ML"))
        assertEquals("g", ServingUnitLabels.normalizedBaseUnit("unknown"))
        assertTrue(ServingUnitLabels.isRawUnitLabel(ServingUnitLabels.RAW_GRAM))
        assertTrue(ServingUnitLabels.isRawUnitLabel(ServingUnitLabels.RAW_ML))
        assertFalse(ServingUnitLabels.isRawUnitLabel("1 Porsiyon"))

        // MealLogItem.formattedQuantity handles NaN/Inf/negative gracefully without crashing
        val badLog = MealLogItem(
            id = 1L,
            dateIso = "2026-11-21",
            mealType = MealType.LUNCH,
            foodId = 1L,
            foodName = "Test",
            unitLabel = ServingUnitLabels.RAW_GRAM,
            multiplier = Float.NaN,
            totalGrams = Float.NaN,
            calories = 0,
            proteinGrams = 0f,
            carbsGrams = 0f,
            fatGrams = 0f,
            timestampMillis = 0L,
            baseUnit = "g",
        )
        assertEquals("0 g", badLog.formattedQuantity)
        assertEquals("1x 1 Porsiyon (0 ml)", badLog.copy(unitLabel = "1 Porsiyon", baseUnit = "ml").formattedQuantity)

        // DailyGoalImpactPreview guards 0 calorieGoal / proteinGoal
        val impact = DailyGoalImpactPreview(0, 500, 0, 0f, 30f, 0, 0f, 50f, 0, 0f, 15f, 0, "")
        assertTrue(impact.caloriePercentAfter >= 0)
        assertTrue(impact.proteinPercentAfter >= 0)
    }

    @Test
    fun allRoomEntities_roundTripCleanlyAndSurviveCorruptEnumsAndJson() {
        // FoodItemEntity with corrupt JSON, unknown category & mealType, blank normalizedSearchText
        val corruptFoodEntity = FoodItemEntity(
            id = 42L,
            name = "Şeftali Suyu",
            subtitle = "Doğal",
            normalizedSearchText = "",
            category = "UNKNOWN_CATEGORY",
            caloriesPer100g = 48,
            proteinPer100g = 0.5f,
            carbsPer100g = 11f,
            fatPer100g = 0.1f,
            defaultUnitLabel = "1 Bardak",
            defaultUnitGrams = 200f,
            alternateUnitsJson = "{corrupt-json",
            preferredMealTypesCsv = "BREAKFAST, BOGUS_MEAL, SNACK",
            baseUnit = "ML",
        )
        val foodDomain = corruptFoodEntity.toDomain()
        assertEquals(FoodCategory.HOME_COOKED, foodDomain.category)
        assertTrue(foodDomain.alternateUnits.isEmpty())
        assertEquals(setOf(MealType.BREAKFAST, MealType.SNACK), foodDomain.preferredMealTypes)
        assertEquals("ml", foodDomain.baseUnit)
        assertTrue(foodDomain.normalizedSearchText.contains("seftali"))

        // MealLogEntity round-trip + unknown mealType fallback
        val mealLog = MealLogItem(
            id = 7L,
            dateIso = "2026-11-21",
            mealType = MealType.DINNER,
            foodId = 42L,
            foodName = "Şeftali Suyu",
            unitLabel = "1 Bardak",
            multiplier = 1f,
            totalGrams = 200f,
            calories = 96,
            proteinGrams = 1f,
            carbsGrams = 22f,
            fatGrams = 0.2f,
            timestampMillis = 5555L,
            baseUnit = "ml",
        )
        assertEquals(mealLog, MealLogEntity.fromDomain(mealLog).toDomain())
        assertEquals(MealType.BREAKFAST, MealLogEntity.fromDomain(mealLog).copy(mealType = "BAD_MEAL").toDomain().mealType)

        // WeightLogEntity
        val weightEntity = WeightLogEntity(id = 3L, dateIso = "2026-11-21", weightKg = 74.2f, timestampMillis = 999L)
        assertEquals(WeightEntry(3L, "2026-11-21", 74.2f, 999L), weightEntity.toDomain())

        // GoalHistoryEntity round-trip + unknown enum fallbacks
        val goalHistory = GoalHistoryEntry(
            id = 9L,
            effectiveDateIso = "2026-11-21",
            calories = 2150,
            proteinGrams = 160,
            carbsGrams = 220,
            fatGrams = 68,
            goalType = GoalType.LOSE_WEIGHT,
            targetWeightKg = 72f,
            source = GoalChangeSource.ADAPTIVE_APPROVED,
            timestampMillis = 7777L,
        )
        assertEquals(goalHistory, GoalHistoryEntity.fromDomain(goalHistory).toDomain())
        val fallbackGoal = GoalHistoryEntity.fromDomain(goalHistory).copy(goalType = "X", source = "Y").toDomain()
        assertEquals(GoalType.MAINTAIN, fallbackGoal.goalType)
        assertEquals(GoalChangeSource.MANUAL, fallbackGoal.source)

        // FoodFeedbackEntity round-trip + unknown status fallback
        val fb = FoodFeedbackRecord(
            foodKey = "code:TR0186",
            foodId = 10L,
            foodName = "Tavuk",
            status = FoodFeedbackStatus.PINNED,
            acceptedCount = 4,
            dismissedCount = 1,
            lastSuggestedMillis = 100L,
            lastAcceptedMillis = 200L,
            updatedAtMillis = 300L,
        )
        assertEquals(fb, FoodFeedbackEntity.fromDomain(fb).toDomain())
        assertEquals(
            FoodFeedbackStatus.NEUTRAL,
            FoodFeedbackEntity.fromDomain(fb).copy(status = "INVALID").toDomain().status,
        )

        // MetabolismSnapshotEntity round-trip
        val snap = MetabolismSnapshotRecord(
            dateIso = "2026-11-21",
            estimatedTdeeKcal = 2580,
            uncertaintyKcal = 135,
            priorTdeeKcal = 2700,
            trendWeightKg = 77.6f,
            confidenceScore = 82,
            qualityLevel = "HIGH",
            updatedAtMillis = 123456L,
        )
        assertEquals(snap, MetabolismSnapshotEntity.fromDomain(snap).toDomain())
    }

    @Test
    fun nutraFormatters_turkishLocaleHugeValuesCacheEvictionAndLeapYearSpan() {
        assertEquals("istanbul ığdır çöşüğ", NutraFormatters.lowercaseTr("İSTANBUL IĞDIR ÇÖŞÜĞ"))
        assertEquals("İstanbul", NutraFormatters.capitalizeTr("istanbul"))
        assertEquals("Iğdır", NutraFormatters.capitalizeTr("ığdır"))

        // Huge numbers and negative zero
        assertEquals("0,0", NutraFormatters.formatKg(-0.0f))
        assertTrue(NutraFormatters.formatKg(2_500_000f).isNotBlank())
        assertTrue(NutraFormatters.formatMacroGrams(2_500_000f).isNotBlank())
        assertTrue(NutraFormatters.formatMultiplier(250_000.5f).isNotBlank())
        assertEquals("0", NutraFormatters.formatMultiplier(-0.0f))

        // Date cache overflow (> 128 entries) evicts and stays accurate
        val base = LocalDate.of(2026, 1, 1)
        for (i in 0..145) {
            val iso = base.plusDays(i.toLong()).toString()
            assertFalse(NutraFormatters.formatShortDate(iso, todayIso = "2027-01-01").isBlank())
            assertFalse(NutraFormatters.formatShortWeekday(iso).isBlank())
        }
        assertEquals("invalid-date", NutraFormatters.formatShortDate("invalid-date", todayIso = "2026-11-21"))
        assertEquals("", NutraFormatters.formatShortDate(null, todayIso = "2026-11-21"))
        assertEquals("", NutraFormatters.formatShortWeekday("invalid-date"))

        // Leap year February span (2024-02-26 Mon -> 2024-03-03 Sun crosses Feb 29)
        assertEquals("Şubat – Mart 2024", NutraFormatters.formatMonthSpan(LocalDate.of(2024, 2, 26)))
    }

    @Test
    fun calculatorAndWeightTrend_handleNaNInfinityAndExtremeInputsSafely() {
        val calc = NutritionCalculatorUseCase()
        val nanGoals = calc.calculateGoals(
            sex = BiologicalSex.FEMALE,
            age = -10,
            heightCm = 999,
            weightKg = Float.NaN,
            activityLevel = ActivityLevel.ACTIVE,
            goalType = GoalType.LOSE_WEIGHT,
        )
        assertTrue(nanGoals.calories in 1000..6000)
        assertTrue(nanGoals.proteinGrams >= 30)
        assertTrue(nanGoals.waterMl in 1500..4500)

        // Zero-macro baseline scaling falls back cleanly to default macro ratios
        val zeroBase = MacroGoals(calories = 0, proteinGrams = 0, carbsGrams = 0, fatGrams = 0, waterMl = 2500)
        val scaled = calc.scaleMacrosForCalorieGoal(zeroBase, 2000, MacroGoalBounds())
        assertEquals(2000, scaled.calories)
        assertTrue(scaled.proteinGrams >= 30 && scaled.carbsGrams >= 20 && scaled.fatGrams >= 20)

        // WeightTrendCalculator filters NaN/Inf/negative/blank and clamps extreme alpha
        val trendCalc = WeightTrendCalculator()
        val messy = listOf(
            WeightEntry(1L, "2026-11-10", 80.0f, 100L),
            WeightEntry(2L, "2026-11-11", Float.NaN, 200L),
            WeightEntry(3L, "2026-11-12", Float.POSITIVE_INFINITY, 300L),
            WeightEntry(4L, "2026-11-13", -5f, 400L),
            WeightEntry(5L, "   ", 79.0f, 500L),
            WeightEntry(6L, "2026-11-14", 79.0f, 600L),
        )
        val trendNanAlpha = trendCalc.calculateTrend(messy, smoothingFactor = Float.NaN)
        assertEquals(2, trendNanAlpha.size)
        assertEquals(80.0f, trendNanAlpha[0].emaTrendKg, 0.01f)
        // 80.0 * 0.75 + 79.0 * 0.25 = 79.75 -> rounded to 1 decimal place = 79.8
        assertEquals(79.8f, trendNanAlpha[1].emaTrendKg, 0.01f)

        val trendAlphaOne = trendCalc.calculateTrend(messy, smoothingFactor = 5.0f)
        assertEquals(79.0f, trendAlphaOne[1].emaTrendKg, 0.01f)
    }

    @Test
    fun contextualComboEngine_preservesMlBaseUnitAndUpdatesLatestTimestampOnRepeatedBaskets() {
        val engine = ContextualMealRankerAndComboEngine()
        val catalog = TurkishFoodSeedCatalog.initialFoods.mapIndexed { idx, f -> f.copy(id = idx + 1L) }
        val milk = catalog.first { it.catalogCode == "TR0447" } // Liquid (ml)
        val oat = catalog.first { it.name.contains("Yulaf", ignoreCase = true) }
        val egg = catalog.first { it.catalogCode == "TR0460" }
        val cheese = catalog.first { it.name.contains("Ezine", ignoreCase = true) }

        // Basket A (egg + cheese): appears on day 1 & day 2 (older: ts 1000 & 2000)
        // Basket B (milk [ml] + oat): appears on day 1 & day 5 (fresher: ts 1500 & 9000)
        val logs = listOf(
            MealLogItem(1L, "2026-11-01", MealType.BREAKFAST, egg.id, egg.name, "1 Adet", 2f, 100f, 155, 12.6f, 1.1f, 10.6f, 1000L, "g"),
            MealLogItem(2L, "2026-11-01", MealType.BREAKFAST, cheese.id, cheese.name, "1 Dilim", 1f, 30f, 87, 5.2f, 0.5f, 7f, 1001L, "g"),
            MealLogItem(3L, "2026-11-02", MealType.BREAKFAST, egg.id, egg.name, "1 Adet", 2f, 100f, 155, 12.6f, 1.1f, 10.6f, 2000L, "g"),
            MealLogItem(4L, "2026-11-02", MealType.BREAKFAST, cheese.id, cheese.name, "1 Dilim", 1f, 30f, 87, 5.2f, 0.5f, 7f, 2001L, "g"),
            MealLogItem(5L, "2026-11-01", MealType.BREAKFAST, milk.id, milk.name, "1 Su Bardağı", 1f, 200f, 94, 6.4f, 9.6f, 3.2f, 1500L, "ml"),
            MealLogItem(6L, "2026-11-01", MealType.BREAKFAST, oat.id, oat.name, "1 Kase", 1f, 50f, 190, 6.5f, 33f, 3.5f, 1501L, "g"),
            MealLogItem(7L, "2026-11-05", MealType.BREAKFAST, milk.id, milk.name, "1 Su Bardağı", 1f, 200f, 94, 6.4f, 9.6f, 3.2f, 9000L, "ml"),
            MealLogItem(8L, "2026-11-05", MealType.BREAKFAST, oat.id, oat.name, "1 Kase", 1f, 50f, 190, 6.5f, 33f, 3.5f, 9001L, "g"),
        )

        val combos = engine.buildSmartComboSuggestions(
            mealType = MealType.BREAKFAST,
            currentDateMealItems = emptyList(),
            historicalLogs = logs,
            catalogFoods = catalog,
        )
        assertTrue(combos.size >= 2)
        // Both have coOccurrenceCount == 2; milk+oat has latestTimestamp 9001 > 2001 so it MUST rank first!
        val firstCombo = combos.first()
        val milkEntry = firstCombo.items.firstOrNull { it.foodId == milk.id }
        assertNotNull("Expected fresher milk+oat basket to rank first", milkEntry)
        assertEquals("Liquid item in historical combo must preserve baseUnit='ml'", "ml", milkEntry!!.baseUnit)
    }

    @Test
    fun backupSanitizationAndDietaryKeywordCap_remapCustomFeedbackAndValidateGoalHistory() {
        // 1. DietaryProfile sanitization: trims, filters <2 chars, deduplicates via Turkish normalization, caps at 40
        val rawKeywords = listOf("  ", "a", "Şeker", "seker", "ÇİLEK", "cilek") + (1..50).map { "kelime$it" }
        val sanitized = sanitizeDietaryProfileData(DietaryProfile(excludedKeywords = rawKeywords))
        assertEquals(MAX_EXCLUDED_KEYWORDS, sanitized.excludedKeywords.size)
        assertEquals("Şeker", sanitized.excludedKeywords[0])
        assertEquals("ÇİLEK", sanitized.excludedKeywords[1])
        assertTrue(sanitized.excludedKeywords.none { it == "seker" || it == "cilek" || it == "a" })

        // 2. validateBackupPayload rejects corrupt goalHistory
        val basePayload = NutraBackupPayload(
            version = 1,
            exportedAtDateIso = "2026-11-21",
            userProfile = UserProfile(),
            customFoods = emptyList(),
            mealLogs = emptyList(),
            waterLogs = emptyMap(),
            weightEntries = emptyList(),
        )
        assertNull(validateBackupPayload(basePayload))
        assertNotNull(
            validateBackupPayload(
                basePayload.copy(
                    goalHistory = listOf(
                        GoalHistoryEntry(1L, "not-a-date", 2000, 140, 200, 65, GoalType.MAINTAIN, 75f, GoalChangeSource.MANUAL, 1L),
                    ),
                ),
            ),
        )
        assertNotNull(
            validateBackupPayload(
                basePayload.copy(
                    goalHistory = listOf(
                        GoalHistoryEntry(1L, "2026-11-21", 0, 140, 200, 65, GoalType.MAINTAIN, 75f, GoalChangeSource.MANUAL, 1L),
                    ),
                ),
            ),
        )

        // 3. sanitizeImportedGoalHistory clamps NaN/extremes and resets id = 0
        val cleanedGoal = sanitizeImportedGoalHistory(
            GoalHistoryEntry(
                id = 99L,
                effectiveDateIso = "2026-11-21",
                calories = 200,
                proteinGrams = 999,
                carbsGrams = -5,
                fatGrams = 500,
                goalType = GoalType.LOSE_WEIGHT,
                targetWeightKg = Float.NaN,
                source = GoalChangeSource.MANUAL,
                timestampMillis = -100L,
            ),
        )
        assertEquals(0L, cleanedGoal.id)
        assertEquals(1000, cleanedGoal.calories)
        assertEquals(400, cleanedGoal.proteinGrams)
        assertEquals(20, cleanedGoal.carbsGrams)
        assertEquals(250, cleanedGoal.fatGrams)
        assertEquals(70f, cleanedGoal.targetWeightKg, 0.01f)
        assertEquals(0L, cleanedGoal.timestampMillis)

        // 4. remapAndSanitizeFoodFeedback remaps custom foodId AND custom:<id>:<norm> key while preserving code: keys
        val remappedCustom = remapAndSanitizeFoodFeedback(
            record = FoodFeedbackRecord(
                foodKey = "custom:501:ev yapimi yulaf",
                foodId = 501L,
                foodName = " Ev Yapımı Yulaf ",
                status = FoodFeedbackStatus.DISLIKED,
                acceptedCount = -2,
                dismissedCount = -1,
                lastSuggestedMillis = -5L,
            ),
            importedFoodIdMap = mapOf(501L to 1205L),
            nameToTargetId = emptyMap(),
        )
        assertEquals(1205L, remappedCustom.foodId)
        assertEquals("custom:1205:ev yapimi yulaf", remappedCustom.foodKey)
        assertEquals("Ev Yapımı Yulaf", remappedCustom.foodName)
        assertEquals(0, remappedCustom.acceptedCount)
        assertEquals(0, remappedCustom.dismissedCount)
        assertEquals(0L, remappedCustom.lastSuggestedMillis)

        val remappedCatalog = remapAndSanitizeFoodFeedback(
            record = FoodFeedbackRecord(
                foodKey = "code:TR0186",
                foodId = null,
                foodName = "Tavuk Göğsü",
                status = FoodFeedbackStatus.PINNED,
            ),
            importedFoodIdMap = emptyMap(),
            nameToTargetId = mapOf("tavuk gogsu" to 88L),
        )
        assertEquals("code:TR0186", remappedCatalog.foodKey)
        assertEquals(88L, remappedCatalog.foodId)
    }

    @Test
    fun readBoundedUtf8Stream_handlesExactByteBoundariesAndMultiByteTurkishCharacters() {
        val exactText = "ÖĞÜN ŞİFRE ÇÜĞ".repeat(10)
        val exactBytes = exactText.toByteArray(Charsets.UTF_8)

        // Exact limit == maxBytes succeeds
        val ok = readBoundedUtf8Stream(ByteArrayInputStream(exactBytes), maxBytes = exactBytes.size)
        assertTrue(ok is BoundedReadResult.Success)
        assertEquals(exactText, (ok as BoundedReadResult.Success).text)

        // maxBytes - 1 returns TooLarge
        assertEquals(
            BoundedReadResult.TooLarge,
            readBoundedUtf8Stream(ByteArrayInputStream(exactBytes), maxBytes = exactBytes.size - 1),
        )

        // Empty stream returns EmptyOrFailed
        assertEquals(
            BoundedReadResult.EmptyOrFailed,
            readBoundedUtf8Stream(ByteArrayInputStream(ByteArray(0)), maxBytes = 1024),
        )
    }

    @Test
    fun metabolismChartIndexForX_matchesCanvasXForPixelAccurately() {
        val widthPx = 1000
        val padX = 30f
        val count = 17
        val usable = widthPx - padX * 2f

        for (i in 0 until count) {
            val canvasX = padX + usable * (i.toFloat() / (count - 1))
            assertEquals("Exact inverse mismatch at index $i", i, metabolismChartIndexForX(canvasX, widthPx, padX, count))
        }
        // Out-of-bounds clamping
        assertEquals(0, metabolismChartIndexForX(-50f, widthPx, padX, count))
        assertEquals(0, metabolismChartIndexForX(padX - 5f, widthPx, padX, count))
        assertEquals(count - 1, metabolismChartIndexForX(widthPx + 50f, widthPx, padX, count))
        assertEquals(0, metabolismChartIndexForX(500f, widthPx, padX, pointCount = 1))
        assertEquals(0, metabolismChartIndexForX(500f, widthPx, padX, pointCount = 0))
    }

    @Test
    fun waterReminderCalculator_handlesExtremeInputsAndLeapYearRollover() {
        val extreme = WaterReminderScheduleCalculator.sanitize(
            WaterReminderSettings(
                enabled = true,
                intervalMinutes = -99,
                startHour = 25,
                startMinute = 99,
                endHour = -5,
                endMinute = -10,
            ),
        )
        assertEquals(WaterReminderBounds.MIN_INTERVAL_MINUTES, extreme.intervalMinutes)
        assertTrue(extreme.startHour in 0..23 && extreme.startMinute in 0..59)
        assertTrue(extreme.endHour in 0..23 && extreme.endMinute in 0..59)
        assertTrue((extreme.endHour * 60 + extreme.endMinute) - (extreme.startHour * 60 + extreme.startMinute) >= WaterReminderBounds.MIN_WINDOW_SPAN_MINUTES)

        // Leap year Feb 28 22:30 -> Feb 29 09:00!
        val settings = WaterReminderSettings(enabled = true, intervalMinutes = 120, startHour = 9, startMinute = 0, endHour = 21, endMinute = 0)
        val next = WaterReminderScheduleCalculator.computeNextTriggerDateTime(
            now = LocalDateTime.of(2024, 2, 28, 22, 30),
            settings = settings,
        )
        assertEquals(LocalDateTime.of(2024, 2, 29, 9, 0), next)

        assertEquals("00:00", WaterReminderScheduleCalculator.formatClockFromMinutes(-100))
        assertEquals("23:59", WaterReminderScheduleCalculator.formatClockFromMinutes(9999))
        assertTrue(WaterReminderScheduleCalculator.isWithinActiveWindow(LocalTime.of(9, 0), settings))
        assertTrue(WaterReminderScheduleCalculator.isWithinActiveWindow(LocalTime.of(21, 10), settings))
        assertFalse(WaterReminderScheduleCalculator.isWithinActiveWindow(LocalTime.of(21, 11), settings))
    }

    @Test
    fun roomMigrationsChain_isContiguousFromV1ToV5AndExecutesAllExpectedSchemaStatements() {
        val migrations = listOf(
            AppModule.MIGRATION_1_2,
            AppModule.MIGRATION_2_3,
            AppModule.MIGRATION_3_4,
            AppModule.MIGRATION_4_5,
        )
        assertEquals(1, migrations.first().startVersion)
        assertEquals(5, migrations.last().endVersion)
        for (i in 0 until migrations.lastIndex) {
            assertEquals(migrations[i].endVersion, migrations[i + 1].startVersion)
        }

        val executedSql = mutableListOf<String>()
        val recordingDb = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedSql.add(args[0].toString())
            }
            when (method.returnType) {
                Boolean::class.javaPrimitiveType -> false
                Int::class.javaPrimitiveType -> 0
                Long::class.javaPrimitiveType -> 0L
                else -> null
            }
        } as SupportSQLiteDatabase

        migrations.forEach { it.migrate(recordingDb) }
        val allSql = executedSql.joinToString("\n")

        // Verify v4 columns & indices
        assertTrue(allSql.contains("catalogCode"))
        assertTrue(allSql.contains("baseUnit"))
        assertTrue(allSql.contains("index_food_items_catalogCode"))

        // Verify v5 tables & indices match Entities.kt exactly
        assertTrue(allSql.contains("CREATE TABLE IF NOT EXISTS `goal_history`"))
        assertTrue(allSql.contains("index_goal_history_effectiveDateIso_timestampMillis"))
        assertTrue(allSql.contains("CREATE TABLE IF NOT EXISTS `food_feedback`"))
        assertTrue(allSql.contains("index_food_feedback_status"))
        assertTrue(allSql.contains("index_food_feedback_foodId"))
        assertTrue(allSql.contains("CREATE TABLE IF NOT EXISTS `metabolism_snapshots`"))
    }
}
