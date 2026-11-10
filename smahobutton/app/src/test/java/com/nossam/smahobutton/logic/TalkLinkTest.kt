package com.nossam.smahobutton.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TalkLinkTest {
    @Test
    fun talkUsesOneForegroundServiceForStartAndAPlainServiceForStop() {
        val start = talkCall(start = true)
        val stop = talkCall(start = false)
        assertEquals("jp.kikitori", start.packageName)
        assertEquals("jp.kikitori.TalkService", start.className)
        assertEquals("jp.kikitori.action.TALK_START", start.action)
        assertTrue(start.foreground)
        assertEquals(start.packageName, stop.packageName)
        assertEquals(start.className, stop.className)
        assertEquals("jp.kikitori.action.TALK_STOP", stop.action)
        assertFalse(stop.foreground)
        assertEquals("receiver", KIKITORI_RECEIVER)
        assertEquals("text", KIKITORI_TEXT)
    }

    @Test
    fun textIsInsertedAtTheCursorWithoutReplacingTheField() {
        val (next, cursor) = insertAtCursor("おはよう", 2, "ね")
        assertEquals("おはねよう", next)
        assertEquals(3, cursor)
        val (mid, midCursor) = insertAtCursor("abcd", 1, "XY")
        assertEquals("aXYbcd", mid)
        assertEquals(3, midCursor)
        val (end, endCursor) = insertAtCursor("abc", -1, "!")
        assertEquals("abc!", end)
        assertEquals(4, endCursor)
        val (empty, emptyCursor) = insertAtCursor("", 0, "やあ")
        assertEquals("やあ", empty)
        assertEquals(2, emptyCursor)
        val (kept, keptCursor) = insertAtCursor("あい", 1, "う\n")
        assertEquals("あう\nい", kept)
        assertEquals(3, keptCursor)
        val (plain, _) = insertAtCursor("こんにちは", 5, "ね")
        assertEquals("こんにちはね", plain)
        assertFalse(plain.contains("\n"))
    }

    @Test
    fun talkActionKeepsItsNameAndHoldBehavior() {
        val button = ButtonSpec(
            tap = ActionSpec(TYPE_TALK),
            longPress = ActionSpec(TYPE_HOME),
        )
        assertTrue(holdsToTalk(button))
        assertTrue(assignsTalk(button))
        assertFalse(
            holdsToTalk(
                button.copy(
                    useMenu = true,
                    tap = ActionSpec(TYPE_TALK),
                    longPress = ActionSpec(TYPE_NONE),
                    doubleTap = ActionSpec(TYPE_NONE),
                ),
            ),
        )
        assertTrue(holdsToTalk(ButtonSpec(longPress = ActionSpec(TYPE_TALK))))
        assertTrue(assignsTalk(ButtonSpec(useMenu = true, menu = listOf(ActionSpec(TYPE_TALK)))))
        val restored = SettingsJson.decode(SettingsJson.encode(SettingsSpec(buttons = listOf(button))))
        assertEquals(TYPE_TALK, restored.buttons.single().tap.type)
        assertEquals("話す（ききとり）", actionTitle(ActionSpec(TYPE_TALK)))
        assertEquals("話す", actionShort(ActionSpec(TYPE_TALK)))
    }
}
