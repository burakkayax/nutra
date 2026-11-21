package com.nutra.app.domain.intelligence

import com.nutra.app.core.designsystem.NutraFormatters
import com.nutra.app.domain.model.AllergenTag
import com.nutra.app.domain.model.DailyNutritionSummary
import com.nutra.app.domain.model.DietStyle
import com.nutra.app.domain.model.FoodFeedbackRecord
import com.nutra.app.domain.model.FoodItem
import com.nutra.app.domain.model.MealLogItem
import com.nutra.app.domain.model.MealType
import com.nutra.app.domain.model.UserProfile
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Offline, explainable Nutra Intelligence meal recommendation engine.
 *
 * Combines:
 * 1. Remaining daily & per-meal calorie/macro budget planning.
 * 2. Culinary archetype templates tailored to Turkish cuisine & modern dietary styles.
 * 3. Habit learning (with honest cold-start fallback) and missing-data-safe micronutrient awareness.
 * 4. Box-constrained portion optimization via [PortionOptimizer].
 * 5. Maximal Marginal Relevance (MMR) diversity selection so recommendations stay varied and practical.
 */
class MealRecommendationEngine @Inject constructor(
    private val classifier: FoodCulinaryClassifier,
    private val profileLearner: UserEatingProfileLearner,
    private val portionOptimizer: PortionOptimizer,
) {

    fun inferActiveMealType(
        dailySummary: DailyNutritionSummary,
        nowTime: LocalTime = LocalTime.now(),
    ): MealType {
        val loggedTypes = dailySummary.meals
            .filter { it.items.isNotEmpty() }
            .map { it.mealType }
            .toSet()

        val hour = nowTime.hour
        val naturalByHour = when {
            hour < 11 -> MealType.BREAKFAST
            hour < 15 -> MealType.LUNCH
            hour < 17 -> MealType.SNACK
            hour < 22 -> MealType.DINNER
            else -> MealType.SNACK
        }
        if (naturalByHour !in loggedTypes) return naturalByHour
        return when {
            MealType.BREAKFAST !in loggedTypes && hour < 13 -> MealType.BREAKFAST
            MealType.LUNCH !in loggedTypes && hour < 17 -> MealType.LUNCH
            MealType.DINNER !in loggedTypes -> MealType.DINNER
            else -> MealType.SNACK
        }
    }

    fun generateRecommendations(
        profile: UserProfile,
        dailySummary: DailyNutritionSummary,
        mealType: MealType,
        catalogFoods: List<FoodItem>,
        historicalLogs: List<MealLogItem>,
        feedbackRecords: List<FoodFeedbackRecord> = emptyList(),
        today: LocalDate = LocalDate.now(),
        variationSeed: Int = 0,
        maxResults: Int = 4,
    ): IntelligenceReport {
        val classifiedAll = classifier.classifyCatalog(catalogFoods)
        val eatingProfile = profileLearner.learn(
            profile = profile,
            classifiedCatalog = classifiedAll,
            historicalLogs = historicalLogs,
            feedbackRecords = feedbackRecords,
            today = today,
        )
        val budget = calculateMealBudget(
            dailySummary = dailySummary,
            mealType = mealType,
            eatingProfile = eatingProfile,
        )

        val feedbackByKey = feedbackRecords.associateBy { it.foodKey }
        val allowed = classifiedAll.filter { cf ->
            classifier.isAllowedByPreferences(cf, profile.dietaryProfile, feedbackByKey)
        }

        // Identify active missing-data-safe micronutrient priorities (fiber low, sodium elevated, etc.)
        val lowFiberActive = eatingProfile.micronutrientSignals.any {
            it.type == MicronutrientType.FIBER && it.status == NutrientSignalStatus.LOW_RECORDED
        }
        val highSodiumActive = eatingProfile.micronutrientSignals.any {
            it.type == MicronutrientType.SODIUM && it.status == NutrientSignalStatus.ELEVATED_RECORDED
        }
        val lowMicronutrients = eatingProfile.micronutrientSignals
            .filter { it.status == NutrientSignalStatus.LOW_RECORDED }
            .map { it.type }
            .toSet()

        val prioritizedMicroNote = when {
            lowFiberActive ->
                "Son günlerde kayıtlı lif alımın referansın altında görünüyor — lif katkısı yüksek öğünlere öncelik verildi."
            highSodiumActive ->
                "Son günlerde kayıtlı sodyum alımın yüksek seyrediyor — daha taze ve düşük sodyumlu seçenekler öne çıkarıldı."
            lowMicronutrients.isNotEmpty() -> {
                val names = lowMicronutrients.take(2).joinToString(" ve ") { it.title.lowercase() }
                "Son günlerde kayıtlı $names alımın düşük görünüyor — bu besinleri içeren dengeli seçenekler desteklendi."
            }
            else -> null
        }

        val coldStartNote = when (eatingProfile.maturity) {
            HabitMaturity.NONE ->
                "Henüz yeterli öğün geçmişin olmadığı için kişisel alışkanlık varsayımı yapılmadı; hedeflerine ve beslenme tercihlerine göre dengeli Türk mutfağı seçenekleri oluşturuldu."
            HabitMaturity.EARLY ->
                "İlk ${eatingProfile.loggedDaysCount} günlük kayıtlarından öğrenmeye başlandı; düzenli kayıt yaptıkça öneriler daha fazla kişiselleşir."
            HabitMaturity.ESTABLISHED -> null
        }

        if (allowed.size < 2) {
            return IntelligenceReport(
                mealType = mealType,
                budget = budget,
                eatingProfile = eatingProfile,
                recommendations = emptyList(),
                prioritizedMicronutrientNote = prioritizedMicroNote,
                coldStartNote = "Seçili alerjen/diyet filtreleriyle uyumlu yeterli besin bulunamadı. Profil → Beslenme tercihlerinden filtrelerini gözden geçirebilirsin.",
            )
        }

        // Partition allowed foods by culinary role and meal affinity
        val mealPreferred = allowed.filter { mealType in it.food.preferredMealTypes || it.food.preferredMealTypes.isEmpty() }
        val pool = if (mealPreferred.size >= 18) mealPreferred else allowed

        val scoredByRole = buildRoleCandidatePools(
            pool = pool,
            allAllowed = allowed,
            mealType = mealType,
            dietStyle = profile.dietaryProfile.dietStyle,
            eatingProfile = eatingProfile,
            lowMicronutrients = lowMicronutrients,
            highSodiumActive = highSodiumActive,
            variationSeed = variationSeed,
        )

        val rawCandidates = assembleArchetypeCandidates(
            mealType = mealType,
            budget = budget,
            dietStyle = profile.dietaryProfile.dietStyle,
            scoredByRole = scoredByRole,
            allowedById = allowed.associateBy { it.food.id },
            eatingProfile = eatingProfile,
            lowFiberActive = lowFiberActive,
            variationSeed = variationSeed,
        )

        val evaluated = ArrayList<ScoredRecommendationCandidate>(rawCandidates.size)
        val seenSignatures = HashSet<String>(rawCandidates.size)

        for ((idx, raw) in rawCandidates.withIndex()) {
            val distinctFoods = raw.foods.distinctBy { it.food.id }
            if (distinctFoods.size < 2) continue
            val sig = distinctFoods.map { it.food.id }.sorted().joinToString("-")
            if (!seenSignatures.add(sig)) continue

            val opt = portionOptimizer.optimizePortions(
                foods = distinctFoods,
                budget = budget,
                usualGramsByFoodId = eatingProfile.usualGramsByFoodId,
            )
            if (opt.items.isEmpty() || opt.totalCalories <= 0) continue

            val rec = buildRecommendationModel(
                id = "intel_${mealType.name}_${variationSeed}_$idx",
                mealType = mealType,
                raw = raw,
                opt = opt,
                budget = budget,
                dailySummary = dailySummary,
                eatingProfile = eatingProfile,
                lowMicronutrients = lowMicronutrients,
                highSodiumActive = highSodiumActive,
                classifiedMap = distinctFoods.associateBy { it.food.id },
            )
            val totalScore = computeCompositeCandidateScore(
                rec = rec,
                foods = distinctFoods,
                eatingProfile = eatingProfile,
                dietStyle = profile.dietaryProfile.dietStyle,
                lowMicronutrients = lowMicronutrients,
                highSodiumActive = highSodiumActive,
                isHabitAdapted = raw.isHabitAdapted,
            )
            evaluated.add(ScoredRecommendationCandidate(rec, distinctFoods, totalScore))
        }

        // Maximal Marginal Relevance (MMR) selection for genuine variety
        val selected = selectDiverseTopRecommendations(evaluated, maxResults)

        return IntelligenceReport(
            mealType = mealType,
            budget = budget,
            eatingProfile = eatingProfile,
            recommendations = selected,
            prioritizedMicronutrientNote = prioritizedMicroNote,
            coldStartNote = coldStartNote,
        )
    }

    fun optimizeCustomCombo(
        selectedFoods: List<FoodItem>,
        budget: MealBudget,
        usualGramsByFoodId: Map<Long, Float> = emptyMap(),
    ): CustomPortionOptimizationResult {
        val classified = selectedFoods.map { classifier.classifySingle(it) }
        return portionOptimizer.optimizePortions(
            foods = classified,
            budget = budget,
            usualGramsByFoodId = usualGramsByFoodId,
        )
    }

    fun calculateMealBudget(
        dailySummary: DailyNutritionSummary,
        mealType: MealType,
        eatingProfile: UserEatingProfile,
    ): MealBudget {
        val goalCal = dailySummary.goals.calories.coerceAtLeast(1000)
        val goalP = dailySummary.goals.proteinGrams.coerceAtLeast(30).toFloat()
        val goalC = dailySummary.goals.carbsGrams.coerceAtLeast(20).toFloat()
        val goalF = dailySummary.goals.fatGrams.coerceAtLeast(20).toFloat()

        val remCal = goalCal - dailySummary.consumedCalories
        val remP = (goalP - dailySummary.consumedProtein).coerceAtLeast(0f)
        val remC = (goalC - dailySummary.consumedCarbs).coerceAtLeast(0f)
        val remF = (goalF - dailySummary.consumedFat).coerceAtLeast(0f)

        // Already over or very close to daily target -> honest light option budget
        if (remCal < 140) {
            val lightCal = if (mealType == MealType.SNACK) 160 else 280
            val lightP = max(14f, min(remP, 32f))
            val lightC = ((lightCal * 0.35f) / 4f).coerceAtLeast(10f)
            val lightF = ((lightCal * 0.25f) / 9f).coerceAtLeast(4f)
            return MealBudget(
                mealType = mealType,
                targetCalories = lightCal,
                targetProteinGrams = round1(lightP),
                targetCarbsGrams = round1(lightC),
                targetFatGrams = round1(lightF),
                dailyRemainingCalories = remCal,
                dailyRemainingProtein = round1(remP),
                dailyRemainingCarbs = round1(remC),
                dailyRemainingFat = round1(remF),
                mode = MealBudgetMode.LIGHT_OVER_BUDGET,
                budgetSummaryText = if (remCal <= 0) {
                    "Günlük kalori hedefin doldu (${NutraFormatters.formatInt(abs(remCal))} kcal fazla). Hafif ve doyurucu seçenekler listelendi."
                } else {
                    "Günlük hedefine çok yakınsın (${NutraFormatters.formatInt(remCal)} kcal kaldı). Hafif seçenekler listelendi."
                },
            )
        }

        // Determine unlogged future meals for the rest of the day
        val loggedMeals = dailySummary.meals.filter { it.items.isNotEmpty() }.map { it.mealType }.toSet()
        val expectedRemainingMainMeals = when (mealType) {
            MealType.BREAKFAST -> listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER, MealType.SNACK)
            MealType.LUNCH -> listOf(MealType.LUNCH, MealType.DINNER, MealType.SNACK)
            MealType.DINNER -> listOf(MealType.DINNER)
            MealType.SNACK -> listOf(MealType.SNACK)
        }.filter { it == mealType || it !in loggedMeals }

        val shareOfThisMeal = if (mealType == MealType.DINNER || expectedRemainingMainMeals.size <= 1) {
            // Dinner or last remaining meal gets the full remaining budget (capped sensibly for a single meal)
            1.0f
        } else {
            val thisWeight = eatingProfile.mealCalorieShares[mealType] ?: 0.30f
            val remWeightSum = expectedRemainingMainMeals
                .sumOf { (eatingProfile.mealCalorieShares[it] ?: 0.25f).toDouble() }
                .toFloat()
                .coerceAtLeast(thisWeight)
            (thisWeight / remWeightSum).coerceIn(0.20f, 0.80f)
        }

        val maxSingleMealCap = when (mealType) {
            MealType.SNACK -> min(remCal, (goalCal * 0.24f).roundToInt().coerceIn(220, 480))
            else -> min(remCal, (goalCal * 0.55f).roundToInt().coerceIn(500, 1250))
        }
        val minSingleMealFloor = if (mealType == MealType.SNACK) 120 else 260

        val allocatedCal = (remCal * shareOfThisMeal).roundToInt()
            .coerceIn(min(remCal, minSingleMealFloor), maxSingleMealCap)
        val calRatio = (allocatedCal.toFloat() / remCal.toFloat()).coerceIn(0.15f, 1f)

        val targetP = (remP * calRatio).coerceIn(
            if (mealType == MealType.SNACK) 6f else 18f,
            max(remP, if (mealType == MealType.SNACK) 25f else 65f),
        )
        val targetC = (remC * calRatio).coerceIn(10f, max(remC, 120f))
        val targetF = (remF * calRatio).coerceIn(4f, max(remF, 45f))

        val isFullRemaining = shareOfThisMeal >= 0.95f && allocatedCal >= remCal - 20
        val mode = if (isFullRemaining) MealBudgetMode.REMAINING_FOR_DAY else MealBudgetMode.PROPORTIONAL_MEAL_SHARE
        val summaryText = if (isFullRemaining) {
            "${mealType.title} için kalan ${NutraFormatters.formatInt(allocatedCal)} kcal ve ${NutraFormatters.formatInt(targetP.roundToInt())} g protein hedefleniyor."
        } else {
            "${mealType.title} için önerilen öğün bütçesi ~${NutraFormatters.formatInt(allocatedCal)} kcal · ${NutraFormatters.formatInt(targetP.roundToInt())} g protein (günlük kalan: ${NutraFormatters.formatInt(remCal)} kcal)."
        }

        return MealBudget(
            mealType = mealType,
            targetCalories = allocatedCal,
            targetProteinGrams = round1(targetP),
            targetCarbsGrams = round1(targetC),
            targetFatGrams = round1(targetF),
            dailyRemainingCalories = remCal,
            dailyRemainingProtein = round1(remP),
            dailyRemainingCarbs = round1(remC),
            dailyRemainingFat = round1(remF),
            mode = mode,
            budgetSummaryText = summaryText,
        )
    }

    private fun buildRoleCandidatePools(
        pool: List<ClassifiedFood>,
        allAllowed: List<ClassifiedFood>,
        mealType: MealType,
        dietStyle: DietStyle,
        eatingProfile: UserEatingProfile,
        lowMicronutrients: Set<MicronutrientType>,
        highSodiumActive: Boolean,
        variationSeed: Int,
    ): Map<MealRole, List<ClassifiedFood>> {
        val grouped = HashMap<MealRole, MutableList<ClassifiedFood>>()
        for (cf in pool) {
            grouped.getOrPut(cf.role) { ArrayList() }.add(cf)
        }
        // Backfill sparse roles from full allowed catalog if meal-restricted pool is too narrow
        for (role in MealRole.entries) {
            if (role == MealRole.INGREDIENT_ONLY) continue
            val current = grouped[role]
            if (current == null || current.size < 4) {
                val extras = allAllowed.filter { it.role == role }
                if (extras.isNotEmpty()) {
                    grouped[role] = extras.toMutableList()
                }
            }
        }

        val mealAffinityMap = eatingProfile.mealFoodAffinity[mealType].orEmpty()
        val result = HashMap<MealRole, List<ClassifiedFood>>(grouped.size)
        for ((role, list) in grouped) {
            val ranked = list.sortedByDescending { cf ->
                scoreIndividualFood(
                    cf = cf,
                    mealType = mealType,
                    mealAffinityMap = mealAffinityMap,
                    eatingProfile = eatingProfile,
                    dietStyle = dietStyle,
                    lowMicronutrients = lowMicronutrients,
                    highSodiumActive = highSodiumActive,
                    variationSeed = variationSeed,
                )
            }
            result[role] = ranked.take(14)
        }
        return result
    }

    private fun scoreIndividualFood(
        cf: ClassifiedFood,
        mealType: MealType,
        mealAffinityMap: Map<Long, Float>,
        eatingProfile: UserEatingProfile,
        dietStyle: DietStyle,
        lowMicronutrients: Set<MicronutrientType>,
        highSodiumActive: Boolean,
        variationSeed: Int,
    ): Double {
        val food = cf.food
        var s = 0.0

        if (mealType in food.preferredMealTypes) s += 16.0
        if (cf.isWholeFood) s += 10.0
        if (food.isFavorite) s += 14.0

        // Habit learning (weighted strictly by maturity so cold-start doesn't hallucinate!)
        val habitScale = when (eatingProfile.maturity) {
            HabitMaturity.NONE -> 0.0
            HabitMaturity.EARLY -> 0.55
            HabitMaturity.ESTABLISHED -> 1.0
        }
        if (habitScale > 0.0) {
            val mealAff = (mealAffinityMap[food.id] ?: 0f).toDouble()
            val globalAff = (eatingProfile.foodAffinityById[food.id] ?: 0f).toDouble()
            s += (mealAff * 22.0 + globalAff * 10.0) * habitScale
            if (cf.isTurkishHomeCooked) {
                s += (eatingProfile.homeCookedAffinity - 0.5) * 28.0 * habitScale
            }
        } else if (cf.isTurkishHomeCooked) {
            // Gentle quality prior for unbranded Turkish staples on cold start
            s += 8.0
        }

        // Diet style soft boosts
        when (dietStyle) {
            DietStyle.MEDITERRANEAN -> {
                if (cf.isTurkishHomeCooked || cf.containsFish || cf.role == MealRole.VEG_SALAD_SIDE || AllergenTag.LEGUMES in cf.allergens) {
                    s += 10.0
                }
            }
            DietStyle.LOW_CARB -> {
                if (food.carbsPer100g <= 12f) s += 12.0 else if (food.carbsPer100g >= 35f) s -= 10.0
            }
            else -> Unit
        }

        // Micronutrient signals (only non-null values get credit!)
        if (MicronutrientType.FIBER in lowMicronutrients) {
            val fib = food.fiberPer100g
            if (fib != null && fib >= 2.0f) s += min(16.0, fib * 2.2)
        }
        if (MicronutrientType.CALCIUM in lowMicronutrients) {
            val ca = food.calciumMgPer100g
            if (ca != null && ca >= 80f) s += 8.0
        }
        if (MicronutrientType.IRON in lowMicronutrients) {
            val fe = food.ironMgPer100g
            if (fe != null && fe >= 1.5f) s += 8.0
        }
        if (MicronutrientType.POTASSIUM in lowMicronutrients) {
            val k = food.potassiumMgPer100g
            if (k != null && k >= 250f) s += 7.0
        }
        if (MicronutrientType.VITAMIN_C in lowMicronutrients) {
            val vc = food.vitaminCMgPer100g
            if (vc != null && vc >= 15f) s += 7.0
        }
        if (highSodiumActive) {
            val na = cf.effectiveSodiumMgPer100g
            if (na != null) {
                if (na > 500f) s -= 12.0 else if (na < 150f) s += 6.0
            }
        }

        // Anti-repetition penalty for foods eaten in last 24h / 48h
        if (food.id in eatingProfile.recentlyEatenFoodIds24h) s -= 15.0
        else if (food.id in eatingProfile.recentlyEatenFoodIds48h) s -= 6.0

        // Deterministic controlled exploration when user taps "Alternatif Öneriler"
        if (variationSeed > 0) {
            val hash = ((food.id * 2654435761L + variationSeed * 1013904223L) ushr 16).toInt() and 0xFF
            s += (hash / 255.0 - 0.5) * 18.0
        }

        return s
    }

    private data class RawMealCandidate(
        val foods: List<ClassifiedFood>,
        val styleTag: RecommendationStyleTag,
        val isHabitAdapted: Boolean,
    )

    private fun assembleArchetypeCandidates(
        mealType: MealType,
        budget: MealBudget,
        dietStyle: DietStyle,
        scoredByRole: Map<MealRole, List<ClassifiedFood>>,
        allowedById: Map<Long, ClassifiedFood>,
        eatingProfile: UserEatingProfile,
        lowFiberActive: Boolean,
        variationSeed: Int,
    ): List<RawMealCandidate> {
        val candidates = ArrayList<RawMealCandidate>(48)

        val proteins = scoredByRole[MealRole.MAIN_PROTEIN].orEmpty()
        val composites = scoredByRole[MealRole.MAIN_COMPOSITE_DISH].orEmpty()
        val carbs = scoredByRole[MealRole.CARB_BASE].orEmpty()
        val soups = scoredByRole[MealRole.SOUP].orEmpty()
        val dairies = scoredByRole[MealRole.DAIRY_PROBIOTIC].orEmpty()
        val vegSides = scoredByRole[MealRole.VEG_SALAD_SIDE].orEmpty()
        val fruits = scoredByRole[MealRole.FRUIT].orEmpty()
        val nuts = scoredByRole[MealRole.HEALTHY_FAT_NUT].orEmpty()

        fun pickRotated(list: List<ClassifiedFood>, index: Int): ClassifiedFood? {
            if (list.isEmpty()) return null
            val shifted = (index + variationSeed) % list.size
            return list[shifted]
        }

        when (mealType) {
            MealType.BREAKFAST -> {
                val cheeses = dairies.filter {
                    val n = it.food.normalizedName
                    n.contains("peynir") || n.contains("kasar") || n.contains("ezine") || n.contains("lor")
                }.ifEmpty { dairies }
                val yogurtsOrMilk = dairies.filter {
                    val n = it.food.normalizedName
                    n.contains("yogurt") || n.contains("sut") || n.contains("kefir")
                }.ifEmpty { dairies }
                val breads = carbs.filter {
                    val n = it.food.normalizedName
                    n.contains("ekmek") || n.contains("simit") || n.contains("lavas")
                }.ifEmpty { carbs }
                val oats = carbs.filter {
                    val n = it.food.normalizedName
                    n.contains("yulaf") || n.contains("musli") || n.contains("granola")
                }.ifEmpty { carbs }
                val eggMains = proteins.filter {
                    val n = it.food.normalizedName
                    n.contains("yumurta") || n.contains("omlet") || n.contains("menemen")
                }.ifEmpty { proteins }

                // Archetype 1: Classic Turkish Breakfast (Egg/Menemen + Cheese + Bread + optional Veg/Olive)
                for (i in 0 until min(4, eggMains.size)) {
                    val main = eggMains[i]
                    val cheese = pickRotated(cheeses, i)
                    val bread = pickRotated(breads, i)
                    val side = pickRotated(vegSides.ifEmpty { nuts }, i)
                    val items = listOfNotNull(main, cheese, bread, if (budget.targetCalories >= 480) side else null)
                    if (items.size >= 2) {
                        candidates.add(RawMealCandidate(items, RecommendationStyleTag.BALANCED_TURKISH, false))
                    }
                }
                // Archetype 2: Oat / Yogurt Bowl (Oat + Yogurt/Milk + Fruit/Nuts)
                for (i in 0 until min(3, oats.size)) {
                    val oat = oats[i]
                    val base = pickRotated(yogurtsOrMilk, i)
                    val fruit = pickRotated(fruits, i)
                    val nut = pickRotated(nuts, i)
                    val items = listOfNotNull(oat, base, fruit, if (budget.targetCalories >= 450) nut else null)
                    if (items.size >= 2) {
                        candidates.add(
                            RawMealCandidate(
                                items,
                                if (lowFiberActive) RecommendationStyleTag.FIBER_RICH else RecommendationStyleTag.HIGH_PROTEIN,
                                false,
                            ),
                        )
                    }
                }
                // Archetype 3: Savory Light / Vegan-friendly Breakfast (Bread/Oat + Veg + Nut/Avocado)
                for (i in 0 until min(2, breads.size)) {
                    val bread = breads[i]
                    val veg = pickRotated(vegSides, i)
                    val fat = pickRotated(nuts, i)
                    val items = listOfNotNull(bread, veg, fat)
                    if (items.size >= 2) {
                        candidates.add(RawMealCandidate(items, RecommendationStyleTag.DISCOVERY, false))
                    }
                }
            }

            MealType.LUNCH, MealType.DINNER -> {
                val savoryDairies = dairies.filter {
                    val n = it.food.normalizedName
                    n.contains("yogurt") || n.contains("ayran") || n.contains("cacik") || n.contains("kefir")
                }.ifEmpty { dairies }
                val savoryCarbs = carbs.filter {
                    val n = it.food.normalizedName
                    !n.contains("yulaf") && !n.contains("musli") && !n.contains("granola")
                }.ifEmpty { carbs }
                val fiberCarbs = savoryCarbs.sortedByDescending { it.food.fiberPer100g ?: -1f }
                val legumeOrFiberMains = (composites + soups).filter {
                    (it.food.fiberPer100g ?: 0f) >= 1.5f || AllergenTag.LEGUMES in it.allergens
                }

                // Archetype 1: Protein + Grain/Carb + Yogurt/Ayran (e.g., Izgara Tavuk + Bulgur + Yoğurt)
                for (i in 0 until min(6, proteins.size)) {
                    val protein = proteins[i]
                    val carbPool = if (lowFiberActive) fiberCarbs else savoryCarbs
                    val carb = if (dietStyle == DietStyle.LOW_CARB) pickRotated(vegSides, i) else pickRotated(carbPool, i)
                    val dairy = pickRotated(savoryDairies, i)
                    val veg = pickRotated(vegSides, i)
                    val combo = listOfNotNull(
                        protein,
                        carb,
                        dairy ?: veg,
                    )
                    if (combo.size >= 2) {
                        candidates.add(
                            RawMealCandidate(
                                combo,
                                if (budget.targetProteinGrams >= 35f) RecommendationStyleTag.HIGH_PROTEIN else RecommendationStyleTag.BALANCED_TURKISH,
                                false,
                            ),
                        )
                    }
                    // Variant with veg side instead of dairy for variety (or dairy-free users)
                    if (veg != null && carb != null && veg.food.id != carb.food.id) {
                        candidates.add(
                            RawMealCandidate(
                                listOf(protein, carb, veg),
                                if (lowFiberActive) RecommendationStyleTag.FIBER_RICH else RecommendationStyleTag.BALANCED_TURKISH,
                                false,
                            ),
                        )
                    }
                }

                // Archetype 2: Turkish Home-Cooked / Legume Dish + Veg/Salad + Yogurt/Ayran
                for (i in 0 until min(5, composites.size)) {
                    val dish = composites[i]
                    val veg = pickRotated(vegSides, i)
                    val dairy = pickRotated(savoryDairies, i)
                    val combo = listOfNotNull(dish, veg, dairy)
                    if (combo.size >= 2) {
                        candidates.add(
                            RawMealCandidate(
                                combo,
                                if (dish in legumeOrFiberMains && lowFiberActive) RecommendationStyleTag.FIBER_RICH else RecommendationStyleTag.BALANCED_TURKISH,
                                false,
                            ),
                        )
                    }
                }

                // Archetype 3: Legume / Soup + Protein or Salad + Yogurt (great for fiber & lighter calories!)
                for (i in 0 until min(4, soups.size)) {
                    val soup = soups[i]
                    val companion = pickRotated(proteins.ifEmpty { composites }, i)
                    val side = pickRotated(vegSides.ifEmpty { savoryDairies }, i)
                    val combo = listOfNotNull(soup, companion, side)
                    if (combo.size >= 2) {
                        candidates.add(
                            RawMealCandidate(
                                combo,
                                if (budget.mode == MealBudgetMode.LIGHT_OVER_BUDGET) RecommendationStyleTag.LIGHT_OPTION else RecommendationStyleTag.FIBER_RICH,
                                false,
                            ),
                        )
                    }
                }

                // Archetype 4: High-Fiber Plant / Legume Forward Plate (matches Mercimek + Sebze + Yoğurt/Ekmek)
                for (i in 0 until min(4, legumeOrFiberMains.size)) {
                    val main = legumeOrFiberMains[i]
                    val veg = pickRotated(vegSides, i + 1)
                    val third = pickRotated(savoryDairies.ifEmpty { fiberCarbs }, i)
                    val combo = listOfNotNull(main, veg, third)
                    if (combo.size >= 2) {
                        candidates.add(RawMealCandidate(combo, RecommendationStyleTag.FIBER_RICH, false))
                    }
                }
            }

            MealType.SNACK -> {
                val snackDairies = dairies.filter {
                    val n = it.food.normalizedName
                    n.contains("yogurt") || n.contains("kefir") || n.contains("sut") || n.contains("lor")
                }.ifEmpty { dairies }

                // Archetype 1: Fruit + Nuts
                for (i in 0 until min(4, fruits.size)) {
                    val fruit = fruits[i]
                    val nut = pickRotated(nuts, i)
                    if (nut != null) {
                        candidates.add(
                            RawMealCandidate(
                                listOf(fruit, nut),
                                if (lowFiberActive) RecommendationStyleTag.FIBER_RICH else RecommendationStyleTag.LIGHT_OPTION,
                                false,
                            ),
                        )
                    }
                }
                // Archetype 2: Yogurt/Kefir + Fruit (+ optional Nut/Oat)
                for (i in 0 until min(4, snackDairies.size)) {
                    val dairy = snackDairies[i]
                    val fruit = pickRotated(fruits, i)
                    val nut = pickRotated(nuts, i)
                    val items = listOfNotNull(dairy, fruit, if (budget.targetCalories >= 260) nut else null)
                    if (items.size >= 2) {
                        candidates.add(RawMealCandidate(items, RecommendationStyleTag.HIGH_PROTEIN, false))
                    }
                }
            }
        }

        // Habit-Adapted Personalization Archetype (ONLY when habit maturity is EARLY or ESTABLISHED)
        if (eatingProfile.maturity != HabitMaturity.NONE) {
            val mealTopIds = eatingProfile.mealFoodAffinity[mealType]
                .orEmpty()
                .entries
                .sortedByDescending { it.value }
                .map { it.key }
                .take(5)

            for (anchorId in mealTopIds) {
                val anchor = allowedById[anchorId] ?: continue
                val partners = eatingProfile.coOccurrencePairs[anchorId]
                    .orEmpty()
                    .mapNotNull { allowedById[it] }
                    .filter { it.role != anchor.role }
                    .take(2)

                if (partners.isNotEmpty()) {
                    val combo = buildList {
                        add(anchor)
                        addAll(partners)
                        // Add a fresh fiber/veg side if missing and fiber is low
                        if (size == 2 && lowFiberActive && none { it.role == MealRole.VEG_SALAD_SIDE }) {
                            vegSides.firstOrNull()?.let { add(it) }
                        }
                    }
                    candidates.add(RawMealCandidate(combo, RecommendationStyleTag.HABIT_ADAPTED, isHabitAdapted = true))
                }
            }
        }

        return candidates
    }

    private fun buildRecommendationModel(
        id: String,
        mealType: MealType,
        raw: RawMealCandidate,
        opt: CustomPortionOptimizationResult,
        budget: MealBudget,
        dailySummary: DailyNutritionSummary,
        eatingProfile: UserEatingProfile,
        lowMicronutrients: Set<MicronutrientType>,
        highSodiumActive: Boolean,
        classifiedMap: Map<Long, ClassifiedFood>,
    ): MealRecommendation {
        val title = opt.items.take(3).joinToString(" · ") { shortDisplayName(it.food.name) }

        val (microHighlights, missingNote) = computeMicronutrientHighlights(opt.items, classifiedMap)
        val impact = buildGoalImpactPreview(dailySummary, opt)
        val reasons = buildReasons(
            raw = raw,
            opt = opt,
            budget = budget,
            eatingProfile = eatingProfile,
            microHighlights = microHighlights,
            lowMicronutrients = lowMicronutrients,
            highSodiumActive = highSodiumActive,
            classifiedMap = classifiedMap,
        )

        return MealRecommendation(
            id = id,
            mealType = mealType,
            title = title,
            styleTag = raw.styleTag,
            items = opt.items,
            totalCalories = opt.totalCalories,
            totalProtein = opt.totalProtein,
            totalCarbs = opt.totalCarbs,
            totalFat = opt.totalFat,
            micronutrientHighlights = microHighlights,
            missingMicronutrientNote = missingNote,
            reasons = reasons,
            goalImpact = impact,
            fitScore = opt.fitScore,
            isPersonalized = eatingProfile.maturity != HabitMaturity.NONE && (
                raw.isHabitAdapted || opt.items.any { (eatingProfile.foodAffinityById[it.food.id] ?: 0f) > 0.25f }
                ),
        )
    }

    private fun computeMicronutrientHighlights(
        items: List<RecommendedFoodPortion>,
        classifiedMap: Map<Long, ClassifiedFood>,
    ): Pair<List<MicronutrientContribution>, String?> {
        val totalCount = items.size
        val highlights = ArrayList<MicronutrientContribution>(4)
        var anyMissingData = false

        fun collect(
            type: MicronutrientType,
            minMeaningful: Float,
            extractor: (ClassifiedFood) -> Float?,
        ) {
            var sum = 0.0
            var known = 0
            for (item in items) {
                val cf = classifiedMap[item.food.id] ?: continue
                val per100 = extractor(cf)
                if (per100 != null && per100.isFinite()) {
                    sum += (per100 * item.totalGrams) / 100.0
                    known++
                }
            }
            if (known < totalCount) anyMissingData = true
            if (known > 0 && sum >= minMeaningful) {
                val value = round1(sum.toFloat())
                val formatted = if (type.unit == "mg") {
                    "${NutraFormatters.formatInt(value.roundToInt())} mg"
                } else {
                    "${NutraFormatters.formatMacroGrams(value)} g"
                }
                highlights.add(
                    MicronutrientContribution(
                        type = type,
                        amount = value,
                        formattedValue = formatted,
                        knownItemCount = known,
                        totalItemCount = totalCount,
                    ),
                )
            }
        }

        collect(MicronutrientType.FIBER, 2.5f) { it.food.fiberPer100g }
        collect(MicronutrientType.CALCIUM, 100f) { it.food.calciumMgPer100g }
        collect(MicronutrientType.POTASSIUM, 350f) { it.food.potassiumMgPer100g }
        collect(MicronutrientType.IRON, 1.5f) { it.food.ironMgPer100g }
        collect(MicronutrientType.VITAMIN_C, 15f) { it.food.vitaminCMgPer100g }
        collect(MicronutrientType.SODIUM, 100f) { it.effectiveSodiumMgPer100g }

        val missingNote = if (anyMissingData && highlights.isNotEmpty()) {
            "Mikro besin toplamları yalnızca verisi bulunan besinlerden hesaplandı; eksik veriler sıfır sayılmadı."
        } else {
            null
        }
        return highlights.take(4) to missingNote
    }

    private fun buildGoalImpactPreview(
        dailySummary: DailyNutritionSummary,
        opt: CustomPortionOptimizationResult,
    ): DailyGoalImpactPreview {
        val calBefore = dailySummary.consumedCalories
        val calAfter = calBefore + opt.totalCalories
        val pBefore = dailySummary.consumedProtein
        val pAfter = round1(pBefore + opt.totalProtein)
        val cBefore = dailySummary.consumedCarbs
        val cAfter = round1(cBefore + opt.totalCarbs)
        val fBefore = dailySummary.consumedFat
        val fAfter = round1(fBefore + opt.totalFat)

        val calPct = ((calAfter.toFloat() / dailySummary.goals.calories.coerceAtLeast(1)) * 100f).roundToInt()
        val pPct = ((pAfter / dailySummary.goals.proteinGrams.coerceAtLeast(1)) * 100f).roundToInt()
        val summary = "Eklendiğinde günlük kalori %$calPct, protein hedefin %$pPct düzeyine ulaşır."

        return DailyGoalImpactPreview(
            caloriesBefore = calBefore,
            caloriesAfter = calAfter,
            calorieGoal = dailySummary.goals.calories,
            proteinBefore = pBefore,
            proteinAfter = pAfter,
            proteinGoal = dailySummary.goals.proteinGrams,
            carbsBefore = cBefore,
            carbsAfter = cAfter,
            carbsGoal = dailySummary.goals.carbsGrams,
            fatBefore = fBefore,
            fatAfter = fAfter,
            fatGoal = dailySummary.goals.fatGrams,
            impactSummary = summary,
        )
    }

    private fun buildReasons(
        raw: RawMealCandidate,
        opt: CustomPortionOptimizationResult,
        budget: MealBudget,
        eatingProfile: UserEatingProfile,
        microHighlights: List<MicronutrientContribution>,
        lowMicronutrients: Set<MicronutrientType>,
        highSodiumActive: Boolean,
        classifiedMap: Map<Long, ClassifiedFood>,
    ): List<String> {
        val reasons = ArrayList<String>(3)

        // Reason 1: Calorie & Protein fit
        val calText = NutraFormatters.formatInt(opt.totalCalories)
        val protText = NutraFormatters.formatMacroGrams(opt.totalProtein)
        if (budget.mode == MealBudgetMode.LIGHT_OVER_BUDGET) {
            reasons.add("Günlük hedefine çok yaklaştığın için hafif ve dengeli bir seçenek ($calText kcal · $protText g protein).")
        } else {
            reasons.add(
                "${budget.mealType.title} bütçene (%${opt.fitScore} uyum) göre $calText kcal ve $protText g protein sağlayacak şekilde porsiyonlandı.",
            )
        }

        // Reason 2: Micronutrient contribution (when known & relevant)
        val fiberContrib = microHighlights.firstOrNull { it.type == MicronutrientType.FIBER }
        if (fiberContrib != null && MicronutrientType.FIBER in lowMicronutrients) {
            reasons.add("Son günlerde düşük seyreden lif alımına +${fiberContrib.formattedValue} katkı sağlar.")
        } else if (highSodiumActive) {
            val sodiumContrib = microHighlights.firstOrNull { it.type == MicronutrientType.SODIUM }
            if (sodiumContrib != null && sodiumContrib.amount <= 650f) {
                reasons.add("Sodyum dengesini korumak için daha kontrollü tuz içeriğine (${sodiumContrib.formattedValue}) sahip.")
            }
        } else if (fiberContrib != null && fiberContrib.amount >= 5f) {
            reasons.add("Yüksek tokluk için +${fiberContrib.formattedValue} lif içerir.")
        }

        // Reason 3: Personalization or Turkish cuisine fit (honest about cold-start!)
        if (eatingProfile.maturity != HabitMaturity.NONE) {
            val familiarCount = opt.items.count { (eatingProfile.foodAffinityById[it.food.id] ?: 0f) > 0.25f }
            val homeCount = opt.items.count { classifiedMap[it.food.id]?.isTurkishHomeCooked == true }
            when {
                raw.isHabitAdapted ->
                    reasons.add("Geçmişte sevdiğin kombinasyonlardan uyarlanıp bugünkü kalan hedefine göre dengelendi.")
                homeCount >= 2 && eatingProfile.homeCookedAffinity >= 0.58f ->
                    reasons.add("Tercih ettiğin Türk mutfağı ev yemeği tarzıyla uyumlu.")
                familiarCount >= 1 && familiarCount < opt.items.size ->
                    reasons.add("Sık tükettiğin besinleri yeni bir eşlikçiyle çeşitlendirir.")
            }
        } else if (reasons.size < 2) {
            reasons.add("Günlük hayatta pratik ve dengeli Türk mutfağı eşleşmesi olarak oluşturuldu.")
        }

        return reasons.take(3)
    }

    private data class ScoredRecommendationCandidate(
        val rec: MealRecommendation,
        val foods: List<ClassifiedFood>,
        val totalScore: Double,
    )

    private fun computeCompositeCandidateScore(
        rec: MealRecommendation,
        foods: List<ClassifiedFood>,
        eatingProfile: UserEatingProfile,
        dietStyle: DietStyle,
        lowMicronutrients: Set<MicronutrientType>,
        highSodiumActive: Boolean,
        isHabitAdapted: Boolean,
    ): Double {
        var score = rec.fitScore * 1.15

        // Habit & cuisine alignment (scaled by maturity)
        val habitWeight = when (eatingProfile.maturity) {
            HabitMaturity.NONE -> 0.0
            HabitMaturity.EARLY -> 0.5
            HabitMaturity.ESTABLISHED -> 1.0
        }
        if (habitWeight > 0.0) {
            val avgAff = foods.map { eatingProfile.foodAffinityById[it.food.id] ?: 0f }.average()
            score += avgAff * 14.0 * habitWeight
            if (isHabitAdapted) score += 5.0 * habitWeight
            val homeRatio = foods.count { it.isTurkishHomeCooked }.toDouble() / foods.size
            score += (eatingProfile.homeCookedAffinity - 0.5) * homeRatio * 18.0 * habitWeight
        }

        // Micronutrient alignment
        if (MicronutrientType.FIBER in lowMicronutrients) {
            val fiber = rec.micronutrientHighlights.firstOrNull { it.type == MicronutrientType.FIBER }?.amount ?: 0f
            score += min(14.0, fiber * 1.6)
        }
        if (highSodiumActive) {
            val na = rec.micronutrientHighlights.firstOrNull { it.type == MicronutrientType.SODIUM }?.amount
            if (na != null) {
                if (na > 1000f) score -= 12.0 else if (na < 550f) score += 6.0
            }
        }

        // Novelty & anti-repetition across 24h
        val recentOverlap = foods.count { it.food.id in eatingProfile.recentlyEatenFoodIds24h }
        score -= recentOverlap * 6.5

        // Whole-food nutritional quality bonus
        val wholeRatio = foods.count { it.isWholeFood }.toDouble() / foods.size
        score += wholeRatio * 8.0

        if (dietStyle == DietStyle.MEDITERRANEAN && foods.any { it.role == MealRole.VEG_SALAD_SIDE || it.containsFish }) {
            score += 6.0
        }

        return score
    }

    /**
     * Maximal Marginal Relevance (MMR): balances quality score against overlap with
     * already-selected recommendations so the user receives genuinely distinct meal ideas.
     */
    private fun selectDiverseTopRecommendations(
        candidates: List<ScoredRecommendationCandidate>,
        maxResults: Int,
    ): List<MealRecommendation> {
        if (candidates.isEmpty()) return emptyList()
        val remaining = candidates.toMutableList()
        val selected = ArrayList<ScoredRecommendationCandidate>(maxResults)

        // Pick top overall candidate first
        remaining.sortByDescending { it.totalScore }
        selected.add(remaining.removeAt(0))

        while (selected.size < maxResults && remaining.isNotEmpty()) {
            var bestIdx = 0
            var bestMmr = Double.NEGATIVE_INFINITY
            for (i in remaining.indices) {
                val cand = remaining[i]
                val candIds = cand.foods.map { it.food.id }.toSet()
                val candPrimaryId = cand.foods.firstOrNull()?.food?.id
                var maxSim = 0.0
                for (chosen in selected) {
                    val chosenIds = chosen.foods.map { it.food.id }.toSet()
                    val inter = candIds.intersect(chosenIds).size.toDouble()
                    val union = (candIds.size + chosenIds.size - inter).coerceAtLeast(1.0)
                    var sim = inter / union
                    if (candPrimaryId != null && candPrimaryId == chosen.foods.firstOrNull()?.food?.id) {
                        sim = max(sim, 0.72) // Strongly encourage distinct main dish across cards!
                    }
                    if (sim > maxSim) maxSim = sim
                }
                val mmr = 0.72 * cand.totalScore - 0.28 * (maxSim * 100.0)
                if (mmr > bestMmr) {
                    bestMmr = mmr
                    bestIdx = i
                }
            }
            selected.add(remaining.removeAt(bestIdx))
        }

        return selected.map { it.rec }
    }

    private fun shortDisplayName(name: String): String {
        val cleaned = name
            .replace(" (Derisiz, Izgara)", "")
            .replace(" (Derisiz, Haşlama)", "")
            .replace(" (Haşlama, Derisiz)", "")
            .replace(" (Pişmiş)", "")
            .replace(" (Orta)", "")
            .trim()
        return if (cleaned.length <= 24) cleaned else cleaned.take(22).trimEnd() + "…"
    }

    private fun round1(v: Float): Float = (v * 10f).roundToInt() / 10f
}
