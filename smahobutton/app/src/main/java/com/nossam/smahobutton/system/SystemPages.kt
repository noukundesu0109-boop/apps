package com.nossam.smahobutton.system

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.nossam.smahobutton.a11y.SmahoAccessibilityService

data class PermissionSnapshot(
    val overlay: Boolean,
    val accessibility: Boolean,
    val notifications: Boolean,
    val bluetooth: Boolean,
    val dnd: Boolean,
    val battery: Boolean,
)

fun readPermissions(context: Context): PermissionSnapshot {
    val app = context.applicationContext
    val notifications = if (Build.VERSION.SDK_INT < 33) {
        true
    } else {
        ContextCompat.checkSelfPermission(app, android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }
    val bluetooth = if (Build.VERSION.SDK_INT < 31) {
        true
    } else {
        ContextCompat.checkSelfPermission(app, android.Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
    }
    val power = app.getSystemService(android.os.PowerManager::class.java)
    val notificationsManager = app.getSystemService(android.app.NotificationManager::class.java)
    return PermissionSnapshot(
        overlay = Settings.canDrawOverlays(app),
        accessibility = isAccessibilityEnabled(app),
        notifications = notifications,
        bluetooth = bluetooth,
        dnd = notificationsManager?.isNotificationPolicyAccessGranted == true,
        battery = power?.isIgnoringBatteryOptimizations(app.packageName) == true,
    )
}

fun isAccessibilityEnabled(context: Context): Boolean {
    val expected = ComponentName(context, SmahoAccessibilityService::class.java)
    val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
    val enabled = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
    if (enabled.any { it.resolveInfo?.serviceInfo?.let { info ->
            ComponentName(info.packageName, info.name) == expected
        } == true
    }) {
        return true
    }
    val setting = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ) ?: return false
    val full = expected.flattenToString()
    val short = expected.flattenToShortString()
    return setting.split(':').any { it.equals(full, true) || it.equals(short, true) }
}

fun overlaySettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))

fun accessibilitySettingsIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

fun appDetailsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

fun dndSettingsIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

fun batterySettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))

fun Context.tryStart(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        if (intent.action == Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) {
            try {
                startActivity(
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                return
            } catch (_: Exception) {
            }
        }
        Toast.makeText(this, "設定を開けませんでした", Toast.LENGTH_SHORT).show()
    }
}
