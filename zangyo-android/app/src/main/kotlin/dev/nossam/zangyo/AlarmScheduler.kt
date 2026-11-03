package dev.nossam.zangyo

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import dev.nossam.zangyo.plan.AlarmPlanner
import dev.nossam.zangyo.plan.PlannedAlarm
import dev.nossam.zangyo.plan.WorkData
import dev.nossam.zangyo.plan.parseWorkData
import dev.nossam.zangyo.plan.plannedToJson
import java.time.Instant
import java.time.ZonedDateTime
import java.util.UUID
import java.util.concurrent.Executors

internal object UiBridge {
    var scheduleSink: ((String) -> Unit)? = null
    var permissionSink: ((String) -> Unit)? = null
    var lastSchedule: String? = null

    fun publishSchedule(json: String) {
        lastSchedule = json
        val sink = scheduleSink ?: return
        android.os.Handler(android.os.Looper.getMainLooper()).post { sink(json) }
    }
}

internal object AlarmScheduler {
    private const val TAG = "ZangyoAlarm"
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

    fun snooze(context: Context, title: String, detail: String) {
        val app = context.applicationContext
        val schedule = synchronized(lock) {
            val store = AlarmStore(app)
            val at = AlarmPlanner.snoozeTrigger(System.currentTimeMillis())
            val kept = store.readSnoozes().filter { it.atMillis > System.currentTimeMillis() + 5_000L }
            store.writeSnoozes(
                kept + StoredSnooze(
                    id = UUID.randomUUID().toString(),
                    atMillis = at,
                    title = title.ifBlank { "アラーム" },
                    detail = detail,
                ),
            )
            rebuild(app)
        }
        UiBridge.publishSchedule(schedule)
    }

    fun reschedule(context: Context) {
        val app = context.applicationContext
        val schedule = synchronized(lock) { rebuild(app) }
        UiBridge.publishSchedule(schedule)
    }

    fun setAlarmSkipped(context: Context, id: String, skipped: Boolean) {
        if (!id.startsWith("s|")) return
        val app = context.applicationContext
        val schedule = synchronized(lock) {
            val store = AlarmStore(app)
            val data = readWork(store)
            val planned = AlarmPlanner.plan(data, ZonedDateTime.now(AlarmPlanner.ZONE), AlarmPlanner.DEFAULT_HORIZON_DAYS)
            if (planned.none { it.id == id }) return@synchronized rebuild(app)
            var skips = AlarmPlanner.reconcileSkips(data, planned, store.readSkips())
            skips = if (skipped) AlarmPlanner.skipAlarm(skips, id) else AlarmPlanner.restoreAlarm(skips, id)
            store.writeSkips(skips)
            rebuild(app)
        }
        UiBridge.publishSchedule(schedule)
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
        val data = readWork(store)
        val now = ZonedDateTime.now(AlarmPlanner.ZONE)
        val shiftPlan = AlarmPlanner.plan(data, now, AlarmPlanner.DEFAULT_HORIZON_DAYS)
        val skips = AlarmPlanner.reconcileSkips(data, shiftPlan, store.readSkips())
        store.writeSkips(skips)
        val planned = AlarmPlanner.alarmsToSchedule(shiftPlan, skips).toMutableList()
        val snoozes = store.readSnoozes().filter { it.atMillis > System.currentTimeMillis() + 5_000L }
        store.writeSnoozes(snoozes)
        for (snooze in snoozes) {
            val zoned = Instant.ofEpochMilli(snooze.atMillis).atZone(AlarmPlanner.ZONE)
            planned += PlannedAlarm(
                id = "z|${snooze.id}",
                triggerAtMillis = snooze.atMillis,
                date = zoned.toLocalDate().toString(),
                time = "%02d:%02d".format(zoned.hour, zoned.minute),
                title = snooze.title,
                detail = snooze.detail.ifBlank { "5分後にもう一度" },
                source = "snooze",
            )
        }
        planned.sortWith(compareBy({ it.triggerAtMillis }, { it.id }))

        val manager = context.getSystemService(AlarmManager::class.java)
        val previous = store.readCodes()
        if (manager != null) cancelAll(context, manager, previous)
        val ids = planned.map { it.id } + "maintenance"
        val codes = assignCodes(ids, previous)
        val exact = manager != null && canExact(context)
        if (manager != null) {
            for (alarm in planned) {
                scheduleFire(context, manager, codes.getValue(alarm.id), alarm, exact)
            }
            scheduleMaintenance(context, manager, codes.getValue("maintenance"), maintenanceAt(now), exact)
        }
        store.writeCodes(codes)
        Log.i(TAG, "scheduled ${planned.size} alarms exact=$exact skipped=${skips.size}")
        val shown = (shiftPlan + planned.filter { it.source == "snooze" })
            .sortedWith(compareBy({ it.triggerAtMillis }, { it.id }))
        return plannedToJson(shown, AlarmPlanner.DEFAULT_HORIZON_DAYS, skips)
    }

    private fun readWork(store: AlarmStore): WorkData {
        val json = store.readWork()
        if (json.isNullOrBlank()) {
            return WorkData(emptyMap(), emptySet(), emptySet(), AlarmPlanner.defaultAlarms())
        }
        return try {
            parseWorkData(json)
        } catch (error: Exception) {
            Log.w(TAG, "work state could not be read", error)
            WorkData(emptyMap(), emptySet(), emptySet(), AlarmPlanner.defaultAlarms())
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
            val action = if (id == "maintenance") AlarmIntents.ACTION_MAINT else AlarmIntents.ACTION_FIRE
            manager.cancel(pendingBroadcast(context, action, code))
        }
    }

    private fun scheduleFire(
        context: Context,
        manager: AlarmManager,
        requestCode: Int,
        alarm: PlannedAlarm,
        exact: Boolean,
    ) {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(AlarmIntents.ACTION_FIRE).apply {
            putExtra(AlarmIntents.EXTRA_ID, alarm.id)
            putExtra(AlarmIntents.EXTRA_TITLE, alarm.title)
            putExtra(AlarmIntents.EXTRA_DETAIL, alarm.detail)
            putExtra(AlarmIntents.EXTRA_SOURCE, alarm.source)
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
            if (exact) {
                manager.setAlarmClock(AlarmManager.AlarmClockInfo(alarm.triggerAtMillis, show), operation)
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

    private fun pendingBroadcast(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun maintenanceAt(now: ZonedDateTime): Long {
        var at = now.toLocalDate().atTime(0, 10).atZone(AlarmPlanner.ZONE)
        if (!at.toInstant().isAfter(now.toInstant())) at = at.plusDays(1)
        return at.toInstant().toEpochMilli()
    }
}
