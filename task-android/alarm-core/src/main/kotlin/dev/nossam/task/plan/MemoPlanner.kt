package dev.nossam.task.plan

import org.json.JSONArray
import org.json.JSONObject
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
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

    /** Next clean date: a recorded done-date plus a finite cycle, including 0 days. */
    fun nextCleanDate(done: String?, cycle: Int?): LocalDate? {
        val date = parseDate(done) ?: return null
        if (cycle == null) return null
        return try {
            date.plusDays(cycle.toLong())
        } catch (_: DateTimeException) {
            null
        }
    }

    /**
     * Next car date: done-date plus a non-zero month count.
     * The day is clamped the same way as the web app (`jt`), so Jan 31 plus one month is Feb 28 or 29.
     * Km-only items have no month count and therefore no calendar date.
     */
    fun nextCarDate(done: String?, months: Int?): LocalDate? {
        val date = parseDate(done) ?: return null
        if (months == null || months == 0) return null
        return try {
            addMonthsClamped(date, months)
        } catch (_: DateTimeException) {
            null
        }
    }

    fun addMonthsClamped(date: LocalDate, months: Int): LocalDate {
        val total = date.year * 12L + (date.monthValue - 1) + months
        val year = Math.floorDiv(total, 12L).toInt()
        val monthIndex = Math.floorMod(total, 12L).toInt()
        val length = YearMonth.of(year, monthIndex + 1).lengthOfMonth()
        return LocalDate.of(year, monthIndex + 1, minOf(date.dayOfMonth, length))
    }

    /** Color keys present on each date. Kinds already removed by the user are absent from [board]. */
    fun marks(board: TaskBoard): Map<String, Set<String>> {
        val out = LinkedHashMap<String, LinkedHashSet<String>>()
        fun add(date: String?, kind: String) {
            if (parseDate(date) == null) return
            out.getOrPut(date!!) { LinkedHashSet() }.add(kind)
        }
        for (memo in board.memos) add(memo.due, KIND_MEMO)
        for (item in board.cleaning) add(nextCleanDate(item.done, item.cycle)?.toString(), KIND_CLEAN)
        for (item in board.bousai) add(item.due, KIND_BOUSAI)
        for (item in board.cars) add(nextCarDate(item.done, item.months)?.toString(), KIND_CAR)
        return out
    }

    /** Memos for [iso] (timed first, untimed last), then 掃除, 防災, 車. */
    fun itemsOn(board: TaskBoard, iso: String): List<DayItem> {
        if (parseDate(iso) == null) return emptyList()
        val memos = board.memos.filter { it.due == iso }.sortedWith(
            compareBy<MemoEntry> { if (parseTime(it.time) == null) 1 else 0 }
                .thenBy { it.time ?: "" }
                .thenBy { it.text }
                .thenBy { it.id },
        ).map {
            DayItem(KIND_MEMO, it.id, it.text, time = it.time, done = it.done)
        }
        val cleaning = board.cleaning.filter { nextCleanDate(it.done, it.cycle)?.toString() == iso }
            .sortedWith(compareBy({ it.title }, { it.id }))
            .map { DayItem(KIND_CLEAN, it.id, it.title, tabId = "clean") }
        val bousai = board.bousai.filter { it.due == iso }
            .sortedWith(compareBy({ it.title }, { it.id }))
            .map { DayItem(KIND_BOUSAI, it.id, it.title, tabId = "bousai") }
        val cars = board.cars.filter { nextCarDate(it.done, it.months)?.toString() == iso }
            .sortedWith(compareBy({ it.title }, { it.id }))
            .map { DayItem(KIND_CAR, it.id, it.title, tabId = it.tabId) }
        return memos + cleaning + bousai + cars
    }

    /** One morning notification. Done memos are omitted. Null when the day has nothing to list. */
    fun dailySummary(board: TaskBoard, iso: String): DailySummary? {
        val date = parseDate(iso) ?: return null
        val lines = itemsOn(board, iso).filter { it.kind != KIND_MEMO || !it.done }.map { summaryLine(it) }
        if (lines.isEmpty()) return null
        return DailySummary(
            title = "${formatJapaneseDate(date)}の予定",
            body = lines.joinToString("\n"),
        )
    }

    /**
     * Next daily-summary instant in Asia/Tokyo.
     * Today at [timeText] when that moment is still ahead, otherwise tomorrow.
     * Missing or invalid times use 06:00.
     */
    fun nextSummaryAt(now: ZonedDateTime, timeText: String? = null): ZonedDateTime {
        val time = parseTime(timeText) ?: LocalTime.of(6, 0)
        val nowZ = now.withZoneSameInstant(ZONE)
        var at = nowZ.toLocalDate().atTime(time).atZone(ZONE)
        if (!at.isAfter(nowZ)) at = at.plusDays(1)
        return at
    }

    private fun summaryLine(item: DayItem): String = when (item.kind) {
        KIND_MEMO -> if (item.time != null) "${item.time} ${item.title}" else "時刻なし ${item.title}"
        KIND_CLEAN -> "掃除 ${item.title}"
        KIND_BOUSAI -> "防災 ${item.title}"
        KIND_CAR -> "車 ${item.title}"
        else -> item.title
    }

    const val KIND_MEMO = "memo"
    const val KIND_CLEAN = "clean"
    const val KIND_BOUSAI = "bousai"
    const val KIND_CAR = "car"

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

data class TaskBoard(
    val memos: List<MemoEntry> = emptyList(),
    val cleaning: List<CleanEntry> = emptyList(),
    val bousai: List<BousaiEntry> = emptyList(),
    val cars: List<CarEntry> = emptyList(),
)

data class CleanEntry(
    val id: String,
    val title: String,
    val done: String?,
    val cycle: Int?,
)

data class BousaiEntry(
    val id: String,
    val title: String,
    val due: String?,
)

data class CarEntry(
    val id: String,
    val tabId: String,
    val title: String,
    val done: String?,
    val months: Int?,
)

data class DayItem(
    val kind: String,
    val id: String,
    val title: String,
    val time: String? = null,
    val done: Boolean = false,
    val tabId: String? = null,
)

data class DailySummary(
    val title: String,
    val body: String,
)

private val ISO_DATE = Regex("""^\d{4}-\d{2}-\d{2}$""")
private val TIME = Regex("""^\d{2}:\d{2}$""")

fun parseMemos(json: String): List<MemoEntry> = parseBoard(json).memos

private val REMOVABLE = setOf(MemoPlanner.KIND_CLEAN, MemoPlanner.KIND_BOUSAI, MemoPlanner.KIND_CAR)

fun parseBoard(json: String): TaskBoard {
    val root = try {
        JSONObject(json)
    } catch (_: Exception) {
        return TaskBoard()
    }
    val removed = HashSet<String>()
    root.optJSONArray("removed")?.let { array ->
        for (i in 0 until array.length()) {
            val kind = array.optString(i)
            if (kind in REMOVABLE) removed += kind
        }
    }
    val memos = root.optJSONArray("memos")?.let { readMemos(it) } ?: emptyList()
    val cleaning = if (MemoPlanner.KIND_CLEAN in removed) {
        emptyList()
    } else {
        readCleaning(root.optJSONArray("cleaning"))
    }
    val bousai = if (MemoPlanner.KIND_BOUSAI in removed) {
        emptyList()
    } else {
        readBousai(root.optJSONArray("bousai"))
    }
    val cars = if (MemoPlanner.KIND_CAR in removed) {
        emptyList()
    } else {
        readCars(root.optJSONArray("tabs"))
    }
    return TaskBoard(memos = memos, cleaning = cleaning, bousai = bousai, cars = cars)
}

private fun readCleaning(array: JSONArray?): List<CleanEntry> {
    if (array == null) return emptyList()
    val out = ArrayList<CleanEntry>(array.length())
    for (i in 0 until array.length()) {
        val item = array.optJSONObject(i) ?: continue
        val place = item.optString("place").trim()
        val task = item.optString("task").trim()
        val title = listOf(place, task).filter { it.isNotEmpty() }.joinToString(" ").ifEmpty { "掃除" }
        val done = item.optString("done").takeIf { MemoPlanner.parseDate(it) != null }
        out += CleanEntry(
            id = item.optString("id").ifBlank { "c$i" },
            title = title,
            done = done,
            cycle = jsonWholeNumber(item, "cycle"),
        )
    }
    return out
}

private fun readBousai(array: JSONArray?): List<BousaiEntry> {
    if (array == null) return emptyList()
    val out = ArrayList<BousaiEntry>(array.length())
    for (i in 0 until array.length()) {
        val item = array.optJSONObject(i) ?: continue
        val name = item.optString("item").trim().ifEmpty { "防災グッズ" }
        val qty = item.optString("qty").trim()
        val title = if (qty.isEmpty()) name else "$name（$qty）"
        out += BousaiEntry(
            id = item.optString("id").ifBlank { "b$i" },
            title = title,
            due = item.optString("due").takeIf { MemoPlanner.parseDate(it) != null },
        )
    }
    return out
}

private fun readCars(tabs: JSONArray?): List<CarEntry> {
    if (tabs == null) return emptyList()
    val out = ArrayList<CarEntry>()
    for (t in 0 until tabs.length()) {
        val tab = tabs.optJSONObject(t) ?: continue
        if (tab.optString("kind") != MemoPlanner.KIND_CAR) continue
        val tabId = tab.optString("id").ifBlank { "car" }
        val items = tab.optJSONArray("items") ?: continue
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            out += CarEntry(
                id = item.optString("id").ifBlank { "car$i" },
                tabId = tabId,
                title = item.optString("name").trim().ifEmpty { "車" },
                done = item.optString("done").takeIf { MemoPlanner.parseDate(it) != null },
                months = jsonWholeNumber(item, "months"),
            )
        }
    }
    return out
}

private fun jsonWholeNumber(obj: JSONObject, key: String): Int? {
    if (!obj.has(key) || obj.isNull(key)) return null
    val value = obj.get(key)
    if (value !is Number) return null
    val asDouble = value.toDouble()
    if (!asDouble.isFinite() || asDouble % 1.0 != 0.0) return null
    val asLong = asDouble.toLong()
    if (asLong !in Int.MIN_VALUE..Int.MAX_VALUE) return null
    return asLong.toInt()
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
