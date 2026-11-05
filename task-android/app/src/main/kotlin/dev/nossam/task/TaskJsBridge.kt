package dev.nossam.task

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface

class TaskJsBridge(
    private val appContext: Context,
    private val ui: Ui,
) {
    interface Ui {
        fun shareBackup(name: String, content: String)
        fun saveToDrive(name: String, content: String)
        fun restoreFromDrive(phase: String)
        fun openPermissions()
        fun startVoice()
        fun finishDictation()
        fun listenForCommand()
        fun stopVoice()
    }

    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onState(json: String) {
        if (json.length > 8_000_000) return
        TaskAlarms.enqueue { TaskAlarms.updateWork(appContext, json) }
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
    fun restoreFromDrive(phase: String) {
        val safe = if (phase == "download") "download" else "preview"
        main.post { ui.restoreFromDrive(safe) }
    }

    @JavascriptInterface
    fun openPermissions() {
        main.post { ui.openPermissions() }
    }

    @JavascriptInterface
    fun startVoice() {
        main.post { ui.startVoice() }
    }

    @JavascriptInterface
    fun finishDictation() {
        main.post { ui.finishDictation() }
    }

    @JavascriptInterface
    fun listenForCommand() {
        main.post { ui.listenForCommand() }
    }

    @JavascriptInterface
    fun stopVoice() {
        main.post { ui.stopVoice() }
    }

    @JavascriptInterface
    fun getListenOnOpen(): String = if (VoiceSettings.listenOnOpen(appContext)) "true" else "false"

    @JavascriptInterface
    fun setListenOnOpen(value: String) {
        if (value.length > 8) return
        VoiceSettings.setListenOnOpen(appContext, value == "true")
    }

    @JavascriptInterface
    fun getSummaryTime(): String = AlarmStore(appContext).readSummaryTime()

    @JavascriptInterface
    fun ackDone(kind: String, id: String) {
        if (kind.length > 20 || id.length > 200) return
        TaskAlarms.enqueue { TaskAlarms.acknowledgeDone(appContext, kind, id) }
    }

    @JavascriptInterface
    fun setSummaryTime(value: String) {
        if (value.length > 8) return
        TaskAlarms.enqueue { TaskAlarms.setSummaryTime(appContext, value) }
    }
}
