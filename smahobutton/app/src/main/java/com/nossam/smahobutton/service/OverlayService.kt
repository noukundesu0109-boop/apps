package com.nossam.smahobutton.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentCallbacks
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.nossam.smahobutton.MainActivity
import com.nossam.smahobutton.R
import com.nossam.smahobutton.overlay.FloatingOverlay
import com.nossam.smahobutton.runtime.SmahoRuntime

class OverlayService : Service() {
    private var overlay: FloatingOverlay? = null
    private val configCallbacks = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            overlay?.relayout()
        }

        @Suppress("DEPRECATION")
        override fun onLowMemory() = Unit
    }

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        registerComponentCallbacks(configCallbacks)
        ensureOverlay()
        SmahoRuntime.refreshButtons()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        ensureOverlay()
        SmahoRuntime.refreshButtons()
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val enabled = runCatching { SmahoRuntime.repo.state.value?.enabled == true }.getOrDefault(false)
        if (enabled) {
            try {
                start(applicationContext)
            } catch (error: Exception) {
                Log.w(TAG, "restart after swipe failed", error)
            }
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        unregisterComponentCallbacks(configCallbacks)
        if (SmahoRuntime.serviceOverlay === overlay) SmahoRuntime.serviceOverlay = null
        overlay?.hide()
        overlay = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun ensureOverlay() {
        if (overlay != null || !Settings.canDrawOverlays(this)) return
        overlay = FloatingOverlay(
            this,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            SmahoRuntime.callbacks(),
        )
        SmahoRuntime.serviceOverlay = overlay
    }

    private fun startInForeground() {
        ensureChannel(this)
        val type = if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), type)
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_button)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val TAG = "SmahoButton"

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, OverlayService::class.java))
            } catch (error: Exception) {
                Log.e(TAG, "foreground service start failed", error)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }
}
