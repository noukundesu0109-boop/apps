package dev.nossam.task.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

class VoiceMemoParserTest {
    private val now: ZonedDateTime = ZonedDateTime.of(2026, 11, 4, 15, 0, 0, 0, VoiceMemoParser.ZONE)

    @Test
    fun tomorrowAtNineIsHospital() {
        val parsed = VoiceMemoParser.parse("明日の9時に病院", now)
        assertEquals("病院", parsed.content)
        assertEquals(LocalDate.parse("2026-11-05"), parsed.date)
        assertEquals(LocalTime.of(9, 0), parsed.time)
    }

    @Test
    fun afternoonHalfHourAndMorningWords() {
        val afternoon = VoiceMemoParser.parse("今日の午後3時半に買い物", now)
        assertEquals("買い物", afternoon.content)
        assertEquals(LocalDate.parse("2026-11-04"), afternoon.date)
        assertEquals(LocalTime.of(15, 30), afternoon.time)

        val morning = VoiceMemoParser.parse("あしたの朝7時にごみ", now)
        assertEquals("ごみ", morning.content)
        assertEquals(LocalDate.parse("2026-11-05"), morning.date)
        assertEquals(LocalTime.of(7, 0), morning.time)
    }

    @Test
    fun monthDayAndDayOfMonth() {
        val month = VoiceMemoParser.parse("3月5日午前10時病院", now)
        assertEquals("病院", month.content)
        assertEquals(LocalDate.parse("2027-03-05"), month.date)
        assertEquals(LocalTime.of(10, 0), month.time)

        val laterThisMonth = VoiceMemoParser.parse("15日の10時30分会議", now)
        assertEquals("会議", laterThisMonth.content)
        assertEquals(LocalDate.parse("2026-11-15"), laterThisMonth.date)
        assertEquals(LocalTime.of(10, 30), laterThisMonth.time)

        val alreadyPassed = VoiceMemoParser.parse("1日に振込", now)
        assertEquals("振込", alreadyPassed.content)
        assertEquals(LocalDate.parse("2026-12-01"), alreadyPassed.date)
        assertNull(alreadyPassed.time)
    }

    @Test
    fun weekdaysAndNextWeek() {
        val fridayNight = VoiceMemoParser.parse("金曜日の夜8時に映画", now)
        assertEquals("映画", fridayNight.content)
        assertEquals(LocalDate.parse("2026-11-06"), fridayNight.date)
        assertEquals(LocalTime.of(20, 0), fridayNight.time)

        val todayWeekday = VoiceMemoParser.parse("水曜日に会議", now)
        assertEquals("会議", todayWeekday.content)
        assertEquals(LocalDate.parse("2026-11-04"), todayWeekday.date)

        val nextMonday = VoiceMemoParser.parse("来週月曜日に掃除", now)
        assertEquals("掃除", nextMonday.content)
        assertEquals(LocalDate.parse("2026-11-09"), nextMonday.date)
        assertNull(nextMonday.time)

        val hiragana = VoiceMemoParser.parse("来週のげつようび", now)
        assertEquals(LocalDate.parse("2026-11-09"), hiragana.date)
        assertEquals("", hiragana.content)

        val withParticle = VoiceMemoParser.parse("来週の月曜日に掃除", now)
        assertEquals("掃除", withParticle.content)
        assertEquals(LocalDate.parse("2026-11-09"), withParticle.date)
    }

    @Test
    fun relativeOffsetsAndKanjiNumbers() {
        val minutes = VoiceMemoParser.parse("30分後に薬", now)
        assertEquals("薬", minutes.content)
        assertEquals(LocalDate.parse("2026-11-04"), minutes.date)
        assertEquals(LocalTime.of(15, 30), minutes.time)

        val hours = VoiceMemoParser.parse("2時間後に出発", now)
        assertEquals("出発", hours.content)
        assertEquals(LocalTime.of(17, 0), hours.time)

        val kanji = VoiceMemoParser.parse("あさっての九時半までに提出", now)
        assertEquals("提出", kanji.content)
        assertEquals(LocalDate.parse("2026-11-06"), kanji.date)
        assertEquals(LocalTime.of(9, 30), kanji.time)

        val twentyThree = VoiceMemoParser.parse("今日の二十三時に終電", now)
        assertEquals("終電", twentyThree.content)
        assertEquals(LocalTime.of(23, 0), twentyThree.time)
    }

    @Test
    fun noonMidnightAndNoSchedule() {
        assertEquals(LocalTime.MIDNIGHT, VoiceMemoParser.parse("午前12時に起床", now).time)
        assertEquals(LocalTime.NOON, VoiceMemoParser.parse("午後12時に昼食", now).time)
        assertEquals(LocalTime.MIDNIGHT, VoiceMemoParser.parse("夜12時に寝坊", now).time)

        val plain = VoiceMemoParser.parse("牛乳を買う", now)
        assertEquals("牛乳を買う", plain.content)
        assertNull(plain.date)
        assertNull(plain.time)

        val dateOnly = VoiceMemoParser.parse("明日病院", now)
        assertEquals("病院", dateOnly.content)
        assertEquals(LocalDate.parse("2026-11-05"), dateOnly.date)
        assertNull(dateOnly.time)
    }

    @Test
    fun fullWidthDigitsAndParticlesAreRemoved() {
        val parsed = VoiceMemoParser.parse("明日の９時に　病院", now)
        assertEquals("病院", parsed.content)
        assertEquals(LocalTime.of(9, 0), parsed.time)
        assertEquals(23, VoiceMemoParser.readNumber("二十三"))
        assertEquals(10, VoiceMemoParser.readNumber("十"))
        assertEquals(15, VoiceMemoParser.readNumber("十五"))
    }
}
