package dev.nossam.task.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class WidgetWindowTest {
    private val zone = ZoneId.of("Asia/Tokyo")

    @Test
    fun timedItemsCoverTheNext24HoursAndEarlierToday() {
        val now = at("2026-11-16", 10, 0)
        val lines = MemoPlanner.widgetWindow(board(), now)
        assertEquals(listOf("日付だけ", "朝", "午後", "明日だけ", "明日の朝"), lines.map { it.item.title })
        assertTrue(lines.none { it.item.title == "完了" || it.item.title == "明日の遅い" || it.item.title == "昨日" })
    }

    @Test
    fun untimedItemShowsFromMidnightTheDayBefore() {
        val before = at("2026-11-14", 23, 59)
        assertTrue(MemoPlanner.widgetWindow(board(), before).none { it.item.title == "日付だけ" })
        val open = at("2026-11-15", 0, 0)
        assertTrue(MemoPlanner.widgetWindow(board(), open).any { it.item.title == "日付だけ" })
        val during = at("2026-11-16", 10, 0)
        val todayUntimed = MemoPlanner.widgetWindow(board(), during).first { it.item.title == "日付だけ" }
        assertEquals(LocalDate.parse("2026-11-16").atStartOfDay(zone).toInstant(), todayUntimed.at.toInstant())
        val after = at("2026-11-17", 0, 0)
        assertTrue(MemoPlanner.widgetWindow(board(), after).none { it.item.title == "日付だけ" })
    }

    @Test
    fun tomorrowIsLabeledAndTodayIsNot() {
        val lines = MemoPlanner.widgetWindow(board(), at("2026-11-16", 10, 0))
        val today = lines.first { it.item.title == "午後" }
        val tomorrow = lines.first { it.item.title == "明日の朝" }
        val tomorrowUntimed = lines.first { it.item.title == "明日だけ" }
        assertEquals("15:00", today.timeText)
        assertEquals("午後", today.titleText)
        assertEquals("", tomorrow.timeText)
        assertEquals("明日 09:00 明日の朝", tomorrow.titleText)
        assertEquals("明日 明日だけ", tomorrowUntimed.titleText)
    }

    @Test
    fun sortsByEffectiveTimeWithUntimedAtMidnight() {
        val lines = MemoPlanner.widgetWindow(board(), at("2026-11-16", 10, 0))
        assertEquals(
            listOf("2026-11-16T00:00", "2026-11-16T08:10", "2026-11-16T15:00", "2026-11-17T00:00", "2026-11-17T09:00"),
            lines.map { it.at.toLocalDateTime().toString() },
        )
        assertTrue(lines.indexOfFirst { it.item.title == "日付だけ" } < lines.indexOfFirst { it.item.title == "朝" })
        assertTrue(lines.indexOfFirst { it.item.title == "明日だけ" } < lines.indexOfFirst { it.item.title == "明日の朝" })
    }

    @Test
    fun doneItemsStayOut() {
        val lines = MemoPlanner.widgetWindow(board(), at("2026-11-16", 10, 0))
        assertTrue(lines.none { it.item.done || it.item.title == "完了" })
    }

    @Test
    fun refreshIsTheNextBoundaryAndNeverMoreThanAnHour() {
        val soon = MemoPlanner.nextWidgetRefresh(board(), at("2026-11-16", 8, 40))
        assertEquals(at("2026-11-16", 9, 0), soon)
        val quiet = MemoPlanner.nextWidgetRefresh(TaskBoard(), at("2026-11-16", 10, 0))
        assertEquals(at("2026-11-16", 11, 0), quiet)
        val nearMidnight = MemoPlanner.nextWidgetRefresh(TaskBoard(), at("2026-11-16", 23, 40))
        assertEquals(at("2026-11-17", 0, 0), nearMidnight)
    }

    private fun board(): TaskBoard = TaskBoard(
        memos = listOf(
            MemoEntry("m-morning", "朝", "2026-11-16", "08:10", done = false),
            MemoEntry("m-afternoon", "午後", "2026-11-16", "15:00", done = false),
            MemoEntry("m-open", "日付だけ", "2026-11-16", null, done = false),
            MemoEntry("m-done", "完了", "2026-11-16", "07:00", done = true),
            MemoEntry("m-tomorrow", "明日の朝", "2026-11-17", "09:00", done = false),
            MemoEntry("m-late", "明日の遅い", "2026-11-17", "11:00", done = false),
            MemoEntry("m-plain", "明日だけ", "2026-11-17", null, done = false),
            MemoEntry("m-yesterday", "昨日", "2026-11-15", "21:00", done = false),
        ),
    )

    private fun at(iso: String, hour: Int, minute: Int): ZonedDateTime =
        LocalDate.parse(iso).atTime(hour, minute).atZone(zone)
}
