package dev.nossam.task

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import dev.nossam.task.plan.MemoPlanner
import dev.nossam.task.plan.PlannedMemoAlarm
import dev.nossam.task.plan.SnoozeHold
import dev.nossam.task.plan.TaskBoard
import dev.nossam.task.plan.completeOccurrence
import dev.nossam.task.plan.parseBoard
import dev.nossam.task.plan.plannedMemosToJson
import org.json.JSONObject
import java.time.Instant
import java.time.ZonedDateTime
import java.util.UUID
import java.util.concurrent.Executors

internal object UiBridge {
    var scheduleSink: ((String) -> Unit)? = null
    var permissionSink: ((String) -> Unit)? = null
    var lastSchedule: String? = null
    var doneSink: ((String) -> Unit)? = null

    fun publishSchedule(json: String) {
        lastSchedule = json
        val sink = scheduleSink ?: return
        android.os.Handler(android.os.Looper.getMainLooper()).post { sink(json) }
    }

    fun publishDone(json: String) {
        val sink = doneSink ?: return
        android.os.Handler(android.os.Looper.getMainLooper()).post { sink(json) }
    }
}

internal object TaskAlarms {
    private const val TAG = "TaskAlarm"
    private val lock = Any()
    private val executor = Executors.newSingleThreadExecutor()

    fun enqueue(block: () -> Unit) {
        executor.execute {
            try {
                block()
            } catch (error: Exception) {
                Log.e(TAG, "alarm update failed", error)
            }
        }
    }

    fun updateWork(context: Context, json: String) {
        val app = context.applicationContext
        val schedule = synchronized(lock) {
            AlarmStore(app).writeWork(json)
            rebuild(app)
        }
        UiBridge.publishSchedule(schedule)
    }

    fun snooze(
        context: Context,
        title: String,
        detail: String,
        minutes: Int = MemoPlanner.SNOOZE_MINUTES,
        kind: String = "",
        itemId: String = "",
        tabId: String = "",
        origDate: String = "",
        origTime: String = "",
    ) {
        val app = context.applicationContext
        val schedule = synchronized(lock) {
            val store = AlarmStore(app)
            val at = MemoPlanner.snoozeTrigger(System.currentTimeMillis(), minutes)
            val kept = store.readSnoozes().filter { it.atMillis > System.currentTimeMillis() + 5_000L }
            store.writeSnoozes(
                kept + StoredSnooze(
                    id = UUID.randomUUID().toString(),
                    atMillis = at,
                    title = title.ifBlank { "アラーム" },
                    detail = detail,
                    kind = kind,
                    itemId = itemId,
                    tabId = tabId,
                    origDate = origDate,
                    origTime = origTime,
                ),
            )
            rebuild(app)
        }
        UiBridge.publishSchedule(schedule)
    }

    fun doneOnStop(context: Context): Boolean {
        val json = AlarmStore(context.applicationContext).readWork() ?: return false
        return try {
            parseBoard(json).doneOnStop
        } catch (_: Exception) {
            false
        }
    }

    fun reschedule(context: Context) {
        val app = context.applicationContext
        val schedule = synchronized(lock) { rebuild(app) }
        UiBridge.publishSchedule(schedule)
    }

    fun setSummaryTime(context: Context, value: String) {
        val time = MemoPlanner.parseTime(value) ?: return
        val formatted = "%02d:%02d".format(time.hour, time.minute)
        val app = context.applicationContext
        val schedule = synchronized(lock) {
            AlarmStore(app).writeSummaryTime(formatted)
            rebuild(app)
        }
        UiBridge.publishSchedule(schedule)
    }

    fun complete(context: Context, kind: String, itemId: String, tabId: String) {
        if (itemId.isBlank() || kind.isBlank()) return
        val app = context.applicationContext
        val schedule = synchronized(lock) {
            val store = AlarmStore(app)
            val json = store.readWork()
            if (json != null) {
                val today = ZonedDateTime.now(MemoPlanner.ZONE).toLocalDate().toString()
                store.writeWork(completeOccurrence(json, kind, itemId, tabId, today))
                store.addPendingDone(StoredDone(kind, itemId, tabId))
                store.writeSnoozes(store.readSnoozes().filterNot { sameItem(it, kind, itemId, tabId) })
            }
            rebuild(app)
        }
        AlarmRingService.quietItem(app, kind, itemId, tabId)
        UiBridge.publishSchedule(schedule)
        UiBridge.publishDone(JSONObject().put("kind", kind).put("id", itemId).put("tabId", tabId).toString())
    }

    fun acknowledgeDone(context: Context, kind: String, itemId: String) {
        if (itemId.length > 200 || kind.length > 20) return
        AlarmStore(context.applicationContext).removePendingDone(kind, itemId)
    }

    fun showLead(context: Context, title: String, time: String, minutes: Int) {
        val app = context.applicationContext
        if (!PermissionGate.notificationsOk(app)) return
        ensureAheadChannel(app)
        val open = PendingIntent.getActivity(
            app,
            11,
            Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val whenLabel = MemoPlanner.leadLabel(minutes)
        val body = "$whenLabel $time $title".trim()
        val notification = NotificationCompat.Builder(app, AlarmIntents.LEAD_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(app.getString(R.string.ahead_title))
            .setContentText(body)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        try {
            app.getSystemService(NotificationManager::class.java)?.notify(AlarmIntents.LEAD_NOTIFICATION_ID, notification)
        } catch (error: SecurityException) {
            Log.w(TAG, "advance notice was rejected", error)
        }
    }

    fun showQuiet(context: Context, title: String, detail: String, itemId: String) {
        val app = context.applicationContext
        if (!PermissionGate.notificationsOk(app)) return
        ensureQuietChannel(app)
        val open = PendingIntent.getActivity(
            app,
            12,
            Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(app, AlarmIntents.QUIET_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(title.ifBlank { "予定" })
            .setContentText(detail)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        val id = AlarmIntents.QUIET_NOTIFICATION_BASE + (itemId.hashCode() and 0x0FFF)
        try {
            app.getSystemService(NotificationManager::class.java)?.notify(id, notification)
        } catch (error: SecurityException) {
            Log.w(TAG, "quiet notice was rejected", error)
        }
    }

    fun showSummary(context: Context, dateIso: String?) {
        synchronized(lock) {
            val app = context.applicationContext
            val board = readBoard(AlarmStore(app))
            val date = MemoPlanner.parseDate(dateIso) ?: ZonedDateTime.now(MemoPlanner.ZONE).toLocalDate()
            val summary = MemoPlanner.dailySummary(board, date.toString())
            val manager = app.getSystemService(NotificationManager::class.java) ?: return
            if (summary == null) {
                manager.cancel(AlarmIntents.SUMMARY_NOTIFICATION_ID)
                Log.i(TAG, "daily summary empty for $date")
                return
            }
            ensureSummaryChannel(app)
            val open = PendingIntent.getActivity(
                app,
                7,
                Intent(app, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(app, AlarmIntents.SUMMARY_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_alarm)
                .setContentTitle(summary.title)
                .setContentText(summary.body.lineSequence().firstOrNull() ?: summary.title)
                .setStyle(NotificationCompat.BigTextStyle().bigText(summary.body))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build()
            try {
                manager.notify(AlarmIntents.SUMMARY_NOTIFICATION_ID, notification)
                Log.i(TAG, "posted daily summary for $date")
            } catch (error: SecurityException) {
                Log.w(TAG, "daily summary notification was rejected", error)
            }
        }
    }

    fun canExact(context: Context): Boolean {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            manager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    private fun rebuild(context: Context): String {
        val store = AlarmStore(context)
        val board = readBoard(store)
        val now = ZonedDateTime.now(MemoPlanner.ZONE)
        val planned = MemoPlanner.planAlerts(board, now).toMutableList()
        val snoozes = store.readSnoozes()
            .filter { it.atMillis > System.currentTimeMillis() + 5_000L }
            .filter { MemoPlanner.keepSnooze(board, it.toHold()) }
        store.writeSnoozes(snoozes)
        for (snooze in snoozes) {
            val zoned = Instant.ofEpochMilli(snooze.atMillis).atZone(MemoPlanner.ZONE)
            planned += PlannedMemoAlarm(
                id = "z|${snooze.id}",
                triggerAtMillis = snooze.atMillis,
                date = zoned.toLocalDate().toString(),
                time = "%02d:%02d".format(zoned.hour, zoned.minute),
                title = snooze.title,
                detail = snooze.detail.ifBlank { "5分後にもう一度" },
                itemId = snooze.itemId,
                kind = snooze.kind.ifBlank { MemoPlanner.KIND_MEMO },
                tabId = snooze.tabId,
                sourceDate = snooze.origDate,
                sourceTime = snooze.origTime,
            )
        }
        planned.sortWith(compareBy({ it.triggerAtMillis }, { it.id }))

        val manager = context.getSystemService(AlarmManager::class.java)
        val previous = store.readCodes()
        if (manager != null) cancelAll(context, manager, previous)
        val leads = MemoPlanner.planLead(board, now)
        val summaryAt = MemoPlanner.nextSummaryAt(now, store.readSummaryTime())
        val ids = planned.map { it.id } + leads.map { it.id } + listOf("maintenance", "summary")
        val codes = assignCodes(ids, previous)
        val exact = manager != null && canExact(context)
        if (manager != null) {
            for (alarm in planned) {
                scheduleFire(context, manager, codes.getValue(alarm.id), alarm, exact)
            }
            for (lead in leads) {
                scheduleLead(context, manager, codes.getValue(lead.id), lead, exact)
            }
            scheduleMaintenance(context, manager, codes.getValue("maintenance"), maintenanceAt(now), exact)
            scheduleSummary(context, manager, codes.getValue("summary"), summaryAt, exact)
        }
        store.writeCodes(codes)
        NextTaskNotice.update(context, if (board.nextTaskNotice) MemoPlanner.nextToday(board, now) else null)
        quietStaleRing(context, board)
        TodayWidget.updateAll(context)
        Log.i(TAG, "scheduled ${planned.size} alarms exact=$exact summary=${summaryAt.toLocalTime()}")
        return plannedMemosToJson(planned)
    }

    private fun readBoard(store: AlarmStore): TaskBoard {
        val json = store.readWork()
        if (json.isNullOrBlank()) return TaskBoard()
        return try {
            parseBoard(json)
        } catch (error: Exception) {
            Log.w(TAG, "task state could not be read", error)
            TaskBoard()
        }
    }

    private fun assignCodes(ids: List<String>, previous: Map<String, Int>): Map<String, Int> {
        val used = HashSet<Int>()
        val out = LinkedHashMap<String, Int>()
        var next = 1000
        for (id in ids) {
            var code = previous[id]
            if (code == null || code <= 0 || code in used) {
                while (next in used) next += 1
                code = next
                next += 1
            }
            used += code
            out[id] = code
        }
        return out
    }

    private fun cancelAll(context: Context, manager: AlarmManager, codes: Map<String, Int>) {
        for ((id, code) in codes) {
            val action = when {
                id == "maintenance" -> AlarmIntents.ACTION_MAINT
                id == "summary" -> AlarmIntents.ACTION_SUMMARY
                id.startsWith("p|") -> AlarmIntents.ACTION_LEAD
                else -> AlarmIntents.ACTION_FIRE
            }
            manager.cancel(pendingBroadcast(context, action, code))
        }
    }

    private fun scheduleFire(
        context: Context,
        manager: AlarmManager,
        requestCode: Int,
        alarm: PlannedMemoAlarm,
        exact: Boolean,
    ) {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(AlarmIntents.ACTION_FIRE).apply {
            putExtra(AlarmIntents.EXTRA_ID, alarm.id)
            putExtra(AlarmIntents.EXTRA_TITLE, alarm.title)
            putExtra(AlarmIntents.EXTRA_DETAIL, alarm.detail)
            putExtra(AlarmIntents.EXTRA_SOURCE, "memo")
            putExtra(AlarmIntents.EXTRA_KIND, alarm.kind)
            putExtra(AlarmIntents.EXTRA_ITEM, alarm.itemId)
            putExtra(AlarmIntents.EXTRA_TAB, alarm.tabId)
            putExtra(AlarmIntents.EXTRA_DATE, alarm.sourceDate.ifBlank { alarm.date })
            putExtra(AlarmIntents.EXTRA_TIME, alarm.sourceTime.ifBlank { alarm.time })
            putExtra(AlarmIntents.EXTRA_SILENT, alarm.silent)
        }
        val operation = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val show = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            if (exact && !alarm.silent) {
                manager.setAlarmClock(AlarmManager.AlarmClockInfo(alarm.triggerAtMillis, show), operation)
            } else if (exact) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarm.triggerAtMillis, operation)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarm.triggerAtMillis, operation)
            }
        } catch (error: SecurityException) {
            Log.w(TAG, "exact alarm was rejected", error)
            try {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarm.triggerAtMillis, operation)
            } catch (fallback: Exception) {
                Log.e(TAG, "could not schedule ${alarm.id}", fallback)
            }
        }
    }

    private fun scheduleMaintenance(
        context: Context,
        manager: AlarmManager,
        requestCode: Int,
        triggerAtMillis: Long,
        exact: Boolean,
    ) {
        val operation = pendingBroadcast(context, AlarmIntents.ACTION_MAINT, requestCode)
        try {
            if (exact) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
            }
        } catch (error: SecurityException) {
            Log.w(TAG, "maintenance alarm was rejected", error)
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
        }
    }

    private fun scheduleLead(
        context: Context,
        manager: AlarmManager,
        requestCode: Int,
        alarm: PlannedMemoAlarm,
        exact: Boolean,
    ) {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(AlarmIntents.ACTION_LEAD).apply {
            putExtra(AlarmIntents.EXTRA_TITLE, alarm.title)
            putExtra(AlarmIntents.EXTRA_ID, alarm.time)
            putExtra(AlarmIntents.EXTRA_LEAD, alarm.leadMinutes)
        }
        val operation = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            if (exact) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarm.triggerAtMillis, operation)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarm.triggerAtMillis, operation)
            }
        } catch (error: SecurityException) {
            Log.w(TAG, "advance alarm was rejected", error)
            try {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarm.triggerAtMillis, operation)
            } catch (fallback: Exception) {
                Log.e(TAG, "could not schedule ${alarm.id}", fallback)
            }
        }
    }

    private fun scheduleSummary(
        context: Context,
        manager: AlarmManager,
        requestCode: Int,
        whenAt: ZonedDateTime,
        exact: Boolean,
    ) {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(AlarmIntents.ACTION_SUMMARY).apply {
            putExtra(AlarmIntents.EXTRA_DATE, whenAt.toLocalDate().toString())
        }
        val operation = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val trigger = whenAt.toInstant().toEpochMilli()
        try {
            if (exact) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, operation)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, operation)
            }
        } catch (error: SecurityException) {
            Log.w(TAG, "summary alarm was rejected", error)
            try {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, operation)
            } catch (fallback: Exception) {
                Log.e(TAG, "could not schedule the daily summary", fallback)
            }
        }
    }

    private fun pendingBroadcast(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun quietStaleRing(context: Context, board: TaskBoard) {
        if (!AlarmRingService.ringIsStale(board)) return
        try {
            context.startService(Intent(context, AlarmRingService::class.java).setAction(AlarmIntents.ACTION_QUIET))
        } catch (error: Exception) {
            Log.w(TAG, "could not stop the ringing alarm", error)
        }
    }

    private fun StoredSnooze.toHold(): SnoozeHold {
        return SnoozeHold(
            id = id,
            atMillis = atMillis,
            kind = kind,
            itemId = itemId,
            tabId = tabId,
            origDate = origDate.ifBlank { null },
            origTime = origTime.ifBlank { null },
        )
    }

    private fun sameItem(snooze: StoredSnooze, kind: String, itemId: String, tabId: String): Boolean {
        return MemoPlanner.sameSnoozeItem(kind, itemId, tabId, snooze.kind, snooze.itemId, snooze.tabId)
    }

    private fun maintenanceAt(now: ZonedDateTime): Long {
        var at = now.toLocalDate().atTime(0, 10).atZone(MemoPlanner.ZONE)
        if (!at.toInstant().isAfter(now.toInstant())) at = at.plusDays(1)
        return at.toInstant().toEpochMilli()
    }
}
