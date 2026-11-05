package dev.nossam.task.voice

/**
 * How long voice entry keeps listening through a pause.
 * Speech engines may treat the silence values as hints. The command screen
 * also restarts after a pause-shaped error until [COMMAND_WATCH_MILLIS].
 */
object VoiceListen {
    /** Quiet time before the engine should treat the phrase as finished. */
    const val COMPLETE_SILENCE_MILLIS = 6_000

    /** Quiet time before the engine may guess that the phrase is finished. */
    const val POSSIBLY_COMPLETE_SILENCE_MILLIS = 4_000

    /** Do not finish during this opening stretch, even if the room is quiet. */
    const val MINIMUM_LENGTH_MILLIS = 10_000

    /** Backstop for the confirmation listener. Longer than one utterance. */
    const val COMMAND_WATCH_MILLIS = 30_000L

    const val SILENCE_RESTART_DELAY_MILLIS = 400L

    /** How many pause-shaped errors to absorb before giving the command up. */
    const val MAX_SILENCE_RESTARTS = 8

    fun pauseEnded(errorCode: Int): Boolean = errorCode == ERROR_SPEECH_TIMEOUT || errorCode == ERROR_NO_MATCH

    private const val ERROR_SPEECH_TIMEOUT = 6
    private const val ERROR_NO_MATCH = 7
}
