package com.example.engine

import com.example.data.model.AppRestriction
import java.util.concurrent.TimeUnit

sealed class ValidationResult {
    object Allowed : ValidationResult()
    data class Blocked(val reason: String) : ValidationResult()
}

data class ClockTamperingResult(
    val isTampered: Boolean,
    val details: String = ""
)

object CommitmentLockValidator {

    const val SEVEN_DAYS_MILLIS = 7L * 24L * 60L * 60L * 1000L

    /**
     * Validates whether an edit is permitted under the 7-day commitment lock.
     * Rules can only be made stricter (e.g., lower daily limit).
     */
    fun validateRestrictionEdit(
        existing: AppRestriction,
        updated: AppRestriction,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): ValidationResult {
        if (!existing.isCommitmentActive(currentTimeMillis)) {
            return ValidationResult.Allowed
        }

        // Cannot pause during commitment lock
        if (updated.isPaused && !existing.isPaused) {
            return ValidationResult.Blocked("Cannot pause a rule during an active 7-Day Commitment Lock.")
        }

        // Cannot increase daily limit
        if (updated.dailyLimitMinutes > existing.dailyLimitMinutes) {
            return ValidationResult.Blocked(
                "Cannot increase daily limit from ${existing.dailyLimitMinutes}m to ${updated.dailyLimitMinutes}m during commitment lock. Rules may only be made stricter."
            )
        }

        // Cannot remove days of the week
        val existingDays = existing.getDaysList().toSet()
        val updatedDays = updated.getDaysList().toSet()
        if (!updatedDays.containsAll(existingDays)) {
            return ValidationResult.Blocked(
                "Cannot remove active days during commitment lock. You may only add more days."
            )
        }

        return ValidationResult.Allowed
    }

    /**
     * Validates whether deletion is allowed.
     */
    fun validateRestrictionDelete(
        existing: AppRestriction,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): ValidationResult {
        if (existing.isCommitmentActive(currentTimeMillis)) {
            val countdown = formatLockCountdown(existing.commitmentLockedUntil, currentTimeMillis)
            return ValidationResult.Blocked(
                "This restriction is locked under your 7-Day Commitment. $countdown. Deletions are forbidden."
            )
        }
        return ValidationResult.Allowed
    }

    /**
     * Formats remaining time until unlock (e.g., "Unlocks in 5d 3h").
     */
    fun formatLockCountdown(
        lockedUntilMillis: Long,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): String {
        val remainingMillis = lockedUntilMillis - currentTimeMillis
        if (remainingMillis <= 0) {
            return "Commitment completed"
        }

        val days = TimeUnit.MILLISECONDS.toDays(remainingMillis)
        val hours = TimeUnit.MILLISECONDS.toHours(remainingMillis) % 24
        val minutes = TimeUnit.MILLISECONDS.toMinutes(remainingMillis) % 60

        return when {
            days > 0 -> "Unlocks in ${days}d ${hours}h"
            hours > 0 -> "Unlocks in ${hours}h ${minutes}m"
            else -> "Unlocks in ${minutes}m"
        }
    }

    /**
     * Detects clock tampering by comparing changes in wall clock time vs SystemClock.elapsedRealtime().
     * If user moves the system clock backwards or skips ahead significantly faster than elapsed time,
     * tampering is flagged.
     */
    fun detectClockTampering(
        lastAnchorWallTime: Long,
        lastAnchorElapsedRealtime: Long,
        currentWallTime: Long,
        currentElapsedRealtime: Long
    ): ClockTamperingResult {
        if (lastAnchorWallTime == 0L || lastAnchorElapsedRealtime == 0L) {
            return ClockTamperingResult(isTampered = false)
        }

        // 1. Clock moved backwards
        if (currentWallTime < lastAnchorWallTime - 5000L) {
            return ClockTamperingResult(
                isTampered = true,
                details = "System clock was turned backwards. Commitment lock remains active."
            )
        }

        // 2. Clock jumped forward ahead of monotonic boot elapsed realtime
        val elapsedDiff = currentElapsedRealtime - lastAnchorElapsedRealtime
        val wallDiff = currentWallTime - lastAnchorWallTime

        // If wall clock moved forward by more than 15 minutes beyond elapsed realtime drift
        if (elapsedDiff > 0 && (wallDiff - elapsedDiff) > TimeUnit.MINUTES.toMillis(15)) {
            return ClockTamperingResult(
                isTampered = true,
                details = "System clock was artificially set forward. Monotonic hardware timer detected tamper."
            )
        }

        return ClockTamperingResult(isTampered = false)
    }
}
