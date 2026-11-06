package com.nossam.smahobutton.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nossam.smahobutton.SmahoApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = (appContext as? SmahoApp)?.repository ?: return@launch
                if (repo.current().enabled) {
                    withContext(Dispatchers.Main) { OverlayService.start(appContext) }
                }
            } catch (error: Exception) {
                Log.e(TAG, "boot start failed", error)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "SmahoButton"
    }
}
