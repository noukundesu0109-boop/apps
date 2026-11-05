package com.nossam.smahobutton.apps

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ScreenshotStore {
    fun savePng(context: Context, bitmap: Bitmap): Uri? {
        val name = "smahobutton-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= 29) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/Screenshots",
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = runCatching {
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        }.getOrNull() ?: return null
        val ok = runCatching {
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } == true
        }.getOrDefault(false)
        if (!ok) {
            runCatching { resolver.delete(uri, null, null) }
            return null
        }
        if (Build.VERSION.SDK_INT >= 29) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            runCatching { resolver.update(uri, values, null, null) }
        }
        return uri
    }

    fun share(context: Context, uri: Uri) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(send, "共有")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(chooser) }
    }
}
