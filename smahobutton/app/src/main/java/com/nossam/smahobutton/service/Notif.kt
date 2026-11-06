package com.nossam.smahobutton.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.nossam.smahobutton.R

internal const val CHANNEL_ID = "smahobutton_running"
internal const val NOTIFICATION_ID = 7

internal fun ensureChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    val channel = NotificationChannel(
        CHANNEL_ID,
        context.getString(R.string.channel_name),
        NotificationManager.IMPORTANCE_LOW,
    ).apply {
        description = context.getString(R.string.channel_desc)
        setShowBadge(false)
    }
    manager.createNotificationChannel(channel)
}
