package dev.nossam.task.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

class VoiceCommandParserTest {
    private val now: ZonedDateTime = ZonedDateTime.of(2026, 11, 4, 15, 0, 0, 0, VoiceMemoParser.ZONE)

    @Test
    fun savePhrases() {
        listOf("保存", "保存して", "保存してください", "ほぞんして", "いいよ", "良いよ", "いいです", "OK", "ok", "ＯＫ", "はい", "はいよ").forEach { phrase ->
            assertEquals(phrase, VoiceCommand.Save, VoiceCommandParser.parse(phrase, now))
        }
    }

    @Test
    fun retryPhrases() {
        listOf("やり直し", "やり直して", "やりなおし", "もう一回", "もう1回", "もういっかい", "もう一度お願い").forEach { phrase ->
            assertEquals(phrase, VoiceCommand.Retry, VoiceCommandParser.parse(phrase, now))
        }
    }

    @Test
    fun cancelPhrases() {
        listOf("やめる", "やめて", "やめてください", "キャンセル", "きゃんせる", "いらない", "要らない").forEach { phrase ->
            assertEquals(phrase, VoiceCommand.Cancel, VoiceCommandParser.parse(phrase, now))
        }
    }

    @Test
    fun changesOnlyTheTime() {
        assertEquals(VoiceCommand.SetTime(LocalTime.of(10, 0)), VoiceCommandParser.parse("時間は10時", now))
        assertEquals(VoiceCommand.SetTime(LocalTime.of(15, 30)), VoiceCommandParser.parse("時刻は午後3時半", now))
        assertEquals(VoiceCommand.SetTime(LocalTime.of(15, 30)), VoiceCommandParser.parse("時間は午後3時半にして", now))
        assertEquals(VoiceCommand.SetTime(LocalTime.of(10, 0)), VoiceCommandParser.parse("じかんは10じ", now))
        assertEquals(VoiceCommand.SetTime(LocalTime.of(20, 0)), VoiceCommandParser.parse("時間は夜8時", now))
        assertEquals(VoiceCommand.SetTime(null), VoiceCommandParser.parse("時間なし", now))
        assertEquals(VoiceCommand.SetTime(null), VoiceCommandParser.parse("時刻はなし", now))
        assertEquals(VoiceCommand.SetTime(null), VoiceCommandParser.parse("時間はいらない", now))
    }

    @Test
    fun changesOnlyTheDate() {
        assertEquals(VoiceCommand.SetDate(LocalDate.parse("2026-11-06")), VoiceCommandParser.parse("日付は金曜", now))
        assertEquals(VoiceCommand.SetDate(LocalDate.parse("2026-11-06")), VoiceCommandParser.parse("ひづけはきんようび", now))
        assertEquals(VoiceCommand.SetDate(LocalDate.parse("2026-12-15")), VoiceCommandParser.parse("日にちは12月15日", now))
        assertEquals(VoiceCommand.SetDate(LocalDate.parse("2026-11-05")), VoiceCommandParser.parse("日付は明日", now))
        assertEquals(VoiceCommand.SetDate(null), VoiceCommandParser.parse("日付なし", now))
        assertEquals(VoiceCommand.SetDate(null), VoiceCommandParser.parse("日にちはない", now))
    }

    @Test
    fun changesOnlyTheContent() {
        assertEquals(VoiceCommand.SetContent("歯医者"), VoiceCommandParser.parse("内容は歯医者", now))
        assertEquals(VoiceCommand.SetContent("歯医者の予約"), VoiceCommandParser.parse("内容は歯医者の予約です", now))
        assertEquals(VoiceCommand.SetContent("牛乳を買う"), VoiceCommandParser.parse("ないようは牛乳を買う", now))
    }

    @Test
    fun sayingDoneDuringDictationOpensConfirmWithoutEatingTheMemo() {
        assertEquals(DictationCue.Finish(""), VoiceCommandParser.dictationCue("できた"))
        assertEquals(DictationCue.Finish(""), VoiceCommandParser.dictationCue("できたよ"))
        assertEquals(DictationCue.Finish(""), VoiceCommandParser.dictationCue("できました"))
        assertEquals(DictationCue.Finish(""), VoiceCommandParser.dictationCue("出来た"))
        assertEquals(DictationCue.Finish(""), VoiceCommandParser.dictationCue("えーと、できた"))
        assertEquals(DictationCue.Finish("病院"), VoiceCommandParser.dictationCue("病院 できた"))
        assertEquals(DictationCue.Finish("病院 明日の3時"), VoiceCommandParser.dictationCue("病院 明日の3時、できたよ"))
        assertEquals(DictationCue.Memo("宿題ができた"), VoiceCommandParser.dictationCue("宿題ができた"))
        assertEquals(DictationCue.Memo("できたてパン"), VoiceCommandParser.dictationCue("できたてパン"))
        assertEquals(VoiceCommand.Unrecognized, VoiceCommandParser.parse("できた", now))
    }

    @Test
    fun sayingCancelDuringDictationStopsAndLeavesRealMemosAlone() {
        listOf("やめる", "やめて", "キャンセル", "きゃんせる", "止めてください").forEach { phrase ->
            assertEquals(phrase, DictationCue.Cancel, VoiceCommandParser.dictationCue(phrase))
        }
        assertEquals(DictationCue.Cancel, VoiceCommandParser.dictationCue("病院、やめる"))
        assertEquals(DictationCue.Memo("薬をやめる"), VoiceCommandParser.dictationCue("薬をやめる"))
        assertTrue(VoiceCommandParser.dictationCue("") is DictationCue.Memo)
    }

    @Test
    fun unknownSpeechIsLeftAlone() {
        assertEquals(VoiceCommand.Unrecognized, VoiceCommandParser.parse("天気がいい", now))
        assertEquals(VoiceCommand.Unrecognized, VoiceCommandParser.parse("時間はあした", now))
        assertEquals(VoiceCommand.Unrecognized, VoiceCommandParser.parse("内容は", now))
        assertEquals(VoiceCommand.Unrecognized, VoiceCommandParser.parse("", now))
        assertEquals(
            VoiceCommand.Save,
            VoiceCommandParser.parseFirst(listOf("天気", "保存して"), now),
        )
    }
}
