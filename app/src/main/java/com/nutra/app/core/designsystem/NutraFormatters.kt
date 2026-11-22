package com.nutra.app.core.designsystem

import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.roundToInt

object NutraFormatters {
    val TR_LOCALE: Locale = Locale.forLanguageTag("tr-TR")

    private val intFormatter = ThreadLocal.withInitial {
        NumberFormat.getIntegerInstance(TR_LOCALE)
    }

    private val dateLabelFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM", TR_LOCALE)

    private val dateWithYearFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM yyyy", TR_LOCALE)

    private val shortWeekdayFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE", TR_LOCALE)

    // Bounded caches avoid repeated LocalDate.parse + DateTimeFormatter allocations across recompositions
    private val formattedDateCache = ConcurrentHashMap<String, String>(64)
    private val formattedWeekdayCache = ConcurrentHashMap<String, String>(64)

    fun formatInt(value: Int): String =
        if (value in -999..999) {
            value.toString()
        } else {
            intFormatter.get()!!.format(value)
        }

    fun formatKg(value: Float): String {
        val normalized = if (!value.isFinite() || abs(value) < 0.05f) 0f else value
        val absVal = abs(normalized)
        if (absVal >= 1_000_000f) {
            return String.format(TR_LOCALE, "%.1f", normalized)
        }
        val tenths = (absVal.toDouble() * 10.0).roundToInt()
        val whole = tenths / 10
        val frac = tenths % 10
        return if (normalized < 0f && tenths > 0) {
            "-$whole,$frac"
        } else {
            "$whole,$frac"
        }
    }

    fun formatMacroGrams(value: Float): String {
        val safe = if (!value.isFinite() || value <= 0f) 0f else value
        if (safe >= 1_000_000f) {
            return String.format(TR_LOCALE, "%.1f", safe)
        }
        val tenths = (safe * 10f).roundToInt()
        val frac = tenths % 10
        return if (frac == 0) {
            formatInt(tenths / 10)
        } else {
            "${tenths / 10},$frac"
        }
    }

    fun formatMultiplier(multiplier: Float): String {
        val safe = if (multiplier.isFinite()) multiplier else 1f
        if (safe % 1f == 0f) {
            return safe.toInt().toString()
        }
        val absVal = abs(safe)
        if (absVal >= 100_000f) {
            return String.format(TR_LOCALE, "%.2f", safe).trimEnd('0').trimEnd(',')
        }
        val hundredths = (absVal.toDouble() * 100.0).roundToInt()
        val sign = if (safe < 0f && hundredths > 0) "-" else ""
        val whole = hundredths / 100
        val frac = hundredths % 100
        return when {
            frac == 0 -> "$sign$whole"
            frac % 10 == 0 -> "$sign$whole,${frac / 10}"
            frac < 10 -> "$sign$whole,0$frac"
            else -> "$sign$whole,$frac"
        }
    }

    fun capitalizeTr(text: String): String =
        text.replaceFirstChar { it.titlecase(TR_LOCALE) }

    fun lowercaseTr(text: String): String =
        text.lowercase(TR_LOCALE)

    fun formatMonthSpan(weekStart: LocalDate): String {
        val weekEnd = weekStart.plusDays(6)
        val startMonth = capitalizeTr(
            weekStart.month.getDisplayName(java.time.format.TextStyle.FULL, TR_LOCALE),
        )
        val endMonth = capitalizeTr(
            weekEnd.month.getDisplayName(java.time.format.TextStyle.FULL, TR_LOCALE),
        )
        return when {
            weekStart.year != weekEnd.year -> "$startMonth ${weekStart.year} – $endMonth ${weekEnd.year}"
            weekStart.month != weekEnd.month -> "$startMonth – $endMonth ${weekEnd.year}"
            else -> "$startMonth ${weekStart.year}"
        }
    }

    fun sanitizeNumericInput(input: String, allowDecimal: Boolean = true): String {
        var hasSeparator = false
        return buildString(input.length) {
            for (i in 0 until input.length) {
                val ch = input[i]
                when {
                    ch.isDigit() -> append(ch)
                    allowDecimal && (ch == ',' || ch == '.') && !hasSeparator -> {
                        hasSeparator = true
                        append(ch)
                    }
                }
            }
        }
    }

    fun parseLocalizedFloat(input: String): Float? =
        input.trim().replace(',', '.').toFloatOrNull()?.takeIf { it.isFinite() }

    fun parseLocalizedDouble(input: String): Double? =
        input.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

    fun formatWaterLitres(ml: Int): String =
        if (ml >= 1000) {
            val tenths = (ml.toDouble() / 100.0).roundToInt()
            "${tenths / 10},${tenths % 10} L"
        } else {
            "${formatInt(ml)} ml"
        }

    fun formatShortDate(dateIso: String?, todayIso: String? = null): String {
        if (dateIso.isNullOrBlank()) return ""
        val effectiveTodayIso = todayIso ?: LocalDate.now().toString()
        if (dateIso == effectiveTodayIso) return "Bugün"
        return formattedDateCache[dateIso] ?: runCatching {
            val formatted = LocalDate.parse(dateIso).format(dateLabelFormatter)
            if (formattedDateCache.size > 128) formattedDateCache.clear()
            formattedDateCache[dateIso] = formatted
            formatted
        }.getOrDefault(dateIso)
    }

    fun formatShortDateWithYearIfNeeded(dateIso: String?, referenceToday: LocalDate = LocalDate.now()): String {
        if (dateIso.isNullOrBlank()) return ""
        return runCatching {
            val date = LocalDate.parse(dateIso)
            if (date == referenceToday) return "Bugün"
            if (date.year != referenceToday.year) {
                date.format(dateWithYearFormatter)
            } else {
                date.format(dateLabelFormatter)
            }
        }.getOrDefault(dateIso)
    }

    fun formatShortWeekday(dateIso: String): String {
        if (dateIso.isBlank()) return ""
        return formattedWeekdayCache[dateIso] ?: runCatching {
            val formatted = LocalDate.parse(dateIso)
                .format(shortWeekdayFormatter)
                .replace(".", "")
                .replaceFirstChar { it.uppercase(TR_LOCALE) }
            if (formattedWeekdayCache.size > 128) formattedWeekdayCache.clear()
            formattedWeekdayCache[dateIso] = formatted
            formatted
        }.getOrDefault("")
    }
}
