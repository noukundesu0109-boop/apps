package dev.nossam.task

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
        val date = intent.getStringExtra(AlarmIntents.EXTRA_DATE)
        val time = intent.getStringExtra(AlarmIntents.EXTRA_TIME)
        Thread {
            try {
                if (action == AlarmIntents.ACTION_SUMMARY) {
                    TaskAlarms.showSummary(app, date)
                }
                if (action == AlarmIntents.ACTION_LEAD) {
                    val minutes = intent.getIntExtra(AlarmIntents.EXTRA_LEAD, 0)
                    val whenText = intent.getStringExtra(AlarmIntents.EXTRA_ID).orEmpty()
                    TaskAlarms.showLead(app, title.orEmpty(), whenText, minutes)
                }
                if (action == AlarmIntents.ACTION_DONE) {
                    TaskAlarms.complete(
                        app,
                        intent.getStringExtra(AlarmIntents.EXTRA_KIND).orEmpty(),
                        intent.getStringExtra(AlarmIntents.EXTRA_ITEM).orEmpty(),
                        intent.getStringExtra(AlarmIntents.EXTRA_TAB).orEmpty(),
                    )
                }
                if (action != AlarmIntents.ACTION_DONE) TaskAlarms.reschedule(app)
                if (action == AlarmIntents.ACTION_FIRE) {
                    val service = Intent(app, AlarmRingService::class.java).setAction(AlarmIntents.ACTION_FIRE).apply {
                        putExtra(AlarmIntents.EXTRA_TITLE, title)
                        putExtra(AlarmIntents.EXTRA_DETAIL, detail)
                        putExtra(AlarmIntents.EXTRA_SOURCE, source)
                        putExtra(AlarmIntents.EXTRA_KIND, intent.getStringExtra(AlarmIntents.EXTRA_KIND))
                        putExtra(AlarmIntents.EXTRA_ITEM, intent.getStringExtra(AlarmIntents.EXTRA_ITEM))
                        putExtra(AlarmIntents.EXTRA_TAB, intent.getStringExtra(AlarmIntents.EXTRA_TAB))
                        putExtra(AlarmIntents.EXTRA_DATE, date)
                        putExtra(AlarmIntents.EXTRA_TIME, time)
                    }
                    try {
                        ContextCompat.startForegroundService(app, service)
                    } catch (error: Exception) {
                        Log.e(TAG, "could not start the ringing service", error)
                    }
                }
            } finally {
                pending.finish()
            }
        }.start()
    }

    private companion object {
        const val TAG = "TaskAlarm"
    }
}

internal class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        Thread {
            try {
                TaskAlarms.reschedule(app)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
