package com.nutra.app

import com.nutra.app.core.designsystem.CalorieDisplayMode
import com.nutra.app.feature.dashboard.DashboardUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardUiStateTest {

    @Test
    fun defaultDashboardUiState_showsConsumedCaloriesAndCollapsedMeals() {
        val state = DashboardUiState()
        assertTrue(state.isLoading)
        assertEquals(CalorieDisplayMode.CONSUMED, state.calorieDisplayMode)
        assertTrue(state.expandedMeals.isEmpty())
    }
}
