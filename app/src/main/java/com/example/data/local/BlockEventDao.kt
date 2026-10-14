package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.BlockEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockEventDao {
    @Query("SELECT * FROM block_events ORDER BY timestamp DESC LIMIT 50")
    fun getRecentBlockEvents(): Flow<List<BlockEvent>>

    @Query("SELECT * FROM block_events ORDER BY timestamp DESC LIMIT 50")
    suspend fun getRecentBlockEventsSync(): List<BlockEvent>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlockEvent(event: BlockEvent)

    @Query("DELETE FROM block_events")
    suspend fun clearAllBlockEvents()
}
