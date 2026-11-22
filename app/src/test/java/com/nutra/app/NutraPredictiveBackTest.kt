package com.nutra.app

import androidx.activity.BackEventCompat
import com.nutra.app.core.designsystem.PREDICTIVE_BACK_BG_MIN_SCALE
import com.nutra.app.core.designsystem.PREDICTIVE_BACK_COMMIT_SCALE_MULTIPLIER
import com.nutra.app.core.designsystem.PREDICTIVE_BACK_MAX_SCRIM_ALPHA
import com.nutra.app.core.designsystem.PREDICTIVE_BACK_MIN_SCALE
import com.nutra.app.core.designsystem.PREDICTIVE_BACK_TARGET_CORNER_DP
import com.nutra.app.core.designsystem.PredictiveBackTransform
import com.nutra.app.core.designsystem.computePredictiveBackTransform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NutraPredictiveBackTest {

    @Test
    fun settledState_returnsIdentityWithNoClipOrScrim() {
        val result = computePredictiveBackTransform(
            enterProgress = 1f,
            backProgress = 0f,
            commitExitProgress = 0f,
            swipeEdge = BackEventCompat.EDGE_LEFT,
            containerWidthPx = 1080f,
            density = 3f,
            deviceCornerRadiusPx = 72f,
            isDarkTheme = false,
            reducedMotion = false,
        )

        assertEquals(PredictiveBackTransform.Identity, result)
        assertEquals(1f, result.foregroundScale, 0.0001f)
        assertEquals(0f, result.foregroundTranslationX, 0.0001f)
        assertFalse(result.foregroundClip)
        assertEquals(0f, result.backgroundScrimAlpha, 0.0001f)
    }

    @Test
    fun preCommitLeftSwipe_shrinksRoundsCornersShiftsRightAndRevealsBackground() {
        val density = 3f
        val widthPx = 1152f // 384dp * 3
        val half = computePredictiveBackTransform(
            enterProgress = 1f,
            backProgress = 0.5f,
            commitExitProgress = 0f,
            swipeEdge = BackEventCompat.EDGE_LEFT,
            containerWidthPx = widthPx,
            density = density,
            deviceCornerRadiusPx = 0f,
            isDarkTheme = true,
            reducedMotion = false,
        )
        val full = computePredictiveBackTransform(
            enterProgress = 1f,
            backProgress = 1f,
            commitExitProgress = 0f,
            swipeEdge = BackEventCompat.EDGE_LEFT,
            containerWidthPx = widthPx,
            density = density,
            deviceCornerRadiusPx = 0f,
            isDarkTheme = true,
            reducedMotion = false,
        )

        assertTrue("Scale should decrease monotonically", half.foregroundScale < 1f && full.foregroundScale < half.foregroundScale)
        assertEquals(PREDICTIVE_BACK_MIN_SCALE, full.foregroundScale, 0.001f)
        assertTrue("Left edge swipe should move foreground right", half.foregroundTranslationX > 0f && full.foregroundTranslationX > half.foregroundTranslationX)
        assertTrue("Corners should round progressively", half.foregroundClip && full.foregroundClip)
        assertTrue("Corner radius should grow toward target", half.foregroundCornerRadiusPx > 0f && full.foregroundCornerRadiusPx >= PREDICTIVE_BACK_TARGET_CORNER_DP * density - 0.1f)
        assertEquals("Pre-commit foreground stays fully opaque", 1f, half.foregroundAlpha, 0.0001f)
        assertEquals("Pre-commit foreground stays fully opaque", 1f, full.foregroundAlpha, 0.0001f)
        assertTrue("Background scale should emerge toward 1f", full.backgroundScale > half.backgroundScale && full.backgroundScale <= 1f)
        assertTrue("Background scrim should clear as gesture advances", full.backgroundScrimAlpha < half.backgroundScrimAlpha && half.backgroundScrimAlpha < PREDICTIVE_BACK_MAX_SCRIM_ALPHA)
        assertTrue("Dark theme should render subtle rim border", full.foregroundBorderAlpha > 0f)
    }

    @Test
    fun preCommitRightSwipe_shiftsForegroundLeftAndMirrorsBackgroundParallax() {
        val leftSwipe = computePredictiveBackTransform(
            enterProgress = 1f,
            backProgress = 0.75f,
            commitExitProgress = 0f,
            swipeEdge = BackEventCompat.EDGE_LEFT,
            containerWidthPx = 1080f,
            density = 3f,
            deviceCornerRadiusPx = 60f,
            isDarkTheme = false,
            reducedMotion = false,
        )
        val rightSwipe = computePredictiveBackTransform(
            enterProgress = 1f,
            backProgress = 0.75f,
            commitExitProgress = 0f,
            swipeEdge = BackEventCompat.EDGE_RIGHT,
            containerWidthPx = 1080f,
            density = 3f,
            deviceCornerRadiusPx = 60f,
            isDarkTheme = false,
            reducedMotion = false,
        )

        assertTrue("Right swipe should shift foreground left", rightSwipe.foregroundTranslationX < 0f)
        assertEquals(-leftSwipe.foregroundTranslationX, rightSwipe.foregroundTranslationX, 0.001f)
        assertEquals(-leftSwipe.backgroundTranslationX, rightSwipe.backgroundTranslationX, 0.001f)
        assertEquals(leftSwipe.foregroundScale, rightSwipe.foregroundScale, 0.0001f)
        assertEquals(leftSwipe.foregroundCornerRadiusPx, rightSwipe.foregroundCornerRadiusPx, 0.0001f)
    }

    @Test
    fun commitExit_isContinuousFromCommittedPoseAndFadesOutCleanly() {
        val committedProgress = 0.6f
        val preCommitEnd = computePredictiveBackTransform(
            enterProgress = 1f,
            backProgress = committedProgress,
            commitExitProgress = 0f,
            swipeEdge = BackEventCompat.EDGE_LEFT,
            containerWidthPx = 1080f,
            density = 3f,
            deviceCornerRadiusPx = 48f,
            isDarkTheme = true,
            reducedMotion = false,
        )
        val exitMid = computePredictiveBackTransform(
            enterProgress = 1f,
            backProgress = committedProgress,
            commitExitProgress = 0.5f,
            swipeEdge = BackEventCompat.EDGE_LEFT,
            containerWidthPx = 1080f,
            density = 3f,
            deviceCornerRadiusPx = 48f,
            isDarkTheme = true,
            reducedMotion = false,
        )
        val exitDone = computePredictiveBackTransform(
            enterProgress = 1f,
            backProgress = committedProgress,
            commitExitProgress = 1f,
            swipeEdge = BackEventCompat.EDGE_LEFT,
            containerWidthPx = 1080f,
            density = 3f,
            deviceCornerRadiusPx = 48f,
            isDarkTheme = true,
            reducedMotion = false,
        )

        assertTrue("Scale continues shrinking cleanly during exit", exitMid.foregroundScale < preCommitEnd.foregroundScale)
        assertEquals(
            preCommitEnd.foregroundScale * PREDICTIVE_BACK_COMMIT_SCALE_MULTIPLIER,
            exitDone.foregroundScale,
            0.001f,
        )
        assertTrue("Translation continues gliding in swipe direction", exitMid.foregroundTranslationX > preCommitEnd.foregroundTranslationX)
        assertEquals(0.5f, exitMid.foregroundAlpha, 0.001f)
        assertEquals(0f, exitDone.foregroundAlpha, 0.0001f)
        assertEquals("Background finishes at full scale", 1f, exitDone.backgroundScale, 0.0001f)
        assertEquals("Background finishes centered", 0f, exitDone.backgroundTranslationX, 0.0001f)
        assertEquals("Background scrim clears completely", 0f, exitDone.backgroundScrimAlpha, 0.0001f)
    }

    @Test
    fun deviceCornerRadius_startsFromHardwareRadiusWithoutSharpCornerDrop() {
        val hardwareCorner = 84f
        val slightSwipe = computePredictiveBackTransform(
            enterProgress = 1f,
            backProgress = 0.05f,
            commitExitProgress = 0f,
            swipeEdge = BackEventCompat.EDGE_LEFT,
            containerWidthPx = 1080f,
            density = 3f,
            deviceCornerRadiusPx = hardwareCorner,
            isDarkTheme = false,
            reducedMotion = false,
        )

        assertTrue(
            "Corner radius should never drop below hardware bezel radius",
            slightSwipe.foregroundCornerRadiusPx >= hardwareCorner,
        )
    }

    @Test
    fun reducedMotion_disablesScalingTranslationAndClipping() {
        val result = computePredictiveBackTransform(
            enterProgress = 1f,
            backProgress = 0.8f,
            commitExitProgress = 0.5f,
            swipeEdge = BackEventCompat.EDGE_RIGHT,
            containerWidthPx = 1080f,
            density = 3f,
            deviceCornerRadiusPx = 60f,
            isDarkTheme = false,
            reducedMotion = true,
        )

        assertEquals(1f, result.foregroundScale, 0.0001f)
        assertEquals(0f, result.foregroundTranslationX, 0.0001f)
        assertFalse(result.foregroundClip)
        assertEquals(1f, result.backgroundScale, 0.0001f)
        assertEquals(0f, result.backgroundTranslationX, 0.0001f)
        assertTrue(result.foregroundAlpha in 0f..1f)
    }

    @Test
    fun forwardEnter_recessesBackgroundIntoExactPredictiveBackStartingState() {
        val nearEndEnter = computePredictiveBackTransform(
            enterProgress = 0.99f,
            backProgress = 0f,
            commitExitProgress = 0f,
            swipeEdge = BackEventCompat.EDGE_LEFT,
            containerWidthPx = 1080f,
            density = 3f,
            deviceCornerRadiusPx = 0f,
            isDarkTheme = false,
            reducedMotion = false,
        )

        assertEquals(PREDICTIVE_BACK_BG_MIN_SCALE, nearEndEnter.backgroundScale, 0.01f)
        assertEquals(PREDICTIVE_BACK_MAX_SCRIM_ALPHA, nearEndEnter.backgroundScrimAlpha, 0.01f)
    }
}
