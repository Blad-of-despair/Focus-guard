package com.example.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.ui.screens.BlockedAppActivity
import com.example.util.BlockLogger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CutoffScheduler {

    private const val TAG = "FocusGuardCutoff"
    private const val REQUEST_CODE = 8801

    @Volatile
    var currentlyScheduledPackage: String? = null
        private set

    @Volatile
    var scheduledTriggerWallTime: Long = 0L
        private set

    private var currentAppName: String = ""
    private val mainHandler = Handler(Looper.getMainLooper())
    private var handlerRunnable: Runnable? = null

    /**
     * Schedules an exact real-time cutoff when remaining daily limit expires.
     */
    fun schedule(
        context: Context,
        packageName: String,
        appName: String,
        remainingMillis: Long
    ) {
        // If remaining is 0 or negative, trigger immediately
        if (remainingMillis <= 0L) {
            triggerCutoff(context, packageName, appName, "Daily usage limit reached in real time.")
            return
        }

        val targetWallTime = System.currentTimeMillis() + remainingMillis

        // If already scheduled for this package at practically the same target time (within 2s), don't duplicate
        if (currentlyScheduledPackage == packageName && Math.abs(targetWallTime - scheduledTriggerWallTime) < 2000L) {
            return
        }

        // Cancel previous if scheduled for a different target or package
        cancel(context, currentlyScheduledPackage, "Rescheduling with updated usage")

        currentlyScheduledPackage = packageName
        currentAppName = appName
        scheduledTriggerWallTime = targetWallTime

        val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val targetTimeFormatted = timeFormatter.format(Date(targetWallTime))
        val remainingSecs = (remainingMillis / 1000).coerceAtLeast(1)

        val scheduleMessage = "[SCHEDULED] Cutoff scheduled for $appName in ${remainingSecs}s (at $targetTimeFormatted)"
        Log.i(TAG, scheduleMessage)
        BlockLogger.logBlock(context, packageName, appName, scheduleMessage)

        // 1. Schedule with AlarmManager.setExactAndAllowWhileIdle
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val intent = Intent(context, CutoffReceiver::class.java).apply {
            action = CutoffReceiver.ACTION_CUTOFF_ALARM
            putExtra(CutoffReceiver.EXTRA_PACKAGE_NAME, packageName)
            putExtra(CutoffReceiver.EXTRA_APP_NAME, appName)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager?.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    targetWallTime,
                    pendingIntent
                )
            } else {
                alarmManager?.setExact(
                    AlarmManager.RTC_WAKEUP,
                    targetWallTime,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm permission missing, falling back to handler", e)
        }

        // 2. Schedule Handler in-process as an immediate zero-IPC backup
        handlerRunnable?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable {
            if (currentlyScheduledPackage == packageName) {
                triggerCutoff(context, packageName, appName, "Daily usage limit reached in real time.")
            }
        }
        handlerRunnable = runnable
        mainHandler.postDelayed(runnable, remainingMillis)
    }

    /**
     * Cancels any scheduled cutoff when the app leaves foreground or rule changes.
     */
    fun cancel(context: Context, packageName: String? = null, reason: String = "App left foreground") {
        if (currentlyScheduledPackage == null) return

        val pkgToCancel = packageName ?: currentlyScheduledPackage ?: return
        if (currentlyScheduledPackage != pkgToCancel && packageName != null) {
            return
        }

        val appName = currentAppName

        // Cancel AlarmManager
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val intent = Intent(context, CutoffReceiver::class.java).apply {
            action = CutoffReceiver.ACTION_CUTOFF_ALARM
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager?.cancel(pendingIntent)
            pendingIntent.cancel()
        }

        // Cancel Handler
        handlerRunnable?.let {
            mainHandler.removeCallbacks(it)
            handlerRunnable = null
        }

        val cancelMessage = "[CANCELLED] Cutoff cancelled for $appName: $reason"
        Log.i(TAG, cancelMessage)
        BlockLogger.logBlock(context, pkgToCancel, appName, cancelMessage)

        currentlyScheduledPackage = null
        scheduledTriggerWallTime = 0L
    }

    /**
     * Executes the cutoff: logs the event, launches the block screen over the app, and returns to home.
     */
    fun triggerCutoff(
        context: Context,
        packageName: String,
        appName: String,
        reason: String
    ) {
        currentlyScheduledPackage = null
        scheduledTriggerWallTime = 0L
        handlerRunnable?.let {
            mainHandler.removeCallbacks(it)
            handlerRunnable = null
        }

        val firedMessage = "[CUTOFF FIRED] Limit reached for $appName. Enforcing block screen now."
        Log.i(TAG, firedMessage)
        BlockLogger.logBlock(context, packageName, appName, firedMessage)

        // Launch BlockedAppActivity on top
        val blockIntent = Intent(context, BlockedAppActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(BlockedAppActivity.EXTRA_APP_NAME, appName)
            putExtra(BlockedAppActivity.EXTRA_REASON, reason)
            putExtra(BlockedAppActivity.EXTRA_UNLOCK_TIME, "Tomorrow at 00:00")
            putExtra(BlockedAppActivity.EXTRA_PACKAGE_NAME, packageName)
        }
        context.startActivity(blockIntent)

        // Also notify accessibility service if active
        FocusGuardAccessibilityService.instance?.let { service ->
            service.triggerHomeAfterBlock()
        }
    }
}
