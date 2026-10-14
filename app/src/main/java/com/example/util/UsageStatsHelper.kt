package com.example.util

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.example.data.model.AppUsageInfo
import com.example.data.model.DailyUsageSummary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class UsageStatsHelper(private val context: Context) {

    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    /**
     * Calculates today's usage from real-time UsageEvents (ACTIVITY_RESUMED / ACTIVITY_PAUSED),
     * including the currently active foreground session. This avoids queryUsageStats aggregation lag.
     */
    fun getAppUsageTodayWithCurrentSessionMillis(packageName: String): Long {
        if (usageStatsManager == null) return 0L
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.timeInMillis
        val now = System.currentTimeMillis()

        val usageEvents = usageStatsManager.queryEvents(startOfDay, now) ?: return getAppUsageTodayMillis(packageName)
        val event = UsageEvents.Event()
        var totalUsageMillis = 0L
        var resumeTimestamp = 0L

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.packageName == packageName) {
                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED,
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                        resumeTimestamp = event.timeStamp
                    }
                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.ACTIVITY_STOPPED,
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                        if (resumeTimestamp > 0L && event.timeStamp >= resumeTimestamp) {
                            totalUsageMillis += (event.timeStamp - resumeTimestamp)
                            resumeTimestamp = 0L
                        }
                    }
                }
            }
        }

        // If currently in the foreground (resumed without a subsequent pause)
        if (resumeTimestamp > 0L && now >= resumeTimestamp) {
            totalUsageMillis += (now - resumeTimestamp)
        }

        val aggregated = getAppUsageTodayMillis(packageName)
        return maxOf(totalUsageMillis, aggregated)
    }

    /**
     * Retrieves today's total foreground usage for a specific app package in milliseconds.
     */
    fun getAppUsageTodayMillis(packageName: String): Long {
        if (usageStatsManager == null) return 0L
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )

        return stats.filter { it.packageName == packageName }
            .sumOf { it.totalTimeInForeground }
    }

    /**
     * Gets total foreground usage for all apps today.
     */
    fun getAllAppsUsageToday(): List<AppUsageInfo> {
        if (usageStatsManager == null) return emptyList()
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val pm = context.packageManager
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )

        val result = mutableListOf<AppUsageInfo>()
        for (stat in stats) {
            if (stat.totalTimeInForeground > 30_000L) { // Filter out less than 30s
                val appName = try {
                    val appInfo = pm.getApplicationInfo(stat.packageName, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    stat.packageName
                }

                result.add(
                    AppUsageInfo(
                        packageName = stat.packageName,
                        appName = appName,
                        totalTimeForegroundMillis = stat.totalTimeInForeground,
                        lastTimeUsedMillis = stat.lastTimeUsed
                    )
                )
            }
        }
        return result.sortedByDescending { it.totalTimeForegroundMillis }
    }

    /**
     * Computes usage summary for each of the last 7 days.
     */
    fun getWeeklyUsageSummary(): List<DailyUsageSummary> {
        if (usageStatsManager == null) return emptyList()

        val result = mutableListOf<DailyUsageSummary>()
        val dateFormat = SimpleDateFormat("EEE", Locale.getDefault())

        val calendar = Calendar.getInstance()
        for (i in 6 downTo 0) {
            val dayCal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -i)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startOfDay = dayCal.timeInMillis
            val endOfDay = if (i == 0) {
                System.currentTimeMillis()
            } else {
                startOfDay + (24 * 60 * 60 * 1000) - 1
            }

            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startOfDay,
                endOfDay
            )

            val totalMillis = stats.sumOf { it.totalTimeInForeground }
            val minutes = totalMillis / (1000 * 60)

            result.add(
                DailyUsageSummary(
                    dateString = dateFormat.format(dayCal.time),
                    dayTimestamp = startOfDay,
                    totalForegroundMinutes = minutes
                )
            )
        }

        return result
    }
}
