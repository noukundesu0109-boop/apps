package dev.nossam.task.voice

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

data class SpokenMemo(
    val content: String,
    val date: LocalDate?,
    val time: LocalTime?,
)

/**
 * Turns a spoken Japanese memo into a date, a time, and the remaining words.
 * Missing pieces stay empty: 「病院」 is a memo with no date.
 */
object VoiceMemoParser {
    val ZONE: ZoneId = ZoneId.of("Asia/Tokyo")

    private val NUM = """(?:[0-9]{1,3}|[〇零一二三四五六七八九十]{1,4})"""
    private val DOW = """(?:[月火水木金土日]曜(?:日)?|(?:げつ|か|すい|もく|きん|ど|にち)ようび)"""
    private val CLOCK = Regex("""(?:(午前|午後|朝|夜|ごぜん|ごご|あさ|よる)\s*)?($NUM)(?:時|じ)(?!間)(?:(半)|($NUM)(?:分|ふん|ぷん))?""")
    private val HOURS_LATER = Regex("""($NUM)\s*時間後""")
    private val MINUTES_LATER = Regex("""($NUM)\s*分後""")
    private val RELATIVE_DAY = Regex("""今日|明日|明後日|きょう|あした|あす|あさって""")
    private val NEXT_WEEK = Regex("""来週の?($DOW)""")
    private val WEEKDAY = Regex(DOW)
    private val MONTH_DAY = Regex("""($NUM)月($NUM)日""")
    private val DAY_ONLY = Regex("""($NUM)日""")

    fun parse(spoken: String, now: ZonedDateTime): SpokenMemo {
        val nowZ = now.withZoneSameInstant(ZONE)
        val text = normalize(spoken)
        var date: LocalDate? = null
        var time: LocalTime? = null
        var timeLocked = false
        val content = StringBuilder()
        var index = 0
        while (index < text.length) {
            val hours = matchAt(HOURS_LATER, text, index)
            val minutes = matchAt(MINUTES_LATER, text, index)
            val relative = if (hours == null && minutes == null) matchAt(RELATIVE_DAY, text, index) else null
            val nextWeek = if (hours == null && minutes == null && relative == null) matchAt(NEXT_WEEK, text, index) else null
            val weekday = if (hours == null && minutes == null && relative == null && nextWeek == null) matchAt(WEEKDAY, text, index) else null
            val monthDay = if (listOf(hours, minutes, relative, nextWeek, weekday).all { it == null }) matchAt(MONTH_DAY, text, index) else null
            val dayOnly = if (listOf(hours, minutes, relative, nextWeek, weekday, monthDay).all { it == null }) matchAt(DAY_ONLY, text, index) else null
            val clock = if (hours == null && minutes == null) matchAt(CLOCK, text, index) else null
            val found = hours ?: minutes ?: relative ?: nextWeek ?: weekday ?: monthDay ?: dayOnly ?: clock
            if (found == null) {
                content.append(text[index])
                index += 1
                continue
            }
            when {
                hours != null -> {
                    val amount = readNumber(hours.groupValues[1])
                    if (amount != null && amount in 0..240) {
                        val at = nowZ.plusHours(amount.toLong()).withSecond(0).withNano(0)
                        date = at.toLocalDate()
                        time = at.toLocalTime().withSecond(0)
                        timeLocked = true
                    } else {
                        content.append(found.value)
                    }
                }
                minutes != null -> {
                    val amount = readNumber(minutes.groupValues[1])
                    if (amount != null && amount in 0..24 * 60) {
                        val at = nowZ.plusMinutes(amount.toLong()).withSecond(0).withNano(0)
                        date = at.toLocalDate()
                        time = at.toLocalTime().withSecond(0)
                        timeLocked = true
                    } else {
                        content.append(found.value)
                    }
                }
                relative != null -> {
                    if (date == null) date = relativeDay(found.value, nowZ.toLocalDate())
                }
                nextWeek != null -> {
                    val dow = dayOfWeek(nextWeek.groupValues[1])
                    if (dow != null && date == null) date = weekdayDate(nowZ.toLocalDate(), dow, nextWeek = true)
                }
                weekday != null -> {
                    val dow = dayOfWeek(found.value)
                    if (dow != null && date == null) date = weekdayDate(nowZ.toLocalDate(), dow, nextWeek = false)
                }
                monthDay != null -> {
                    val month = readNumber(monthDay.groupValues[1])
                    val day = readNumber(monthDay.groupValues[2])
                    if (month != null && day != null && date == null) {
                        date = monthDayDate(nowZ.toLocalDate(), month, day) ?: date
                    }
                }
                dayOnly != null -> {
                    val day = readNumber(dayOnly.groupValues[1])
                    if (day != null && date == null) date = dayOfMonth(nowZ.toLocalDate(), day) ?: date
                }
                clock != null && !timeLocked -> {
                    val hour = readNumber(clock.groupValues[2])
                    val minute = when {
                        clock.groupValues[3] == "半" -> 30
                        clock.groupValues[4].isNotEmpty() -> readNumber(clock.groupValues[4])
                        else -> 0
                    }
                    if (hour != null && minute != null) {
                        time = clockTime(clock.groupValues[1], hour, minute) ?: time
                    }
                }
            }
            index = found.range.last + 1
            index = consumeConnector(text, index)
        }
        return SpokenMemo(cleanContent(content.toString()), date, time)
    }

    private fun matchAt(regex: Regex, text: String, index: Int): MatchResult? {
        val found = regex.find(text, index) ?: return null
        return if (found.range.first == index) found else null
    }

    private fun consumeConnector(text: String, start: Int): Int {
        val rest = text.substring(start)
        val connector = Regex("""^(?:までに|に|の|で)""").find(rest) ?: return start
        return start + connector.value.length
    }

    private fun normalize(raw: String): String {
        val mapped = buildString(raw.length) {
            for (ch in raw.trim()) {
                append(
                    when (ch) {
                        in '０'..'９' -> '0' + (ch - '０')
                        '　' -> ' '
                        else -> ch
                    },
                )
            }
        }
        return mapped.replace(Regex("""\s+"""), "")
    }

    internal fun readNumber(token: String): Int? {
        if (token.isEmpty()) return null
        if (token.all { it.isDigit() }) return token.toIntOrNull()
        val digit = mapOf(
            '〇' to 0, '零' to 0, '一' to 1, '二' to 2, '三' to 3, '四' to 4,
            '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9,
        )
        if (token == "十") return 10
        val ten = token.indexOf('十')
        if (ten >= 0) {
            val tens = if (ten == 0) 1 else digit[token[ten - 1]] ?: return null
            if (ten > 1) return null
            val ones = if (ten == token.lastIndex) 0 else digit[token[ten + 1]] ?: return null
            if (token.length > ten + 2) return null
            return tens * 10 + ones
        }
        if (token.length == 1) return digit[token[0]]
        return null
    }

    private fun relativeDay(word: String, today: LocalDate): LocalDate = when (word) {
        "今日", "きょう" -> today
        "明日", "あした", "あす" -> today.plusDays(1)
        else -> today.plusDays(2)
    }

    private fun dayOfWeek(phrase: String): DayOfWeek? {
        val names = listOf(
            "月" to DayOfWeek.MONDAY, "げつ" to DayOfWeek.MONDAY,
            "火" to DayOfWeek.TUESDAY, "か" to DayOfWeek.TUESDAY,
            "水" to DayOfWeek.WEDNESDAY, "すい" to DayOfWeek.WEDNESDAY,
            "木" to DayOfWeek.THURSDAY, "もく" to DayOfWeek.THURSDAY,
            "金" to DayOfWeek.FRIDAY, "きん" to DayOfWeek.FRIDAY,
            "土" to DayOfWeek.SATURDAY, "ど" to DayOfWeek.SATURDAY,
            "日" to DayOfWeek.SUNDAY, "にち" to DayOfWeek.SUNDAY,
        )
        return names.firstOrNull { (name, _) -> phrase.startsWith(name) }?.second
    }

    private fun weekdayDate(today: LocalDate, target: DayOfWeek, nextWeek: Boolean): LocalDate {
        val thisMonday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val base = if (nextWeek) thisMonday.plusWeeks(1) else today
        if (!nextWeek) {
            val delta = (target.value - today.dayOfWeek.value + 7) % 7
            return today.plusDays(delta.toLong())
        }
        return base.plusDays((target.value - 1).toLong())
    }

    private fun monthDayDate(today: LocalDate, month: Int, day: Int): LocalDate? {
        if (month !in 1..12) return null
        val thisYear = dateOrNull(today.year, month, day) ?: return null
        return if (thisYear.isBefore(today)) dateOrNull(today.year + 1, month, day) else thisYear
    }

    private fun dayOfMonth(today: LocalDate, day: Int): LocalDate? {
        val thisMonth = dateOrNull(today.year, today.monthValue, day)
        if (thisMonth != null && !thisMonth.isBefore(today)) return thisMonth
        val next = today.plusMonths(1)
        return dateOrNull(next.year, next.monthValue, day)
    }

    private fun dateOrNull(year: Int, month: Int, day: Int): LocalDate? {
        if (month !in 1..12 || day !in 1..31) return null
        val length = YearMonth.of(year, month).lengthOfMonth()
        if (day > length) return null
        return LocalDate.of(year, month, day)
    }

    private fun clockTime(marker: String, hour: Int, minute: Int): LocalTime? {
        if (minute !in 0..59) return null
        var adjusted = hour
        when (marker) {
            "午後", "ごご", "夜", "よる" -> if (adjusted in 1..11) adjusted += 12 else if (adjusted == 12 && (marker == "夜" || marker == "よる")) adjusted = 0
            "午前", "ごぜん", "朝", "あさ" -> if (adjusted == 12) adjusted = 0
        }
        if (adjusted == 24 && minute == 0) return LocalTime.MIDNIGHT
        if (adjusted !in 0..23) return null
        return LocalTime.of(adjusted, minute)
    }

    private fun cleanContent(raw: String): String {
        var text = raw.replace(Regex("""\s+"""), " ").trim()
        val edge = Regex("""^(?:までに|に|の|で)+|(?:までに|に|の|で)+$""")
        while (true) {
            val next = edge.replace(text, "").trim()
            if (next == text) return text
            text = next
        }
    }
}
