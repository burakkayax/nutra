package com.nutra.app.core.designsystem

import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

object NutraFormatters {
    val TR_LOCALE: Locale = Locale.forLanguageTag("tr-TR")

    private val intFormatter = ThreadLocal.withInitial {
        NumberFormat.getIntegerInstance(TR_LOCALE)
    }

    private val dateLabelFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM", TR_LOCALE)

    private val shortWeekdayFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE", TR_LOCALE)

    // Bounded caches avoid repeated LocalDate.parse + DateTimeFormatter allocations across recompositions
    private val formattedDateCache = ConcurrentHashMap<String, String>(64)
    private val formattedWeekdayCache = ConcurrentHashMap<String, String>(64)

    fun formatInt(value: Int): String = intFormatter.get()!!.format(value)

    fun formatKg(value: Float): String {
        val normalized = if (!value.isFinite() || abs(value) < 0.05f) 0f else value
        return String.format(TR_LOCALE, "%.1f", normalized)
    }

    fun formatMultiplier(multiplier: Float): String {
        val safe = if (multiplier.isFinite()) multiplier else 1f
        return if (safe % 1f == 0f) {
            safe.toInt().toString()
        } else {
            String.format(Locale.US, "%.2f", safe).trimEnd('0').trimEnd('.')
        }
    }

    fun sanitizeNumericInput(input: String, allowDecimal: Boolean = true): String {
        var hasSeparator = false
        return buildString(input.length) {
            for (ch in input) {
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
            String.format(TR_LOCALE, "%.1f L", ml / 1000f)
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
