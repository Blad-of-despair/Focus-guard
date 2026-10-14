package com.example

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import com.example.data.local.AppDatabase
import com.example.data.local.FocusGuardPreferences
import com.example.data.local.SecurityManager
import com.example.service.FocusGuardMonitoringService
import com.example.util.UsageStatsHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class FocusGuardApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var preferences: FocusGuardPreferences
        private set

    lateinit var securityManager: SecurityManager
        private set

    lateinit var usageStatsHelper: UsageStatsHelper
        private set

    private val applicationScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var startedActivityCount = 0

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        preferences = FocusGuardPreferences(this)
        securityManager = SecurityManager(this, preferences)
        usageStatsHelper = UsageStatsHelper(this)

        // Record clock anchor for tampering detection
        applicationScope.launch {
            preferences.saveClockAnchor(
                wallTimeMillis = System.currentTimeMillis(),
                elapsedRealtimeMillis = SystemClock.elapsedRealtime()
            )
        }

        // Start background monitoring service
        FocusGuardMonitoringService.start(this)

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                // Hide content in recent tasks screen for security
                activity.window.setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE
                )
            }

            override fun onActivityStarted(activity: Activity) {
                startedActivityCount++
            }

            override fun onActivityResumed(activity: Activity) {}

            override fun onActivityPaused(activity: Activity) {}

            override fun onActivityStopped(activity: Activity) {
                startedActivityCount--
                if (startedActivityCount <= 0) {
                    // App went to background: lock session and record timestamp
                    applicationScope.launch {
                        val lockApp = preferences.lockFocusGuardFlow.first()
                        if (lockApp) {
                            SecurityManager.lockSession()
                        }
                        preferences.updateLastBackgroundTime(System.currentTimeMillis())
                    }
                }
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
