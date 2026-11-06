package com.nossam.smahobutton.backup

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

fun Context.readBackupText(uri: Uri): String? = try {
    contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
} catch (_: Exception) {
    null
}

fun Context.shareBackup(json: String, fileName: String) {
    val dir = File(cacheDir, "backup").apply { mkdirs() }
    val file = File(dir, fileName)
    file.writeText(json, Charsets.UTF_8)
    val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TITLE, fileName)
        clipData = ClipData.newRawUri(fileName, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(send, "バックアップの保存先"))
}
