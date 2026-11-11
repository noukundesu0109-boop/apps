package com.nossam.smahobutton.service

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentCallbacks
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
    private var microphoneOn = false
    private var listening = false
    private val configCallbacks = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            overlay?.relayout()
        }

        @Suppress("DEPRECATION")
        override fun onLowMemory() = Unit
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
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
        if (instance === this) instance = null
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

    /**
     * Adds the microphone foreground type while this process is allowed to use the mic.
     * The button service keeps running as specialUse when this is refused.
     */
    fun promoteMicrophone(force: Boolean = false): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        if (microphoneOn && !force) return true
        microphoneOn = true
        return try {
            startInForeground()
            true
        } catch (error: Exception) {
            Log.w(TAG, "microphone foreground type was not accepted", error)
            microphoneOn = false
            listening = false
            try {
                startInForeground()
            } catch (restore: Exception) {
                Log.w(TAG, "could not restore the button foreground service", restore)
            }
            false
        }
    }

    fun beginListening(): Boolean {
        if (!microphoneOn && !promoteMicrophone()) return false
        listening = true
        return try {
            startInForeground()
            true
        } catch (error: Exception) {
            Log.w(TAG, "listening notification failed", error)
            listening = false
            try {
                startInForeground()
            } catch (_: Exception) {
            }
            false
        }
    }

    fun endListening() {
        if (!listening && !microphoneOn) return
        listening = false
        microphoneOn = false
        try {
            startInForeground()
        } catch (error: Exception) {
            Log.w(TAG, "could not restore the button notification", error)
        }
    }

    private fun startInForeground() {
        ensureChannel(this)
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), foregroundType())
    }

    private fun foregroundType(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0
        var type = 0
        if (Build.VERSION.SDK_INT >= 34) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        }
        if (microphoneOn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        return type
    }

    private fun notification(): Notification {
        val text = if (listening) getString(R.string.notif_listening) else getString(R.string.notif_text)
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_button)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(text)
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

        /** True when the running button service may open the microphone. */
        fun armMicrophone(): Boolean = instance?.promoteMicrophone() == true

        /** Re-applies the microphone type while this process has a visible activity. */
        fun promoteWhileVisible(): Boolean = instance?.promoteMicrophone(force = true) == true

        fun showListening(): Boolean = instance?.beginListening() == true

        fun hideListening() {
            instance?.endListening()
        }

        fun armIfPermitted(context: Context) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            armMicrophone()
        }

        @Volatile
        private var instance: OverlayService? = null
    }
}
