package com.nossam.smahobutton.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TalkLinkTest {
    @Test
    fun warmAndRecognizeUseTheSameServiceAndThisAppDoesNotAskKikitoriToRecord() {
        val warm = talkWarm()
        val recognize = talkRecognize()
        assertEquals("jp.kikitori", warm.packageName)
        assertEquals("jp.kikitori.TalkService", warm.className)
        assertEquals("jp.kikitori.action.TALK_WARM", warm.action)
        assertEquals(warm.packageName, recognize.packageName)
        assertEquals(warm.className, recognize.className)
        assertEquals("jp.kikitori.action.TALK_RECOGNIZE", recognize.action)
        assertEquals("receiver", KIKITORI_RECEIVER)
        assertEquals("audio", KIKITORI_AUDIO)
        assertEquals("text", KIKITORI_TEXT)
        assertEquals("error", KIKITORI_ERROR)
        assertFalse(warm.action.contains("TALK_START"))
        assertFalse(recognize.action.contains("TALK_START"))
        assertFalse(warm.action.contains("TALK_STOP"))
        assertFalse(recognize.action.contains("TALK_STOP"))
    }

    @Test
    fun wavHeaderIs16kHzMonoPcm() {
        val header = wavHeader(pcmBytes = 3200)
        assertEquals(44, header.size)
        assertEquals("RIFF", header.decodeToString(0, 4))
        assertEquals("WAVE", header.decodeToString(8, 12))
        assertEquals("fmt ", header.decodeToString(12, 16))
        assertEquals("data", header.decodeToString(36, 40))
        assertEquals(36 + 3200, leInt(header, 4))
        assertEquals(1, leShort(header, 20))
        assertEquals(1, leShort(header, 22))
        assertEquals(TALK_SAMPLE_RATE, leInt(header, 24))
        assertEquals(TALK_SAMPLE_RATE * 2, leInt(header, 28))
        assertEquals(2, leShort(header, 32))
        assertEquals(16, leShort(header, 34))
        assertEquals(3200, leInt(header, 40))
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

    private fun leInt(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)
    }

    private fun leShort(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)
    }
}
