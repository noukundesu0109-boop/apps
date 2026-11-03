package dev.nossam.zangyo.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

class AlarmPlannerTest {
    private val zone = AlarmPlanner.ZONE

    @Test
    fun dayShiftUsesNormalDayTimesOnTheSameCalendarDay() {
        val data = work(
            shifts = mapOf("2026-11-03" to "day"),
        )
        val plan = AlarmPlanner.plan(data, noon("2026-11-02"), horizonDays = 5)
        assertEquals(listOf("02:15", "04:35", "06:07", "11:18"), plan.map { it.time })
        assertTrue(plan.all { it.source == "day" && it.date == "2026-11-03" })
        plan.forEach { alarm ->
            val zoned = Instant.ofEpochMilli(alarm.triggerAtMillis).atZone(zone)
            assertEquals(LocalDate.parse("2026-11-03"), zoned.toLocalDate())
            assertEquals(LocalTime.parse(alarm.time), zoned.toLocalTime())
        }
        assertEquals("11月3日（火） 通常の昼勤", plan.first().detail)
    }

    @Test
    fun earlyFlagSelectsEarlySetNotNormalDaySet() {
        val data = work(
            shifts = mapOf("2026-11-04" to "day"),
            early = setOf("2026-11-04"),
        )
        val plan = AlarmPlanner.plan(data, noon("2026-11-03"), horizonDays = 3)
        assertEquals("early", AlarmPlanner.alarmKind(data, "2026-11-04"))
        assertEquals(listOf("02:00", "04:20", "05:55", "11:18"), plan.map { it.time })
        assertTrue(plan.all { it.source == "early" })
        assertEquals("目覚まし", plan.first().title)
    }

    @Test
    fun nightShiftUsesNightTimes() {
        val data = work(shifts = mapOf("2026-11-05" to "night"))
        val plan = AlarmPlanner.plan(data, noon("2026-11-04"), horizonDays = 3)
        assertEquals("night", AlarmPlanner.alarmKind(data, "2026-11-05"))
        assertEquals(listOf("14:30", "16:47", "21:59"), plan.map { it.time })
        assertEquals(listOf("目覚まし", "移動", "休憩"), plan.map { it.title })
        assertTrue(plan.none { it.title == "出勤" })
    }

    @Test
    fun offShiftAndMissingShiftScheduleNothing() {
        val data = work(
            shifts = mapOf(
                "2026-11-06" to "off",
                "2026-11-08" to "day",
            ),
        )
        assertNull(AlarmPlanner.alarmKind(data, "2026-11-06"))
        assertNull(AlarmPlanner.alarmKind(data, "2026-11-07"))
        val plan = AlarmPlanner.plan(data, noon("2026-11-05"), horizonDays = 4)
        assertEquals(listOf("2026-11-08"), plan.map { it.date }.distinct())
    }

    @Test
    fun leaveDaySchedulesNothingEvenIfShiftIsWork() {
        val data = work(
            shifts = mapOf(
                "2026-11-07" to "day",
                "2026-11-08" to "night",
                "2026-11-09" to "day",
            ),
            early = setOf("2026-11-07"),
            leave = setOf("2026-11-07", "2026-11-08"),
        )
        assertNull(AlarmPlanner.alarmKind(data, "2026-11-07"))
        assertNull(AlarmPlanner.alarmKind(data, "2026-11-08"))
        assertEquals("day", AlarmPlanner.alarmKind(data, "2026-11-09"))
        val plan = AlarmPlanner.plan(data, noon("2026-11-06"), horizonDays = 5)
        assertTrue(plan.all { it.date == "2026-11-09" && it.source == "day" })
    }

    @Test
    fun earlyFlagDoesNotOverrideNightOrOff() {
        val data = work(
            shifts = mapOf(
                "2026-11-10" to "night",
                "2026-11-11" to "off",
            ),
            early = setOf("2026-11-10", "2026-11-11"),
        )
        assertEquals("night", AlarmPlanner.alarmKind(data, "2026-11-10"))
        assertNull(AlarmPlanner.alarmKind(data, "2026-11-11"))
        val plan = AlarmPlanner.plan(data, noon("2026-11-09"), horizonDays = 4)
        assertEquals(listOf("14:30", "16:47", "21:59"), plan.map { it.time })
    }

    @Test
    fun pastTimesTodayAreSkippedAndLaterTimesRemain() {
        val data = work(shifts = mapOf("2026-11-03" to "day"))
        val now = ZonedDateTime.of(2026, 11, 3, 5, 0, 0, 0, zone)
        val plan = AlarmPlanner.plan(data, now, horizonDays = 1)
        assertEquals(listOf("06:07", "11:18"), plan.map { it.time })
    }

    @Test
    fun exactCurrentMinuteIsNotScheduledAgain() {
        val data = work(
            alarms = mapOf(
                "day" to listOf(AlarmRow("出勤", "05:00")),
                "early" to emptyList(),
                "night" to emptyList(),
            ),
            shifts = mapOf("2026-11-03" to "day"),
        )
        val now = ZonedDateTime.of(2026, 11, 3, 5, 0, 0, 0, zone)
        val plan = AlarmPlanner.plan(data, now, horizonDays = 1)
        assertTrue(plan.isEmpty())
    }

    @Test
    fun horizonIncludesTheLastDayAndExcludesTheDayAfter() {
        val data = work(
            shifts = mapOf(
                "2026-11-01" to "day",
                "2026-11-02" to "day",
                "2026-11-03" to "night",
            ),
        )
        val now = ZonedDateTime.of(2026, 11, 1, 0, 30, 0, 0, zone)
        val plan = AlarmPlanner.plan(data, now, horizonDays = 2)
        assertEquals(setOf("2026-11-01", "2026-11-02"), plan.map { it.date }.toSet())
        assertTrue(plan.none { it.date == "2026-11-03" })
    }

    @Test
    fun customTimesAreUsedAndBlankOrInvalidTimesAreSkipped() {
        val data = work(
            shifts = mapOf("2026-11-12" to "day", "2026-11-13" to "night"),
            alarms = mapOf(
                "day" to listOf(
                    AlarmRow("起床", "03:10"),
                    AlarmRow("空", ""),
                    AlarmRow("不正", "99:99"),
                    AlarmRow("桁不足", "3:00"),
                ),
                "early" to emptyList(),
                "night" to listOf(AlarmRow("  ", "18:00")),
            ),
        )
        val plan = AlarmPlanner.plan(data, noon("2026-11-11"), horizonDays = 4)
        assertEquals(listOf("03:10", "18:00"), plan.map { it.time })
        assertEquals("起床", plan[0].title)
        assertEquals("アラーム", plan[1].title)
    }

    @Test
    fun missingAlarmSetsFallBackToTheBuiltInDefaults() {
        val data = parseWorkData(
            """
            {"shifts":{"2026-11-03":"day","2026-11-04":"night"},"early":["2026-11-03"]}
            """.trimIndent(),
        )
        assertEquals("02:00", data.alarms.getValue("early").first().time)
        assertEquals(3, data.alarms.getValue("night").size)
        val plan = AlarmPlanner.plan(data, noon("2026-11-02"), horizonDays = 4)
        assertEquals("early", plan.first { it.date == "2026-11-03" }.source)
        assertEquals("02:00", plan.first { it.date == "2026-11-03" }.time)
        assertTrue(plan.any { it.date == "2026-11-04" && it.time == "21:59" })
    }

    @Test
    fun explicitEmptyAlarmSetDoesNotFallBackToDefaults() {
        val data = parseWorkData(
            """
            {"shifts":{"2026-11-03":"day"},"alarms":{"day":[],"early":[{"label":"x","time":"01:00"}],"night":[]}}
            """.trimIndent(),
        )
        val plan = AlarmPlanner.plan(data, noon("2026-11-02"), horizonDays = 3)
        assertTrue(plan.isEmpty())
    }

    @Test
    fun holidayOvertimeHoursDoNotCancelAWorkday() {
        val data = parseWorkData(
            """
            {
              "app":"zangyo",
              "days":{"2026-11-03":{"holiday":8,"over":1.5},"2026-11-04":{"holiday":8}},
              "shifts":{"2026-11-03":"day","2026-11-04":"off"},
              "early":[],
              "leave":[],
              "alarms":{"day":[{"label":"目覚まし","time":"02:15"}],"early":[],"night":[]}
            }
            """.trimIndent(),
        )
        val plan = AlarmPlanner.plan(data, noon("2026-11-02"), horizonDays = 4)
        assertEquals(1, plan.size)
        assertEquals("2026-11-03", plan.single().date)
        assertEquals("day", plan.single().source)
    }

    @Test
    fun dottedLeaveStringAndBackupShapeAreHonored() {
        val data = parseWorkData(
            """
            {
              "version":1,
              "app":"zangyo",
              "savedAt":"2026-11-01T00:00:00.000Z",
              "days":{},
              "adjust":{},
              "leave":"2026-11-03.2026-11-05.not-a-date",
              "shifts":{"2026-11-03":"day","2026-11-04":"day","2026-11-05":"night","2026-13-01":"day"},
              "early":["2026-11-04","bad"],
              "alarms":{"day":[{"label":"出勤","time":"04:35"}]}
            }
            """.trimIndent(),
        )
        assertEquals(setOf("2026-11-03", "2026-11-05"), data.leave)
        assertEquals(setOf("2026-11-04"), data.early)
        assertEquals("02:00", data.alarms.getValue("early").first().time)
        val plan = AlarmPlanner.plan(data, noon("2026-11-02"), horizonDays = 5)
        assertEquals(listOf("2026-11-04"), plan.map { it.date }.distinct())
        assertEquals("early", plan.first().source)
    }

    @Test
    fun snoozeIsFiveMinutesLater() {
        assertEquals(1_000L + 5 * 60_000L, AlarmPlanner.snoozeTrigger(1_000L))
        assertEquals(5, AlarmPlanner.SNOOZE_MINUTES)
    }

    @Test
    fun defaultClockTimesMatchTheRequestedSets() {
        val alarms = AlarmPlanner.defaultAlarms()
        assertEquals(listOf("02:00", "04:20", "05:55", "11:18"), alarms.getValue("early").map { it.time })
        assertEquals(listOf("目覚まし", "出勤", "移動", "休憩"), alarms.getValue("early").map { it.label })
        assertEquals(listOf("02:15", "04:35", "06:07", "11:18"), alarms.getValue("day").map { it.time })
        assertEquals(listOf("14:30", "16:47", "21:59"), alarms.getValue("night").map { it.time })
        assertEquals(listOf("目覚まし", "移動", "休憩"), alarms.getValue("night").map { it.label })
    }

    private fun noon(iso: String): ZonedDateTime {
        val date = LocalDate.parse(iso)
        return date.atTime(12, 0).atZone(zone)
    }

    private fun work(
        shifts: Map<String, String> = emptyMap(),
        early: Set<String> = emptySet(),
        leave: Set<String> = emptySet(),
        alarms: Map<String, List<AlarmRow>> = AlarmPlanner.defaultAlarms(),
    ) = WorkData(shifts, early, leave, alarms)
}
