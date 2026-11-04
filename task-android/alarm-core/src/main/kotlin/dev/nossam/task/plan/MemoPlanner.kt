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
                itemId = memo.id,
                kind = KIND_MEMO,
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

    /** A postponed date replaces only the current occurrence. */
    fun shownDate(natural: LocalDate?, shift: String?): LocalDate? = parseDate(shift) ?: natural

    /** Memo rings unless turned off. Every other tab rings only when the setting is on. */
    fun ringsOn(board: TaskBoard, key: String): Boolean {
        val value = board.rings[key]
        return if (key == KIND_MEMO) value != false else value == true
    }

    /** Color keys present on each date. Kinds already removed by the user are absent from [board]. */
    fun marks(board: TaskBoard): Map<String, Set<String>> {
        val out = LinkedHashMap<String, LinkedHashSet<String>>()
        fun add(date: LocalDate?, kind: String) {
            if (date == null) return
            out.getOrPut(date.toString()) { LinkedHashSet() }.add(kind)
        }
        for (memo in board.memos) add(parseDate(memo.due), KIND_MEMO)
        for (item in board.cleaning) add(shownDate(nextCleanDate(item.done, item.cycle), item.shift), KIND_CLEAN)
        for (item in board.bousai) add(shownDate(parseDate(item.due), item.shift), KIND_BOUSAI)
        for (item in board.cars) add(shownDate(nextCarDate(item.done, item.months), item.shift), KIND_CAR)
        for (item in board.extras) add(shownDate(nextCarDate(item.done, item.months), item.shift), extraKind(item.tabId))
        return out
    }

    /** Every dated item on [iso], timed ones first, then untimed. */
    fun itemsOn(board: TaskBoard, iso: String): List<DayItem> {
        if (parseDate(iso) == null) return emptyList()
        val items = ArrayList<DayItem>()
        for (memo in board.memos) {
            if (memo.due != iso) continue
            items += DayItem(KIND_MEMO, memo.id, memo.text, time = memo.time, done = memo.done, label = "メモ")
        }
        for (item in board.cleaning) {
            if (shownDate(nextCleanDate(item.done, item.cycle), item.shift)?.toString() != iso) continue
            items += DayItem(KIND_CLEAN, item.id, item.title, time = item.time, tabId = "clean", label = "掃除", shifted = parseDate(item.shift) != null)
        }
        for (item in board.bousai) {
            if (shownDate(parseDate(item.due), item.shift)?.toString() != iso) continue
            items += DayItem(KIND_BOUSAI, item.id, item.title, time = item.time, tabId = "bousai", label = "防災", shifted = parseDate(item.shift) != null)
        }
        for (item in board.cars) {
            if (shownDate(nextCarDate(item.done, item.months), item.shift)?.toString() != iso) continue
            items += DayItem(KIND_CAR, item.id, item.title, time = item.time, tabId = item.tabId, label = "車", shifted = parseDate(item.shift) != null)
        }
        for (item in board.extras) {
            if (shownDate(nextCarDate(item.done, item.months), item.shift)?.toString() != iso) continue
            items += DayItem(extraKind(item.tabId), item.id, item.title, time = item.time, tabId = item.tabId, label = item.tabName, shifted = parseDate(item.shift) != null)
        }
        return items.sortedWith(
            compareBy<DayItem> { if (parseTime(it.time) == null) 1 else 0 }
                .thenBy { it.time ?: "" }
                .thenBy { it.title }
                .thenBy { it.kind }
                .thenBy { it.id },
        )
    }

    /**
     * Full-screen alarms. Memos ring by default. Other tabs ring only when that
     * tab is switched on and the item has a time. The date is the postponed one
     * when this occurrence was moved.
     */
    fun planAlerts(board: TaskBoard, now: ZonedDateTime): List<PlannedMemoAlarm> {
        val memoAlarms = if (ringsOn(board, KIND_MEMO)) plan(board.memos, now) else emptyList()
        val nowZ = now.withZoneSameInstant(ZONE)
        val nowInstant = nowZ.toInstant()
        val limit = nowZ.plusYears(2).toInstant()
        val extra = ArrayList<PlannedMemoAlarm>()
        fun add(ringKey: String, idPrefix: String, id: String, title: String, date: LocalDate?, timeText: String?) {
            if (!ringsOn(board, ringKey)) return
            val time = parseTime(timeText) ?: return
            val day = date ?: return
            val instant = day.atTime(time).atZone(ZONE).toInstant()
            if (!instant.isAfter(nowInstant) || instant.isAfter(limit)) return
            extra += PlannedMemoAlarm(
                id = "$idPrefix|$id|$day|$timeText",
                triggerAtMillis = instant.toEpochMilli(),
                date = day.toString(),
                time = timeText!!,
                title = title.ifBlank { "予定" },
                detail = "${formatJapaneseDate(day)} $timeText",
                itemId = id,
                kind = when (idPrefix) {
                    "c" -> KIND_CLEAN
                    "b" -> KIND_BOUSAI
                    else -> "tab"
                },
                tabId = ringKey,
            )
        }
        for (item in board.cleaning) {
            add("clean", "c", item.id, item.title, shownDate(nextCleanDate(item.done, item.cycle), item.shift), item.time)
        }
        for (item in board.bousai) {
            add("bousai", "b", item.id, item.title, shownDate(parseDate(item.due), item.shift), item.time)
        }
        for (item in board.cars) {
            add(item.tabId, "t", item.id, item.title, shownDate(nextCarDate(item.done, item.months), item.shift), item.time)
        }
        for (item in board.extras) {
            add(item.tabId, "t", item.id, item.title, shownDate(nextCarDate(item.done, item.months), item.shift), item.time)
        }
        return (memoAlarms + extra).sortedWith(compareBy({ it.triggerAtMillis }, { it.id }))
    }

    /** The next timed item still ahead today, among tabs that ring. */
    fun nextToday(board: TaskBoard, now: ZonedDateTime): PlannedMemoAlarm? {
        val today = now.withZoneSameInstant(ZONE).toLocalDate().toString()
        return planAlerts(board, now).firstOrNull { it.date == today }
    }

    /** A sounding notice before the usual alarm. Minutes come from the memo, or from the shared setting. */
    fun planLead(board: TaskBoard, now: ZonedDateTime): List<PlannedMemoAlarm> {
        val nowMillis = now.withZoneSameInstant(ZONE).toInstant().toEpochMilli()
        val out = ArrayList<PlannedMemoAlarm>()
        for (alarm in planAlerts(board, now)) {
            val minutes = leadFor(board, alarm)
            if (minutes <= 0) continue
            val at = alarm.triggerAtMillis - minutes * 60_000L
            if (at <= nowMillis) continue
            out += alarm.copy(
                id = "p|${alarm.id}",
                triggerAtMillis = at,
                detail = leadLabel(minutes),
                leadMinutes = minutes,
            )
        }
        return out
    }

    fun leadFor(board: TaskBoard, alarm: PlannedMemoAlarm): Int {
        if (alarm.kind == KIND_MEMO) {
            val own = board.memos.firstOrNull { it.id == alarm.itemId }?.lead
            if (own != null && own in LEAD_CHOICES) return own
        }
        return if (board.leadMinutes in LEAD_CHOICES) board.leadMinutes else DEFAULT_LEAD
    }

    fun leadLabel(minutes: Int): String = if (minutes == 60) "1時間前" else "${minutes}分前"

    fun nextTaskText(alarm: PlannedMemoAlarm): String = "${alarm.time} ${alarm.title}"

    fun nextTaskNotice(alarm: PlannedMemoAlarm): String = "次のタスク ${nextTaskText(alarm)}"

    fun extraKind(tabId: String) = "x:$tabId"

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

    private fun summaryLine(item: DayItem): String {
        val prefix = when (item.kind) {
            KIND_MEMO -> if (item.time != null) "${item.time} " else "時刻なし "
            else -> if (item.time != null) "${item.time} ${item.label} " else "${item.label} "
        }
        return (prefix + item.title).trim()
    }

    const val KIND_MEMO = "memo"
    const val KIND_CLEAN = "clean"
    const val KIND_BOUSAI = "bousai"
    const val KIND_CAR = "car"
    const val DEFAULT_LEAD = 10
    const val LATER_MINUTES = 10
    val LEAD_CHOICES = setOf(0, 5, 10, 15, 30, 60)

    /** Whether stopping the alarm should also mark the item done. */
    fun marksDone(stop: AlarmStop, doneOnStop: Boolean): Boolean = when (stop) {
        AlarmStop.DoneButton -> true
        AlarmStop.Later -> false
        AlarmStop.Other -> doneOnStop
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
    val lead: Int? = null,
)

enum class AlarmStop { DoneButton, Later, Other }

data class PlannedMemoAlarm(
    val id: String,
    val triggerAtMillis: Long,
    val date: String,
    val time: String,
    val title: String,
    val detail: String,
    val itemId: String = "",
    val kind: String = MemoPlanner.KIND_MEMO,
    val tabId: String = "",
    val leadMinutes: Int = 0,
)

data class TaskBoard(
    val memos: List<MemoEntry> = emptyList(),
    val cleaning: List<CleanEntry> = emptyList(),
    val bousai: List<BousaiEntry> = emptyList(),
    val cars: List<CarEntry> = emptyList(),
    val extras: List<ExtraEntry> = emptyList(),
    val rings: Map<String, Boolean> = emptyMap(),
    val leadMinutes: Int = MemoPlanner.DEFAULT_LEAD,
    val nextTaskNotice: Boolean = true,
    val doneOnStop: Boolean = false,
)

data class CleanEntry(
    val id: String,
    val title: String,
    val done: String?,
    val cycle: Int?,
    val shift: String? = null,
    val time: String? = null,
)

data class BousaiEntry(
    val id: String,
    val title: String,
    val due: String?,
    val shift: String? = null,
    val time: String? = null,
)

data class CarEntry(
    val id: String,
    val tabId: String,
    val title: String,
    val done: String?,
    val months: Int?,
    val shift: String? = null,
    val time: String? = null,
)

data class ExtraEntry(
    val tabId: String,
    val tabName: String,
    val id: String,
    val title: String,
    val done: String?,
    val months: Int?,
    val shift: String? = null,
    val time: String? = null,
)

data class DayItem(
    val kind: String,
    val id: String,
    val title: String,
    val time: String? = null,
    val done: Boolean = false,
    val tabId: String? = null,
    val label: String = "",
    val shifted: Boolean = false,
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
    return TaskBoard(
        memos = memos,
        cleaning = cleaning,
        bousai = bousai,
        cars = cars,
        extras = readExtras(root.optJSONArray("tabs")),
        rings = readRings(root.optJSONObject("rings")),
        leadMinutes = readLead(root.opt("leadMinutes")) ?: MemoPlanner.DEFAULT_LEAD,
        nextTaskNotice = if (root.has("nextTaskNotice") && root.opt("nextTaskNotice") is Boolean) {
            root.getBoolean("nextTaskNotice")
        } else {
            true
        },
        doneOnStop = root.has("doneOnStop") && root.opt("doneOnStop") is Boolean && root.getBoolean("doneOnStop"),
    )
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
            shift = item.optString("shift").takeIf { MemoPlanner.parseDate(it) != null },
            time = item.optString("time").takeIf { MemoPlanner.parseTime(it) != null },
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
            shift = item.optString("shift").takeIf { MemoPlanner.parseDate(it) != null },
            time = item.optString("time").takeIf { MemoPlanner.parseTime(it) != null },
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
                shift = item.optString("shift").takeIf { MemoPlanner.parseDate(it) != null },
                time = item.optString("time").takeIf { MemoPlanner.parseTime(it) != null },
            )
        }
    }
    return out
}

private fun readExtras(tabs: JSONArray?): List<ExtraEntry> {
    if (tabs == null) return emptyList()
    val out = ArrayList<ExtraEntry>()
    for (t in 0 until tabs.length()) {
        val tab = tabs.optJSONObject(t) ?: continue
        if (tab.optString("kind") == MemoPlanner.KIND_CAR) continue
        val tabId = tab.optString("id").ifBlank { "t$t" }
        val tabName = tab.optString("name").trim().ifEmpty { "タブ" }
        val items = tab.optJSONArray("items") ?: continue
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            out += ExtraEntry(
                tabId = tabId,
                tabName = tabName,
                id = item.optString("id").ifBlank { "x$i" },
                title = item.optString("name").trim().ifEmpty { tabName },
                done = item.optString("done").takeIf { MemoPlanner.parseDate(it) != null },
                months = jsonWholeNumber(item, "months"),
                shift = item.optString("shift").takeIf { MemoPlanner.parseDate(it) != null },
                time = item.optString("time").takeIf { MemoPlanner.parseTime(it) != null },
            )
        }
    }
    return out
}

private fun readLead(raw: Any?): Int? {
    val number = when (raw) {
        is Number -> raw.toInt()
        is String -> raw.toIntOrNull()
        else -> null
    } ?: return null
    return number.takeIf { it in MemoPlanner.LEAD_CHOICES }
}

fun completeOccurrence(json: String, kind: String, itemId: String, tabId: String, today: String): String {
    if (MemoPlanner.parseDate(today) == null || itemId.isBlank()) return json
    val root = try {
        JSONObject(json)
    } catch (_: Exception) {
        return json
    }
    when (kind) {
        MemoPlanner.KIND_MEMO -> markMemoDone(root.optJSONArray("memos"), itemId, today)
        MemoPlanner.KIND_CLEAN -> markChoreDone(root.optJSONArray("cleaning"), itemId, today)
        MemoPlanner.KIND_BOUSAI -> clearBousai(root.optJSONArray("bousai"), itemId)
        else -> markTabDone(root.optJSONArray("tabs"), itemId, tabId, today)
    }
    return root.toString()
}

private fun markMemoDone(array: JSONArray?, itemId: String, today: String) {
    if (array == null) return
    for (i in 0 until array.length()) {
        val item = array.optJSONObject(i) ?: continue
        if (item.optString("id") != itemId) continue
        if (MemoPlanner.parseDate(item.optString("done")) == null) item.put("done", today)
    }
}

private fun markChoreDone(array: JSONArray?, itemId: String, today: String) {
    if (array == null) return
    for (i in 0 until array.length()) {
        val item = array.optJSONObject(i) ?: continue
        if (item.optString("id") != itemId) continue
        item.put("done", today)
        item.put("shift", JSONObject.NULL)
    }
}

private fun clearBousai(array: JSONArray?, itemId: String) {
    if (array == null) return
    for (i in 0 until array.length()) {
        val item = array.optJSONObject(i) ?: continue
        if (item.optString("id") != itemId) continue
        item.put("due", JSONObject.NULL)
        item.put("shift", JSONObject.NULL)
    }
}

private fun markTabDone(tabs: JSONArray?, itemId: String, tabId: String, today: String) {
    if (tabs == null) return
    for (t in 0 until tabs.length()) {
        val tab = tabs.optJSONObject(t) ?: continue
        if (tabId.isNotBlank() && tab.optString("id") != tabId) continue
        val items = tab.optJSONArray("items") ?: continue
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            if (item.optString("id") != itemId) continue
            item.put("done", today)
            item.put("shift", JSONObject.NULL)
        }
    }
}

private fun readRings(obj: JSONObject?): Map<String, Boolean> {
    if (obj == null) return emptyMap()
    val out = LinkedHashMap<String, Boolean>()
    val keys = obj.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        if (obj.opt(key) is Boolean) out[key] = obj.getBoolean(key)
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
        out += MemoEntry(id = id, text = text, due = due, time = time, done = done, lead = readLead(item.opt("lead")))
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
