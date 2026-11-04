package dev.nossam.zangyo

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface

class ZangyoJsBridge(
    private val appContext: Context,
    private val ui: Ui,
) {
    interface Ui {
        fun shareBackup(name: String, content: String)
        fun saveToDrive(name: String, content: String)
        fun openPermissions()
        fun requestNotifications()
    }

    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun setAlarmSkipped(id: String, skipped: Boolean) {
        if (id.length > 400) return
        AlarmScheduler.enqueue { AlarmScheduler.setAlarmSkipped(appContext, id, skipped) }
    }

    @JavascriptInterface
    fun onState(json: String) {
        if (json.length > 8_000_000) return
        AlarmScheduler.enqueue { AlarmScheduler.updateWork(appContext, json) }
    }

    @JavascriptInterface
    fun saveBackup(name: String, content: String) {
        if (content.length > 8_000_000) return
        main.post { ui.shareBackup(name, content) }
    }

    @JavascriptInterface
    fun saveToDrive(name: String, content: String) {
        main.post { ui.saveToDrive(name, content) }
    }

    @JavascriptInterface
    fun openPermissions() {
        main.post { ui.openPermissions() }
    }

    @JavascriptInterface
    fun getNextAlarmNotice(): String = if (NextAlarmPrefs.enabled(appContext)) "true" else "false"

    @JavascriptInterface
    fun setNextAlarmNotice(value: String) {
        if (value.length > 8) return
        val on = value == "true"
        NextAlarmPrefs.setEnabled(appContext, on)
        if (on) main.post { ui.requestNotifications() }
        AlarmScheduler.enqueue { AlarmScheduler.reschedule(appContext) }
    }

    @JavascriptInterface
    fun requestNotifications() {
        main.post { ui.requestNotifications() }
    }
}
