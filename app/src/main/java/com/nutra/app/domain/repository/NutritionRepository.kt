package com.nutra.app.domain.repository

import android.content.Context
import androidx.room.withTransaction
import com.nutra.app.core.database.DailyMealTotalsTuple
import com.nutra.app.core.database.FoodFeedbackEntity
import com.nutra.app.core.database.FoodItemEntity
import com.nutra.app.core.database.GoalHistoryEntity
import com.nutra.app.core.database.MealLogEntity
import com.nutra.app.core.database.MetabolismSnapshotEntity
import com.nutra.app.core.database.NutraDao
import com.nutra.app.core.database.NutraDatabase
import com.nutra.app.core.database.TurkishFoodSeedCatalog
import com.nutra.app.core.database.WaterLogEntity
import com.nutra.app.core.database.WeightLogEntity
import com.nutra.app.core.datastore.UserPreferencesDataStore
import com.nutra.app.domain.metabolism.DailyIntakeObservation
import com.nutra.app.domain.model.AppResult
import com.nutra.app.domain.model.ComboFoodEntry
import com.nutra.app.domain.model.DailyAnalyticsPoint
import com.nutra.app.domain.model.DailyNutritionSummary
import com.nutra.app.domain.model.FoodFeedbackRecord
import com.nutra.app.domain.model.FoodFeedbackStatus
import com.nutra.app.domain.model.FoodItem
import com.nutra.app.domain.model.GoalChangeSource
import com.nutra.app.domain.model.GoalHistoryEntry
import com.nutra.app.domain.model.GoalType
import com.nutra.app.domain.model.MacroGoals
import com.nutra.app.domain.model.MealLogItem
import com.nutra.app.domain.model.MealSummary
import com.nutra.app.domain.model.MealType
import com.nutra.app.domain.model.MetabolismSnapshotRecord
import com.nutra.app.domain.model.ServingUnitLabels
import com.nutra.app.domain.model.UserProfile
import com.nutra.app.domain.model.WeightEntry
import com.nutra.app.domain.usecase.TurkishTextNormalizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToInt

@Serializable
data class NutraBackupPayload(
    val version: Int = SUPPORTED_BACKUP_VERSION,
    val exportedAtDateIso: String,
    val userProfile: UserProfile,
    val customFoods: List<FoodItem>,
    val mealLogs: List<MealLogItem>,
    val waterLogs: Map<String, Int>,
    val weightEntries: List<WeightEntry>,
    val goalHistory: List<GoalHistoryEntry> = emptyList(),
    val foodFeedback: List<FoodFeedbackRecord> = emptyList(),
)

data class BackupValidationReport(
    val payload: NutraBackupPayload,
    val validCustomFoods: Int,
    val skippedCustomFoods: Int,
    val validMealLogs: Int,
    val skippedMealLogs: Int,
    val validWaterDays: Int,
    val skippedWaterDays: Int,
    val validWeightEntries: Int,
    val skippedWeightEntries: Int,
    val validGoalHistory: Int,
    val skippedGoalHistory: Int,
    val validFoodFeedback: Int,
    val skippedFoodFeedback: Int,
    val warnings: List<String>,
    val blockingError: String?,
) {
    val totalSkippedCount: Int
        get() = skippedCustomFoods + skippedMealLogs + skippedWaterDays +
            skippedWeightEntries + skippedGoalHistory + skippedFoodFeedback

    val hasWarnings: Boolean
        get() = warnings.isNotEmpty() || totalSkippedCount > 0
}

internal const val MIN_SUPPORTED_BACKUP_VERSION = 1
internal const val SUPPORTED_BACKUP_VERSION = 2
internal const val MAX_BACKUP_BYTES = 20 * 1024 * 1024 // 20 MiB
internal const val MAX_JSON_NESTING_DEPTH = 16

internal fun verifyJsonNestingDepth(rawJson: String, maxDepth: Int = MAX_JSON_NESTING_DEPTH): Boolean {
    var depth = 0
    var inString = false
    var escaped = false
    for (i in 0 until rawJson.length) {
        val ch = rawJson[i]
        if (inString) {
            if (escaped) {
                escaped = false
            } else if (ch == '\\') {
                escaped = true
            } else if (ch == '"') {
                inString = false
            }
            continue
        }
        when (ch) {
            '"' -> inString = true
            '{', '[' -> {
                depth++
                if (depth > maxDepth) return false
            }
            '}', ']' -> {
                depth--
                if (depth < 0) return false
            }
        }
    }
    return depth == 0
}

internal fun analyzeBackupPayload(payload: NutraBackupPayload): BackupValidationReport {
    val warnings = mutableListOf<String>()
    var blockingError: String? = null

    if (payload.version !in MIN_SUPPORTED_BACKUP_VERSION..SUPPORTED_BACKUP_VERSION) {
        blockingError = "Bu yedek sürümü (v${payload.version}) bu uygulama sürümüyle uyumlu değil."
    }

    val validCustom = payload.customFoods.count { isValidBackupFood(it) }
    val skippedCustom = payload.customFoods.size - validCustom
    val validMeals = payload.mealLogs.count { isValidBackupMealLog(it) }
    val skippedMeals = payload.mealLogs.size - validMeals
    val validWater = payload.waterLogs.keys.count(::isValidBackupIsoDate)
    val skippedWater = payload.waterLogs.size - validWater
    val validWeights = payload.weightEntries.count { isValidBackupWeightEntry(it) }
    val skippedWeights = payload.weightEntries.size - validWeights
    val validGoals = payload.goalHistory.count { isValidBackupIsoDate(it.effectiveDateIso) && it.calories > 0 }
    val skippedGoals = payload.goalHistory.size - validGoals
    val validFeedback = payload.foodFeedback.count { it.foodKey.isNotBlank() }
    val skippedFeedback = payload.foodFeedback.size - validFeedback

    if (blockingError == null) {
        if (payload.mealLogs.isNotEmpty() && validMeals == 0) {
            blockingError = "Yedek dosyasındaki öğün kayıtları geçersiz veya bozuk."
        } else if (payload.waterLogs.isNotEmpty() && validWater == 0) {
            blockingError = "Yedek dosyasındaki su kayıtları geçersiz veya bozuk."
        } else if (payload.weightEntries.isNotEmpty() && validWeights == 0) {
            blockingError = "Yedek dosyasındaki kilo kayıtları geçersiz veya bozuk."
        } else if (payload.goalHistory.isNotEmpty() && validGoals == 0) {
            blockingError = "Yedek dosyasındaki hedef geçmişi geçersiz veya bozuk."
        }
    }

    if (skippedMeals > 0) warnings.add("$skippedMeals geçersiz öğün kaydı atlanacak.")
    if (skippedWeights > 0) warnings.add("$skippedWeights geçersiz kilo kaydı atlanacak.")
    if (skippedWater > 0) warnings.add("$skippedWater geçersiz su kaydı atlanacak.")
    if (skippedCustom > 0) warnings.add("$skippedCustom geçersiz özel besin atlanacak.")
    if (skippedGoals > 0) warnings.add("$skippedGoals geçersiz hedef geçmişi kaydı atlanacak.")
    if (skippedFeedback > 0) warnings.add("$skippedFeedback geçersiz geri bildirim kaydı atlanacak.")

    return BackupValidationReport(
        payload = payload,
        validCustomFoods = validCustom,
        skippedCustomFoods = skippedCustom,
        validMealLogs = validMeals,
        skippedMealLogs = skippedMeals,
        validWaterDays = validWater,
        skippedWaterDays = skippedWater,
        validWeightEntries = validWeights,
        skippedWeightEntries = skippedWeights,
        validGoalHistory = validGoals,
        skippedGoalHistory = skippedGoals,
        validFoodFeedback = validFeedback,
        skippedFoodFeedback = skippedFeedback,
        warnings = warnings,
        blockingError = blockingError,
    )
}

internal fun validateBackupPayload(payload: NutraBackupPayload): String? =
    analyzeBackupPayload(payload).blockingError

private fun isValidBackupFood(food: FoodItem): Boolean =
    food.name.isNotBlank() &&
        food.caloriesPer100g >= 0 &&
        food.proteinPer100g.isFinite() && food.proteinPer100g >= 0f &&
        food.carbsPer100g.isFinite() && food.carbsPer100g >= 0f &&
        food.fatPer100g.isFinite() && food.fatPer100g >= 0f &&
        food.defaultUnitGrams.isFinite() && food.defaultUnitGrams > 0f

private fun isValidBackupMealLog(log: MealLogItem): Boolean =
    log.foodName.isNotBlank() && isValidBackupIsoDate(log.dateIso)

private fun isValidBackupWeightEntry(entry: WeightEntry): Boolean =
    isValidBackupIsoDate(entry.dateIso) && entry.weightKg.isFinite() && entry.weightKg in 20f..400f

internal fun sanitizeImportedGoalHistory(entry: GoalHistoryEntry): GoalHistoryEntry {
    val safeTargetWeight = (if (entry.targetWeightKg.isFinite()) entry.targetWeightKg else 70f).coerceIn(30f, 300f)
    return entry.copy(
        id = 0L,
        calories = entry.calories.coerceIn(1000, 6000),
        proteinGrams = entry.proteinGrams.coerceIn(30, 400),
        carbsGrams = entry.carbsGrams.coerceIn(20, 600),
        fatGrams = entry.fatGrams.coerceIn(20, 250),
        waterGoalMl = entry.waterGoalMl.coerceIn(1000, 7000),
        targetWeightKg = safeTargetWeight,
        timestampMillis = entry.timestampMillis.coerceAtLeast(0L),
    )
}

internal fun remapAndSanitizeFoodFeedback(
    record: FoodFeedbackRecord,
    importedFoodIdMap: Map<Long, Long>,
    nameToTargetId: Map<String, Long>,
): FoodFeedbackRecord {
    val remappedId = record.foodId?.let { importedFoodIdMap[it] }
        ?: record.foodName.takeIf { it.isNotBlank() }?.let { nameToTargetId[TurkishTextNormalizer.normalize(it)] }
        ?: record.foodId
    val remappedKey = if (record.foodKey.startsWith("custom:") && remappedId != null && remappedId > 0L) {
        val parts = record.foodKey.split(':', limit = 3)
        val normSuffix = parts.getOrNull(2)?.takeIf { it.isNotBlank() }
            ?: TurkishTextNormalizer.normalize(record.foodName)
        if (normSuffix.isNotBlank()) "custom:$remappedId:$normSuffix" else record.foodKey.trim()
    } else {
        record.foodKey.trim()
    }
    return record.copy(
        foodKey = remappedKey,
        foodId = remappedId,
        foodName = record.foodName.trim(),
        acceptedCount = record.acceptedCount.coerceAtLeast(0),
        dismissedCount = record.dismissedCount.coerceAtLeast(0),
        lastSuggestedMillis = record.lastSuggestedMillis.coerceAtLeast(0L),
        lastAcceptedMillis = record.lastAcceptedMillis.coerceAtLeast(0L),
        updatedAtMillis = record.updatedAtMillis.coerceAtLeast(0L),
    )
}

private fun isValidBackupIsoDate(dateIso: String): Boolean =
    runCatching { LocalDate.parse(dateIso) }.isSuccess

@Singleton
open class NutritionRepository @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val database: NutraDatabase,
    private val nutraDao: NutraDao,
    private val preferencesDataStore: UserPreferencesDataStore,
) {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val seedMutex = Mutex()
    private val storageMutex = Mutex()

    @Volatile
    private var catalogVerified = false

    private val backupJson by lazy {
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
    }

    private val goalsFlow = preferencesDataStore.userProfileFlow
        .map { it.goals }
        .distinctUntilChanged()

    private val calorieGoalFlow = goalsFlow
        .map { it.calories }
        .distinctUntilChanged()

    private val sharedAllFoodsFlow: SharedFlow<List<FoodItem>> by lazy {
        flow {
            ensureCatalogSeeded()
            var previousById = emptyMap<Long, Pair<FoodItemEntity, FoodItem>>()
            nutraDao.observeAllFoods().collect { entities ->
                val nextById = HashMap<Long, Pair<FoodItemEntity, FoodItem>>(entities.size.coerceAtLeast(16))
                val domainList = ArrayList<FoodItem>(entities.size)
                for (i in entities.indices) {
                    val entity = entities[i]
                    val prev = previousById[entity.id]
                    val domain = when {
                        prev != null && prev.first == entity -> prev.second
                        prev != null && prev.first.copy(isFavorite = entity.isFavorite) == entity ->
                            prev.second.copy(isFavorite = entity.isFavorite)
                        else -> entity.toDomain()
                    }
                    nextById[entity.id] = entity to domain
                    domainList.add(domain)
                }
                previousById = nextById
                emit(domainList)
            }
        }
            .flowOn(Dispatchers.Default)
            .shareIn(
                scope = repositoryScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
                replay = 1,
            )
    }

    suspend fun ensureCatalogSeeded() = withContext(Dispatchers.IO) {
        if (catalogVerified) return@withContext
        seedMutex.withLock {
            if (!catalogVerified) {
                val catalogSeedCount = nutraDao.countCatalogSeedFoods()
                val legacySeeds = nutraDao.getLegacySeedFoods()
                val minCatalogVersion = nutraDao.getMinSeedCatalogVersion() ?: 0
                val needsCatalogUpgrade = catalogSeedCount < TurkishFoodSeedCatalog.EXPECTED_CATALOG_SIZE ||
                    legacySeeds.isNotEmpty() ||
                    minCatalogVersion < TurkishFoodSeedCatalog.CURRENT_CATALOG_VERSION

                if (needsCatalogUpgrade) {
                    val catalogEntities = TurkishFoodSeedCatalog.toEntities(appContext)
                    database.withTransaction {
                        val existingCatalogByCode = nutraDao.getCatalogSeedFoods()
                            .mapNotNull { entity -> entity.catalogCode?.let { it to entity } }
                            .toMap()

                        if (legacySeeds.isEmpty() && existingCatalogByCode.isEmpty()) {
                            nutraDao.insertFoods(catalogEntities)
                        } else {
                            val legacyFavNormNames = HashSet<String>()
                            val legacyFavCodes = HashSet<String>()
                            for (legacy in legacySeeds) {
                                if (legacy.isFavorite) {
                                    val norm = TurkishTextNormalizer.normalize(legacy.name)
                                    legacyFavNormNames.add(norm)
                                    TurkishFoodSeedCatalog.LEGACY_NAME_TO_CATALOG_CODE[norm]?.let {
                                        legacyFavCodes.add(it)
                                    }
                                }
                            }
                            val preserveLegacyFavs = legacySeeds.isNotEmpty()
                            val missingEntities = ArrayList<FoodItemEntity>()
                            for (candidate in catalogEntities) {
                                val code = candidate.catalogCode ?: continue
                                val existing = existingCatalogByCode[code]
                                if (existing != null) {
                                    // Upgrade seed metadata (Catalog v3 allergens, diet flags, clean names, servings)
                                    // in-place while preserving existing primary key ID and user favorite flag.
                                    if (existing.catalogVersion < candidate.catalogVersion || !existing.allergenVerified) {
                                        nutraDao.updateFood(
                                            candidate.copy(
                                                id = existing.id,
                                                isFavorite = existing.isFavorite,
                                            ),
                                        )
                                    }
                                } else if (preserveLegacyFavs) {
                                    val normName = TurkishTextNormalizer.normalize(candidate.name)
                                    val wasFav = code in legacyFavCodes || normName in legacyFavNormNames
                                    missingEntities.add(candidate.copy(isFavorite = wasFav))
                                } else {
                                    missingEntities.add(candidate)
                                }
                            }
                            if (missingEntities.isNotEmpty()) {
                                nutraDao.insertFoods(missingEntities)
                            }
                            if (legacySeeds.isNotEmpty()) {
                                val updatedCatalog = nutraDao.getCatalogSeedFoods()
                                val idByCode = updatedCatalog
                                    .mapNotNull { e -> e.catalogCode?.let { it to e.id } }
                                    .toMap()
                                val idByNormName = updatedCatalog
                                    .associate { TurkishTextNormalizer.normalize(it.name) to it.id }

                                for (legacy in legacySeeds) {
                                    val legacyNorm = TurkishTextNormalizer.normalize(legacy.name)
                                    val targetCode = TurkishFoodSeedCatalog.LEGACY_NAME_TO_CATALOG_CODE[legacyNorm]
                                    val targetId = targetCode?.let { idByCode[it] } ?: idByNormName[legacyNorm]
                                    nutraDao.remapMealLogFoodId(legacy.id, targetId)
                                }
                                nutraDao.deleteLegacySeedFoods()
                            }
                        }
                    }
                    TurkishFoodSeedCatalog.releaseCache()
                }
                catalogVerified = true
            }
        }
    }

    fun observeAllFoods(): Flow<List<FoodItem>> = sharedAllFoodsFlow

    fun observeRecentHistoricalMealLogs(): Flow<List<MealLogItem>> =
        nutraDao.observeRecentHistoricalMealLogs()
            .map { list -> list.map { it.toDomain() } }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    fun observeMealLogsSince(startDateIso: String): Flow<List<MealLogItem>> =
        nutraDao.observeMealLogsInRange(startDateIso, LocalDate.now().toString())
            .map { list -> list.map { it.toDomain() } }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    fun observeMealLogsInRange(startDateIso: String, endDateIso: String): Flow<List<MealLogItem>> =
        nutraDao.observeMealLogsInRange(startDateIso, endDateIso)
            .map { list -> list.map { it.toDomain() } }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    fun observeDailyMetabolismIntake(
        startDate: LocalDate,
        endDate: LocalDate,
    ): Flow<List<DailyIntakeObservation>> =
        nutraDao.observeDailyMetabolismIntakeInRange(startDate.toString(), endDate.toString())
            .map { tuples ->
                tuples.map { t ->
                    DailyIntakeObservation(
                        dateIso = t.dateIso,
                        calories = t.totalCalories,
                        proteinGrams = t.totalProtein.toFloat(),
                        carbsGrams = t.totalCarbs.toFloat(),
                        fatGrams = t.totalFat.toFloat(),
                        mealTypeCount = t.mealTypeCount,
                        itemCount = t.itemCount,
                    )
                }
            }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    fun observeGoalHistory(): Flow<List<GoalHistoryEntry>> =
        nutraDao.observeGoalHistory()
            .map { list -> list.map { it.toDomain() } }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    fun observeFoodFeedback(): Flow<List<FoodFeedbackRecord>> =
        nutraDao.observeAllFoodFeedback()
            .map { list -> list.map { it.toDomain() } }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    fun observeMetabolismSnapshots(): Flow<List<MetabolismSnapshotRecord>> =
        nutraDao.observeMetabolismSnapshots()
            .map { list -> list.map { it.toDomain() } }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    suspend fun recordGoalHistory(
        goals: MacroGoals,
        goalType: GoalType,
        targetWeightKg: Float,
        source: GoalChangeSource,
        effectiveDateIso: String = LocalDate.now().toString(),
    ) = withContext(Dispatchers.IO) {
        storageMutex.withLock {
            recordGoalHistoryLocked(goals, goalType, targetWeightKg, source, effectiveDateIso)
        }
    }

    private suspend fun recordGoalHistoryLocked(
        goals: MacroGoals,
        goalType: GoalType,
        targetWeightKg: Float,
        source: GoalChangeSource,
        effectiveDateIso: String,
    ) {
        val safeTargetWeight = (if (targetWeightKg.isFinite()) targetWeightKg else 70f).coerceIn(30f, 300f)
        val now = System.currentTimeMillis()
        database.withTransaction {
            val latest = nutraDao.findLatestGoalHistoryForDate(effectiveDateIso)
            if (latest != null && latest.source == source.name) {
                nutraDao.updateGoalHistory(
                    latest.copy(
                        calories = goals.calories,
                        proteinGrams = goals.proteinGrams,
                        carbsGrams = goals.carbsGrams,
                        fatGrams = goals.fatGrams,
                        waterGoalMl = goals.waterMl,
                        goalType = goalType.name,
                        targetWeightKg = safeTargetWeight,
                        timestampMillis = now,
                    ),
                )
            } else {
                nutraDao.insertGoalHistory(
                    GoalHistoryEntity(
                        effectiveDateIso = effectiveDateIso,
                        calories = goals.calories,
                        proteinGrams = goals.proteinGrams,
                        carbsGrams = goals.carbsGrams,
                        fatGrams = goals.fatGrams,
                        waterGoalMl = goals.waterMl,
                        goalType = goalType.name,
                        targetWeightKg = safeTargetWeight,
                        source = source.name,
                        timestampMillis = now,
                    ),
                )
            }
        }
    }

    suspend fun approveAdaptiveCalorieProposal(
        proposedGoals: MacroGoals,
        profile: UserProfile,
        todayIso: String = LocalDate.now().toString(),
    ) = withContext(Dispatchers.IO) {
        storageMutex.withLock {
            // Single atomic DataStore write for both updated goals and review date
            preferencesDataStore.updateGoalsAndAdaptiveReview(proposedGoals, todayIso)
            recordGoalHistoryLocked(
                goals = proposedGoals,
                goalType = profile.goalType,
                targetWeightKg = profile.targetWeightKg,
                source = GoalChangeSource.ADAPTIVE_APPROVED,
                effectiveDateIso = todayIso,
            )
        }
    }

    suspend fun dismissAdaptiveCalorieProposal(
        todayIso: String = LocalDate.now().toString(),
    ) = withContext(Dispatchers.IO) {
        preferencesDataStore.recordAdaptiveReviewDate(todayIso)
    }

    suspend fun recordRecommendationAccepted(
        foodKeysWithIdsAndNames: List<Triple<String, Long?, String>>,
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        database.withTransaction {
            for ((key, fid, name) in foodKeysWithIdsAndNames) {
                val existing = nutraDao.findFoodFeedbackByKey(key)
                val updated = if (existing != null) {
                    existing.copy(
                        foodId = fid ?: existing.foodId,
                        foodName = name.ifBlank { existing.foodName },
                        acceptedCount = existing.acceptedCount + 1,
                        lastAcceptedMillis = now,
                        updatedAtMillis = now,
                    )
                } else {
                    FoodFeedbackEntity(
                        foodKey = key,
                        foodId = fid,
                        foodName = name,
                        status = FoodFeedbackStatus.NEUTRAL.name,
                        acceptedCount = 1,
                        dismissedCount = 0,
                        lastSuggestedMillis = now,
                        lastAcceptedMillis = now,
                        updatedAtMillis = now,
                    )
                }
                nutraDao.upsertFoodFeedback(updated)
            }
        }
    }

    suspend fun setFoodDisliked(
        foodKey: String,
        foodId: Long?,
        foodName: String,
        disliked: Boolean,
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        database.withTransaction {
            val existing = nutraDao.findFoodFeedbackByKey(foodKey)
            val nextStatus = if (disliked) FoodFeedbackStatus.DISLIKED.name else FoodFeedbackStatus.NEUTRAL.name
            val updated = if (existing != null) {
                existing.copy(
                    foodId = foodId ?: existing.foodId,
                    foodName = foodName.ifBlank { existing.foodName },
                    status = nextStatus,
                    dismissedCount = if (disliked) existing.dismissedCount + 1 else existing.dismissedCount,
                    updatedAtMillis = now,
                )
            } else {
                FoodFeedbackEntity(
                    foodKey = foodKey,
                    foodId = foodId,
                    foodName = foodName,
                    status = nextStatus,
                    dismissedCount = if (disliked) 1 else 0,
                    updatedAtMillis = now,
                )
            }
            nutraDao.upsertFoodFeedback(updated)
        }
    }

    suspend fun saveMetabolismSnapshots(snapshots: List<MetabolismSnapshotRecord>) = withContext(Dispatchers.IO) {
        if (snapshots.isEmpty()) return@withContext
        val latestIncoming = snapshots.maxByOrNull { it.dateIso } ?: return@withContext
        val existingLatest = nutraDao.getLatestMetabolismSnapshot()
        if (existingLatest != null &&
            existingLatest.dateIso == latestIncoming.dateIso &&
            existingLatest.qualityLevel == latestIncoming.qualityLevel &&
            abs(existingLatest.estimatedTdeeKcal - latestIncoming.estimatedTdeeKcal) < 15 &&
            abs(existingLatest.uncertaintyKcal - latestIncoming.uncertaintyKcal) < 10
        ) {
            return@withContext
        }
        nutraDao.upsertMetabolismSnapshots(snapshots.map { MetabolismSnapshotEntity.fromDomain(it) })
    }

    fun observeDailySummary(dateIso: String): Flow<DailyNutritionSummary> {
        val mealsFlow = nutraDao.observeMealLogsForDate(dateIso)
            .map { mealEntities ->
                val buckets = Array(MealType.entries.size) { mutableListOf<MealLogItem>() }
                for (i in mealEntities.indices) {
                    val item = mealEntities[i].toDomain()
                    buckets[item.mealType.ordinal].add(item)
                }
                MealType.entries.map { type ->
                    MealSummary(
                        mealType = type,
                        items = buckets[type.ordinal],
                    )
                }
            }
            .distinctUntilChanged()

        val waterMlFlow = nutraDao.observeWaterForDate(dateIso)
            .map { it?.waterMl ?: 0 }
            .distinctUntilChanged()

        return combine(
            goalsFlow,
            observeGoalHistory(),
            mealsFlow,
            waterMlFlow,
        ) { liveGoals, goalHistory, mealsByType, waterMl ->
            val todayIso = LocalDate.now().toString()
            val effectiveGoals = if (dateIso < todayIso) {
                goalHistory
                    .asSequence()
                    .filter { it.effectiveDateIso <= dateIso }
                    .maxWithOrNull(compareBy<GoalHistoryEntry> { it.effectiveDateIso }.thenBy { it.timestampMillis })
                    ?.let { entry ->
                        MacroGoals(
                            calories = entry.calories,
                            proteinGrams = entry.proteinGrams,
                            carbsGrams = entry.carbsGrams,
                            fatGrams = entry.fatGrams,
                            waterMl = entry.waterGoalMl,
                        )
                    } ?: liveGoals
            } else {
                liveGoals
            }
            DailyNutritionSummary(
                dateIso = dateIso,
                goals = effectiveGoals,
                meals = mealsByType,
                waterMl = waterMl,
            )
        }.distinctUntilChanged().flowOn(Dispatchers.Default)
    }

    fun observeAnalyticsRange(
        startDate: LocalDate,
        endDate: LocalDate,
    ): Flow<List<DailyAnalyticsPoint>> {
        val startIso = startDate.toString()
        val endIso = endDate.toString()

        val totalsByDateFlow = nutraDao.observeDailyMealTotalsInRange(startIso, endIso)
            .map { rows ->
                val map = HashMap<String, DailyMealTotalsTuple>(rows.size.coerceAtLeast(8))
                for (i in rows.indices) {
                    val row = rows[i]
                    map[row.dateIso] = row
                }
                map
            }
            .distinctUntilChanged()

        val waterByDateFlow = nutraDao.observeWaterInRange(startIso, endIso)
            .map { waterLogs ->
                val map = HashMap<String, Int>(waterLogs.size.coerceAtLeast(8))
                for (i in waterLogs.indices) {
                    val water = waterLogs[i]
                    map[water.dateIso] = water.waterMl
                }
                map
            }
            .distinctUntilChanged()

        return combine(
            calorieGoalFlow,
            observeGoalHistory(),
            totalsByDateFlow,
            waterByDateFlow,
        ) { currentTargetCalories, goalHistory, totalsByDate, waterByDate ->
            val sortedHistory = goalHistory.sortedWith(
                compareBy<GoalHistoryEntry> { it.effectiveDateIso }.thenBy { it.timestampMillis },
            )
            val daysCount = (ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1).coerceAtLeast(1)
            val points = ArrayList<DailyAnalyticsPoint>(daysCount)
            var cursor = startDate
            var histIdx = 0
            var effectiveHistoricalCalories: Int? = null
            while (!cursor.isAfter(endDate)) {
                val iso = cursor.toString()
                while (histIdx < sortedHistory.size && sortedHistory[histIdx].effectiveDateIso <= iso) {
                    effectiveHistoricalCalories = sortedHistory[histIdx].calories
                    histIdx++
                }
                val acc = totalsByDate[iso]
                val dayWater = waterByDate[iso] ?: 0
                points.add(
                    DailyAnalyticsPoint(
                        dateIso = iso,
                        calories = acc?.totalCalories ?: 0,
                        targetCalories = effectiveHistoricalCalories ?: currentTargetCalories,
                        proteinGrams = acc?.totalProtein?.toFloat() ?: 0f,
                        carbsGrams = acc?.totalCarbs?.toFloat() ?: 0f,
                        fatGrams = acc?.totalFat?.toFloat() ?: 0f,
                        waterMl = dayWater,
                    ),
                )
                cursor = cursor.plusDays(1)
            }
            points
        }.distinctUntilChanged().flowOn(Dispatchers.Default)
    }

    fun observeWeightEntries(): Flow<List<WeightEntry>> =
        nutraDao.observeAllWeightLogs()
            .map { list -> list.map { it.toDomain() } }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    suspend fun logFoodToMeal(
        dateIso: String,
        mealType: MealType,
        food: FoodItem,
        unitLabel: String,
        unitGrams: Float,
        multiplier: Float,
    ): MealLogItem = withContext(Dispatchers.IO) {
        val item = buildMealLogItem(
            dateIso = dateIso,
            mealType = mealType,
            food = food,
            unitLabel = unitLabel,
            unitGrams = unitGrams,
            multiplier = multiplier,
        )
        val newId = nutraDao.insertMealLog(MealLogEntity.fromDomain(item))
        item.copy(id = newId)
    }

    suspend fun createCustomFoodAndLog(
        dateIso: String,
        mealType: MealType,
        food: FoodItem,
    ): FoodItem = withContext(Dispatchers.IO) {
        storageMutex.withLock {
            ensureCatalogSeeded()
            val normName = TurkishTextNormalizer.normalize(food.name)
            val normSearch = TurkishTextNormalizer.normalize("${food.name} ${food.brand.orEmpty()}")
            val uid = food.customUid?.takeIf { it.isNotBlank() } ?: "CUSTOM_${UUID.randomUUID()}"
            val prepared = food.copy(
                isCustom = true,
                allergenVerified = false,
                customUid = uid,
                normalizedName = normName,
                normalizedSearchText = normSearch,
            )
            database.withTransaction {
                val id = nutraDao.insertFood(FoodItemEntity.fromDomain(prepared, normSearch))
                val savedFood = prepared.copy(id = id)
                val logItem = buildMealLogItem(
                    dateIso = dateIso,
                    mealType = mealType,
                    food = savedFood,
                    unitLabel = savedFood.defaultUnitLabel,
                    unitGrams = savedFood.defaultUnitGrams,
                    multiplier = 1f,
                )
                nutraDao.insertMealLog(MealLogEntity.fromDomain(logItem))
                savedFood
            }
        }
    }

    private fun buildMealLogItem(
        dateIso: String,
        mealType: MealType,
        food: FoodItem,
        unitLabel: String,
        unitGrams: Float,
        multiplier: Float,
    ): MealLogItem {
        val isRawUnitMode = ServingUnitLabels.isRawUnitLabel(unitLabel)
        val finiteMultiplier = if (multiplier.isFinite()) multiplier else if (isRawUnitMode) 100f else 1f
        val finiteUnitGrams = if (unitGrams.isFinite() && unitGrams > 0f) unitGrams else food.defaultUnitGrams.takeIf { it.isFinite() && it > 0f } ?: 100f
        val safeMultiplier = if (isRawUnitMode) {
            finiteMultiplier.coerceIn(1f, 3000f)
        } else {
            finiteMultiplier.coerceIn(0.1f, 50f)
        }
        val totalGrams = if (isRawUnitMode) {
            safeMultiplier
        } else {
            (finiteUnitGrams * safeMultiplier).coerceAtLeast(1f)
        }
        return MealLogItem(
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
            timestampMillis = System.currentTimeMillis(),
            baseUnit = food.displayUnitSuffix,
        )
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
                baseUnit = ServingUnitLabels.normalizedBaseUnit(entry.baseUnit),
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
        val isRawUnitMode = ServingUnitLabels.isRawUnitLabel(logItem.unitLabel)
        val finiteNewMult = if (newMultiplier.isFinite()) newMultiplier else logItem.multiplier.takeIf { it.isFinite() } ?: 1f
        val safeNewMult = if (isRawUnitMode) {
            finiteNewMult.coerceIn(1f, 3000f)
        } else {
            finiteNewMult.coerceIn(0.25f, 20f)
        }
        val safeOldMult = (if (logItem.multiplier.isFinite()) logItem.multiplier else 1f).coerceAtLeast(0.05f)
        val sourceFood = logItem.foodId?.let { nutraDao.findFoodById(it)?.toDomain() }

        val updated = if (sourceFood != null) {
            val newTotalGrams = if (isRawUnitMode) {
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
            val newTotalGrams = if (isRawUnitMode) {
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
        storageMutex.withLock {
            nutraDao.updateFavorite(foodId, isFavorite)
        }
    }

    suspend fun flipFavoriteFood(foodId: Long): Boolean? = withContext(Dispatchers.IO) {
        storageMutex.withLock {
            val current = nutraDao.findFoodById(foodId) ?: return@withLock null
            val next = !current.isFavorite
            nutraDao.updateFavorite(foodId, next)
            next
        }
    }

    suspend fun createCustomFood(food: FoodItem): FoodItem = withContext(Dispatchers.IO) {
        storageMutex.withLock {
            ensureCatalogSeeded()
            val normName = TurkishTextNormalizer.normalize(food.name)
            val normSearch = TurkishTextNormalizer.normalize("${food.name} ${food.brand.orEmpty()}")
            val uid = food.customUid?.takeIf { it.isNotBlank() } ?: "CUSTOM_${UUID.randomUUID()}"
            val prepared = food.copy(
                isCustom = true,
                allergenVerified = false,
                customUid = uid,
                normalizedName = normName,
                normalizedSearchText = normSearch,
            )
            val id = nutraDao.insertFood(FoodItemEntity.fromDomain(prepared, normSearch))
            prepared.copy(id = id)
        }
    }

    open suspend fun getWaterMl(dateIso: String): Int = withContext(Dispatchers.IO) {
        nutraDao.getWaterForDate(dateIso)?.waterMl ?: 0
    }

    suspend fun addWaterMl(dateIso: String, deltaMl: Int) = withContext(Dispatchers.IO) {
        storageMutex.withLock {
            nutraDao.addWaterDeltaAtomic(
                dateIso = dateIso,
                deltaMl = deltaMl,
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
    }

    suspend fun logWeight(
        dateIso: String,
        weightKg: Float,
        syncProfileWeight: Boolean = true,
    ) = withContext(Dispatchers.IO) {
        if (!weightKg.isFinite()) return@withContext
        storageMutex.withLock {
            val safeWeight = weightKg.coerceIn(30f, 300f)
            nutraDao.insertWeightLog(
                WeightLogEntity(
                    dateIso = dateIso,
                    weightKg = safeWeight,
                    timestampMillis = System.currentTimeMillis(),
                ),
            )
            if (syncProfileWeight) {
                preferencesDataStore.updateCurrentWeight(safeWeight)
            }
        }
    }

    suspend fun upsertDailyWeight(
        dateIso: String,
        weightKg: Float,
        syncProfileWeight: Boolean = false,
    ) = withContext(Dispatchers.IO) {
        if (!weightKg.isFinite()) return@withContext
        storageMutex.withLock {
            val safeWeight = weightKg.coerceIn(30f, 300f)
            val now = System.currentTimeMillis()
            database.withTransaction {
                val existing = nutraDao.findLatestWeightLogForDate(dateIso)
                if (existing != null) {
                    nutraDao.updateWeightLog(
                        existing.copy(
                            weightKg = safeWeight,
                            timestampMillis = now,
                        ),
                    )
                } else {
                    nutraDao.insertWeightLog(
                        WeightLogEntity(
                            dateIso = dateIso,
                            weightKg = safeWeight,
                            timestampMillis = now,
                        ),
                    )
                }
            }
            if (syncProfileWeight) {
                preferencesDataStore.updateCurrentWeight(safeWeight)
            }
        }
    }

    suspend fun updateWeightEntry(entry: WeightEntry, newWeightKg: Float) = withContext(Dispatchers.IO) {
        if (!newWeightKg.isFinite() || entry.id <= 0L) return@withContext
        storageMutex.withLock {
            val safeWeight = newWeightKg.coerceIn(30f, 300f)
            nutraDao.updateWeightLog(
                WeightLogEntity(
                    id = entry.id,
                    dateIso = entry.dateIso,
                    weightKg = safeWeight,
                    timestampMillis = entry.timestampMillis,
                ),
            )
            val latest = nutraDao.getAllWeightLogsSnapshot().lastOrNull()
            if (latest != null) {
                preferencesDataStore.updateCurrentWeight(latest.weightKg)
            }
        }
    }

    suspend fun deleteWeightEntry(entryId: Long) = withContext(Dispatchers.IO) {
        if (entryId <= 0L) return@withContext
        storageMutex.withLock {
            nutraDao.deleteWeightLogById(entryId)
            val latest = nutraDao.getAllWeightLogsSnapshot().lastOrNull()
            if (latest != null) {
                preferencesDataStore.updateCurrentWeight(latest.weightKg)
            }
        }
    }

    private data class RoomBackupSnapshot(
        val seedFavoritesById: Map<Long, Boolean>,
        val customFoods: List<FoodItemEntity>,
        val mealLogs: List<MealLogEntity>,
        val waterLogs: List<WaterLogEntity>,
        val weightLogs: List<WeightLogEntity>,
        val goalHistory: List<GoalHistoryEntity>,
        val foodFeedback: List<FoodFeedbackEntity>,
    )

    private suspend fun captureRoomSnapshot(): RoomBackupSnapshot = database.withTransaction {
        val allFoods = nutraDao.getAllFoodsSnapshot()
        RoomBackupSnapshot(
            seedFavoritesById = allFoods.filter { !it.isCustom }.associate { it.id to it.isFavorite },
            customFoods = allFoods.filter { it.isCustom },
            mealLogs = nutraDao.getAllMealLogsSnapshot(),
            waterLogs = nutraDao.getAllWaterLogsSnapshot(),
            weightLogs = nutraDao.getAllWeightLogsSnapshot(),
            goalHistory = nutraDao.getAllGoalHistorySnapshot(),
            foodFeedback = nutraDao.getAllFoodFeedbackSnapshot(),
        )
    }

    private suspend fun rollbackRoomSnapshot(snapshot: RoomBackupSnapshot) {
        database.withTransaction {
            nutraDao.clearSeedFavorites()
            for ((seedId, isFav) in snapshot.seedFavoritesById) {
                if (isFav) nutraDao.updateFavorite(seedId, true)
            }
            nutraDao.deleteAllCustomFoods()
            if (snapshot.customFoods.isNotEmpty()) {
                nutraDao.insertFoods(snapshot.customFoods)
            }
            nutraDao.deleteAllMealLogs()
            if (snapshot.mealLogs.isNotEmpty()) {
                nutraDao.insertMealLogs(snapshot.mealLogs)
            }
            nutraDao.deleteAllWaterLogs()
            if (snapshot.waterLogs.isNotEmpty()) {
                nutraDao.upsertWaterLogs(snapshot.waterLogs)
            }
            nutraDao.deleteAllWeightLogs()
            if (snapshot.weightLogs.isNotEmpty()) {
                nutraDao.insertWeightLogs(snapshot.weightLogs)
            }
            nutraDao.deleteAllGoalHistory()
            if (snapshot.goalHistory.isNotEmpty()) {
                nutraDao.insertGoalHistoryEntries(snapshot.goalHistory)
            }
            nutraDao.deleteAllFoodFeedback()
            if (snapshot.foodFeedback.isNotEmpty()) {
                nutraDao.upsertFoodFeedbackList(snapshot.foodFeedback)
            }
        }
    }

    private suspend fun buildBackupPayloadLocked(): NutraBackupPayload {
        val profile = preferencesDataStore.getSnapshot()
        return database.withTransaction {
            val allFoods = nutraDao.getAllFoodsSnapshot().map { it.toDomain() }
            val customFoods = allFoods.filter { it.isCustom || it.isFavorite }
            val mealLogs = nutraDao.getAllMealLogsSnapshot().map { it.toDomain() }
            val waterLogs = nutraDao.getAllWaterLogsSnapshot().associate { it.dateIso to it.waterMl }
            val weightEntries = nutraDao.getAllWeightLogsSnapshot().map { it.toDomain() }
            val goalHistory = nutraDao.getAllGoalHistorySnapshot().map { it.toDomain() }
            val foodFeedback = nutraDao.getAllFoodFeedbackSnapshot().map { it.toDomain() }
            NutraBackupPayload(
                version = SUPPORTED_BACKUP_VERSION,
                exportedAtDateIso = LocalDate.now().toString(),
                userProfile = profile,
                customFoods = customFoods,
                mealLogs = mealLogs,
                waterLogs = waterLogs,
                weightEntries = weightEntries,
                goalHistory = goalHistory,
                foodFeedback = foodFeedback,
            )
        }
    }

    suspend fun exportBackupJson(): AppResult<String> = withContext(Dispatchers.IO) {
        storageMutex.withLock {
            try {
                val snapshot = buildBackupPayloadLocked()
                AppResult.Success(backupJson.encodeToString(snapshot))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppResult.Error("Yedekleme oluşturulamadı. Lütfen tekrar dene.", e)
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun exportBackupToStream(outputStream: OutputStream): AppResult<Unit> = withContext(Dispatchers.IO) {
        storageMutex.withLock {
            try {
                val snapshot = buildBackupPayloadLocked()
                backupJson.encodeToStream(snapshot, outputStream)
                outputStream.flush()
                AppResult.Success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppResult.Error("Yedek dosyası yazılamadı. Depolama iznini kontrol edip tekrar dene.", e)
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun inspectBackupStream(inputStream: InputStream): AppResult<BackupValidationReport> = withContext(Dispatchers.IO) {
        try {
            val buffer = ByteArray(16 * 1024)
            val baos = java.io.ByteArrayOutputStream()
            var totalRead = 0
            while (true) {
                val read = inputStream.read(buffer)
                if (read == -1) break
                totalRead += read
                if (totalRead > MAX_BACKUP_BYTES) {
                    return@withContext AppResult.Error("Yedek dosyası izin verilen 20 MB sınırını aşıyor.")
                }
                baos.write(buffer, 0, read)
            }
            val bytes = baos.toByteArray()
            if (bytes.isEmpty()) {
                return@withContext AppResult.Error("Yedek dosyası boş veya okunamadı.")
            }
            val rawText = bytes.toString(Charsets.UTF_8)
            if (rawText.isBlank()) {
                return@withContext AppResult.Error("Yedek dosyası boş veya okunamadı.")
            }
            if (!verifyJsonNestingDepth(rawText)) {
                return@withContext AppResult.Error("Yedek dosyasının JSON yapısı bozuk veya aşırı iç içe.")
            }
            val payload = backupJson.decodeFromStream<NutraBackupPayload>(ByteArrayInputStream(bytes))
            val report = analyzeBackupPayload(payload)
            if (report.blockingError != null) {
                AppResult.Error(report.blockingError)
            } else {
                AppResult.Success(report)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppResult.Error("Yedek dosyası okunamadı. Geçerli bir Nutra JSON yedeği seçtiğinden emin ol.", e)
        }
    }

    suspend fun inspectBackupJson(rawJson: String): AppResult<BackupValidationReport> =
        inspectBackupStream(ByteArrayInputStream(rawJson.toByteArray(Charsets.UTF_8)))

    suspend fun importBackupJson(rawJson: String): AppResult<Unit> = withContext(Dispatchers.IO) {
        when (val inspected = inspectBackupJson(rawJson)) {
            is AppResult.Error -> inspected
            is AppResult.Success -> importBackupPayload(inspected.data.payload)
        }
    }

    suspend fun importBackupPayload(payload: NutraBackupPayload): AppResult<Unit> = withContext(Dispatchers.IO) {
        storageMutex.withLock {
            val report = analyzeBackupPayload(payload)
            if (report.blockingError != null) {
                return@withLock AppResult.Error(report.blockingError)
            }

            ensureCatalogSeeded()
            val previousRoomSnapshot = try {
                captureRoomSnapshot()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@withLock AppResult.Error("Mevcut veriler yedeklenemediği için geri yükleme iptal edildi.", e)
            }

            try {
                database.withTransaction {
                    nutraDao.clearSeedFavorites()
                    // Remove stale local custom foods so pre-existing custom rows never pollute restored state
                    nutraDao.deleteAllCustomFoods()

                    val existingFoods = nutraDao.getAllFoodsSnapshot()
                    val existingById = existingFoods.associateBy { it.id }
                    val existingSeedByCode = existingFoods
                        .filter { !it.isCustom && !it.catalogCode.isNullOrBlank() }
                        .associateBy { it.catalogCode!! }
                    val existingByBarcode = existingFoods
                        .filter { !it.barcode.isNullOrBlank() }
                        .associateBy { it.barcode!! }
                        .toMutableMap()
                    val existingSeedByName = existingFoods
                        .filter { !it.isCustom }
                        .associateBy { TurkishTextNormalizer.normalize(it.name) }
                    val restoredCustomByUid = HashMap<String, FoodItemEntity>()
                    val restoredCustomByName = HashMap<String, FoodItemEntity>()
                    val nameToTargetId = existingFoods
                        .associate { TurkishTextNormalizer.normalize(it.name) to it.id }
                        .toMutableMap()
                    for ((legacyNorm, targetCode) in TurkishFoodSeedCatalog.LEGACY_NAME_TO_CATALOG_CODE) {
                        val targetEntity = existingSeedByCode[targetCode] ?: continue
                        nameToTargetId.putIfAbsent(legacyNorm, targetEntity.id)
                    }
                    val importedFoodIdMap = HashMap<Long, Long>()

                    for (rawItem in payload.customFoods) {
                        if (!isValidBackupFood(rawItem)) continue
                        val normName = TurkishTextNormalizer.normalize(rawItem.name)
                        if (normName.isBlank()) continue
                        val normSearch = TurkishTextNormalizer.normalize("${rawItem.name} ${rawItem.brand.orEmpty()}")

                        if (!rawItem.isCustom) {
                            // Seed/catalog item: match by catalogCode, legacy code map, barcode, or seed name
                            val seedMatch = rawItem.catalogCode?.takeIf { it.isNotBlank() }?.let { existingSeedByCode[it] }
                                ?: TurkishFoodSeedCatalog.LEGACY_NAME_TO_CATALOG_CODE[normName]?.let { existingSeedByCode[it] }
                                ?: rawItem.barcode?.takeIf { it.isNotBlank() }?.let { existingByBarcode[it] }?.takeIf { !it.isCustom }
                                ?: existingSeedByName[normName]

                            if (seedMatch != null) {
                                if (seedMatch.isFavorite != rawItem.isFavorite) {
                                    nutraDao.updateFavorite(seedMatch.id, rawItem.isFavorite)
                                }
                                if (rawItem.id > 0L) importedFoodIdMap[rawItem.id] = seedMatch.id
                                nameToTargetId[normName] = seedMatch.id
                            } else {
                                // Unmatched legacy non-custom food: store safely as a custom food rather than a broken null-code seed
                                val fallbackUid = rawItem.customUid?.takeIf { it.isNotBlank() }
                                    ?: "IMPORTED_${UUID.randomUUID()}"
                                val sanitizedCustom = sanitizeImportedCustomFood(rawItem, fallbackUid, normName, normSearch)
                                val entity = FoodItemEntity.fromDomain(sanitizedCustom, normSearch)
                                val newId = nutraDao.insertFood(entity)
                                val inserted = entity.copy(id = newId)
                                restoredCustomByUid[fallbackUid] = inserted
                                restoredCustomByName[normName] = inserted
                                if (rawItem.id > 0L) importedFoodIdMap[rawItem.id] = newId
                                nameToTargetId[normName] = newId
                            }
                        } else {
                            // Custom food: NEVER match against catalog seeds (prevents silent custom-food loss!)
                            val uid = rawItem.customUid?.takeIf { it.isNotBlank() }
                                ?: "CUSTOM_${UUID.randomUUID()}"
                            val existingCustomMatch = restoredCustomByUid[uid] ?: restoredCustomByName[normName]
                            val sanitizedCustom = sanitizeImportedCustomFood(rawItem, uid, normName, normSearch)
                            if (existingCustomMatch != null) {
                                val updatedEntity = FoodItemEntity.fromDomain(
                                    item = sanitizedCustom.copy(id = existingCustomMatch.id),
                                    normalizedText = normSearch,
                                )
                                nutraDao.updateFood(updatedEntity)
                                restoredCustomByUid[uid] = updatedEntity
                                restoredCustomByName[normName] = updatedEntity
                                if (rawItem.id > 0L) importedFoodIdMap[rawItem.id] = existingCustomMatch.id
                                nameToTargetId[normName] = existingCustomMatch.id
                            } else {
                                val entity = FoodItemEntity.fromDomain(sanitizedCustom.copy(id = 0L), normSearch)
                                val newId = nutraDao.insertFood(entity)
                                val inserted = entity.copy(id = newId)
                                restoredCustomByUid[uid] = inserted
                                restoredCustomByName[normName] = inserted
                                if (!inserted.barcode.isNullOrBlank()) {
                                    existingByBarcode[inserted.barcode] = inserted
                                }
                                if (rawItem.id > 0L) importedFoodIdMap[rawItem.id] = newId
                                nameToTargetId[normName] = newId
                            }
                        }
                    }

                    nutraDao.deleteAllMealLogs()
                    val mealEntities = payload.mealLogs
                        .filter(::isValidBackupMealLog)
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
                                    timestampMillis = log.timestampMillis.coerceAtLeast(0L),
                                ),
                            )
                        }
                    if (mealEntities.isNotEmpty()) {
                        nutraDao.insertMealLogs(mealEntities)
                    }

                    nutraDao.deleteAllWaterLogs()
                    val now = System.currentTimeMillis()
                    val waterEntities = payload.waterLogs
                        .filterKeys(::isValidBackupIsoDate)
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
                        .filter(::isValidBackupWeightEntry)
                        .map {
                            WeightLogEntity(
                                dateIso = it.dateIso,
                                weightKg = it.weightKg.coerceIn(30f, 300f),
                                timestampMillis = it.timestampMillis.coerceAtLeast(0L),
                            )
                        }
                    if (weightEntities.isNotEmpty()) {
                        nutraDao.insertWeightLogs(weightEntities)
                    }

                    nutraDao.deleteAllGoalHistory()
                    val goalHistoryEntities = payload.goalHistory
                        .filter { isValidBackupIsoDate(it.effectiveDateIso) && it.calories > 0 }
                        .map { GoalHistoryEntity.fromDomain(sanitizeImportedGoalHistory(it)) }
                    if (goalHistoryEntities.isNotEmpty()) {
                        nutraDao.insertGoalHistoryEntries(goalHistoryEntities)
                    }

                    nutraDao.deleteAllFoodFeedback()
                    val feedbackEntities = payload.foodFeedback
                        .filter { it.foodKey.isNotBlank() }
                        .map { record ->
                            FoodFeedbackEntity.fromDomain(
                                remapAndSanitizeFoodFeedback(
                                    record = record,
                                    importedFoodIdMap = importedFoodIdMap,
                                    nameToTargetId = nameToTargetId,
                                ),
                            )
                        }
                    if (feedbackEntities.isNotEmpty()) {
                        nutraDao.upsertFoodFeedbackList(feedbackEntities)
                    }

                    nutraDao.deleteAllMetabolismSnapshots()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@withLock AppResult.Error("Veritabanı geri yüklenirken hata oluştu; mevcut kayıtların korundu.", e)
            }

            // Phase 2: Restore DataStore profile; if DataStore fails, rollback Room atomically
            try {
                preferencesDataStore.restoreProfile(payload.userProfile)
                AppResult.Success(Unit)
            } catch (e: CancellationException) {
                runCatching { rollbackRoomSnapshot(previousRoomSnapshot) }
                throw e
            } catch (e: Exception) {
                runCatching { rollbackRoomSnapshot(previousRoomSnapshot) }
                AppResult.Error("Profil ayarları geri yüklenemedi; önceki tüm verilerin güvenle geri alındı.", e)
            }
        }
    }

    private fun sanitizeImportedCustomFood(
        raw: FoodItem,
        uid: String,
        normName: String,
        normSearch: String,
    ): FoodItem = raw.copy(
        id = 0L,
        name = raw.name.trim(),
        subtitle = raw.subtitle.trim(),
        caloriesPer100g = raw.caloriesPer100g.coerceIn(0, 1200),
        proteinPer100g = (if (raw.proteinPer100g.isFinite()) raw.proteinPer100g else 0f).coerceIn(0f, 100f),
        carbsPer100g = (if (raw.carbsPer100g.isFinite()) raw.carbsPer100g else 0f).coerceIn(0f, 100f),
        fatPer100g = (if (raw.fatPer100g.isFinite()) raw.fatPer100g else 0f).coerceIn(0f, 100f),
        defaultUnitLabel = raw.defaultUnitLabel.trim().ifBlank { "1 Porsiyon" },
        defaultUnitGrams = (if (raw.defaultUnitGrams.isFinite()) raw.defaultUnitGrams else 100f).coerceIn(1f, 5000f),
        isCustom = true,
        catalogCode = null,
        allergenVerified = false,
        customUid = uid,
        baseUnit = ServingUnitLabels.normalizedBaseUnit(raw.baseUnit),
        normalizedName = normName,
        normalizedSearchText = normSearch,
    )
}
