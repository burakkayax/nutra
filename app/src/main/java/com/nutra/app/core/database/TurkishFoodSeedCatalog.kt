package com.nutra.app.core.database

import android.content.Context
import com.nutra.app.domain.model.AllergenTag
import com.nutra.app.domain.model.FoodCategory
import com.nutra.app.domain.model.FoodDietFlags
import com.nutra.app.domain.model.FoodItem
import com.nutra.app.domain.model.MealType
import com.nutra.app.domain.model.ServingUnit
import com.nutra.app.domain.model.ServingUnitLabels
import com.nutra.app.domain.usecase.TurkishTextNormalizer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream

@Serializable
internal data class SeedCatalogPayloadDto(
    val catalogVersion: Int = 3,
    val foods: List<SeedFoodDto> = emptyList(),
)

@Serializable
internal data class SeedServingDto(
    val l: String,
    val g: Float,
)

@Serializable
internal data class SeedFoodDto(
    val code: String,
    val name: String,
    val subtitle: String,
    val category: String,
    val baseUnit: String = "g",
    val kcal: Int,
    val protein: Float,
    val carbs: Float,
    val fat: Float,
    val defaultUnitLabel: String,
    val defaultUnitGrams: Float,
    val alternateUnits: List<SeedServingDto> = emptyList(),
    val preferredMeals: List<String> = emptyList(),
    val brand: String? = null,
    val barcode: String? = null,
    val preparationState: String? = null,
    val isFavorite: Boolean = false,
    val searchKeywords: String = "",
    val saturatedFat: Float? = null,
    val sugar: Float? = null,
    val fiber: Float? = null,
    val salt: Float? = null,
    val sodiumMg: Float? = null,
    val calciumMg: Float? = null,
    val potassiumMg: Float? = null,
    val ironMg: Float? = null,
    val vitaminCMg: Float? = null,
    val allergens: List<String> = emptyList(),
    val dietFlags: List<String> = emptyList(),
)

private data class SeedCatalogRecord(
    val item: FoodItem,
    val normalizedSearchText: String,
)

object TurkishFoodSeedCatalog {

    const val CATALOG_ASSET_NAME: String = "turkish_food_catalog.json"
    const val EXPECTED_CATALOG_SIZE: Int = 621
    const val CURRENT_CATALOG_VERSION: Int = 3

    private val catalogJson = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val CATEGORY_BY_NAME: Map<String, FoodCategory> = FoodCategory.entries.associateBy { it.name }
    private val MEAL_BY_NAME: Map<String, MealType> = MealType.entries.associateBy { it.name }
    private val ALLERGEN_BY_NAME: Map<String, AllergenTag> = AllergenTag.entries.associateBy { it.name }

    /**
     * Maps normalized names from the legacy 38-item starter seed to stable catalog codes (`TRxxxx`)
     * so existing v3 installs preserve favorites and historical meal log links on upgrade.
     */
    val LEGACY_NAME_TO_CATALOG_CODE: Map<String, String> = mapOf(
        "haslanmis yumurta" to "TR0460",
        "ezine beyaz peynir" to "TR0850",
        "tam bugday ekmegi" to "TR0122",
        "siyah zeytin" to "TR0332",
        "yulaf ezmesi" to "TR0531",
        "menemen" to "TR0600",
        "taze kasar peyniri" to "TR0849",
        "kahvaltilik lor peyniri" to "TR0467",
        "susamli simit" to "TR0120",
        "avokado" to "TR0341",
        "mercimek corbasi" to "TR0678",
        "ezogelin corbasi" to "TR0673",
        "yayla corbasi" to "TR0683",
        "tavuk suyu corbasi" to "TR0682",
        "izgara tavuk gogsu" to "TR0186",
        "izgara kofte" to "TR0582",
        "izgara somon fileto" to "TR0183",
        "dana bonfile antrikot izgara" to "TR0828",
        "ton baligi suda suzulmus" to "TR0001",
        "bulgur pilavi" to "TR0562",
        "tereyagli pirinc pilavi" to "TR0619",
        "etli kuru fasulye" to "TR0593",
        "etli nohut yemegi" to "TR0605",
        "zeytinyagli taze fasulye" to "TR0630",
        "karniyarik" to "TR0587",
        "kayseri mantisi yogurtlu" to "TR0599",
        "suzme yogurt" to "TR0853",
        "coban salata" to "TR0565",
        "humus" to "TR0575",
        "yesil elma" to "TR0344",
        "muz yerli" to "TR0361",
        "cig badem" to "TR0320",
        "ceviz ici" to "TR0322",
        "sekersiz yer fistigi ezmesi" to "TR0326",
        "ayran" to "TR0459",
        "sade kefir" to "TR0464",
    )

    @Volatile
    private var cachedRecords: List<SeedCatalogRecord>? = null

    val initialFoods: List<FoodItem>
        get() = loadRecords(context = null).map { it.item }

    fun loadFoods(context: Context? = null): List<FoodItem> =
        loadRecords(context).map { it.item }

    fun toEntities(context: Context? = null): List<FoodItemEntity> =
        loadRecords(context).map { record ->
            FoodItemEntity.fromDomain(record.item, record.normalizedSearchText)
        }

    fun releaseCache() {
        cachedRecords = null
    }

    private fun loadRecords(context: Context?): List<SeedCatalogRecord> {
        cachedRecords?.let { return it }
        synchronized(this) {
            cachedRecords?.let { return it }
            val rawJson = openCatalogStream(context)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: error("Besin kataloğu asset dosyası bulunamadı: $CATALOG_ASSET_NAME")
            val payload = catalogJson.decodeFromString<SeedCatalogPayloadDto>(rawJson)
            val version = payload.catalogVersion.coerceAtLeast(CURRENT_CATALOG_VERSION)
            val parsed = payload.foods.map { dto -> toRecord(dto, version) }
            cachedRecords = parsed
            return parsed
        }
    }

    private fun openCatalogStream(context: Context?): InputStream? {
        if (context != null) {
            runCatching { context.assets.open(CATALOG_ASSET_NAME) }.getOrNull()?.let { return it }
        }
        val classLoader = TurkishFoodSeedCatalog::class.java.classLoader
        return classLoader?.getResourceAsStream(CATALOG_ASSET_NAME)
            ?: TurkishFoodSeedCatalog::class.java.getResourceAsStream("/$CATALOG_ASSET_NAME")
    }

    private fun toRecord(dto: SeedFoodDto, catalogVersion: Int): SeedCatalogRecord {
        val category = CATEGORY_BY_NAME[dto.category] ?: FoodCategory.HOME_COOKED
        val baseUnit = ServingUnitLabels.normalizedBaseUnit(dto.baseUnit)
        val preferredMeals = dto.preferredMeals.mapNotNull { MEAL_BY_NAME[it] }.toSet()
        val alternates = dto.alternateUnits.map { ServingUnit(label = it.l, grams = it.g) }
        val normalizedName = TurkishTextNormalizer.normalize(dto.name)
        // Only index food-intrinsic tokens (name, brand, searchKeywords, code) —
        // never category titles or broad subtitles that cause cross-allergen contamination.
        val searchParts = buildString(128) {
            append(dto.name)
            if (!dto.brand.isNullOrBlank()) {
                append(' ')
                append(dto.brand)
            }
            if (dto.searchKeywords.isNotBlank()) {
                append(' ')
                append(dto.searchKeywords)
            }
            if (dto.code.isNotBlank()) {
                append(' ')
                append(dto.code)
            }
        }
        val normalizedSearch = TurkishTextNormalizer.normalize(searchParts)
        val allergens = dto.allergens.mapNotNull { ALLERGEN_BY_NAME[it.trim().uppercase()] }.toSet()
        val dietFlags = FoodDietFlags.fromTokens(dto.dietFlags)

        val item = FoodItem(
            name = dto.name,
            subtitle = dto.subtitle,
            category = category,
            caloriesPer100g = dto.kcal.coerceAtLeast(0),
            proteinPer100g = dto.protein.coerceAtLeast(0f),
            carbsPer100g = dto.carbs.coerceAtLeast(0f),
            fatPer100g = dto.fat.coerceAtLeast(0f),
            defaultUnitLabel = dto.defaultUnitLabel,
            defaultUnitGrams = dto.defaultUnitGrams.coerceAtLeast(1f),
            alternateUnits = alternates,
            preferredMealTypes = preferredMeals,
            barcode = dto.barcode?.takeIf { it.isNotBlank() },
            isFavorite = dto.isFavorite,
            isCustom = false,
            catalogCode = dto.code.takeIf { it.isNotBlank() },
            brand = dto.brand?.takeIf { it.isNotBlank() },
            baseUnit = baseUnit,
            saturatedFatPer100g = dto.saturatedFat,
            sugarPer100g = dto.sugar,
            fiberPer100g = dto.fiber,
            saltPer100g = dto.salt,
            sodiumMgPer100g = dto.sodiumMg,
            calciumMgPer100g = dto.calciumMg,
            potassiumMgPer100g = dto.potassiumMg,
            ironMgPer100g = dto.ironMg,
            vitaminCMgPer100g = dto.vitaminCMg,
            preparationState = dto.preparationState?.takeIf { it.isNotBlank() },
            allergens = allergens,
            dietFlags = dietFlags,
            allergenVerified = true,
            customUid = null,
            catalogVersion = catalogVersion,
            normalizedName = normalizedName,
            normalizedSearchText = normalizedSearch,
        )
        return SeedCatalogRecord(item = item, normalizedSearchText = normalizedSearch)
    }
}
