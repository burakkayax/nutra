package com.nutra.app.domain.intelligence

import com.nutra.app.domain.model.AllergenTag
import com.nutra.app.domain.model.DietaryProfile
import com.nutra.app.domain.model.FoodCategory
import com.nutra.app.domain.model.FoodFeedbackRecord
import com.nutra.app.domain.model.FoodItem
import com.nutra.app.domain.usecase.TurkishTextNormalizer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Classifies catalog and custom [FoodItem]s into culinary roles, edibility states,
 * allergen/dietary tags, and realistic portion constraints.
 *
 * Prevents cooking-only ingredients (raw meat, dry uncooked legumes/pasta, pure oils, sauces,
 * raw potatoes/onions) and oversized default units from leaking into practical meal suggestions.
 */
@Singleton
class FoodCulinaryClassifier @Inject constructor(
    private val eligibilityPolicy: FoodEligibilityPolicy,
) {
    constructor() : this(FoodEligibilityPolicy())

    private data class CatalogCacheSnapshot(
        val signature: Int,
        val byKey: Map<String, ClassifiedFood>,
    )

    @Volatile
    private var cacheSnapshot: CatalogCacheSnapshot = CatalogCacheSnapshot(0, emptyMap())

    fun foodFeedbackKey(food: FoodItem): String =
        food.catalogCode?.takeIf { it.isNotBlank() }
            ?: food.customUid?.takeIf { it.isNotBlank() }
            ?: "custom:${food.id}:${TurkishTextNormalizer.normalize(food.name)}"

    fun classifyCatalog(foods: List<FoodItem>): List<ClassifiedFood> {
        val signature = computeSignature(foods)
        val snapshot = cacheSnapshot
        if (signature == snapshot.signature && snapshot.byKey.size == foods.size) {
            val out = ArrayList<ClassifiedFood>(foods.size)
            var allHit = true
            for (food in foods) {
                val c = snapshot.byKey[foodFeedbackKey(food)]
                if (c != null && c.food == food) {
                    out.add(c)
                } else {
                    allHit = false
                    break
                }
            }
            if (allHit) return out
        }

        val classified = ArrayList<ClassifiedFood>(foods.size)
        val nextMap = HashMap<String, ClassifiedFood>(foods.size.coerceAtLeast(16))
        for (food in foods) {
            val item = classifySingle(food)
            classified.add(item)
            nextMap[item.feedbackKey] = item
        }
        cacheSnapshot = CatalogCacheSnapshot(signature = signature, byKey = nextMap)
        return classified
    }

    fun classifySingle(food: FoodItem): ClassifiedFood {
        val normName = food.normalizedName.ifBlank { TurkishTextNormalizer.normalize(food.name) }
        val paddedName = " $normName "
        val prep = TurkishTextNormalizer.normalize(food.preparationState.orEmpty())

        val edibility = resolveEdibility(food, normName, paddedName, prep)
        val role = if (edibility == EdibilityStatus.COOKING_INGREDIENT_ONLY) {
            MealRole.INGREDIENT_ONLY
        } else {
            resolveMealRole(food, normName, paddedName)
        }

        val containsFish: Boolean
        val containsPoultry: Boolean
        val containsRedMeat: Boolean
        val allergens: Set<AllergenTag>
        val containsAnimalProduct: Boolean

        if (food.allergenVerified) {
            // Verified Catalog v3 structural metadata is authoritative (zero category-title bleed-through)
            val df = food.dietFlags
            containsFish = df.containsFish
            containsPoultry = df.containsPoultry
            containsRedMeat = df.containsRedMeat
            allergens = food.allergens
            containsAnimalProduct = df.containsAnimalProduct ||
                containsRedMeat || containsPoultry || containsFish ||
                AllergenTag.DAIRY in allergens || AllergenTag.EGG in allergens || df.containsHoney
        } else {
            // Heuristic fallback for unverified custom/legacy items: inspect ONLY food.name (never category/subtitle!)
            val isExplicitlyMeatFree = containsAnyToken(normName, PLANT_PROTEIN_TOKENS) ||
                normName.contains("mercimek kofte") ||
                normName.contains("cig kofte") ||
                normName.contains("patates kofte") ||
                normName.contains("ispanakli kofte") ||
                normName.contains("koftelik")

            containsFish = containsAnyToken(normName, FISH_TOKENS) || containsAnyToken(paddedName, FISH_WORD_TOKENS)
            containsPoultry = !isExplicitlyMeatFree && containsAnyToken(normName, POULTRY_TOKENS)
            val hasExplicitRedMeatWord = containsAnyToken(normName, EXPLICIT_RED_MEAT_TOKENS)
            val hasGenericMeatDishWord = containsAnyToken(normName, GENERIC_MEAT_DISH_TOKENS)
            containsRedMeat = !isExplicitlyMeatFree && (
                hasExplicitRedMeatWord ||
                    (!containsPoultry && !containsFish && hasGenericMeatDishWord) ||
                    (food.category == FoodCategory.MEAT_POULTRY && !containsFish && !containsPoultry)
                )

            allergens = detectAllergensFromNameOnly(normName, paddedName, containsFish)
            val containsHoney = (containsAnyToken(paddedName, HONEY_WORD_TOKENS) || containsAnyToken(normName, HONEY_SUBSTRING_TOKENS)) &&
                !normName.contains("balkabagi")
            containsAnimalProduct = containsRedMeat || containsPoultry || containsFish ||
                AllergenTag.DAIRY in allergens || AllergenTag.EGG in allergens || containsHoney
        }

        val isFastFood = containsAnyToken(normName, FAST_FOOD_TOKENS)
        val isTurkishHomeCooked = food.brand == null && !isFastFood && (
            food.category == FoodCategory.HOME_COOKED ||
                food.category == FoodCategory.SOUP ||
                food.category == FoodCategory.SALAD_MEZE ||
                (food.category == FoodCategory.MEAT_POULTRY && edibility == EdibilityStatus.READY_TO_EAT) ||
                (food.category == FoodCategory.BREAKFAST && role != MealRole.SNACK_SWEET && !containsAnyToken(normName, PACKAGED_SNACK_TOKENS))
            )

        val isWholeFood = food.brand == null &&
            food.category != FoodCategory.SNACKS_SWEETS &&
            food.category != FoodCategory.OILS_SAUCES &&
            role != MealRole.SNACK_SWEET &&
            !containsAnyToken(normName, PROCESSED_MEAT_TOKENS)

        val effectiveSodium = food.sodiumMgPer100g ?: food.saltPer100g?.let { it * 393.4f }

        val portionConstraint = computePortionConstraint(food, role, normName, paddedName)

        return ClassifiedFood(
            food = food,
            feedbackKey = foodFeedbackKey(food),
            edibility = edibility,
            role = role,
            allergens = allergens,
            containsRedMeat = containsRedMeat,
            containsPoultry = containsPoultry,
            containsFish = containsFish,
            containsAnimalProduct = containsAnimalProduct,
            isTurkishHomeCooked = isTurkishHomeCooked,
            isWholeFood = isWholeFood,
            portionConstraint = portionConstraint,
            effectiveSodiumMgPer100g = effectiveSodium,
        )
    }

    fun isAllowedByPreferences(
        classified: ClassifiedFood,
        dietaryProfile: DietaryProfile,
        feedbackByKey: Map<String, FoodFeedbackRecord>,
        requireReadyToEat: Boolean = true,
        forAutomaticRecommendation: Boolean = true,
    ): Boolean = eligibilityPolicy.isEligible(
        classified = classified,
        dietaryProfile = dietaryProfile,
        feedbackByKey = feedbackByKey,
        requireReadyToEat = requireReadyToEat,
        forAutomaticRecommendation = forAutomaticRecommendation,
    )

    private fun resolveEdibility(food: FoodItem, normName: String, paddedName: String, prep: String): EdibilityStatus {
        if (food.caloriesPer100g <= 2 && food.proteinPer100g < 0.5f && food.carbsPer100g < 0.5f && food.fatPer100g < 0.5f) {
            return EdibilityStatus.COOKING_INGREDIENT_ONLY
        }
        if (food.category == FoodCategory.OILS_SAUCES) {
            return EdibilityStatus.COOKING_INGREDIENT_ONLY
        }
        // Raw whole lemon is a condiment/acidifier, not a standalone 150g fruit portion
        if (food.category == FoodCategory.FRUIT_NUTS && containsAnyToken(paddedName, RAW_LEMON_WORD_TOKENS)) {
            return EdibilityStatus.COOKING_INGREDIENT_ONLY
        }
        // Alcohol & energy drinks are excluded from healthy meal recommendations
        if (food.category == FoodCategory.DRINKS &&
            (containsAnyToken(paddedName, ALCOHOL_WORD_TOKENS) || containsAnyToken(normName, DRY_OR_EXCLUDED_DRINK_TOKENS))
        ) {
            return EdibilityStatus.COOKING_INGREDIENT_ONLY
        }
        if (containsAnyToken(normName, INGREDIENT_ONLY_SUBSTRING_TOKENS) ||
            containsAnyToken(paddedName, INGREDIENT_ONLY_WORD_TOKENS)
        ) {
            // Allow ready-to-eat dishes that mention olive oil / butter / sunflower oil preparation
            val isPreparedDishWithOilOrButter = normName.contains("zeytinyagli") ||
                normName.contains("tereyagli") ||
                normName.contains("aycicek yagli")
            if (!isPreparedDishWithOilOrButter) {
                return EdibilityStatus.COOKING_INGREDIENT_ONLY
            }
        }
        // Raw meat/fish/poultry, frozen raw seafood, or minced raw meat
        if (food.category == FoodCategory.MEAT_POULTRY) {
            if (prep.contains("cig") || prep.contains("dondurulmus") || normName.contains("cig") || normName.contains("kiyma")) {
                return EdibilityStatus.COOKING_INGREDIENT_ONLY
            }
        }
        // Raw whole eggs (uncooked) should not be suggested as ready-to-eat
        if (food.category == FoodCategory.BREAKFAST && prep.contains("cig")) {
            return EdibilityStatus.COOKING_INGREDIENT_ONLY
        }
        // Dry uncooked grains/legumes/pasta/flour (except oats, muesli, bread, cooked pilav/makarna)
        if (food.category == FoodCategory.GRAINS_BAKERY) {
            if (prep.contains("kuru") || prep.contains("cig") || normName.contains("pismemis") || normName.contains("kuru")) {
                if (!normName.contains("yulaf") && !normName.contains("musli") && !normName.contains("gevrek") && !normName.contains("ekmek")) {
                    return EdibilityStatus.COOKING_INGREDIENT_ONLY
                }
            }
        }
        // Raw vegetables that are NOT normally eaten raw as a standalone side (or culinary herbs / hot chili peppers)
        if (food.category == FoodCategory.VEGETABLES) {
            if (containsAnyToken(normName, HERB_OR_CHILI_VEG_TOKENS)) {
                return EdibilityStatus.COOKING_INGREDIENT_ONLY
            }
            if (prep.contains("cig") || prep.contains("kuru") || prep.contains("dondurulmus")) {
                if (!isRawEdibleSaladVegetable(normName)) {
                    return EdibilityStatus.COOKING_INGREDIENT_ONLY
                }
            }
        }
        // Raw meat-free cig kofte is ready to eat!
        if (food.category == FoodCategory.SALAD_MEZE && normName.contains("cig kofte")) {
            return EdibilityStatus.READY_TO_EAT
        }
        return EdibilityStatus.READY_TO_EAT
    }

    private fun isRawEdibleSaladVegetable(normName: String): Boolean {
        if (containsAnyToken(normName, RAW_EDIBLE_VEG_TOKENS)) return true
        // Support catalog comma-reversed titles like "Lahana, kırmızı, baş", "Biber, sivri", "Biber, kırmızı"
        if (normName.contains("lahana") && normName.contains("kirmizi")) return true
        if (normName.contains("biber") && !normName.contains("aci") &&
            (normName.contains("sivri") || normName.contains("kirmizi") || normName.contains("yesil") || normName.contains("kapya") || normName.contains("carliston"))
        ) {
            return true
        }
        return false
    }

    private fun resolveMealRole(food: FoodItem, normName: String, paddedName: String): MealRole {
        if (food.category == FoodCategory.SOUP || normName.contains("corba")) return MealRole.SOUP

        if (containsAnyToken(normName, DAIRY_ROLE_TOKENS)) {
            return MealRole.DAIRY_PROBIOTIC
        }

        if (food.category == FoodCategory.DRINKS) {
            return if (containsAnyToken(normName, PROTEIN_DRINK_TOKENS)) {
                MealRole.DAIRY_PROBIOTIC
            } else {
                MealRole.BEVERAGE
            }
        }

        if (food.category == FoodCategory.MEAT_POULTRY) {
            return MealRole.MAIN_PROTEIN
        }

        if (food.category == FoodCategory.HOME_COOKED) {
            return when {
                containsAnyToken(normName, CARB_SIDE_DISH_TOKENS) -> MealRole.CARB_BASE
                containsAnyToken(normName, LIGHT_VEG_DISH_TOKENS) -> MealRole.VEG_SALAD_SIDE
                food.proteinPer100g >= 11f && food.carbsPer100g <= 12f -> MealRole.MAIN_PROTEIN
                else -> MealRole.MAIN_COMPOSITE_DISH
            }
        }

        if (food.category == FoodCategory.SALAD_MEZE) {
            return when {
                normName.contains("cacik") || normName.contains("haydari") -> MealRole.DAIRY_PROBIOTIC
                normName.contains("kisir") || normName.contains("cig kofte") || normName.contains("mercimek kofte") -> MealRole.CARB_BASE
                else -> MealRole.VEG_SALAD_SIDE
            }
        }

        if (food.category == FoodCategory.VEGETABLES) {
            return MealRole.VEG_SALAD_SIDE
        }

        if (food.category == FoodCategory.BREAKFAST) {
            return when {
                containsAnyToken(normName, EGG_MAIN_TOKENS) -> MealRole.MAIN_PROTEIN
                containsAnyToken(normName, OAT_CEREAL_TOKENS) -> MealRole.CARB_BASE
                containsAnyToken(normName, SWEET_SPREAD_BREAKFAST_TOKENS) || containsAnyToken(paddedName, HONEY_WORD_TOKENS) ->
                    MealRole.SNACK_SWEET
                (normName.contains("kaymak") && !normName.contains("kaymaksiz")) || containsAnyToken(normName, OLIVE_FAT_TOKENS) ->
                    MealRole.HEALTHY_FAT_NUT
                else -> MealRole.DAIRY_PROBIOTIC
            }
        }

        if (food.category == FoodCategory.GRAINS_BAKERY) {
            return if (containsAnyToken(normName, PASTRY_MAIN_TOKENS)) {
                MealRole.MAIN_COMPOSITE_DISH
            } else {
                MealRole.CARB_BASE
            }
        }

        if (food.category == FoodCategory.FRUIT_NUTS) {
            return if (food.fatPer100g >= 20f || containsAnyToken(normName, NUT_SEED_TOKENS)) {
                MealRole.HEALTHY_FAT_NUT
            } else {
                MealRole.FRUIT
            }
        }

        if (food.category == FoodCategory.SNACKS_SWEETS) {
            return MealRole.SNACK_SWEET
        }

        // Custom / fallback heuristic based on macro profile
        return when {
            food.proteinPer100g >= 14f -> MealRole.MAIN_PROTEIN
            food.carbsPer100g >= 25f -> MealRole.CARB_BASE
            food.fatPer100g >= 30f -> MealRole.HEALTHY_FAT_NUT
            else -> MealRole.MAIN_COMPOSITE_DISH
        }
    }

    private fun detectAllergensFromNameOnly(
        normName: String,
        paddedName: String,
        containsFish: Boolean,
    ): Set<AllergenTag> {
        val set = HashSet<AllergenTag>(4)
        // Strip plant-milk tokens before checking dairy, so "Badem Sütü ve Yoğurt" still flags DAIRY!
        var dairySearchText = normName.replace("kaymaksiz", "")
        for (pm in PLANT_MILK_TOKENS) {
            dairySearchText = dairySearchText.replace(pm, "")
        }
        if (containsAnyToken(dairySearchText, DAIRY_ALLERGEN_TOKENS)) {
            set.add(AllergenTag.DAIRY)
        }
        val isGlutenFree = normName.contains("glutensiz")
        if (!isGlutenFree && containsAnyToken(normName, GLUTEN_ALLERGEN_TOKENS)) {
            set.add(AllergenTag.GLUTEN)
        }
        if (containsAnyToken(normName, EGG_ALLERGEN_TOKENS) && !normName.contains("yumurtasiz")) {
            set.add(AllergenTag.EGG)
        }
        if (containsFish) {
            set.add(AllergenTag.FISH_SEAFOOD)
        }
        if (containsAnyToken(normName, PEANUT_ALLERGEN_TOKENS)) {
            set.add(AllergenTag.PEANUT)
        }
        val treeNutSearch = normName
            .replace("hindistan cevizi", "")
            .replace("yer fistikli", "")
            .replace("yer fistigi", "")
            .replace("fistik ezmesi", "")
            .replace("fistik ezmeli", "")
        if (containsAnyToken(treeNutSearch, TREE_NUT_ALLERGEN_TOKENS)) {
            set.add(AllergenTag.TREE_NUTS)
        }
        if (containsAnyToken(normName, SOY_ALLERGEN_TOKENS)) {
            set.add(AllergenTag.SOY)
        }
        if (containsAnyToken(normName, SESAME_ALLERGEN_TOKENS) || containsAnyToken(paddedName, SESAME_WORD_TOKENS)) {
            set.add(AllergenTag.SESAME)
        }
        if (containsAnyToken(normName, LEGUME_ALLERGEN_TOKENS)) {
            set.add(AllergenTag.LEGUMES)
        }
        return set
    }

    private fun computePortionConstraint(
        food: FoodItem,
        role: MealRole,
        normName: String,
        paddedName: String,
    ): PortionConstraint {
        val defaultG = (if (food.defaultUnitGrams.isFinite()) food.defaultUnitGrams else 100f).coerceAtLeast(10f)
        val isProteinPowder = food.proteinPer100g >= 55f && !food.isLiquid
        val isPuddingOrCup = normName.contains("puding") || normName.contains("sutlac") || normName.contains("muhallebi") || normName.contains("kazandibi")
        val isCheese = normName.contains("peynir") || normName.contains("kasar") || normName.contains("ezine") || normName.contains("lor")
        val isBread = normName.contains("ekmek") || normName.contains("lavas") || normName.contains("simit")
        val isOat = normName.contains("yulaf") || normName.contains("musli") || normName.contains("granola")
        val isEgg = normName.contains("yumurta") && !normName.contains("menemen")
        val isOlive = normName.contains("zeytin") && !normName.contains("zeytinyagli")
        val isAvocado = normName.contains("avokado")
        val isSweetSpread = containsAnyToken(normName, SWEET_SPREAD_BREAKFAST_TOKENS) || containsAnyToken(paddedName, HONEY_WORD_TOKENS)
        val isKaymak = normName.contains("kaymak") && !normName.contains("kaymaksiz")
        val isPickleOrSpicyMeze = normName.contains("tursu") || normName.contains("acili ezme")
        val isHumusOrDip = normName.contains("humus") || normName.contains("haydari")

        return when {
            isProteinPowder -> PortionConstraint(minGrams = 20f, maxGrams = 45f, typicalGrams = 30f, gramStep = 5f)
            isPuddingOrCup -> PortionConstraint(
                minGrams = (defaultG * 0.5f).coerceIn(50f, 120f),
                maxGrams = (defaultG * 1.5f).coerceIn(100f, 220f),
                typicalGrams = defaultG.coerceIn(80f, 180f),
                gramStep = 10f,
            )
            isEgg -> PortionConstraint(minGrams = 50f, maxGrams = 200f, typicalGrams = 100f, gramStep = 50f)
            isCheese -> PortionConstraint(minGrams = 25f, maxGrams = 120f, typicalGrams = 45f, gramStep = 10f)
            isBread -> PortionConstraint(minGrams = 25f, maxGrams = 120f, typicalGrams = 50f, gramStep = 10f)
            isOat -> PortionConstraint(minGrams = 30f, maxGrams = 100f, typicalGrams = 50f, gramStep = 10f)
            isOlive -> PortionConstraint(minGrams = 15f, maxGrams = 50f, typicalGrams = 25f, gramStep = 5f)
            isAvocado -> PortionConstraint(minGrams = 40f, maxGrams = 150f, typicalGrams = 75f, gramStep = 15f)
            isSweetSpread || isKaymak -> PortionConstraint(minGrams = 15f, maxGrams = 45f, typicalGrams = 25f, gramStep = 5f)
            isPickleOrSpicyMeze -> PortionConstraint(minGrams = 30f, maxGrams = 90f, typicalGrams = 50f, gramStep = 10f)
            isHumusOrDip -> PortionConstraint(minGrams = 45f, maxGrams = 130f, typicalGrams = 75f, gramStep = 15f)
            role == MealRole.MAIN_PROTEIN -> PortionConstraint(
                minGrams = 80f,
                maxGrams = 260f,
                typicalGrams = defaultG.coerceIn(100f, 180f),
                gramStep = 10f,
            )
            role == MealRole.MAIN_COMPOSITE_DISH -> PortionConstraint(
                minGrams = 120f,
                maxGrams = 360f,
                typicalGrams = defaultG.coerceIn(160f, 280f),
                gramStep = 20f,
            )
            role == MealRole.CARB_BASE -> PortionConstraint(
                minGrams = 60f,
                maxGrams = 300f,
                typicalGrams = defaultG.coerceIn(100f, 180f),
                gramStep = 10f,
            )
            role == MealRole.SOUP -> PortionConstraint(
                minGrams = 150f,
                maxGrams = 350f,
                typicalGrams = defaultG.coerceIn(200f, 260f),
                gramStep = 25f,
            )
            role == MealRole.DAIRY_PROBIOTIC -> {
                if (food.isLiquid || normName.contains("ayran") || normName.contains("kefir") || normName.contains("sut")) {
                    PortionConstraint(minGrams = 150f, maxGrams = 350f, typicalGrams = 200f, gramStep = 25f)
                } else {
                    PortionConstraint(minGrams = 80f, maxGrams = 260f, typicalGrams = 150f, gramStep = 10f)
                }
            }
            role == MealRole.VEG_SALAD_SIDE -> PortionConstraint(
                minGrams = 80f,
                maxGrams = 300f,
                typicalGrams = defaultG.coerceIn(120f, 220f),
                gramStep = 20f,
            )
            role == MealRole.FRUIT -> PortionConstraint(
                minGrams = 80f,
                maxGrams = 250f,
                typicalGrams = defaultG.coerceIn(110f, 180f),
                gramStep = 15f,
            )
            role == MealRole.HEALTHY_FAT_NUT -> PortionConstraint(
                minGrams = 10f,
                maxGrams = 45f,
                typicalGrams = 25f,
                gramStep = 5f,
            )
            role == MealRole.SNACK_SWEET -> PortionConstraint(
                minGrams = 20f,
                maxGrams = 70f,
                typicalGrams = defaultG.coerceIn(25f, 50f),
                gramStep = 5f,
            )
            else -> PortionConstraint(
                minGrams = 100f,
                maxGrams = 300f,
                typicalGrams = defaultG.coerceIn(150f, 250f),
                gramStep = 25f,
            )
        }
    }

    private fun containsAnyToken(text: String, tokens: Array<String>): Boolean {
        for (token in tokens) {
            if (text.contains(token)) return true
        }
        return false
    }

    private fun computeSignature(foods: List<FoodItem>): Int {
        var h = foods.size
        if (foods.isNotEmpty()) {
            val first = foods.first()
            val last = foods.last()
            val mid = foods[foods.size / 2]
            h = 31 * h + first.id.hashCode() + first.name.hashCode()
            h = 31 * h + last.id.hashCode() + last.name.hashCode()
            h = 31 * h + mid.id.hashCode() + mid.name.hashCode()
        }
        return h
    }

    private companion object {
        val INGREDIENT_ONLY_SUBSTRING_TOKENS = arrayOf(
            "aycicek yagi", "aycicekyagi", "zeytinyagi", "tereyagi", "margarin", "pane harci", "dokme cay",
            "bulyon", "nisasta", "kabartma", "toz seker", "esmer seker",
            "sirke", "nar eksisi", "ketcap", "mayonez", "hardal", "salca", "bugday unu",
            "misir unu", "tam bugday unu", "galeta unu", "kakao tozu",
            "karabiber", "kimyon", "kekik", "pul biber", "sumak", "nane kuru", "sogan", "sarimsak",
        )
        val INGREDIENT_ONLY_WORD_TOKENS = arrayOf(
            " un ", " tuz ", " maya ", " krema ", " baharat ",
        )
        val RAW_LEMON_WORD_TOKENS = arrayOf(" limon ")
        val ALCOHOL_WORD_TOKENS = arrayOf(" bira ", " sarap ", " raki ", " viski ", " votka ", " cin ")
        val DRY_OR_EXCLUDED_DRINK_TOKENS = arrayOf(
            "dokme cay", "poset cay", "granul kahve", "turk kahvesi kuru",
            "enerji icecegi", "red bull",
        )
        val HERB_OR_CHILI_VEG_TOKENS = arrayOf("maydanoz", "dereotu", "nane", "aci biber")
        val RAW_EDIBLE_VEG_TOKENS = arrayOf(
            "domates", "salatalik", "marul", "roka", "havuc",
            "tere", "turp", "semizotu", "avokado", "yesil biber", "kapya", "carliston", "sivri biber",
            "gobek", "kivircik", "kirmizi lahana",
        )
        val DAIRY_ROLE_TOKENS = arrayOf("yogurt", "ayran", "kefir", "cacik", "haydari", "peynir", "kasar", "ezine", "lor", "labne")
        val PROTEIN_DRINK_TOKENS = arrayOf("sut", "ayran", "kefir", "probiyotik", "protein")
        val CARB_SIDE_DISH_TOKENS = arrayOf("pilav", "makarna", "eriste", "kuskus", "patates", "kisir", "bulgur", "sehriye")
        val LIGHT_VEG_DISH_TOKENS = arrayOf("zeytinyagli", "salata", "brokoli", "ispanak", "kabak", "taze fasulye", "bamya", "pirasa", "enginar", "kereviz", "semizotu", "bezelye")
        val EGG_MAIN_TOKENS = arrayOf("yumurta", "omlet", "menemen", "cilbir")
        val OAT_CEREAL_TOKENS = arrayOf("yulaf", "musli", "granola", "gevrek", "corn flakes", "cheerios")
        val OLIVE_FAT_TOKENS = arrayOf("zeytin", "avokado", "fistik ezmesi", "badem ezmesi", "tahin")
        val SWEET_SPREAD_BREAKFAST_TOKENS = arrayOf("recel", "pekmez", "helva", "nutella", "sarelle", "banada", "findik kremasi")
        val PASTRY_MAIN_TOKENS = arrayOf("borek", "gozleme", "lahmacun", "pide", "pizza", "tost", "sandvic")
        val NUT_SEED_TOKENS = arrayOf("badem", "ceviz", "findik", "fistik", "kaju", "cekirdek", "chia", "keten", "kabak cekirdegi", "ezme")
        val PACKAGED_SNACK_TOKENS = arrayOf("biskuvi", "cips", "gofret", "bar", "cikolata", "kraker")
        val FAST_FOOD_TOKENS = arrayOf("hamburger", "cheeseburger", "pizza", "hot dog", "nugget", "patates kizartma", "sandvic")
        val PROCESSED_MEAT_TOKENS = arrayOf("salam", "sosis", "sucuk", "nugget", "jambon", "pepperoni")

        val FISH_TOKENS = arrayOf("balik", "somon", "levrek", "cipura", "hamsi", "palamut", "sardalya", "uskumru", "karides", "kalamar", "midye", "alabalik", "istavrit", "mezgit", "lufer")
        val FISH_WORD_TOKENS = arrayOf(" ton ")
        val POULTRY_TOKENS = arrayOf("tavuk", "pilic", "hindi", "banvit", "keskinoglu")
        val EXPLICIT_RED_MEAT_TOKENS = arrayOf(
            "dana", "kuzu", "sigir", "pastirma", "sucuk", "salam", "sosis", "jambon", "pepperoni",
            "etli", "et sote", "kiymali", "kiyma", "kavurma", "antrikot", "bonfile", "biftek", "pirzola",
            "iskembe", "kelle", "paca", "iskender", "tas kebabi", "adana", "urfa", "beyti",
            "karniyarik", "hunkar", "alinazik", "manti", "lahmacun", "dugun corbasi", "icli kofte",
        )
        val GENERIC_MEAT_DISH_TOKENS = arrayOf("kofte", "kebap", "doner", "hamburger", "tantuni")
        // Do NOT include "sebzeli" or bare "soya" here — "Sebzeli Tavuk" and "Soya Soslu Dana Eti" contain meat!
        val PLANT_PROTEIN_TOKENS = arrayOf("bitkisel", "vegan", "soya kofte", "soya kiyma", "soya eti", "tofu", "tempeh", "seitan", "etsiz")
        val HONEY_WORD_TOKENS = arrayOf(" bal ", " bali ", " balli ")
        val HONEY_SUBSTRING_TOKENS = arrayOf("cicek bali", "cam bali", "suzme bal", "karakovan")
        val PLANT_MILK_TOKENS = arrayOf("badem sutu", "yulaf sutu", "soya sutu", "hindistan cevizi sutu", "bitkisel sut", "yulafli icecek")
        val DAIRY_ALLERGEN_TOKENS = arrayOf(
            "sut", "yogurt", "peynir", "ayran", "kefir", "kasar", "lor", "ezine", "labne",
            "krema", "tereyag", "cacik", "haydari", "kaymak", "sutlac", "muhallebi", "kazandibi",
            "dondurma", "whey", "tulum", "hellim", "cheddar", "mozzarella", "yayla corbasi",
            "kremali", "manti", "alinazik", "hunkar", "iskender", "milk", "probiyotik",
            "kunefe", "puding", "danette", "cornetto", "magnum", "algida", "kinder", "nutella", "sarelle", "banada",
        )
        val GLUTEN_ALLERGEN_TOKENS = arrayOf(
            "ekmek", "bugday", "bulgur", "makarna", "borek", "simit", "pogaca", "gozleme",
            "katmer", "manti", "lahmacun", "pide", "pizza", "hamburger", "tost", "sandvic",
            "biskuvi", "kraker", "kek", "pasta", "baklava", "kuskus", "sehriye", "eriste",
            "tarhana", "galeta", "lavas", "yufka", "arpa", "cavdar", "irmik", "gevrek",
            "musli", "granola", "gofret", "kurabiye", "waffle", "krep", "pankek", "ezogelin",
            "nugget", "icli kofte", "kisir", "cig kofte", "mercimek kofte", "unlu", "kepekli",
            "kunefe", "kadayif", "sekerpare", "lokma", "cornetto", "un helva", "un corba", "un kurabiye",
        )
        val EGG_ALLERGEN_TOKENS = arrayOf("yumurta", "omlet", "menemen", "cilbir", "mayonez", "krep", "pankek", "waffle", "kek", "manti")
        val TREE_NUT_ALLERGEN_TOKENS = arrayOf(
            "badem", "ceviz", "findik", "antep fistigi", "sam fistigi", "kaju", "kuruyemis",
            "asorti", "pikan", "fistikli", "kestane", "nutella", "sarelle", "banada", "kinder",
        )
        val PEANUT_ALLERGEN_TOKENS = arrayOf("yer fistik", "fistik ezme")
        val SOY_ALLERGEN_TOKENS = arrayOf("soya", "tofu", "edamame")
        val SESAME_ALLERGEN_TOKENS = arrayOf("susam", "tahin", "humus", "simit", "kurek helva", "yaz helva")
        val SESAME_WORD_TOKENS = arrayOf(" susam ")
        val LEGUME_ALLERGEN_TOKENS = arrayOf("mercimek", "fasulye", "nohut", "barbunya", "bezelye", "bakla", "borulce", "humus", "falafel", "ezogelin", "soya")
    }
}
