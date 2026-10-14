package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.example.FocusGuardApplication
import com.example.data.local.SecurityManager
import com.example.engine.RestrictionEngine
import com.example.ui.screens.AppLockActivity
import com.example.ui.screens.BlockedAppActivity
import com.example.ui.screens.MindfulPauseActivity
import com.example.util.BlockLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FocusGuardAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        @Volatile
        var currentForegroundPackage: String? = null

        // Keeps track of temporary pass after Mindful Pause breathing
        val tempAllowedPackages = mutableMapOf<String, Long>()

        fun grantTemporaryPass(packageName: String, durationMillis: Long = 60_000L) {
            tempAllowedPackages[packageName] = System.currentTimeMillis() + durationMillis
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return

        // Skip system UI, launchers, and our own app
        if (packageName == applicationContext.packageName ||
            packageName == "com.android.systemui" ||
            packageName.contains("launcher")
        ) {
            currentForegroundPackage = null
            return
        }

        currentForegroundPackage = packageName

        // Check and handle package on every foreground transition without debounce
        serviceScope.launch {
            checkAndHandlePackage(packageName)
        }
    }

    private suspend fun checkAndHandlePackage(packageName: String) {
        val app = application as? FocusGuardApplication ?: return
        val database = app.database
        val usageHelper = app.usageStatsHelper

        // Check if temporary pass is active from Mindful Pause
        val passExpires = tempAllowedPackages[packageName] ?: 0L
        val isPassActive = passExpires > System.currentTimeMillis()

        // 1. Check App Lock Protection
        val restriction = database.restrictionDao().getRestrictionSync(packageName)
        if (restriction != null && restriction.isAppLockProtected && !SecurityManager.isSessionUnlocked) {
            val lockIntent = Intent(this, AppLockActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(AppLockActivity.EXTRA_TARGET_PACKAGE, packageName)
            }
            startActivity(lockIntent)
            return
        }

        // 2. Check Detox Plan Association
        val activePlans = database.detoxPlanDao().getActivePlansSync()
        val detoxPlan = activePlans.firstOrNull { it.getTargetPackages().contains(packageName) }

        // If part of active detox plan and no pass yet, show Mindful Pause breathing screen first
        if (detoxPlan != null && !isPassActive) {
            val pauseIntent = Intent(this, MindfulPauseActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MindfulPauseActivity.EXTRA_PACKAGE_NAME, packageName)
                putExtra(MindfulPauseActivity.EXTRA_APP_NAME, restriction?.appName ?: packageName)
            }
            startActivity(pauseIntent)
            return
        }

        // 3. Check App Restriction (Limits / Windows / Total Block)
        if (restriction != null && !restriction.isPaused) {
            // Use real-time UsageEvents calculation including active ongoing session
            val todayUsageMillis = usageHelper.getAppUsageTodayWithCurrentSessionMillis(packageName)
            val todayUsageMinutes = (todayUsageMillis / (1000 * 60)).toInt()

            val result = RestrictionEngine.evaluate(restriction, todayUsageMinutes)
            if (result.isBlocked) {
                // Log the block event for debugging and history
                BlockLogger.logBlock(this, packageName, restriction.appName, result.reason)

                // Launch BlockedAppActivity directly on top first (no concurrent home action race)
                val blockIntent = Intent(this, BlockedAppActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(BlockedAppActivity.EXTRA_APP_NAME, restriction.appName)
                    putExtra(BlockedAppActivity.EXTRA_REASON, result.reason)
                    putExtra(BlockedAppActivity.EXTRA_UNLOCK_TIME, result.unlockTimeFormatted)
                    putExtra(BlockedAppActivity.EXTRA_PACKAGE_NAME, packageName)
                }
                startActivity(blockIntent)
            }
        }
    }

    override fun onInterrupt() {}
}
