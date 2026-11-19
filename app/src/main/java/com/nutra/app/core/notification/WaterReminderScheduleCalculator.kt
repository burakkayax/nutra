package com.nutra.app.core.notification

import com.nutra.app.domain.model.WaterReminderSettings
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.Locale

object WaterReminderBounds {
    const val MIN_INTERVAL_MINUTES = 10
    const val MAX_INTERVAL_MINUTES = 360
    const val DEFAULT_INTERVAL_MINUTES = 120
    const val CUSTOM_STEP_MINUTES = 5

    const val MIN_START_TOTAL_MINUTES = 5 * 60
    const val MAX_START_TOTAL_MINUTES = 22 * 60
    const val MIN_END_TOTAL_MINUTES = 6 * 60
    const val MAX_END_TOTAL_MINUTES = 23 * 60 + 30
    const val MIN_WINDOW_SPAN_MINUTES = 30
    const val TIME_STEP_MINUTES = 30

    val PRESET_INTERVALS_MINUTES: List<Int> = listOf(30, 60, 90, 120, 180)
}

object WaterReminderScheduleCalculator {

    fun sanitize(settings: WaterReminderSettings): WaterReminderSettings {
        val safeInterval = settings.intervalMinutes.coerceIn(
            WaterReminderBounds.MIN_INTERVAL_MINUTES,
            WaterReminderBounds.MAX_INTERVAL_MINUTES,
        )
        val rawStart = settings.startHour.coerceIn(0, 23) * 60 + settings.startMinute.coerceIn(0, 59)
        val safeStart = rawStart.coerceIn(0, 23 * 60 + 59 - WaterReminderBounds.MIN_WINDOW_SPAN_MINUTES)
        val rawEnd = settings.endHour.coerceIn(0, 23) * 60 + settings.endMinute.coerceIn(0, 59)
        val safeEnd = rawEnd.coerceIn(safeStart + WaterReminderBounds.MIN_WINDOW_SPAN_MINUTES, 23 * 60 + 59)

        return settings.copy(
            intervalMinutes = safeInterval,
            startHour = safeStart / 60,
            startMinute = safeStart % 60,
            endHour = safeEnd / 60,
            endMinute = safeEnd % 60,
        )
    }

    fun isWithinActiveWindow(time: LocalTime, settings: WaterReminderSettings): Boolean {
        val safe = sanitize(settings)
        val startTotalSec = (safe.startHour * 60 + safe.startMinute) * 60
        val endTotalSec = (safe.endHour * 60 + safe.endMinute) * 60
        val currentSec = time.toSecondOfDay()
        val earliestSec = (startTotalSec - 120).coerceAtLeast(0)
        val latestSec = (endTotalSec + 600).coerceAtMost(24 * 60 * 60 - 1)
        return currentSec in earliestSec..latestSec
    }

    fun computeNextTriggerDateTime(
        now: LocalDateTime,
        settings: WaterReminderSettings,
        minLeadSeconds: Long = 5L,
    ): LocalDateTime? {
        if (!settings.enabled) return null
        val safe = sanitize(settings)
        val startTime = LocalTime.of(safe.startHour, safe.startMinute)
        val endTime = LocalTime.of(safe.endHour, safe.endMinute)

        val earliest = now.plusSeconds(minLeadSeconds.coerceAtLeast(1L))
        val today = earliest.toLocalDate()
        val todayStart = LocalDateTime.of(today, startTime)
        val todayEnd = LocalDateTime.of(today, endTime)

        if (!earliest.isAfter(todayStart)) {
            return todayStart
        }
        if (earliest.isAfter(todayEnd)) {
            return LocalDateTime.of(today.plusDays(1), startTime)
        }

        val elapsedSeconds = ChronoUnit.SECONDS.between(todayStart, earliest)
        val intervalSeconds = safe.intervalMinutes.toLong() * 60L
        val steps = (elapsedSeconds + intervalSeconds - 1L) / intervalSeconds
        val candidate = todayStart.plusSeconds(steps * intervalSeconds)

        return if (!candidate.isAfter(todayEnd)) {
            candidate
        } else {
            LocalDateTime.of(today.plusDays(1), startTime)
        }
    }

    fun estimateDailyReminderCount(settings: WaterReminderSettings): Int {
        val safe = sanitize(settings)
        val startTotal = safe.startHour * 60 + safe.startMinute
        val endTotal = safe.endHour * 60 + safe.endMinute
        val span = (endTotal - startTotal).coerceAtLeast(0)
        return (span / safe.intervalMinutes) + 1
    }

    fun formatInterval(minutes: Int): String {
        val safe = minutes.coerceIn(
            WaterReminderBounds.MIN_INTERVAL_MINUTES,
            WaterReminderBounds.MAX_INTERVAL_MINUTES,
        )
        return when {
            safe == 90 -> "1,5 saat"
            safe % 60 == 0 -> "${safe / 60} saat"
            safe < 60 -> "$safe dk"
            else -> "${safe / 60} sa ${safe % 60} dk"
        }
    }

    fun formatClock(hour: Int, minute: Int): String =
        "%02d:%02d".format(Locale.US, hour.coerceIn(0, 23), minute.coerceIn(0, 59))

    fun formatClockFromMinutes(totalMinutes: Int): String {
        val clamped = totalMinutes.coerceIn(0, 23 * 60 + 59)
        return formatClock(clamped / 60, clamped % 60)
    }

    fun formatNextTriggerLabel(now: LocalDateTime, nextTrigger: LocalDateTime): String {
        val clock = formatClock(nextTrigger.hour, nextTrigger.minute)
        val dayPrefix = if (nextTrigger.toLocalDate() == now.toLocalDate()) "Bugün" else "Yarın"
        return "$dayPrefix $clock"
    }

    fun formatProfileSummary(settings: WaterReminderSettings): String {
        val safe = sanitize(settings)
        if (!safe.enabled) return "Kapalı"
        val interval = formatInterval(safe.intervalMinutes)
        val start = formatClock(safe.startHour, safe.startMinute)
        val end = formatClock(safe.endHour, safe.endMinute)
        return "Açık · $interval aralıkla · $start–$end"
    }
}
