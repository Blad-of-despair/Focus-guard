package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

object RestrictionMode {
    const val BLOCK_COMPLETELY = "BLOCK_COMPLETELY"
    const val WINDOW_ONLY = "WINDOW_ONLY"
    const val DAILY_LIMIT_ONLY = "DAILY_LIMIT_ONLY"
}

@Entity(tableName = "app_restrictions")
data class AppRestriction(
    @PrimaryKey
    val packageName: String,
    val appName: String,
    val mode: String = RestrictionMode.DAILY_LIMIT_ONLY,
    val dailyLimitMinutes: Int = 60,
    val windowStartMinutes: Int = 1260, // 21:00 (9:00 PM)
    val windowEndMinutes: Int = 1320,   // 22:00 (10:00 PM)
    val daysOfWeek: String = "1,2,3,4,5,6,7", // 1=Mon, 2=Tue, 3=Wed, 4=Thu, 5=Fri, 6=Sat, 7=Sun
    val isPaused: Boolean = false,
    val commitmentLockedUntil: Long = 0L,
    val minLimitCommitted: Int = 60,
    val isAppLockProtected: Boolean = false,
    val warningNotifiedToday: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun isCommitmentActive(currentTimeMillis: Long = System.currentTimeMillis()): Boolean {
        return commitmentLockedUntil > currentTimeMillis
    }

    fun getDaysList(): List<Int> {
        return daysOfWeek.split(",").mapNotNull { it.trim().toIntOrNull() }
    }
}
