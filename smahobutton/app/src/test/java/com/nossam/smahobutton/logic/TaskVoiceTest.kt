package com.nossam.smahobutton.logic

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskVoiceTest {
    @Test
    fun voiceIntentMatchesTheTaskWidget() {
        val intent = taskVoiceIntent()
        assertEquals("dev.nossam.task", intent.packageName)
        assertEquals("dev.nossam.task.MainActivity", intent.className)
        assertEquals("dev.nossam.task.extra.VOICE", intent.extraKey)
        assertTrue(intent.extraValue)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP, intent.flags)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
    }

    @Test
    fun voiceActionStaysInBackupAndUsesTheMic() {
        val spec = SettingsSpec(
            buttons = listOf(
                ButtonSpec(
                    icon = "mic",
                    tap = ActionSpec(TYPE_TASK_VOICE),
                    doubleTap = ActionSpec(TYPE_TASK_VOICE),
                    longPress = ActionSpec(TYPE_TASK_VOICE),
                    menu = listOf(ActionSpec(TYPE_TASK_VOICE)),
                ),
            ),
        )
        val restored = SettingsJson.decode(SettingsJson.encode(spec))
        val button = restored.buttons.single()
        assertEquals("mic", button.icon)
        assertEquals(TYPE_TASK_VOICE, button.tap.type)
        assertEquals(TYPE_TASK_VOICE, button.doubleTap.type)
        assertEquals(TYPE_TASK_VOICE, button.longPress.type)
        assertEquals(TYPE_TASK_VOICE, button.menu.single().type)
        assertEquals("タスクに声で予定を入れる", actionTitle(button.tap))
        assertEquals("声で予定", actionShort(button.tap))
        assertEquals("mic", actionIcon(button.tap))
        assertEquals("mic", actionIcon(button.menu.single()))
    }
}
