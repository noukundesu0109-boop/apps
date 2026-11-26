package dev.nossam.task.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class WidgetWindowTest {
    private val zone = ZoneId.of("Asia/Tokyo")
    private val today = LocalDate.parse("2026-11-16")

    @Test
    fun weekWindowKeepsSevenDaysAndOverdueItems() {
        val lines = MemoPlanner.widgetWindow(board(), at("2026-11-16", 10, 0))
        assertEquals(
            listOf("昨日", "日付だけ", "朝", "午後", "明日の朝", "六日後"),
            lines.map { it.item.title },
        )
        assertTrue(lines.none { it.item.title == "完了" || it.item.title == "八日後" })
        val rows = MemoPlanner.widgetRows(board(), at("2026-11-16", 10, 0))
        val headers = rows.filterIsInstance<WidgetRow.Header>().map { it.label }
        assertEquals(listOf("11/15(日)", "今日", "明日", "11/22(日)"), headers)
    }

    @Test
    fun untimedItemsSortAsMidnight() {
        val lines = MemoPlanner.widgetWindow(board(), at("2026-11-16", 10, 0))
        val open = lines.first { it.item.title == "日付だけ" }
        assertEquals(today.atStartOfDay(zone).toInstant(), open.at.toInstant())
        assertTrue(lines.indexOfFirst { it.item.title == "日付だけ" } < lines.indexOfFirst { it.item.title == "朝" })
        assertEquals("", open.timeText)
    }

    @Test
    fun tabItemsAreIncludedWithTheirLabels() {
        val lines = MemoPlanner.widgetWindow(tabs(), at("2026-11-16", 10, 0))
        assertEquals(
            listOf("車 オイル", "掃除 居間 床", "防災 水", "備品 電池", "日常 換気"),
            lines.map { it.titleText },
        )
    }

    @Test
    fun refreshStaysWithinAnHourAndHitsMidnight() {
        assertEquals(at("2026-11-16", 11, 0), MemoPlanner.nextWidgetRefresh(TaskBoard(), at("2026-11-16", 10, 0)))
        assertEquals(at("2026-11-17", 0, 0), MemoPlanner.nextWidgetRefresh(TaskBoard(), at("2026-11-16", 23, 40)))
    }

    private fun board(): TaskBoard = TaskBoard(
        memos = listOf(
            MemoEntry("m-y", "昨日", "2026-11-15", "21:00", done = false),
            MemoEntry("m-open", "日付だけ", "2026-11-16", null, done = false),
            MemoEntry("m-morning", "朝", "2026-11-16", "08:10", done = false),
            MemoEntry("m-afternoon", "午後", "2026-11-16", "15:00", done = false),
            MemoEntry("m-done", "完了", "2026-11-16", "07:00", done = true),
            MemoEntry("m-tomorrow", "明日の朝", "2026-11-17", "09:00", done = false),
            MemoEntry("m-sixth", "六日後", "2026-11-22", "12:00", done = false),
            MemoEntry("m-seventh", "八日後", "2026-11-23", "12:00", done = false),
        ),
    )

    private fun tabs(): TaskBoard = TaskBoard(
        cleaning = listOf(CleanEntry("c1", "居間 床", "2026-11-09", 7, time = "09:00")),
        bousai = listOf(BousaiEntry("b1", "水", "2026-11-18", time = "07:00")),
        cars = listOf(CarEntry("car1", "car", "オイル", "2026-05-16", 6, time = "08:00")),
        extras = listOf(
            ExtraEntry("stock", "備品", "s1", "電池", "2026-10-19", 1, time = "10:00"),
            ExtraEntry("nichijo", "日常", "d1", "換気", null, 1, due = "2026-11-20", time = "09:00", repeat = "month", every = 1),
        ),
    )

    private fun at(iso: String, hour: Int, minute: Int): ZonedDateTime =
        LocalDate.parse(iso).atTime(hour, minute).atZone(zone)
}
