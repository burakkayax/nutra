package com.nutra.app.core.database

import android.content.ContentValues
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nutra.app.domain.model.FoodCategory
import com.nutra.app.domain.model.FoodFeedbackRecord
import com.nutra.app.domain.model.FoodFeedbackStatus
import com.nutra.app.domain.model.FoodItem
import com.nutra.app.domain.model.GoalChangeSource
import com.nutra.app.domain.model.GoalHistoryEntry
import com.nutra.app.domain.model.GoalType
import com.nutra.app.domain.model.MealLogItem
import com.nutra.app.domain.model.MealType
import com.nutra.app.domain.model.MetabolismSnapshotRecord
import com.nutra.app.domain.model.ServingUnit
import com.nutra.app.domain.model.ServingUnitLabels
import com.nutra.app.domain.model.WeightEntry
import com.nutra.app.domain.usecase.TurkishTextNormalizer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val entityJson = Json { ignoreUnknownKeys = true }
private val FOOD_CATEGORY_BY_NAME: Map<String, FoodCategory> = FoodCategory.entries.associateBy { it.name }
private val MEAL_TYPE_BY_NAME: Map<String, MealType> = MealType.entries.associateBy { it.name }
private val GOAL_TYPE_BY_NAME: Map<String, GoalType> = GoalType.entries.associateBy { it.name }
private val GOAL_SOURCE_BY_NAME: Map<String, GoalChangeSource> = GoalChangeSource.entries.associateBy { it.name }
private val FEEDBACK_STATUS_BY_NAME: Map<String, FoodFeedbackStatus> = FoodFeedbackStatus.entries.associateBy { it.name }

@Entity(
    tableName = "food_items",
    indices = [
        Index(value = ["normalizedSearchText"]),
        Index(value = ["category"]),
        Index(value = ["barcode"]),
        Index(value = ["catalogCode"]),
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
    val catalogCode: String? = null,
    val brand: String? = null,
    val baseUnit: String = "g",
    val saturatedFatPer100g: Float? = null,
    val sugarPer100g: Float? = null,
    val fiberPer100g: Float? = null,
    val saltPer100g: Float? = null,
    val sodiumMgPer100g: Float? = null,
    val calciumMgPer100g: Float? = null,
    val potassiumMgPer100g: Float? = null,
    val ironMgPer100g: Float? = null,
    val vitaminCMgPer100g: Float? = null,
    val preparationState: String? = null,
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
            catalogCode = catalogCode,
            brand = brand,
            baseUnit = ServingUnitLabels.normalizedBaseUnit(baseUnit),
            saturatedFatPer100g = saturatedFatPer100g,
            sugarPer100g = sugarPer100g,
            fiberPer100g = fiberPer100g,
            saltPer100g = saltPer100g,
            sodiumMgPer100g = sodiumMgPer100g,
            calciumMgPer100g = calciumMgPer100g,
            potassiumMgPer100g = potassiumMgPer100g,
            ironMgPer100g = ironMgPer100g,
            vitaminCMgPer100g = vitaminCMgPer100g,
            preparationState = preparationState,
            normalizedName = TurkishTextNormalizer.normalize(name),
            normalizedSearchText = normalizedSearchText.ifBlank {
                TurkishTextNormalizer.normalize("$name ${brand.orEmpty()} $subtitle")
            },
        )
    }

    fun toContentValues(): ContentValues = ContentValues(28).apply {
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
        if (catalogCode != null) put("catalogCode", catalogCode) else putNull("catalogCode")
        if (brand != null) put("brand", brand) else putNull("brand")
        put("baseUnit", ServingUnitLabels.normalizedBaseUnit(baseUnit))
        if (saturatedFatPer100g != null) put("saturatedFatPer100g", saturatedFatPer100g) else putNull("saturatedFatPer100g")
        if (sugarPer100g != null) put("sugarPer100g", sugarPer100g) else putNull("sugarPer100g")
        if (fiberPer100g != null) put("fiberPer100g", fiberPer100g) else putNull("fiberPer100g")
        if (saltPer100g != null) put("saltPer100g", saltPer100g) else putNull("saltPer100g")
        if (sodiumMgPer100g != null) put("sodiumMgPer100g", sodiumMgPer100g) else putNull("sodiumMgPer100g")
        if (calciumMgPer100g != null) put("calciumMgPer100g", calciumMgPer100g) else putNull("calciumMgPer100g")
        if (potassiumMgPer100g != null) put("potassiumMgPer100g", potassiumMgPer100g) else putNull("potassiumMgPer100g")
        if (ironMgPer100g != null) put("ironMgPer100g", ironMgPer100g) else putNull("ironMgPer100g")
        if (vitaminCMgPer100g != null) put("vitaminCMgPer100g", vitaminCMgPer100g) else putNull("vitaminCMgPer100g")
        if (preparationState != null) put("preparationState", preparationState) else putNull("preparationState")
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
            catalogCode = item.catalogCode,
            brand = item.brand,
            baseUnit = ServingUnitLabels.normalizedBaseUnit(item.baseUnit),
            saturatedFatPer100g = item.saturatedFatPer100g,
            sugarPer100g = item.sugarPer100g,
            fiberPer100g = item.fiberPer100g,
            saltPer100g = item.saltPer100g,
            sodiumMgPer100g = item.sodiumMgPer100g,
            calciumMgPer100g = item.calciumMgPer100g,
            potassiumMgPer100g = item.potassiumMgPer100g,
            ironMgPer100g = item.ironMgPer100g,
            vitaminCMgPer100g = item.vitaminCMgPer100g,
            preparationState = item.preparationState,
        )
    }
}

@Entity(
    tableName = "meal_logs",
    indices = [
        Index(value = ["dateIso", "timestampMillis"]),
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
    val baseUnit: String = "g",
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
        baseUnit = ServingUnitLabels.normalizedBaseUnit(baseUnit),
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
            baseUnit = ServingUnitLabels.normalizedBaseUnit(item.baseUnit),
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
    indices = [Index(value = ["dateIso", "timestampMillis"])],
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

@Entity(
    tableName = "goal_history",
    indices = [Index(value = ["effectiveDateIso", "timestampMillis"])],
)
data class GoalHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val effectiveDateIso: String,
    val calories: Int,
    val proteinGrams: Int,
    val carbsGrams: Int,
    val fatGrams: Int,
    val goalType: String,
    val targetWeightKg: Float,
    val source: String,
    val timestampMillis: Long,
) {
    fun toDomain(): GoalHistoryEntry = GoalHistoryEntry(
        id = id,
        effectiveDateIso = effectiveDateIso,
        calories = calories,
        proteinGrams = proteinGrams,
        carbsGrams = carbsGrams,
        fatGrams = fatGrams,
        goalType = GOAL_TYPE_BY_NAME[goalType] ?: GoalType.MAINTAIN,
        targetWeightKg = targetWeightKg,
        source = GOAL_SOURCE_BY_NAME[source] ?: GoalChangeSource.MANUAL,
        timestampMillis = timestampMillis,
    )

    companion object {
        fun fromDomain(entry: GoalHistoryEntry): GoalHistoryEntity = GoalHistoryEntity(
            id = entry.id,
            effectiveDateIso = entry.effectiveDateIso,
            calories = entry.calories,
            proteinGrams = entry.proteinGrams,
            carbsGrams = entry.carbsGrams,
            fatGrams = entry.fatGrams,
            goalType = entry.goalType.name,
            targetWeightKg = entry.targetWeightKg,
            source = entry.source.name,
            timestampMillis = entry.timestampMillis,
        )
    }
}

@Entity(
    tableName = "food_feedback",
    indices = [
        Index(value = ["status"]),
        Index(value = ["foodId"]),
    ],
)
data class FoodFeedbackEntity(
    @PrimaryKey
    val foodKey: String,
    val foodId: Long? = null,
    val foodName: String = "",
    val status: String = FoodFeedbackStatus.NEUTRAL.name,
    val acceptedCount: Int = 0,
    val dismissedCount: Int = 0,
    val lastSuggestedMillis: Long = 0L,
    val lastAcceptedMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
) {
    fun toDomain(): FoodFeedbackRecord = FoodFeedbackRecord(
        foodKey = foodKey,
        foodId = foodId,
        foodName = foodName,
        status = FEEDBACK_STATUS_BY_NAME[status] ?: FoodFeedbackStatus.NEUTRAL,
        acceptedCount = acceptedCount,
        dismissedCount = dismissedCount,
        lastSuggestedMillis = lastSuggestedMillis,
        lastAcceptedMillis = lastAcceptedMillis,
        updatedAtMillis = updatedAtMillis,
    )

    companion object {
        fun fromDomain(record: FoodFeedbackRecord): FoodFeedbackEntity = FoodFeedbackEntity(
            foodKey = record.foodKey,
            foodId = record.foodId,
            foodName = record.foodName,
            status = record.status.name,
            acceptedCount = record.acceptedCount,
            dismissedCount = record.dismissedCount,
            lastSuggestedMillis = record.lastSuggestedMillis,
            lastAcceptedMillis = record.lastAcceptedMillis,
            updatedAtMillis = record.updatedAtMillis,
        )
    }
}

@Entity(tableName = "metabolism_snapshots")
data class MetabolismSnapshotEntity(
    @PrimaryKey
    val dateIso: String,
    val estimatedTdeeKcal: Int,
    val uncertaintyKcal: Int,
    val priorTdeeKcal: Int,
    val trendWeightKg: Float,
    val confidenceScore: Int,
    val qualityLevel: String,
    val updatedAtMillis: Long,
) {
    fun toDomain(): MetabolismSnapshotRecord = MetabolismSnapshotRecord(
        dateIso = dateIso,
        estimatedTdeeKcal = estimatedTdeeKcal,
        uncertaintyKcal = uncertaintyKcal,
        priorTdeeKcal = priorTdeeKcal,
        trendWeightKg = trendWeightKg,
        confidenceScore = confidenceScore,
        qualityLevel = qualityLevel,
        updatedAtMillis = updatedAtMillis,
    )

    companion object {
        fun fromDomain(record: MetabolismSnapshotRecord): MetabolismSnapshotEntity = MetabolismSnapshotEntity(
            dateIso = record.dateIso,
            estimatedTdeeKcal = record.estimatedTdeeKcal,
            uncertaintyKcal = record.uncertaintyKcal,
            priorTdeeKcal = record.priorTdeeKcal,
            trendWeightKg = record.trendWeightKg,
            confidenceScore = record.confidenceScore,
            qualityLevel = record.qualityLevel,
            updatedAtMillis = record.updatedAtMillis,
        )
    }
}
