package dev.nossam.zangyo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import dev.nossam.zangyo.plan.AlarmPlanner
import dev.nossam.zangyo.plan.PlannedAlarm

internal object NextAlarmNotice {
    private const val TAG = "ZangyoAlarm"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            AlarmIntents.NEXT_CHANNEL_ID,
            context.getString(R.string.next_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.next_channel_desc)
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    fun update(context: Context, next: PlannedAlarm?) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (next == null || !NextAlarmPrefs.enabled(context) || !PermissionGate.notificationsOk(context)) {
            manager.cancel(AlarmIntents.NEXT_NOTIFICATION_ID)
            return
        }
        ensureChannel(context)
        try {
            manager.notify(AlarmIntents.NEXT_NOTIFICATION_ID, build(context, next))
        } catch (error: SecurityException) {
            Log.w(TAG, "next alarm notification was blocked", error)
            manager.cancel(AlarmIntents.NEXT_NOTIFICATION_ID)
        }
    }

    fun showTab(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(AlarmIntents.EXTRA_OPEN_TAB, "alarms")
        }
        return PendingIntent.getActivity(
            context,
            AlarmIntents.SHOW_TAB_REQUEST,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun build(context: Context, alarm: PlannedAlarm): Notification {
        val text = AlarmPlanner.nextAlarmText(alarm)
        val builder = NotificationCompat.Builder(context, AlarmIntents.NEXT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(context.getString(R.string.next_alarm_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(AlarmPlanner.nextAlarmNotice(alarm)))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(showTab(context))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
        if (alarm.id.startsWith("s|")) {
            val skip = Intent(context, AlarmReceiver::class.java)
                .setAction(AlarmIntents.ACTION_SKIP_NEXT)
                .putExtra(AlarmIntents.EXTRA_ID, alarm.id)
            val pending = PendingIntent.getBroadcast(
                context,
                AlarmIntents.NEXT_SKIP_REQUEST,
                skip,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, context.getString(R.string.next_alarm_skip), pending)
        }
        return builder.build()
    }
}
