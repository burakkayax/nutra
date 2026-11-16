package com.nutra.app

import com.nutra.app.feature.dashboard.DashboardUiState
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
}
