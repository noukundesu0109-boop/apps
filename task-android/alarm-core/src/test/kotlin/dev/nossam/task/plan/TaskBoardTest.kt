package dev.nossam.task.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

class TaskBoardTest {
    private val zone = MemoPlanner.ZONE

    @Test
    fun marksMemosCleanNextDueBousaiExpiryAndCarMonths() {
        val marks = MemoPlanner.marks(sample())
        assertEquals(setOf("memo", "clean", "bousai", "car"), marks["2026-11-06"])
        assertEquals(setOf("memo"), marks["2026-11-07"])
        assertEquals(setOf("car"), marks["2026-02-28"])
        assertEquals(setOf("clean"), marks["2026-11-05"])
        assertEquals(null, marks["2026-11-08"])
    }

    @Test
    fun carMonthEndClampsLikeTheWebApp() {
        assertEquals(LocalDate.parse("2026-02-28"), MemoPlanner.nextCarDate("2026-01-31", 1))
        assertEquals(LocalDate.parse("2024-02-29"), MemoPlanner.nextCarDate("2024-01-31", 1))
        assertEquals(LocalDate.parse("2026-11-06"), MemoPlanner.nextCarDate("2026-05-06", 6))
        assertNull(MemoPlanner.nextCarDate("2026-05-06", 0))
        assertNull(MemoPlanner.nextCarDate("2026-05-06", null))
        assertNull(MemoPlanner.nextCarDate(null, 6))
    }

    @Test
    fun cleanCycleIncludesZeroAndSkipsMissingPieces() {
        assertEquals(LocalDate.parse("2026-11-06"), MemoPlanner.nextCleanDate("2026-10-07", 30))
        assertEquals(LocalDate.parse("2026-11-06"), MemoPlanner.nextCleanDate("2026-11-06", 0))
        assertEquals(LocalDate.parse("2026-11-05"), MemoPlanner.nextCleanDate("2026-11-06", -1))
        assertNull(MemoPlanner.nextCleanDate(null, 7))
        assertNull(MemoPlanner.nextCleanDate("2026-11-06", null))
    }

    @Test
    fun dayListOrdersMemosThenChores() {
        val titles = MemoPlanner.itemsOn(sample(), "2026-11-06").map { it.kind to it.title }
        assertEquals(
            listOf(
                "memo" to "完了",
                "memo" to "朝",
                "memo" to "午後",
                "memo" to "日付だけ",
                "clean" to "居間 床",
                "clean" to "当日",
                "bousai" to "水（1箱）",
                "car" to "エンジンオイル",
            ),
            titles,
        )
        assertEquals("clean", MemoPlanner.itemsOn(sample(), "2026-11-06")[4].tabId)
        assertEquals("car", MemoPlanner.itemsOn(sample(), "2026-11-06").last().tabId)
        assertEquals(emptyList<DayItem>(), MemoPlanner.itemsOn(sample(), "2026-11-08"))
    }

    @Test
    fun choresDoNotBecomeRingingAlarms() {
        val plan = MemoPlanner.plan(sample().memos, noon("2026-11-01"))
        assertEquals(listOf("朝", "午後", "別の日"), plan.map { it.title })
    }

    @Test
    fun dailySummaryOmitsDoneMemosAndIsAbsentWhenEmpty() {
        val summary = MemoPlanner.dailySummary(sample(), "2026-11-06")
        assertEquals("11月6日（金）の予定", summary!!.title)
        assertEquals(
            """
            08:10 朝
            15:00 午後
            時刻なし 日付だけ
            掃除 居間 床
            掃除 当日
            防災 水（1箱）
            車 エンジンオイル
            """.trimIndent(),
            summary.body,
        )
        val doneOnly = TaskBoard(memos = listOf(MemoEntry("m", "済", "2026-11-06", "09:00", done = true)))
        assertNull(MemoPlanner.dailySummary(doneOnly, "2026-11-06"))
        assertNull(MemoPlanner.dailySummary(TaskBoard(), "2026-11-06"))
        assertNull(MemoPlanner.dailySummary(sample(), "not-a-date"))
    }

    @Test
    fun summaryTimeIsTodayWhenStillAheadOtherwiseTomorrowInTokyo() {
        val early = ZonedDateTime.of(2026, 11, 3, 5, 59, 0, 0, zone)
        assertEquals(
            ZonedDateTime.of(2026, 11, 3, 6, 0, 0, 0, zone),
            MemoPlanner.nextSummaryAt(early, null),
        )
        val exact = ZonedDateTime.of(2026, 11, 3, 6, 0, 0, 0, zone)
        assertEquals(
            ZonedDateTime.of(2026, 11, 4, 6, 0, 0, 0, zone),
            MemoPlanner.nextSummaryAt(exact, "06:00"),
        )
        val afternoon = ZonedDateTime.of(2026, 11, 3, 12, 0, 0, 0, zone)
        assertEquals(
            ZonedDateTime.of(2026, 11, 4, 7, 30, 0, 0, zone),
            MemoPlanner.nextSummaryAt(afternoon, "07:30"),
        )
        val fromUtc = ZonedDateTime.parse("2026-11-02T20:30:00Z")
        assertEquals(
            ZonedDateTime.of(2026, 11, 3, 6, 0, 0, 0, zone).toInstant(),
            MemoPlanner.nextSummaryAt(fromUtc, "06:00").toInstant(),
        )
        val fallback = MemoPlanner.nextSummaryAt(afternoon, "99:99")
        assertEquals(LocalDate.parse("2026-11-04"), fallback.toLocalDate())
        assertEquals(LocalTime.of(6, 0), fallback.toLocalTime())
    }

    @Test
    fun removedTabsAreSkippedAndCustomTabsAreNotMarked() {
        val board = parseBoard(
            """
            {"memos":[{"id":"m1","text":"メモ","due":"2026-11-03","done":null}],
             "removed":["clean","bousai","car"],
             "cleaning":[{"id":"c1","place":"台所","task":"床","done":"2026-11-01","cycle":2}],
             "bousai":[{"id":"b1","item":"水","qty":"1箱","due":"2026-11-03"}],
             "tabs":[
               {"id":"car","kind":"car","name":"車","items":[{"id":"car1","name":"オイル","done":"2026-11-03","months":1}]},
               {"id":"t1","kind":"custom","name":"買い物","items":[{"id":"i1","name":"電池","done":"2026-11-03","months":1}]}
             ]}
            """.trimIndent(),
        )
        assertEquals(setOf("memo"), MemoPlanner.marks(board)["2026-11-03"])
        assertEquals(listOf("メモ"), MemoPlanner.itemsOn(board, "2026-11-03").map { it.title })
        assertEquals(listOf("m1"), board.memos.map { it.id })
    }

    @Test
    fun backupParsesChoreDatesAndLeavesKmOnlyOffTheCalendar() {
        val board = parseBoard(
            """
            {"app":"tasks","memos":[{"id":"m1","text":"牛乳","due":"2026-11-06","done":null}],
             "cleaning":[{"id":"c1","place":"居間","task":"床","done":"2026-10-07","cycle":30},{"id":"c2","place":"窓","task":"","done":null,"cycle":7}],
             "bousai":[{"id":"b1","item":"水","qty":"1箱","due":"2026-11-06"},{"id":"b2","item":"電池","due":null}],
             "tabs":[{"id":"car","kind":"car","items":[
               {"id":"oil","name":"エンジンオイル","done":"2026-05-06","months":6},
               {"id":"pad","name":"ブレーキパッド","done":"2026-01-01","months":null,"km":40000}
             ]}]}
            """.trimIndent(),
        )
        assertEquals(setOf("memo", "clean", "bousai", "car"), MemoPlanner.marks(board)["2026-11-06"])
        assertEquals("居間 床", board.cleaning[0].title)
        assertEquals("水（1箱）", board.bousai[0].title)
        assertEquals(listOf("oil", "pad"), board.cars.map { it.id })
        assertNull(MemoPlanner.nextCarDate(board.cars[1].done, board.cars[1].months))
        assertEquals(parseMemos("""{"memos":[{"id":"m1","text":"牛乳","due":"2026-11-06","done":null}]}""").map { it.id }, board.memos.map { it.id })
    }

    private fun sample(): TaskBoard = TaskBoard(
        memos = listOf(
            MemoEntry("m1", "午後", "2026-11-06", "15:00", done = false),
            MemoEntry("m2", "日付だけ", "2026-11-06", null, done = false),
            MemoEntry("m3", "朝", "2026-11-06", "08:10", done = false),
            MemoEntry("m4", "完了", "2026-11-06", "07:00", done = true),
            MemoEntry("m5", "別の日", "2026-11-07", "09:00", done = false),
        ),
        cleaning = listOf(
            CleanEntry("c1", "居間 床", "2026-10-07", 30),
            CleanEntry("c2", "未実施", null, 7),
            CleanEntry("c0", "当日", "2026-11-06", 0),
            CleanEntry("c3", "前日", "2026-11-06", -1),
        ),
        bousai = listOf(
            BousaiEntry("b1", "水（1箱）", "2026-11-06"),
            BousaiEntry("b2", "電池", null),
        ),
        cars = listOf(
            CarEntry("car1", "car", "エンジンオイル", "2026-05-06", 6),
            CarEntry("car2", "car", "ブレーキパッド", "2026-01-01", null),
            CarEntry("car3", "car", "ワイパー", "2026-01-31", 1),
        ),
    )

    private fun noon(iso: String): ZonedDateTime =
        LocalDate.parse(iso).atTime(12, 0).atZone(zone)
}
