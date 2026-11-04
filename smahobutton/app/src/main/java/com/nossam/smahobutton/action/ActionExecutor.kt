package com.nossam.smahobutton.action

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.nossam.smahobutton.PermitActivity
import com.nossam.smahobutton.SmahoApp
import com.nossam.smahobutton.apps.InstalledApps
import com.nossam.smahobutton.logic.ActionSpec
import com.nossam.smahobutton.logic.TYPE_BACK
import com.nossam.smahobutton.logic.TYPE_HOME
import com.nossam.smahobutton.logic.TYPE_LOCK
import com.nossam.smahobutton.logic.TYPE_NONE
import com.nossam.smahobutton.logic.TYPE_NOTIFICATIONS
import com.nossam.smahobutton.logic.TYPE_OPEN_APP
import com.nossam.smahobutton.logic.TYPE_RECENTS
import com.nossam.smahobutton.logic.TYPE_REMEMBER_VOLUME
import com.nossam.smahobutton.logic.TYPE_RINGER
import com.nossam.smahobutton.logic.TYPE_SCREENSHOT
import com.nossam.smahobutton.logic.TYPE_SHUTTER
import com.nossam.smahobutton.logic.TYPE_TASK_VOICE
import com.nossam.smahobutton.logic.TYPE_TORCH
import com.nossam.smahobutton.logic.taskVoiceIntent
import com.nossam.smahobutton.logic.TYPE_VOLUME_DOWN
import com.nossam.smahobutton.logic.TYPE_VOLUME_PANEL
import com.nossam.smahobutton.logic.TYPE_VOLUME_UP
import com.nossam.smahobutton.logic.rememberVolume
import com.nossam.smahobutton.runtime.SmahoRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ActionExecutor(private val app: SmahoApp) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var torchOn = false
    private var torchWatching = false

    fun perform(action: ActionSpec) {
        when (action.type) {
            TYPE_NONE, "" -> Unit
            TYPE_SCREENSHOT -> screenshot()
            TYPE_OPEN_APP -> openApp(action.packageName)
            TYPE_TORCH -> toggleTorch()
            TYPE_VOLUME_UP -> adjust(AudioManager.ADJUST_RAISE)
            TYPE_VOLUME_DOWN -> adjust(AudioManager.ADJUST_LOWER)
            TYPE_VOLUME_PANEL -> adjust(AudioManager.ADJUST_SAME)
            TYPE_RINGER -> cycleRinger()
            TYPE_BACK -> global(AccessibilityService.GLOBAL_ACTION_BACK, "戻れませんでした")
            TYPE_HOME -> global(AccessibilityService.GLOBAL_ACTION_HOME, "ホームに戻れませんでした")
            TYPE_RECENTS -> global(AccessibilityService.GLOBAL_ACTION_RECENTS, "最近のアプリを出せませんでした")
            TYPE_NOTIFICATIONS -> global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS, "通知を出せませんでした")
            TYPE_LOCK -> global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN, "画面をロックできませんでした")
            TYPE_REMEMBER_VOLUME -> remember()
            TYPE_SHUTTER -> SmahoRuntime.cameraShutter()
            TYPE_TASK_VOICE -> openTaskVoice()
        }
    }

    private fun openTaskVoice() {
        val spec = taskVoiceIntent()
        val installed = try {
            @Suppress("DEPRECATION")
            app.packageManager.getPackageInfo(spec.packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
        if (!installed) {
            SmahoRuntime.toast("タスクのアプリが見つかりません")
            return
        }
        val intent = Intent()
            .setClassName(spec.packageName, spec.className)
            .putExtra(spec.extraKey, spec.extraValue)
            .setFlags(spec.flags)
        try {
            SmahoRuntime.overlayContext().startActivity(intent)
        } catch (_: Exception) {
            SmahoRuntime.toast("タスクのアプリが見つかりません")
        }
    }

    private fun screenshot() {
        if (SmahoRuntime.accessibility == null) {
            SmahoRuntime.toast("ユーザー補助をオンにしてください")
            return
        }
        SmahoRuntime.holdHidden = true
        SmahoRuntime.refreshButtons()
        scope.launch {
            var ok = false
            try {
                delay(280)
                ok = try {
                    SmahoRuntime.accessibility
                        ?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT) == true
                } catch (_: Exception) {
                    false
                }
                delay(1000)
                if (!ok) SmahoRuntime.toast("スクリーンショットできませんでした")
            } finally {
                SmahoRuntime.holdHidden = false
                SmahoRuntime.refreshButtons()
            }
        }
    }

    private fun openApp(packageName: String?) {
        if (packageName.isNullOrBlank()) {
            SmahoRuntime.toast("アプリが選ばれていません")
            return
        }
        val launch = app.packageManager.getLaunchIntentForPackage(packageName)
        if (launch == null) {
            SmahoRuntime.toast("アプリを開けませんでした")
            return
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            app.startActivity(launch)
        } catch (_: Exception) {
            SmahoRuntime.toast("アプリを開けませんでした")
        }
    }

    private fun toggleTorch() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(app, android.Manifest.permission.CAMERA)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            SmahoRuntime.toast("ライトにはカメラの許可が必要です")
            val intent = Intent(app, PermitActivity::class.java).apply {
                action = PermitActivity.ACTION_CAMERA
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            }
            try {
                app.startActivity(intent)
            } catch (_: Exception) {
                SmahoRuntime.toast("設定を開けませんでした")
            }
            return
        }
        watchTorch()
        val cameras = app.getSystemService(CameraManager::class.java)
        try {
            val id = cameras.cameraIdList.firstOrNull { cameraId ->
                cameras.getCameraCharacteristics(cameraId)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (id == null) {
                SmahoRuntime.toast("このスマホにはライトがありません")
                return
            }
            torchOn = !torchOn
            cameras.setTorchMode(id, torchOn)
        } catch (_: Exception) {
            torchOn = false
            SmahoRuntime.toast("ライトを使えませんでした")
        }
    }

    private fun watchTorch() {
        if (torchWatching) return
        val cameras = app.getSystemService(CameraManager::class.java) ?: return
        cameras.registerTorchCallback(object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                torchOn = enabled
            }
        }, Handler(Looper.getMainLooper()))
        torchWatching = true
    }

    private fun adjust(direction: Int) {
        val audio = app.getSystemService(AudioManager::class.java) ?: return
        try {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        } catch (_: Exception) {
            SmahoRuntime.toast("音量を変えられませんでした")
        }
    }

    private fun cycleRinger() {
        val audio = app.getSystemService(AudioManager::class.java) ?: return
        val next = if (audio.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            AudioManager.RINGER_MODE_VIBRATE
        } else {
            AudioManager.RINGER_MODE_NORMAL
        }
        try {
            audio.ringerMode = next
        } catch (_: SecurityException) {
            askForDnd()
            return
        }
        if (audio.ringerMode != next) {
            askForDnd()
            return
        }
        SmahoRuntime.toast(if (next == AudioManager.RINGER_MODE_VIBRATE) "バイブにしました" else "音を戻しました")
    }

    private fun askForDnd() {
        SmahoRuntime.toast("おやすみモードの許可が必要です")
        try {
            app.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: Exception) {
        }
    }

    private fun global(action: Int, failure: String) {
        val service = SmahoRuntime.accessibility
        if (service == null) {
            SmahoRuntime.toast("ユーザー補助をオンにしてください")
            return
        }
        val ok = try {
            service.performGlobalAction(action)
        } catch (_: Exception) {
            false
        }
        if (!ok) SmahoRuntime.toast(failure)
    }

    private fun remember() {
        val settings = SmahoRuntime.repo.state.value ?: return
        val packageName = SmahoRuntime.foregroundForRemember()
        val output = com.nossam.smahobutton.apps.MediaOutput.current(app)
        val result = rememberVolume(
            packageName = packageName,
            label = packageName?.let { InstalledApps.label(app, it) },
            currentVolume = InstalledApps.musicVolume(app),
            existing = settings.appVolumes,
            filter = SmahoRuntime.currentFilter(),
            output = output,
        )
        if (result.ok) {
            scope.launch {
                SmahoRuntime.repo.update { it.copy(appVolumes = result.updated) }
            }
        }
        SmahoRuntime.toast(result.message)
    }
}
