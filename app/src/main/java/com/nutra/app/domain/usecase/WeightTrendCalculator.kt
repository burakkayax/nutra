package com.nutra.app.domain.usecase

import com.nutra.app.domain.model.WeightEntry
import com.nutra.app.domain.model.WeightTrendPoint
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

class WeightTrendCalculator @Inject constructor() {

    /**
     * Computes a time-aware Exponential Moving Average (default tau ≈ 7 days, matching
     * [com.nutra.app.domain.metabolism.AdaptiveMetabolismEngine]) over daily scale readings
     * to filter out transient water retention / glycogen fluctuations and reveal the true weight trend.
     */
    fun calculateTrend(
        entries: List<WeightEntry>,
        smoothingFactor: Float = 0.25f,
    ): List<WeightTrendPoint> {
        if (entries.isEmpty()) return emptyList()
        val safeAlpha = if (smoothingFactor.isFinite()) smoothingFactor.coerceIn(0.01f, 1f) else 0.25f
        // Derive continuous-time tau from caller's smoothingFactor; default 0.25f maps to tau = 7.0 days
        val tauDays = if (kotlin.math.abs(safeAlpha - 0.25f) < 0.001f) {
            7.0
        } else if (safeAlpha >= 0.999f) {
            0.001
        } else {
            (-1.0 / ln(1.0 - safeAlpha.toDouble())).coerceIn(0.5, 60.0)
        }

        // Single-pass deduplication keeping the latest valid weigh-in per calendar day
        val latestByDate = LinkedHashMap<String, WeightEntry>(entries.size.coerceAtMost(256))
        var isAlreadySorted = true
        var lastInsertedDate: String? = null

        for (i in entries.indices) {
            val entry = entries[i]
            if (!entry.weightKg.isFinite() || entry.weightKg <= 0f || entry.dateIso.isBlank()) continue
            val existing = latestByDate[entry.dateIso]
            if (existing == null) {
                if (isAlreadySorted && lastInsertedDate != null && entry.dateIso < lastInsertedDate) {
                    isAlreadySorted = false
                }
                latestByDate[entry.dateIso] = entry
                lastInsertedDate = entry.dateIso
            } else if (
                entry.timestampMillis > existing.timestampMillis ||
                (entry.timestampMillis == existing.timestampMillis && entry.id > existing.id)
            ) {
                latestByDate[entry.dateIso] = entry
            }
        }
        if (latestByDate.isEmpty()) return emptyList()

        val dailyEntries = ArrayList<WeightEntry>(latestByDate.size)
        for (entry in latestByDate.values) {
            dailyEntries.add(entry)
        }
        if (!isAlreadySorted) {
            dailyEntries.sortBy { it.dateIso }
        }

        val parsedDates = dailyEntries.map { runCatching { LocalDate.parse(it.dateIso) }.getOrNull() }
        val result = ArrayList<WeightTrendPoint>(dailyEntries.size)
        var currentEma = dailyEntries[0].weightKg.toDouble()

        for (i in dailyEntries.indices) {
            val entry = dailyEntries[i]
            currentEma = if (i == 0) {
                entry.weightKg.toDouble()
            } else {
                val prevDate = parsedDates[i - 1]
                val currDate = parsedDates[i]
                val alpha = if (prevDate != null && currDate != null) {
                    val dt = ChronoUnit.DAYS.between(prevDate, currDate).coerceAtLeast(1L).toDouble()
                    if (safeAlpha >= 0.999f) 1.0 else (1.0 - exp(-dt / tauDays)).coerceIn(0.14, 0.72)
                } else {
                    safeAlpha.toDouble()
                }
                (alpha * entry.weightKg.toDouble()) + ((1.0 - alpha) * currentEma)
            }
            val roundedEma = (currentEma * 10.0).roundToInt() / 10f
            result.add(
                WeightTrendPoint(
                    dateIso = entry.dateIso,
                    rawWeightKg = entry.weightKg,
                    emaTrendKg = roundedEma,
                ),
            )
        }
        return result
    }
}
