package dev.nossam.zangyo.plan

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException

/**
 * Decides which alarms ring for the 就業 calendar.
 *
 * [alarmKind] is the rule that used to live in the web app:
 * leave (年休) and non-work days (no shift, or 休日 / off) produce none;
 * a day shift with the early flag uses the early set; night uses the night set.
 * Every time is on the same calendar day as the shift, in Asia/Tokyo.
 */
object AlarmPlanner {
    val ZONE: ZoneId = ZoneId.of("Asia/Tokyo")
    const val DEFAULT_HORIZON_DAYS = 21
    const val SNOOZE_MINUTES = 5
    private const val REMINDER_YEARS = 2L

    val KIND_LABELS = mapOf(
        "early" to "早出の昼勤",
        "day" to "通常の昼勤",
        "night" to "夜勤",
    )

    fun defaultAlarms(): Map<String, List<AlarmRow>> = mapOf(
        "early" to listOf(
            AlarmRow("目覚まし", "02:00"),
            AlarmRow("出勤", "04:20"),
            AlarmRow("移動", "05:55"),
            AlarmRow("休憩", "11:18"),
        ),
        "day" to listOf(
            AlarmRow("目覚まし", "02:15"),
            AlarmRow("出勤", "04:35"),
            AlarmRow("移動", "06:07"),
            AlarmRow("休憩", "11:18"),
        ),
        "night" to listOf(
            AlarmRow("目覚まし", "14:30"),
            AlarmRow("移動", "16:47"),
            AlarmRow("休憩", "21:59"),
        ),
    )

    fun alarmKind(data: WorkData, iso: String): String? {
        if (iso in data.leave) return null
        return when (data.shifts[iso]) {
            "night" -> "night"
            "day" -> if (iso in data.early) "early" else "day"
            else -> null
        }
    }

    fun snoozeTrigger(nowMillis: Long, minutes: Int = SNOOZE_MINUTES): Long =
        nowMillis + minutes * 60_000L

    fun plan(
        data: WorkData,
        reminders: List<Reminder>,
        now: ZonedDateTime,
        horizonDays: Int = DEFAULT_HORIZON_DAYS,
    ): List<PlannedAlarm> {
        require(horizonDays >= 1) { "horizonDays must be at least 1" }
        val nowZ = now.withZoneSameInstant(ZONE)
        val nowInstant = nowZ.toInstant()
        val start = nowZ.toLocalDate()
        val end = start.plusDays(horizonDays.toLong() - 1)
        val out = ArrayList<PlannedAlarm>()

        var date = start
        while (!date.isAfter(end)) {
            val iso = date.toString()
            val kind = alarmKind(data, iso)
            if (kind != null) {
                data.alarms[kind].orEmpty().forEachIndexed { index, row ->
                    val time = parseTime(row.time) ?: return@forEachIndexed
                    val whenAt = date.atTime(time).atZone(ZONE)
                    if (!whenAt.toInstant().isAfter(nowInstant)) return@forEachIndexed
                    val title = row.label.trim().ifEmpty { "アラーム" }
                    out += PlannedAlarm(
                        id = "s|$iso|$kind|$index|${row.time}|$title",
                        triggerAtMillis = whenAt.toInstant().toEpochMilli(),
                        date = iso,
                        time = row.time,
                        title = title,
                        detail = "${formatJapaneseDate(date)} ${KIND_LABELS[kind] ?: kind}",
                        source = kind,
                    )
                }
            }
            date = date.plusDays(1)
        }

        val reminderLimit = nowZ.plusYears(REMINDER_YEARS).toInstant()
        for (reminder in reminders) {
            val day = try {
                LocalDate.parse(reminder.date)
            } catch (_: DateTimeParseException) {
                continue
            }
            val time = parseTime(reminder.time) ?: continue
            val whenAt = day.atTime(time).atZone(ZONE)
            val instant = whenAt.toInstant()
            if (!instant.isAfter(nowInstant) || instant.isAfter(reminderLimit)) continue
            val title = reminder.name.trim().ifEmpty { "リマインド" }
            val stableId = reminder.id.ifBlank { "anon|${reminder.date}|${reminder.time}|$title" }
            out += PlannedAlarm(
                id = "r|$stableId|${reminder.date}|${reminder.time}",
                triggerAtMillis = instant.toEpochMilli(),
                date = reminder.date,
                time = reminder.time,
                title = title,
                detail = formatJapaneseDate(day),
                source = "reminder",
            )
        }

        return out.sortedWith(compareBy({ it.triggerAtMillis }, { it.id }))
    }

    fun formatJapaneseDate(date: LocalDate): String {
        val dow = listOf("月", "火", "水", "木", "金", "土", "日")[date.dayOfWeek.value - 1]
        return "${date.monthValue}月${date.dayOfMonth}日（$dow）"
    }

    fun parseTime(text: String): LocalTime? {
        if (!TIME.matches(text)) return null
        val hour = text.substring(0, 2).toInt()
        val minute = text.substring(3, 5).toInt()
        if (hour !in 0..23 || minute !in 0..59) return null
        return LocalTime.of(hour, minute)
    }
}

data class AlarmRow(val label: String, val time: String)

data class WorkData(
    val shifts: Map<String, String>,
    val early: Set<String>,
    val leave: Set<String>,
    val alarms: Map<String, List<AlarmRow>>,
)

data class Reminder(
    val id: String,
    val name: String,
    val date: String,
    val time: String,
)

data class PlannedAlarm(
    val id: String,
    val triggerAtMillis: Long,
    val date: String,
    val time: String,
    val title: String,
    val detail: String,
    val source: String,
)

private val ISO_DATE = Regex("""^\d{4}-\d{2}-\d{2}$""")
private val TIME = Regex("""^\d{2}:\d{2}$""")

fun parseWorkData(json: String): WorkData {
    val root = JSONObject(json)
    val shifts = linkedMapOf<String, String>()
    val shiftObj = root.optJSONObject("shifts")
    if (shiftObj != null) {
        val keys = shiftObj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (!isIsoDate(key)) continue
            when (val value = shiftObj.optString(key)) {
                "day", "night", "off" -> shifts[key] = value
            }
        }
    }
    return WorkData(
        shifts = shifts,
        early = readDates(root.opt("early")),
        leave = readDates(root.opt("leave")),
        alarms = readAlarms(root.opt("alarms")),
    )
}

fun parseReminders(json: String): List<Reminder> {
    val array = try {
        JSONArray(json)
    } catch (_: Exception) {
        return emptyList()
    }
    val out = ArrayList<Reminder>(array.length())
    for (i in 0 until array.length()) {
        val item = array.optJSONObject(i) ?: continue
        val date = item.optString("date")
        val time = item.optString("time")
        if (!isIsoDate(date) || AlarmPlanner.parseTime(time) == null) continue
        val name = item.optString("name")
        val id = item.optString("id").ifBlank { "anon|$date|$time|$name" }
        out += Reminder(id = id, name = name, date = date, time = time)
    }
    return out
}

private fun readAlarms(raw: Any?): Map<String, List<AlarmRow>> {
    val defaults = AlarmPlanner.defaultAlarms()
    if (raw !is JSONObject) return defaults
    return AlarmPlanner.KIND_LABELS.keys.associateWith { kind ->
        val value = raw.opt(kind)
        if (value is JSONArray) parseRows(value) else defaults.getValue(kind)
    }
}

private fun parseRows(array: JSONArray): List<AlarmRow> {
    val rows = ArrayList<AlarmRow>(array.length())
    for (i in 0 until array.length()) {
        val item = array.optJSONObject(i) ?: continue
        val time = item.optString("time")
        rows += AlarmRow(
            label = item.optString("label"),
            time = if (TIME.matches(time)) time else "",
        )
    }
    return rows
}

private fun readDates(raw: Any?): Set<String> {
    val values = when (raw) {
        is JSONArray -> (0 until raw.length()).map { raw.optString(it) }
        is String -> raw.split('.')
        else -> emptyList()
    }
    return values.filter(::isIsoDate).toSet()
}

private fun isIsoDate(text: String): Boolean {
    if (!ISO_DATE.matches(text)) return false
    return try {
        LocalDate.parse(text)
        true
    } catch (_: DateTimeParseException) {
        false
    }
}

fun remindersToJson(items: List<Reminder>): String {
    val array = JSONArray()
    for (item in items) {
        array.put(
            JSONObject()
                .put("id", item.id)
                .put("name", item.name)
                .put("date", item.date)
                .put("time", item.time),
        )
    }
    return array.toString()
}

fun plannedToJson(items: List<PlannedAlarm>, horizonDays: Int): String {
    val array = JSONArray()
    for (item in items) {
        array.put(
            JSONObject()
                .put("id", item.id)
                .put("at", Instant.ofEpochMilli(item.triggerAtMillis).atZone(AlarmPlanner.ZONE).toOffsetDateTime().toString())
                .put("date", item.date)
                .put("time", item.time)
                .put("title", item.title)
                .put("detail", item.detail)
                .put("source", item.source),
        )
    }
    return JSONObject()
        .put("horizonDays", horizonDays)
        .put("items", array)
        .toString()
}
