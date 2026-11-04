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
 * A day shift with the early flag uses the early set; night uses the night set.
 * 休み (off) and 年休 (leave) use their own sets, which start empty.
 * A per-day override replaces that day's usual times, or silences the day.
 * Every time is on the same calendar day, in Asia/Tokyo.
 */
object AlarmPlanner {
    val ZONE: ZoneId = ZoneId.of("Asia/Tokyo")
    const val DEFAULT_HORIZON_DAYS = 21
    const val SNOOZE_MINUTES = 5

    val KIND_LABELS = linkedMapOf(
        "early" to "早出の昼勤",
        "day" to "通常の昼勤",
        "night" to "夜勤",
        "off" to "休み",
        "leave" to "年休",
        "override" to "この日だけ",
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
        "off" to emptyList(),
        "leave" to emptyList(),
    )

    fun alarmKind(data: WorkData, iso: String): String? {
        val override = data.dayAlarms[iso]
        if (override != null) {
            if (override.mute) return null
            if (override.times.isNotEmpty()) return "override"
        }
        if (iso in data.leave) return "leave"
        return when (data.shifts[iso]) {
            "night" -> "night"
            "day" -> if (isEarly(data, iso)) "early" else "day"
            "off" -> "off"
            else -> null
        }
    }

    /** 1 is Monday … 7 is Sunday, matching the 「月〜日」 picker. */
    fun isEarly(data: WorkData, iso: String): Boolean {
        if (data.shifts[iso] != "day") return false
        if (iso in data.earlySkip) return false
        if (iso in data.early) return true
        val date = try {
            LocalDate.parse(iso)
        } catch (_: DateTimeParseException) {
            return false
        }
        return date.dayOfWeek.value in data.earlyWeekdays
    }

    fun snoozeTrigger(nowMillis: Long, minutes: Int = SNOOZE_MINUTES): Long =
        nowMillis + minutes * 60_000L

    fun plan(
        data: WorkData,
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
                val rows = if (kind == "override") data.dayAlarms[iso]?.times.orEmpty() else data.alarms[kind].orEmpty()
                rows.forEachIndexed { index, row ->
                    if (!row.enabled) return@forEachIndexed
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

        return out.sortedWith(compareBy({ it.triggerAtMillis }, { it.id }))
    }

    /** Drop skips whose alarm is gone, or whose day is no longer that shift type. */
    fun reconcileSkips(data: WorkData, planned: List<PlannedAlarm>, skips: Set<String>): Set<String> {
        val live = planned.associateBy { it.id }
        return skips.filter { id ->
            val alarm = live[id] ?: return@filter false
            alarmKind(data, alarm.date) == alarm.source
        }.toSet()
    }

    fun skipAlarm(skips: Set<String>, id: String): Set<String> = skips + id

    fun restoreAlarm(skips: Set<String>, id: String): Set<String> = skips - id

    fun alarmsToSchedule(planned: List<PlannedAlarm>, skips: Set<String>): List<PlannedAlarm> =
        planned.filter { it.id !in skips }

    /**
     * Days entered in one calendar year.
     * 早出の昼勤 is still a day shift. A blank day is not counted.
     * 年休 is its own list, so a day can be both a shift and 年休.
     */
    fun countYear(data: WorkData, year: Int): YearShiftCounts {
        if (year !in 1..9999) return YearShiftCounts(0, 0, 0, 0)
        val prefix = "%04d-".format(year)
        var day = 0
        var night = 0
        var off = 0
        for ((iso, shift) in data.shifts) {
            if (!iso.startsWith(prefix) || !isIsoDate(iso)) continue
            when (shift) {
                "day" -> day += 1
                "night" -> night += 1
                "off" -> off += 1
            }
        }
        val leave = data.leave.count { it.startsWith(prefix) && isIsoDate(it) }
        return YearShiftCounts(day, night, off, leave)
    }

    /** The soonest alarm that has not rung yet. Skipped ones are already absent from [alarms]. */
    fun nextUpcoming(alarms: List<PlannedAlarm>, nowMillis: Long): PlannedAlarm? {
        return alarms
            .filter { it.triggerAtMillis > nowMillis }
            .minWithOrNull(compareBy({ it.triggerAtMillis }, { it.id }))
    }

    /** 「11/5(木) 5:30 起床」 */
    fun nextAlarmText(alarm: PlannedAlarm): String {
        val date = LocalDate.parse(alarm.date)
        val dow = listOf("月", "火", "水", "木", "金", "土", "日")[date.dayOfWeek.value - 1]
        return "${date.monthValue}/${date.dayOfMonth}($dow) ${displayTime(alarm.time)} ${alarm.title}"
    }

    fun nextAlarmNotice(alarm: PlannedAlarm): String = "次のアラーム ${nextAlarmText(alarm)}"

    fun displayTime(hhmm: String): String {
        val parsed = parseTime(hhmm) ?: return hhmm
        return "${parsed.hour}:${parsed.minute.toString().padStart(2, '0')}"
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

data class YearShiftCounts(val day: Int, val night: Int, val off: Int, val leave: Int)

data class AlarmRow(val label: String, val time: String, val enabled: Boolean = true)

data class DayOverride(val mute: Boolean, val times: List<AlarmRow> = emptyList())

data class WorkData(
    val shifts: Map<String, String>,
    val early: Set<String>,
    val leave: Set<String>,
    val alarms: Map<String, List<AlarmRow>>,
    val dayAlarms: Map<String, DayOverride> = emptyMap(),
    val earlyWeekdays: Set<Int> = emptySet(),
    val earlySkip: Set<String> = emptySet(),
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
        dayAlarms = readDayAlarms(root.optJSONObject("dayAlarms")),
        earlyWeekdays = readWeekdays(root.opt("earlyWeekdays")),
        earlySkip = readDates(root.opt("earlySkip")),
    )
}

private fun readAlarms(raw: Any?): Map<String, List<AlarmRow>> {
    val defaults = AlarmPlanner.defaultAlarms()
    if (raw !is JSONObject) return defaults
    return AlarmPlanner.defaultAlarms().keys.associateWith { kind ->
        val value = raw.opt(kind)
        if (value is JSONArray) parseRows(value) else defaults.getValue(kind)
    }
}

private fun parseRows(array: JSONArray): List<AlarmRow> {
    val rows = ArrayList<AlarmRow>(array.length())
    for (i in 0 until array.length()) {
        val item = array.optJSONObject(i) ?: continue
        val time = item.optString("time")
        val enabled = if (item.has("enabled") && item.opt("enabled") is Boolean) item.getBoolean("enabled") else true
        rows += AlarmRow(
            label = item.optString("label"),
            time = if (TIME.matches(time)) time else "",
            enabled = enabled,
        )
    }
    return rows
}

private fun readDayAlarms(raw: JSONObject?): Map<String, DayOverride> {
    if (raw == null) return emptyMap()
    val out = linkedMapOf<String, DayOverride>()
    val keys = raw.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        if (!isIsoDate(key)) continue
        val item = raw.optJSONObject(key) ?: continue
        if (item.optBoolean("mute", false)) {
            out[key] = DayOverride(mute = true)
            continue
        }
        val times = item.optJSONArray("times")?.let { parseRows(it) }.orEmpty()
        if (times.isNotEmpty()) out[key] = DayOverride(mute = false, times = times)
    }
    return out
}

private fun readWeekdays(raw: Any?): Set<Int> {
    if (raw !is JSONArray) return emptySet()
    return (0 until raw.length()).mapNotNull { index ->
        val value = raw.opt(index)
        val number = (value as? Number)?.toInt() ?: return@mapNotNull null
        number.takeIf { it in 1..7 }
    }.toSet()
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

fun plannedToJson(items: List<PlannedAlarm>, horizonDays: Int, skipped: Set<String> = emptySet()): String {
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
                .put("source", item.source)
                .put("skipped", item.id in skipped),
        )
    }
    return JSONObject()
        .put("horizonDays", horizonDays)
        .put("items", array)
        .toString()
}
