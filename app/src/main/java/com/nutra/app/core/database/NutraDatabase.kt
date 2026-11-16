package com.nutra.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        FoodItemEntity::class,
        MealLogEntity::class,
        WaterLogEntity::class,
        WeightLogEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class NutraDatabase : RoomDatabase() {
    abstract fun nutraDao(): NutraDao
}
