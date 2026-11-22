package com.nutra.app

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nutra.app.core.database.FoodItemEntity
import com.nutra.app.core.database.NutraDatabase
import com.nutra.app.core.database.TurkishFoodSeedCatalog
import com.nutra.app.core.datastore.UserPreferencesDataStore
import com.nutra.app.domain.model.AllergenTag
import com.nutra.app.domain.model.AppResult
import com.nutra.app.domain.model.DietStyle
import com.nutra.app.domain.model.DietaryProfile
import com.nutra.app.domain.model.FoodCategory
import com.nutra.app.domain.model.FoodFeedbackRecord
import com.nutra.app.domain.model.FoodFeedbackStatus
import com.nutra.app.domain.model.FoodItem
import com.nutra.app.domain.model.GoalChangeSource
import com.nutra.app.domain.model.GoalHistoryEntry
import com.nutra.app.domain.model.GoalType
import com.nutra.app.domain.model.MacroGoals
import com.nutra.app.domain.model.MealLogItem
import com.nutra.app.domain.model.MealType
import com.nutra.app.domain.model.UserProfile
import com.nutra.app.domain.model.WeightEntry
import com.nutra.app.domain.repository.NutraBackupPayload
import com.nutra.app.domain.repository.NutritionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37], application = Application::class)
class NutritionRepositoryIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var app: Application
    private lateinit var database: NutraDatabase
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var preferences: UserPreferencesDataStore
    private lateinit var repository: NutritionRepository

    private var failNextRestoreProfile = false

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        app = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(app, NutraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val prefsFile = File(tempFolder.newFolder("ds_${UUID.randomUUID()}"), "nutra_test.preferences_pb")
        val backingDataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) { prefsFile }
        preferences = object : UserPreferencesDataStore(app, backingDataStore) {
            override suspend fun restoreProfile(profile: UserProfile) {
                if (failNextRestoreProfile) {
                    failNextRestoreProfile = false
                    throw IOException("Simulated DataStore write failure")
                }
                super.restoreProfile(profile)
            }
        }
        repository = NutritionRepository(
            appContext = app,
            database = database,
            nutraDao = database.nutraDao(),
            preferencesDataStore = preferences,
        )
    }

    @After
    fun tearDown() {
        database.close()
        dataStoreScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun backupRoundTrip_preservesCustomUidGoalHistoryWaterGoalFeedbackMealsWaterAndWeights() = runBlocking {
        val today = LocalDate.of(2026, 11, 21)
        val todayIso = today.toString()

        val initialProfile = UserProfile(
            age = 31,
            heightCm = 179,
            currentWeightKg = 77.5f,
            targetWeightKg = 72.0f,
            goalType = GoalType.LOSE_WEIGHT,
            goals = MacroGoals(calories = 2150, proteinGrams = 155, carbsGrams = 215, fatGrams = 68, waterMl = 2800),
            onboardingCompleted = true,
            dietaryProfile = DietaryProfile(
                dietStyle = DietStyle.MEDITERRANEAN,
                excludedAllergens = setOf(AllergenTag.PEANUT),
                excludedKeywords = listOf("pırasa"),
            ),
        )
        preferences.saveOnboardingProfile(initialProfile)
        preferences.updateDietaryProfile(initialProfile.dietaryProfile)
        repository.ensureCatalogSeeded()

        val customUid = "custom-test-uid-001"
        val customId = database.nutraDao().insertFood(
            FoodItemEntity.fromDomain(
                FoodItem(
                    name = "Özel Granola Kasesi",
                    subtitle = "Ev yapımı",
                    category = FoodCategory.BREAKFAST,
                    caloriesPer100g = 340,
                    proteinPer100g = 11f,
                    carbsPer100g = 52f,
                    fatPer100g = 9.5f,
                    defaultUnitLabel = "1 Kase",
                    defaultUnitGrams = 60f,
                    isCustom = true,
                    customUid = customUid,
                    allergenVerified = false,
                ),
                normalizedText = "ozel granola kasesi ev yapimi",
            ),
        )
        val savedCustom = database.nutraDao().getAllFoodsSnapshot().first { it.id == customId }.toDomain()
        repository.logFoodToMeal(
            dateIso = todayIso,
            mealType = MealType.BREAKFAST,
            food = savedCustom,
            unitLabel = "1 Kase",
            unitGrams = 60f,
            multiplier = 1f,
        )
        repository.addWaterMl(todayIso, 1250)
        repository.logWeight(todayIso, 77.5f)
        repository.recordGoalHistory(
            effectiveDateIso = todayIso,
            goals = initialProfile.goals,
            goalType = initialProfile.goalType,
            targetWeightKg = initialProfile.targetWeightKg,
            source = GoalChangeSource.MANUAL,
        )
        repository.setFoodDisliked(
            foodKey = "TR0001",
            foodId = null,
            foodName = "Dardanel Aç Ye Mısırlı Ton Balığı",
            disliked = true,
        )

        // Export via streaming API
        val out = ByteArrayOutputStream()
        val exportResult = repository.exportBackupToStream(out)
        assertTrue("Expected export success, got $exportResult", exportResult is AppResult.Success)
        val backupBytes = out.toByteArray()
        assertTrue(backupBytes.isNotEmpty())

        // Mutate live state before restoring
        repository.addWaterMl(todayIso, 500)
        assertEquals(1750, repository.getWaterMl(todayIso))

        // Phase 1: Inspect backup stream
        val inspectResult = repository.inspectBackupStream(ByteArrayInputStream(backupBytes))
        assertTrue("Expected inspect success, got $inspectResult", inspectResult is AppResult.Success)
        val report = (inspectResult as AppResult.Success).data
        assertNull(report.blockingError)
        // Backup exports user custom foods plus favorited catalog entries to preserve favorite state
        assertTrue("Expected at least 1 custom/favorite food in backup, got ${report.validCustomFoods}", report.validCustomFoods >= 1)
        assertEquals(1, report.validMealLogs)
        assertEquals(1, report.validWaterDays)
        assertEquals(1, report.validWeightEntries)
        assertEquals(1, report.validGoalHistory)
        assertEquals(1, report.validFoodFeedback)
        assertEquals(0, report.totalSkippedCount)

        // Phase 2: Import validated payload
        val importResult = repository.importBackupPayload(report.payload)
        assertTrue("Expected import success, got $importResult", importResult is AppResult.Success)

        // Verify full restoration including customUid, waterGoalMl, feedback & water amount
        assertEquals(1250, repository.getWaterMl(todayIso))
        val restoredProfile = preferences.getSnapshot()
        assertEquals(2150, restoredProfile.goals.calories)
        assertEquals(2800, restoredProfile.goals.waterMl)
        assertEquals(setOf(AllergenTag.PEANUT), restoredProfile.dietaryProfile.excludedAllergens)

        val restoredCustom = database.nutraDao().getAllFoodsSnapshot().firstOrNull { it.isCustom }
        assertNotNull("Custom food must be restored", restoredCustom)
        assertEquals(customUid, restoredCustom!!.customUid)

        val restoredMeals = database.nutraDao().getAllMealLogsSnapshot()
        assertEquals(1, restoredMeals.size)
        assertEquals(restoredCustom.id, restoredMeals.first().foodId)

        val restoredGoals = database.nutraDao().getAllGoalHistorySnapshot()
        assertEquals(1, restoredGoals.size)
        assertEquals(2800, restoredGoals.first().waterGoalMl)

        val restoredFeedback = database.nutraDao().getAllFoodFeedbackSnapshot()
        assertEquals(1, restoredFeedback.size)
        assertEquals("TR0001", restoredFeedback.first().foodKey)
    }

    @Test
    fun importBackupPayload_rollsBackRoomCompletelyWhenDataStoreWriteThrows() = runBlocking {
        val todayIso = "2026-11-21"
        preferences.saveOnboardingProfile(
            UserProfile(
                age = 28,
                currentWeightKg = 80f,
                goals = MacroGoals(calories = 2400, waterMl = 2500),
                onboardingCompleted = true,
            ),
        )
        repository.ensureCatalogSeeded()
        repository.addWaterMl(todayIso, 900)
        repository.logWeight(todayIso, 80.0f)

        val incomingPayload = NutraBackupPayload(
            version = 2,
            exportedAtDateIso = todayIso,
            userProfile = UserProfile(
                age = 40,
                currentWeightKg = 65f,
                goals = MacroGoals(calories = 1800, waterMl = 2000),
                onboardingCompleted = true,
            ),
            customFoods = emptyList(),
            mealLogs = listOf(
                MealLogItem(
                    id = 1L,
                    dateIso = todayIso,
                    mealType = MealType.LUNCH,
                    foodId = null,
                    foodName = "Yeni Yemek",
                    unitLabel = "1 Porsiyon",
                    multiplier = 1f,
                    totalGrams = 150f,
                    calories = 350,
                    proteinGrams = 25f,
                    carbsGrams = 30f,
                    fatGrams = 12f,
                    timestampMillis = 1000L,
                ),
            ),
            waterLogs = mapOf(todayIso to 3000),
            weightEntries = listOf(WeightEntry(1L, todayIso, 65.0f, 1000L)),
        )

        failNextRestoreProfile = true
        val result = repository.importBackupPayload(incomingPayload)
        assertTrue("Import must fail when DataStore throws IOException", result is AppResult.Error)

        // Room MUST have rolled back to the pre-import state!
        assertEquals("Water must roll back to 900 ml", 900, repository.getWaterMl(todayIso))
        val weightsAfterRollback = database.nutraDao().getAllWeightLogsSnapshot()
        assertEquals(1, weightsAfterRollback.size)
        assertEquals(80.0f, weightsAfterRollback.first().weightKg, 0.01f)
        assertEquals("Meal logs must roll back to empty", 0, database.nutraDao().getAllMealLogsSnapshot().size)
        assertEquals("Profile must remain at 2400 kcal", 2400, preferences.getSnapshot().goals.calories)
    }

    @Test
    fun inspectBackupStream_rejectsDeepNestingOversizedPayloadAndReportsPartialSkips() = runBlocking {
        // 1. Deeply nested JSON (> 16 levels) rejected safely
        val deepJson = "[".repeat(25) + "]".repeat(25)
        val deepResult = repository.inspectBackupStream(ByteArrayInputStream(deepJson.toByteArray()))
        assertTrue("Expected error on deep JSON nesting", deepResult is AppResult.Error)

        // 2. Partial corrupt items are counted and warned rather than crashing
        val partialJson = """
            {
              "version": 2,
              "exportedAtDateIso": "2026-11-21",
              "userProfile": {},
              "customFoods": [],
              "mealLogs": [
                {"id":1,"dateIso":"2026-11-21","mealType":"LUNCH","foodName":"Geçerli Yemek","unitLabel":"1 Porsiyon","multiplier":1,"totalGrams":100,"calories":200,"proteinGrams":15,"carbsGrams":20,"fatGrams":6,"timestampMillis":1000},
                {"id":2,"dateIso":"bad-date","mealType":"LUNCH","foodName":"Bozuk Tarih","unitLabel":"1 Porsiyon","multiplier":1,"totalGrams":100,"calories":200,"proteinGrams":15,"carbsGrams":20,"fatGrams":6,"timestampMillis":1001}
              ],
              "waterLogs": {"2026-11-21": 1500, "not-a-date": 500},
              "weightEntries": [{"id":1,"dateIso":"2026-11-21","weightKg":75.0,"timestampMillis":1000}]
            }
        """.trimIndent()
        val partialResult = repository.inspectBackupStream(ByteArrayInputStream(partialJson.toByteArray()))
        assertTrue("Expected partial backup to inspect cleanly, got $partialResult", partialResult is AppResult.Success)
        val report = (partialResult as AppResult.Success).data
        assertNull(report.blockingError)
        assertEquals(1, report.validMealLogs)
        assertEquals(1, report.skippedMealLogs)
        assertEquals(1, report.validWaterDays)
        assertEquals(1, report.skippedWaterDays)
        assertEquals(2, report.totalSkippedCount)
        assertTrue(report.hasWarnings)
    }

    @Test
    fun concurrentAtomicWaterDeltas_neverLoseUpdatesUnder20ParallelCalls() = runBlocking {
        val dateIso = "2026-11-21"
        val jobs = (1..20).map {
            async(Dispatchers.IO) {
                repository.addWaterMl(dateIso, 250)
            }
        }
        jobs.awaitAll()
        assertEquals(5000, repository.getWaterMl(dateIso))
    }

    @Test
    fun historicalGoalHistory_preservesPastDayTargetsInAnalyticsAndDailySummary() = runBlocking {
        val day1 = LocalDate.of(2026, 11, 15)
        val day5 = LocalDate.of(2026, 11, 19)
        val day7 = LocalDate.of(2026, 11, 21)

        preferences.saveOnboardingProfile(
            UserProfile(
                goals = MacroGoals(calories = 1900, proteinGrams = 140, carbsGrams = 180, fatGrams = 60, waterMl = 2200),
                onboardingCompleted = true,
            ),
        )
        repository.recordGoalHistory(
            effectiveDateIso = day1.toString(),
            goals = MacroGoals(calories = 2400, proteinGrams = 170, carbsGrams = 250, fatGrams = 75, waterMl = 3000),
            goalType = GoalType.MAINTAIN,
            targetWeightKg = 76f,
            source = GoalChangeSource.MANUAL,
        )
        repository.recordGoalHistory(
            effectiveDateIso = day5.toString(),
            goals = MacroGoals(calories = 1900, proteinGrams = 140, carbsGrams = 180, fatGrams = 60, waterMl = 2200),
            goalType = GoalType.LOSE_WEIGHT,
            targetWeightKg = 72f,
            source = GoalChangeSource.MANUAL,
        )

        val day2Summary = repository.observeDailySummary(day1.plusDays(1).toString()).first()
        assertEquals("Past date before day5 change must retain 2400 kcal goal", 2400, day2Summary.goals.calories)
        assertEquals("Past date before day5 change must retain 3000 ml water goal", 3000, day2Summary.goals.waterMl)

        val day7Summary = repository.observeDailySummary(day7.toString()).first()
        assertEquals("Date on/after day5 change must use 1900 kcal goal", 1900, day7Summary.goals.calories)
        assertEquals("Date on/after day5 change must use 2200 ml water goal", 2200, day7Summary.goals.waterMl)

        val rangePoints = repository.observeAnalyticsRange(startDate = day1, endDate = day7).first()
        val pDay2 = rangePoints.first { it.dateIso == day1.plusDays(1).toString() }
        val pDay7 = rangePoints.first { it.dateIso == day7.toString() }
        assertEquals(2400, pDay2.targetCalories)
        assertEquals(1900, pDay7.targetCalories)
    }

    @Test
    fun ensureCatalogSeeded_upgradesLegacyV2CatalogToV3WhilePreservingFavoritesAndCustomFoods() = runBlocking {
        // Seed one legacy v2 catalog item (with favorite = true) + one custom food
        val dao = database.nutraDao()
        val sampleV3 = TurkishFoodSeedCatalog.initialFoods.first { it.catalogCode == "TR0001" }
        val legacyEntity = FoodItemEntity.fromDomain(
            sampleV3.copy(
                name = "Eski Ton Balığı Adı",
                allergens = emptySet(),
                dietFlags = com.nutra.app.domain.model.FoodDietFlags(),
                allergenVerified = false,
                catalogVersion = 2,
                isFavorite = true,
            ),
            normalizedText = "eski ton baligi adi",
        )
        val originalId = dao.insertFood(legacyEntity)
        val customId = dao.insertFood(
            FoodItemEntity.fromDomain(
                FoodItem(
                    name = "Kullanıcı Özel Yemeği",
                    subtitle = "Ev yapımı",
                    category = FoodCategory.HOME_COOKED,
                    caloriesPer100g = 180,
                    proteinPer100g = 12f,
                    carbsPer100g = 15f,
                    fatPer100g = 8f,
                    defaultUnitLabel = "1 Porsiyon",
                    defaultUnitGrams = 200f,
                    isCustom = true,
                    customUid = "user-custom-1",
                ),
                normalizedText = "kullanici ozel yemegi",
            ),
        )

        repository.ensureCatalogSeeded()

        val allFoodsAfter = dao.getAllFoodsSnapshot()
        val upgraded = allFoodsAfter.firstOrNull { it.id == originalId }
        assertNotNull(upgraded)
        assertEquals("ID must be preserved during v3 in-place upgrade", originalId, upgraded!!.id)
        assertEquals("Favorite flag must survive v3 upgrade", true, upgraded.isFavorite)
        assertEquals("catalogVersion must be upgraded to 3", 3, upgraded.catalogVersion)
        assertTrue("allergenVerified must become true after v3 upgrade", upgraded.allergenVerified)
        assertTrue("FISH_SEAFOOD allergen must be populated", upgraded.allergensCsv.contains("FISH_SEAFOOD"))

        val customAfter = allFoodsAfter.firstOrNull { it.id == customId }
        assertNotNull("Custom food must remain untouched during catalog v3 upgrade", customAfter)
        assertEquals("user-custom-1", customAfter!!.customUid)
        assertEquals(621, dao.countCatalogSeedFoods())
        assertFalse(customAfter.allergenVerified)
    }
}
