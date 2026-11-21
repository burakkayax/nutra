package com.nutra.app.domain.metabolism

import com.nutra.app.core.designsystem.NutraFormatters
import com.nutra.app.domain.model.BiologicalSex
import com.nutra.app.domain.model.GoalHistoryEntry
import com.nutra.app.domain.model.GoalType
import com.nutra.app.domain.model.MacroGoals
import com.nutra.app.domain.model.UserProfile
import com.nutra.app.domain.model.WeightEntry
import com.nutra.app.domain.usecase.MacroGoalBounds
import com.nutra.app.domain.usecase.NutritionCalculatorUseCase
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Offline, scientifically grounded adaptive metabolism and weight-management engine.
 *
 * Key methods:
 * - Time-aware exponential weight smoothing with robust outlier rejection.
 * - 1D Bayesian / Kalman filter estimating latent TDEE with explicit uncertainty bounds
 *   and dynamic intake-completeness / scale-volatility observation variance.
 * - Hall (2011) & Forbes-inspired non-linear dynamic weight trajectory projection.
 * - Multi-hypothesis plateau diagnostics preventing premature calorie cuts.
 * - Strict clinical guardrails protecting adolescents (< 18), special health modes,
 *   and adult minimum energy floors.
 */
class AdaptiveMetabolismEngine @Inject constructor(
    private val nutritionCalculator: NutritionCalculatorUseCase,
) {

    companion object {
        const val MIN_ADULT_AGE = 18
        const val FEMALE_MIN_CALORIES = 1200
        const val MALE_MIN_CALORIES = 1500
        const val MAX_SINGLE_STEP_KCAL = 150
        const val MIN_MEANINGFUL_PROPOSAL_DELTA_KCAL = 60
        const val PROPOSAL_COOLDOWN_DAYS = 7L
        const val LOOKBACK_DAYS = 90L
        const val WINDOW_DAYS = 28L
        private const val WEIGHT_SMOOTHING_TAU_DAYS = 7.0
        private const val HALL_MASS_ENERGY_COEFF_KCAL_PER_KG = 24.0
        private const val WEEKLY_PROCESS_NOISE_KCAL = 38.0
        private const val MIN_POSTERIOR_SD_KCAL = 65.0
        private const val CREDIBLE_Z_80 = 1.28
        private const val OUTLIER_JUMP_KG = 4.0f
    }

    fun analyze(
        profile: UserProfile,
        intakeObservations: List<DailyIntakeObservation>,
        weightEntries: List<WeightEntry>,
        goalHistory: List<GoalHistoryEntry> = emptyList(),
        today: LocalDate = LocalDate.now(),
    ): MetabolismAnalysisResult {
        val guardrailState = resolveGuardrailState(profile)
        val guardrailNotice = buildGuardrailNotice(guardrailState, profile.age)

        val cleanedWeights = cleanDailyWeights(weightEntries, today)
        val validWeighIns = cleanedWeights.filter { !it.isOutlier }
        val ignoredOutlierCount = cleanedWeights.count { it.isOutlier }

        val currentRawWeight = validWeighIns.lastOrNull()?.weightKg
        val referenceWeightKg = currentRawWeight ?: profile.currentWeightKg.coerceIn(35f, 250f)

        val priorBmr = computeMifflinBmr(
            sex = profile.sex,
            age = profile.age,
            heightCm = profile.heightCm,
            weightKg = referenceWeightKg,
        )
        val priorTdee = (priorBmr * profile.activityLevel.multiplier).roundToInt().coerceIn(1100, 5200)

        // Build intake lookup map
        val intakeByDate = HashMap<LocalDate, DailyIntakeObservation>(intakeObservations.size.coerceAtLeast(16))
        for (obs in intakeObservations) {
            val d = runCatching { LocalDate.parse(obs.dateIso) }.getOrNull() ?: continue
            if (!d.isAfter(today)) {
                intakeByDate[d] = obs
            }
        }

        // Classify completed days (up to yesterday so today's partial intake never skews TDEE)
        val yesterday = today.minusDays(1)
        val lookbackStart = today.minusDays(LOOKBACK_DAYS - 1)
        val window28Start = yesterday.minusDays(WINDOW_DAYS - 1)
        val window14Start = yesterday.minusDays(13)

        val dayValidityByDate = HashMap<LocalDate, DayIntakeValidity>(96)
        var loggedIntakeLast28 = 0
        var completeIntakeLast28 = 0
        var suspectedIncompleteLast28 = 0
        var completeCalSum28 = 0L
        var completeCalSum14 = 0L
        var completeCalCount14 = 0

        var cursor = lookbackStart
        while (!cursor.isAfter(yesterday)) {
            val obs = intakeByDate[cursor]
            val validity = classifyIntakeDay(obs, priorTdee)
            dayValidityByDate[cursor] = validity
            if (!cursor.isBefore(window28Start)) {
                if (obs != null && obs.calories > 0) loggedIntakeLast28++
                if (validity == DayIntakeValidity.COMPLETE && obs != null) {
                    completeIntakeLast28++
                    completeCalSum28 += obs.calories
                    if (!cursor.isBefore(window14Start)) {
                        completeCalSum14 += obs.calories
                        completeCalCount14++
                    }
                } else if (validity == DayIntakeValidity.SUSPECTED_INCOMPLETE) {
                    suspectedIncompleteLast28++
                }
            }
            cursor = cursor.plusDays(1)
        }

        val avgIntake28 = if (completeIntakeLast28 > 0) {
            (completeCalSum28.toDouble() / completeIntakeLast28).roundToInt()
        } else {
            null
        }
        val avgIntake14 = if (completeCalCount14 > 0) {
            (completeCalSum14.toDouble() / completeCalCount14).roundToInt()
        } else {
            null
        }

        // Compute time-aware smoothed weight trend over all valid weigh-ins
        val smoothedWeighIns = computeTimeAwareTrend(validWeighIns)
        val dailyTrendByDate = interpolateDailyTrend(smoothedWeighIns, lookbackStart, today)
        val currentTrendWeightKg = smoothedWeighIns.lastOrNull()?.trendWeightKg ?: referenceWeightKg
        val scaleVolatilityKg = computeScaleVolatility(smoothedWeighIns)

        val weighInsLast28 = validWeighIns.count { !it.date.isBefore(today.minusDays(27)) && !it.date.isAfter(today) }
        val spanDays = computeEffectiveSpanDays(validWeighIns, intakeByDate, dayValidityByDate, today)
        val effectiveWindowDays = spanDays.coerceIn(1, 28)
        val intakeCompleteness = (completeIntakeLast28.toFloat() / effectiveWindowDays.toFloat()).coerceIn(0f, 1f)
        val weighInsPerWeek = (weighInsLast28.toFloat() / (effectiveWindowDays / 7f).coerceAtLeast(1f)).coerceAtMost(7f)

        val dataQuality = buildDataQualityReport(
            spanDays = spanDays,
            loggedIntakeLast28 = loggedIntakeLast28,
            completeIntakeLast28 = completeIntakeLast28,
            suspectedIncompleteLast28 = suspectedIncompleteLast28,
            weighInCountLast28 = weighInsLast28,
            totalValidWeighIns = validWeighIns.size,
            intakeCompleteness = intakeCompleteness,
            weighInsPerWeek = weighInsPerWeek,
            ignoredOutliers = ignoredOutlierCount,
        )

        // Compute recent weekly weight change velocity (kg/week) from smoothed trend
        val weeklyRateKg = computeRecentWeeklyRate(smoothedWeighIns, dailyTrendByDate, today)

        // Run 1D Bayesian / Kalman filter for adaptive TDEE
        val kalmanResult = runAdaptiveKalmanTdee(
            profile = profile,
            priorTdeeAtCurrent = priorTdee,
            smoothedWeighIns = smoothedWeighIns,
            dailyTrendByDate = dailyTrendByDate,
            intakeByDate = intakeByDate,
            dayValidityByDate = dayValidityByDate,
            scaleVolatilityKg = scaleVolatilityKg,
            today = today,
        )

        val estimatedTdee = kalmanResult.estimatedTdeeKcal
        val uncertaintyKcal = kalmanResult.uncertaintyKcal
        val tdeeDeltaFromPrior = estimatedTdee - priorTdee
        val confidenceScore = computeConfidenceScore(
            priorSd = max(180.0, priorTdee * 0.10),
            posteriorSd = kalmanResult.posteriorSd,
            dataQuality = dataQuality,
            kalmanUpdatesCount = kalmanResult.updatesCount,
        )
        val isCalibrated = kalmanResult.updatesCount >= 2 && dataQuality.level != DataQualityLevel.INSUFFICIENT

        // Build clean weight series for charting (last 90 days)
        val rawByDate = cleanedWeights.associateBy { it.date }
        val chartWeightSeries = buildList {
            if (smoothedWeighIns.isNotEmpty()) {
                val firstChartDate = maxOf(lookbackStart, smoothedWeighIns.first().date)
                val lastChartDate = smoothedWeighIns.last().date
                var d = firstChartDate
                while (!d.isAfter(lastChartDate)) {
                    val trend = dailyTrendByDate[d]
                    val raw = rawByDate[d]
                    if (trend != null) {
                        add(
                            AdaptiveWeightPoint(
                                dateIso = d.toString(),
                                rawWeightKg = raw?.weightKg,
                                trendWeightKg = round1(trend),
                                isOutlierRaw = raw?.isOutlier == true,
                            ),
                        )
                    }
                    d = d.plusDays(1)
                }
            }
        }

        // Plateau evaluation
        val plateauAssessment = evaluatePlateau(
            profile = profile,
            dailyTrendByDate = dailyTrendByDate,
            smoothedWeighIns = smoothedWeighIns,
            intakeByDate = intakeByDate,
            dayValidityByDate = dayValidityByDate,
            scaleVolatilityKg = scaleVolatilityKg,
            dataQuality = dataQuality,
            avgIntake28 = avgIntake28,
            currentTrendWeightKg = currentTrendWeightKg,
            today = today,
        )

        // Calorie target proposal (strictly safety-guarded, never silent!)
        val calorieProposal = buildCalorieTargetProposal(
            profile = profile,
            guardrailState = guardrailState,
            priorBmr = priorBmr,
            estimatedTdee = estimatedTdee,
            uncertaintyKcal = uncertaintyKcal,
            confidenceScore = confidenceScore,
            dataQuality = dataQuality,
            plateau = plateauAssessment,
            currentTrendWeightKg = currentTrendWeightKg,
            weeklyRateKg = weeklyRateKg,
            today = today,
            goalHistory = goalHistory,
        )

        // Non-linear Hall/Forbes dynamic weight projection
        val projectionIntake = resolveProjectionIntake(
            profile = profile,
            avgIntake28 = avgIntake28,
            avgIntake14 = avgIntake14,
            dataQuality = dataQuality,
        )
        val (projectionCurve, targetEta) = projectDynamicWeight(
            profile = profile,
            startWeightKg = currentTrendWeightKg,
            estimatedTdee = estimatedTdee,
            uncertaintyKcal = uncertaintyKcal,
            projectedIntakeKcal = projectionIntake,
            today = today,
        )

        val plainSummary = buildPlainSummary(
            guardrailState = guardrailState,
            dataQuality = dataQuality,
            priorTdee = priorTdee,
            estimatedTdee = estimatedTdee,
            uncertaintyKcal = uncertaintyKcal,
            weeklyRateKg = weeklyRateKg,
            isCalibrated = isCalibrated,
            goalType = profile.goalType,
        )

        return MetabolismAnalysisResult(
            analysisDateIso = today.toString(),
            guardrailState = guardrailState,
            guardrailNotice = guardrailNotice,
            priorBmrKcal = priorBmr.roundToInt(),
            priorTdeeKcal = priorTdee,
            estimatedTdeeKcal = estimatedTdee,
            uncertaintyKcal = uncertaintyKcal,
            tdeeDeltaFromPriorKcal = tdeeDeltaFromPrior,
            confidenceScore = confidenceScore,
            isAdaptiveCalibrated = isCalibrated,
            currentRawWeightKg = currentRawWeight,
            currentTrendWeightKg = round1(currentTrendWeightKg),
            weeklyWeightChangeKg = round2(weeklyRateKg),
            scaleVolatilityKg = round2(scaleVolatilityKg.toFloat()),
            averageDailyIntakeLast28Kcal = avgIntake28,
            averageDailyIntakeLast14Kcal = avgIntake14,
            activeDailyCalorieGoal = profile.goals.calories,
            targetWeightKg = profile.targetWeightKg,
            dataQuality = dataQuality,
            weightSeries = chartWeightSeries,
            tdeeHistory = kalmanResult.history,
            projectionCurve = projectionCurve,
            targetEta = targetEta,
            plateauAssessment = plateauAssessment,
            calorieProposal = calorieProposal,
            plainSummary = plainSummary,
        )
    }

    fun computeMifflinBmr(
        sex: BiologicalSex,
        age: Int,
        heightCm: Int,
        weightKg: Float,
    ): Float {
        val safeWeight = weightKg.coerceIn(35f, 250f)
        val safeHeight = heightCm.coerceIn(120, 230)
        val safeAge = age.coerceIn(14, 100)
        val sexConstant = when (sex) {
            BiologicalSex.MALE -> 5f
            BiologicalSex.FEMALE -> -161f
        }
        return (10f * safeWeight) + (6.25f * safeHeight) - (5f * safeAge) + sexConstant
    }

    private fun resolveGuardrailState(profile: UserProfile): MetabolismGuardrailState = when {
        profile.age < MIN_ADULT_AGE -> MetabolismGuardrailState.ADOLESCENT_PROTECTED
        profile.dietaryProfile.specialHealthGuardrail -> MetabolismGuardrailState.SPECIAL_HEALTH_PROTECTED
        else -> MetabolismGuardrailState.NORMAL_ADULT
    }

    private fun buildGuardrailNotice(state: MetabolismGuardrailState, age: Int): String? = when (state) {
        MetabolismGuardrailState.NORMAL_ADULT -> null
        MetabolismGuardrailState.ADOLESCENT_PROTECTED ->
            "Profil yaşın ($age) gelişim dönemi içinde olduğu için standart yetişkin kalori azaltma algoritması devre dışı bırakıldı. Sağlıklı büyüme ve beslenme planı için hekim veya diyetisyen desteği önerilir."
        MetabolismGuardrailState.SPECIAL_HEALTH_PROTECTED ->
            "Özel sağlık / gözetim modu aktif olduğu için otomatik kalori kesintisi önerileri kapalı tutulur. Bu ekran yalnızca gözlem ve trend takibi sunar."
    }

    private data class CleanedWeightEntry(
        val date: LocalDate,
        val weightKg: Float,
        val isOutlier: Boolean,
    )

    private fun cleanDailyWeights(entries: List<WeightEntry>, today: LocalDate): List<CleanedWeightEntry> {
        if (entries.isEmpty()) return emptyList()
        val latestByDate = LinkedHashMap<LocalDate, WeightEntry>(entries.size.coerceAtMost(256))
        for (entry in entries) {
            if (!entry.weightKg.isFinite() || entry.weightKg < 30f || entry.weightKg > 300f) continue
            val d = runCatching { LocalDate.parse(entry.dateIso) }.getOrNull() ?: continue
            if (d.isAfter(today)) continue
            val existing = latestByDate[d]
            if (existing == null ||
                entry.timestampMillis > existing.timestampMillis ||
                (entry.timestampMillis == existing.timestampMillis && entry.id > existing.id)
            ) {
                latestByDate[d] = entry
            }
        }
        val sorted = latestByDate.entries.sortedBy { it.key }
        val result = ArrayList<CleanedWeightEntry>(sorted.size)
        val recentValid = ArrayList<Float>(7)

        for (i in sorted.indices) {
            val date = sorted[i].key
            val weight = sorted[i].value.weightKg
            var isOutlier = false
            if (recentValid.isNotEmpty()) {
                val medianRef = medianFloat(recentValid)
                val prevDate = sorted.getOrNull(i - 1)?.key
                val daysSincePrev = if (prevDate != null) ChronoUnit.DAYS.between(prevDate, date).coerceAtLeast(1L) else 1L
                val diffFromRef = abs(weight - medianRef)
                val maxPlausibleJump = OUTLIER_JUMP_KG + (daysSincePrev * 0.35f).coerceAtMost(6f)
                if (diffFromRef > maxPlausibleJump) {
                    // Check if next weigh-in confirms a sustained shift or reverts back (typo)
                    val nextWeight = sorted.getOrNull(i + 1)?.value?.weightKg
                    if (nextWeight == null || abs(nextWeight - medianRef) < abs(nextWeight - weight)) {
                        isOutlier = true
                    }
                }
            }
            result.add(CleanedWeightEntry(date = date, weightKg = weight, isOutlier = isOutlier))
            if (!isOutlier) {
                recentValid.add(weight)
                if (recentValid.size > 5) recentValid.removeAt(0)
            }
        }
        return result
    }

    private fun classifyIntakeDay(obs: DailyIntakeObservation?, priorTdee: Int): DayIntakeValidity {
        if (obs == null || obs.calories <= 0) return DayIntakeValidity.UNLOGGED
        val extremeHigh = max(6500, (priorTdee * 2.6f).roundToInt())
        if (obs.calories > extremeHigh) return DayIntakeValidity.EXTREME_OUTLIER
        val suspectedLowFloor = max(600, (priorTdee * 0.40f).roundToInt())
        if (obs.calories < suspectedLowFloor && obs.mealTypeCount <= 1) {
            return DayIntakeValidity.SUSPECTED_INCOMPLETE
        }
        return DayIntakeValidity.COMPLETE
    }

    private data class SmoothedWeighIn(
        val date: LocalDate,
        val rawWeightKg: Float,
        val trendWeightKg: Float,
    )

    private fun computeTimeAwareTrend(validWeighIns: List<CleanedWeightEntry>): List<SmoothedWeighIn> {
        if (validWeighIns.isEmpty()) return emptyList()
        val out = ArrayList<SmoothedWeighIn>(validWeighIns.size)
        var trend = validWeighIns[0].weightKg.toDouble()
        out.add(SmoothedWeighIn(validWeighIns[0].date, validWeighIns[0].weightKg, trend.toFloat()))
        for (i in 1 until validWeighIns.size) {
            val prev = validWeighIns[i - 1]
            val curr = validWeighIns[i]
            val dt = ChronoUnit.DAYS.between(prev.date, curr.date).coerceAtLeast(1L).toDouble()
            val alpha = (1.0 - exp(-dt / WEIGHT_SMOOTHING_TAU_DAYS)).coerceIn(0.14, 0.72)
            trend = alpha * curr.weightKg + (1.0 - alpha) * trend
            out.add(SmoothedWeighIn(curr.date, curr.weightKg, trend.toFloat()))
        }
        return out
    }

    private fun interpolateDailyTrend(
        smoothed: List<SmoothedWeighIn>,
        minDate: LocalDate,
        maxDate: LocalDate,
    ): Map<LocalDate, Float> {
        if (smoothed.isEmpty()) return emptyMap()
        val map = LinkedHashMap<LocalDate, Float>()
        if (smoothed.size == 1) {
            val only = smoothed[0]
            if (!only.date.isBefore(minDate) && !only.date.isAfter(maxDate)) {
                map[only.date] = only.trendWeightKg
            }
            return map
        }
        for (i in 0 until smoothed.lastIndex) {
            val a = smoothed[i]
            val b = smoothed[i + 1]
            val span = ChronoUnit.DAYS.between(a.date, b.date).coerceAtLeast(1L)
            for (offset in 0L until span) {
                val d = a.date.plusDays(offset)
                if (!d.isBefore(minDate) && !d.isAfter(maxDate)) {
                    val frac = offset.toFloat() / span.toFloat()
                    map[d] = a.trendWeightKg + (b.trendWeightKg - a.trendWeightKg) * frac
                }
            }
        }
        val last = smoothed.last()
        if (!last.date.isBefore(minDate) && !last.date.isAfter(maxDate)) {
            map[last.date] = last.trendWeightKg
        }
        return map
    }

    private fun computeScaleVolatility(smoothed: List<SmoothedWeighIn>): Double {
        if (smoothed.size < 4) return 0.45
        val recent = smoothed.takeLast(28)
        val absResiduals = recent.map { abs(it.rawWeightKg - it.trendWeightKg) }
        val mad = medianFloat(absResiduals).toDouble()
        // Normal distribution scale factor: sigma ≈ 1.4826 * MAD
        return (mad * 1.4826).coerceIn(0.22, 1.8)
    }

    private fun computeEffectiveSpanDays(
        validWeighIns: List<CleanedWeightEntry>,
        intakeByDate: Map<LocalDate, DailyIntakeObservation>,
        validityByDate: Map<LocalDate, DayIntakeValidity>,
        today: LocalDate,
    ): Int {
        val earliestWeight = validWeighIns.firstOrNull()?.date
        val earliestCompleteIntake = validityByDate.entries
            .filter { it.value == DayIntakeValidity.COMPLETE && (intakeByDate[it.key]?.calories ?: 0) > 0 }
            .minOfOrNull { it.key }
        val latestWeight = validWeighIns.lastOrNull()?.date
        if (earliestWeight == null || earliestCompleteIntake == null || latestWeight == null) return 0
        val overlapStart = maxOf(earliestWeight, earliestCompleteIntake)
        val overlapEnd = minOf(latestWeight, today)
        if (overlapEnd.isBefore(overlapStart)) return 0
        return (ChronoUnit.DAYS.between(overlapStart, overlapEnd).toInt() + 1).coerceAtLeast(1)
    }

    private fun buildDataQualityReport(
        spanDays: Int,
        loggedIntakeLast28: Int,
        completeIntakeLast28: Int,
        suspectedIncompleteLast28: Int,
        weighInCountLast28: Int,
        totalValidWeighIns: Int,
        intakeCompleteness: Float,
        weighInsPerWeek: Float,
        ignoredOutliers: Int,
    ): DataQualityReport {
        val level = when {
            spanDays < 10 || completeIntakeLast28 < 7 || weighInCountLast28 < 4 -> DataQualityLevel.INSUFFICIENT
            spanDays < 14 || completeIntakeLast28 < 12 || weighInCountLast28 < 6 || intakeCompleteness < 0.60f -> DataQualityLevel.LOW
            spanDays >= 24 && completeIntakeLast28 >= 20 && weighInCountLast28 >= 10 && intakeCompleteness >= 0.80f -> DataQualityLevel.HIGH
            else -> DataQualityLevel.MODERATE
        }

        val summary = when (level) {
            DataQualityLevel.INSUFFICIENT ->
                "Kişiselleştirilmiş enerji tahmini için henüz yeterli veri birikmedi. Başlangıç formülü kullanılıyor."
            DataQualityLevel.LOW ->
                "İlk veriler değerlendiriliyor ancak kayıt süresi veya düzeni henüz sınırlı; belirsizlik aralığı geniş."
            DataQualityLevel.MODERATE ->
                "Son kayıtların metabolizmanı kalibre etmek için yeterli; düzenli kayıt sürdükçe güven aralığı daralır."
            DataQualityLevel.HIGH ->
                "Düzenli öğün ve tartı kayıtları sayesinde adaptif enerji tahmininin güvenilirliği yüksek."
        }

        val tip = when {
            weighInCountLast28 < 6 ->
                "Haftada en az 3 sabah tartısı eklemek su dalgalanmalarını ayrıştırmaya yardımcı olur."
            intakeCompleteness < 0.75f || suspectedIncompleteLast28 >= 3 ->
                "Eksik veya tek öğünlü günler belirsizliği artırır; ana öğünleri düzenli kaydetmeye çalış."
            spanDays < 21 ->
                "En az 3 haftalık veri biriktiğinde kişisel enerji harcaman çok daha netleşir."
            ignoredOutliers > 0 ->
                "$ignoredOutliers adet olağandışı tartı sıçraması analizde gürültü olarak filtrelendi."
            else -> null
        }

        return DataQualityReport(
            level = level,
            spanDays = spanDays,
            loggedIntakeDaysLast28 = loggedIntakeLast28,
            completeIntakeDaysLast28 = completeIntakeLast28,
            suspectedIncompleteDaysLast28 = suspectedIncompleteLast28,
            weighInCountLast28 = weighInCountLast28,
            totalValidWeighIns = totalValidWeighIns,
            intakeCompletenessRatio = intakeCompleteness,
            weighInsPerWeek = round1(weighInsPerWeek),
            ignoredOutlierWeighIns = ignoredOutliers,
            summaryMessage = summary,
            improvementTip = tip,
        )
    }

    private fun computeRecentWeeklyRate(
        smoothed: List<SmoothedWeighIn>,
        dailyTrendByDate: Map<LocalDate, Float>,
        today: LocalDate,
    ): Float {
        if (smoothed.size < 2) return 0f
        val endDate = minOf(smoothed.last().date, today)
        val startDate = maxOf(smoothed.first().date, endDate.minusDays(21))
        return computeWindowWeeklyRate(dailyTrendByDate, startDate, endDate)
    }

    private fun computeWindowWeeklyRate(
        dailyTrendByDate: Map<LocalDate, Float>,
        startDate: LocalDate,
        endDate: LocalDate,
    ): Float {
        val span = ChronoUnit.DAYS.between(startDate, endDate).toInt()
        if (span < 3) return 0f

        var n = 0
        var sumX = 0.0
        var sumY = 0.0
        var sumXX = 0.0
        var sumXY = 0.0
        var d = startDate
        var idx = 0
        while (!d.isAfter(endDate)) {
            val y = dailyTrendByDate[d]
            if (y != null) {
                val x = idx.toDouble()
                sumX += x
                sumY += y
                sumXX += x * x
                sumXY += x * y
                n++
            }
            idx++
            d = d.plusDays(1)
        }
        if (n < 3) return 0f
        val denom = n * sumXX - sumX * sumX
        if (abs(denom) < 1e-6) return 0f
        val slopePerDay = (n * sumXY - sumX * sumY) / denom
        return (slopePerDay * 7.0).toFloat().coerceIn(-3.0f, 3.0f)
    }

    private data class KalmanTdeeResult(
        val estimatedTdeeKcal: Int,
        val uncertaintyKcal: Int,
        val posteriorSd: Double,
        val updatesCount: Int,
        val history: List<TdeeHistoryPoint>,
    )

    private fun runAdaptiveKalmanTdee(
        profile: UserProfile,
        priorTdeeAtCurrent: Int,
        smoothedWeighIns: List<SmoothedWeighIn>,
        dailyTrendByDate: Map<LocalDate, Float>,
        intakeByDate: Map<LocalDate, DailyIntakeObservation>,
        dayValidityByDate: Map<LocalDate, DayIntakeValidity>,
        scaleVolatilityKg: Double,
        today: LocalDate,
    ): KalmanTdeeResult {
        val priorSd = max(180.0, priorTdeeAtCurrent * 0.10)
        if (smoothedWeighIns.size < 2 || dailyTrendByDate.isEmpty()) {
            val point = TdeeHistoryPoint(
                dateIso = today.toString(),
                estimatedTdeeKcal = priorTdeeAtCurrent,
                uncertaintyKcal = (priorSd * CREDIBLE_Z_80).roundToInt().coerceIn(90, 360),
                priorTdeeKcal = priorTdeeAtCurrent,
                rollingIntakeAvgKcal = null,
            )
            return KalmanTdeeResult(
                estimatedTdeeKcal = priorTdeeAtCurrent,
                uncertaintyKcal = point.uncertaintyKcal,
                posteriorSd = priorSd,
                updatesCount = 0,
                history = listOf(point),
            )
        }

        val lookbackStart = today.minusDays(LOOKBACK_DAYS - 1)
        val firstTrendDate = maxOf(lookbackStart, smoothedWeighIns.first().date)
        val lastTrendDate = minOf(smoothedWeighIns.last().date, today)
        val totalTrendDays = ChronoUnit.DAYS.between(firstTrendDate, lastTrendDate).toInt()
        if (totalTrendDays < 7) {
            val point = TdeeHistoryPoint(
                dateIso = lastTrendDate.toString(),
                estimatedTdeeKcal = priorTdeeAtCurrent,
                uncertaintyKcal = (priorSd * CREDIBLE_Z_80).roundToInt().coerceIn(90, 360),
                priorTdeeKcal = priorTdeeAtCurrent,
                rollingIntakeAvgKcal = null,
            )
            return KalmanTdeeResult(
                estimatedTdeeKcal = priorTdeeAtCurrent,
                uncertaintyKcal = point.uncertaintyKcal,
                posteriorSd = priorSd,
                updatesCount = 0,
                history = listOf(point),
            )
        }

        val startWeight = dailyTrendByDate[firstTrendDate] ?: smoothedWeighIns.first().trendWeightKg
        val startPriorTdee = computeMifflinBmr(profile.sex, profile.age, profile.heightCm, startWeight) *
            profile.activityLevel.multiplier

        var stateMean = startPriorTdee.toDouble()
        var stateVar = max(180.0, startPriorTdee * 0.10).pow(2)
        var updatesCount = 0

        val history = ArrayList<TdeeHistoryPoint>(16)
        history.add(
            TdeeHistoryPoint(
                dateIso = firstTrendDate.toString(),
                estimatedTdeeKcal = stateMean.roundToInt(),
                uncertaintyKcal = (sqrt(stateVar) * CREDIBLE_Z_80).roundToInt().coerceIn(75, 360),
                priorTdeeKcal = startPriorTdee.roundToInt(),
                rollingIntakeAvgKcal = null,
            ),
        )

        // Build global fallback median intake for short-gap imputation
        val validCalValues = dayValidityByDate.entries
            .filter { it.value == DayIntakeValidity.COMPLETE }
            .mapNotNull { intakeByDate[it.key]?.calories?.toFloat() }
        val globalMedianIntake = if (validCalValues.isNotEmpty()) medianFloat(validCalValues).toDouble() else null

        // Step in 7-day cadence using a 14-day energy balance window (variance-inflated for 50% overlap)
        val evalDates = ArrayList<LocalDate>()
        var evalDate = firstTrendDate.plusDays(14)
        while (!evalDate.isAfter(lastTrendDate)) {
            evalDates.add(evalDate)
            evalDate = evalDate.plusDays(7)
        }
        if (evalDates.isEmpty() && totalTrendDays >= 10) {
            evalDates.add(lastTrendDate)
        } else if (evalDates.isNotEmpty() && ChronoUnit.DAYS.between(evalDates.last(), lastTrendDate) >= 4) {
            evalDates.add(lastTrendDate)
        }

        var prevStepDate = firstTrendDate
        var prevStepWeight = startWeight

        for (stepDate in evalDates) {
            val dtStep = ChronoUnit.DAYS.between(prevStepDate, stepDate).coerceAtLeast(1L).toDouble()
            val currWeight = dailyTrendByDate[stepDate] ?: prevStepWeight
            val currPriorTdee = computeMifflinBmr(profile.sex, profile.age, profile.heightCm, currWeight) *
                profile.activityLevel.multiplier

            // 1. Predict step (mass-dependent resting metabolic shift + adaptive process noise)
            val massShiftKcal = HALL_MASS_ENERGY_COEFF_KCAL_PER_KG * (currWeight - prevStepWeight)
            val predictedMean = stateMean + massShiftKcal
            val processVar = (WEEKLY_PROCESS_NOISE_KCAL * sqrt(dtStep / 7.0)).pow(2)
            val predictedVar = stateVar + processVar

            // 2. Build energy-balance observation over [stepDate - windowLen, stepDate]
            val windowLen = min(14L, ChronoUnit.DAYS.between(firstTrendDate, stepDate))
            val winStart = stepDate.minusDays(windowLen)
            val wStart = dailyTrendByDate[winStart]
            val wEnd = dailyTrendByDate[stepDate]

            // Collect completed intake days inside [winStart, stepDate - 1]
            var completeCount = 0
            var completeSum = 0.0
            var totalIntakeDays = 0
            val completeList = ArrayList<Float>(14)
            var d = winStart
            val intakeEnd = minOf(stepDate.minusDays(1), today.minusDays(1))
            while (!d.isAfter(intakeEnd)) {
                totalIntakeDays++
                val v = dayValidityByDate[d]
                val obs = intakeByDate[d]
                if (v == DayIntakeValidity.COMPLETE && obs != null) {
                    completeCount++
                    completeSum += obs.calories
                    completeList.add(obs.calories.toFloat())
                }
                d = d.plusDays(1)
            }

            val windowWeighIns = smoothedWeighIns.count { !it.date.isBefore(winStart) && !it.date.isAfter(stepDate) }
            val completeness = if (totalIntakeDays > 0) completeCount.toDouble() / totalIntakeDays.toDouble() else 0.0
            val avgWindowIntake = if (completeCount > 0) (completeSum / completeCount) else globalMedianIntake

            if (wStart != null &&
                wEnd != null &&
                windowLen >= 10 &&
                completeCount >= 6 &&
                completeness >= 0.45 &&
                windowWeighIns >= 3 &&
                avgWindowIntake != null
            ) {
                val deltaWeightKg = (wEnd - wStart).toDouble()
                val isLoss = deltaWeightKg <= 0.0
                val isFirstWindow = ChronoUnit.DAYS.between(firstTrendDate, winStart) < 5
                val rho = estimateTissueEnergyDensityKcalPerKg(
                    sex = profile.sex,
                    age = profile.age,
                    heightCm = profile.heightCm,
                    weightKg = (wStart + wEnd) / 2f,
                    isLoss = isLoss,
                    isInitialPhase = isFirstWindow,
                )
                val dailyEnergyStorage = (deltaWeightKg * rho) / windowLen.toDouble()
                val observedTdee = avgWindowIntake - dailyEnergyStorage

                // Observation variance from scale noise + intake completeness + overlap autocorrelation
                val weightNoiseSd = (rho * sqrt(2.0) * scaleVolatilityKg) /
                    (windowLen.toDouble() * sqrt(max(1.0, windowWeighIns / 2.0)))
                val intakeSpreadSd = if (completeList.size >= 3) {
                    val mean = completeSum / completeList.size
                    val variance = completeList.sumOf { (it - mean).pow(2) } / completeList.size
                    (sqrt(variance) / sqrt(completeList.size.toDouble())).coerceIn(40.0, 220.0)
                } else {
                    120.0
                }
                val missingPenaltySd = 360.0 * (1.0 - completeness)
                val overlapFactor = if (dtStep < windowLen) 1.5 else 1.0
                val rawObsVar = overlapFactor * (
                    weightNoiseSd.pow(2) + (intakeSpreadSd + 75.0 + missingPenaltySd).pow(2)
                )

                // Huber robust gating protects against transient water flushes / unlogged holidays
                val innovation = observedTdee - predictedMean
                val innovationSd = sqrt(predictedVar + rawObsVar)
                val huberThreshold = 2.2 * innovationSd
                val effectiveObsVar = if (abs(innovation) > huberThreshold && huberThreshold > 1.0) {
                    val ratio = abs(innovation) / huberThreshold
                    rawObsVar * ratio * ratio
                } else {
                    rawObsVar
                }

                val kalmanGain = predictedVar / (predictedVar + effectiveObsVar)
                val lowerClamp = max(1050.0, currPriorTdee * 0.58)
                val upperClamp = min(5600.0, currPriorTdee * 1.52)
                stateMean = (predictedMean + kalmanGain * innovation).coerceIn(lowerClamp, upperClamp)
                stateVar = max(MIN_POSTERIOR_SD_KCAL.pow(2), (1.0 - kalmanGain) * predictedVar)
                updatesCount++
            } else {
                stateMean = predictedMean
                stateVar = min(priorSd.pow(2) * 1.25, predictedVar)
            }

            history.add(
                TdeeHistoryPoint(
                    dateIso = stepDate.toString(),
                    estimatedTdeeKcal = stateMean.roundToInt(),
                    uncertaintyKcal = (sqrt(stateVar) * CREDIBLE_Z_80).roundToInt().coerceIn(75, 360),
                    priorTdeeKcal = currPriorTdee.roundToInt(),
                    rollingIntakeAvgKcal = avgWindowIntake?.roundToInt(),
                ),
            )
            prevStepDate = stepDate
            prevStepWeight = currWeight
        }

        val finalSd = sqrt(stateVar)
        val roundedTdee = ((stateMean + 5.0) / 10.0).toInt() * 10
        val roundedUncertainty = (((finalSd * CREDIBLE_Z_80) + 5.0) / 10.0).toInt() * 10

        return KalmanTdeeResult(
            estimatedTdeeKcal = roundedTdee.coerceIn(1100, 5500),
            uncertaintyKcal = roundedUncertainty.coerceIn(70, 360),
            posteriorSd = finalSd,
            updatesCount = updatesCount,
            history = history,
        )
    }

    /**
     * Forbes / Hall-grounded effective tissue energy density (kcal/kg).
     * Avoids the overly rigid constant 7,700 kcal/kg assumption across lean/higher-adiposity bodies
     * and dampens early glycogen/water shifts in week 1.
     */
    internal fun estimateTissueEnergyDensityKcalPerKg(
        sex: BiologicalSex,
        age: Int,
        heightCm: Int,
        weightKg: Float,
        isLoss: Boolean,
        isInitialPhase: Boolean,
    ): Double {
        val hMeters = (heightCm.coerceIn(120, 230) / 100.0)
        val bmi = weightKg.coerceIn(35f, 250f) / (hMeters * hMeters)
        val sexBit = if (sex == BiologicalSex.MALE) 1.0 else 0.0
        val estBodyFatFrac = ((1.20 * bmi + 0.23 * age - 10.8 * sexBit - 5.4) / 100.0).coerceIn(0.10, 0.52)
        val estFatMassKg = weightKg * estBodyFatFrac

        val baseDensity = if (isLoss) {
            // Protein-sparing Forbes curve: fat accounts for ~70-87% of sustained weight loss
            val fatFraction = (estFatMassKg / (estFatMassKg + 5.8)).coerceIn(0.68, 0.87)
            fatFraction * 9400.0 + (1.0 - fatFraction) * 1800.0
        } else {
            // Mixed lean + fat gain
            val fatFraction = (estFatMassKg / (estFatMassKg + 9.5)).coerceIn(0.48, 0.76)
            fatFraction * 9400.0 + (1.0 - fatFraction) * 2100.0
        }

        val phaseAdjusted = if (isInitialPhase) baseDensity * 0.86 else baseDensity
        return if (isLoss) {
            phaseAdjusted.coerceIn(6200.0, 8400.0)
        } else {
            phaseAdjusted.coerceIn(5500.0, 7600.0)
        }
    }

    private fun computeConfidenceScore(
        priorSd: Double,
        posteriorSd: Double,
        dataQuality: DataQualityReport,
        kalmanUpdatesCount: Int,
    ): Int {
        if (dataQuality.level == DataQualityLevel.INSUFFICIENT || kalmanUpdatesCount == 0) {
            val starter = ((dataQuality.completeIntakeDaysLast28 / 14f) * 20f +
                (dataQuality.weighInCountLast28 / 6f) * 15f).roundToInt()
            return starter.coerceIn(0, 34)
        }
        val varianceReduction = (1.0 - (posteriorSd / priorSd).coerceIn(0.2, 1.0)) / 0.8
        val spanFactor = (dataQuality.spanDays / 28.0).coerceIn(0.0, 1.0)
        val completenessFactor = dataQuality.intakeCompletenessRatio.toDouble()
        val weighFactor = (dataQuality.weighInsPerWeek / 4.0).coerceIn(0.0, 1.0)
        val raw = 20.0 + 35.0 * varianceReduction + 20.0 * spanFactor + 15.0 * completenessFactor + 10.0 * weighFactor
        return raw.roundToInt().coerceIn(25, 95)
    }

    private fun evaluatePlateau(
        profile: UserProfile,
        dailyTrendByDate: Map<LocalDate, Float>,
        smoothedWeighIns: List<SmoothedWeighIn>,
        intakeByDate: Map<LocalDate, DailyIntakeObservation>,
        dayValidityByDate: Map<LocalDate, DayIntakeValidity>,
        scaleVolatilityKg: Double,
        dataQuality: DataQualityReport,
        avgIntake28: Int?,
        currentTrendWeightKg: Float,
        today: LocalDate,
    ): PlateauAssessment {
        if (profile.goalType == GoalType.MAINTAIN) {
            return PlateauAssessment(
                isStalled = false,
                stallDurationDays = 0,
                headline = "Kilo koruma hedefi aktif",
                explanation = "Kilonu koruma hedefinde yatay seyir beklenen ve sağlıklı bir durumdur.",
                factors = emptyList(),
                recommendsWaiting = false,
            )
        }

        val alreadyNearTarget = abs(currentTrendWeightKg - profile.targetWeightKg) <= 0.4f
        if (alreadyNearTarget) {
            return PlateauAssessment(
                isStalled = false,
                stallDurationDays = 0,
                headline = "Hedef kiloya çok yakınsın",
                explanation = "Trend kilon hedef kilona ulaşmış görünüyor; plato yerine koruma dengesi ön planda.",
                factors = emptyList(),
                recommendsWaiting = false,
            )
        }

        if (smoothedWeighIns.size < 4 || dataQuality.spanDays < 14) {
            return PlateauAssessment(
                isStalled = false,
                stallDurationDays = 0,
                headline = "Plato değerlendirmesi için veri toplanıyor",
                explanation = "Kilo durağanlığını güvenilir biçimde ayırt etmek için en az 2–3 haftalık tartı ve öğün takibi gerekir.",
                factors = emptyList(),
                recommendsWaiting = true,
            )
        }

        val endDate = minOf(smoothedWeighIns.last().date, today)
        val endWeight = dailyTrendByDate[endDate] ?: currentTrendWeightKg

        // Measure how many consecutive days back from endDate the trend stayed flat using robust OLS trend progress
        var stallDays = 0
        for (daysBack in 14..35) {
            val pastDate = endDate.minusDays(daysBack.toLong())
            val pastWeight = dailyTrendByDate[pastDate] ?: break
            val windowRateKgPerWeek = computeWindowWeeklyRate(dailyTrendByDate, pastDate, endDate)
            val modeledProgressTowardGoal = when (profile.goalType) {
                GoalType.LOSE_WEIGHT -> -(windowRateKgPerWeek * (daysBack / 7f))
                GoalType.GAIN_MUSCLE -> (windowRateKgPerWeek * (daysBack / 7f))
                GoalType.MAINTAIN -> 0f
            }
            val endpointProgressTowardGoal = when (profile.goalType) {
                GoalType.LOSE_WEIGHT -> pastWeight - endWeight
                GoalType.GAIN_MUSCLE -> endWeight - pastWeight
                GoalType.MAINTAIN -> 0f
            }
            val effectiveProgress = min(modeledProgressTowardGoal, endpointProgressTowardGoal)
            if (effectiveProgress < 0.25f) {
                stallDays = daysBack
            } else {
                break
            }
        }

        if (stallDays < 14) {
            return PlateauAssessment(
                isStalled = false,
                stallDurationDays = 0,
                headline = "Kilo trendi hedefine doğru ilerliyor",
                explanation = "Son haftalarda anlamlı bir durağanlık tespit edilmedi; mevcut ritmini koruyabilirsin.",
                factors = emptyList(),
                recommendsWaiting = false,
            )
        }

        // Stall detected (>= 14 days): evaluate competing non-metabolic hypotheses BEFORE assuming slowdown!
        val factors = ArrayList<PlateauFactor>(5)
        val lookDays = stallDays.coerceIn(14, 28)
        val stallStart = endDate.minusDays(lookDays.toLong())
        var completeInStall = 0
        var totalInStall = 0
        var stallIntakeSum = 0L
        var d = stallStart
        val intakeEnd = minOf(endDate.minusDays(1), today.minusDays(1))
        while (!d.isAfter(intakeEnd)) {
            totalInStall++
            if (dayValidityByDate[d] == DayIntakeValidity.COMPLETE) {
                completeInStall++
                stallIntakeSum += intakeByDate[d]?.calories ?: 0
            }
            d = d.plusDays(1)
        }
        val stallCompleteness = if (totalInStall > 0) completeInStall.toFloat() / totalInStall.toFloat() else 0f
        val stallAvgIntake = if (completeInStall > 0) (stallIntakeSum.toFloat() / completeInStall).roundToInt() else avgIntake28
        val weighInsInStall = smoothedWeighIns.count { !it.date.isBefore(stallStart) && !it.date.isAfter(endDate) }
        val weighInsPerWeekInStall = weighInsInStall / (lookDays / 7f)

        // Hypothesis 1: Transient water/glycogen retention
        if (stallDays < 21 || scaleVolatilityKg >= 0.50) {
            val score = (if (stallDays < 21) 0.78f else 0.48f) + (if (scaleVolatilityKg >= 0.55) 0.15f else 0f)
            factors.add(
                PlateauFactor(
                    type = PlateauFactorType.WATER_RETENTION_NOISE,
                    likelihoodScore = score.coerceAtMost(0.95f),
                    explanation = "Tuz alımı, karbonhidrat/glikojen değişimi, antrenman yoğunluğu veya uyku kaynaklı geçici su tutulması 2–3 haftalık yağ değişimini maskeleyebilir.",
                ),
            )
        }

        // Hypothesis 2: Incomplete food logging
        if (stallCompleteness < 0.80f) {
            val pct = (stallCompleteness * 100f).roundToInt()
            factors.add(
                PlateauFactor(
                    type = PlateauFactorType.INCOMPLETE_LOGGING,
                    likelihoodScore = (1f - stallCompleteness + 0.35f).coerceIn(0.45f, 0.95f),
                    explanation = "Son $lookDays günde öğün kayıt tamlığı %$pct düzeyinde. Kaydedilmeyen atıştırmalıklar, yağlar veya hafta sonu öğünleri ortalama alımı yükseltebilir.",
                ),
            )
        }

        // Hypothesis 3: Sparse weigh-ins
        if (weighInsPerWeekInStall < 3.0f) {
            factors.add(
                PlateauFactor(
                    type = PlateauFactorType.SPARSE_WEIGH_INS,
                    likelihoodScore = 0.72f,
                    explanation = "Bu dönemde haftalık tartı sıklığı ${NutraFormatters.formatKg(weighInsPerWeekInStall)} gün. Az tartım nedeniyle gerçek eğilim henüz netleşmemiş olabilir.",
                ),
            )
        }

        // Hypothesis 4: Actual logged intake diverging from goal
        val goalMismatchKcal = if (stallAvgIntake != null) {
            when (profile.goalType) {
                GoalType.LOSE_WEIGHT -> stallAvgIntake - profile.goals.calories
                GoalType.GAIN_MUSCLE -> profile.goals.calories - stallAvgIntake
                GoalType.MAINTAIN -> 0
            }
        } else {
            0
        }
        if (goalMismatchKcal >= 120) {
            val dirWord = if (profile.goalType == GoalType.LOSE_WEIGHT) "üzerinde" else "altında"
            factors.add(
                PlateauFactor(
                    type = PlateauFactorType.INTAKE_GOAL_MISMATCH,
                    likelihoodScore = 0.82f,
                    explanation = "Ortalama günlük alımın (${NutraFormatters.formatInt(stallAvgIntake ?: 0)} kcal) mevcut hedefinin (~${NutraFormatters.formatInt(goalMismatchKcal)} kcal) $dirWord seyrediyor. Hedefi değiştirmeden önce mevcut hedefe uyum gözden geçirilebilir.",
                ),
            )
        }

        // Hypothesis 5: True energy balance shift / activity drop — only strong when data is clean & >= 21 days!
        val hasStrongEvidence = stallDays >= 21 &&
            stallCompleteness >= 0.80f &&
            weighInsPerWeekInStall >= 3.0f &&
            goalMismatchKcal < 120

        factors.add(
            PlateauFactor(
                type = PlateauFactorType.METABOLIC_OR_ACTIVITY_SHIFT,
                likelihoodScore = if (hasStrongEvidence) 0.80f else 0.30f,
                explanation = if (hasStrongEvidence) {
                    "3 haftayı aşan düzenli kayıtlarda kilo sabit seyrediyor. Günlük hareket (NEAT) azalması veya vücut ağırlığı düştükçe enerji harcamasının uyumu etkili olabilir."
                } else {
                    "Metabolizma yavaşlamasından söz edebilmek için önce en az 3 haftalık düzenli öğün ve tartı kaydıyla diğer olasılıkların elenmesi gerekir."
                },
            ),
        )

        factors.sortByDescending { it.likelihoodScore }

        val recommendsWaiting = !hasStrongEvidence
        val headline = if (recommendsWaiting) {
            "Son $stallDays gündür kilo sabit — acele etme"
        } else {
            "Son $stallDays gündür belirgin kilo platosu"
        }
        val explanation = if (recommendsWaiting) {
            "Tartıdaki durağanlık mutlaka metabolizmanın yavaşladığı anlamına gelmez. Mevcut verilerde su tutulması, kayıt düzeni veya tartı sıklığı gibi etkenler öne çıkıyor; kalori hedefini hemen düşürmeden 1 hafta daha gözlemlemek daha güvenilir."
        } else {
            "Düzenli öğün ve tartı kayıtlarına rağmen 3 haftadır kilo trendi yatay seyrediyor. Günlük adım/aktivite düzeyini kontrol edebilir veya aşağıdaki ölçülü kalori güncellemesini değerlendirebilirsin."
        }

        return PlateauAssessment(
            isStalled = true,
            stallDurationDays = stallDays,
            headline = headline,
            explanation = explanation,
            factors = factors,
            recommendsWaiting = recommendsWaiting,
        )
    }

    private fun buildCalorieTargetProposal(
        profile: UserProfile,
        guardrailState: MetabolismGuardrailState,
        priorBmr: Float,
        estimatedTdee: Int,
        uncertaintyKcal: Int,
        confidenceScore: Int,
        dataQuality: DataQualityReport,
        plateau: PlateauAssessment,
        currentTrendWeightKg: Float,
        weeklyRateKg: Float,
        today: LocalDate,
        goalHistory: List<GoalHistoryEntry>,
    ): CalorieTargetProposal? {
        // Hard safety gates: never recommend adult deficits for adolescents or special health mode
        if (guardrailState != MetabolismGuardrailState.NORMAL_ADULT) return null
        if (!profile.adaptiveMetabolismEnabled) return null

        // Evidence gates: require sufficient data & confidence
        if (dataQuality.level == DataQualityLevel.INSUFFICIENT ||
            dataQuality.level == DataQualityLevel.LOW ||
            confidenceScore < 55 ||
            dataQuality.completeIntakeDaysLast28 < 14 ||
            dataQuality.weighInCountLast28 < 6
        ) {
            return null
        }

        // Respect plateau wait policy: never recommend cutting calories when plateau evidence is inconclusive
        if (plateau.isStalled && plateau.recommendsWaiting) return null

        // Cooldown gate: avoid nagging if user reviewed/applied a target recently
        val lastReviewDate = profile.lastAdaptiveReviewDateIso?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val lastAdaptiveHistoryDate = goalHistory
            .lastOrNull { it.source == com.nutra.app.domain.model.GoalChangeSource.ADAPTIVE_APPROVED }
            ?.effectiveDateIso
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val latestDecisionDate = listOfNotNull(lastReviewDate, lastAdaptiveHistoryDate).maxOrNull()
        if (latestDecisionDate != null && ChronoUnit.DAYS.between(latestDecisionDate, today) < PROPOSAL_COOLDOWN_DAYS) {
            return null
        }

        val currentCalories = profile.goals.calories
        val sexFloor = if (profile.sex == BiologicalSex.FEMALE) FEMALE_MIN_CALORIES else MALE_MIN_CALORIES
        val bmrFloor = (((priorBmr * 0.85f) + 9f) / 10f).toInt() * 10
        val safetyFloor = max(sexFloor, bmrFloor)

        // Ideal calorie target based on adaptive TDEE + goal type
        val rawIdealCalories = when (profile.goalType) {
            GoalType.LOSE_WEIGHT -> {
                // Target ~0.5% body weight/week loss (clamped 0.25..0.75 kg/week, max 25% TDEE deficit)
                val targetWeeklyLossKg = (currentTrendWeightKg * 0.005f).coerceIn(0.25f, 0.75f)
                val rho = estimateTissueEnergyDensityKcalPerKg(
                    profile.sex,
                    profile.age,
                    profile.heightCm,
                    currentTrendWeightKg,
                    isLoss = true,
                    isInitialPhase = false,
                )
                val idealDeficit = ((targetWeeklyLossKg * rho) / 7.0).roundToInt()
                    .coerceIn(250, min(600, (estimatedTdee * 0.25).roundToInt()))
                estimatedTdee - idealDeficit
            }
            GoalType.MAINTAIN -> estimatedTdee
            GoalType.GAIN_MUSCLE -> {
                val targetWeeklyGainKg = (currentTrendWeightKg * 0.003f).coerceIn(0.15f, 0.35f)
                val rho = estimateTissueEnergyDensityKcalPerKg(
                    profile.sex,
                    profile.age,
                    profile.heightCm,
                    currentTrendWeightKg,
                    isLoss = false,
                    isInitialPhase = false,
                )
                val idealSurplus = ((targetWeeklyGainKg * rho) / 7.0).roundToInt().coerceIn(180, 350)
                estimatedTdee + idealSurplus
            }
        }

        // Smooth step limit (±150 kcal) and floor clamp
        val stepClamped = rawIdealCalories.coerceIn(
            currentCalories - MAX_SINGLE_STEP_KCAL,
            currentCalories + MAX_SINGLE_STEP_KCAL,
        )
        val isSafetyClamped = stepClamped < safetyFloor
        val floored = stepClamped.coerceIn(safetyFloor, 4800)
        val proposedCalories = ((floored + 5) / 10) * 10

        // Never propose lowering calories if user is already at or below the clinical floor
        if (currentCalories <= safetyFloor && proposedCalories <= currentCalories) return null

        val delta = proposedCalories - currentCalories
        if (abs(delta) < MIN_MEANINGFUL_PROPOSAL_DELTA_KCAL) return null

        // Scale macros via NutritionCalculatorUseCase while protecting lean-mass protein adequacy
        val scaledGoals = nutritionCalculator.scaleMacrosForCalorieGoal(
            baselineGoals = profile.goals,
            targetCalories = proposedCalories,
            bounds = MacroGoalBounds(),
        ).let { base ->
            if (profile.goalType == GoalType.LOSE_WEIGHT) {
                val minProtectiveProtein = (currentTrendWeightKg * 1.4f).roundToInt()
                    .coerceAtMost(((proposedCalories * 0.38f) / 4f).roundToInt())
                if (base.proteinGrams < minProtectiveProtein) {
                    val extraP = minProtectiveProtein - base.proteinGrams
                    val nextCarbs = (base.carbsGrams - extraP).coerceAtLeast(60)
                    base.copy(proteinGrams = minProtectiveProtein, carbsGrams = nextCarbs)
                } else {
                    base
                }
            } else {
                base
            }
        }

        val directionPhrase = if (delta < 0) "biraz daha düşük" else "biraz daha yüksek"
        val rationale = buildString {
            append("Son ${dataQuality.spanDays.coerceAtMost(28)} günlük kilo trendin ")
            append("(${formatSignedRate(weeklyRateKg)} kg/hafta) ve kalori kayıtların değerlendirildiğinde ")
            append("günlük enerji harcamanın (~${NutraFormatters.formatInt(estimatedTdee)} kcal) ")
            append("mevcut hedefine kıyasla $directionPhrase olabileceği görülüyor. ")
            append("Hedefine dengeli ilerlemek için günlük kaloriyi ")
            append("${NutraFormatters.formatInt(currentCalories)} → ${NutraFormatters.formatInt(proposedCalories)} kcal ")
            append("olarak güncellemek değerlendirilebilir.")
        }

        val safetyNote = if (isSafetyClamped) {
            "Sağlıklı minimum enerji sınırı (${NutraFormatters.formatInt(safetyFloor)} kcal) gözetilerek daha düşük bir hedef önerilmedi."
        } else {
            null
        }

        return CalorieTargetProposal(
            currentGoals = profile.goals,
            proposedGoals = scaledGoals,
            deltaKcal = delta,
            title = if (delta < 0) "Kalori hedefinde ölçülü düşüş önerisi" else "Kalori hedefinde artış önerisi",
            rationale = rationale,
            uncertaintyNote = "Tahmin belirsizliği ±${NutraFormatters.formatInt(uncertaintyKcal)} kcal · Güncelleme yalnızca senin onayınla uygulanır.",
            isSafetyClamped = isSafetyClamped,
            safetyNote = safetyNote,
        )
    }

    private fun resolveProjectionIntake(
        profile: UserProfile,
        avgIntake28: Int?,
        avgIntake14: Int?,
        dataQuality: DataQualityReport,
    ): Int {
        val observed = avgIntake14 ?: avgIntake28
        return when {
            observed == null || dataQuality.level == DataQualityLevel.INSUFFICIENT -> profile.goals.calories
            dataQuality.intakeCompletenessRatio >= 0.70f ->
                (0.65f * observed + 0.35f * profile.goals.calories).roundToInt()
            else ->
                (0.35f * observed + 0.65f * profile.goals.calories).roundToInt()
        }
    }

    /**
     * Non-linear Hall (2011) / Forbes dynamic body-weight simulator.
     * As weight drops or rises, daily expenditure adapts dynamically (~24 kcal/kg + modest adaptive thermogenesis),
     * naturally capturing deceleration and non-linear approach to equilibrium.
     */
    private fun projectDynamicWeight(
        profile: UserProfile,
        startWeightKg: Float,
        estimatedTdee: Int,
        uncertaintyKcal: Int,
        projectedIntakeKcal: Int,
        today: LocalDate,
    ): Pair<List<WeightProjectionPoint>, TargetEtaRange?> {
        val targetWeight = profile.targetWeightKg
        val isLosing = profile.goalType == GoalType.LOSE_WEIGHT
        val isGaining = profile.goalType == GoalType.GAIN_MUSCLE

        // Uncertainty scenarios: optimistic reaches goal faster, conservative slower
        val optimisticTdee = if (isLosing) estimatedTdee + uncertaintyKcal else estimatedTdee - uncertaintyKcal
        val conservativeTdee = if (isLosing) estimatedTdee - uncertaintyKcal else estimatedTdee + uncertaintyKcal

        fun stepNextWeek(currW: Double, baseTdee: Double): Double {
            val massShift = HALL_MASS_ENERGY_COEFF_KCAL_PER_KG * (currW - startWeightKg)
            // Mild adaptive thermogenesis (~6% of sustained deficit/surplus)
            val rawBalance = projectedIntakeKcal - (baseTdee + massShift)
            val adaptiveOffset = if (rawBalance < 0) rawBalance * 0.06 else rawBalance * 0.04
            val effectiveTdee = max(950.0, baseTdee + massShift + adaptiveOffset)
            val dailyBalance = projectedIntakeKcal - effectiveTdee
            val rho = estimateTissueEnergyDensityKcalPerKg(
                sex = profile.sex,
                age = profile.age,
                heightCm = profile.heightCm,
                weightKg = currW.toFloat().coerceIn(35f, 250f),
                isLoss = dailyBalance <= 0.0,
                isInitialPhase = false,
            )
            return (currW + (7.0 * dailyBalance) / rho).coerceIn(35.0, 250.0)
        }

        val curve = ArrayList<WeightProjectionPoint>(17)
        var wExp = startWeightKg.toDouble()
        var wOpt = startWeightKg.toDouble()
        var wCon = startWeightKg.toDouble()

        var expectedWeekHit: Int? = null
        var earliestWeekHit: Int? = null
        var latestWeekHit: Int? = null

        val alreadyAtTarget = abs(startWeightKg - targetWeight) <= 0.4f

        for (week in 0..78) {
            if (week <= 16) {
                curve.add(
                    WeightProjectionPoint(
                        dateIso = today.plusWeeks(week.toLong()).toString(),
                        weekOffset = week,
                        expectedKg = round1(wExp.toFloat()),
                        optimisticKg = round1(wOpt.toFloat()),
                        conservativeKg = round1(wCon.toFloat()),
                    ),
                )
            }
            if (!alreadyAtTarget && week > 0) {
                if (isLosing) {
                    if (earliestWeekHit == null && wOpt <= targetWeight + 0.15) earliestWeekHit = week
                    if (expectedWeekHit == null && wExp <= targetWeight + 0.15) expectedWeekHit = week
                    if (latestWeekHit == null && wCon <= targetWeight + 0.15) latestWeekHit = week
                } else if (isGaining) {
                    if (earliestWeekHit == null && wOpt >= targetWeight - 0.15) earliestWeekHit = week
                    if (expectedWeekHit == null && wExp >= targetWeight - 0.15) expectedWeekHit = week
                    if (latestWeekHit == null && wCon >= targetWeight - 0.15) latestWeekHit = week
                }
            }
            wExp = stepNextWeek(wExp, estimatedTdee.toDouble())
            wOpt = stepNextWeek(wOpt, optimisticTdee.toDouble())
            wCon = stepNextWeek(wCon, conservativeTdee.toDouble())
        }

        // Steady-state equilibrium weight (Hall 100 kJ/day per kg ≈ 24 kcal/day/kg)
        val equilibriumKg = (startWeightKg + (projectedIntakeKcal - estimatedTdee) / HALL_MASS_ENERGY_COEFF_KCAL_PER_KG)
            .toFloat()
            .coerceIn(35f, 250f)

        val eta = when {
            profile.goalType == GoalType.MAINTAIN -> TargetEtaRange(
                reachesTarget = true,
                alreadyAtTarget = true,
                minWeeks = 0,
                maxWeeks = 0,
                expectedWeeks = 0,
                earliestDateIso = today.toString(),
                latestDateIso = today.toString(),
                equilibriumWeightKg = round1(equilibriumKg),
                summaryText = "Kilo koruma modunda mevcut denge ağırlığı ~${NutraFormatters.formatKg(equilibriumKg)} kg.",
            )
            alreadyAtTarget -> TargetEtaRange(
                reachesTarget = true,
                alreadyAtTarget = true,
                minWeeks = 0,
                maxWeeks = 0,
                expectedWeeks = 0,
                earliestDateIso = today.toString(),
                latestDateIso = today.toString(),
                equilibriumWeightKg = round1(equilibriumKg),
                summaryText = "Hedef kilona (${NutraFormatters.formatKg(targetWeight)} kg) ulaştın.",
            )
            expectedWeekHit != null -> {
                val minW = (earliestWeekHit ?: max(1, (expectedWeekHit * 0.75).roundToInt())).coerceAtLeast(1)
                val maxW = (latestWeekHit ?: (expectedWeekHit * 1.4).roundToInt()).coerceAtLeast(minW + 1)
                val earliestDate = today.plusWeeks(minW.toLong())
                val latestDate = today.plusWeeks(maxW.toLong())
                TargetEtaRange(
                    reachesTarget = true,
                    alreadyAtTarget = false,
                    minWeeks = minW,
                    maxWeeks = maxW,
                    expectedWeeks = expectedWeekHit,
                    earliestDateIso = earliestDate.toString(),
                    latestDateIso = latestDate.toString(),
                    equilibriumWeightKg = round1(equilibriumKg),
                    summaryText = "Mevcut ritimle hedefe tahmini ulaşma: $minW – $maxW hafta (${NutraFormatters.formatShortDate(earliestDate.toString(), today.toString())} – ${NutraFormatters.formatShortDate(latestDate.toString(), today.toString())})",
                )
            }
            else -> {
                TargetEtaRange(
                    reachesTarget = false,
                    alreadyAtTarget = false,
                    minWeeks = earliestWeekHit,
                    maxWeeks = null,
                    expectedWeeks = null,
                    earliestDateIso = earliestWeekHit?.let { today.plusWeeks(it.toLong()).toString() },
                    latestDateIso = null,
                    equilibriumWeightKg = round1(equilibriumKg),
                    summaryText = "Mevcut kalori alımıyla kilo değişimi doğrusal sürmez; ağırlığın ~${NutraFormatters.formatKg(equilibriumKg)} kg civarında dengeye oturabilir.",
                )
            }
        }

        return curve to eta
    }

    private fun buildPlainSummary(
        guardrailState: MetabolismGuardrailState,
        dataQuality: DataQualityReport,
        priorTdee: Int,
        estimatedTdee: Int,
        uncertaintyKcal: Int,
        weeklyRateKg: Float,
        isCalibrated: Boolean,
        goalType: GoalType,
    ): String {
        if (guardrailState != MetabolismGuardrailState.NORMAL_ADULT) {
            return "Günlük enerji harcaman formüle göre yaklaşık ${NutraFormatters.formatInt(priorTdee)} kcal tahmin ediliyor. Güvenlik modu nedeniyle yalnızca trend gözlemi sunuluyor."
        }
        if (!isCalibrated || dataQuality.level == DataQualityLevel.INSUFFICIENT) {
            return "Başlangıç formülüne göre günlük enerji harcaman ~${NutraFormatters.formatInt(priorTdee)} kcal (±${NutraFormatters.formatInt(uncertaintyKcal)} kcal). Düzenli öğün ve tartı kaydı arttıkça tahmin gerçek verilerinle kişiselleşir."
        }
        val diff = estimatedTdee - priorTdee
        val shiftText = when {
            diff <= -70 -> "başlangıç tahmininden yaklaşık ${NutraFormatters.formatInt(abs(diff))} kcal daha düşük"
            diff >= 70 -> "başlangıç tahmininden yaklaşık ${NutraFormatters.formatInt(diff)} kcal daha yüksek"
            else -> "başlangıç formülüyle büyük ölçüde uyumlu"
        }
        val goalLabel = when (goalType) {
            GoalType.LOSE_WEIGHT -> "kilo verme"
            GoalType.MAINTAIN -> "kilo koruma"
            GoalType.GAIN_MUSCLE -> "hacim kazanımı"
        }
        return "Gerçek kilo trendin (${formatSignedRate(weeklyRateKg)} kg/hafta) ve kalori kayıtlarına göre günlük enerji harcaman ~${NutraFormatters.formatInt(estimatedTdee)} ± ${NutraFormatters.formatInt(uncertaintyKcal)} kcal aralığında; bu değer $shiftText ($goalLabel hedefi)."
    }

    private fun formatSignedRate(rateKg: Float): String {
        val sign = if (rateKg > 0.04f) "+" else ""
        return "$sign${NutraFormatters.formatKg(rateKg)}"
    }

    private fun medianFloat(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2f
    }

    private fun round1(v: Float): Float = (v * 10f).roundToInt() / 10f
    private fun round2(v: Float): Float = (v * 100f).roundToInt() / 100f
}
