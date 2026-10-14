package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.DetoxPlan
import kotlinx.coroutines.flow.Flow

@Dao
interface DetoxPlanDao {
    @Query("SELECT * FROM detox_plans ORDER BY startDateMillis DESC")
    fun getAllPlans(): Flow<List<DetoxPlan>>

    @Query("SELECT * FROM detox_plans WHERE isActive = 1")
    fun getActivePlans(): Flow<List<DetoxPlan>>

    @Query("SELECT * FROM detox_plans WHERE isActive = 1")
    suspend fun getActivePlansSync(): List<DetoxPlan>

    @Query("SELECT * FROM detox_plans WHERE planId = :planId LIMIT 1")
    suspend fun getPlanById(planId: Long): DetoxPlan?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlan(plan: DetoxPlan): Long

    @Update
    suspend fun updatePlan(plan: DetoxPlan)

    @Delete
    suspend fun deletePlan(plan: DetoxPlan)

    @Query("DELETE FROM detox_plans WHERE planId = :planId")
    suspend fun deletePlanById(planId: Long)
}
