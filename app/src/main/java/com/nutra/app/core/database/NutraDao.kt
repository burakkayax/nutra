package com.nutra.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class DailyMealTotalsTuple(
    val dateIso: String,
    val totalCalories: Int,
    val totalProtein: Double,
    val totalCarbs: Double,
    val totalFat: Double,
)

data class DailyMetabolismIntakeTuple(
    val dateIso: String,
    val totalCalories: Int,
    val totalProtein: Double,
    val totalCarbs: Double,
    val totalFat: Double,
    val mealTypeCount: Int,
    val itemCount: Int,
)

@Dao
interface NutraDao {

    // --- Food Catalog ---
    @Query("SELECT * FROM food_items ORDER BY isFavorite DESC, name ASC")
    fun observeAllFoods(): Flow<List<FoodItemEntity>>

    @Query("SELECT * FROM food_items")
    suspend fun getAllFoodsSnapshot(): List<FoodItemEntity>

    @Query("SELECT COUNT(*) FROM food_items")
    suspend fun getFoodCount(): Int

    @Query("SELECT COUNT(*) FROM food_items WHERE isCustom = 0 AND catalogCode IS NOT NULL")
    suspend fun countCatalogSeedFoods(): Int

    @Query("SELECT * FROM food_items WHERE isCustom = 0 AND catalogCode IS NULL")
    suspend fun getLegacySeedFoods(): List<FoodItemEntity>

    @Query("SELECT * FROM food_items WHERE isCustom = 0 AND catalogCode IS NOT NULL")
    suspend fun getCatalogSeedFoods(): List<FoodItemEntity>

    @Query("DELETE FROM food_items WHERE isCustom = 0 AND catalogCode IS NULL")
    suspend fun deleteLegacySeedFoods()

    @Query("UPDATE meal_logs SET foodId = :newFoodId WHERE foodId = :oldFoodId")
    suspend fun remapMealLogFoodId(oldFoodId: Long, newFoodId: Long?)

    @Query("SELECT * FROM food_items WHERE id = :foodId LIMIT 1")
    suspend fun findFoodById(foodId: Long): FoodItemEntity?

    @Query("SELECT * FROM food_items WHERE barcode = :barcode LIMIT 1")
    suspend fun findFoodByBarcode(barcode: String): FoodItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFoods(foods: List<FoodItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFood(food: FoodItemEntity): Long

    @Update
    suspend fun updateFood(food: FoodItemEntity)

    @Query("UPDATE food_items SET isFavorite = :isFavorite WHERE id = :foodId")
    suspend fun updateFavorite(foodId: Long, isFavorite: Boolean)

    @Query("UPDATE food_items SET isFavorite = 0 WHERE isCustom = 0")
    suspend fun clearSeedFavorites()

    // --- Meal Logs ---
    @Query("SELECT * FROM meal_logs WHERE dateIso = :dateIso ORDER BY timestampMillis ASC")
    fun observeMealLogsForDate(dateIso: String): Flow<List<MealLogEntity>>

    @Query("SELECT * FROM meal_logs WHERE dateIso BETWEEN :startDateIso AND :endDateIso ORDER BY dateIso ASC, timestampMillis ASC")
    fun observeMealLogsInRange(startDateIso: String, endDateIso: String): Flow<List<MealLogEntity>>

    @Query(
        """
        SELECT dateIso,
               COALESCE(SUM(calories), 0) AS totalCalories,
               COALESCE(SUM(proteinGrams), 0.0) AS totalProtein,
               COALESCE(SUM(carbsGrams), 0.0) AS totalCarbs,
               COALESCE(SUM(fatGrams), 0.0) AS totalFat
        FROM meal_logs
        WHERE dateIso BETWEEN :startDateIso AND :endDateIso
        GROUP BY dateIso
        """,
    )
    fun observeDailyMealTotalsInRange(
        startDateIso: String,
        endDateIso: String,
    ): Flow<List<DailyMealTotalsTuple>>

    @Query("SELECT * FROM meal_logs ORDER BY timestampMillis DESC LIMIT 600")
    fun observeRecentHistoricalMealLogs(): Flow<List<MealLogEntity>>

    @Query("SELECT * FROM meal_logs WHERE dateIso >= :startDateIso ORDER BY dateIso ASC, timestampMillis ASC")
    fun observeMealLogsSince(startDateIso: String): Flow<List<MealLogEntity>>

    @Query(
        """
        SELECT dateIso,
               COALESCE(SUM(calories), 0) AS totalCalories,
               COALESCE(SUM(proteinGrams), 0.0) AS totalProtein,
               COALESCE(SUM(carbsGrams), 0.0) AS totalCarbs,
               COALESCE(SUM(fatGrams), 0.0) AS totalFat,
               COUNT(DISTINCT mealType) AS mealTypeCount,
               COUNT(*) AS itemCount
        FROM meal_logs
        WHERE dateIso BETWEEN :startDateIso AND :endDateIso
        GROUP BY dateIso
        ORDER BY dateIso ASC
        """,
    )
    fun observeDailyMetabolismIntakeInRange(
        startDateIso: String,
        endDateIso: String,
    ): Flow<List<DailyMetabolismIntakeTuple>>

    @Query("SELECT * FROM meal_logs ORDER BY timestampMillis ASC")
    suspend fun getAllMealLogsSnapshot(): List<MealLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMealLog(log: MealLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMealLogs(logs: List<MealLogEntity>)

    @Update
    suspend fun updateMealLog(log: MealLogEntity)

    @Query("DELETE FROM meal_logs WHERE id = :logId")
    suspend fun deleteMealLogById(logId: Long)

    @Query("DELETE FROM meal_logs")
    suspend fun deleteAllMealLogs()

    // --- Water Logs ---
    @Query("SELECT * FROM water_logs WHERE dateIso = :dateIso LIMIT 1")
    fun observeWaterForDate(dateIso: String): Flow<WaterLogEntity?>

    @Query("SELECT * FROM water_logs WHERE dateIso = :dateIso LIMIT 1")
    suspend fun getWaterForDate(dateIso: String): WaterLogEntity?

    @Query("SELECT * FROM water_logs WHERE dateIso BETWEEN :startDateIso AND :endDateIso")
    fun observeWaterInRange(startDateIso: String, endDateIso: String): Flow<List<WaterLogEntity>>

    @Query("SELECT * FROM water_logs")
    suspend fun getAllWaterLogsSnapshot(): List<WaterLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWaterLog(waterLog: WaterLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWaterLogs(waterLogs: List<WaterLogEntity>)

    @Query("DELETE FROM water_logs")
    suspend fun deleteAllWaterLogs()

    // --- Weight Logs ---
    @Query("SELECT * FROM weight_logs ORDER BY dateIso ASC, timestampMillis ASC")
    fun observeAllWeightLogs(): Flow<List<WeightLogEntity>>

    @Query("SELECT * FROM weight_logs ORDER BY dateIso ASC, timestampMillis ASC")
    suspend fun getAllWeightLogsSnapshot(): List<WeightLogEntity>

    @Query("SELECT * FROM weight_logs WHERE dateIso = :dateIso ORDER BY timestampMillis DESC, id DESC LIMIT 1")
    suspend fun findLatestWeightLogForDate(dateIso: String): WeightLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeightLog(weightLog: WeightLogEntity): Long

    @Update
    suspend fun updateWeightLog(weightLog: WeightLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeightLogs(weightLogs: List<WeightLogEntity>)

    @Query("DELETE FROM weight_logs")
    suspend fun deleteAllWeightLogs()

    // --- Goal History ---
    @Query("SELECT * FROM goal_history ORDER BY effectiveDateIso ASC, timestampMillis ASC")
    fun observeGoalHistory(): Flow<List<GoalHistoryEntity>>

    @Query("SELECT * FROM goal_history ORDER BY effectiveDateIso ASC, timestampMillis ASC")
    suspend fun getAllGoalHistorySnapshot(): List<GoalHistoryEntity>

    @Query("SELECT COUNT(*) FROM goal_history")
    suspend fun countGoalHistory(): Int

    @Query("SELECT * FROM goal_history WHERE effectiveDateIso = :effectiveDateIso ORDER BY timestampMillis DESC, id DESC LIMIT 1")
    suspend fun findLatestGoalHistoryForDate(effectiveDateIso: String): GoalHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoalHistory(entry: GoalHistoryEntity): Long

    @Update
    suspend fun updateGoalHistory(entry: GoalHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoalHistoryEntries(entries: List<GoalHistoryEntity>)

    @Query("DELETE FROM goal_history")
    suspend fun deleteAllGoalHistory()

    // --- Food Feedback ---
    @Query("SELECT * FROM food_feedback")
    fun observeAllFoodFeedback(): Flow<List<FoodFeedbackEntity>>

    @Query("SELECT * FROM food_feedback")
    suspend fun getAllFoodFeedbackSnapshot(): List<FoodFeedbackEntity>

    @Query("SELECT * FROM food_feedback WHERE foodKey = :foodKey LIMIT 1")
    suspend fun findFoodFeedbackByKey(foodKey: String): FoodFeedbackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFoodFeedback(entity: FoodFeedbackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFoodFeedbackList(entities: List<FoodFeedbackEntity>)

    @Query("DELETE FROM food_feedback WHERE foodKey = :foodKey")
    suspend fun deleteFoodFeedbackByKey(foodKey: String)

    @Query("DELETE FROM food_feedback")
    suspend fun deleteAllFoodFeedback()

    // --- Metabolism Snapshots ---
    @Query("SELECT * FROM metabolism_snapshots ORDER BY dateIso ASC")
    fun observeMetabolismSnapshots(): Flow<List<MetabolismSnapshotEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMetabolismSnapshots(snapshots: List<MetabolismSnapshotEntity>)

    @Query("DELETE FROM metabolism_snapshots")
    suspend fun deleteAllMetabolismSnapshots()
}
