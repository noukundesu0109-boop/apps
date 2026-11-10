package com.nossam.smahobutton.action

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import com.nossam.smahobutton.logic.KIKITORI_RECEIVER
import com.nossam.smahobutton.logic.KIKITORI_TEXT
import com.nossam.smahobutton.logic.TalkCall
import com.nossam.smahobutton.logic.talkCall

/**
 * Hold-to-talk with ききとり's [jp.kikitori.TalkService].
 * A missing app or a failed start only returns false. Nothing is shown.
 */
object Kikitori {
    private var live = false
    private var receiver: ResultReceiver? = null

    fun start(context: Context, onResult: (Int, String?) -> Unit): Boolean {
        var delivered = false
        val pending = object : ResultReceiver(Handler(Looper.getMainLooper())) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                if (delivered) return
                val text = resultData?.getString(KIKITORI_TEXT)
                if (resultCode == 0 && !text.isNullOrEmpty()) delivered = true
                onResult(resultCode, text)
            }
        }
        receiver = pending
        return try {
            context.startForegroundService(intent(talkCall(start = true), pending))
            live = true
            true
        } catch (_: Exception) {
            live = false
            false
        }
    }

    fun stop(context: Context) {
        if (!live) return
        live = false
        val pending = receiver
        try {
            context.startService(intent(talkCall(start = false), pending))
        } catch (_: Exception) {
            // ききとり is gone, or the service is already stopped.
        }
    }

    private fun intent(call: TalkCall, pending: ResultReceiver?): Intent {
        val intent = Intent(call.action).setClassName(call.packageName, call.className)
        if (pending != null) intent.putExtra(KIKITORI_RECEIVER, pending)
        return intent
    }
}
