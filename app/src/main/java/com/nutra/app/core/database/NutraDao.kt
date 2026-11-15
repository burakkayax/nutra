package com.nutra.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NutraDao {

    // --- Food Catalog ---
    @Query("SELECT * FROM food_items ORDER BY isFavorite DESC, name ASC")
    fun observeAllFoods(): Flow<List<FoodItemEntity>>

    @Query("SELECT COUNT(*) FROM food_items")
    suspend fun getFoodCount(): Int

    @Query("SELECT * FROM food_items WHERE barcode = :barcode LIMIT 1")
    suspend fun findFoodByBarcode(barcode: String): FoodItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFoods(foods: List<FoodItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFood(food: FoodItemEntity): Long

    @Query("UPDATE food_items SET isFavorite = :isFavorite WHERE id = :foodId")
    suspend fun updateFavorite(foodId: Long, isFavorite: Boolean)

    // --- Meal Logs ---
    @Query("SELECT * FROM meal_logs WHERE dateIso = :dateIso ORDER BY timestampMillis ASC")
    fun observeMealLogsForDate(dateIso: String): Flow<List<MealLogEntity>>

    @Query("SELECT * FROM meal_logs WHERE dateIso BETWEEN :startDateIso AND :endDateIso ORDER BY dateIso ASC, timestampMillis ASC")
    fun observeMealLogsInRange(startDateIso: String, endDateIso: String): Flow<List<MealLogEntity>>

    @Query("SELECT * FROM meal_logs ORDER BY timestampMillis DESC LIMIT 600")
    fun observeRecentHistoricalMealLogs(): Flow<List<MealLogEntity>>

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

    // --- Water Logs ---
    @Query("SELECT * FROM water_logs WHERE dateIso = :dateIso LIMIT 1")
    fun observeWaterForDate(dateIso: String): Flow<WaterLogEntity?>

    @Query("SELECT * FROM water_logs WHERE dateIso BETWEEN :startDateIso AND :endDateIso")
    fun observeWaterInRange(startDateIso: String, endDateIso: String): Flow<List<WaterLogEntity>>

    @Query("SELECT * FROM water_logs")
    suspend fun getAllWaterLogsSnapshot(): List<WaterLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWaterLog(waterLog: WaterLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWaterLogs(waterLogs: List<WaterLogEntity>)

    // --- Weight Logs ---
    @Query("SELECT * FROM weight_logs ORDER BY dateIso ASC, timestampMillis ASC")
    fun observeAllWeightLogs(): Flow<List<WeightLogEntity>>

    @Query("SELECT * FROM weight_logs ORDER BY dateIso ASC, timestampMillis ASC")
    suspend fun getAllWeightLogsSnapshot(): List<WeightLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeightLog(weightLog: WeightLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeightLogs(weightLogs: List<WeightLogEntity>)

    @Query("DELETE FROM weight_logs WHERE id = :id")
    suspend fun deleteWeightLogById(id: Long)
}
