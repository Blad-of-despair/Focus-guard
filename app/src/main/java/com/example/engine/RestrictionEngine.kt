package com.example.engine

import com.example.data.model.AppRestriction
import com.example.data.model.RestrictionMode
import java.util.Calendar

data class RestrictionCheckResult(
    val isBlocked: Boolean,
    val reason: String = "",
    val unlockTimeFormatted: String = "",
    val minutesRemaining: Int = 0,
    val shouldWarn5Min: Boolean = false
)

object RestrictionEngine {

    /**
     * Checks if a minute of day (0..1439) falls inside an allowed window.
     * Handles overnight windows (e.g. 21:00 (1260) to 02:00 (120)).
     */
    fun isWithinAllowedWindow(currentMinuteOfDay: Int, startMin: Int, endMin: Int): Boolean {
        return if (startMin <= endMin) {
            currentMinuteOfDay in startMin..endMin
        } else {
            // Overnight window
            currentMinuteOfDay >= startMin || currentMinuteOfDay <= endMin
        }
    }

    /**
     * Maps Calendar day (SUNDAY=1, MONDAY=2, etc.) to ISO day (MONDAY=1..SUNDAY=7).
     */
    fun getIsoDayOfWeek(calendarDay: Int): Int {
        return when (calendarDay) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }
    }

    /**
     * Evaluates restriction for an app given current usage and time.
     */
    fun evaluate(
        restriction: AppRestriction,
        todayUsageMinutes: Int,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): RestrictionCheckResult {
        if (restriction.isPaused) {
            return RestrictionCheckResult(isBlocked = false)
        }

        val calendar = Calendar.getInstance().apply { timeInMillis = currentTimeMillis }
        val dayOfWeek = getIsoDayOfWeek(calendar.get(Calendar.DAY_OF_WEEK))
        val activeDays = restriction.getDaysList()

        if (!activeDays.contains(dayOfWeek)) {
            // Rule doesn't apply today
            return RestrictionCheckResult(isBlocked = false)
        }

        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMin = calendar.get(Calendar.MINUTE)
        val currentMinuteOfDay = currentHour * 60 + currentMin

        when (restriction.mode) {
            RestrictionMode.BLOCK_COMPLETELY -> {
                return RestrictionCheckResult(
                    isBlocked = true,
                    reason = "This app is completely blocked on this schedule.",
                    unlockTimeFormatted = "Tomorrow at 00:00"
                )
            }

            RestrictionMode.WINDOW_ONLY -> {
                val inWindow = isWithinAllowedWindow(
                    currentMinuteOfDay,
                    restriction.windowStartMinutes,
                    restriction.windowEndMinutes
                )
                if (!inWindow) {
                    val startHour = restriction.windowStartMinutes / 60
                    val startMinute = restriction.windowStartMinutes % 60
                    val unlockTimeStr = String.format("%02d:%02d", startHour, startMinute)
                    return RestrictionCheckResult(
                        isBlocked = true,
                        reason = "App is only allowed within the scheduled window (${formatMinutes(restriction.windowStartMinutes)} - ${formatMinutes(restriction.windowEndMinutes)}).",
                        unlockTimeFormatted = unlockTimeStr
                    )
                }
            }

            RestrictionMode.DAILY_LIMIT_ONLY -> {
                val remaining = restriction.dailyLimitMinutes - todayUsageMinutes
                val shouldWarn = remaining in 1..5 && !restriction.warningNotifiedToday

                if (todayUsageMinutes >= restriction.dailyLimitMinutes) {
                    return RestrictionCheckResult(
                        isBlocked = true,
                        reason = "Daily usage limit of ${restriction.dailyLimitMinutes}m has been reached.",
                        unlockTimeFormatted = "Tomorrow at 00:00",
                        minutesRemaining = 0,
                        shouldWarn5Min = false
                    )
                }

                return RestrictionCheckResult(
                    isBlocked = false,
                    minutesRemaining = maxOf(0, remaining),
                    shouldWarn5Min = shouldWarn
                )
            }
        }

        return RestrictionCheckResult(isBlocked = false)
    }

    fun formatMinutes(totalMin: Int): String {
        val h = totalMin / 60
        val m = totalMin % 60
        val amPm = if (h >= 12) "PM" else "AM"
        val displayHour = when {
            h == 0 -> 12
            h > 12 -> h - 12
            else -> h
        }
        return String.format("%02d:%02d %s", displayHour, m, amPm)
    }
}
