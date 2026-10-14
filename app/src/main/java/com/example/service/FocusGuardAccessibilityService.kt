package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.example.FocusGuardApplication
import com.example.data.local.SecurityManager
import com.example.data.model.RestrictionMode
import com.example.engine.RestrictionEngine
import com.example.ui.screens.AppLockActivity
import com.example.ui.screens.MindfulPauseActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

class FocusGuardAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        @Volatile
        var instance: FocusGuardAccessibilityService? = null

        @Volatile
        var currentForegroundPackage: String? = null

        val tempAllowedPackages = mutableMapOf<String, Long>()

        fun grantTemporaryPass(packageName: String, durationMillis: Long = 60_000L) {
            tempAllowedPackages[packageName] = System.currentTimeMillis() + durationMillis
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        CutoffScheduler.cancel(applicationContext, reason = "Accessibility service stopped")
        if (instance == this) {
            instance = null
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val eventType = event.eventType
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            return
        }

        val packageName = event.packageName?.toString() ?: return

        // Skip system launcher, status bar, and FocusGuard itself
        if (packageName == applicationContext.packageName ||
            packageName == "com.android.systemui" ||
            packageName.contains("launcher")
        ) {
            if (currentForegroundPackage != null) {
                CutoffScheduler.cancel(applicationContext, currentForegroundPackage, "Returned to home or launcher")
                currentForegroundPackage = null
            }
            return
        }

        val previousPackage = currentForegroundPackage
        if (previousPackage != null && previousPackage != packageName) {
            CutoffScheduler.cancel(applicationContext, previousPackage, "Switched away from app")
        }
        currentForegroundPackage = packageName

        serviceScope.launch {
            checkAndHandlePackage(packageName)
        }
    }

    private suspend fun checkAndHandlePackage(packageName: String) {
        val app = application as? FocusGuardApplication ?: return
        val database = app.database
        val usageHelper = app.usageStatsHelper

        val passExpires = tempAllowedPackages[packageName] ?: 0L
        val isPassActive = passExpires > System.currentTimeMillis()

        // 1. App Lock Protection
        val restriction = database.restrictionDao().getRestrictionSync(packageName)
        if (restriction != null && restriction.isAppLockProtected && !SecurityManager.isSessionUnlocked) {
            val lockIntent = Intent(this, AppLockActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(AppLockActivity.EXTRA_TARGET_PACKAGE, packageName)
            }
            startActivity(lockIntent)
            return
        }

        // 2. Detox Plan Mindful Pause
        val activePlans = database.detoxPlanDao().getActivePlansSync()
        val detoxPlan = activePlans.firstOrNull { it.getTargetPackages().contains(packageName) }
        if (detoxPlan != null && !isPassActive) {
            val pauseIntent = Intent(this, MindfulPauseActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MindfulPauseActivity.EXTRA_PACKAGE_NAME, packageName)
                putExtra(MindfulPauseActivity.EXTRA_APP_NAME, restriction?.appName ?: packageName)
            }
            startActivity(pauseIntent)
            return
        }

        // 3. Restriction Evaluation & Real-Time Cutoff Scheduling
        if (restriction != null && !restriction.isPaused) {
            val todayUsageMillis = usageHelper.getAppUsageTodayWithCurrentSessionMillis(packageName)
            val todayUsageMinutes = (todayUsageMillis / (1000 * 60)).toInt()

            val result = RestrictionEngine.evaluate(restriction, todayUsageMinutes)
            if (result.isBlocked) {
                // Limit already reached or outside allowed window: trigger cutoff immediately
                CutoffScheduler.triggerCutoff(
                    context = this,
                    packageName = packageName,
                    appName = restriction.appName,
                    reason = result.reason
                )
            } else {
                // Not yet blocked: calculate exact remaining milliseconds and schedule exact alarm
                when (restriction.mode) {
                    RestrictionMode.DAILY_LIMIT_ONLY -> {
                        val limitMillis = restriction.dailyLimitMinutes * 60 * 1000L
                        val remainingMillis = limitMillis - todayUsageMillis
                        if (remainingMillis <= 0L) {
                            CutoffScheduler.triggerCutoff(
                                context = this,
                                packageName = packageName,
                                appName = restriction.appName,
                                reason = "Daily usage limit reached in real time."
                            )
                        } else {
                            CutoffScheduler.schedule(
                                context = this,
                                packageName = packageName,
                                appName = restriction.appName,
                                remainingMillis = remainingMillis
                            )
                        }
                    }

                    RestrictionMode.WINDOW_ONLY -> {
                        val calendar = Calendar.getInstance()
                        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
                        val currentMin = calendar.get(Calendar.MINUTE)
                        val currentSec = calendar.get(Calendar.SECOND)
                        val currentMinuteOfDay = currentHour * 60 + currentMin

                        val inWindow = RestrictionEngine.isWithinAllowedWindow(
                            currentMinuteOfDay,
                            restriction.windowStartMinutes,
                            restriction.windowEndMinutes
                        )

                        if (!inWindow) {
                            CutoffScheduler.triggerCutoff(
                                context = this,
                                packageName = packageName,
                                appName = restriction.appName,
                                reason = "Outside allowed time window."
                            )
                        } else {
                            val endMin = restriction.windowEndMinutes
                            val remainingMinutes = if (endMin >= currentMinuteOfDay) {
                                endMin - currentMinuteOfDay
                            } else {
                                (1440 - currentMinuteOfDay) + endMin
                            }
                            val remainingMillis = (remainingMinutes * 60 * 1000L) - (currentSec * 1000L)
                            CutoffScheduler.schedule(
                                context = this,
                                packageName = packageName,
                                appName = restriction.appName,
                                remainingMillis = remainingMillis.coerceAtLeast(1000L)
                            )
                        }
                    }
                }
            }
        }
    }

    fun triggerHomeAfterBlock() {
        serviceScope.launch {
            // Allow the BlockedAppActivity 400ms to mount its window before performing home action
            delay(400L)
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    override fun onInterrupt() {}
}
