package com.nutra.app.domain.usecase

import com.nutra.app.core.designsystem.NutraFormatters
import com.nutra.app.domain.model.ComboFoodEntry
import com.nutra.app.domain.model.FoodCategory
import com.nutra.app.domain.model.FoodItem
import com.nutra.app.domain.model.MealLogItem
import com.nutra.app.domain.model.MealType
import com.nutra.app.domain.model.SmartComboSuggestion
import javax.inject.Inject

private data class ScoredFood(
    val food: FoodItem,
    val score: Double,
)

private class HistoricalBasketGroup(
    val sampleBasket: List<MealLogItem>,
    var count: Int,
    var latestTimestamp: Long,
)

/**
 * Pre-aggregated historical usage statistics for a specific [MealType],
 * allowing repeated search and category filtering without re-scanning historical logs.
 */
data class MealUsageStats(
    val mealSpecificCounts: Map<Long, Int>,
    val mealSpecificLatestTime: Map<Long, Long>,
    val globalCounts: Map<Long, Int>,
    val maxTimestamp: Long,
)

class ContextualMealRankerAndComboEngine @Inject constructor() {

    fun prepareMealUsageStats(
        historicalLogs: List<MealLogItem>,
        mealType: MealType,
    ): MealUsageStats {
        val mealSpecificCounts = HashMap<Long, Int>()
        val mealSpecificLatestTime = HashMap<Long, Long>()
        val globalCounts = HashMap<Long, Int>()
        var maxTimestamp = 1L

        for (log in historicalLogs) {
            val fid = log.foodId ?: continue
            globalCounts[fid] = (globalCounts[fid] ?: 0) + 1
            if (log.mealType == mealType) {
                mealSpecificCounts[fid] = (mealSpecificCounts[fid] ?: 0) + 1
                val prevLatest = mealSpecificLatestTime[fid] ?: 0L
                if (log.timestampMillis > prevLatest) {
                    mealSpecificLatestTime[fid] = log.timestampMillis
                    if (log.timestampMillis > maxTimestamp) {
                        maxTimestamp = log.timestampMillis
                    }
                }
            }
        }
        return MealUsageStats(
            mealSpecificCounts = mealSpecificCounts,
            mealSpecificLatestTime = mealSpecificLatestTime,
            globalCounts = globalCounts,
            maxTimestamp = maxTimestamp,
        )
    }

    /**
     * Ranks foods specifically for the active [mealType], prioritizing:
     * 1. Recent & frequent items logged in this specific [mealType]
     * 2. User favorites & custom foods
     * 3. Natural meal affinity ([FoodItem.preferredMealTypes])
     * 4. Overall usage frequency
     */
    fun rankFoodsForMeal(
        allFoods: List<FoodItem>,
        historicalLogs: List<MealLogItem>,
        mealType: MealType,
        query: String,
        selectedCategory: FoodCategory,
    ): List<FoodItem> = rankFoodsWithStats(
        allFoods = allFoods,
        stats = prepareMealUsageStats(historicalLogs, mealType),
        mealType = mealType,
        query = query,
        selectedCategory = selectedCategory,
    )

    fun rankFoodsWithStats(
        allFoods: List<FoodItem>,
        stats: MealUsageStats,
        mealType: MealType,
        query: String,
        selectedCategory: FoodCategory,
    ): List<FoodItem> {
        val normQuery = if (query.isBlank()) "" else TurkishTextNormalizer.normalize(query)
        val queryTokens = if (normQuery.isEmpty()) emptyList() else TurkishTextNormalizer.tokenizeNormalized(normQuery)
        val mealSpecificCounts = stats.mealSpecificCounts
        val mealSpecificLatestTime = stats.mealSpecificLatestTime
        val globalCounts = stats.globalCounts
        val maxTimestampDouble = stats.maxTimestamp.toDouble()
        val hasMaxTimestamp = stats.maxTimestamp > 0L

        val scored = ArrayList<ScoredFood>(if (queryTokens.isEmpty()) allFoods.size else (allFoods.size / 4).coerceAtLeast(16))
        for (i in allFoods.indices) {
            val food = allFoods[i]
            val categoryMatch = selectedCategory == FoodCategory.ALL ||
                (selectedCategory == FoodCategory.CUSTOM && (food.isCustom || food.isFavorite)) ||
                food.category == selectedCategory
            if (!categoryMatch) continue

            if (queryTokens.isNotEmpty()) {
                val normSearch = food.normalizedSearchText.ifBlank {
                    TurkishTextNormalizer.normalize("${food.name} ${food.subtitle}")
                }
                if (!TurkishTextNormalizer.matchesNormalizedTokens(normSearch, queryTokens)) continue
            }

            val mealFreq = mealSpecificCounts[food.id] ?: 0
            val globalFreq = globalCounts[food.id] ?: 0
            val latestTs = mealSpecificLatestTime[food.id] ?: 0L
            val recencyRatio = if (latestTs > 0L && hasMaxTimestamp) {
                latestTs.toDouble() / maxTimestampDouble
            } else {
                0.0
            }

            var score = 0.0
            score += mealFreq * 28.0
            score += recencyRatio * 18.0
            score += globalFreq * 5.0
            if (food.isFavorite) score += 22.0
            if (food.preferredMealTypes.contains(mealType)) score += 14.0
            if (normQuery.isNotEmpty()) {
                val normName = food.normalizedName.ifBlank {
                    TurkishTextNormalizer.normalize(food.name)
                }
                when {
                    normName == normQuery -> score += 60.0
                    normName.startsWith(normQuery) -> score += 35.0
                    TurkishTextNormalizer.matchesNormalizedTokens(normName, queryTokens) -> score += 18.0
                }
                if (food.brand == null && !food.isCustom) {
                    score += 4.0
                }
            }
            scored.add(ScoredFood(food, score))
        }

        scored.sortWith { a, b ->
            val cmp = b.score.compareTo(a.score)
            if (cmp != 0) cmp else a.food.name.compareTo(b.food.name)
        }
        val result = ArrayList<FoodItem>(scored.size)
        for (i in scored.indices) {
            result.add(scored[i].food)
        }
        return result
    }

    /**
     * Mines co-occurrence patterns from the user's historical meal baskets and current meal items
     * to suggest frequently paired foods that can be logged together in a single tap.
     */
    fun buildSmartComboSuggestions(
        mealType: MealType,
        currentDateMealItems: List<MealLogItem>,
        historicalLogs: List<MealLogItem>,
        catalogFoods: List<FoodItem>,
    ): List<SmartComboSuggestion> {
        val normCache = HashMap<String, String>(64)
        fun cachedNorm(text: String): String =
            normCache.getOrPut(text) { TurkishTextNormalizer.normalize(text) }

        val currentFoodNames = HashSet<String>(currentDateMealItems.size.coerceAtLeast(4))
        for (i in currentDateMealItems.indices) {
            val item = currentDateMealItems[i]
            if (item.mealType == mealType) {
                currentFoodNames.add(cachedNorm(item.foodName))
            }
        }

        val suggestions = ArrayList<SmartComboSuggestion>(2)

        // 1. Mine historical baskets for this MealType grouped by dateIso
        val basketsByDate = LinkedHashMap<String, MutableList<MealLogItem>>()
        for (i in historicalLogs.indices) {
            val log = historicalLogs[i]
            if (log.mealType == mealType) {
                basketsByDate.getOrPut(log.dateIso) { ArrayList(4) }.add(log)
            }
        }

        if (basketsByDate.isNotEmpty()) {
            val signatureMap = LinkedHashMap<String, HistoricalBasketGroup>()
            val seenNorms = HashSet<String>(8)
            val keyBuilder = StringBuilder(64)

            for (basket in basketsByDate.values) {
                if (basket.size < 2) continue
                seenNorms.clear()
                val distinctItems = ArrayList<MealLogItem>(basket.size)
                var basketMaxTs = Long.MIN_VALUE
                for (i in basket.indices) {
                    val item = basket[i]
                    if (seenNorms.add(cachedNorm(item.foodName))) {
                        distinctItems.add(item)
                        if (item.timestampMillis > basketMaxTs) {
                            basketMaxTs = item.timestampMillis
                        }
                    }
                }
                if (distinctItems.size >= 2) {
                    distinctItems.sortBy { it.foodName }
                    keyBuilder.setLength(0)
                    for (i in distinctItems.indices) {
                        if (i > 0) keyBuilder.append('|')
                        keyBuilder.append(cachedNorm(distinctItems[i].foodName))
                    }
                    val key = keyBuilder.toString()
                    val existing = signatureMap[key]
                    if (existing == null) {
                        signatureMap[key] = HistoricalBasketGroup(
                            sampleBasket = distinctItems,
                            count = 1,
                            latestTimestamp = basketMaxTs,
                        )
                    } else {
                        existing.count++
                        if (basketMaxTs > existing.latestTimestamp) {
                            existing.latestTimestamp = basketMaxTs
                        }
                    }
                }
            }

            val topHistoricalBaskets = ArrayList(signatureMap.values)
            topHistoricalBaskets.sortWith { a, b ->
                val countCmp = b.count.compareTo(a.count)
                if (countCmp != 0) countCmp else b.latestTimestamp.compareTo(a.latestTimestamp)
            }

            val limit = topHistoricalBaskets.size.coerceAtMost(2)
            for (index in 0 until limit) {
                val group = topHistoricalBaskets[index]
                val sampleBasket = group.sampleBasket
                val missingItems = ArrayList<MealLogItem>(sampleBasket.size)
                for (i in sampleBasket.indices) {
                    val item = sampleBasket[i]
                    if (cachedNorm(item.foodName) !in currentFoodNames) {
                        missingItems.add(item)
                    }
                }
                if (missingItems.size >= 2) {
                    val count = group.count
                    val title = missingItems.take(3).joinToString(" + ") { it.foodName }
                    val subtitle = if (count >= 2) {
                        "${mealType.title} öğününde $count kez birlikte tükettiniz"
                    } else {
                        "Son ${NutraFormatters.lowercaseTr(mealType.title)} kaydınızdan hızlı kombin"
                    }
                    suggestions.add(
                        SmartComboSuggestion(
                            id = "hist_${mealType.name}_$index",
                            title = title,
                            subtitle = subtitle,
                            items = missingItems.map { log ->
                                ComboFoodEntry(
                                    foodId = log.foodId,
                                    foodName = log.foodName,
                                    unitLabel = log.unitLabel,
                                    multiplier = log.multiplier,
                                    totalGrams = log.totalGrams,
                                    calories = log.calories,
                                    proteinGrams = log.proteinGrams,
                                    carbsGrams = log.carbsGrams,
                                    fatGrams = log.fatGrams,
                                    baseUnit = log.baseUnit,
                                )
                            },
                            coOccurrenceCount = count,
                        ),
                    )
                }
            }
        }

        if (suggestions.size >= 2) return suggestions.take(2)

        // 2. Contextual Companion Pairing (prefer exact normalized name matches over substring matches)
        val blueprintSpecs = BLUEPRINTS_BY_MEAL[mealType].orEmpty()

        val keywordsNeeded = HashSet<String>(8)
        for (spec in blueprintSpecs) keywordsNeeded.addAll(spec.third)
        val exactMatchedByKeyword = HashMap<String, FoodItem>(keywordsNeeded.size)
        val partialMatchedByKeyword = HashMap<String, FoodItem>(keywordsNeeded.size)
        for (i in catalogFoods.indices) {
            if (exactMatchedByKeyword.size == keywordsNeeded.size) break
            val food = catalogFoods[i]
            val normName = food.normalizedName.ifBlank { cachedNorm(food.name) }
            for (kw in keywordsNeeded) {
                if (!exactMatchedByKeyword.containsKey(kw)) {
                    if (normName == kw) {
                        exactMatchedByKeyword[kw] = food
                    } else if (!partialMatchedByKeyword.containsKey(kw) && normName.contains(kw)) {
                        partialMatchedByKeyword[kw] = food
                    }
                }
            }
        }

        for ((idx, blueprint) in blueprintSpecs.withIndex()) {
            if (suggestions.size >= 2) break
            val validFoods = blueprint.third.mapNotNull { kw ->
                val food = exactMatchedByKeyword[kw] ?: partialMatchedByKeyword[kw] ?: return@mapNotNull null
                val normName = food.normalizedName.ifBlank { cachedNorm(food.name) }
                if (normName in currentFoodNames) null else food
            }
            if (validFoods.size >= 2) {
                val comboEntries = validFoods.map { food ->
                    val grams = food.defaultUnitGrams
                    ComboFoodEntry(
                        foodId = food.id,
                        foodName = food.name,
                        unitLabel = food.defaultUnitLabel,
                        multiplier = 1f,
                        totalGrams = grams,
                        calories = food.caloriesForGrams(grams),
                        proteinGrams = food.proteinForGrams(grams),
                        carbsGrams = food.carbsForGrams(grams),
                        fatGrams = food.fatForGrams(grams),
                        baseUnit = food.displayUnitSuffix,
                    )
                }
                val alreadySuggestedSameTitle = suggestions.any { it.title == blueprint.first }
                if (!alreadySuggestedSameTitle) {
                    suggestions.add(
                        SmartComboSuggestion(
                            id = "curated_${mealType.name}_$idx",
                            title = blueprint.first,
                            subtitle = blueprint.second,
                            items = comboEntries,
                            coOccurrenceCount = 1,
                        ),
                    )
                }
            }
        }

        return suggestions.take(2)
    }

    private companion object {
        val BLUEPRINTS_BY_MEAL: Map<MealType, List<Triple<String, String, List<String>>>> = mapOf(
            MealType.BREAKFAST to listOf(
                Triple(
                    "Klasik Dengeli Kahvaltı Kombini",
                    "Birlikte sık tüketilen yüksek proteinli kahvaltı üçlüsü",
                    listOf("haslanmis yumurta", "ezine peyniri", "ekmek tam bugday unlu"),
                ),
                Triple(
                    "Yulaf & Yoğurt Enerji Kasesi",
                    "Pratik sabah kombinasyonu",
                    listOf("yulaf ezmesi", "yogurt suzme burdur", "ceviz"),
                ),
            ),
            MealType.LUNCH to listOf(
                Triple(
                    "Izgara Tavuk & Bulgur Menü",
                    "Öğle öğününde sıkça beraber tercih edilen dengeli tabak",
                    listOf("tavuk gogus derisiz izgara", "bulgur pilavi", "ayran"),
                ),
                Triple(
                    "Mercimek Çorbası & Çoban Salata",
                    "Hafif ve doyurucu öğle ikilisi",
                    listOf("mercimek corbasi", "coban salatasi"),
                ),
            ),
            MealType.DINNER to listOf(
                Triple(
                    "Izgara Köfte & Salata Menü",
                    "Akşam öğününde sıkça birlikte tüketilen klasik kombin",
                    listOf("izgara kofte", "coban salatasi", "ayran"),
                ),
                Triple(
                    "Ev Yemeği & Yoğurt Kombini",
                    "Dengeli akşam yemeği üçlüsü",
                    listOf("kuru fasulye etli", "pirinc pilavi sivi yagli", "yogurt tam yagli"),
                ),
            ),
            MealType.SNACK to listOf(
                Triple(
                    "Elma & Badem Ara Öğünü",
                    "Kan şekerini dengeleyen sık tüketilen ikili",
                    listOf("elma", "badem"),
                ),
                Triple(
                    "Muz & Kefir Ara Öğünü",
                    "Pratik ve ferah ara öğün ikilisi",
                    listOf("muz", "kefir"),
                ),
            ),
        )
    }
}
