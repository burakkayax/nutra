package com.nutra.app.domain.intelligence

import com.nutra.app.core.designsystem.NutraFormatters
import com.nutra.app.domain.model.BiologicalSex
import com.nutra.app.domain.model.FoodFeedbackRecord
import com.nutra.app.domain.model.FoodFeedbackStatus
import com.nutra.app.domain.model.MealLogItem
import com.nutra.app.domain.model.MealType
import com.nutra.app.domain.model.UserProfile
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Learns the user's evolving nutrition habits, meal-time preferences, usual portions,
 * cuisine affinity, food co-occurrences, and missing-data-safe micronutrient trends.
 *
 * Cold-start guarantee: when logged history is sparse (< 3 days), returns [HabitMaturity.NONE]
 * with neutral priors so the engine never pretends to have learned personal habits.
 */
class UserEatingProfileLearner @Inject constructor() {

    companion object {
        private const val DECAY_HALF_LIFE_DAYS = 21.0
        private const val MIN_MICRONUTRIENT_COVERAGE = 0.50f
        private const val MIN_MICRONUTRIENT_DAYS = 2
        private val DEFAULT_MEAL_SHARES = mapOf(
            MealType.BREAKFAST to 0.25f,
            MealType.LUNCH to 0.33f,
            MealType.DINNER to 0.32f,
            MealType.SNACK to 0.10f,
        )
    }

    fun learn(
        profile: UserProfile,
        classifiedCatalog: List<ClassifiedFood>,
        historicalLogs: List<MealLogItem>,
        feedbackRecords: List<FoodFeedbackRecord>,
        today: LocalDate = LocalDate.now(),
    ): UserEatingProfile {
        val byFoodId = HashMap<Long, ClassifiedFood>(classifiedCatalog.size)
        for (cf in classifiedCatalog) {
            if (cf.food.id > 0L) byFoodId[cf.food.id] = cf
        }
        val feedbackByKey = feedbackRecords.associateBy { it.foodKey }

        val validLogs = historicalLogs.filter { log ->
            val d = runCatching { LocalDate.parse(log.dateIso) }.getOrNull()
            d != null && !d.isAfter(today) && ChronoUnit.DAYS.between(d, today) <= 90L
        }

        val distinctDates = HashSet<String>(64)
        for (log in validLogs) distinctDates.add(log.dateIso)
        val loggedDaysCount = distinctDates.size

        val maturity = when {
            loggedDaysCount < 3 -> HabitMaturity.NONE
            loggedDaysCount < 10 -> HabitMaturity.EARLY
            else -> HabitMaturity.ESTABLISHED
        }

        val foodWeightedCounts = HashMap<Long, Double>()
        val mealFoodWeightedCounts = MealType.entries.associateWith { HashMap<Long, Double>() }
        val gramsHistoryByFoodId = HashMap<Long, MutableList<Float>>()
        val recent24h = HashSet<Long>()
        val recent48h = HashSet<Long>()

        var homeCookedWeight = 0.0
        var totalMainDishWeight = 0.0

        val mealCalTotals = DoubleArray(MealType.entries.size)
        var totalTrackedCal = 0.0

        val basketItemsByDateMeal = HashMap<String, MutableList<Long>>()

        // Recent 14d vs earlier 15..45d drift detection
        var recentHomeShareNum = 0.0
        var recentHomeShareDen = 0.0
        var olderHomeShareNum = 0.0
        var olderHomeShareDen = 0.0

        val lambda = ln(2.0) / DECAY_HALF_LIFE_DAYS
        val yesterday = today.minusDays(1)
        val twoDaysAgo = today.minusDays(2)

        for (log in validLogs) {
            val d = LocalDate.parse(log.dateIso)
            val ageDays = ChronoUnit.DAYS.between(d, today).coerceAtLeast(0L).toDouble()
            val decayWeight = exp(-lambda * ageDays)

            mealCalTotals[log.mealType.ordinal] += log.calories * decayWeight
            totalTrackedCal += log.calories * decayWeight

            val fid = log.foodId ?: continue
            foodWeightedCounts[fid] = (foodWeightedCounts[fid] ?: 0.0) + decayWeight
            val mealMap = mealFoodWeightedCounts.getValue(log.mealType)
            mealMap[fid] = (mealMap[fid] ?: 0.0) + decayWeight

            if (log.totalGrams > 0f && log.totalGrams.isFinite()) {
                gramsHistoryByFoodId.getOrPut(fid) { ArrayList(6) }.add(log.totalGrams)
            }

            if (!d.isBefore(yesterday)) recent24h.add(fid)
            if (!d.isBefore(twoDaysAgo)) recent48h.add(fid)

            val classified = byFoodId[fid]
            if (classified != null) {
                if (classified.role == MealRole.MAIN_PROTEIN ||
                    classified.role == MealRole.MAIN_COMPOSITE_DISH ||
                    classified.role == MealRole.SOUP ||
                    classified.role == MealRole.CARB_BASE
                ) {
                    totalMainDishWeight += decayWeight
                    if (classified.isTurkishHomeCooked) homeCookedWeight += decayWeight

                    if (ageDays <= 14.0) {
                        recentHomeShareDen += 1.0
                        if (classified.isTurkishHomeCooked) recentHomeShareNum += 1.0
                    } else if (ageDays <= 45.0) {
                        olderHomeShareDen += 1.0
                        if (classified.isTurkishHomeCooked) olderHomeShareNum += 1.0
                    }
                }
            }

            val basketKey = "${log.dateIso}|${log.mealType.name}"
            basketItemsByDateMeal.getOrPut(basketKey) { ArrayList(4) }.add(fid)
        }

        // Incorporate explicit recommendation acceptance/pin feedback into food affinity
        for (cf in classifiedCatalog) {
            val fb = feedbackByKey[cf.feedbackKey] ?: continue
            val fid = cf.food.id
            if (fid <= 0L) continue
            if (fb.acceptedCount > 0) {
                foodWeightedCounts[fid] = (foodWeightedCounts[fid] ?: 0.0) + fb.acceptedCount * 0.75
            }
            if (fb.status == FoodFeedbackStatus.PINNED) {
                foodWeightedCounts[fid] = (foodWeightedCounts[fid] ?: 0.0) + 3.0
            }
        }

        // Normalize food affinities to [0..1]
        val maxGlobal = foodWeightedCounts.values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
        val foodAffinity = HashMap<Long, Float>(foodWeightedCounts.size)
        for ((fid, w) in foodWeightedCounts) {
            foodAffinity[fid] = (w / maxGlobal).toFloat().coerceIn(0f, 1f)
        }

        val normalizedMealAffinity = HashMap<MealType, Map<Long, Float>>(4)
        for ((meal, map) in mealFoodWeightedCounts) {
            val maxM = map.values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
            val mOut = HashMap<Long, Float>(map.size)
            for ((fid, w) in map) {
                mOut[fid] = (w / maxM).toFloat().coerceIn(0f, 1f)
            }
            normalizedMealAffinity[meal] = mOut
        }

        val usualGrams = HashMap<Long, Float>(gramsHistoryByFoodId.size)
        for ((fid, list) in gramsHistoryByFoodId) {
            if (list.isNotEmpty()) {
                val sorted = list.sorted()
                val mid = sorted.size / 2
                usualGrams[fid] = if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2f
            }
        }

        // Shrink homeCookedAffinity smoothly toward neutral 0.50 when data is sparse
        val rawHomeAffinity = if (totalMainDishWeight > 0.1) (homeCookedWeight / totalMainDishWeight).toFloat() else 0.5f
        val shrinkage = when (maturity) {
            HabitMaturity.NONE -> 0f
            HabitMaturity.EARLY -> 0.45f
            HabitMaturity.ESTABLISHED -> 0.85f
        }
        val homeCookedAffinity = (0.5f * (1f - shrinkage) + rawHomeAffinity * shrinkage).coerceIn(0.15f, 0.95f)

        // Learned per-meal calorie shares blended with physiological defaults
        val mealShares = HashMap<MealType, Float>(4)
        for (meal in MealType.entries) {
            val defaultShare = DEFAULT_MEAL_SHARES.getValue(meal)
            val learnedShare = if (totalTrackedCal > 200.0) {
                (mealCalTotals[meal.ordinal] / totalTrackedCal).toFloat()
            } else {
                defaultShare
            }
            val shareBlend = if (maturity == HabitMaturity.ESTABLISHED) 0.55f else if (maturity == HabitMaturity.EARLY) 0.25f else 0f
            mealShares[meal] = (defaultShare * (1f - shareBlend) + learnedShare * shareBlend).coerceIn(0.08f, 0.48f)
        }
        val shareSum = mealShares.values.sum().coerceAtLeast(0.01f)
        for (meal in MealType.entries) {
            mealShares[meal] = mealShares.getValue(meal) / shareSum
        }

        // Build co-occurrence adjacency map (pairs consumed together >= 2 times)
        val pairCounts = HashMap<Pair<Long, Long>, Int>()
        for (basket in basketItemsByDateMeal.values) {
            val unique = basket.distinct()
            if (unique.size < 2) continue
            for (i in unique.indices) {
                for (j in i + 1 until unique.size) {
                    val a = minOf(unique[i], unique[j])
                    val b = maxOf(unique[i], unique[j])
                    pairCounts[a to b] = (pairCounts[a to b] ?: 0) + 1
                }
            }
        }
        val coOccurrence = HashMap<Long, MutableSet<Long>>()
        for ((pair, count) in pairCounts) {
            if (count >= 2) {
                coOccurrence.getOrPut(pair.first) { HashSet() }.add(pair.second)
                coOccurrence.getOrPut(pair.second) { HashSet() }.add(pair.first)
            }
        }

        // Missing-data-safe micronutrient evaluation over last 7 logged days
        val micronutrientSignals = evaluateRecentMicronutrients(
            profile = profile,
            byFoodId = byFoodId,
            validLogs = validLogs,
            today = today,
        )

        val habitShiftNote = if (recentHomeShareDen >= 6 && olderHomeShareDen >= 6) {
            val recentRatio = recentHomeShareNum / recentHomeShareDen
            val olderRatio = olderHomeShareNum / olderHomeShareDen
            when {
                recentRatio - olderRatio >= 0.22 -> "Son 2 haftada ev yemeği tercihlerinin arttığı görülüyor."
                olderRatio - recentRatio >= 0.22 -> "Son 2 haftada daha pratik/hızlı öğünlere yöneldiğin görülüyor."
                else -> null
            }
        } else {
            null
        }

        return UserEatingProfile(
            maturity = maturity,
            loggedDaysCount = loggedDaysCount,
            homeCookedAffinity = homeCookedAffinity,
            foodAffinityById = foodAffinity,
            mealFoodAffinity = normalizedMealAffinity,
            usualGramsByFoodId = usualGrams,
            recentlyEatenFoodIds24h = recent24h,
            recentlyEatenFoodIds48h = recent48h,
            mealCalorieShares = mealShares,
            coOccurrencePairs = coOccurrence,
            micronutrientSignals = micronutrientSignals,
            habitShiftNote = habitShiftNote,
        )
    }

    /**
     * Aggregates micronutrient records from the past 7 days WITHOUT treating null values as zero.
     * Any micronutrient with < 50% caloric coverage or < 2 logged days is marked [NutrientSignalStatus.INSUFFICIENT_DATA].
     */
    private fun evaluateRecentMicronutrients(
        profile: UserProfile,
        byFoodId: Map<Long, ClassifiedFood>,
        validLogs: List<MealLogItem>,
        today: LocalDate,
    ): List<MicronutrientSignal> {
        val windowStart = today.minusDays(6)
        val recentLogs = validLogs.filter {
            val d = LocalDate.parse(it.dateIso)
            !d.isBefore(windowStart) && !d.isAfter(today)
        }
        val activeDates = recentLogs.map { it.dateIso }.distinct()
        val dayCount = activeDates.size

        val fiberRef = ((profile.goals.calories / 1000f) * 14f).coerceIn(22f, 38f)
        val sodiumRef = 2300f
        val potassiumRef = if (profile.sex == BiologicalSex.MALE) 3400f else 2600f
        val calciumRef = 1000f
        val ironRef = if (profile.sex == BiologicalSex.FEMALE && profile.age <= 50) 18f else 8f
        val vitCRef = if (profile.sex == BiologicalSex.MALE) 90f else 75f

        val references = listOf(
            MicronutrientType.FIBER to fiberRef,
            MicronutrientType.SODIUM to sodiumRef,
            MicronutrientType.POTASSIUM to potassiumRef,
            MicronutrientType.CALCIUM to calciumRef,
            MicronutrientType.IRON to ironRef,
            MicronutrientType.VITAMIN_C to vitCRef,
        )

        val totalKcal = recentLogs.sumOf { it.calories }.toFloat()

        return references.map { (type, ref) ->
            if (dayCount < MIN_MICRONUTRIENT_DAYS || totalKcal < 400f) {
                return@map MicronutrientSignal(
                    type = type,
                    status = NutrientSignalStatus.INSUFFICIENT_DATA,
                    dailyAverageKnown = null,
                    referenceValue = ref,
                    coverageRatio = 0f,
                    plainNote = "Yeterli kayıt birikmediği için ${type.title.lowercase()} eğilimi hesaplanmadı.",
                )
            }

            var knownSum = 0.0
            var knownKcal = 0.0

            for (log in recentLogs) {
                val cf = log.foodId?.let { byFoodId[it] } ?: continue
                val per100: Float? = when (type) {
                    MicronutrientType.FIBER -> cf.food.fiberPer100g
                    MicronutrientType.SODIUM -> cf.effectiveSodiumMgPer100g
                    MicronutrientType.POTASSIUM -> cf.food.potassiumMgPer100g
                    MicronutrientType.CALCIUM -> cf.food.calciumMgPer100g
                    MicronutrientType.IRON -> cf.food.ironMgPer100g
                    MicronutrientType.VITAMIN_C -> cf.food.vitaminCMgPer100g
                }
                if (per100 != null && per100.isFinite()) {
                    knownSum += (per100 * log.totalGrams) / 100.0
                    knownKcal += log.calories
                }
            }

            val coverage = (knownKcal / totalKcal).toFloat().coerceIn(0f, 1f)
            if (coverage < MIN_MICRONUTRIENT_COVERAGE) {
                return@map MicronutrientSignal(
                    type = type,
                    status = NutrientSignalStatus.INSUFFICIENT_DATA,
                    dailyAverageKnown = null,
                    referenceValue = ref,
                    coverageRatio = coverage,
                    plainNote = "Son kayıtlardaki besinlerin bir kısmında ${type.title.lowercase()} bilgisi eksik olduğu için değerlendirme yapılmadı.",
                )
            }

            // Use recorded daily average (without inventing values for missing items)
            val dailyAvg = (knownSum / dayCount.toDouble()).toFloat()
            val status = if (type.isUpperLimit) {
                when {
                    dailyAvg > ref * 1.12f -> NutrientSignalStatus.ELEVATED_RECORDED
                    else -> NutrientSignalStatus.ADEQUATE
                }
            } else {
                val lowThreshold = if (type == MicronutrientType.FIBER) 0.78f else 0.65f
                when {
                    dailyAvg < ref * lowThreshold -> NutrientSignalStatus.LOW_RECORDED
                    else -> NutrientSignalStatus.ADEQUATE
                }
            }

            val formattedAvg = if (type.unit == "mg") {
                "${NutraFormatters.formatInt(dailyAvg.roundToInt())} mg"
            } else {
                "${NutraFormatters.formatMacroGrams(dailyAvg)} g"
            }
            val formattedRef = if (type.unit == "mg") {
                "${NutraFormatters.formatInt(ref.roundToInt())} mg"
            } else {
                "${NutraFormatters.formatInt(ref.roundToInt())} g"
            }
            val note = when (status) {
                NutrientSignalStatus.LOW_RECORDED ->
                    "Son $dayCount günde kayıtlı ortalama ${type.title.lowercase()} ~$formattedAvg (referans ~$formattedRef); bu besini destekleyen seçenekler öne çıkarılabilir."
                NutrientSignalStatus.ELEVATED_RECORDED ->
                    "Son $dayCount günde kayıtlı ortalama ${type.title.lowercase()} ~$formattedAvg (üst sınır ~$formattedRef); daha taze ve düşük tuzlu seçenekler dengelenebilir."
                NutrientSignalStatus.ADEQUATE ->
                    "Son $dayCount günde kayıtlı ortalama ${type.title.lowercase()} ~$formattedAvg ile dengeli aralıkta."
                NutrientSignalStatus.INSUFFICIENT_DATA ->
                    "Değerlendirme için yeterli veri bulunmuyor."
            }

            MicronutrientSignal(
                type = type,
                status = status,
                dailyAverageKnown = dailyAvg,
                referenceValue = ref,
                coverageRatio = coverage,
                plainNote = note,
            )
        }
    }
}
