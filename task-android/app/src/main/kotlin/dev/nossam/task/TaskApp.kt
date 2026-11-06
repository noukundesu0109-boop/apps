package dev.nossam.task

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class TaskApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ensureAlarmChannel(this)
        ensureSummaryChannel(this)
        ensureNextTaskChannel(this)
        ensureAheadChannel(this)
        ensureQuietChannel(this)
    }
}

internal fun ensureSummaryChannel(context: android.content.Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    val channel = NotificationChannel(
        AlarmIntents.SUMMARY_CHANNEL_ID,
        context.getString(R.string.summary_channel_name),
        NotificationManager.IMPORTANCE_DEFAULT,
    ).apply {
        description = context.getString(R.string.summary_channel_desc)
        lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
    }
    manager.createNotificationChannel(channel)
}

internal fun ensureNextTaskChannel(context: android.content.Context) {
    NextTaskNotice.ensureChannel(context)
}

internal fun ensureAheadChannel(context: android.content.Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    val channel = NotificationChannel(
        AlarmIntents.LEAD_CHANNEL_ID,
        context.getString(R.string.ahead_channel),
        NotificationManager.IMPORTANCE_HIGH,
    ).apply {
        description = context.getString(R.string.ahead_channel_desc)
        lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
    }
    manager.createNotificationChannel(channel)
}

internal fun ensureQuietChannel(context: android.content.Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    val channel = NotificationChannel(
        AlarmIntents.QUIET_CHANNEL_ID,
        context.getString(R.string.quiet_channel_name),
        NotificationManager.IMPORTANCE_DEFAULT,
    ).apply {
        description = context.getString(R.string.quiet_channel_desc)
        lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        setSound(null, null)
        enableVibration(false)
    }
    manager.createNotificationChannel(channel)
}

internal fun ensureAlarmChannel(context: android.content.Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    val channel = NotificationChannel(
        AlarmIntents.CHANNEL_ID,
        context.getString(R.string.channel_name),
        NotificationManager.IMPORTANCE_HIGH,
    ).apply {
        description = context.getString(R.string.channel_desc)
        lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        setSound(null, null)
        enableVibration(false)
    }
    manager.createNotificationChannel(channel)
}
