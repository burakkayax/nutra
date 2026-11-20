package com.nutra.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import com.nutra.app.core.database.TurkishFoodSeedCatalog
import com.nutra.app.core.designsystem.NutraFormatters
import com.nutra.app.core.designsystem.createModalSheetNestedScrollGuard
import com.nutra.app.core.network.GeminiAiService
import com.nutra.app.domain.model.FoodCategory
import com.nutra.app.domain.model.FoodItem
import com.nutra.app.domain.model.MacroGoals
import com.nutra.app.domain.model.MealLogItem
import com.nutra.app.domain.model.MealType
import com.nutra.app.domain.model.ThemePreference
import com.nutra.app.domain.model.UserProfile
import com.nutra.app.domain.model.WeightEntry
import com.nutra.app.domain.repository.NutraBackupPayload
import com.nutra.app.domain.usecase.ContextualMealRankerAndComboEngine
import com.nutra.app.domain.usecase.MacroGoalBounds
import com.nutra.app.domain.usecase.NutritionCalculatorUseCase
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NutraAuditFixesTest {

    @Test
    fun nutraFormatters_handlesNegativeZeroNonFiniteAndTurkishDecimals() {
        assertEquals("0,0", NutraFormatters.formatKg(-0.04f))
        assertEquals("0,0", NutraFormatters.formatKg(Float.NaN))
        assertEquals("72,5", NutraFormatters.formatKg(72.5f))

        assertEquals("0", NutraFormatters.formatMacroGrams(-0.04f))
        assertEquals("0", NutraFormatters.formatMacroGrams(Float.NaN))
        assertEquals("12", NutraFormatters.formatMacroGrams(12.0f))
        assertEquals("12,4", NutraFormatters.formatMacroGrams(12.4f))

        assertEquals("1", NutraFormatters.formatMultiplier(1.0f))
        assertEquals("1,5", NutraFormatters.formatMultiplier(1.5f))
        assertEquals("1,25", NutraFormatters.formatMultiplier(1.25f))

        assertEquals("72,59", NutraFormatters.sanitizeNumericInput("72,5.9abc", allowDecimal = true))
        assertEquals("72,5", NutraFormatters.sanitizeNumericInput("72,5abc", allowDecimal = true))
        assertEquals("7259", NutraFormatters.sanitizeNumericInput("72,5.9abc", allowDecimal = false))
        assertEquals(72.5f, NutraFormatters.parseLocalizedFloat("72,5")!!, 0.001f)
        assertNull(NutraFormatters.parseLocalizedFloat("abc"))

        assertEquals("İlerleme", NutraFormatters.capitalizeTr("ilerleme"))
        assertEquals("Kasım 2026", NutraFormatters.formatMonthSpan(java.time.LocalDate.of(2026, 11, 16)))
        assertEquals("Ekim – Kasım 2026", NutraFormatters.formatMonthSpan(java.time.LocalDate.of(2026, 10, 26)))
        assertEquals("Aralık 2026 – Ocak 2027", NutraFormatters.formatMonthSpan(java.time.LocalDate.of(2026, 12, 28)))
    }

    @Test
    fun mealLogItem_formattedQuantityPreservesQuarterMultipliersAndRawGrams() {
        val rawGramLog = MealLogItem(
            id = 1L,
            dateIso = "2026-11-16",
            mealType = MealType.LUNCH,
            foodId = 10L,
            foodName = "Izgara Tavuk Göğsü",
            unitLabel = "1 Gram",
            multiplier = 250f,
            totalGrams = 250f,
            calories = 413,
            proteinGrams = 77.5f,
            carbsGrams = 0f,
            fatGrams = 9f,
            timestampMillis = 1000L,
        )
        assertEquals("250 g", rawGramLog.formattedQuantity)

        val quarterLog = rawGramLog.copy(
            unitLabel = "1 Dilim",
            multiplier = 1.25f,
            totalGrams = 37.5f,
        )
        assertEquals("1,25x 1 Dilim (38 g)", quarterLog.formattedQuantity)
    }

    @Test
    fun onDeviceNluParser_preservesTurkishDecimalCommaAndCaseInsensitiveConnectors() {
        val aiService = GeminiAiService(OkHttpClient())
        val catalog = TurkishFoodSeedCatalog.initialFoods

        val bulgur = catalog.first { it.name.equals("Bulgur Pilavı", ignoreCase = true) }
        val decimalItems = aiService.parseWithOnDeviceNlu(
            text = "1,5 porsiyon bulgur pilavı",
            catalogFoods = catalog,
        )
        assertEquals(1, decimalItems.size)
        assertTrue(decimalItems.first().name.contains("Bulgur"))
        assertEquals(bulgur.defaultUnitGrams * 1.5f, decimalItems.first().baseGrams, 0.5f)

        val connectedItems = aiService.parseWithOnDeviceNlu(
            text = "2 haşlanmış yumurta VE 150g ızgara tavuk göğsü artı 1 muz",
            catalogFoods = catalog,
        )
        assertEquals(3, connectedItems.size)
        assertTrue(connectedItems[0].name.contains("Yumurta"))
        assertEquals(100f, connectedItems[0].baseGrams, 0.1f)
        assertTrue(connectedItems[1].name.contains("Tavuk"))
        assertEquals(150f, connectedItems[1].baseGrams, 0.1f)
        assertTrue(connectedItems[2].name.contains("Muz"))
    }

    @Test
    fun contextualCombos_produceValidSuggestionsAcrossAllMeals() {
        val engine = ContextualMealRankerAndComboEngine()
        val catalog = TurkishFoodSeedCatalog.initialFoods.mapIndexed { idx, item ->
            item.copy(id = idx + 1L)
        }

        for (meal in MealType.entries) {
            val combos = engine.buildSmartComboSuggestions(
                mealType = meal,
                currentDateMealItems = emptyList(),
                historicalLogs = emptyList(),
                catalogFoods = catalog,
            )
            assertTrue("Expected curated combos for $meal", combos.isNotEmpty())
            assertTrue(combos.all { it.items.size >= 2 })
        }
    }

    @Test
    fun backupPayload_roundTripPreservesFullProfileAndEntries() {
        val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
        val payload = NutraBackupPayload(
            version = 1,
            exportedAtDateIso = "2026-11-16",
            userProfile = UserProfile(
                themePreference = ThemePreference.DARK,
                useDynamicColor = true,
                geminiApiKey = "test-key",
                geminiModel = "gemini-2.5-flash",
                onboardingCompleted = true,
                goals = MacroGoals(calories = 2200, proteinGrams = 160, carbsGrams = 220, fatGrams = 75, waterMl = 2800),
            ),
            customFoods = listOf(
                FoodItem(
                    id = 99L,
                    name = "Özel Yemek",
                    subtitle = "Ev yapımı",
                    category = FoodCategory.CUSTOM,
                    caloriesPer100g = 180,
                    proteinPer100g = 15f,
                    carbsPer100g = 18f,
                    fatPer100g = 5f,
                    defaultUnitLabel = "1 Porsiyon",
                    defaultUnitGrams = 200f,
                    isCustom = true,
                    isFavorite = true,
                ),
            ),
            mealLogs = listOf(
                MealLogItem(
                    id = 5L,
                    dateIso = "2026-11-16",
                    mealType = MealType.DINNER,
                    foodId = 99L,
                    foodName = "Özel Yemek",
                    unitLabel = "1 Gram",
                    multiplier = 250f,
                    totalGrams = 250f,
                    calories = 450,
                    proteinGrams = 37.5f,
                    carbsGrams = 45f,
                    fatGrams = 12.5f,
                    timestampMillis = 123456L,
                ),
            ),
            waterLogs = mapOf("2026-11-16" to 2100),
            weightEntries = listOf(WeightEntry(id = 1L, dateIso = "2026-11-16", weightKg = 74.5f, timestampMillis = 123456L)),
        )

        val encoded = json.encodeToString(payload)
        val decoded = json.decodeFromString<NutraBackupPayload>(encoded)

        assertEquals(payload.userProfile, decoded.userProfile)
        assertEquals(payload.customFoods.first().name, decoded.customFoods.first().name)
        assertEquals(payload.mealLogs, decoded.mealLogs)
        assertEquals(payload.waterLogs, decoded.waterLogs)
        assertEquals(payload.weightEntries, decoded.weightEntries)
    }

    @Test
    fun nutritionCalculator_scaleMacrosStaysWithinBoundsAtExtremes() {
        val useCase = NutritionCalculatorUseCase()
        val bounds = MacroGoalBounds()
        val baseline = MacroGoals(calories = 1100, proteinGrams = 30, carbsGrams = 20, fatGrams = 100, waterMl = 2500)

        val minScaled = useCase.scaleMacrosForCalorieGoal(baseline, 1000, bounds)
        assertEquals(1000, minScaled.calories)
        assertTrue(minScaled.proteinGrams in bounds.minProtein..bounds.maxProtein)
        assertTrue(minScaled.carbsGrams in bounds.minCarbs..bounds.maxCarbs)
        assertTrue(minScaled.fatGrams in bounds.minFat..bounds.maxFat)
    }

    @Test
    fun modalSheetNestedScrollGuard_absorbsUpwardOverflowAndPreservesTopDismiss() = runTest {
        var expanded = true
        val guard = createModalSheetNestedScrollGuard(isExpanded = { expanded })

        // Upward scroll/fling overflow when expanded is absorbed to prevent M3 settle oscillation
        assertEquals(
            Offset(0f, -120f),
            guard.onPostScroll(
                consumed = Offset(0f, -40f),
                available = Offset(0f, -120f),
                source = NestedScrollSource.UserInput,
            ),
        )
        assertEquals(
            Velocity(0f, -4800f),
            guard.onPostFling(
                consumed = Velocity(0f, -1200f),
                available = Velocity(0f, -4800f),
            ),
        )

        // Downward fling overflow from inner scroll hitting top is absorbed
        assertEquals(
            Offset(0f, 90f),
            guard.onPostScroll(
                consumed = Offset(0f, 200f),
                available = Offset(0f, 90f),
                source = NestedScrollSource.SideEffect,
            ),
        )
        assertEquals(
            Velocity(0f, 2600f),
            guard.onPostFling(
                consumed = Velocity(0f, 1400f),
                available = Velocity(0f, 2600f),
            ),
        )

        // Intentional pull-down and dismiss fling from top of list (consumed == 0) pass through to sheet
        assertEquals(
            Offset.Zero,
            guard.onPostScroll(
                consumed = Offset.Zero,
                available = Offset(0f, 80f),
                source = NestedScrollSource.UserInput,
            ),
        )
        assertEquals(
            Velocity.Zero,
            guard.onPostFling(
                consumed = Velocity.Zero,
                available = Velocity(0f, 3200f),
            ),
        )

        // Upward drag while sheet is not expanded passes through so user can pull sheet back up
        expanded = false
        assertEquals(
            Offset.Zero,
            guard.onPostScroll(
                consumed = Offset.Zero,
                available = Offset(0f, -60f),
                source = NestedScrollSource.UserInput,
            ),
        )
    }
}
