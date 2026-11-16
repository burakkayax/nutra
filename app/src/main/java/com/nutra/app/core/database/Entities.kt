package com.nutra.app.core.database

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

        val preferredMeals = if (preferredMealTypesCsv.isBlank()) {
            emptySet()
        } else {
            preferredMealTypesCsv
                .split(',')
                .mapNotNull { token ->
                    token.trim().takeIf { it.isNotEmpty() }?.let {
                        runCatching { MealType.valueOf(it) }.getOrNull()
                    }
                }
                .toSet()
        }

        val cat = runCatching { FoodCategory.valueOf(category) }.getOrDefault(FoodCategory.HOME_COOKED)

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
        mealType = runCatching { MealType.valueOf(mealType) }.getOrDefault(MealType.BREAKFAST),
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
