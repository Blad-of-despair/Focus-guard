package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.AppRestriction
import kotlinx.coroutines.flow.Flow

@Dao
interface RestrictionDao {
    @Query("SELECT * FROM app_restrictions ORDER BY appName ASC")
    fun getAllRestrictions(): Flow<List<AppRestriction>>

    @Query("SELECT * FROM app_restrictions WHERE packageName = :packageName LIMIT 1")
    fun getRestrictionForPackage(packageName: String): Flow<AppRestriction?>

    @Query("SELECT * FROM app_restrictions WHERE packageName = :packageName LIMIT 1")
    suspend fun getRestrictionSync(packageName: String): AppRestriction?

    @Query("SELECT * FROM app_restrictions WHERE isPaused = 0")
    suspend fun getActiveRestrictionsSync(): List<AppRestriction>

    @Query("SELECT * FROM app_restrictions")
    suspend fun getAllRestrictionsSync(): List<AppRestriction>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRestriction(restriction: AppRestriction)

    @Update
    suspend fun updateRestriction(restriction: AppRestriction)

    @Delete
    suspend fun deleteRestriction(restriction: AppRestriction)

    @Query("DELETE FROM app_restrictions WHERE packageName = :packageName")
    suspend fun deleteByPackageName(packageName: String)

    @Query("UPDATE app_restrictions SET warningNotifiedToday = :notified WHERE packageName = :packageName")
    suspend fun updateWarningNotified(packageName: String, notified: Boolean)

    @Query("UPDATE app_restrictions SET warningNotifiedToday = 0")
    suspend fun resetAllDailyWarnings()
}
