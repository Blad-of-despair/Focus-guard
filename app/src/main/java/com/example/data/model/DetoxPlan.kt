package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

object DetoxIntensity {
    const val GENTLE = "GENTLE"     // 3 days per step, -10 min
    const val MODERATE = "MODERATE" // 2 days per step, -15 min
    const val STRICT = "STRICT"     // 1 day per step, -20 min
}

@Entity(tableName = "detox_plans")
data class DetoxPlan(
    @PrimaryKey(autoGenerate = true)
    val planId: Long = 0,
    val title: String,
    val category: String, // "REELS", "GAMING", "SOCIAL", "CUSTOM"
    val targetPackageNames: String, // Comma separated package names
    val initialMinutes: Int,
    val targetGoalMinutes: Int,
    val intensity: String = DetoxIntensity.MODERATE,
    val startDateMillis: Long = System.currentTimeMillis(),
    val commitmentLockedUntil: Long = 0L,
    val streakDays: Int = 0,
    val lastCheckInDate: String = "",
    val totalMinutesSaved: Int = 0,
    val isActive: Boolean = true,
    val currentStepIndex: Int = 0
) {
    fun getTargetPackages(): List<String> {
        return targetPackageNames.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun isCommitmentActive(currentTimeMillis: Long = System.currentTimeMillis()): Boolean {
        return commitmentLockedUntil > currentTimeMillis
    }
}
