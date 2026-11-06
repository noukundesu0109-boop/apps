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
        assertEquals("off", AlarmPlanner.alarmKind(data, "2026-11-06"))
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
        assertEquals("leave", AlarmPlanner.alarmKind(data, "2026-11-07"))
        assertEquals("leave", AlarmPlanner.alarmKind(data, "2026-11-08"))
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
        assertEquals("off", AlarmPlanner.alarmKind(data, "2026-11-11"))
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
    fun skipKeepsThatAlarmOutAndRestorePutsItBack() {
        val data = work(shifts = mapOf("2026-11-03" to "day", "2026-11-04" to "night"))
        val planned = AlarmPlanner.plan(data, noon("2026-11-02"), horizonDays = 4)
        val target = planned.first { it.date == "2026-11-03" && it.time == "02:15" }
        val other = planned.first { it.date == "2026-11-04" }
        val skipped = AlarmPlanner.reconcileSkips(data, planned, AlarmPlanner.skipAlarm(emptySet(), target.id))
        assertEquals(setOf(target.id), skipped)
        val firing = AlarmPlanner.alarmsToSchedule(planned, skipped).map { it.id }
        assertTrue(target.id !in firing)
        assertTrue(other.id in firing)
        val restored = AlarmPlanner.reconcileSkips(data, planned, AlarmPlanner.restoreAlarm(skipped, target.id))
        assertTrue(restored.isEmpty())
        assertEquals(planned.map { it.id }, AlarmPlanner.alarmsToSchedule(planned, restored).map { it.id })
    }

    @Test
    fun changingShiftTypeDropsThatDaysSkips() {
        val day = work(shifts = mapOf("2026-11-03" to "day", "2026-11-04" to "night"))
        val now = noon("2026-11-02")
        val dayPlan = AlarmPlanner.plan(day, now, horizonDays = 4)
        val daySkip = dayPlan.first { it.date == "2026-11-03" }.id
        val nightSkip = dayPlan.first { it.date == "2026-11-04" }.id
        val early = work(shifts = mapOf("2026-11-03" to "day", "2026-11-04" to "night"), early = setOf("2026-11-03"))
        val earlyPlan = AlarmPlanner.plan(early, now, horizonDays = 4)
        val kept = AlarmPlanner.reconcileSkips(early, earlyPlan, setOf(daySkip, nightSkip))
        assertEquals(setOf(nightSkip), kept)
        assertTrue(earlyPlan.none { it.id == daySkip })
        assertEquals(earlyPlan.filter { it.id != nightSkip }.map { it.id }, AlarmPlanner.alarmsToSchedule(earlyPlan, kept).map { it.id })
        assertTrue(earlyPlan.filter { it.date == "2026-11-03" }.all { it.source == "early" })
    }

    @Test
    fun leaveDayDropsSkipsSoALaterWorkShiftIsNotBlocked() {
        val workDay = work(shifts = mapOf("2026-11-03" to "day"))
        val now = noon("2026-11-02")
        val planned = AlarmPlanner.plan(workDay, now, horizonDays = 2)
        val skip = planned.first().id
        val onLeave = work(shifts = mapOf("2026-11-03" to "day"), leave = setOf("2026-11-03"))
        val none = AlarmPlanner.plan(onLeave, now, horizonDays = 2)
        val stored = AlarmPlanner.reconcileSkips(onLeave, none, setOf(skip))
        assertTrue(stored.isEmpty())
        val back = AlarmPlanner.reconcileSkips(workDay, planned, stored)
        assertTrue(back.isEmpty())
        assertEquals(planned.map { it.id }, AlarmPlanner.alarmsToSchedule(planned, back).map { it.id })
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
        assertTrue(alarms.getValue("off").isEmpty())
        assertTrue(alarms.getValue("leave").isEmpty())
        assertTrue(alarms.getValue("day").all { it.enabled })
    }

    @Test
    fun switchedOffTimesDoNotRing() {
        val data = work(
            shifts = mapOf("2026-11-03" to "day"),
            alarms = mapOf(
                "day" to listOf(
                    AlarmRow("起床", "03:00", enabled = false),
                    AlarmRow("出勤", "06:00", enabled = true),
                ),
                "early" to emptyList(),
                "night" to emptyList(),
                "off" to emptyList(),
                "leave" to emptyList(),
            ),
        )
        val plan = AlarmPlanner.plan(data, noon("2026-11-02"), horizonDays = 3)
        assertEquals(listOf("06:00"), plan.map { it.time })
    }

    @Test
    fun leaveAndOffRingOnlyWhenTheUserAddsTimes() {
        val data = parseWorkData(
            """
            {"shifts":{"2026-11-06":"off","2026-11-08":"day"},
             "leave":["2026-11-07"],
             "alarms":{"off":[{"label":"見回り","time":"09:00"}],"leave":[{"label":"連絡","time":"10:00","enabled":true}]}}
            """.trimIndent(),
        )
        val plan = AlarmPlanner.plan(data, noon("2026-11-05"), horizonDays = 5)
        assertEquals(listOf("2026-11-06", "2026-11-07", "2026-11-08"), plan.map { it.date }.distinct())
        assertEquals("off", plan.first { it.date == "2026-11-06" }.source)
        assertEquals("leave", plan.first { it.date == "2026-11-07" }.source)
    }

    @Test
    fun dayOverrideReplacesUsualTimesOrSilencesTheDay() {
        val usual = work(shifts = mapOf("2026-11-03" to "day", "2026-11-04" to "day"))
        val overridden = usual.copy(
            dayAlarms = mapOf(
                "2026-11-03" to DayOverride(
                    mute = false,
                    times = listOf(AlarmRow("起床", "03:30"), AlarmRow("切", "04:00", enabled = false)),
                ),
                "2026-11-04" to DayOverride(mute = true),
            ),
        )
        val plan = AlarmPlanner.plan(overridden, noon("2026-11-02"), horizonDays = 4)
        assertEquals(listOf("03:30"), plan.map { it.time })
        assertEquals("override", plan.single().source)
        assertEquals("11月3日（火） この日だけ", plan.single().detail)
        assertNull(AlarmPlanner.alarmKind(overridden, "2026-11-04"))
    }

    @Test
    fun checkedWeekdayUsesEarlyTimesOnDayShiftsOnly() {
        val data = work(shifts = mapOf("2026-11-02" to "day", "2026-11-03" to "day", "2026-11-02" to "day")).copy(
            shifts = mapOf(
                "2026-11-02" to "day",
                "2026-11-03" to "day",
                "2026-11-09" to "night",
            ),
            earlyWeekdays = setOf(1),
        )
        assertEquals("early", AlarmPlanner.alarmKind(data, "2026-11-02"))
        assertEquals("day", AlarmPlanner.alarmKind(data, "2026-11-03"))
        assertEquals("night", AlarmPlanner.alarmKind(data, "2026-11-09"))
        val plan = AlarmPlanner.plan(data, noon("2026-11-01"), horizonDays = 4)
        assertEquals("02:00", plan.first { it.date == "2026-11-02" }.time)
        assertEquals("02:15", plan.first { it.date == "2026-11-03" }.time)
    }

    @Test
    fun perDayEarlySwitchOverridesTheWeekdayRule() {
        val base = work(
            shifts = mapOf("2026-11-02" to "day", "2026-11-04" to "day"),
        ).copy(earlyWeekdays = setOf(1))
        val holidayMonday = base.copy(earlySkip = setOf("2026-11-02"))
        assertEquals("day", AlarmPlanner.alarmKind(holidayMonday, "2026-11-02"))
        val extraWednesday = base.copy(early = setOf("2026-11-04"))
        assertEquals("early", AlarmPlanner.alarmKind(extraWednesday, "2026-11-04"))
        val muted = base.copy(dayAlarms = mapOf("2026-11-02" to DayOverride(mute = true)))
        assertNull(AlarmPlanner.alarmKind(muted, "2026-11-02"))
    }

    @Test
    fun olderBackupWithoutWeekdaysKeepsManualEarlyOnly() {
        val data = parseWorkData(
            """
            {"shifts":{"2026-11-02":"day","2026-11-04":"day"},"early":["2026-11-04"]}
            """.trimIndent(),
        )
        assertTrue(data.earlyWeekdays.isEmpty())
        assertEquals("day", AlarmPlanner.alarmKind(data, "2026-11-02"))
        assertEquals("early", AlarmPlanner.alarmKind(data, "2026-11-04"))
    }

    @Test
    fun olderBackupWithoutNewSettingsKeepsTheDefaultTimes() {
        val data = parseWorkData(
            """
            {"shifts":{"2026-11-03":"day","2026-11-06":"off"},"leave":["2026-11-07"],"early":[]}
            """.trimIndent(),
        )
        assertEquals("02:15", data.alarms.getValue("day").first().time)
        assertTrue(data.alarms.getValue("off").isEmpty())
        assertTrue(data.alarms.getValue("leave").isEmpty())
        assertTrue(data.dayAlarms.isEmpty())
        val plan = AlarmPlanner.plan(data, noon("2026-11-02"), horizonDays = 7)
        assertEquals(listOf("2026-11-03"), plan.map { it.date }.distinct())
    }

    @Test
    fun nextAlarmIsTheSoonestOneStillAhead() {
        val data = work(shifts = mapOf("2026-11-05" to "day"))
        val now = LocalDate.parse("2026-11-04").atTime(22, 0).atZone(zone)
        val plan = AlarmPlanner.plan(data, now, horizonDays = 3)
        val first = AlarmPlanner.nextUpcoming(plan, now.toInstant().toEpochMilli())
        assertEquals("02:15", first!!.time)
        assertEquals("目覚まし", first.title)
        assertEquals("11/5(木) 2:15 目覚まし", AlarmPlanner.nextAlarmText(first))

        val afterSkip = AlarmPlanner.alarmsToSchedule(plan, setOf(first.id))
        val second = AlarmPlanner.nextUpcoming(afterSkip, now.toInstant().toEpochMilli())
        assertEquals("04:35", second!!.time)
        assertEquals("11/5(木) 4:35 出勤", AlarmPlanner.nextAlarmText(second))
    }

    @Test
    fun nextAlarmNoticeMatchesTheClockWording() {
        val alarm = PlannedAlarm(
            id = "s|2026-11-05|day|0|05:30|起床",
            triggerAtMillis = 0L,
            date = "2026-11-05",
            time = "05:30",
            title = "起床",
            detail = "",
            source = "day",
        )
        assertEquals("11/5(木) 5:30 起床", AlarmPlanner.nextAlarmText(alarm))
        assertEquals("次のアラーム 11/5(木) 5:30 起床", AlarmPlanner.nextAlarmNotice(alarm))
        assertEquals("0:05", AlarmPlanner.displayTime("00:05"))
    }

    @Test
    fun pastAlarmsAndAnEmptyListHaveNoNextAlarm() {
        val data = work(shifts = mapOf("2026-11-05" to "day"))
        val now = LocalDate.parse("2026-11-05").atTime(10, 0).atZone(zone)
        val plan = AlarmPlanner.plan(data, now, horizonDays = 2)
        val next = AlarmPlanner.nextUpcoming(plan, now.toInstant().toEpochMilli())
        assertEquals("11:18", next!!.time)
        assertNull(AlarmPlanner.nextUpcoming(emptyList(), now.toInstant().toEpochMilli()))
    }

    @Test
    fun aSoonerSnoozeIsTheNextAlarm() {
        val shift = PlannedAlarm(
            id = "s|2026-11-05|day|2|06:07|移動",
            triggerAtMillis = 2_000L,
            date = "2026-11-05",
            time = "06:07",
            title = "移動",
            detail = "",
            source = "day",
        )
        val snooze = PlannedAlarm(
            id = "z|snooze",
            triggerAtMillis = 1_000L,
            date = "2026-11-05",
            time = "05:30",
            title = "起床",
            detail = "",
            source = "snooze",
        )
        val next = AlarmPlanner.nextUpcoming(listOf(shift, snooze), 500L)
        assertEquals("snooze", next!!.source)
        assertEquals("起床", next.title)
    }

    @Test
    fun yearCountIncludesEarlyDaysAsDayShiftsAndSkipsBlankDays() {
        val data = work(
            shifts = mapOf(
                "2026-01-05" to "day",
                "2026-01-06" to "day",
                "2026-03-02" to "night",
                "2026-05-03" to "off",
                "2026-02-29" to "day",
                "2026-13-01" to "night",
                "2025-12-31" to "day",
                "2027-01-01" to "night",
            ),
            early = setOf("2026-01-06"),
            leave = setOf("2026-08-14", "2026-01-05", "2026-02-29", "2025-08-01"),
        )
        val counts = AlarmPlanner.countYear(data, 2026)
        assertEquals(2, counts.day)
        assertEquals(1, counts.night)
        assertEquals(3, counts.attended)
        assertEquals(1, counts.off)
        assertEquals(2, counts.leave)
        assertEquals(YearShiftCounts(1, 0, 0, 1), AlarmPlanner.countYear(data, 2025))
        assertEquals(1, AlarmPlanner.countYear(data, 2025).attended)
        assertEquals(YearShiftCounts(0, 1, 0, 0), AlarmPlanner.countYear(data, 2027))
        assertEquals(1, AlarmPlanner.countYear(data, 2027).attended)
        assertEquals(YearShiftCounts(0, 0, 0, 0), AlarmPlanner.countYear(data, 2024))
        assertEquals(0, AlarmPlanner.countYear(data, 2024).attended)
    }

    @Test
    fun weekdayEarlyHoursApplyUntilADayOverridesThem() {
        val data = work(
            shifts = mapOf(
                "2026-11-02" to "day",
                "2026-11-03" to "day",
                "2026-11-04" to "day",
                "2026-11-09" to "day",
                "2026-11-16" to "day",
                "2026-11-05" to "night",
                "2025-11-02" to "day",
            ),
            early = setOf("2026-11-04"),
        ).copy(
            earlySkip = setOf("2026-11-16"),
            earlyWeekdays = setOf(1),
            earlyHoursWeekday = mapOf(1 to 0.25, 2 to 0.5, 3 to 0.5),
            earlyHoursDay = mapOf("2026-11-09" to 1.0, "2026-11-02" to 0.0),
        )
        assertEquals(0.0, AlarmPlanner.earlyHours(data, "2026-11-02"), 0.0)
        assertEquals(0.0, AlarmPlanner.earlyHours(data, "2026-11-03"), 0.0)
        assertEquals(0.5, AlarmPlanner.earlyHours(data, "2026-11-04"), 0.0)
        assertEquals(1.0, AlarmPlanner.earlyHours(data, "2026-11-09"), 0.0)
        assertEquals(0.0, AlarmPlanner.earlyHours(data, "2026-11-16"), 0.0)
        assertEquals(0.0, AlarmPlanner.earlyHours(data, "2026-11-05"), 0.0)
        val year = AlarmPlanner.countYear(data, 2026)
        assertEquals(5, year.day)
        assertEquals(1, year.night)
        assertEquals(6, year.attended)
        assertEquals(3, year.earlyDays)
        assertEquals(1.5, year.earlyHours, 0.0)
        assertEquals(0, AlarmPlanner.countYear(data, 2025).earlyDays)
        assertEquals(1, AlarmPlanner.countYear(data, 2025).attended)
    }

    @Test
    fun oldBackupWithoutEarlyHoursStillLoads() {
        val data = parseWorkData(
            """
            {"shifts":{"2026-11-02":"day","2026-11-03":"night"},"early":["2026-11-02"],"leave":[]}
            """.trimIndent(),
        )
        assertTrue(data.earlyHoursWeekday.isEmpty())
        assertTrue(data.earlyHoursDay.isEmpty())
        assertEquals(0.0, AlarmPlanner.earlyHours(data, "2026-11-02"), 0.0)
        val restored = parseWorkData(
            """
            {"shifts":{"2026-11-02":"day","2026-11-04":"day"},
             "earlyWeekdays":[1],
             "earlyHoursWeekday":{"1":0.25,"8":9,"2":-1,"3":"x","5":"0.5"},
             "earlyHoursDay":{"2026-11-04":0,"2026-02-29":1,"bad":2}}
            """.trimIndent(),
        )
        assertEquals(mapOf(1 to 0.25, 5 to 0.5), restored.earlyHoursWeekday)
        assertEquals(mapOf("2026-11-04" to 0.0), restored.earlyHoursDay)
        assertEquals(0.0, AlarmPlanner.earlyHours(restored, "2026-11-04"), 0.0)
        assertEquals(0.3, AlarmPlanner.roundHours(0.1 + 0.2), 0.0)
    }

    @Test
    fun leapDayCountsOnlyInALeapYear() {
        val data = work(
            shifts = mapOf("2024-02-29" to "day", "2026-02-29" to "night"),
            leave = setOf("2024-02-29"),
        )
        assertEquals(YearShiftCounts(1, 0, 0, 1), AlarmPlanner.countYear(data, 2024))
        assertEquals(YearShiftCounts(0, 0, 0, 0), AlarmPlanner.countYear(data, 2026))
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
