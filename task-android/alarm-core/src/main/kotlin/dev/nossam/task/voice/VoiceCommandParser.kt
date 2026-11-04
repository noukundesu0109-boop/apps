package dev.nossam.task.voice

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

sealed class VoiceCommand {
    data object Save : VoiceCommand()
    data object Retry : VoiceCommand()
    data object Cancel : VoiceCommand()
    data class SetTime(val time: LocalTime?) : VoiceCommand()
    data class SetDate(val date: LocalDate?) : VoiceCommand()
    data class SetContent(val text: String) : VoiceCommand()
    data object Unrecognized : VoiceCommand()
}

/**
 * Reads a short reply on the confirmation screen.
 * 「保存して」 saves, 「時間は10時」 changes only the time, and other speech stays unrecognized.
 */
object VoiceCommandParser {
    private val saveWords = setOf(
        "保存", "ほぞん", "はい", "いい", "よい", "良い", "ok", "okay",
        "おっけー", "おっけい", "おーけー", "オーケー",
    )
    private val retryWords = setOf(
        "やり直し", "やりなおし", "もう一回", "もう1回", "もういっかい", "もう一度", "もういちど",
    )
    private val cancelWords = setOf(
        "やめる", "やめて", "止める", "止めて", "キャンセル", "きゃんせる", "いらない", "要らない", "いらん",
    )
    private val noneWords = setOf("なし", "無い", "ない", "無し", "無", "いらない", "要らない")
    private val timeClear = setOf(
        "時間なし", "時刻なし", "じかんなし", "じこくなし",
        "時間ない", "時刻ない", "じかんない", "じこくない",
        "時間はない", "時刻はない", "じかんはない", "じこくはない",
        "時間はいらない", "時刻はいらない", "じかんはいらない", "じこくはいらない",
    )
    private val dateClear = setOf(
        "日付なし", "日にちなし", "ひづけなし", "ひにちなし",
        "日付ない", "日にちない", "ひづけない", "ひにちない",
        "日付はない", "日にちはない", "ひづけはない", "ひにちはない",
        "日付はいらない", "日にちはいらない",
    )
    private val timePrefixes = listOf("時間は", "時刻は", "じかんは", "じこくは", "時間を", "時刻を", "じかんを", "じこくを", "時間", "時刻", "じかん", "じこく")
    private val datePrefixes = listOf("日付は", "日にちは", "ひづけは", "ひにちは", "日付を", "日にちを", "ひづけを", "ひにちを", "日付", "日にち", "ひづけ", "ひにち")
    private val contentPrefixes = listOf("内容は", "ないようは", "内容を", "ないようを", "内容", "ないよう")
    private val leads = listOf("えーと", "えっと", "ええと", "あのー", "あの", "じゃあ", "じゃ", "うーん", "えー")
    private val politeTails = listOf("お願いします", "お願い", "ください", "下さい", "です", "だよ", "だね", "よ", "ね")
    private val valueTails = listOf(
        "にしてください", "にして下さい", "してください", "して下さい", "にお願い",
        "にして", "にします", "にする", "します", "して", "てください", "て下さい", "てね", "て",
    ) + politeTails

    fun parse(spoken: String, now: ZonedDateTime): VoiceCommand {
        val text = stripLead(normalize(spoken))
        if (text.isEmpty()) return VoiceCommand.Unrecognized
        val softened = stripTail(text, politeTails)
        if (softened in timeClear) return VoiceCommand.SetTime(null)
        if (softened in dateClear) return VoiceCommand.SetDate(null)
        matchEdit(text, now)?.let { return it }
        if (matches(text, saveWords)) return VoiceCommand.Save
        if (matches(text, retryWords)) return VoiceCommand.Retry
        if (matches(text, cancelWords)) return VoiceCommand.Cancel
        return VoiceCommand.Unrecognized
    }

    fun parseFirst(candidates: List<String>, now: ZonedDateTime): VoiceCommand {
        for (candidate in candidates) {
            if (candidate.isBlank()) continue
            val command = parse(candidate, now)
            if (command !is VoiceCommand.Unrecognized) return command
        }
        return VoiceCommand.Unrecognized
    }

    private fun matchEdit(text: String, now: ZonedDateTime): VoiceCommand? {
        takePrefix(text, timePrefixes)?.let { rest ->
            val value = stripTail(rest, valueTails)
            if (value.isEmpty() || value in noneWords) return VoiceCommand.SetTime(null)
            val time = VoiceMemoParser.parse(value, now).time ?: return VoiceCommand.Unrecognized
            return VoiceCommand.SetTime(time)
        }
        takePrefix(text, datePrefixes)?.let { rest ->
            val value = stripTail(rest, valueTails)
            if (value.isEmpty() || value in noneWords) return VoiceCommand.SetDate(null)
            val date = VoiceMemoParser.parse(value, now).date ?: return VoiceCommand.Unrecognized
            return VoiceCommand.SetDate(date)
        }
        takePrefix(text, contentPrefixes)?.let { rest ->
            val value = stripTail(rest, politeTails)
            if (value.isEmpty()) return VoiceCommand.Unrecognized
            return VoiceCommand.SetContent(value)
        }
        return null
    }

    private fun matches(text: String, words: Set<String>): Boolean {
        if (stripTail(text, politeTails + valueTails) in words) return true
        return words.any { word -> text.startsWith(word) && fillerOnly(text.removePrefix(word)) }
    }

    private fun fillerOnly(rest: String): Boolean {
        if (rest.isEmpty() || rest in valueTails) return true
        val stripped = stripTail(rest, valueTails)
        return stripped.isEmpty() || stripped in valueTails
    }

    private fun takePrefix(text: String, prefixes: List<String>): String? {
        val prefix = prefixes.firstOrNull { text.startsWith(it) } ?: return null
        return text.removePrefix(prefix)
    }

    private fun stripLead(text: String): String {
        var value = text
        repeat(4) {
            val lead = leads.firstOrNull { value.startsWith(it) && value.length > it.length } ?: return value
            value = value.removePrefix(lead)
        }
        return value
    }

    private fun stripTail(text: String, tails: List<String>): String {
        var value = text
        val ordered = tails.sortedByDescending { it.length }
        repeat(4) {
            val tail = ordered.firstOrNull { value.endsWith(it) && value.length > it.length } ?: return value
            value = value.removeSuffix(tail)
        }
        return value
    }

    private fun normalize(raw: String): String {
        val mapped = buildString(raw.length) {
            for (ch in raw.trim()) {
                when (ch) {
                    '　', ' ', '\n', '\t', '、', '。', '！', '？', '!', '?', '・', '…' -> Unit
                    in '０'..'９' -> append('0' + (ch - '０'))
                    in 'Ａ'..'Ｚ' -> append('A' + (ch - 'Ａ'))
                    in 'ａ'..'ｚ' -> append('a' + (ch - 'ａ'))
                    in 'ァ'..'ン' -> append((ch.code - 0x60).toChar())
                    else -> append(ch)
                }
            }
        }
        return mapped.lowercase()
    }
}
