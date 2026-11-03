package dev.nossam.zangyo

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class ZangyoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ensureAlarmChannel(this)
    }
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
