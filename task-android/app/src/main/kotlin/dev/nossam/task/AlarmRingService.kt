package dev.nossam.task

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dev.nossam.task.plan.MemoPlanner
import java.util.concurrent.atomic.AtomicBoolean

class AlarmRingService : Service() {
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var title: String = "アラーム"
    private var detail: String = ""
    private var kind: String = ""
    private var itemId: String = ""
    private var tabId: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            AlarmIntents.ACTION_DISMISS -> {
                rememberItem(intent)
                stopFromButton(markDone = TaskAlarms.doneOnStop(this))
                return START_NOT_STICKY
            }
            AlarmIntents.ACTION_MARK_DONE -> {
                rememberItem(intent)
                stopFromButton(markDone = true)
                return START_NOT_STICKY
            }
            AlarmIntents.ACTION_LATER -> {
                rememberItem(intent)
                val laterTitle = intent.getStringExtra(AlarmIntents.EXTRA_TITLE) ?: title
                val laterDetail = intent.getStringExtra(AlarmIntents.EXTRA_DETAIL) ?: detail
                val laterKind = kind
                val laterItem = itemId
                val laterTab = tabId
                closedByButton.set(true)
                stopRinging()
                TaskAlarms.snooze(
                    this,
                    laterTitle,
                    laterDetail,
                    MemoPlanner.LATER_MINUTES,
                    laterKind,
                    laterItem,
                    laterTab,
                )
                return START_NOT_STICKY
            }
            AlarmIntents.ACTION_SNOOZE -> {
                rememberItem(intent)
                val snoozeTitle = intent.getStringExtra(AlarmIntents.EXTRA_TITLE) ?: title
                val snoozeDetail = intent.getStringExtra(AlarmIntents.EXTRA_DETAIL) ?: detail
                val snoozeKind = kind
                val snoozeItem = itemId
                val snoozeTab = tabId
                closedByButton.set(true)
                stopRinging()
                TaskAlarms.snooze(this, snoozeTitle, snoozeDetail, kind = snoozeKind, itemId = snoozeItem, tabId = snoozeTab)
                return START_NOT_STICKY
            }
            AlarmIntents.ACTION_SWIPE -> {
                if (closedByButton.getAndSet(false)) {
                    stopRinging()
                    return START_NOT_STICKY
                }
                rememberItem(intent)
                stopFromButton(markDone = TaskAlarms.doneOnStop(this))
                return START_NOT_STICKY
            }
        }
        title = intent?.getStringExtra(AlarmIntents.EXTRA_TITLE)?.ifBlank { null } ?: "アラーム"
        detail = intent?.getStringExtra(AlarmIntents.EXTRA_DETAIL).orEmpty()
        kind = intent?.getStringExtra(AlarmIntents.EXTRA_KIND).orEmpty()
        itemId = intent?.getStringExtra(AlarmIntents.EXTRA_ITEM).orEmpty()
        tabId = intent?.getStringExtra(AlarmIntents.EXTRA_TAB).orEmpty()
        closedByButton.set(false)
        ensureAlarmChannel(this)
        val type = if (Build.VERSION.SDK_INT >= 34) {
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, AlarmIntents.NOTIFICATION_ID, buildNotification(), type)
        ringing.set(true)
        acquireWakeLock()
        startSound()
        startVibration()
        openScreen()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseSound()
        ringing.set(false)
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            7,
            screenIntent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val done = PendingIntent.getService(
            this,
            8,
            itemIntent(AlarmIntents.ACTION_MARK_DONE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val later = PendingIntent.getService(
            this,
            9,
            itemIntent(AlarmIntents.ACTION_LATER),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val swipe = PendingIntent.getService(
            this,
            12,
            itemIntent(AlarmIntents.ACTION_SWIPE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, AlarmIntents.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(title)
            .setContentText(detail.ifBlank { "アラーム" })
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(false)
            .setAutoCancel(false)
            .setDeleteIntent(swipe)
            .setFullScreenIntent(open, true)
            .setContentIntent(open)
            .addAction(0, "済み", done)
            .addAction(0, "あとで", later)
            .build()
    }

    private fun itemIntent(action: String): Intent {
        return Intent(this, AlarmRingService::class.java).setAction(action).apply {
            putExtra(AlarmIntents.EXTRA_TITLE, title)
            putExtra(AlarmIntents.EXTRA_DETAIL, detail)
            putExtra(AlarmIntents.EXTRA_KIND, kind)
            putExtra(AlarmIntents.EXTRA_ITEM, itemId)
            putExtra(AlarmIntents.EXTRA_TAB, tabId)
        }
    }

    private fun rememberItem(intent: Intent) {
        intent.getStringExtra(AlarmIntents.EXTRA_TITLE)?.takeIf { it.isNotBlank() }?.let { title = it }
        intent.getStringExtra(AlarmIntents.EXTRA_DETAIL)?.let { detail = it }
        intent.getStringExtra(AlarmIntents.EXTRA_KIND)?.let { kind = it }
        intent.getStringExtra(AlarmIntents.EXTRA_ITEM)?.let { itemId = it }
        intent.getStringExtra(AlarmIntents.EXTRA_TAB)?.let { tabId = it }
    }

    private fun stopFromButton(markDone: Boolean) {
        val doneKind = kind
        val doneItem = itemId
        val doneTab = tabId
        closedByButton.set(true)
        stopRinging()
        if (markDone && doneItem.isNotBlank()) {
            TaskAlarms.complete(this, doneKind.ifBlank { MemoPlanner.KIND_MEMO }, doneItem, doneTab)
        }
    }

    private fun screenIntent(): Intent {
        return Intent(this, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(AlarmIntents.EXTRA_TITLE, title)
            putExtra(AlarmIntents.EXTRA_DETAIL, detail)
            putExtra(AlarmIntents.EXTRA_KIND, kind)
            putExtra(AlarmIntents.EXTRA_ITEM, itemId)
            putExtra(AlarmIntents.EXTRA_TAB, tabId)
        }
    }

    private fun openScreen() {
        try {
            startActivity(screenIntent())
        } catch (_: Exception) {
            // The full-screen notification is the other way onto the lock screen.
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val power = getSystemService(PowerManager::class.java) ?: return
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "task:alarm").apply {
            setReferenceCounted(false)
            acquire(10 * 60 * 1000L)
        }
    }

    private fun startSound() {
        releasePlayer()
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val audio = getSystemService(AudioManager::class.java)
        if (audio != null) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(attributes)
                .build()
            focus = request
            audio.requestAudioFocus(request)
        }
        val created = MediaPlayer()
        player = created
        created.setAudioAttributes(attributes)
        created.isLooping = true
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            created.setDataSource(this, uri)
            created.prepare()
            created.start()
        } catch (_: Exception) {
            try {
                created.reset()
                created.setAudioAttributes(attributes)
                created.isLooping = true
                resources.openRawResourceFd(R.raw.alarm_fallback).use { fd ->
                    created.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                }
                created.prepare()
                created.start()
            } catch (_: Exception) {
                created.release()
                player = null
            }
        }
    }

    private fun startVibration() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 400, 700, 800), 0))
    }

    private fun stopRinging() {
        releaseSound()
        ringing.set(false)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releaseSound() {
        releasePlayer()
        val audio = getSystemService(AudioManager::class.java)
        val request = focus
        if (audio != null && request != null) audio.abandonAudioFocusRequest(request)
        focus = null
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator?.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun releasePlayer() {
        val current = player ?: return
        player = null
        try {
            if (current.isPlaying) current.stop()
        } catch (_: Exception) {
        }
        current.release()
    }

    companion object {
        private val ringing = AtomicBoolean(false)
        private val closedByButton = AtomicBoolean(false)

        fun isRinging(): Boolean = ringing.get()
    }
}
