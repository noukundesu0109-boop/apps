package dev.nossam.task

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import dev.nossam.task.plan.MemoPlanner
import dev.nossam.task.plan.PlannedMemoAlarm

internal object NextTaskNotice {
    private const val TAG = "TaskAlarm"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            AlarmIntents.NEXT_CHANNEL_ID,
            context.getString(R.string.next_task_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.next_task_channel_desc)
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    fun update(context: Context, next: PlannedMemoAlarm?) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (next == null || !PermissionGate.notificationsOk(context)) {
            manager.cancel(AlarmIntents.NEXT_NOTIFICATION_ID)
            return
        }
        ensureChannel(context)
        try {
            manager.notify(AlarmIntents.NEXT_NOTIFICATION_ID, build(context, next))
        } catch (error: SecurityException) {
            Log.w(TAG, "next task notification was blocked", error)
            manager.cancel(AlarmIntents.NEXT_NOTIFICATION_ID)
        }
    }

    private fun build(context: Context, alarm: PlannedMemoAlarm): Notification {
        val open = PendingIntent.getActivity(
            context,
            9,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val done = Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmIntents.ACTION_DONE)
            .putExtra(AlarmIntents.EXTRA_KIND, alarm.kind)
            .putExtra(AlarmIntents.EXTRA_ITEM, alarm.itemId)
            .putExtra(AlarmIntents.EXTRA_TAB, alarm.tabId)
        val pending = PendingIntent.getBroadcast(
            context,
            AlarmIntents.NEXT_DONE_REQUEST,
            done,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = MemoPlanner.nextTaskText(alarm)
        return NotificationCompat.Builder(context, AlarmIntents.NEXT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(context.getString(R.string.next_task_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(MemoPlanner.nextTaskNotice(alarm)))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .addAction(0, context.getString(R.string.next_task_done), pending)
            .build()
    }
}
