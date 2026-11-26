package dev.nossam.task.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DailyRepeatTest {
    @Test
    fun planBasisKeepsTheFirstAfterThisOccurrenceMovesToTheThird() {
        val next = MemoPlanner.advanceDaily(
            planned = LocalDate.parse("2026-03-01"),
            series = LocalDate.parse("2026-03-01"),
            repeat = MemoPlanner.REPEAT_MONTH,
            every = 1,
            basis = MemoPlanner.BASIS_PLAN,
            today = LocalDate.parse("2026-03-03"),
        )
        assertEquals(LocalDate.parse("2026-04-01"), next.due)
        assertEquals(LocalDate.parse("2026-03-01"), next.series)
    }

    @Test
    fun doneBasisStartsFromTheDayItWasFinished() {
        val next = MemoPlanner.advanceDaily(
            planned = LocalDate.parse("2026-03-01"),
            series = LocalDate.parse("2026-03-01"),
            repeat = MemoPlanner.REPEAT_MONTH,
            every = 1,
            basis = MemoPlanner.BASIS_DONE,
            today = LocalDate.parse("2026-03-03"),
        )
        assertEquals(LocalDate.parse("2026-04-03"), next.due)
        assertEquals(LocalDate.parse("2026-03-03"), next.series)
    }

    @Test
    fun monthEndAndLeapDayStayOnTheSeriesDay() {
        val leap = MemoPlanner.advanceDaily(
            planned = LocalDate.parse("2024-01-31"),
            series = LocalDate.parse("2024-01-31"),
            repeat = MemoPlanner.REPEAT_MONTH,
            every = 1,
            basis = MemoPlanner.BASIS_PLAN,
            today = LocalDate.parse("2024-01-31"),
        )
        assertEquals(LocalDate.parse("2024-02-29"), leap.due)
        val march = MemoPlanner.advanceDaily(
            planned = leap.due,
            series = leap.series,
            repeat = MemoPlanner.REPEAT_MONTH,
            every = 1,
            basis = MemoPlanner.BASIS_PLAN,
            today = leap.due,
        )
        assertEquals(LocalDate.parse("2024-03-31"), march.due)
        val common = MemoPlanner.addMonthsClamped(LocalDate.parse("2025-01-31"), 1)
        assertEquals(LocalDate.parse("2025-02-28"), common)
        val nextYear = MemoPlanner.advanceDaily(
            planned = LocalDate.parse("2024-02-29"),
            series = LocalDate.parse("2024-02-29"),
            repeat = MemoPlanner.REPEAT_MONTHS,
            every = 12,
            basis = MemoPlanner.BASIS_PLAN,
            today = LocalDate.parse("2024-02-29"),
        )
        assertEquals(LocalDate.parse("2025-02-28"), nextYear.due)
    }

    @Test
    fun aLateDailyOrWeeklyItemMovesToTheNextFutureDate() {
        val daily = MemoPlanner.advanceDaily(
            planned = LocalDate.parse("2026-03-01"),
            series = LocalDate.parse("2026-03-01"),
            repeat = MemoPlanner.REPEAT_DAY,
            every = 1,
            basis = MemoPlanner.BASIS_PLAN,
            today = LocalDate.parse("2026-03-05"),
        )
        assertEquals(LocalDate.parse("2026-03-06"), daily.due)
        val weekly = MemoPlanner.advanceDaily(
            planned = LocalDate.parse("2026-03-02"),
            series = LocalDate.parse("2026-03-02"),
            repeat = MemoPlanner.REPEAT_WEEK,
            every = 1,
            basis = MemoPlanner.BASIS_PLAN,
            today = LocalDate.parse("2026-03-04"),
        )
        assertEquals(LocalDate.parse("2026-03-09"), weekly.due)
    }

    @Test
    fun postponeMovesTheNoticeAndDoneAdvancesTheStoredDate() {
        val json = """
            {"memos":[],"rings":{"nichijo":true},
             "tabs":[{"id":"nichijo","kind":"custom","name":"日常","items":[
               {"id":"d1","name":"換気扇","repeat":"month","every":1,"basis":"plan",
                "due":"2026-04-01","series":"2026-04-01","shift":"2026-04-03","time":"09:00","sound":true}
             ]}]}
        """.trimIndent()
        val board = parseBoard(json)
        val alarm = MemoPlanner.planAlerts(board, noon("2026-04-02")).single()
        assertEquals("2026-04-03", alarm.date)
        assertEquals("09:00", alarm.time)
        assertFalse(alarm.silent)
        val done = parseBoard(completeOccurrence(json, "tab", "d1", "nichijo", "2026-04-03"))
        val item = done.extras.single()
        assertEquals("2026-05-01", item.due)
        assertEquals("2026-04-01", item.series)
        assertNull(item.shift)
        assertEquals("2026-05-01", MemoPlanner.planAlerts(done, noon("2026-04-04")).single().date)
    }

    @Test
    fun silentItemsStayQuietAndOldMonthItemsKeepTheirDate() {
        val quiet = parseBoard(
            """
            {"rings":{"nichijo":true},"tabs":[{"id":"nichijo","name":"日常","kind":"custom","items":[
              {"id":"d1","name":"換気扇","repeat":"week","every":1,"basis":"plan","due":"2026-04-06","time":"08:00","sound":false}
            ]}]}
            """.trimIndent(),
        )
        val alarm = MemoPlanner.planAlerts(quiet, noon("2026-04-01")).single()
        assertTrue(alarm.silent)
        assertEquals(0, MemoPlanner.leadFor(quiet, alarm))
        val legacy = parseBoard(
            """
            {"rings":{"nichijo":true},"tabs":[{"id":"nichijo","name":"日常","kind":"custom","items":[
              {"id":"d1","name":"換気扇","done":"2026-01-31","months":1,"time":"08:00"}
            ]}]}
            """.trimIndent(),
        )
        assertEquals("2026-02-28", MemoPlanner.planAlerts(legacy, noon("2026-02-01")).single().date)
        val car = parseBoard(
            """
            {"tabs":[{"id":"car","kind":"car","items":[
              {"id":"pad","name":"ブレーキパッド","done":"2026-01-01","months":null,"km":40000}
            ]}]}
            """.trimIndent(),
        )
        assertNull(MemoPlanner.nextCarDate(car.cars.single().done, car.cars.single().months))
    }

    private fun noon(iso: String) = LocalDate.parse(iso).atTime(12, 0).atZone(MemoPlanner.ZONE)
}
