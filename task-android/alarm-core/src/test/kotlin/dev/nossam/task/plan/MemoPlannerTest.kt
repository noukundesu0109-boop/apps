package dev.nossam.task.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

class MemoPlannerTest {
    private val zone = MemoPlanner.ZONE

    @Test
    fun datedTimedMemoRingsOnThatSameDay() {
        val memos = listOf(MemoEntry("a", "病院", "2026-11-20", "09:30", done = false))
        val plan = MemoPlanner.plan(memos, noon("2026-11-02"))
        assertEquals(1, plan.size)
        val alarm = plan.single()
        assertEquals("09:30", alarm.time)
        assertEquals("病院", alarm.title)
        val zoned = Instant.ofEpochMilli(alarm.triggerAtMillis).atZone(zone)
        assertEquals(LocalDate.parse("2026-11-20"), zoned.toLocalDate())
        assertEquals(LocalTime.of(9, 30), zoned.toLocalTime())
        assertEquals("11月20日（金） 09:30", alarm.detail)
    }

    @Test
    fun missingTimeOrDateOrDoneDoesNotRing() {
        val memos = listOf(
            MemoEntry("a", "日付だけ", "2026-11-20", null, done = false),
            MemoEntry("b", "時刻だけ", null, "09:00", done = false),
            MemoEntry("c", "完了", "2026-11-20", "10:00", done = true),
            MemoEntry("d", "空時刻", "2026-11-20", "", done = false),
            MemoEntry("e", "壊れた時刻", "2026-11-20", "9:00", done = false),
        )
        assertTrue(MemoPlanner.plan(memos, noon("2026-11-02")).isEmpty())
    }

    @Test
    fun pastTimeTodayIsSkipped() {
        val memos = listOf(
            MemoEntry("a", "朝", "2026-11-03", "08:00", done = false),
            MemoEntry("b", "夜", "2026-11-03", "18:00", done = false),
        )
        val now = ZonedDateTime.of(2026, 11, 3, 12, 0, 0, 0, zone)
        val plan = MemoPlanner.plan(memos, now)
        assertEquals(listOf("夜"), plan.map { it.title })
    }

    @Test
    fun exactCurrentMinuteIsNotScheduledAgain() {
        val memos = listOf(MemoEntry("a", "今", "2026-11-03", "12:00", done = false))
        val now = ZonedDateTime.of(2026, 11, 3, 12, 0, 0, 0, zone)
        assertTrue(MemoPlanner.plan(memos, now).isEmpty())
    }

    @Test
    fun farFutureBeyondTwoYearsIsDropped() {
        val memos = listOf(MemoEntry("a", "遠い", "2029-01-01", "09:00", done = false))
        assertTrue(MemoPlanner.plan(memos, noon("2026-11-02")).isEmpty())
    }

    @Test
    fun calendarMarksDaysThatHaveADueDate() {
        val memos = listOf(
            MemoEntry("a", "あり", "2026-11-06", "09:00", done = false),
            MemoEntry("b", "日付のみ", "2026-11-06", null, done = false),
            MemoEntry("c", "別の日", "2026-11-19", null, done = true),
            MemoEntry("d", "日付なし", null, "08:00", done = false),
        )
        assertEquals(setOf("2026-11-06", "2026-11-19"), MemoPlanner.markedDates(memos))
    }

    @Test
    fun dayListSortsByTimeAndPutsUntimedLast() {
        val memos = listOf(
            MemoEntry("a", "午後", "2026-11-06", "15:00", done = false),
            MemoEntry("b", "日付だけ", "2026-11-06", null, done = false),
            MemoEntry("c", "朝", "2026-11-06", "08:10", done = false),
            MemoEntry("d", "ほかの日", "2026-11-07", "08:00", done = false),
        )
        assertEquals(listOf("朝", "午後", "日付だけ"), MemoPlanner.entriesOn(memos, "2026-11-06").map { it.text })
        assertTrue(MemoPlanner.entriesOn(memos, "2026-11-08").isEmpty())
    }

    @Test
    fun oldBackupWithoutTimeStillParses() {
        val parsed = parseMemos(
            """
            {"app":"tasks","memos":[
              {"id":"m1","text":"牛乳","due":"2026-11-06","done":null,"at":1},
              {"id":"m2","text":"完了済み","due":"2026-11-01","done":"2026-11-02","at":2},
              {"id":"m3","text":"  ","due":null},
              {"text":"時刻つき","due":"2026-12-01","time":"07:05","done":null}
            ],"cleaning":[],"bousai":[]}
            """.trimIndent(),
        )
        assertEquals(listOf("m1", "m2", "m3"), parsed.map { it.id })
        assertEquals(listOf("牛乳", "完了済み", "時刻つき"), parsed.map { it.text })
        assertEquals(null, parsed[0].time)
        assertEquals(true, parsed[1].done)
        assertEquals("07:05", parsed[2].time)
        val plan = MemoPlanner.plan(parsed, noon("2026-11-02"))
        assertEquals(listOf("時刻つき"), plan.map { it.title })
    }

    @Test
    fun snoozeIsFiveMinutes() {
        assertEquals(1_000L + 5 * 60_000L, MemoPlanner.snoozeTrigger(1_000L))
        assertEquals(5, MemoPlanner.SNOOZE_MINUTES)
    }

    private fun noon(iso: String): ZonedDateTime =
        LocalDate.parse(iso).atTime(12, 0).atZone(zone)
}
