package com.nutra.app.core.designsystem

import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

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

    fun formatKg(value: Float): String = String.format(TR_LOCALE, "%.1f", value)

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
