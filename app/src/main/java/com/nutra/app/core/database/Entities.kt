package com.nutra.app.core.database

import android.content.ContentValues
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nutra.app.domain.model.FoodCategory
import com.nutra.app.domain.model.FoodItem
import com.nutra.app.domain.model.MealLogItem
import com.nutra.app.domain.model.MealType
import com.nutra.app.domain.model.ServingUnit
import com.nutra.app.domain.model.WeightEntry
import com.nutra.app.domain.usecase.TurkishTextNormalizer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val entityJson = Json { ignoreUnknownKeys = true }
private val FOOD_CATEGORY_BY_NAME: Map<String, FoodCategory> = FoodCategory.entries.associateBy { it.name }
private val MEAL_TYPE_BY_NAME: Map<String, MealType> = MealType.entries.associateBy { it.name }

@Entity(
    tableName = "food_items",
    indices = [
        Index(value = ["normalizedSearchText"]),
        Index(value = ["category"]),
        Index(value = ["barcode"]),
    ],
)
data class FoodItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val subtitle: String,
    val normalizedSearchText: String,
    val category: String,
    val caloriesPer100g: Int,
    val proteinPer100g: Float,
    val carbsPer100g: Float,
    val fatPer100g: Float,
    val defaultUnitLabel: String,
    val defaultUnitGrams: Float,
    val alternateUnitsJson: String,
    val preferredMealTypesCsv: String,
    val barcode: String? = null,
    val isFavorite: Boolean = false,
    val isCustom: Boolean = false,
) {
    fun toDomain(): FoodItem {
        val units = if (alternateUnitsJson.isBlank() || alternateUnitsJson == "[]") {
            emptyList()
        } else {
            runCatching {
                entityJson.decodeFromString<List<ServingUnit>>(alternateUnitsJson)
            }.getOrDefault(emptyList())
        }

        val preferredMeals = when {
            preferredMealTypesCsv.isBlank() -> emptySet()
            !preferredMealTypesCsv.contains(',') -> {
                MEAL_TYPE_BY_NAME[preferredMealTypesCsv.trim()]?.let { setOf(it) } ?: emptySet()
            }
            else -> {
                preferredMealTypesCsv
                    .split(',')
                    .mapNotNull { token -> MEAL_TYPE_BY_NAME[token.trim()] }
                    .toSet()
            }
        }

        val cat = FOOD_CATEGORY_BY_NAME[category] ?: FoodCategory.HOME_COOKED

        return FoodItem(
            id = id,
            name = name,
            subtitle = subtitle,
            category = cat,
            caloriesPer100g = caloriesPer100g,
            proteinPer100g = proteinPer100g,
            carbsPer100g = carbsPer100g,
            fatPer100g = fatPer100g,
            defaultUnitLabel = defaultUnitLabel,
            defaultUnitGrams = defaultUnitGrams,
            alternateUnits = units,
            preferredMealTypes = preferredMeals,
            barcode = barcode,
            isFavorite = isFavorite,
            isCustom = isCustom,
            normalizedName = TurkishTextNormalizer.normalize(name),
            normalizedSearchText = normalizedSearchText.ifBlank {
                TurkishTextNormalizer.normalize("$name $subtitle")
            },
        )
    }

    fun toContentValues(): ContentValues = ContentValues(15).apply {
        put("name", name)
        put("subtitle", subtitle)
        put("normalizedSearchText", normalizedSearchText)
        put("category", category)
        put("caloriesPer100g", caloriesPer100g)
        put("proteinPer100g", proteinPer100g)
        put("carbsPer100g", carbsPer100g)
        put("fatPer100g", fatPer100g)
        put("defaultUnitLabel", defaultUnitLabel)
        put("defaultUnitGrams", defaultUnitGrams)
        put("alternateUnitsJson", alternateUnitsJson)
        put("preferredMealTypesCsv", preferredMealTypesCsv)
        if (barcode != null) put("barcode", barcode) else putNull("barcode")
        put("isFavorite", if (isFavorite) 1 else 0)
        put("isCustom", if (isCustom) 1 else 0)
    }

    companion object {
        fun fromDomain(item: FoodItem, normalizedText: String): FoodItemEntity = FoodItemEntity(
            id = item.id,
            name = item.name,
            subtitle = item.subtitle,
            normalizedSearchText = normalizedText,
            category = item.category.name,
            caloriesPer100g = item.caloriesPer100g,
            proteinPer100g = item.proteinPer100g,
            carbsPer100g = item.carbsPer100g,
            fatPer100g = item.fatPer100g,
            defaultUnitLabel = item.defaultUnitLabel,
            defaultUnitGrams = item.defaultUnitGrams,
            alternateUnitsJson = entityJson.encodeToString(item.alternateUnits),
            preferredMealTypesCsv = item.preferredMealTypes.joinToString(",") { it.name },
            barcode = item.barcode,
            isFavorite = item.isFavorite,
            isCustom = item.isCustom,
        )
    }
}

@Entity(
    tableName = "meal_logs",
    indices = [
        Index(value = ["dateIso"]),
        Index(value = ["mealType"]),
        Index(value = ["foodId"]),
        Index(value = ["timestampMillis"]),
    ],
)
data class MealLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val dateIso: String,
    val mealType: String,
    val foodId: Long?,
    val foodName: String,
    val unitLabel: String,
    val multiplier: Float,
    val totalGrams: Float,
    val calories: Int,
    val proteinGrams: Float,
    val carbsGrams: Float,
    val fatGrams: Float,
    val timestampMillis: Long,
) {
    fun toDomain(): MealLogItem = MealLogItem(
        id = id,
        dateIso = dateIso,
        mealType = MEAL_TYPE_BY_NAME[mealType] ?: MealType.BREAKFAST,
        foodId = foodId,
        foodName = foodName,
        unitLabel = unitLabel,
        multiplier = multiplier,
        totalGrams = totalGrams,
        calories = calories,
        proteinGrams = proteinGrams,
        carbsGrams = carbsGrams,
        fatGrams = fatGrams,
        timestampMillis = timestampMillis,
    )

    companion object {
        fun fromDomain(item: MealLogItem): MealLogEntity = MealLogEntity(
            id = item.id,
            dateIso = item.dateIso,
            mealType = item.mealType.name,
            foodId = item.foodId,
            foodName = item.foodName,
            unitLabel = item.unitLabel,
            multiplier = item.multiplier,
            totalGrams = item.totalGrams,
            calories = item.calories,
            proteinGrams = item.proteinGrams,
            carbsGrams = item.carbsGrams,
            fatGrams = item.fatGrams,
            timestampMillis = item.timestampMillis,
        )
    }
}

@Entity(tableName = "water_logs")
data class WaterLogEntity(
    @PrimaryKey
    val dateIso: String,
    val waterMl: Int,
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "weight_logs",
    indices = [Index(value = ["dateIso"])],
)
data class WeightLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val dateIso: String,
    val weightKg: Float,
    val timestampMillis: Long,
) {
    fun toDomain(): WeightEntry = WeightEntry(
        id = id,
        dateIso = dateIso,
        weightKg = weightKg,
        timestampMillis = timestampMillis,
    )
}
