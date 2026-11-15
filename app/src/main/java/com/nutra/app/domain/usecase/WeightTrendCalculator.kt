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

        val sortedByDate = entries
            .groupBy { it.dateIso }
            .mapValues { (_, list) -> list.maxBy { it.timestampMillis } }
            .values
            .sortedBy { it.dateIso }

        val result = ArrayList<WeightTrendPoint>(sortedByDate.size)
        var currentEma = sortedByDate.first().weightKg

        for ((index, entry) in sortedByDate.withIndex()) {
            currentEma = if (index == 0) {
                entry.weightKg
            } else {
                (smoothingFactor * entry.weightKg) + ((1f - smoothingFactor) * currentEma)
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
