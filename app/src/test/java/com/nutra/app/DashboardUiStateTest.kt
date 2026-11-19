package com.nutra.app

import com.nutra.app.domain.model.ComboFoodEntry
import com.nutra.app.domain.model.FoodCategory
import com.nutra.app.domain.model.SmartComboSuggestion
import com.nutra.app.feature.dashboard.DashboardUiState
import com.nutra.app.feature.dashboard.LoggingSheetUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardUiStateTest {

    @Test
    fun defaultDashboardUiState_startsLoadingWithCollapsedCalendarAndMeals() {
        val state = DashboardUiState()
        assertTrue(state.isLoading)
        assertFalse(state.isWeekCalendarVisible)
        assertEquals(0, state.dailySummary.consumedCalories)
        assertTrue(state.expandedMeals.isEmpty())
    }

    @Test
    fun loggingSheetUiState_shouldShowSmartCombosOnlyInUnfilteredAllCategoryWithSuggestions() {
        val sampleCombo = SmartComboSuggestion(
            id = "combo_1",
            title = "Klasik Kahvaltı",
            subtitle = "Sık tüketilen",
            items = listOf(
                ComboFoodEntry(
                    foodId = 1L,
                    foodName = "Haşlanmış Yumurta",
                    unitLabel = "1 Adet",
                    multiplier = 1f,
                    totalGrams = 50f,
                    calories = 78,
                    proteinGrams = 6.3f,
                    carbsGrams = 0.6f,
                    fatGrams = 5.3f,
                ),
            ),
            coOccurrenceCount = 2,
        )

        assertFalse(LoggingSheetUiState().shouldShowSmartCombos)
        assertTrue(LoggingSheetUiState(smartCombos = listOf(sampleCombo)).shouldShowSmartCombos)
        assertFalse(
            LoggingSheetUiState(
                isSearchActive = true,
                smartCombos = listOf(sampleCombo),
            ).shouldShowSmartCombos,
        )
        assertFalse(
            LoggingSheetUiState(
                selectedCategory = FoodCategory.BREAKFAST,
                smartCombos = listOf(sampleCombo),
            ).shouldShowSmartCombos,
        )
        assertFalse(
            LoggingSheetUiState(
                favoritesOnly = true,
                smartCombos = listOf(sampleCombo),
            ).shouldShowSmartCombos,
        )
    }
}
