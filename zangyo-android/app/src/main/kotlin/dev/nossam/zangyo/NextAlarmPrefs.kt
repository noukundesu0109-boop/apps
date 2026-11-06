package dev.nossam.zangyo

import android.content.Context

internal object NextAlarmPrefs {
    private const val PREF = "zangyo-next-alarm"
    private const val KEY = "showNotice"

    fun enabled(context: Context): Boolean {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(KEY, true)
    }

    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
    }
}
