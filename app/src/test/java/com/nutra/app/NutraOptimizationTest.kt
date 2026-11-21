package com.nutra.app

import com.nutra.app.core.database.FoodItemEntity
import com.nutra.app.core.designsystem.NutraFormatters
import com.nutra.app.domain.model.ComboFoodEntry
import com.nutra.app.domain.model.DailyAnalyticsPoint
import com.nutra.app.domain.model.DailyNutritionSummary
import com.nutra.app.domain.model.FoodCategory
import com.nutra.app.domain.model.FoodItem
import com.nutra.app.domain.model.MacroGoals
import com.nutra.app.domain.model.MealLogItem
import com.nutra.app.domain.model.MealSummary
import com.nutra.app.domain.model.MealType
import com.nutra.app.domain.model.ServingUnit
import com.nutra.app.domain.model.SmartComboSuggestion
import com.nutra.app.domain.model.WeightTrendPoint
import com.nutra.app.domain.usecase.ContextualMealRankerAndComboEngine
import com.nutra.app.domain.usecase.TurkishTextNormalizer
import com.nutra.app.feature.analytics.AnalyticsRangeOption
import com.nutra.app.feature.analytics.AnalyticsUiState
import com.nutra.app.feature.analytics.filterWeightTrendForRange
import com.nutra.app.feature.analytics.summarizeAnalyticsRange
import com.nutra.app.feature.dashboard.DashboardUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
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

    @Test
    fun precomputedNutritionSummariesAndSmartComboTotalsAreAccurate() {
        val log1 = MealLogItem(1L, "2026-11-19", MealType.BREAKFAST, 1L, "Yumurta", "1 Adet", 2f, 100f, 155, 12.6f, 1.1f, 10.6f, 100L)
        val log2 = MealLogItem(2L, "2026-11-19", MealType.BREAKFAST, 2L, "Peynir", "1 Dilim", 1f, 30f, 87, 5.2f, 0.5f, 7.0f, 200L)
        val breakfast = MealSummary(MealType.BREAKFAST, listOf(log1, log2))
        val lunch = MealSummary(
            MealType.LUNCH,
            listOf(MealLogItem(3L, "2026-11-19", MealType.LUNCH, 3L, "Tavuk", "1 Gram", 150f, 150f, 248, 46.5f, 0f, 5.4f, 300L)),
        )
        val daily = DailyNutritionSummary("2026-11-19", MacroGoals(), listOf(breakfast, lunch), 1200)

        assertEquals(242, breakfast.totalCalories)
        assertEquals(17.8f, breakfast.totalProtein, 0.01f)
        assertEquals(490, daily.consumedCalories)
        assertEquals(64.3f, daily.consumedProtein, 0.01f)
        assertEquals(1.6f, daily.consumedCarbs, 0.01f)
        assertEquals(23.0f, daily.consumedFat, 0.01f)

        val combo = SmartComboSuggestion(
            id = "c1",
            title = "Kahvaltı",
            subtitle = "Sık",
            items = listOf(
                ComboFoodEntry(1L, "Yumurta", "1 Adet", 1f, 50f, 78, 6.3f, 0.6f, 5.3f),
                ComboFoodEntry(2L, "Peynir", "1 Dilim", 1f, 30f, 87, 5.2f, 0.5f, 7.0f),
            ),
            coOccurrenceCount = 3,
        )
        assertEquals(165, combo.totalCalories)
        assertEquals("Yumurta · Peynir", combo.itemsPreview)
    }

    @Test
    fun uiStates_haveValidDefaultsForWeekStartAndEmptyAnalytics() {
        val dashboard = DashboardUiState()
        assertEquals(DayOfWeek.MONDAY, dashboard.weekStart.dayOfWeek)

        val analytics = AnalyticsUiState()
        assertNull(analytics.activeDayPoint)
        assertNull(analytics.activeWaterDayPoint)
    }

    @Test
    fun analyticsRangeSummary_averagesLoggedDaysForCaloriesWaterAndMacros() {
        val points = listOf(
            DailyAnalyticsPoint("2026-11-17", calories = 2000, targetCalories = 2200, proteinGrams = 150f, carbsGrams = 200f, fatGrams = 60f, waterMl = 2400),
            DailyAnalyticsPoint("2026-11-18", calories = 0, targetCalories = 2200, proteinGrams = 0f, carbsGrams = 0f, fatGrams = 0f, waterMl = 0),
            DailyAnalyticsPoint("2026-11-19", calories = 2301, targetCalories = 2200, proteinGrams = 170f, carbsGrams = 240f, fatGrams = 80f, waterMl = 2601),
        )

        val summary = summarizeAnalyticsRange(points)

        assertEquals(2151, summary.averageCalories)
        assertEquals(2501, summary.averageWaterMl)
        assertEquals(160f, summary.averageProteinGrams, 0.01f)
        assertEquals(220f, summary.averageCarbsGrams, 0.01f)
        assertEquals(70f, summary.averageFatGrams, 0.01f)
    }

    @Test
    fun filterWeightTrendForRange_scopesPointsToSelectedDaysWhileKeepingLatestRecordedWeight() {
        val endDate = LocalDate.of(2026, 11, 19)
        val allPoints = listOf(
            WeightTrendPoint("2026-10-25", rawWeightKg = 80.0f, emaTrendKg = 80.0f),
            WeightTrendPoint("2026-11-08", rawWeightKg = 79.2f, emaTrendKg = 79.8f),
            WeightTrendPoint("2026-11-14", rawWeightKg = 78.8f, emaTrendKg = 79.5f),
            WeightTrendPoint("2026-11-19", rawWeightKg = 78.4f, emaTrendKg = 79.2f),
        )

        val last7 = filterWeightTrendForRange(allPoints, AnalyticsRangeOption.DAYS_7, endDate)
        assertEquals(listOf("2026-11-14", "2026-11-19"), last7.pointsInRange.map { it.dateIso })
        assertEquals(78.4f, last7.latestRecordedWeightKg!!, 0.001f)
        assertTrue(last7.hasAnyHistory)

        val last14 = filterWeightTrendForRange(allPoints, AnalyticsRangeOption.DAYS_14, endDate)
        assertEquals(listOf("2026-11-08", "2026-11-14", "2026-11-19"), last14.pointsInRange.map { it.dateIso })

        val last30 = filterWeightTrendForRange(allPoints, AnalyticsRangeOption.DAYS_30, endDate)
        assertEquals(4, last30.pointsInRange.size)
    }
}
