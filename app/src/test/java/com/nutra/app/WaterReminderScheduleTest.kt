package com.nutra.app

import com.nutra.app.core.notification.WaterReminderBounds
import com.nutra.app.core.notification.WaterReminderScheduleCalculator
import com.nutra.app.domain.model.UserProfile
import com.nutra.app.domain.model.WaterReminderSettings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

class WaterReminderScheduleTest {

    private val defaultEnabled = WaterReminderSettings(
        enabled = true,
        intervalMinutes = 120,
        startHour = 9,
        startMinute = 0,
        endHour = 22,
        endMinute = 0,
        stopWhenGoalReached = true,
    )

    @Test
    fun computeNextTrigger_returnsNullWhenDisabled() {
        val now = LocalDateTime.of(2026, 11, 19, 10, 0)
        assertNull(
            WaterReminderScheduleCalculator.computeNextTriggerDateTime(
                now = now,
                settings = defaultEnabled.copy(enabled = false),
            ),
        )
    }

    @Test
    fun computeNextTrigger_schedulesAnchoredSlotsAcrossWindowAndNextDay() {
        // Before active window -> today 09:00
        assertEquals(
            LocalDateTime.of(2026, 11, 19, 9, 0),
            WaterReminderScheduleCalculator.computeNextTriggerDateTime(
                now = LocalDateTime.of(2026, 11, 19, 7, 30),
                settings = defaultEnabled,
            ),
        )

        // Mid-day -> next 2-hour slot (09:00, 11:00, 13:00, ...)
        assertEquals(
            LocalDateTime.of(2026, 11, 19, 13, 0),
            WaterReminderScheduleCalculator.computeNextTriggerDateTime(
                now = LocalDateTime.of(2026, 11, 19, 11, 15),
                settings = defaultEnabled,
            ),
        )

        // Slightly early alarm wake-up with 60s lead -> skips 11:00 and picks 13:00 (no double fire)
        assertEquals(
            LocalDateTime.of(2026, 11, 19, 13, 0),
            WaterReminderScheduleCalculator.computeNextTriggerDateTime(
                now = LocalDateTime.of(2026, 11, 19, 10, 59, 58),
                settings = defaultEnabled,
                minLeadSeconds = 60L,
            ),
        )

        // Past last slot (21:00) -> tomorrow 09:00
        assertEquals(
            LocalDateTime.of(2026, 11, 20, 9, 0),
            WaterReminderScheduleCalculator.computeNextTriggerDateTime(
                now = LocalDateTime.of(2026, 11, 19, 21, 5),
                settings = defaultEnabled,
            ),
        )

        // Just before midnight with 60s lead -> rolls into next day 09:00, not +2 days
        assertEquals(
            LocalDateTime.of(2026, 11, 20, 9, 0),
            WaterReminderScheduleCalculator.computeNextTriggerDateTime(
                now = LocalDateTime.of(2026, 11, 19, 23, 59, 30),
                settings = defaultEnabled,
                minLeadSeconds = 60L,
            ),
        )
    }

    @Test
    fun computeNextTrigger_supportsCustomIntervals() {
        val custom45 = defaultEnabled.copy(intervalMinutes = 45, startHour = 8, startMinute = 0, endHour = 20, endMinute = 0)
        // Slots from 08:00 every 45m: 08:00, 08:45, 09:30, 10:15
        assertEquals(
            LocalDateTime.of(2026, 11, 19, 10, 15),
            WaterReminderScheduleCalculator.computeNextTriggerDateTime(
                now = LocalDateTime.of(2026, 11, 19, 9, 40),
                settings = custom45,
            ),
        )
    }

    @Test
    fun sanitize_clampsIntervalAndEnsuresValidWindow() {
        val clampedLow = WaterReminderScheduleCalculator.sanitize(
            WaterReminderSettings(
                enabled = true,
                intervalMinutes = 2,
                startHour = 22,
                startMinute = 0,
                endHour = 21,
                endMinute = 0,
            ),
        )
        assertEquals(WaterReminderBounds.MIN_INTERVAL_MINUTES, clampedLow.intervalMinutes)
        assertEquals(22, clampedLow.startHour)
        assertEquals(22, clampedLow.endHour)
        assertEquals(30, clampedLow.endMinute)

        val clampedHigh = WaterReminderScheduleCalculator.sanitize(
            WaterReminderSettings(enabled = true, intervalMinutes = 999),
        )
        assertEquals(WaterReminderBounds.MAX_INTERVAL_MINUTES, clampedHigh.intervalMinutes)
    }

    @Test
    fun activeWindowAndFormatting_behaveConsistently() {
        assertTrue(WaterReminderScheduleCalculator.isWithinActiveWindow(LocalTime.of(8, 59), defaultEnabled))
        assertTrue(WaterReminderScheduleCalculator.isWithinActiveWindow(LocalTime.of(22, 5), defaultEnabled))
        assertFalse(WaterReminderScheduleCalculator.isWithinActiveWindow(LocalTime.of(3, 0), defaultEnabled))

        assertEquals(7, WaterReminderScheduleCalculator.estimateDailyReminderCount(defaultEnabled))
        assertEquals("30 dk", WaterReminderScheduleCalculator.formatInterval(30))
        assertEquals("45 dk", WaterReminderScheduleCalculator.formatInterval(45))
        assertEquals("1 saat", WaterReminderScheduleCalculator.formatInterval(60))
        assertEquals("1,5 saat", WaterReminderScheduleCalculator.formatInterval(90))
        assertEquals("1 sa 15 dk", WaterReminderScheduleCalculator.formatInterval(75))
        assertEquals("2 saat", WaterReminderScheduleCalculator.formatInterval(120))

        assertEquals("Kapalı", WaterReminderScheduleCalculator.formatProfileSummary(defaultEnabled.copy(enabled = false)))
        assertEquals("Açık · 2 saat aralıkla · 09:00–22:00", WaterReminderScheduleCalculator.formatProfileSummary(defaultEnabled))
        assertEquals(
            "Bugün 13:00",
            WaterReminderScheduleCalculator.formatNextTriggerLabel(
                now = LocalDateTime.of(2026, 11, 19, 11, 0),
                nextTrigger = LocalDateTime.of(2026, 11, 19, 13, 0),
            ),
        )
        assertEquals(
            "Yarın 09:00",
            WaterReminderScheduleCalculator.formatNextTriggerLabel(
                now = LocalDateTime.of(2026, 11, 19, 21, 30),
                nextTrigger = LocalDateTime.of(2026, 11, 20, 9, 0),
            ),
        )
    }

    @Test
    fun userProfileSerialization_isBackwardCompatibleWithoutWaterReminderField() {
        val json = Json { ignoreUnknownKeys = true }
        val legacyJson = """{"age":30,"heightCm":180,"onboardingCompleted":true}"""
        val decodedLegacy = json.decodeFromString<UserProfile>(legacyJson)
        assertFalse(decodedLegacy.waterReminder.enabled)
        assertEquals(120, decodedLegacy.waterReminder.intervalMinutes)

        val updated = decodedLegacy.copy(
            waterReminder = WaterReminderSettings(enabled = true, intervalMinutes = 45, startHour = 8, startMinute = 30, endHour = 21, endMinute = 30),
        )
        val roundTrip = json.decodeFromString<UserProfile>(json.encodeToString(updated))
        assertEquals(updated.waterReminder, roundTrip.waterReminder)
    }
}
