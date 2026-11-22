package com.nutra.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        FoodItemEntity::class,
        MealLogEntity::class,
        WaterLogEntity::class,
        WeightLogEntity::class,
        GoalHistoryEntity::class,
        FoodFeedbackEntity::class,
        MetabolismSnapshotEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class NutraDatabase : RoomDatabase() {
    abstract fun nutraDao(): NutraDao
}
