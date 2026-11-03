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
        fun openPermissions()
    }

    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onState(json: String) {
        if (json.length > 8_000_000) return
        AlarmScheduler.enqueue { AlarmScheduler.updateWork(appContext, json) }
    }

    @JavascriptInterface
    fun onReminders(json: String) {
        if (json.length > 2_000_000) return
        AlarmScheduler.enqueue { AlarmScheduler.updateReminders(appContext, json) }
    }

    @JavascriptInterface
    fun saveBackup(name: String, content: String) {
        if (content.length > 8_000_000) return
        main.post { ui.shareBackup(name, content) }
    }

    @JavascriptInterface
    fun openPermissions() {
        main.post { ui.openPermissions() }
    }
}
