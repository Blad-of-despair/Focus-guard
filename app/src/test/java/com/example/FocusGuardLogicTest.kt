package com.example

import com.example.data.model.AppRestriction
import com.example.data.model.DetoxIntensity
import com.example.data.model.RestrictionMode
import com.example.engine.CommitmentLockValidator
import com.example.engine.DetoxPlanGenerator
import com.example.engine.RestrictionEngine
import com.example.engine.ValidationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class FocusGuardLogicTest {

    // ==========================================
    // 1. Time-Window Logic Tests
    // ==========================================

    @Test
    fun testDaytimeWindow() {
        val startMin = 9 * 60 // 09:00 (540m)
        val endMin = 17 * 60  // 17:00 (1020m)

        // Inside window at 12:30 (750m)
        assertTrue(RestrictionEngine.isWithinAllowedWindow(12 * 60 + 30, startMin, endMin))

        // Before window at 08:30 (510m)
        assertFalse(RestrictionEngine.isWithinAllowedWindow(8 * 60 + 30, startMin, endMin))

        // After window at 18:00 (1080m)
        assertFalse(RestrictionEngine.isWithinAllowedWindow(18 * 60, startMin, endMin))
    }

    @Test
    fun testOvernightWindow() {
        val startMin = 21 * 60 // 21:00 (1260m)
        val endMin = 2 * 60    // 02:00 (120m)

        // Inside at 22:30 (1350m)
        assertTrue(RestrictionEngine.isWithinAllowedWindow(22 * 60 + 30, startMin, endMin))

        // Inside at 01:15 (75m)
        assertTrue(RestrictionEngine.isWithinAllowedWindow(1 * 60 + 15, startMin, endMin))

        // Outside at 14:00 (840m)
        assertFalse(RestrictionEngine.isWithinAllowedWindow(14 * 60, startMin, endMin))
    }

    @Test
    fun testWarningTrigger5MinBeforeLimit() {
        val restriction = AppRestriction(
            packageName = "com.test.app",
            appName = "Test App",
            mode = RestrictionMode.DAILY_LIMIT_ONLY,
            dailyLimitMinutes = 60,
            daysOfWeek = "1,2,3,4,5,6,7",
            warningNotifiedToday = false
        )

        // 57 min used -> 3 min remaining -> should warn
        val result = RestrictionEngine.evaluate(restriction, todayUsageMinutes = 57)
        assertFalse(result.isBlocked)
        assertTrue(result.shouldWarn5Min)
        assertEquals(3, result.minutesRemaining)

        // When limit is reached at 60 min -> should block
        val blockedResult = RestrictionEngine.evaluate(restriction, todayUsageMinutes = 60)
        assertTrue(blockedResult.isBlocked)
    }

    // ==========================================
    // 2. Reduction-Plan Generator Tests
    // ==========================================

    @Test
    fun testGentleDetoxSchedule() {
        val schedule = DetoxPlanGenerator.generateSchedule(
            initialMinutes = 60,
            targetGoalMinutes = 30,
            intensity = DetoxIntensity.GENTLE
        )

        // Gentle is 3 days per step, -10 min reduction
        // Step 1: 60m (Days 1, 2, 3)
        // Step 2: 50m (Days 4, 5, 6)
        // Step 3: 40m (Days 7, 8, 9)
        // Maintenance: 30m (Target goal)
        assertTrue(schedule.isNotEmpty())
        assertEquals(60, schedule[0].allowedDailyMinutes)
        assertEquals(60, schedule[2].allowedDailyMinutes)
        assertEquals(50, schedule[3].allowedDailyMinutes)
        assertEquals(30, schedule.last().allowedDailyMinutes)
    }

    @Test
    fun testStrictDetoxSchedule() {
        val schedule = DetoxPlanGenerator.generateSchedule(
            initialMinutes = 60,
            targetGoalMinutes = 15,
            intensity = DetoxIntensity.STRICT
        )

        // Strict is 1 day per step, -25 min drop
        // Day 1: 60m
        // Day 2: 35m
        // Maintenance: 15m
        assertEquals(60, schedule[0].allowedDailyMinutes)
        assertEquals(35, schedule[1].allowedDailyMinutes)
        assertEquals(15, schedule.last().allowedDailyMinutes)
    }

    // ==========================================
    // 3. 7-Day Commitment Lock Logic Tests
    // ==========================================

    @Test
    fun testCommitmentLockAllowsStricterEditsOnly() {
        val currentTime = 100_000_000L
        val lockedUntil = currentTime + CommitmentLockValidator.SEVEN_DAYS_MILLIS

        val existing = AppRestriction(
            packageName = "com.test.locked",
            appName = "Locked App",
            dailyLimitMinutes = 60,
            commitmentLockedUntil = lockedUntil,
            daysOfWeek = "1,2,3,4,5"
        )

        // Attempt 1: Loosen limit from 60m to 90m -> Must be BLOCKED
        val loosened = existing.copy(dailyLimitMinutes = 90)
        val loosenedResult = CommitmentLockValidator.validateRestrictionEdit(existing, loosened, currentTime)
        assertTrue(loosenedResult is ValidationResult.Blocked)

        // Attempt 2: Make stricter from 60m to 40m -> Must be ALLOWED
        val stricter = existing.copy(dailyLimitMinutes = 40)
        val stricterResult = CommitmentLockValidator.validateRestrictionEdit(existing, stricter, currentTime)
        assertTrue(stricterResult is ValidationResult.Allowed)

        // Attempt 3: Pause the rule during lock -> Must be BLOCKED
        val paused = existing.copy(isPaused = true)
        val pausedResult = CommitmentLockValidator.validateRestrictionEdit(existing, paused, currentTime)
        assertTrue(pausedResult is ValidationResult.Blocked)

        // Attempt 4: Remove days of week -> Must be BLOCKED
        val fewerDays = existing.copy(daysOfWeek = "1,2")
        val fewerDaysResult = CommitmentLockValidator.validateRestrictionEdit(existing, fewerDays, currentTime)
        assertTrue(fewerDaysResult is ValidationResult.Blocked)

        // Attempt 5: Delete rule during lock -> Must be BLOCKED
        val deleteResult = CommitmentLockValidator.validateRestrictionDelete(existing, currentTime)
        assertTrue(deleteResult is ValidationResult.Blocked)
    }

    @Test
    fun testCountdownFormatting() {
        val currentTime = 1_000_000_000L
        // 5 days and 3 hours remaining
        val lockTime = currentTime + (5L * 24 * 3600 * 1000) + (3L * 3600 * 1000)

        val countdown = CommitmentLockValidator.formatLockCountdown(lockTime, currentTime)
        assertEquals("Unlocks in 5d 3h", countdown)
    }

    @Test
    fun testClockTamperingDetection() {
        val anchorWall = 1_000_000_000L
        val anchorElapsed = 100_000L

        // 1. Clock moved backwards by 1 hour
        val tamperBackwards = CommitmentLockValidator.detectClockTampering(
            lastAnchorWallTime = anchorWall,
            lastAnchorElapsedRealtime = anchorElapsed,
            currentWallTime = anchorWall - (3600 * 1000),
            currentElapsedRealtime = anchorElapsed + 5000L
        )
        assertTrue(tamperBackwards.isTampered)

        // 2. Clock jumped forward by 2 days while elapsed realtime only advanced 10 seconds
        val tamperJumpForward = CommitmentLockValidator.detectClockTampering(
            lastAnchorWallTime = anchorWall,
            lastAnchorElapsedRealtime = anchorElapsed,
            currentWallTime = anchorWall + (2L * 24 * 3600 * 1000),
            currentElapsedRealtime = anchorElapsed + 10_000L
        )
        assertTrue(tamperJumpForward.isTampered)

        // 3. Normal passage of time: 1 minute on both wall clock and elapsed
        val normalTime = CommitmentLockValidator.detectClockTampering(
            lastAnchorWallTime = anchorWall,
            lastAnchorElapsedRealtime = anchorElapsed,
            currentWallTime = anchorWall + 60_000L,
            currentElapsedRealtime = anchorElapsed + 60_000L
        )
        assertFalse(normalTime.isTampered)
    }

    @Test
    fun testRealtimeUsageCalculationWithActiveSession() {
        val pastSessionsMillis = 40 * 60 * 1000L
        val sessionResumeTime = 100_000_000L
        val currentTime = sessionResumeTime + (20 * 60 * 1000L)

        val activeDuration = currentTime - sessionResumeTime
        val totalLiveUsageMillis = pastSessionsMillis + activeDuration
        val totalMinutes = (totalLiveUsageMillis / (1000 * 60)).toInt()

        assertEquals(60, totalMinutes)

        val restriction = AppRestriction(
            packageName = "com.test.live",
            appName = "Live App",
            dailyLimitMinutes = 60
        )
        val result = RestrictionEngine.evaluate(restriction, totalMinutes, currentTime)
        assertTrue(result.isBlocked)
    }
}
