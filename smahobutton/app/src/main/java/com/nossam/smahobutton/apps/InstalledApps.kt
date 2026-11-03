package com.nossam.smahobutton.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import com.nossam.smahobutton.logic.InstalledApp
import java.util.Locale

object InstalledApps {
    fun load(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                val label = info.loadLabel(pm).toString().trim().ifBlank { pkg }
                InstalledApp(pkg, label)
            }
            .distinctBy { it.packageName }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label.lowercase(Locale.JAPAN) })
    }

    fun label(context: Context, packageName: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    fun musicVolume(context: Context): Int =
        context.getSystemService(AudioManager::class.java)?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0

    fun maxMusicVolume(context: Context): Int =
        context.getSystemService(AudioManager::class.java)
            ?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            ?.coerceAtLeast(1)
            ?: 15
}
