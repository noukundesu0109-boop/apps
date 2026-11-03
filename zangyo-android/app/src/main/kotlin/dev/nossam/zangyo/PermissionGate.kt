package dev.nossam.zangyo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.app.NotificationManager
import androidx.core.content.ContextCompat
import org.json.JSONObject

internal object PermissionGate {
    private const val PREFS = "zangyo-permissions"
    private const val SUPPRESS_UNTIL = "suppress_until"

    fun notificationsOk(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun fullScreenOk(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 34) return true
        return context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    }

    fun exactOk(context: Context): Boolean = AlarmScheduler.canExact(context)

    fun batteryOk(context: Context): Boolean {
        val power = context.getSystemService(PowerManager::class.java) ?: return false
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun needsPrompt(context: Context): Boolean {
        val missing = !notificationsOk(context) || !fullScreenOk(context) || !exactOk(context)
        if (!missing) return false
        val until = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(SUPPRESS_UNTIL, 0L)
        return System.currentTimeMillis() > until
    }

    fun suppressForHours(context: Context, hours: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(SUPPRESS_UNTIL, System.currentTimeMillis() + hours * 3_600_000L)
            .apply()
    }

    fun statusJson(context: Context): String {
        return JSONObject()
            .put("notifications", notificationsOk(context))
            .put("fullScreen", fullScreenOk(context))
            .put("exact", exactOk(context))
            .put("battery", batteryOk(context))
            .toString()
    }
}
