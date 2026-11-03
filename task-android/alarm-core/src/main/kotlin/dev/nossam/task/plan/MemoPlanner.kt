package dev.nossam.task.plan

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException

/**
 * Memos already have an optional date (`due`). A time is optional.
 * An alarm is scheduled only when the memo is not done and both a date and a
 * valid time are set, on that same calendar day in Asia/Tokyo.
 */
object MemoPlanner {
    val ZONE: ZoneId = ZoneId.of("Asia/Tokyo")
    const val SNOOZE_MINUTES = 5
    private const val HORIZON_YEARS = 2L

    fun snoozeTrigger(nowMillis: Long, minutes: Int = SNOOZE_MINUTES): Long =
        nowMillis + minutes * 60_000L

    fun plan(memos: List<MemoEntry>, now: ZonedDateTime): List<PlannedMemoAlarm> {
        val nowZ = now.withZoneSameInstant(ZONE)
        val nowInstant = nowZ.toInstant()
        val limit = nowZ.plusYears(HORIZON_YEARS).toInstant()
        val out = ArrayList<PlannedMemoAlarm>()
        for (memo in memos) {
            if (memo.done) continue
            val date = parseDate(memo.due) ?: continue
            val time = parseTime(memo.time) ?: continue
            val whenAt = date.atTime(time).atZone(ZONE)
            val instant = whenAt.toInstant()
            if (!instant.isAfter(nowInstant) || instant.isAfter(limit)) continue
            val title = memo.text.trim().ifEmpty { "メモ" }
            out += PlannedMemoAlarm(
                id = "m|${memo.id}|${date}|${memo.time}",
                triggerAtMillis = instant.toEpochMilli(),
                date = date.toString(),
                time = memo.time!!,
                title = title,
                detail = "${formatJapaneseDate(date)} ${memo.time}",
            )
        }
        return out.sortedWith(compareBy({ it.triggerAtMillis }, { it.id }))
    }

    fun markedDates(memos: List<MemoEntry>): Set<String> =
        memos.mapNotNull { parseDate(it.due)?.toString() }.toSet()

    fun entriesOn(memos: List<MemoEntry>, iso: String): List<MemoEntry> {
        if (parseDate(iso) == null) return emptyList()
        return memos.filter { it.due == iso }.sortedWith(
            compareBy<MemoEntry> { if (parseTime(it.time) == null) 1 else 0 }
                .thenBy { it.time ?: "" }
                .thenBy { it.text },
        )
    }

    fun formatJapaneseDate(date: LocalDate): String {
        val dow = listOf("日", "月", "火", "水", "木", "金", "土")[date.dayOfWeek.value % 7]
        return "${date.monthValue}月${date.dayOfMonth}日（$dow）"
    }

    fun parseTime(text: String?): LocalTime? {
        if (text == null || !TIME.matches(text)) return null
        val hour = text.substring(0, 2).toInt()
        val minute = text.substring(3, 5).toInt()
        if (hour !in 0..23 || minute !in 0..59) return null
        return LocalTime.of(hour, minute)
    }

    fun parseDate(text: String?): LocalDate? {
        if (text == null || !ISO_DATE.matches(text)) return null
        return try {
            LocalDate.parse(text)
        } catch (_: DateTimeParseException) {
            null
        }
    }
}

data class MemoEntry(
    val id: String,
    val text: String,
    val due: String?,
    val time: String?,
    val done: Boolean,
)

data class PlannedMemoAlarm(
    val id: String,
    val triggerAtMillis: Long,
    val date: String,
    val time: String,
    val title: String,
    val detail: String,
)

private val ISO_DATE = Regex("""^\d{4}-\d{2}-\d{2}$""")
private val TIME = Regex("""^\d{2}:\d{2}$""")

fun parseMemos(json: String): List<MemoEntry> {
    val root = try {
        JSONObject(json)
    } catch (_: Exception) {
        return emptyList()
    }
    val array = root.optJSONArray("memos") ?: return emptyList()
    return readMemos(array)
}

private fun readMemos(array: JSONArray): List<MemoEntry> {
    val out = ArrayList<MemoEntry>(array.length())
    for (i in 0 until array.length()) {
        val item = array.optJSONObject(i) ?: continue
        val text = item.optString("text").trim()
        if (text.isEmpty()) continue
        val due = item.optString("due").takeIf { MemoPlanner.parseDate(it) != null }
        val time = item.optString("time").takeIf { MemoPlanner.parseTime(it) != null }
        val done = item.optString("done").let { MemoPlanner.parseDate(it) != null }
        val id = item.optString("id").ifBlank { "m$i" }
        out += MemoEntry(id = id, text = text, due = due, time = time, done = done)
    }
    return out
}

fun plannedMemosToJson(items: List<PlannedMemoAlarm>): String {
    val array = JSONArray()
    for (item in items) {
        array.put(
            JSONObject()
                .put("id", item.id)
                .put("at", Instant.ofEpochMilli(item.triggerAtMillis).atZone(MemoPlanner.ZONE).toOffsetDateTime().toString())
                .put("date", item.date)
                .put("time", item.time)
                .put("title", item.title)
                .put("detail", item.detail),
        )
    }
    return JSONObject().put("items", array).toString()
}
