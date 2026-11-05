package dev.nossam.task.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceListenTest {
    @Test
    fun aPauseDoesNotCountAsFinishedSpeech() {
        assertTrue(VoiceListen.POSSIBLY_COMPLETE_SILENCE_MILLIS >= 3_000)
        assertTrue(VoiceListen.COMPLETE_SILENCE_MILLIS > VoiceListen.POSSIBLY_COMPLETE_SILENCE_MILLIS)
        assertTrue(VoiceListen.MINIMUM_LENGTH_MILLIS >= 8_000)
        assertTrue(VoiceListen.COMMAND_WATCH_MILLIS >= VoiceListen.MINIMUM_LENGTH_MILLIS)
        assertTrue(VoiceListen.MAX_SILENCE_RESTARTS >= 4)
        assertTrue(VoiceListen.pauseEnded(6))
        assertTrue(VoiceListen.pauseEnded(7))
        assertFalse(VoiceListen.pauseEnded(5))
        assertFalse(VoiceListen.pauseEnded(9))
    }
}
