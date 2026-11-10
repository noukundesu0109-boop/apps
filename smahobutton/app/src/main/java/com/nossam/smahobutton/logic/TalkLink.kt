package com.nossam.smahobutton.logic

const val KIKITORI_PACKAGE = "jp.kikitori"
const val KIKITORI_SERVICE = "jp.kikitori.TalkService"
const val KIKITORI_WARM = "jp.kikitori.action.TALK_WARM"
const val KIKITORI_RECOGNIZE = "jp.kikitori.action.TALK_RECOGNIZE"
const val KIKITORI_RECEIVER = "receiver"
const val KIKITORI_AUDIO = "audio"
const val KIKITORI_TEXT = "text"
const val KIKITORI_ERROR = "error"
const val TALK_SAMPLE_RATE = 16000
const val TALK_LOG_TAG = "SmahoTalk"

/** Both calls go to the same service with startService. This app records the audio. */
data class TalkCall(
    val action: String,
    val packageName: String,
    val className: String,
)

fun talkWarm(): TalkCall = TalkCall(KIKITORI_WARM, KIKITORI_PACKAGE, KIKITORI_SERVICE)

fun talkRecognize(): TalkCall = TalkCall(KIKITORI_RECOGNIZE, KIKITORI_PACKAGE, KIKITORI_SERVICE)

/** 44-byte PCM WAV header. 16 kHz, mono, 16-bit, little-endian. */
fun wavHeader(pcmBytes: Int, sampleRate: Int = TALK_SAMPLE_RATE): ByteArray {
    val channels = 1
    val bits = 16
    val byteRate = sampleRate * channels * bits / 8
    val blockAlign = channels * bits / 8
    val header = ByteArray(44)
    fun putAscii(offset: Int, value: String) {
        value.encodeToByteArray().copyInto(header, offset)
    }
    fun putInt(offset: Int, value: Int) {
        header[offset] = (value and 0xff).toByte()
        header[offset + 1] = (value shr 8 and 0xff).toByte()
        header[offset + 2] = (value shr 16 and 0xff).toByte()
        header[offset + 3] = (value shr 24 and 0xff).toByte()
    }
    fun putShort(offset: Int, value: Int) {
        header[offset] = (value and 0xff).toByte()
        header[offset + 1] = (value shr 8 and 0xff).toByte()
    }
    putAscii(0, "RIFF")
    putInt(4, 36 + pcmBytes)
    putAscii(8, "WAVE")
    putAscii(12, "fmt ")
    putInt(16, 16)
    putShort(20, 1)
    putShort(22, channels)
    putInt(24, sampleRate)
    putInt(28, byteRate)
    putShort(32, blockAlign)
    putShort(34, bits)
    putAscii(36, "data")
    putInt(40, pcmBytes)
    return header
}

/**
 * Inserts [insert] at the cursor. The rest of the field stays.
 * A missing cursor appends. Text is inserted exactly, including any newline it already has.
 */
/** Ask the system again whenever the mic is still denied and a prompt is not already open. */
fun shouldAskForMic(granted: Boolean, promptAlreadyOpen: Boolean): Boolean {
    return !granted && !promptAlreadyOpen
}

fun insertAtCursor(current: String, selectionStart: Int, insert: String): Pair<String, Int> {
    val cursor = if (selectionStart < 0) current.length else selectionStart.coerceIn(0, current.length)
    val next = current.substring(0, cursor) + insert + current.substring(cursor)
    return next to (cursor + insert.length)
}
