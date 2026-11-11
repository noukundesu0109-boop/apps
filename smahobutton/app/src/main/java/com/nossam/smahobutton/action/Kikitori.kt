package com.nossam.smahobutton.action

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.util.Log
import androidx.core.content.FileProvider
import com.nossam.smahobutton.logic.KIKITORI_AUDIO
import com.nossam.smahobutton.logic.KIKITORI_ERROR
import com.nossam.smahobutton.logic.KIKITORI_PACKAGE
import com.nossam.smahobutton.logic.KIKITORI_RECEIVER
import com.nossam.smahobutton.logic.KIKITORI_TEXT
import com.nossam.smahobutton.logic.TALK_LOG_TAG
import com.nossam.smahobutton.logic.talkRecognize
import com.nossam.smahobutton.logic.talkWarm
import java.io.File

/**
 * ききとり recognizes audio this app recorded.
 * A missing app or a failed start is ignored. Nothing is shown.
 */
object Kikitori {
    /** Log if nothing has come back. The receiver stays alive after this. */
    private const val RESULT_LOG_MS = 60_000L

    /** Drop the strong reference only after a late result can no longer arrive. */
    private const val RESULT_HOLD_MS = 180_000L

    private val openReceivers = ArrayList<ResultReceiver>()
    private val main = Handler(Looper.getMainLooper())

    fun warm(context: Context) {
        val call = talkWarm()
        try {
            context.startService(Intent(call.action).setClassName(call.packageName, call.className))
        } catch (_: Exception) {
            // ききとり is not installed, or the process cannot be started.
        }
    }

    fun recognize(context: Context, file: File, onResult: (Int, String?) -> Unit): Boolean {
        val uri = try {
            FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        } catch (_: Exception) {
            file.delete()
            return false
        }
        var delivered = false
        var logWait: Runnable? = null
        var dropWait: Runnable? = null
        val pending = object : ResultReceiver(main) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                val error = resultData?.getString(KIKITORI_ERROR)
                val text = resultData?.getString(KIKITORI_TEXT)
                Log.i(
                    TALK_LOG_TAG,
                    "receiver=present resultCode=$resultCode textLength=${text?.length ?: -1}",
                )
                if (resultCode != 0 && !error.isNullOrBlank()) Log.i(TALK_LOG_TAG, "error")
                logWait?.let { main.removeCallbacks(it) }
                dropWait?.let { main.removeCallbacks(it) }
                release(this)
                discard(context, uri, file)
                if (delivered) return
                if (resultCode == 0 && !text.isNullOrEmpty()) delivered = true
                onResult(resultCode, text)
            }
        }
        hold(pending)
        val logTask = Runnable {
            if (isHeld(pending)) Log.i(TALK_LOG_TAG, "ResultReceiver did not arrive")
        }
        val dropTask = Runnable { release(pending) }
        logWait = logTask
        dropWait = dropTask
        main.postDelayed(logTask, RESULT_LOG_MS)
        main.postDelayed(dropTask, RESULT_HOLD_MS)
        val call = talkRecognize()
        val intent = Intent(call.action)
            .setClassName(call.packageName, call.className)
            .putExtra(KIKITORI_RECEIVER, pending)
            .putExtra(KIKITORI_AUDIO, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.clipData = ClipData.newRawUri("audio", uri)
        return try {
            context.grantUriPermission(KIKITORI_PACKAGE, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            sendRecognize(context, intent)
            Log.i(TALK_LOG_TAG, "TALK_RECOGNIZE receiver=attached")
            true
        } catch (_: Exception) {
            Log.i(TALK_LOG_TAG, "TALK_RECOGNIZE receiver=absent send failed")
            main.removeCallbacks(logTask)
            main.removeCallbacks(dropTask)
            release(pending)
            discard(context, uri, file)
            false
        }
    }

    private fun sendRecognize(context: Context, intent: Intent) {
        try {
            context.startForegroundService(intent)
        } catch (_: Exception) {
            context.startService(intent)
        }
    }

    private fun hold(receiver: ResultReceiver) {
        synchronized(openReceivers) { openReceivers.add(receiver) }
    }

    private fun release(receiver: ResultReceiver) {
        synchronized(openReceivers) { openReceivers.remove(receiver) }
    }

    private fun isHeld(receiver: ResultReceiver): Boolean {
        return synchronized(openReceivers) { receiver in openReceivers }
    }

    private fun discard(context: Context, uri: Uri, file: File) {
        runCatching { context.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        file.delete()
    }
}
