package com.nutra.app.domain.repository

import com.nutra.app.core.database.FoodItemEntity
import com.nutra.app.core.database.MealLogEntity
import com.nutra.app.core.database.NutraDao
import com.nutra.app.core.database.TurkishFoodSeedCatalog
import com.nutra.app.core.database.WaterLogEntity
import com.nutra.app.core.database.WeightLogEntity
import com.nutra.app.core.datastore.UserPreferencesDataStore
import com.nutra.app.core.network.OpenFoodFactsService
import com.nutra.app.domain.model.AppResult
import com.nutra.app.domain.model.ComboFoodEntry
import com.nutra.app.domain.model.DailyAnalyticsPoint
import com.nutra.app.domain.model.DailyNutritionSummary
import com.nutra.app.domain.model.DayGoalStatus
import com.nutra.app.domain.model.DayStatusIndicator
import com.nutra.app.domain.model.FoodItem
import com.nutra.app.domain.model.MealLogItem
import com.nutra.app.domain.model.MealSummary
import com.nutra.app.domain.model.MealType
import com.nutra.app.domain.model.UserProfile
import com.nutra.app.domain.model.WeightEntry
import com.nutra.app.domain.usecase.TurkishTextNormalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class NutraBackupPayload(
    val version: Int = 1,
    val exportedAtDateIso: String,
    val userProfile: UserProfile,
    val customFoods: List<FoodItem>,
    val mealLogs: List<MealLogItem>,
    val waterLogs: Map<String, Int>,
    val weightEntries: List<WeightEntry>,
)

@Singleton
class NutritionRepository @Inject constructor(
    private val nutraDao: NutraDao,
    private val preferencesDataStore: UserPreferencesDataStore,
    private val openFoodFactsService: dagger.Lazy<OpenFoodFactsService>,
) {
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val backupJson by lazy {
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }
    }
    private val goalsFlow = preferencesDataStore.userProfileFlow
        .map { it.goals }
        .distinctUntilChanged()

    init {
        repoScope.launch {
            ensureCatalogSeeded()
        }
    }

    suspend fun ensureCatalogSeeded() = withContext(Dispatchers.IO) {
        if (nutraDao.getFoodCount() == 0) {
            nutraDao.insertFoods(TurkishFoodSeedCatalog.toEntities())
        }
    }

    fun observeAllFoods(): Flow<List<FoodItem>> =
        nutraDao.observeAllFoods()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(Dispatchers.Default)

    fun observeRecentHistoricalMealLogs(): Flow<List<MealLogItem>> =
        nutraDao.observeRecentHistoricalMealLogs()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(Dispatchers.Default)

    fun observeDailySummary(dateIso: String): Flow<DailyNutritionSummary> =
        combine(
            goalsFlow,
            nutraDao.observeMealLogsForDate(dateIso),
            nutraDao.observeWaterForDate(dateIso),
        ) { goals, mealEntities, waterEntity ->
            val domainLogs = mealEntities.map { it.toDomain() }
            val mealsByType = MealType.entries.map { type ->
                MealSummary(
                    mealType = type,
                    items = domainLogs.filter { it.mealType == type },
                )
            }
            DailyNutritionSummary(
                dateIso = dateIso,
                goals = goals,
                meals = mealsByType,
                waterMl = waterEntity?.waterMl ?: 0,
            )
        }.flowOn(Dispatchers.Default)

    fun observeWeekStatusIndicators(
        startDateIso: String,
        endDateIso: String,
    ): Flow<Map<String, DayStatusIndicator>> =
        combine(
            goalsFlow,
            nutraDao.observeMealLogsInRange(startDateIso, endDateIso),
        ) { goals, logs ->
            val targetCal = goals.calories.coerceAtLeast(1)
            val grouped = logs.groupBy { it.dateIso }
            grouped.mapValues { (dateIso, dayLogs) ->
                val consumed = dayLogs.sumOf { it.calories }
                val status = when {
                    consumed <= 0 -> DayGoalStatus.EMPTY
                    consumed > (targetCal * 1.08f) -> DayGoalStatus.OVER_BUDGET
                    consumed >= (targetCal * 0.75f) -> DayGoalStatus.ON_TARGET
                    else -> DayGoalStatus.PARTIAL
                }
                DayStatusIndicator(
                    dateIso = dateIso,
                    consumedCalories = consumed,
                    targetCalories = targetCal,
                    status = status,
                )
            }
        }.flowOn(Dispatchers.Default)

    fun observeAnalyticsRange(
        startDate: LocalDate,
        endDate: LocalDate,
    ): Flow<List<DailyAnalyticsPoint>> {
        val startIso = startDate.toString()
        val endIso = endDate.toString()
        return combine(
            goalsFlow,
            nutraDao.observeMealLogsInRange(startIso, endIso),
            nutraDao.observeWaterInRange(startIso, endIso),
        ) { goals, mealLogs, waterLogs ->
            val mealsByDate = mealLogs.groupBy { it.dateIso }
            val waterByDate = waterLogs.associateBy { it.dateIso }

            val points = mutableListOf<DailyAnalyticsPoint>()
            var cursor = startDate
            while (!cursor.isAfter(endDate)) {
                val iso = cursor.toString()
                val dayMeals = mealsByDate[iso].orEmpty()
                val dayWater = waterByDate[iso]?.waterMl ?: 0
                points.add(
                    DailyAnalyticsPoint(
                        dateIso = iso,
                        calories = dayMeals.sumOf { it.calories },
                        targetCalories = goals.calories,
                        proteinGrams = dayMeals.sumOf { it.proteinGrams.toDouble() }.toFloat(),
                        carbsGrams = dayMeals.sumOf { it.carbsGrams.toDouble() }.toFloat(),
                        fatGrams = dayMeals.sumOf { it.fatGrams.toDouble() }.toFloat(),
                        waterMl = dayWater,
                    ),
                )
                cursor = cursor.plusDays(1)
            }
            points
        }.flowOn(Dispatchers.Default)
    }

    fun observeWeightEntries(): Flow<List<WeightEntry>> =
        nutraDao.observeAllWeightLogs()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(Dispatchers.Default)

    suspend fun logFoodToMeal(
        dateIso: String,
        mealType: MealType,
        food: FoodItem,
        unitLabel: String,
        unitGrams: Float,
        multiplier: Float,
    ): MealLogItem = withContext(Dispatchers.IO) {
        val safeMultiplier = multiplier.coerceIn(0.1f, 50f)
        val totalGrams = (unitGrams * safeMultiplier).coerceAtLeast(1f)
        val now = System.currentTimeMillis()
        val item = MealLogItem(
            dateIso = dateIso,
            mealType = mealType,
            foodId = food.id.takeIf { it > 0L },
            foodName = food.name,
            unitLabel = unitLabel,
            multiplier = safeMultiplier,
            totalGrams = totalGrams,
            calories = food.caloriesForGrams(totalGrams),
            proteinGrams = food.proteinForGrams(totalGrams),
            carbsGrams = food.carbsForGrams(totalGrams),
            fatGrams = food.fatForGrams(totalGrams),
            timestampMillis = now,
        )
        val newId = nutraDao.insertMealLog(MealLogEntity.fromDomain(item))
        item.copy(id = newId)
    }

    suspend fun logComboItemsToMeal(
        dateIso: String,
        mealType: MealType,
        entries: List<ComboFoodEntry>,
    ) = withContext(Dispatchers.IO) {
        val baseTime = System.currentTimeMillis()
        val entities = entries.mapIndexed { idx, entry ->
            MealLogEntity(
                dateIso = dateIso,
                mealType = mealType.name,
                foodId = entry.foodId,
                foodName = entry.foodName,
                unitLabel = entry.unitLabel,
                multiplier = entry.multiplier,
                totalGrams = entry.totalGrams,
                calories = entry.calories,
                proteinGrams = entry.proteinGrams,
                carbsGrams = entry.carbsGrams,
                fatGrams = entry.fatGrams,
                timestampMillis = baseTime + idx,
            )
        }
        nutraDao.insertMealLogs(entities)
    }

    suspend fun restoreMealLogItem(item: MealLogItem) = withContext(Dispatchers.IO) {
        nutraDao.insertMealLog(MealLogEntity.fromDomain(item))
    }

    suspend fun updateMealLogMultiplier(
        logItem: MealLogItem,
        newMultiplier: Float,
    ) = withContext(Dispatchers.IO) {
        val safeOldMult = logItem.multiplier.coerceAtLeast(0.05f)
        val ratio = newMultiplier.coerceIn(0.25f, 20f) / safeOldMult
        val updated = logItem.copy(
            multiplier = newMultiplier,
            totalGrams = logItem.totalGrams * ratio,
            calories = (logItem.calories * ratio).toInt().coerceAtLeast(0),
            proteinGrams = ((logItem.proteinGrams * ratio) * 10f).toInt() / 10f,
            carbsGrams = ((logItem.carbsGrams * ratio) * 10f).toInt() / 10f,
            fatGrams = ((logItem.fatGrams * ratio) * 10f).toInt() / 10f,
        )
        nutraDao.updateMealLog(MealLogEntity.fromDomain(updated))
    }

    suspend fun deleteMealLog(logId: Long) = withContext(Dispatchers.IO) {
        nutraDao.deleteMealLogById(logId)
    }

    suspend fun toggleFavoriteFood(foodId: Long, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        nutraDao.updateFavorite(foodId, isFavorite)
    }

    suspend fun createCustomFood(food: FoodItem): FoodItem = withContext(Dispatchers.IO) {
        val norm = TurkishTextNormalizer.normalize("${food.name} ${food.subtitle}")
        val id = nutraDao.insertFood(FoodItemEntity.fromDomain(food.copy(isCustom = true), norm))
        food.copy(id = id, isCustom = true)
    }

    suspend fun findOrLookupBarcode(barcode: String): AppResult<FoodItem> = withContext(Dispatchers.IO) {
        val cleaned = barcode.trim()
        val localMatch = nutraDao.findFoodByBarcode(cleaned)
        if (localMatch != null) {
            return@withContext AppResult.Success(localMatch.toDomain())
        }
        val remoteResult = openFoodFactsService.get().lookupBarcode(cleaned)
        if (remoteResult is AppResult.Success) {
            val saved = createCustomFood(remoteResult.data)
            return@withContext AppResult.Success(saved)
        }
        remoteResult
    }

    suspend fun addWaterMl(dateIso: String, deltaMl: Int) = withContext(Dispatchers.IO) {
        val current = nutraDao.observeWaterForDate(dateIso).first()?.waterMl ?: 0
        val next = (current + deltaMl).coerceIn(0, 10000)
        nutraDao.upsertWaterLog(
            WaterLogEntity(
                dateIso = dateIso,
                waterMl = next,
                updatedAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun logWeight(dateIso: String, weightKg: Float) = withContext(Dispatchers.IO) {
        val safeWeight = weightKg.coerceIn(30f, 300f)
        nutraDao.insertWeightLog(
            WeightLogEntity(
                dateIso = dateIso,
                weightKg = safeWeight,
                timestampMillis = System.currentTimeMillis(),
            ),
        )
        preferencesDataStore.updateCurrentWeight(safeWeight)
    }

    suspend fun exportBackupJson(): AppResult<String> = withContext(Dispatchers.IO) {
        try {
            val profile = preferencesDataStore.getSnapshot()
            val allFoods = nutraDao.observeAllFoods().first().map { it.toDomain() }
            val customFoods = allFoods.filter { it.isCustom || it.isFavorite }
            val mealLogs = nutraDao.getAllMealLogsSnapshot().map { it.toDomain() }
            val waterLogs = nutraDao.getAllWaterLogsSnapshot().associate { it.dateIso to it.waterMl }
            val weightEntries = nutraDao.getAllWeightLogsSnapshot().map { it.toDomain() }

            val payload = NutraBackupPayload(
                version = 1,
                exportedAtDateIso = LocalDate.now().toString(),
                userProfile = profile,
                customFoods = customFoods,
                mealLogs = mealLogs,
                waterLogs = waterLogs,
                weightEntries = weightEntries,
            )
            AppResult.Success(backupJson.encodeToString(payload))
        } catch (e: Exception) {
            AppResult.Error("Yedekleme oluşturulurken hata oluştu: ${e.localizedMessage}", e)
        }
    }

    suspend fun importBackupJson(rawJson: String): AppResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val payload = backupJson.decodeFromString<NutraBackupPayload>(rawJson)
            preferencesDataStore.saveOnboardingProfile(payload.userProfile)
            preferencesDataStore.updateAppearance(
                payload.userProfile.themePreference,
                payload.userProfile.useDynamicColor,
            )

            val customEntities = payload.customFoods.map { item ->
                val norm = TurkishTextNormalizer.normalize("${item.name} ${item.subtitle}")
                FoodItemEntity.fromDomain(item.copy(id = 0L), norm)
            }
            if (customEntities.isNotEmpty()) {
                nutraDao.insertFoods(customEntities)
            }

            val mealEntities = payload.mealLogs.map { MealLogEntity.fromDomain(it.copy(id = 0L)) }
            if (mealEntities.isNotEmpty()) {
                nutraDao.insertMealLogs(mealEntities)
            }

            val now = System.currentTimeMillis()
            val waterEntities = payload.waterLogs.map { (dateIso, ml) ->
                WaterLogEntity(dateIso = dateIso, waterMl = ml, updatedAtMillis = now)
            }
            if (waterEntities.isNotEmpty()) {
                nutraDao.upsertWaterLogs(waterEntities)
            }

            val weightEntities = payload.weightEntries.map {
                WeightLogEntity(
                    dateIso = it.dateIso,
                    weightKg = it.weightKg,
                    timestampMillis = it.timestampMillis,
                )
            }
            if (weightEntities.isNotEmpty()) {
                nutraDao.insertWeightLogs(weightEntities)
            }

            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Error("Yedek dosyası geçersiz veya okunamadı: ${e.localizedMessage}", e)
        }
    }
}
