package com.nutra.app.domain.repository

import androidx.room.withTransaction
import com.nutra.app.core.database.FoodItemEntity
import com.nutra.app.core.database.MealLogEntity
import com.nutra.app.core.database.NutraDao
import com.nutra.app.core.database.NutraDatabase
import com.nutra.app.core.database.TurkishFoodSeedCatalog
import com.nutra.app.core.database.WaterLogEntity
import com.nutra.app.core.database.WeightLogEntity
import com.nutra.app.core.datastore.UserPreferencesDataStore
import com.nutra.app.core.network.OpenFoodFactsService
import com.nutra.app.domain.model.AppResult
import com.nutra.app.domain.model.ComboFoodEntry
import com.nutra.app.domain.model.DailyAnalyticsPoint
import com.nutra.app.domain.model.DailyNutritionSummary
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

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
    private val database: NutraDatabase,
    private val nutraDao: NutraDao,
    private val preferencesDataStore: UserPreferencesDataStore,
    private val openFoodFactsService: dagger.Lazy<OpenFoodFactsService>,
) {
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val seedMutex = Mutex()
    private val waterMutex = Mutex()
    private val barcodeMutex = Mutex()
    private val backupMutex = Mutex()
    @Volatile
    private var catalogVerified = false
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
        if (catalogVerified) return@withContext
        seedMutex.withLock {
            if (!catalogVerified) {
                if (nutraDao.getFoodCount() == 0) {
                    nutraDao.insertFoods(TurkishFoodSeedCatalog.toEntities())
                }
                catalogVerified = true
            }
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
            val buckets = Array(MealType.entries.size) { mutableListOf<MealLogItem>() }
            for (entity in mealEntities) {
                val item = entity.toDomain()
                buckets[item.mealType.ordinal].add(item)
            }
            val mealsByType = MealType.entries.map { type ->
                MealSummary(
                    mealType = type,
                    items = buckets[type.ordinal],
                )
            }
            DailyNutritionSummary(
                dateIso = dateIso,
                goals = goals,
                meals = mealsByType,
                waterMl = waterEntity?.waterMl ?: 0,
            )
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
            class DayTotals {
                var calories = 0
                var protein = 0.0
                var carbs = 0.0
                var fat = 0.0
            }
            val totalsByDate = HashMap<String, DayTotals>(mealLogs.size.coerceAtMost(64))
            for (log in mealLogs) {
                val acc = totalsByDate.getOrPut(log.dateIso) { DayTotals() }
                acc.calories += log.calories
                acc.protein += log.proteinGrams
                acc.carbs += log.carbsGrams
                acc.fat += log.fatGrams
            }
            val waterByDate = HashMap<String, Int>(waterLogs.size)
            for (water in waterLogs) {
                waterByDate[water.dateIso] = water.waterMl
            }

            val points = ArrayList<DailyAnalyticsPoint>(32)
            var cursor = startDate
            while (!cursor.isAfter(endDate)) {
                val iso = cursor.toString()
                val acc = totalsByDate[iso]
                val dayWater = waterByDate[iso] ?: 0
                points.add(
                    DailyAnalyticsPoint(
                        dateIso = iso,
                        calories = acc?.calories ?: 0,
                        targetCalories = goals.calories,
                        proteinGrams = acc?.protein?.toFloat() ?: 0f,
                        carbsGrams = acc?.carbs?.toFloat() ?: 0f,
                        fatGrams = acc?.fat?.toFloat() ?: 0f,
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
        val isRawGramMode = unitLabel == "1 Gram"
        val safeMultiplier = if (isRawGramMode) {
            multiplier.coerceIn(1f, 3000f)
        } else {
            multiplier.coerceIn(0.1f, 50f)
        }
        val totalGrams = if (isRawGramMode) {
            safeMultiplier
        } else {
            (unitGrams * safeMultiplier).coerceAtLeast(1f)
        }
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
        val isRawGramMode = logItem.unitLabel == "1 Gram"
        val safeNewMult = if (isRawGramMode) {
            newMultiplier.coerceIn(1f, 3000f)
        } else {
            newMultiplier.coerceIn(0.25f, 20f)
        }
        val safeOldMult = logItem.multiplier.coerceAtLeast(0.05f)
        val sourceFood = logItem.foodId?.let { nutraDao.findFoodById(it)?.toDomain() }

        val updated = if (sourceFood != null) {
            val newTotalGrams = if (isRawGramMode) {
                safeNewMult
            } else {
                val unitGrams = (logItem.totalGrams / safeOldMult).coerceAtLeast(1f)
                (unitGrams * safeNewMult).coerceAtLeast(1f)
            }
            logItem.copy(
                multiplier = safeNewMult,
                totalGrams = newTotalGrams,
                calories = sourceFood.caloriesForGrams(newTotalGrams),
                proteinGrams = sourceFood.proteinForGrams(newTotalGrams),
                carbsGrams = sourceFood.carbsForGrams(newTotalGrams),
                fatGrams = sourceFood.fatForGrams(newTotalGrams),
            )
        } else {
            val ratio = safeNewMult / safeOldMult
            val newTotalGrams = if (isRawGramMode) {
                safeNewMult
            } else {
                (logItem.totalGrams * ratio).coerceAtLeast(1f)
            }
            logItem.copy(
                multiplier = safeNewMult,
                totalGrams = newTotalGrams,
                calories = (logItem.calories * ratio).roundToInt().coerceAtLeast(0),
                proteinGrams = ((logItem.proteinGrams * ratio) * 10f).roundToInt() / 10f,
                carbsGrams = ((logItem.carbsGrams * ratio) * 10f).roundToInt() / 10f,
                fatGrams = ((logItem.fatGrams * ratio) * 10f).roundToInt() / 10f,
            )
        }
        nutraDao.updateMealLog(MealLogEntity.fromDomain(updated))
    }

    suspend fun deleteMealLog(logId: Long) = withContext(Dispatchers.IO) {
        nutraDao.deleteMealLogById(logId)
    }

    suspend fun toggleFavoriteFood(foodId: Long, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        nutraDao.updateFavorite(foodId, isFavorite)
    }

    suspend fun createCustomFood(food: FoodItem): FoodItem = withContext(Dispatchers.IO) {
        ensureCatalogSeeded()
        val normName = TurkishTextNormalizer.normalize(food.name)
        val norm = TurkishTextNormalizer.normalize("${food.name} ${food.subtitle} ${food.category.title}")
        val id = nutraDao.insertFood(FoodItemEntity.fromDomain(food.copy(isCustom = true), norm))
        food.copy(
            id = id,
            isCustom = true,
            normalizedName = normName,
            normalizedSearchText = norm,
        )
    }

    suspend fun findOrLookupBarcode(barcode: String): AppResult<FoodItem> = withContext(Dispatchers.IO) {
        ensureCatalogSeeded()
        val cleaned = barcode.trim()
        barcodeMutex.withLock {
            val localMatch = nutraDao.findFoodByBarcode(cleaned)
            if (localMatch != null) {
                return@withLock AppResult.Success(localMatch.toDomain())
            }
            val remoteResult = openFoodFactsService.get().lookupBarcode(cleaned)
            if (remoteResult is AppResult.Success) {
                val saved = createCustomFood(remoteResult.data)
                return@withLock AppResult.Success(saved)
            }
            remoteResult
        }
    }

    suspend fun getWaterMl(dateIso: String): Int = withContext(Dispatchers.IO) {
        nutraDao.getWaterForDate(dateIso)?.waterMl ?: 0
    }

    suspend fun addWaterMl(dateIso: String, deltaMl: Int) = withContext(Dispatchers.IO) {
        waterMutex.withLock {
            val current = nutraDao.getWaterForDate(dateIso)?.waterMl ?: 0
            val next = (current + deltaMl).coerceIn(0, 10000)
            nutraDao.upsertWaterLog(
                WaterLogEntity(
                    dateIso = dateIso,
                    waterMl = next,
                    updatedAtMillis = System.currentTimeMillis(),
                ),
            )
        }
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
        backupMutex.withLock {
            try {
                val profile = preferencesDataStore.getSnapshot()
                val allFoods = nutraDao.getAllFoodsSnapshot().map { it.toDomain() }
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
    }

    suspend fun importBackupJson(rawJson: String): AppResult<Unit> = withContext(Dispatchers.IO) {
        backupMutex.withLock {
            try {
                val payload = backupJson.decodeFromString<NutraBackupPayload>(rawJson)
                ensureCatalogSeeded()

            database.withTransaction {
                nutraDao.clearSeedFavorites()
                val existingFoods = nutraDao.getAllFoodsSnapshot()
                val existingById = existingFoods.associateBy { it.id }
                val existingByBarcode = existingFoods
                    .filter { !it.barcode.isNullOrBlank() }
                    .associateBy { it.barcode!! }
                    .toMutableMap()
                val existingSeedByName = existingFoods
                    .filter { !it.isCustom }
                    .associateBy { TurkishTextNormalizer.normalize(it.name) }
                val existingCustomByName = existingFoods
                    .filter { it.isCustom }
                    .associateBy { TurkishTextNormalizer.normalize(it.name) }
                    .toMutableMap()
                val nameToTargetId = existingFoods
                    .associate { TurkishTextNormalizer.normalize(it.name) to it.id }
                    .toMutableMap()
                val importedFoodIdMap = HashMap<Long, Long>()

                for (item in payload.customFoods) {
                    val normName = TurkishTextNormalizer.normalize(item.name)
                    if (normName.isBlank()) continue
                    val normSearch = TurkishTextNormalizer.normalize("${item.name} ${item.subtitle} ${item.category.title}")
                    val barcodeMatch = item.barcode?.takeIf { it.isNotBlank() }?.let { existingByBarcode[it] }
                    val match = barcodeMatch
                        ?: (if (!item.isCustom) existingSeedByName[normName] else null)
                        ?: existingCustomByName[normName]
                        ?: existingSeedByName[normName]

                    if (match != null) {
                        if (!match.isCustom) {
                            if (match.isFavorite != item.isFavorite) {
                                nutraDao.updateFavorite(match.id, item.isFavorite)
                            }
                        } else {
                            val updatedEntity = FoodItemEntity.fromDomain(
                                item = item.copy(id = match.id, isCustom = true),
                                normalizedText = normSearch,
                            )
                            nutraDao.updateFood(updatedEntity)
                            existingCustomByName[normName] = updatedEntity
                            if (!updatedEntity.barcode.isNullOrBlank()) {
                                existingByBarcode[updatedEntity.barcode] = updatedEntity
                            }
                        }
                        if (item.id > 0L) importedFoodIdMap[item.id] = match.id
                        nameToTargetId[normName] = match.id
                    } else {
                        val entity = FoodItemEntity.fromDomain(item.copy(id = 0L), normSearch)
                        val newId = nutraDao.insertFood(entity)
                        val inserted = entity.copy(id = newId)
                        if (inserted.isCustom) {
                            existingCustomByName[normName] = inserted
                        }
                        if (!inserted.barcode.isNullOrBlank()) {
                            existingByBarcode[inserted.barcode] = inserted
                        }
                        if (item.id > 0L) importedFoodIdMap[item.id] = newId
                        nameToTargetId[normName] = newId
                    }
                }

                nutraDao.deleteAllMealLogs()
                val mealEntities = payload.mealLogs
                    .filter { it.foodName.isNotBlank() && isValidIsoDate(it.dateIso) }
                    .map { log ->
                        val normFoodName = TurkishTextNormalizer.normalize(log.foodName)
                        val mappedId = log.foodId?.let { oldId ->
                            importedFoodIdMap[oldId] ?: existingById[oldId]
                                ?.takeIf { TurkishTextNormalizer.normalize(it.name) == normFoodName }
                                ?.id
                        } ?: nameToTargetId[normFoodName]

                        val safeMultiplier = if (log.multiplier.isFinite()) log.multiplier.coerceAtLeast(0.1f) else 1f
                        val safeGrams = if (log.totalGrams.isFinite()) log.totalGrams.coerceAtLeast(1f) else 100f
                        val safeProtein = if (log.proteinGrams.isFinite()) log.proteinGrams.coerceAtLeast(0f) else 0f
                        val safeCarbs = if (log.carbsGrams.isFinite()) log.carbsGrams.coerceAtLeast(0f) else 0f
                        val safeFat = if (log.fatGrams.isFinite()) log.fatGrams.coerceAtLeast(0f) else 0f

                        MealLogEntity.fromDomain(
                            log.copy(
                                id = 0L,
                                foodId = mappedId,
                                multiplier = safeMultiplier,
                                totalGrams = safeGrams,
                                calories = log.calories.coerceAtLeast(0),
                                proteinGrams = safeProtein,
                                carbsGrams = safeCarbs,
                                fatGrams = safeFat,
                            ),
                        )
                    }
                if (mealEntities.isNotEmpty()) {
                    nutraDao.insertMealLogs(mealEntities)
                }

                nutraDao.deleteAllWaterLogs()
                val now = System.currentTimeMillis()
                val waterEntities = payload.waterLogs
                    .filterKeys { isValidIsoDate(it) }
                    .map { (dateIso, ml) ->
                        WaterLogEntity(
                            dateIso = dateIso,
                            waterMl = ml.coerceIn(0, 10000),
                            updatedAtMillis = now,
                        )
                    }
                if (waterEntities.isNotEmpty()) {
                    nutraDao.upsertWaterLogs(waterEntities)
                }

                nutraDao.deleteAllWeightLogs()
                val weightEntities = payload.weightEntries
                    .filter { isValidIsoDate(it.dateIso) && it.weightKg.isFinite() }
                    .map {
                        WeightLogEntity(
                            dateIso = it.dateIso,
                            weightKg = it.weightKg.coerceIn(30f, 300f),
                            timestampMillis = it.timestampMillis,
                        )
                    }
                if (weightEntities.isNotEmpty()) {
                    nutraDao.insertWeightLogs(weightEntities)
                }
            }

                preferencesDataStore.restoreProfile(payload.userProfile)
                AppResult.Success(Unit)
            } catch (e: Exception) {
                AppResult.Error("Yedek dosyası geçersiz veya okunamadı: ${e.localizedMessage}", e)
            }
        }
    }

    private fun isValidIsoDate(dateIso: String): Boolean =
        runCatching { LocalDate.parse(dateIso) }.isSuccess
}
