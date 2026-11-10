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
import com.nossam.smahobutton.logic.talkRecognize
import com.nossam.smahobutton.logic.talkWarm
import java.io.File

/**
 * ききとり recognizes audio this app recorded.
 * A missing app or a failed start is ignored. Nothing is shown.
 */
object Kikitori {
    private const val TAG = "SmahoButton"

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
        val pending = object : ResultReceiver(Handler(Looper.getMainLooper())) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                val error = resultData?.getString(KIKITORI_ERROR)
                if (resultCode != 0 && !error.isNullOrBlank()) Log.d(TAG, error)
                discard(context, uri, file)
                if (delivered) return
                val text = resultData?.getString(KIKITORI_TEXT)
                if (resultCode == 0 && !text.isNullOrEmpty()) delivered = true
                onResult(resultCode, text)
            }
        }
        val call = talkRecognize()
        val intent = Intent(call.action)
            .setClassName(call.packageName, call.className)
            .putExtra(KIKITORI_RECEIVER, pending)
            .putExtra(KIKITORI_AUDIO, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.clipData = ClipData.newRawUri("audio", uri)
        return try {
            context.grantUriPermission(KIKITORI_PACKAGE, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startService(intent)
            true
        } catch (_: Exception) {
            discard(context, uri, file)
            false
        }
    }

    private fun discard(context: Context, uri: Uri, file: File) {
        runCatching { context.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        file.delete()
    }
}
