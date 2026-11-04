package dev.nossam.task.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
                "car" to "エンジンオイル",
                "clean" to "居間 床",
                "clean" to "当日",
                "memo" to "日付だけ",
                "bousai" to "水（1箱）",
            ),
            titles,
        )
        assertEquals("clean", MemoPlanner.itemsOn(sample(), "2026-11-06")[4].tabId)
        assertEquals("bousai", MemoPlanner.itemsOn(sample(), "2026-11-06").last().tabId)
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
            車 エンジンオイル
            掃除 居間 床
            掃除 当日
            時刻なし 日付だけ
            防災 水（1箱）
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
    fun postponedDateReplacesOnlyThisOccurrence() {
        val moved = CleanEntry("c1", "居間 床", "2026-10-04", 30, shift = "2026-11-10")
        val board = TaskBoard(cleaning = listOf(moved))
        assertEquals(setOf("clean"), MemoPlanner.marks(board)["2026-11-10"])
        assertEquals(null, MemoPlanner.marks(board)["2026-11-03"])
        assertEquals(listOf("居間 床"), MemoPlanner.itemsOn(board, "2026-11-10").map { it.title })
        assertTrue(MemoPlanner.itemsOn(board, "2026-11-10").single().shifted)
        val afterDone = moved.copy(done = "2026-11-10", shift = null)
        assertEquals(LocalDate.parse("2026-12-10"), MemoPlanner.nextCleanDate(afterDone.done, afterDone.cycle))
        assertEquals(
            LocalDate.parse("2026-12-10"),
            MemoPlanner.shownDate(MemoPlanner.nextCleanDate(afterDone.done, afterDone.cycle), afterDone.shift),
        )
    }

    @Test
    fun customTabsAreMarkedAndTheMorningSummaryIncludesThem() {
        val board = TaskBoard(
            extras = listOf(ExtraEntry("shop", "買い物", "i1", "電池", "2026-10-18", 1, shift = "2026-11-20")),
        )
        assertEquals(setOf("x:shop"), MemoPlanner.marks(board)["2026-11-20"])
        assertEquals(null, MemoPlanner.marks(board)["2026-11-18"])
        assertEquals("買い物 電池", MemoPlanner.dailySummary(board, "2026-11-20")!!.body)
    }

    @Test
    fun onlySwitchedOnTabsRingAndMemosStayOnByDefault() {
        val board = parseBoard(
            """
            {"memos":[{"id":"m1","text":"病院","due":"2026-11-20","time":"09:30","done":null}],
             "cleaning":[{"id":"c1","place":"居間","task":"床","done":"2026-11-20","cycle":0,"time":"08:00"}],
             "bousai":[{"id":"b1","item":"水","due":"2026-11-20","time":"07:00"}],
             "tabs":[{"id":"car","kind":"car","items":[{"id":"oil","name":"オイル","done":"2026-11-20","months":0,"time":"10:00"}]},
                      {"id":"shop","kind":"custom","name":"買い物","items":[{"id":"i1","name":"電池","done":"2026-11-20","months":0,"time":"11:00"}]}],
             "rings":{"clean":true}}
            """.trimIndent(),
        )
        assertEquals(listOf("居間 床", "病院"), MemoPlanner.planAlerts(board, noon("2026-11-02")).map { it.title })
        val memoOff = board.copy(rings = board.rings + ("memo" to false))
        assertEquals(listOf("居間 床"), MemoPlanner.planAlerts(memoOff, noon("2026-11-02")).map { it.title })
        val old = parseBoard("""{"memos":[{"id":"m1","text":"病院","due":"2026-11-20","time":"09:30","done":null}],"cleaning":[],"bousai":[]}""")
        assertEquals(listOf("病院"), MemoPlanner.planAlerts(old, noon("2026-11-02")).map { it.title })
    }

    @Test
    fun removedTabsAreSkippedAndADatedCustomTabIsKept() {
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
        assertEquals(setOf("x:t1"), MemoPlanner.marks(board)["2026-12-03"])
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

    @Test
    fun nextTaskIsTheSoonestTimedItemStillAheadToday() {
        val board = TaskBoard(
            memos = listOf(
                MemoEntry("m1", "病院", "2026-11-04", "18:30", done = false),
                MemoEntry("m2", "済", "2026-11-04", "09:00", done = true),
                MemoEntry("m3", "時刻なし", "2026-11-04", null, done = false),
                MemoEntry("m4", "明日", "2026-11-05", "08:00", done = false),
                MemoEntry("m5", "過ぎた", "2026-11-04", "08:00", done = false),
            ),
            cleaning = listOf(CleanEntry("c1", "居間 床", "2026-11-04", 0, time = "12:00")),
            rings = mapOf("clean" to true),
        )
        val now = ZonedDateTime.of(2026, 11, 4, 10, 0, 0, 0, zone)
        val next = MemoPlanner.nextToday(board, now)
        assertEquals("居間 床", next!!.title)
        assertEquals("12:00 居間 床", MemoPlanner.nextTaskText(next))
        val memoOnly = board.copy(rings = emptyMap())
        assertEquals("病院", MemoPlanner.nextToday(memoOnly, now)!!.title)
        assertEquals("次のタスク 18:30 病院", MemoPlanner.nextTaskNotice(MemoPlanner.nextToday(memoOnly, now)!!))
        assertEquals(null, MemoPlanner.nextToday(memoOnly, ZonedDateTime.of(2026, 11, 4, 19, 0, 0, 0, zone)))
    }

    @Test
    fun leadNoticeUsesTheMemoOverrideOrTheSharedSetting() {
        val board = parseBoard(
            """
            {"memos":[
               {"id":"m1","text":"病院","due":"2026-11-04","time":"18:30","done":null,"lead":5},
               {"id":"m2","text":"薬","due":"2026-11-04","time":"20:00","done":null}
             ],
             "cleaning":[{"id":"c1","place":"居間","task":"床","done":"2026-11-04","cycle":0,"time":"12:00"}],
             "rings":{"clean":false},"leadMinutes":10,"nextTaskNotice":true}
            """.trimIndent(),
        )
        assertEquals(10, board.leadMinutes)
        assertEquals(true, board.nextTaskNotice)
        assertEquals(5, board.memos.first { it.id == "m1" }.lead)
        assertEquals(null, board.memos.first { it.id == "m2" }.lead)
        val now = ZonedDateTime.of(2026, 11, 4, 10, 0, 0, 0, zone)
        val leads = MemoPlanner.planLead(board, now)
        assertEquals(listOf("p|m|m1|2026-11-04|18:30", "p|m|m2|2026-11-04|20:00"), leads.map { it.id })
        assertEquals(5, leads[0].leadMinutes)
        assertEquals(10, leads[1].leadMinutes)
        assertEquals("5分前", leads[0].detail)
        assertTrue(leads[0].triggerAtMillis < MemoPlanner.planAlerts(board, now).first { it.itemId == "m1" }.triggerAtMillis)
        val none = board.copy(leadMinutes = 0)
        assertTrue(MemoPlanner.planLead(none, now).none { it.itemId == "m2" })
        assertEquals(listOf("病院"), MemoPlanner.planLead(none, now).map { it.title })
        val old = parseBoard("""{"memos":[{"id":"m1","text":"病院","due":"2026-11-04","time":"18:30","done":null}]}""")
        assertEquals(10, old.leadMinutes)
        assertEquals(true, old.nextTaskNotice)
        assertEquals(null, old.memos[0].lead)
    }

    @Test
    fun stoppingTheAlarmMarksDoneOnlyForTheDoneButtonUnlessTheSettingIsOn() {
        assertTrue(MemoPlanner.marksDone(AlarmStop.DoneButton, doneOnStop = false))
        assertTrue(!MemoPlanner.marksDone(AlarmStop.Later, doneOnStop = true))
        assertTrue(!MemoPlanner.marksDone(AlarmStop.Other, doneOnStop = false))
        assertTrue(MemoPlanner.marksDone(AlarmStop.Other, doneOnStop = true))
        assertEquals(10, MemoPlanner.LATER_MINUTES)
        assertEquals(
            600_000L,
            MemoPlanner.snoozeTrigger(1_000L, MemoPlanner.LATER_MINUTES) - 1_000L,
        )
        val old = parseBoard("""{"memos":[{"id":"m1","text":"病院","due":"2026-11-04","time":"18:30","done":null}]}""")
        assertTrue(!old.doneOnStop)
        val on = parseBoard("""{"memos":[],"doneOnStop":true}""")
        assertTrue(on.doneOnStop)
    }

    @Test
    fun markingDoneFromTheNoticeUpdatesTheStoredItem() {
        val json = """{"memos":[{"id":"m1","text":"病院","due":"2026-11-04","time":"18:30","done":null}],"cleaning":[{"id":"c1","place":"居間","task":"床","done":"2026-10-01","cycle":30,"time":"09:00","shift":"2026-11-04"}],"bousai":[{"id":"b1","item":"水","due":"2026-11-04","time":"07:00"}]}"""
        val memo = completeOccurrence(json, "memo", "m1", "", "2026-11-04")
        assertTrue(memo.contains("\"done\":\"2026-11-04\""))
        val clean = completeOccurrence(json, "clean", "c1", "", "2026-11-04")
        val cleanBoard = parseBoard(clean)
        assertEquals("2026-11-04", cleanBoard.cleaning[0].done)
        assertEquals(null, cleanBoard.cleaning[0].shift)
        val gear = parseBoard(completeOccurrence(json, "bousai", "b1", "", "2026-11-04"))
        assertEquals(null, gear.bousai[0].due)
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
