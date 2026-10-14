package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.FocusGuardApplication
import com.example.MainActivity
import com.example.R
import com.example.engine.RestrictionEngine
import com.example.ui.screens.BlockedAppActivity
import com.example.util.BlockLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FocusGuardMonitoringService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var monitoringJob: Job? = null

    companion object {
        const val CHANNEL_MONITORING_ID = "focusguard_monitoring_channel"
        const val CHANNEL_WARNINGS_ID = "focusguard_warnings_channel"
        const val NOTIFICATION_ID_FOREGROUND = 1001
        const val NOTIFICATION_ID_WARNING = 1002

        fun start(context: Context) {
            val intent = Intent(context, FocusGuardMonitoringService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        startInForeground()
        startPeriodicCheckLoop()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            val monitoringChannel = NotificationChannel(
                CHANNEL_MONITORING_ID,
                getString(R.string.notification_channel_monitoring),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_monitoring_desc)
                setShowBadge(false)
            }

            val warningChannel = NotificationChannel(
                CHANNEL_WARNINGS_ID,
                getString(R.string.notification_channel_warnings),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_warnings_desc)
                enableVibration(true)
            }

            notificationManager.createNotificationChannel(monitoringChannel)
            notificationManager.createNotificationChannel(warningChannel)
        }
    }

    private fun startInForeground() {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_MONITORING_ID)
            .setContentTitle("FocusGuard Active Protection")
            .setContentText("Monitoring app limits & guarding your digital commitment")
            .setSmallIcon(R.drawable.ic_focusguard_logo)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID_FOREGROUND,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID_FOREGROUND, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID_FOREGROUND, notification)
        }
    }

    private fun startPeriodicCheckLoop() {
        monitoringJob = serviceScope.launch {
            val app = application as? FocusGuardApplication ?: return@launch
            val database = app.database
            val usageHelper = app.usageStatsHelper

            while (isActive) {
                var loopDelay = 3000L

                try {
                    val fgPackage = FocusGuardAccessibilityService.currentForegroundPackage
                    val activeRestrictions = database.restrictionDao().getActiveRestrictionsSync()

                    // Check if the current foreground app has an active restriction
                    val fgRestriction = if (fgPackage != null) {
                        activeRestrictions.firstOrNull { it.packageName == fgPackage }
                    } else null

                    if (fgRestriction != null) {
                        // While a restricted app is in the foreground, check EVERY 1 SECOND in real-time
                        loopDelay = 1000L

                        // Calculate usage from real-time UsageEvents including the running session
                        val todayUsageMillis = usageHelper.getAppUsageTodayWithCurrentSessionMillis(fgRestriction.packageName)
                        val todayUsageMinutes = (todayUsageMillis / (1000 * 60)).toInt()

                        val result = RestrictionEngine.evaluate(fgRestriction, todayUsageMinutes)
                        if (result.isBlocked) {
                            // Log and immediately enforce block
                            BlockLogger.logBlock(this@FocusGuardMonitoringService, fgRestriction.packageName, fgRestriction.appName, result.reason)

                            val blockIntent = Intent(this@FocusGuardMonitoringService, BlockedAppActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                putExtra(BlockedAppActivity.EXTRA_APP_NAME, fgRestriction.appName)
                                putExtra(BlockedAppActivity.EXTRA_REASON, result.reason)
                                putExtra(BlockedAppActivity.EXTRA_UNLOCK_TIME, result.unlockTimeFormatted)
                                putExtra(BlockedAppActivity.EXTRA_PACKAGE_NAME, fgRestriction.packageName)
                            }
                            startActivity(blockIntent)
                        } else if (result.shouldWarn5Min) {
                            showWarningNotification(fgRestriction.appName, result.minutesRemaining)
                            database.restrictionDao().updateWarningNotified(fgRestriction.packageName, true)
                        }
                    } else {
                        // General check for warning notifications on other restricted apps
                        for (restriction in activeRestrictions) {
                            val todayUsageMillis = usageHelper.getAppUsageTodayMillis(restriction.packageName)
                            val todayUsageMinutes = (todayUsageMillis / (1000 * 60)).toInt()

                            val result = RestrictionEngine.evaluate(restriction, todayUsageMinutes)
                            if (result.shouldWarn5Min) {
                                showWarningNotification(restriction.appName, result.minutesRemaining)
                                database.restrictionDao().updateWarningNotified(restriction.packageName, true)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                delay(loopDelay)
            }
        }
    }

    private fun showWarningNotification(appName: String, minutesRemaining: Int) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_WARNINGS_ID)
            .setContentTitle("Limit Ending Soon: $appName")
            .setContentText("You have only $minutesRemaining minutes remaining for $appName today.")
            .setSmallIcon(R.drawable.ic_focusguard_logo)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(NOTIFICATION_ID_WARNING + appName.hashCode(), notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
