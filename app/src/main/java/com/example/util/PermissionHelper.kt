package com.example.util

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.example.service.FocusGuardAccessibilityService
import com.example.service.FocusGuardDeviceAdminReceiver

data class PermissionItem(
    val id: String,
    val title: String,
    val description: String,
    val isGranted: Boolean,
    val isRequired: Boolean = true
)

object PermissionHelper {

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun hasOverlayPermission(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun hasAccessibilityPermission(context: Context): Boolean {
        val expectedServiceName = "${context.packageName}/${FocusGuardAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices.split(":").any {
            it.equals(expectedServiceName, ignoreCase = true) ||
                    it.contains(context.packageName) && it.contains("FocusGuardAccessibilityService")
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun hasBatteryOptimizationExemption(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun hasDeviceAdminPermission(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return false
        val adminComponent = ComponentName(context, FocusGuardDeviceAdminReceiver::class.java)
        return dpm.isAdminActive(adminComponent)
    }

    fun getPermissionsList(context: Context): List<PermissionItem> {
        return listOf(
            PermissionItem(
                id = "usage_access",
                title = "Usage Access",
                description = "Required to calculate daily time spent and trigger 5-minute warnings.",
                isGranted = hasUsageStatsPermission(context),
                isRequired = true
            ),
            PermissionItem(
                id = "accessibility",
                title = "Accessibility Service",
                description = "Enables instant foreground detection to enforce limits and show mindful pause screen.",
                isGranted = hasAccessibilityPermission(context),
                isRequired = true
            ),
            PermissionItem(
                id = "overlay",
                title = "Display Over Other Apps",
                description = "Required to present full-screen restriction alerts over blocked apps.",
                isGranted = hasOverlayPermission(context),
                isRequired = true
            ),
            PermissionItem(
                id = "notifications",
                title = "Notifications",
                description = "Delivers active protection status and 5-minute limit expiration warnings.",
                isGranted = hasNotificationPermission(context),
                isRequired = true
            ),
            PermissionItem(
                id = "battery",
                title = "Battery Optimization Exemption",
                description = "Prevents Android OEM task killers from prematurely killing focus protection.",
                isGranted = hasBatteryOptimizationExemption(context),
                isRequired = false
            ),
            PermissionItem(
                id = "device_admin",
                title = "Device Administrator",
                description = "Prevents impulse uninstallation during active 7-Day Commitment Locks.",
                isGranted = hasDeviceAdminPermission(context),
                isRequired = false
            )
        )
    }

    fun createIntentForPermission(context: Context, id: String): Intent {
        return when (id) {
            "usage_access" -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            "accessibility" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            "overlay" -> Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            "notifications" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                }
            }
            "battery" -> {
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}")
                )
            }
            "device_admin" -> {
                val adminComponent = ComponentName(context, FocusGuardDeviceAdminReceiver::class.java)
                Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                    putExtra(
                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "FocusGuard requires Device Admin to enforce unbreakable 7-day commitment locks."
                    )
                }
            }
            else -> Intent(Settings.ACTION_SETTINGS)
        }
    }
}
