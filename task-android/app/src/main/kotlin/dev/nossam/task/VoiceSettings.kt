package dev.nossam.task

import android.content.Context

internal object VoiceSettings {
    private const val PREF = "task-voice"
    private const val KEY = "listenOnOpen"

    fun listenOnOpen(context: Context): Boolean {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(KEY, false)
    }

    fun setListenOnOpen(context: Context, on: Boolean) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
    }
}
