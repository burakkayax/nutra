package com.nutra.app.domain.usecase

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
        val normQuery = TurkishTextNormalizer.normalize(query)
        val queryTokens = TurkishTextNormalizer.tokenizeNormalized(normQuery)
        val mealSpecificCounts = stats.mealSpecificCounts
        val mealSpecificLatestTime = stats.mealSpecificLatestTime
        val globalCounts = stats.globalCounts
        val maxTimestamp = stats.maxTimestamp

        val scored = ArrayList<ScoredFood>(allFoods.size)
        for (food in allFoods) {
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
            val recencyRatio = if (latestTs > 0L && maxTimestamp > 0L) {
                latestTs.toDouble() / maxTimestamp.toDouble()
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
                if (normName.startsWith(normQuery)) {
                    score += 35.0
                }
            }
            scored.add(ScoredFood(food, score))
        }

        scored.sortWith { a, b ->
            val cmp = b.score.compareTo(a.score)
            if (cmp != 0) cmp else a.food.name.compareTo(b.food.name)
        }
        return scored.map { it.food }
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

        val currentFoodNames = HashSet<String>()
        for (item in currentDateMealItems) {
            if (item.mealType == mealType) {
                currentFoodNames.add(cachedNorm(item.foodName))
            }
        }

        val suggestions = mutableListOf<SmartComboSuggestion>()

        // 1. Mine historical baskets for this MealType grouped by dateIso
        val basketsByDate = LinkedHashMap<String, MutableList<MealLogItem>>()
        for (log in historicalLogs) {
            if (log.mealType == mealType) {
                basketsByDate.getOrPut(log.dateIso) { mutableListOf() }.add(log)
            }
        }

        if (basketsByDate.isNotEmpty()) {
            val signatureMap = LinkedHashMap<String, MutableList<List<MealLogItem>>>()
            for (basket in basketsByDate.values) {
                if (basket.size < 2) continue
                val distinctItems = basket
                    .distinctBy { cachedNorm(it.foodName) }
                    .sortedBy { it.foodName }
                if (distinctItems.size >= 2) {
                    val key = distinctItems.joinToString("|") { cachedNorm(it.foodName) }
                    signatureMap.getOrPut(key) { mutableListOf() }.add(distinctItems)
                }
            }

            val topHistoricalBaskets = signatureMap.entries
                .sortedWith(
                    compareByDescending<Map.Entry<String, MutableList<List<MealLogItem>>>> { it.value.size }
                        .thenByDescending { entry -> entry.value.first().maxOf { it.timestampMillis } },
                )
                .take(2)

            for ((index, entry) in topHistoricalBaskets.withIndex()) {
                val sampleBasket = entry.value.first()
                val missingItems = sampleBasket.filter {
                    cachedNorm(it.foodName) !in currentFoodNames
                }
                if (missingItems.size >= 2) {
                    val count = entry.value.size
                    val title = missingItems.take(3).joinToString(" + ") { it.foodName }
                    val subtitle = if (count >= 2) {
                        "${mealType.title} öğününde $count kez birlikte tükettiniz"
                    } else {
                        "Son ${mealType.title.lowercase()} kaydınızdan hızlı kombin"
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
                                )
                            },
                            coOccurrenceCount = count,
                        ),
                    )
                }
            }
        }

        if (suggestions.size >= 2) return suggestions.take(2)

        // 2. Contextual Companion Pairing (single-pass keyword match over pre-normalized food names)
        val blueprintSpecs: List<Triple<String, String, List<String>>> = when (mealType) {
            MealType.BREAKFAST -> listOf(
                Triple(
                    "Klasik Dengeli Kahvaltı Kombini",
                    "Birlikte sık tüketilen yüksek proteinli kahvaltı üçlüsü",
                    listOf("haslanmis yumurta", "ezine beyaz peynir", "tam bugday ekmegi"),
                ),
                Triple(
                    "Yulaf & Yoğurt Enerji Kasesi",
                    "Pratik sabah kombinasyonu",
                    listOf("yulaf ezmesi", "suzme yogurt", "ceviz ici"),
                ),
            )
            MealType.LUNCH -> listOf(
                Triple(
                    "Izgara Tavuk & Bulgur Menü",
                    "Öğle öğününde sıkça beraber tercih edilen dengeli tabak",
                    listOf("izgara tavuk gogsu", "bulgur pilavi", "ayran"),
                ),
                Triple(
                    "Mercimek Çorbası & Mevsim Salata",
                    "Hafif ve doyurucu öğle ikilisi",
                    listOf("mercimek corbasi", "coban salata"),
                ),
            )
            MealType.DINNER -> listOf(
                Triple(
                    "Izgara Köfte & Piyaz / Salata",
                    "Akşam öğününde sıkça birlikte tüketilen klasik kombin",
                    listOf("izgara kofte", "coban salata", "ayran"),
                ),
                Triple(
                    "Zeytinyağlı Ev Yemeği & Yoğurt",
                    "Dengeli akşam yemeği ikilisi",
                    listOf("kuru fasulye", "pirinc pilavi", "suzme yogurt"),
                ),
            )
            MealType.SNACK -> listOf(
                Triple(
                    "Meyve & Çiğ Badem Ara Öğünü",
                    "Kan şekerini dengeleyen sık tüketilen ikili",
                    listOf("yesil elma", "cig badem"),
                ),
            )
        }

        val keywordsNeeded = HashSet<String>(8)
        for (spec in blueprintSpecs) keywordsNeeded.addAll(spec.third)
        val matchedByKeyword = HashMap<String, FoodItem>(keywordsNeeded.size)
        for (food in catalogFoods) {
            if (matchedByKeyword.size == keywordsNeeded.size) break
            val normName = food.normalizedName.ifBlank { cachedNorm(food.name) }
            for (kw in keywordsNeeded) {
                if (!matchedByKeyword.containsKey(kw) && normName.contains(kw)) {
                    matchedByKeyword[kw] = food
                }
            }
        }

        for ((idx, blueprint) in blueprintSpecs.withIndex()) {
            if (suggestions.size >= 2) break
            val validFoods = blueprint.third.mapNotNull { kw ->
                val food = matchedByKeyword[kw] ?: return@mapNotNull null
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
}
