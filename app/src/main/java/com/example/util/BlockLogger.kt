package com.example.util

import android.content.Context
import android.util.Log
import com.example.FocusGuardApplication
import com.example.data.model.BlockEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BlockLogger {

    private const val TAG = "FocusGuardBlock"
    private val scope = CoroutineScope(Dispatchers.IO)

    fun logBlock(context: Context, packageName: String, appName: String, reason: String) {
        val now = System.currentTimeMillis()
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(now))
        Log.i(TAG, "[BLOCKED] App: $appName ($packageName) at $timeStr. Reason: $reason")

        val app = context.applicationContext as? FocusGuardApplication ?: return
        scope.launch {
            try {
                val event = BlockEvent(
                    packageName = packageName,
                    appName = appName,
                    timestamp = now,
                    reason = reason,
                    formattedTime = timeStr
                )
                app.database.blockEventDao().insertBlockEvent(event)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist block event", e)
            }
        }
    }
}
