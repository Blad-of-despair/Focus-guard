package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Entity(tableName = "block_events")
data class BlockEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val reason: String,
    val formattedTime: String = SimpleDateFormat("HH:mm:ss (MMM dd)", Locale.getDefault()).format(Date(timestamp))
)
