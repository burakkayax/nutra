package com.nutra.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nutra.app.core.database.FoodFeedbackEntity
import com.nutra.app.core.database.GoalHistoryEntity
import com.nutra.app.core.database.MealLogEntity
import com.nutra.app.core.database.MetabolismSnapshotEntity
import com.nutra.app.core.database.NutraDatabase
import com.nutra.app.core.database.TurkishFoodSeedCatalog
import com.nutra.app.core.di.AppModule
import com.nutra.app.domain.model.FoodFeedbackStatus
import com.nutra.app.domain.model.GoalChangeSource
import com.nutra.app.domain.model.GoalType
import com.nutra.app.domain.model.MealType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NutraAndroid17InstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun roomDatabase_realSqliteMigrationFromV1ToV5PreservesDataAndValidatesSchema() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "nutra_migration_v1_v5_test.db"
        context.deleteDatabase(dbName)

        try {
            // 1. Create an authentic Room v1 SQLite database on device hardware
            val v1Helper = FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration.builder(context)
                    .name(dbName)
                    .callback(
                        object : SupportSQLiteOpenHelper.Callback(1) {
                            override fun onCreate(db: SupportSQLiteDatabase) {
                                db.execSQL(
                                    """
                                    CREATE TABLE IF NOT EXISTS `food_items` (
                                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                        `name` TEXT NOT NULL,
                                        `subtitle` TEXT NOT NULL,
                                        `normalizedSearchText` TEXT NOT NULL,
                                        `category` TEXT NOT NULL,
                                        `caloriesPer100g` INTEGER NOT NULL,
                                        `proteinPer100g` REAL NOT NULL,
                                        `carbsPer100g` REAL NOT NULL,
                                        `fatPer100g` REAL NOT NULL,
                                        `defaultUnitLabel` TEXT NOT NULL,
                                        `defaultUnitGrams` REAL NOT NULL,
                                        `alternateUnitsJson` TEXT NOT NULL,
                                        `preferredMealTypesCsv` TEXT NOT NULL,
                                        `barcode` TEXT,
                                        `isFavorite` INTEGER NOT NULL,
                                        `isCustom` INTEGER NOT NULL
                                    )
                                    """.trimIndent(),
                                )
                                db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_items_normalizedSearchText` ON `food_items` (`normalizedSearchText`)")
                                db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_items_category` ON `food_items` (`category`)")
                                db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_items_barcode` ON `food_items` (`barcode`)")
                                db.execSQL(
                                    """
                                    CREATE TABLE IF NOT EXISTS `meal_logs` (
                                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                        `dateIso` TEXT NOT NULL,
                                        `mealType` TEXT NOT NULL,
                                        `foodId` INTEGER,
                                        `foodName` TEXT NOT NULL,
                                        `unitLabel` TEXT NOT NULL,
                                        `multiplier` REAL NOT NULL,
                                        `totalGrams` REAL NOT NULL,
                                        `calories` INTEGER NOT NULL,
                                        `proteinGrams` REAL NOT NULL,
                                        `carbsGrams` REAL NOT NULL,
                                        `fatGrams` REAL NOT NULL,
                                        `timestampMillis` INTEGER NOT NULL
                                    )
                                    """.trimIndent(),
                                )
                                db.execSQL("CREATE INDEX IF NOT EXISTS `index_meal_logs_dateIso` ON `meal_logs` (`dateIso`)")
                                db.execSQL("CREATE INDEX IF NOT EXISTS `index_meal_logs_mealType` ON `meal_logs` (`mealType`)")
                                db.execSQL("CREATE INDEX IF NOT EXISTS `index_meal_logs_foodId` ON `meal_logs` (`foodId`)")
                                db.execSQL(
                                    """
                                    CREATE TABLE IF NOT EXISTS `water_logs` (
                                        `dateIso` TEXT NOT NULL,
                                        `waterMl` INTEGER NOT NULL,
                                        `updatedAtMillis` INTEGER NOT NULL,
                                        PRIMARY KEY(`dateIso`)
                                    )
                                    """.trimIndent(),
                                )
                                db.execSQL(
                                    """
                                    CREATE TABLE IF NOT EXISTS `weight_logs` (
                                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                        `dateIso` TEXT NOT NULL,
                                        `weightKg` REAL NOT NULL,
                                        `timestampMillis` INTEGER NOT NULL
                                    )
                                    """.trimIndent(),
                                )
                                db.execSQL("CREATE INDEX IF NOT EXISTS `index_weight_logs_dateIso` ON `weight_logs` (`dateIso`)")

                                // Seed real v1 rows
                                db.execSQL(
                                    """
                                    INSERT INTO `food_items` VALUES (
                                        1, 'Ev Yapımı Yulaf', 'Klasik', 'ev yapimi yulaf klasik', 'BREAKFAST',
                                        120, 6.0, 18.0, 3.0, '1 Kase', 200.0, '[]', 'BREAKFAST', NULL, 1, 1
                                    )
                                    """.trimIndent(),
                                )
                                db.execSQL(
                                    """
                                    INSERT INTO `meal_logs` VALUES (
                                        1, '2026-11-21', 'BREAKFAST', 1, 'Ev Yapımı Yulaf',
                                        '1 Kase', 1.0, 200.0, 240, 12.0, 36.0, 6.0, 1795180000000
                                    )
                                    """.trimIndent(),
                                )
                                db.execSQL("INSERT INTO `water_logs` VALUES ('2026-11-21', 1500, 1795180000000)")
                                db.execSQL("INSERT INTO `weight_logs` VALUES (1, '2026-11-21', 78.2, 1795180000000)")
                            }

                            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                        },
                    )
                    .build(),
            )
            v1Helper.writableDatabase.close()
            v1Helper.close()

            // 2. Open with Room v5 & run all 4 production migrations (validates exact Room schema hash!)
            val migratedDb = Room.databaseBuilder(context, NutraDatabase::class.java, dbName)
                .addMigrations(
                    AppModule.MIGRATION_1_2,
                    AppModule.MIGRATION_2_3,
                    AppModule.MIGRATION_3_4,
                    AppModule.MIGRATION_4_5,
                )
                .build()

            try {
                val dao = migratedDb.nutraDao()
                val foods = dao.getAllFoodsSnapshot()
                assertEquals(1, foods.size)
                assertEquals("Ev Yapımı Yulaf", foods.first().name)
                assertEquals("g", foods.first().baseUnit)

                val logs = dao.getAllMealLogsSnapshot()
                assertEquals(1, logs.size)
                assertEquals(240, logs.first().calories)
                assertEquals("g", logs.first().baseUnit)

                // Verify v5 tables accept writes & queries cleanly
                dao.insertGoalHistory(
                    GoalHistoryEntity(
                        effectiveDateIso = "2026-11-21",
                        calories = 2200,
                        proteinGrams = 160,
                        carbsGrams = 220,
                        fatGrams = 70,
                        goalType = GoalType.LOSE_WEIGHT.name,
                        targetWeightKg = 74f,
                        source = GoalChangeSource.MANUAL.name,
                        timestampMillis = 1000L,
                    ),
                )
                val latestGoal = dao.findLatestGoalHistoryForDate("2026-11-21")
                assertNotNull(latestGoal)
                assertEquals(2200, latestGoal!!.calories)

                dao.upsertFoodFeedback(
                    FoodFeedbackEntity(
                        foodKey = "custom:1:ev yapimi yulaf",
                        foodId = 1L,
                        foodName = "Ev Yapımı Yulaf",
                        status = FoodFeedbackStatus.PINNED.name,
                        acceptedCount = 3,
                        updatedAtMillis = 2000L,
                    ),
                )
                assertEquals(1, dao.getAllFoodFeedbackSnapshot().size)

                dao.upsertMetabolismSnapshots(
                    listOf(
                        MetabolismSnapshotEntity(
                            dateIso = "2026-11-21",
                            estimatedTdeeKcal = 2500,
                            uncertaintyKcal = 170,
                            priorTdeeKcal = 2720,
                            trendWeightKg = 78.2f,
                            confidenceScore = 87,
                            qualityLevel = "HIGH",
                            updatedAtMillis = 3000L,
                        ),
                    ),
                )
                assertEquals(1, dao.observeMetabolismSnapshots().first().size)
            } finally {
                migratedDb.close()
            }
        } finally {
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun inMemoryRoomDatabase_seeds621CatalogFoodsAndVerifiesQueriesAndCoalescing() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, NutraDatabase::class.java).build()
        try {
            val dao = db.nutraDao()
            val entities = TurkishFoodSeedCatalog.toEntities(context)
            assertEquals(TurkishFoodSeedCatalog.EXPECTED_CATALOG_SIZE, entities.size)
            dao.insertFoods(entities)

            assertEquals(621, dao.getFoodCount())
            assertEquals(621, dao.countCatalogSeedFoods())
            val tuna = dao.getCatalogSeedFoods().firstOrNull { it.catalogCode == "TR0001" }
            assertNotNull("Expected TR0001 seeded via AssetManager", tuna)
            assertNotNull(dao.findFoodByBarcode("8690526051222"))

            // Log meals and verify daily totals + metabolism intake aggregation queries
            dao.insertMealLog(
                MealLogEntity(
                    dateIso = "2026-11-21",
                    mealType = MealType.DINNER.name,
                    foodId = tuna!!.id,
                    foodName = tuna.name,
                    unitLabel = "1 Kutu",
                    multiplier = 1f,
                    totalGrams = 104f,
                    calories = 190,
                    proteinGrams = 26f,
                    carbsGrams = 0f,
                    fatGrams = 9f,
                    timestampMillis = 1000L,
                ),
            )
            val totals = dao.observeDailyMealTotalsInRange("2026-11-20", "2026-11-21").first()
            assertEquals(1, totals.size)
            assertEquals(190, totals.first().totalCalories)
            val metabolismIntake = dao.observeDailyMetabolismIntakeInRange("2026-11-20", "2026-11-21").first()
            assertEquals(1, metabolismIntake.size)
            assertEquals(1, metabolismIntake.first().mealTypeCount)
            assertEquals(1, metabolismIntake.first().itemCount)

            // Same-day GoalHistory update coalescing
            val id = dao.insertGoalHistory(
                GoalHistoryEntity(
                    effectiveDateIso = "2026-11-21",
                    calories = 2250,
                    proteinGrams = 165,
                    carbsGrams = 225,
                    fatGrams = 70,
                    goalType = GoalType.LOSE_WEIGHT.name,
                    targetWeightKg = 74f,
                    source = GoalChangeSource.MANUAL.name,
                    timestampMillis = 1000L,
                ),
            )
            val existing = dao.findLatestGoalHistoryForDate("2026-11-21")!!
            assertEquals(id, existing.id)
            dao.updateGoalHistory(existing.copy(calories = 2150, timestampMillis = 2000L))
            val allGoals = dao.getAllGoalHistorySnapshot()
            assertEquals(1, allGoals.size)
            assertEquals(2150, allGoals.first().calories)
        } finally {
            db.close()
        }
    }

    @Test
    fun mainActivityEndToEndNavigation_exercisesAllTabsAndOverlaysWithoutCrashOnAndroid17() {
        // Wait for either Onboarding or Dashboard hydration
        composeRule.waitUntil(timeoutMillis = 10_000L) {
            runCatching {
                composeRule.onAllNodesWithText("Bugün").fetchSemanticsNodes().isNotEmpty() ||
                    composeRule.onAllNodesWithText("Atla").fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }

        // If fresh install started on Onboarding, skip into Dashboard cleanly
        if (composeRule.onAllNodesWithText("Atla").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText("Atla").performClick()
            composeRule.waitForIdle()
            if (composeRule.onAllNodesWithText("Hemen başla").fetchSemanticsNodes().isNotEmpty()) {
                composeRule.onNodeWithText("Hemen başla").performClick()
            }
            composeRule.waitUntil(timeoutMillis = 10_000L) {
                composeRule.onAllNodesWithText("Bugün").fetchSemanticsNodes().isNotEmpty()
            }
        }

        // 1. Open Nutra Intelligence Studio from Dashboard if card is visible
        if (composeRule.onAllNodesWithText("Önerileri gör").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText("Önerileri gör").performScrollTo().performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Nutra Intelligence").assertIsDisplayed()
            composeRule.onNodeWithText("Öğün Dengele").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Kendi öğününü hedefe göre dengele").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("Geri").performClick()
            composeRule.waitForIdle()
        }

        // 2. Navigate to Analytics (İlerleme), switch ranges, and open Metabolism Detail
        composeRule.onNodeWithText("İlerleme").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("14 Gün").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("30 Gün").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("7 Gün").performClick()
        composeRule.waitForIdle()

        if (composeRule.onAllNodesWithText("Adaptif Metabolizma").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText("Adaptif Metabolizma").performScrollTo().performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText("TDEE, kilo projeksiyonu ve plato analizi").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("Geri").performClick()
            composeRule.waitForIdle()
        }

        // 3. Navigate to Profile, open Daily Goals & Dietary Preferences, then return to Dashboard
        composeRule.onNodeWithText("Profil").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Günlük hedefler").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Kalori ve makrolar").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Geri").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Beslenme tercihleri").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Beslenme düzeni").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Geri").performClick()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("Bugün").onFirst().performClick()
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodesWithText("Günlük kalori").fetchSemanticsNodes().isNotEmpty())
    }
}
