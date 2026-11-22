package com.nutra.app.core.di

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nutra.app.core.database.NutraDao
import com.nutra.app.core.database.NutraDatabase
import com.nutra.app.core.database.TurkishFoodSeedCatalog
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_meal_logs_timestampMillis` ON `meal_logs` (`timestampMillis`)")
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP INDEX IF EXISTS `index_meal_logs_dateIso`")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_meal_logs_dateIso_timestampMillis` ON `meal_logs` (`dateIso`, `timestampMillis`)",
            )
            db.execSQL("DROP INDEX IF EXISTS `index_weight_logs_dateIso`")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_weight_logs_dateIso_timestampMillis` ON `weight_logs` (`dateIso`, `timestampMillis`)",
            )
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `food_items` ADD COLUMN `catalogCode` TEXT")
            db.execSQL("ALTER TABLE `food_items` ADD COLUMN `brand` TEXT")
            db.execSQL("ALTER TABLE `food_items` ADD COLUMN `baseUnit` TEXT NOT NULL DEFAULT 'g'")
            db.execSQL("ALTER TABLE `food_items` ADD COLUMN `saturatedFatPer100g` REAL")
            db.execSQL("ALTER TABLE `food_items` ADD COLUMN `sugarPer100g` REAL")
            db.execSQL("ALTER TABLE `food_items` ADD COLUMN `fiberPer100g` REAL")
            db.execSQL("ALTER TABLE `food_items` ADD COLUMN `saltPer100g` REAL")
            db.execSQL("ALTER TABLE `food_items` ADD COLUMN `sodiumMgPer100g` REAL")
            db.execSQL("ALTER TABLE `food_items` ADD COLUMN `calciumMgPer100g` REAL")
            db.execSQL("ALTER TABLE `food_items` ADD COLUMN `potassiumMgPer100g` REAL")
            db.execSQL("ALTER TABLE `food_items` ADD COLUMN `ironMgPer100g` REAL")
            db.execSQL("ALTER TABLE `food_items` ADD COLUMN `vitaminCMgPer100g` REAL")
            db.execSQL("ALTER TABLE `food_items` ADD COLUMN `preparationState` TEXT")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_items_catalogCode` ON `food_items` (`catalogCode`)")
            db.execSQL("ALTER TABLE `meal_logs` ADD COLUMN `baseUnit` TEXT NOT NULL DEFAULT 'g'")
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `goal_history` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `effectiveDateIso` TEXT NOT NULL,
                    `calories` INTEGER NOT NULL,
                    `proteinGrams` INTEGER NOT NULL,
                    `carbsGrams` INTEGER NOT NULL,
                    `fatGrams` INTEGER NOT NULL,
                    `goalType` TEXT NOT NULL,
                    `targetWeightKg` REAL NOT NULL,
                    `source` TEXT NOT NULL,
                    `timestampMillis` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_goal_history_effectiveDateIso_timestampMillis` ON `goal_history` (`effectiveDateIso`, `timestampMillis`)",
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `food_feedback` (
                    `foodKey` TEXT NOT NULL,
                    `foodId` INTEGER,
                    `foodName` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `acceptedCount` INTEGER NOT NULL,
                    `dismissedCount` INTEGER NOT NULL,
                    `lastSuggestedMillis` INTEGER NOT NULL,
                    `lastAcceptedMillis` INTEGER NOT NULL,
                    `updatedAtMillis` INTEGER NOT NULL,
                    PRIMARY KEY(`foodKey`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_food_feedback_status` ON `food_feedback` (`status`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_food_feedback_foodId` ON `food_feedback` (`foodId`)",
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `metabolism_snapshots` (
                    `dateIso` TEXT NOT NULL,
                    `estimatedTdeeKcal` INTEGER NOT NULL,
                    `uncertaintyKcal` INTEGER NOT NULL,
                    `priorTdeeKcal` INTEGER NOT NULL,
                    `trendWeightKg` REAL NOT NULL,
                    `confidenceScore` INTEGER NOT NULL,
                    `qualityLevel` TEXT NOT NULL,
                    `updatedAtMillis` INTEGER NOT NULL,
                    PRIMARY KEY(`dateIso`)
                )
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `food_items` ADD COLUMN `allergensCsv` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `food_items` ADD COLUMN `dietFlagsMask` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `food_items` ADD COLUMN `allergenVerified` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `food_items` ADD COLUMN `customUid` TEXT")
            db.execSQL("ALTER TABLE `food_items` ADD COLUMN `catalogVersion` INTEGER NOT NULL DEFAULT 2")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_items_customUid` ON `food_items` (`customUid`)")
            db.execSQL("UPDATE `food_items` SET `customUid` = 'CUSTOM_' || `id` WHERE `isCustom` = 1 AND `customUid` IS NULL")
            db.execSQL("ALTER TABLE `goal_history` ADD COLUMN `waterGoalMl` INTEGER NOT NULL DEFAULT 2500")
        }
    }

    @Provides
    @Singleton
    fun provideNutraDatabase(
        @ApplicationContext context: Context,
    ): NutraDatabase = Room.databaseBuilder(
        context,
        NutraDatabase::class.java,
        "nutra_database.db",
    )
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
        .addCallback(
            object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    val entities = TurkishFoodSeedCatalog.toEntities(context)
                    db.beginTransactionNonExclusive()
                    try {
                        for (entity in entities) {
                            db.insert("food_items", SQLiteDatabase.CONFLICT_REPLACE, entity.toContentValues())
                        }
                        db.setTransactionSuccessful()
                    } finally {
                        db.endTransaction()
                    }
                }
            },
        )
        .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
        .build()

    @Provides
    @Singleton
    fun provideNutraDao(database: NutraDatabase): NutraDao = database.nutraDao()
}
