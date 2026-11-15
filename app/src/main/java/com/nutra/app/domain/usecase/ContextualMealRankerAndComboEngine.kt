package com.nutra.app.domain.usecase

import com.nutra.app.domain.model.ComboFoodEntry
import com.nutra.app.domain.model.FoodCategory
import com.nutra.app.domain.model.FoodItem
import com.nutra.app.domain.model.MealLogItem
import com.nutra.app.domain.model.MealType
import com.nutra.app.domain.model.SmartComboSuggestion
import javax.inject.Inject

class ContextualMealRankerAndComboEngine @Inject constructor() {

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
    ): List<FoodItem> {
        val filtered = allFoods.filter { food ->
            val categoryMatch = selectedCategory == FoodCategory.ALL ||
                (selectedCategory == FoodCategory.CUSTOM && (food.isCustom || food.isFavorite)) ||
                food.category == selectedCategory
            val queryMatch = query.isBlank() ||
                TurkishTextNormalizer.matchesQuery("${food.name} ${food.subtitle}", query)
            categoryMatch && queryMatch
        }

        val mealSpecificCounts = HashMap<Long, Int>()
        val mealSpecificLatestTime = HashMap<Long, Long>()
        val globalCounts = HashMap<Long, Int>()

        for (log in historicalLogs) {
            val fid = log.foodId ?: continue
            globalCounts[fid] = (globalCounts[fid] ?: 0) + 1
            if (log.mealType == mealType) {
                mealSpecificCounts[fid] = (mealSpecificCounts[fid] ?: 0) + 1
                val prevLatest = mealSpecificLatestTime[fid] ?: 0L
                if (log.timestampMillis > prevLatest) {
                    mealSpecificLatestTime[fid] = log.timestampMillis
                }
            }
        }

        val maxTimestamp = mealSpecificLatestTime.values.maxOrNull() ?: 1L

        return filtered.sortedWith(
            compareByDescending<FoodItem> { food ->
                val mealFreq = mealSpecificCounts[food.id] ?: 0
                val globalFreq = globalCounts[food.id] ?: 0
                val latestTs = mealSpecificLatestTime[food.id] ?: 0L
                val recencyRatio = if (latestTs > 0L && maxTimestamp > 0L) {
                    (latestTs.toDouble() / maxTimestamp.toDouble())
                } else {
                    0.0
                }

                var score = 0.0
                score += mealFreq * 28.0
                score += recencyRatio * 18.0
                score += globalFreq * 5.0
                if (food.isFavorite) score += 22.0
                if (food.preferredMealTypes.contains(mealType)) score += 14.0
                if (query.isNotBlank() && TurkishTextNormalizer.normalize(food.name)
                        .startsWith(TurkishTextNormalizer.normalize(query))
                ) {
                    score += 35.0
                }
                score
            }.thenBy { it.name },
        )
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
        val currentFoodNames = currentDateMealItems
            .filter { it.mealType == mealType }
            .map { TurkishTextNormalizer.normalize(it.foodName) }
            .toSet()

        val suggestions = mutableListOf<SmartComboSuggestion>()

        // 1. Mine historical baskets for this MealType grouped by dateIso
        val basketsByDate = historicalLogs
            .filter { it.mealType == mealType }
            .groupBy { it.dateIso }
            .values
            .filter { it.size >= 2 }

        if (basketsByDate.isNotEmpty()) {
            // Find the most frequent multi-item combination or companion items
            val signatureMap = LinkedHashMap<String, MutableList<List<MealLogItem>>>()
            for (basket in basketsByDate) {
                val distinctItems = basket
                    .distinctBy { TurkishTextNormalizer.normalize(it.foodName) }
                    .sortedBy { it.foodName }
                if (distinctItems.size >= 2) {
                    val key = distinctItems.joinToString("|") { TurkishTextNormalizer.normalize(it.foodName) }
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
                    TurkishTextNormalizer.normalize(it.foodName) !in currentFoodNames
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

        // 2. Contextual Companion Pairing (if user already added an anchor food or needs curated smart combos)
        val foodByNormName = catalogFoods.associateBy { TurkishTextNormalizer.normalize(it.name) }

        fun findCatalogFood(keyword: String): FoodItem? =
            catalogFoods.firstOrNull { TurkishTextNormalizer.normalize(it.name).contains(keyword) }

        val curatedBlueprints: List<Triple<String, String, List<FoodItem?>>> = when (mealType) {
            MealType.BREAKFAST -> listOf(
                Triple(
                    "Klasik Dengeli Kahvaltı Kombini",
                    "Birlikte sık tüketilen yüksek proteinli kahvaltı üçlüsü",
                    listOf(
                        findCatalogFood("haslanmis yumurta"),
                        findCatalogFood("ezine beyaz peynir"),
                        findCatalogFood("tam bugday ekmegi"),
                    ),
                ),
                Triple(
                    "Yulaf & Yoğurt Enerji Kasesi",
                    "Pratik sabah kombinasyonu",
                    listOf(
                        findCatalogFood("yulaf ezmesi"),
                        findCatalogFood("suzme yogurt"),
                        findCatalogFood("ceviz ici"),
                    ),
                ),
            )
            MealType.LUNCH -> listOf(
                Triple(
                    "Izgara Tavuk & Bulgur Menü",
                    "Öğle öğününde sıkça beraber tercih edilen dengeli tabak",
                    listOf(
                        findCatalogFood("izgara tavuk gogsu"),
                        findCatalogFood("bulgur pilavi"),
                        findCatalogFood("ayran"),
                    ),
                ),
                Triple(
                    "Mercimek Çorbası & Mevsim Salata",
                    "Hafif ve doyurucu öğle ikilisi",
                    listOf(
                        findCatalogFood("mercimek corbasi"),
                        findCatalogFood("coban salata"),
                    ),
                ),
            )
            MealType.DINNER -> listOf(
                Triple(
                    "Izgara Köfte & Piyaz / Salata",
                    "Akşam öğününde sıkça birlikte tüketilen klasik kombin",
                    listOf(
                        findCatalogFood("izgara kofte"),
                        findCatalogFood("coban salata"),
                        findCatalogFood("ayran"),
                    ),
                ),
                Triple(
                    "Zeytinyağlı Ev Yemeği & Yoğurt",
                    "Dengeli akşam yemeği ikilisi",
                    listOf(
                        findCatalogFood("kuru fasulye"),
                        findCatalogFood("pirinc pilavi"),
                        findCatalogFood("suzme yogurt"),
                    ),
                ),
            )
            MealType.SNACK -> listOf(
                Triple(
                    "Meyve & Çiğ Badem Ara Öğünü",
                    "Kan şekerini dengeleyen sık tüketilen ikili",
                    listOf(
                        findCatalogFood("yesil elma"),
                        findCatalogFood("cig badem"),
                    ),
                ),
            )
        }

        for ((idx, blueprint) in curatedBlueprints.withIndex()) {
            if (suggestions.size >= 2) break
            val validFoods = blueprint.third.filterNotNull().filter {
                TurkishTextNormalizer.normalize(it.name) !in currentFoodNames
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
