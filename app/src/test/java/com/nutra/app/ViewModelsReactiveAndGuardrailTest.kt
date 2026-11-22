package com.nutra.app

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.nutra.app.core.database.NutraDatabase
import com.nutra.app.core.datastore.UserPreferencesDataStore
import com.nutra.app.core.notification.WaterReminderScheduler
import com.nutra.app.domain.intelligence.FoodCulinaryClassifier
import com.nutra.app.domain.intelligence.FoodEligibilityPolicy
import com.nutra.app.domain.intelligence.IntelligenceReport
import com.nutra.app.domain.intelligence.MealRecommendationEngine
import com.nutra.app.domain.intelligence.PortionOptimizer
import com.nutra.app.domain.intelligence.UserEatingProfile
import com.nutra.app.domain.intelligence.UserEatingProfileLearner
import com.nutra.app.domain.metabolism.AdaptiveMetabolismEngine
import com.nutra.app.domain.metabolism.DailyIntakeObservation
import com.nutra.app.domain.metabolism.MetabolismAnalysisResult
import com.nutra.app.domain.model.ActivityLevel
import com.nutra.app.domain.model.BiologicalSex
import com.nutra.app.domain.model.DailyNutritionSummary
import com.nutra.app.domain.model.DietStyle
import com.nutra.app.domain.model.DietaryProfile
import com.nutra.app.domain.model.FoodFeedbackRecord
import com.nutra.app.domain.model.FoodItem
import com.nutra.app.domain.model.GoalHistoryEntry
import com.nutra.app.domain.model.GoalType
import com.nutra.app.domain.model.MacroGoals
import com.nutra.app.domain.model.MealLogItem
import com.nutra.app.domain.model.MealType
import com.nutra.app.domain.model.ThemePreference
import com.nutra.app.domain.model.UserProfile
import com.nutra.app.domain.model.WeightEntry
import com.nutra.app.domain.repository.NutritionRepository
import com.nutra.app.domain.usecase.ContextualMealRankerAndComboEngine
import com.nutra.app.domain.usecase.NutritionCalculatorUseCase
import com.nutra.app.domain.usecase.WeightTrendCalculator
import com.nutra.app.feature.analytics.AnalyticsViewModel
import com.nutra.app.feature.dashboard.DashboardViewModel
import com.nutra.app.feature.intelligence.IntelligenceViewModel
import com.nutra.app.feature.onboarding.OnboardingViewModel
import com.nutra.app.feature.profile.ProfileViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37], application = Application::class)
class ViewModelsReactiveAndGuardrailTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var app: Application
    private lateinit var database: NutraDatabase
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var preferences: UserPreferencesDataStore
    private lateinit var repository: NutritionRepository
    private val calculator = NutritionCalculatorUseCase()

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        app = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(app, NutraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val prefsFile = File(tempFolder.newFolder("vm_ds_${UUID.randomUUID()}"), "nutra_vm.preferences_pb")
        val backing = PreferenceDataStoreFactory.create(scope = dataStoreScope) { prefsFile }
        preferences = UserPreferencesDataStore(app, backing)
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

    private suspend fun waitUntil(timeoutMs: Long = 5_000L, predicate: suspend () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            delay(25L)
        }
        assertTrue("Timed out after ${timeoutMs}ms waiting for expected state", predicate())
    }

    @Test
    fun onboardingSkip_persistsOnboardingWithoutWritingPhantomWeightLog() = runBlocking {
        val vm = OnboardingViewModel(
            savedStateHandle = SavedStateHandle(),
            preferencesDataStore = preferences,
            nutritionRepository = repository,
            calculatorUseCase = calculator,
        )
        var finished = false
        vm.skipOnboarding { finished = true }

        waitUntil { finished && preferences.getSnapshot().onboardingCompleted }
        assertTrue(preferences.getSnapshot().onboardingCompleted)
        assertEquals(
            "Skipping onboarding must NEVER insert a phantom initial weight entry",
            0,
            database.nutraDao().getAllWeightLogsSnapshot().size,
        )
    }

    @Test
    fun onboardingAdolescent16_locksCalorieGoalsToObservationModeWithZeroDeficit() = runBlocking {
        val vm = OnboardingViewModel(
            savedStateHandle = SavedStateHandle(),
            preferencesDataStore = preferences,
            nutritionRepository = repository,
            calculatorUseCase = calculator,
        )
        vm.selectGoalType(GoalType.LOSE_WEIGHT)
        vm.setAge(16)

        val state = vm.uiState.value
        assertTrue("Age 16 must activate adolescent observation mode", state.isAdolescentObservationMode)

        val expectedMaintain = calculator.calculateGoals(
            sex = state.sex,
            age = 16,
            heightCm = state.heightCm,
            weightKg = state.currentWeightKg,
            activityLevel = state.activityLevel,
            goalType = GoalType.MAINTAIN,
        )
        assertEquals(
            "Adolescent LOSE_WEIGHT must match MAINTAIN calories (zero automatic deficit)",
            expectedMaintain.calories,
            state.calculatedGoals.calories,
        )
    }

    @Test
    fun dashboardCustomFoodCreator_rejectsPhysicallyImpossibleMacrosAndAcceptsValidLiquidFood() = runBlocking {
        preferences.saveOnboardingProfile(UserProfile(onboardingCompleted = true))
        val vm = DashboardViewModel(
            savedStateHandle = SavedStateHandle(),
            nutritionRepository = repository,
            preferencesDataStore = preferences,
            rankerAndComboEngine = ContextualMealRankerAndComboEngine(),
            eligibilityPolicy = FoodEligibilityPolicy(),
        )
        vm.openAddFoodModal(MealType.SNACK)

        // 1. Physically impossible calories (> 950 kcal / 100g)
        vm.createAndLogCustomFood(
            name = "İmkansız Besin",
            unitLabel = "1 Porsiyon",
            unitGrams = 100f,
            caloriesPerServing = 1500,
            proteinPerServing = 10f,
            carbsPerServing = 10f,
            fatPerServing = 10f,
        )
        delay(100L)
        assertEquals("Impossible kcal custom food must be rejected", 0, database.nutraDao().getAllFoodsSnapshot().count { it.isCustom })

        // 2. Physically impossible macro sum (> 105g per 100g)
        vm.createAndLogCustomFood(
            name = "Aşırı Makro",
            unitLabel = "1 Porsiyon",
            unitGrams = 100f,
            caloriesPerServing = 500,
            proteinPerServing = 50f,
            carbsPerServing = 50f,
            fatPerServing = 20f,
        )
        delay(100L)
        assertEquals("Macro sum > 105g/100g must be rejected", 0, database.nutraDao().getAllFoodsSnapshot().count { it.isCustom })

        // 3. Valid liquid custom food (baseUnit = "ml") must persist with allergenVerified=false & non-blank customUid
        vm.createAndLogCustomFood(
            name = "Ev Yapımı Smoothie",
            unitLabel = "1 Bardak",
            unitGrams = 250f,
            caloriesPerServing = 180,
            proteinPerServing = 8f,
            carbsPerServing = 28f,
            fatPerServing = 4f,
            baseUnit = "ml",
        )
        waitUntil { database.nutraDao().getAllFoodsSnapshot().any { it.isCustom && it.name == "Ev Yapımı Smoothie" } }
        val saved = database.nutraDao().getAllFoodsSnapshot().first { it.isCustom }
        assertEquals("ml", saved.baseUnit)
        assertFalse("User custom food must start with allergenVerified=false", saved.allergenVerified)
        assertTrue("Custom food must receive a non-blank customUid", !saved.customUid.isNullOrBlank())
    }

    @Test
    fun intelligenceViewModel_doesNotRecomputeRecommendationsOnWaterOrThemeChanges() = runBlocking {
        preferences.saveOnboardingProfile(UserProfile(onboardingCompleted = true))
        repository.ensureCatalogSeeded()

        val invokeCounter = AtomicInteger(0)
        val countingEngine = object : MealRecommendationEngine(
            FoodCulinaryClassifier(),
            UserEatingProfileLearner(),
            PortionOptimizer(),
        ) {
            override fun generateRecommendations(
                profile: UserProfile,
                dailySummary: DailyNutritionSummary,
                mealType: MealType,
                catalogFoods: List<FoodItem>,
                historicalLogs: List<MealLogItem>,
                feedbackRecords: List<FoodFeedbackRecord>,
                today: LocalDate,
                variationSeed: Int,
                maxResults: Int,
                precomputedEatingProfile: UserEatingProfile?,
            ): IntelligenceReport {
                invokeCounter.incrementAndGet()
                return super.generateRecommendations(
                    profile, dailySummary, mealType, catalogFoods, historicalLogs,
                    feedbackRecords, today, variationSeed, maxResults, precomputedEatingProfile,
                )
            }
        }

        val vm = IntelligenceViewModel(
            nutritionRepository = repository,
            preferencesDataStore = preferences,
            recommendationEngine = countingEngine,
        )

        vm.uiState.test {
            // Wait for initial loaded state
            var loaded = awaitItem()
            while (loaded.isLoading) {
                loaded = awaitItem()
            }
            // Allow any initial dashboard-card computation burst to settle
            delay(250L)
            val countAfterInit = invokeCounter.get()
            assertTrue("Expected initial recommendation computation, got $countAfterInit", countAfterInit >= 1)

            // Trigger unrelated water + appearance updates!
            repository.addWaterMl(LocalDate.now().toString(), 250)
            preferences.updateAppearance(ThemePreference.DARK, useDynamicColor = true)
            delay(350L)

            assertEquals(
                "Water log and theme updates MUST NOT trigger MealRecommendationEngine recomputation",
                countAfterInit,
                invokeCounter.get(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun analyticsViewModel_doesNotReRunKalmanMetabolismOnThemeOrDietaryChanges() = runBlocking {
        preferences.saveOnboardingProfile(UserProfile(onboardingCompleted = true))
        val analyzeCounter = AtomicInteger(0)
        val countingEngine = object : AdaptiveMetabolismEngine(calculator) {
            override fun analyze(
                profile: UserProfile,
                intakeObservations: List<DailyIntakeObservation>,
                weightEntries: List<WeightEntry>,
                goalHistory: List<GoalHistoryEntry>,
                today: LocalDate,
            ): MetabolismAnalysisResult {
                analyzeCounter.incrementAndGet()
                return super.analyze(profile, intakeObservations, weightEntries, goalHistory, today)
            }
        }

        val vm = AnalyticsViewModel(
            savedStateHandle = SavedStateHandle(),
            nutritionRepository = repository,
            preferencesDataStore = preferences,
            weightTrendCalculator = WeightTrendCalculator(),
            adaptiveMetabolismEngine = countingEngine,
        )

        vm.metabolismAnalysis.test {
            var current = awaitItem()
            while (current == null) {
                current = awaitItem()
            }
            delay(250L)
            val countAfterInit = analyzeCounter.get()
            assertTrue("Expected initial Kalman analysis, got $countAfterInit", countAfterInit >= 1)

            // Theme change & dietary preference edit should NOT trigger metabolism re-analysis
            preferences.updateAppearance(ThemePreference.DARK, useDynamicColor = false)
            preferences.updateDietaryProfile(DietaryProfile(dietStyle = DietStyle.MEDITERRANEAN))
            delay(350L)

            assertEquals(
                "Theme & diet style edits MUST NOT trigger AdaptiveMetabolismEngine recomputation",
                countAfterInit,
                analyzeCounter.get(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun profileViewModel_waterOnlyEditPreservesGoalHistoryAndMifflinUndoRestoresPriorGoals() = runBlocking {
        val originalGoals = MacroGoals(calories = 2350, proteinGrams = 165, carbsGrams = 240, fatGrams = 72, waterMl = 2500)
        preferences.saveOnboardingProfile(
            UserProfile(
                sex = BiologicalSex.MALE,
                age = 29,
                heightCm = 178,
                currentWeightKg = 78f,
                targetWeightKg = 72f,
                activityLevel = ActivityLevel.MODERATE,
                goalType = GoalType.LOSE_WEIGHT,
                goals = originalGoals,
                onboardingCompleted = true,
            ),
        )
        val scheduler = object : WaterReminderScheduler(app) {
            override fun sync(settings: com.nutra.app.domain.model.WaterReminderSettings, minLeadSeconds: Long) = Unit
            override fun cancel() = Unit
        }
        val vm = ProfileViewModel(
            preferencesDataStore = preferences,
            nutritionRepository = repository,
            calculatorUseCase = calculator,
            waterReminderScheduler = scheduler,
        )
        waitUntil { !vm.uiState.first().isLoading }

        // 1. Water-only update must persist new waterMl without creating a spurious calorie-change history row
        val beforeHistoryCount = database.nutraDao().getAllGoalHistorySnapshot().size
        vm.setWaterGoal(3100)
        waitUntil { preferences.getSnapshot().goals.waterMl == 3100 }
        assertEquals(
            "Water-only edit must not create a calorie goal_history row when calories did not change",
            beforeHistoryCount,
            database.nutraDao().getAllGoalHistorySnapshot().size,
        )

        // 2. Recalculate with Mifflin-St Jeor + Undo restores previous goals cleanly
        vm.recalculateWithMifflinStJeor()
        waitUntil { preferences.getSnapshot().goals.calories != originalGoals.calories }
        val mifflinCalories = preferences.getSnapshot().goals.calories
        assertTrue("Mifflin recalculation should update calorie target", mifflinCalories != originalGoals.calories)

        vm.undoRecalculateGoals()
        waitUntil { preferences.getSnapshot().goals.calories == originalGoals.calories }
        assertEquals(originalGoals.calories, preferences.getSnapshot().goals.calories)
    }

    @Test
    fun analyticsViewModel_allWeightEntriesExposesFullNewestFirstHistoryAndSupportsEditDelete() = runBlocking {
        preferences.saveOnboardingProfile(UserProfile(onboardingCompleted = true))
        val today = LocalDate.now()
        // Insert 18 entries (> previous 14 inline cap) to confirm WeightHistoryScreen gets full history
        for (daysAgo in 17 downTo 0) {
            val dateIso = today.minusDays(daysAgo.toLong()).toString()
            repository.logWeight(dateIso, 80f - (17 - daysAgo) * 0.1f)
        }

        val vm = AnalyticsViewModel(
            savedStateHandle = SavedStateHandle(),
            nutritionRepository = repository,
            preferencesDataStore = preferences,
            weightTrendCalculator = WeightTrendCalculator(),
            adaptiveMetabolismEngine = AdaptiveMetabolismEngine(calculator),
        )

        waitUntil { vm.allWeightEntries.first().size == 18 }
        val entries = vm.allWeightEntries.first()
        assertEquals(18, entries.size)
        assertEquals("Newest entry should come first", today.toString(), entries.first().dateIso)
        assertEquals("Oldest entry should come last", today.minusDays(17).toString(), entries.last().dateIso)

        // Edit the newest entry
        val newest = entries.first()
        vm.updateWeightEntry(newest, 77.4f)
        waitUntil { vm.allWeightEntries.first().firstOrNull { it.id == newest.id }?.weightKg == 77.4f }

        // Delete the newest entry
        vm.deleteWeightEntry(newest)
        waitUntil { vm.allWeightEntries.first().size == 17 }
        assertTrue(vm.allWeightEntries.first().none { it.id == newest.id })
    }
}
