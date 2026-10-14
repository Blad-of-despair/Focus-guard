package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CutoffReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_CUTOFF_ALARM = "com.example.focusguard.ACTION_CUTOFF_ALARM"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_APP_NAME = "extra_app_name"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        if (intent.action == ACTION_CUTOFF_ALARM) {
            val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: return
            val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: packageName

            CutoffScheduler.triggerCutoff(
                context = context,
                packageName = packageName,
                appName = appName,
                reason = "Daily usage limit reached in real time."
            )
        }
    }
}
