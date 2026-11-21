package com.nutra.app.domain.intelligence

import com.nutra.app.domain.model.AllergenTag
import com.nutra.app.domain.model.DietStyle
import com.nutra.app.domain.model.DietaryProfile
import com.nutra.app.domain.model.FoodCategory
import com.nutra.app.domain.model.FoodFeedbackRecord
import com.nutra.app.domain.model.FoodFeedbackStatus
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
class FoodCulinaryClassifier @Inject constructor() {

    @Volatile
    private var lastCatalogSignature: Int = 0

    @Volatile
    private var cachedMap: Map<Long, ClassifiedFood> = emptyMap()

    fun foodFeedbackKey(food: FoodItem): String =
        food.catalogCode?.takeIf { it.isNotBlank() }
            ?: "custom:${food.id}:${TurkishTextNormalizer.normalize(food.name)}"

    fun classifyCatalog(foods: List<FoodItem>): List<ClassifiedFood> {
        val signature = computeSignature(foods)
        val existing = cachedMap
        if (signature == lastCatalogSignature && existing.size == foods.size) {
            val out = ArrayList<ClassifiedFood>(foods.size)
            var allHit = true
            for (food in foods) {
                val c = existing[food.id]
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
        val nextMap = HashMap<Long, ClassifiedFood>(foods.size.coerceAtLeast(16))
        for (food in foods) {
            val item = classifySingle(food)
            classified.add(item)
            if (food.id > 0L) nextMap[food.id] = item
        }
        cachedMap = nextMap
        lastCatalogSignature = signature
        return classified
    }

    fun classifySingle(food: FoodItem): ClassifiedFood {
        val normName = food.normalizedName.ifBlank { TurkishTextNormalizer.normalize(food.name) }
        val normFull = food.normalizedSearchText.ifBlank {
            TurkishTextNormalizer.normalize("${food.name} ${food.subtitle} ${food.brand.orEmpty()}")
        }
        val prep = TurkishTextNormalizer.normalize(food.preparationState.orEmpty())

        val edibility = resolveEdibility(food, normName, prep)
        val role = if (edibility == EdibilityStatus.COOKING_INGREDIENT_ONLY) {
            MealRole.INGREDIENT_ONLY
        } else {
            resolveMealRole(food, normName)
        }

        val containsFish = containsAnyToken(normFull, FISH_TOKENS)
        val containsPoultry = containsAnyToken(normFull, POULTRY_TOKENS)
        val containsRedMeat = containsAnyToken(normFull, RED_MEAT_TOKENS) ||
            (food.category == FoodCategory.MEAT_POULTRY && !containsFish && !containsPoultry && !containsAnyToken(normFull, PLANT_PROTEIN_TOKENS))

        val allergens = detectAllergens(food, normName, normFull, containsFish)
        val containsHoney = containsAnyToken(normName, HONEY_TOKENS)
        val containsAnimalProduct = containsRedMeat || containsPoultry || containsFish ||
            AllergenTag.DAIRY in allergens || AllergenTag.EGG in allergens || containsHoney

        val isTurkishHomeCooked = food.brand == null && (
            food.category == FoodCategory.HOME_COOKED ||
                food.category == FoodCategory.SOUP ||
                food.category == FoodCategory.SALAD_MEZE ||
                (food.category == FoodCategory.MEAT_POULTRY && edibility == EdibilityStatus.READY_TO_EAT) ||
                (food.category == FoodCategory.BREAKFAST && !containsAnyToken(normName, PACKAGED_SNACK_TOKENS))
            )

        val isWholeFood = food.brand == null &&
            food.category != FoodCategory.SNACKS_SWEETS &&
            food.category != FoodCategory.OILS_SAUCES &&
            !containsAnyToken(normName, PROCESSED_MEAT_TOKENS)

        val effectiveSodium = food.sodiumMgPer100g ?: food.saltPer100g?.let { it * 393.4f }

        val portionConstraint = computePortionConstraint(food, role, normName)

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
    ): Boolean {
        if (classified.edibility != EdibilityStatus.READY_TO_EAT) return false

        val fb = feedbackByKey[classified.feedbackKey]
        if (fb?.status == FoodFeedbackStatus.DISLIKED) return false

        // Hard allergen exclusions
        for (excluded in dietaryProfile.excludedAllergens) {
            if (excluded in classified.allergens) return false
        }

        // Diet style hard rules
        when (dietaryProfile.dietStyle) {
            DietStyle.STANDARD, DietStyle.MEDITERRANEAN, DietStyle.LOW_CARB -> Unit
            DietStyle.VEGETARIAN -> {
                if (classified.containsRedMeat || classified.containsPoultry || classified.containsFish) return false
            }
            DietStyle.VEGAN -> {
                if (classified.containsAnimalProduct) return false
            }
            DietStyle.PESCATARIAN -> {
                if (classified.containsRedMeat || classified.containsPoultry) return false
            }
        }

        // User-excluded custom keywords
        if (dietaryProfile.excludedKeywords.isNotEmpty()) {
            val searchTarget = classified.food.normalizedSearchText.ifBlank {
                TurkishTextNormalizer.normalize("${classified.food.name} ${classified.food.subtitle}")
            }
            for (kw in dietaryProfile.excludedKeywords) {
                val normKw = TurkishTextNormalizer.normalize(kw)
                if (normKw.length >= 2 && searchTarget.contains(normKw)) {
                    return false
                }
            }
        }

        return true
    }

    private fun resolveEdibility(food: FoodItem, normName: String, prep: String): EdibilityStatus {
        if (food.caloriesPer100g <= 2 && food.proteinPer100g < 0.5f && food.carbsPer100g < 0.5f && food.fatPer100g < 0.5f) {
            return EdibilityStatus.COOKING_INGREDIENT_ONLY
        }
        if (food.category == FoodCategory.OILS_SAUCES) {
            return EdibilityStatus.COOKING_INGREDIENT_ONLY
        }
        if (containsAnyToken(normName, INGREDIENT_ONLY_TOKENS)) {
            // Allow ready-to-eat dishes that happen to mention olive oil ("zeytinyagli ...")
            if (!normName.contains("zeytinyagli") && !normName.contains("tereyagli")) {
                return EdibilityStatus.COOKING_INGREDIENT_ONLY
            }
        }
        // Raw meat/fish/poultry or minced raw meat
        if (food.category == FoodCategory.MEAT_POULTRY) {
            if (prep.contains("cig") || normName.contains("cig") || normName.contains("kiyma")) {
                return EdibilityStatus.COOKING_INGREDIENT_ONLY
            }
        }
        // Dry uncooked grains/legumes/pasta/flour (except oats, muesli, bread, cooked pilav/makarna)
        if (food.category == FoodCategory.GRAINS_BAKERY) {
            if (prep.contains("kuru") || prep.contains("cig") || normName.contains("pismemis") || normName.contains("kuru")) {
                if (!normName.contains("yulaf") && !normName.contains("musli") && !normName.contains("gevrek") && !normName.contains("ekmek")) {
                    return EdibilityStatus.COOKING_INGREDIENT_ONLY
                }
            }
        }
        // Raw vegetables that are NOT normally eaten raw standalone
        if (food.category == FoodCategory.VEGETABLES && (prep.contains("cig") || prep.contains("kuru") || prep.contains("dondurulmus"))) {
            if (!containsAnyToken(normName, RAW_EDIBLE_VEG_TOKENS)) {
                return EdibilityStatus.COOKING_INGREDIENT_ONLY
            }
        }
        // Raw meat-free cig kofte is ready to eat!
        if (food.category == FoodCategory.SALAD_MEZE && normName.contains("cig kofte")) {
            return EdibilityStatus.READY_TO_EAT
        }
        if (food.category == FoodCategory.DRINKS && containsAnyToken(normName, DRY_DRINK_TOKENS)) {
            return EdibilityStatus.COOKING_INGREDIENT_ONLY
        }
        return EdibilityStatus.READY_TO_EAT
    }

    private fun resolveMealRole(food: FoodItem, normName: String): MealRole {
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
                normName.contains("kisir") || normName.contains("cig kofte") -> MealRole.CARB_BASE
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
                containsAnyToken(normName, OLIVE_FAT_TOKENS) -> MealRole.HEALTHY_FAT_NUT
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

    private fun detectAllergens(
        food: FoodItem,
        normName: String,
        normFull: String,
        containsFish: Boolean,
    ): Set<AllergenTag> {
        val set = HashSet<AllergenTag>(4)
        val isPlantMilk = containsAnyToken(normName, PLANT_MILK_TOKENS)
        if (!isPlantMilk && containsAnyToken(normFull, DAIRY_ALLERGEN_TOKENS)) {
            set.add(AllergenTag.DAIRY)
        }
        val isGlutenFree = normFull.contains("glutensiz")
        if (!isGlutenFree && containsAnyToken(normFull, GLUTEN_ALLERGEN_TOKENS)) {
            set.add(AllergenTag.GLUTEN)
        }
        if (containsAnyToken(normFull, EGG_ALLERGEN_TOKENS)) {
            set.add(AllergenTag.EGG)
        }
        if (containsFish) {
            set.add(AllergenTag.FISH_SEAFOOD)
        }
        if (containsAnyToken(normFull, TREE_NUT_ALLERGEN_TOKENS)) {
            set.add(AllergenTag.TREE_NUTS)
        }
        if (containsAnyToken(normFull, PEANUT_ALLERGEN_TOKENS)) {
            set.add(AllergenTag.PEANUT)
        }
        if (containsAnyToken(normFull, SOY_ALLERGEN_TOKENS)) {
            set.add(AllergenTag.SOY)
        }
        if (containsAnyToken(normFull, SESAME_ALLERGEN_TOKENS)) {
            set.add(AllergenTag.SESAME)
        }
        if (containsAnyToken(normFull, LEGUME_ALLERGEN_TOKENS)) {
            set.add(AllergenTag.LEGUMES)
        }
        return set
    }

    private fun computePortionConstraint(
        food: FoodItem,
        role: MealRole,
        normName: String,
    ): PortionConstraint {
        val defaultG = food.defaultUnitGrams.coerceAtLeast(10f)
        val isCheese = normName.contains("peynir") || normName.contains("kasar") || normName.contains("ezine") || normName.contains("lor")
        val isBread = normName.contains("ekmek") || normName.contains("lavas") || normName.contains("simit")
        val isOat = normName.contains("yulaf") || normName.contains("musli") || normName.contains("granola")
        val isEgg = normName.contains("yumurta") && !normName.contains("menemen")
        val isOlive = normName.contains("zeytin") && !normName.contains("zeytinyagli")
        val isAvocado = normName.contains("avokado")

        return when {
            isEgg -> PortionConstraint(minGrams = 50f, maxGrams = 200f, typicalGrams = 100f, gramStep = 50f)
            isCheese -> PortionConstraint(minGrams = 25f, maxGrams = 120f, typicalGrams = 45f, gramStep = 10f)
            isBread -> PortionConstraint(minGrams = 25f, maxGrams = 120f, typicalGrams = 50f, gramStep = 10f)
            isOat -> PortionConstraint(minGrams = 30f, maxGrams = 100f, typicalGrams = 50f, gramStep = 10f)
            isOlive -> PortionConstraint(minGrams = 15f, maxGrams = 50f, typicalGrams = 25f, gramStep = 5f)
            isAvocado -> PortionConstraint(minGrams = 40f, maxGrams = 150f, typicalGrams = 75f, gramStep = 15f)
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
            h = 31 * h + foods.first().id.hashCode()
            h = 31 * h + foods.last().id.hashCode()
            h = 31 * h + foods[foods.size / 2].id.hashCode()
        }
        return h
    }

    private companion object {
        val INGREDIENT_ONLY_TOKENS = arrayOf(
            "aycicek yag", "zeytinyagi", "tereyagi", "margarin", "pane harci", "dokme cay",
            "bulyon", "nisasta", "kabartma", "maya", "toz seker", "esmer seker", "tuz ",
            "sirke", "nar eksisi", "ketcap", "mayonez", "hardal", "salca", "un ", "bugday unu",
            "misir unu", "tam bugday unu", "galeta unu", "kakao tozu", "krema", "baharat",
            "karabiber", "kimyon", "kekik", "pul biber", "sumak", "nane kuru", "sogan", "sarimsak",
        )
        val RAW_EDIBLE_VEG_TOKENS = arrayOf(
            "domates", "salatalik", "marul", "roka", "havuc", "maydanoz", "dereotu",
            "tere", "turp", "semizotu", "avokado", "yesil biber", "kapya", "carliston", "sivri biber",
            "gobek", "kivircik", "kirmizi lahana",
        )
        val DRY_DRINK_TOKENS = arrayOf("dokme cay", "poset cay", "granul kahve", "turk kahvesi kuru")
        val DAIRY_ROLE_TOKENS = arrayOf("yogurt", "ayran", "kefir", "cacik", "haydari", "peynir", "kasar", "ezine", "lor", "labne")
        val PROTEIN_DRINK_TOKENS = arrayOf("sut", "ayran", "kefir", "probiyotik", "protein")
        val CARB_SIDE_DISH_TOKENS = arrayOf("pilav", "makarna", "eriste", "kuskus", "patates", "kisir", "bulgur", "sehriye")
        val LIGHT_VEG_DISH_TOKENS = arrayOf("zeytinyagli", "salata", "brokoli", "ispanak", "kabak", "taze fasulye", "bamya", "pirasa", "enginar", "kereviz", "semizotu", "bezelye")
        val EGG_MAIN_TOKENS = arrayOf("yumurta", "omlet", "menemen", "cilbir")
        val OAT_CEREAL_TOKENS = arrayOf("yulaf", "musli", "granola", "gevrek", "corn flakes")
        val OLIVE_FAT_TOKENS = arrayOf("zeytin", "avokado", "fistik ezmesi", "badem ezmesi", "tahin")
        val PASTRY_MAIN_TOKENS = arrayOf("borek", "gozleme", "lahmacun", "pide", "pizza", "tost", "sandvic")
        val NUT_SEED_TOKENS = arrayOf("badem", "ceviz", "findik", "fistik", "kaju", "cekirdek", "chia", "keten", "kabak cekirdegi", "ezme")
        val PACKAGED_SNACK_TOKENS = arrayOf("biskuvi", "cips", "gofret", "bar", "cikolata", "kraker")
        val PROCESSED_MEAT_TOKENS = arrayOf("salam", "sosis", "sucuk", "nugget", "jambon")

        val FISH_TOKENS = arrayOf("balik", "ton ", "ton,", "somon", "levrek", "cipura", "hamsi", "palamut", "sardalya", "uskumru", "karides", "kalamar", "midye", "alabalik", "istavrit", "mezgit", "lufer", "dardanel")
        val POULTRY_TOKENS = arrayOf("tavuk", "pilic", "hindi", "banvit", "keskinoglu")
        val RED_MEAT_TOKENS = arrayOf(
            "dana", "kuzu", "sigir", "kofte", "kebap", "adana", "urfa", "doner", "kavurma",
            "pastirma", "sucuk", "salam", "sosis", "etli", "et sote", "kiymali", "kiyma",
            "karniyarik", "hunkar", "alinazik", "iskembe", "kelle", "paca", "manti",
            "hamburger", "lahmacun", "antrikot", "bonfile", "biftek", "pirzola", "tas kebabi",
            "dugun corbasi", "icli kofte", "beyti", "iskender", "tantuni",
        )
        val PLANT_PROTEIN_TOKENS = arrayOf("bitkisel", "vegan", "soya", "tofu", "etsiz", "sebzeli")
        val HONEY_TOKENS = arrayOf("bal ", "cicek bali", "cam bali", "suzme bal")
        val PLANT_MILK_TOKENS = arrayOf("badem sutu", "yulaf sutu", "soya sutu", "hindistan cevizi sutu", "bitkisel sut")
        val DAIRY_ALLERGEN_TOKENS = arrayOf(
            "sut", "yogurt", "peynir", "ayran", "kefir", "kasar", "lor", "ezine", "labne",
            "krema", "tereyag", "cacik", "haydari", "kaymak", "sutlac", "muhallebi", "kazandibi",
            "dondurma", "whey", "tulum", "hellim", "cheddar", "mozzarella", "yayla corbasi",
            "kremali", "manti", "alinazik", "hunkar", "iskender", "milk", "probiyotik",
        )
        val GLUTEN_ALLERGEN_TOKENS = arrayOf(
            "ekmek", "bugday", "bulgur", "makarna", "borek", "simit", "pogaca", "gozleme",
            "katmer", "manti", "lahmacun", "pide", "pizza", "hamburger", "tost", "sandvic",
            "biskuvi", "kraker", "kek", "pasta", "baklava", "kuskus", "sehriye", "eriste",
            "tarhana", "galeta", "lavas", "yufka", "arpa", "cavdar", "irmik", "gevrek",
            "musli", "granola", "gofret", "kurabiye", "waffle", "krep", "pankek", "ezogelin",
            "nugget", "icli kofte", "kisir", "cig kofte", "unlu", "kepekli",
        )
        val EGG_ALLERGEN_TOKENS = arrayOf("yumurta", "omlet", "menemen", "cilbir", "mayonez", "krep", "pankek", "waffle", "kek", "manti")
        val TREE_NUT_ALLERGEN_TOKENS = arrayOf("badem", "ceviz", "findik", "antep fistigi", "sam fistigi", "kaju", "kuruyemis", "asorti", "pikan", "fistikli")
        val PEANUT_ALLERGEN_TOKENS = arrayOf("yer fistigi", "fistik ezmesi")
        val SOY_ALLERGEN_TOKENS = arrayOf("soya", "tofu", "edamame")
        val SESAME_ALLERGEN_TOKENS = arrayOf("susam", "tahin", "helva", "humus", "simit")
        val LEGUME_ALLERGEN_TOKENS = arrayOf("mercimek", "fasulye", "nohut", "barbunya", "bezelye", "bakla", "borulce", "humus", "falafel", "ezogelin", "soya")
    }
}
