package com.nossam.smahobutton.logic

const val KIKITORI_PACKAGE = "jp.kikitori"
const val KIKITORI_SERVICE = "jp.kikitori.TalkService"
const val KIKITORI_START = "jp.kikitori.action.TALK_START"
const val KIKITORI_STOP = "jp.kikitori.action.TALK_STOP"
const val KIKITORI_RECEIVER = "receiver"
const val KIKITORI_TEXT = "text"

/** How a press is sent to ききとり. Start and stop share one service. Start must be foreground. */
data class TalkCall(
    val action: String,
    val packageName: String,
    val className: String,
    val foreground: Boolean,
)

fun talkCall(start: Boolean): TalkCall = TalkCall(
    action = if (start) KIKITORI_START else KIKITORI_STOP,
    packageName = KIKITORI_PACKAGE,
    className = KIKITORI_SERVICE,
    foreground = start,
)

/**
 * Inserts [insert] at the cursor. The rest of the field stays.
 * A missing cursor appends. Text is inserted exactly, including any newline it already has.
 */
fun insertAtCursor(current: String, selectionStart: Int, insert: String): Pair<String, Int> {
    val cursor = if (selectionStart < 0) current.length else selectionStart.coerceIn(0, current.length)
    val next = current.substring(0, cursor) + insert + current.substring(cursor)
    return next to (cursor + insert.length)
}
