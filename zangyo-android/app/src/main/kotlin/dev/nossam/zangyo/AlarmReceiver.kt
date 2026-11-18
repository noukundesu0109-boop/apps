package dev.nossam.zangyo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

internal class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        val action = intent.action
        val title = intent.getStringExtra(AlarmIntents.EXTRA_TITLE)
        val detail = intent.getStringExtra(AlarmIntents.EXTRA_DETAIL)
        val source = intent.getStringExtra(AlarmIntents.EXTRA_SOURCE)
        Thread {
            try {
                when (action) {
                    AlarmIntents.ACTION_SKIP_NEXT -> {
                        val id = intent.getStringExtra(AlarmIntents.EXTRA_ID).orEmpty()
                        if (id.startsWith("s|")) AlarmScheduler.setAlarmSkipped(app, id, true)
                        else AlarmScheduler.reschedule(app)
                    }
                    AlarmIntents.ACTION_CANCEL_SNOOZE -> {
                        AlarmScheduler.cancelSnooze(app, intent.getStringExtra(AlarmIntents.EXTRA_ID).orEmpty())
                    }
                    else -> {
                        if (action == AlarmIntents.ACTION_FIRE) {
                            AlarmRingService.markRinging()
                            val service = Intent(app, AlarmRingService::class.java)
                                .setAction(AlarmIntents.ACTION_FIRE)
                                .apply {
                                    putExtra(AlarmIntents.EXTRA_TITLE, title)
                                    putExtra(AlarmIntents.EXTRA_DETAIL, detail)
                                    putExtra(AlarmIntents.EXTRA_SOURCE, source)
                                }
                            try {
                                ContextCompat.startForegroundService(app, service)
                            } catch (error: Exception) {
                                Log.e(TAG, "could not start the ringing service", error)
                                AlarmRingService.clearRinging()
                                AlarmFallbackNotice.show(app, title, detail)
                            }
                        }
                        try {
                            AlarmScheduler.reschedule(app)
                        } catch (error: Exception) {
                            Log.e(TAG, "could not reschedule alarms", error)
                        }
                    }
                }
            } finally {
                pending.finish()
            }
        }.start()
    }

    private companion object {
        const val TAG = "ZangyoAlarm"
    }
}

internal class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        Thread {
            try {
                AlarmScheduler.reschedule(app)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
