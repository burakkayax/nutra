package com.nutra.app.domain.usecase

import com.nutra.app.domain.model.WeightEntry
import com.nutra.app.domain.model.WeightTrendPoint
import javax.inject.Inject
import kotlin.math.roundToInt

class WeightTrendCalculator @Inject constructor() {

    /**
     * Computes a 7-day Exponential Moving Average (EMA, alpha = 0.25) over daily scale readings
     * to filter out transient water retention / glycogen fluctuations and reveal the true weight trend.
     */
    fun calculateTrend(
        entries: List<WeightEntry>,
        smoothingFactor: Float = 0.25f,
    ): List<WeightTrendPoint> {
        if (entries.isEmpty()) return emptyList()
        val safeAlpha = if (smoothingFactor.isFinite()) smoothingFactor.coerceIn(0.01f, 1f) else 0.25f

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

        val result = ArrayList<WeightTrendPoint>(dailyEntries.size)
        var currentEma = dailyEntries[0].weightKg
        val decay = 1f - safeAlpha

        for (i in dailyEntries.indices) {
            val entry = dailyEntries[i]
            currentEma = if (i == 0) {
                entry.weightKg
            } else {
                (safeAlpha * entry.weightKg) + (decay * currentEma)
            }
            val roundedEma = (currentEma * 10f).roundToInt() / 10f
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
