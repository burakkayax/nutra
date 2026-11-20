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
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
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

    @Provides
    @Singleton
    fun provideNutraDatabase(
        @ApplicationContext context: Context,
    ): NutraDatabase = Room.databaseBuilder(
        context,
        NutraDatabase::class.java,
        "nutra_database.db",
    )
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
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

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build()
}
