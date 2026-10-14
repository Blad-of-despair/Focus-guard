package com.example.data.model

import android.graphics.drawable.Drawable

data class InstalledAppItem(
    val packageName: String,
    val appName: String,
    val icon: Drawable? = null,
    val isSystemApp: Boolean = false,
    val category: String = "Other"
)

data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val totalTimeForegroundMillis: Long,
    val lastTimeUsedMillis: Long,
    val launchCount: Int = 0
) {
    val totalMinutes: Long
        get() = totalTimeForegroundMillis / (1000 * 60)

    fun formattedTime(): String {
        val totalSecs = totalTimeForegroundMillis / 1000
        val hours = totalSecs / 3600
        val minutes = (totalSecs % 3600) / 60
        return if (hours > 0) {
            "${hours}h ${minutes}m"
        } else {
            "${minutes}m"
        }
    }
}

data class DailyUsageSummary(
    val dateString: String, // "Mon", "Tue", etc.
    val dayTimestamp: Long,
    val totalForegroundMinutes: Long
)
