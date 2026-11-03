package com.nossam.smahobutton.runtime

import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import com.nossam.smahobutton.SmahoApp
import com.nossam.smahobutton.action.ActionExecutor
import com.nossam.smahobutton.a11y.SmahoAccessibilityService
import com.nossam.smahobutton.data.SettingsRepository
import com.nossam.smahobutton.logic.ActionSpec
import com.nossam.smahobutton.logic.ButtonSpec
import com.nossam.smahobutton.logic.FilterConfig
import com.nossam.smahobutton.logic.ForegroundVolumeCoordinator
import com.nossam.smahobutton.logic.GESTURE_DOUBLE
import com.nossam.smahobutton.logic.GESTURE_LONG
import com.nossam.smahobutton.logic.GESTURE_TAP
import com.nossam.smahobutton.logic.SettingsSpec
import com.nossam.smahobutton.logic.applyForeground
import com.nossam.smahobutton.overlay.BubbleToast
import com.nossam.smahobutton.overlay.FloatingOverlay
import com.nossam.smahobutton.service.OverlayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object SmahoRuntime {
    private lateinit var app: SmahoApp
    lateinit var repo: SettingsRepository
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val coordinator = ForegroundVolumeCoordinator()
    private val bubble = BubbleToast()
    private var pollJob: Job? = null
    private var filterCache: FilterConfig? = null
    private var filterAt = 0L
    private val executor by lazy { ActionExecutor(app) }

    var accessibility: SmahoAccessibilityService? = null
    var a11yOverlay: FloatingOverlay? = null
    var serviceOverlay: FloatingOverlay? = null

    @Volatile
    var holdHidden: Boolean = false

    val confirmedPackage: String? get() = coordinator.confirmedPackage

    fun install(application: SmahoApp, repository: SettingsRepository) {
        app = application
        repo = repository
        scope.launch {
            var previous: SettingsSpec? = null
            repository.settings.collect { settings ->
                val wasEnabled = previous?.enabled == true
                val first = previous == null
                val volumeChanged = previous?.appVolumes != settings.appVolumes ||
                    previous?.restoreVolumeOnLeave != settings.restoreVolumeOnLeave
                previous = settings
                refreshButtons(settings)
                if (settings.enabled && (first || !wasEnabled)) {
                    OverlayService.start(app)
                } else if (!settings.enabled && wasEnabled) {
                    OverlayService.stop(app)
                }
                if (first || volumeChanged) reapplyVolume(settings)
            }
        }
    }

    fun callbacks(): FloatingOverlay.Callbacks = object : FloatingOverlay.Callbacks {
        override fun onGesture(button: ButtonSpec, kind: String) {
            if (kind == GESTURE_TAP && button.useMenu) {
                if (button.menu.isEmpty()) toast("メニューが空です")
                else activeOverlay()?.showMenu(button)
                return
            }
            val action = when (kind) {
                GESTURE_DOUBLE -> button.doubleTap
                GESTURE_LONG -> button.longPress
                else -> button.tap
            }
            executor.perform(action)
        }

        override fun onMoved(id: String, edge: String, yFraction: Float) {
            scope.launch {
                repo.update { settings ->
                    settings.copy(
                        buttons = settings.buttons.map { button ->
                            if (button.id == id) button.copy(edge = edge, yFraction = yFraction) else button
                        },
                    )
                }
            }
        }

        override fun onMenuAction(action: ActionSpec) {
            executor.perform(action)
        }
    }

    fun refreshButtons() {
        val settings = if (::repo.isInitialized) repo.state.value else null
        if (settings != null) refreshButtons(settings)
    }

    fun refreshButtons(settings: SettingsSpec) {
        if (!::app.isInitialized) return
        if (!settings.enabled || holdHidden) {
            a11yOverlay?.hide()
            serviceOverlay?.hide()
            return
        }
        if (a11yOverlay != null) {
            serviceOverlay?.hide()
            a11yOverlay?.render(settings.buttons)
        } else if (Settings.canDrawOverlays(app)) {
            serviceOverlay?.render(settings.buttons)
        }
    }

    fun onWindowPackage(packageName: String?) {
        if (!::app.isInitialized || !::repo.isInitialized) return
        val settings = repo.state.value ?: return
        val audio = app.getSystemService(AudioManager::class.java) ?: return
        val now = SystemClock.uptimeMillis()
        val saved = settings.appVolumes.associate { it.packageName to it.volume }
        val outcome = coordinator.onWindow(
            packageName = packageName,
            now = now,
            currentVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC),
            saved = saved,
            restoreOnLeave = settings.restoreVolumeOnLeave,
            filter = currentFilter(),
        )
        applyVolume(outcome.setVolume)
        schedulePoll()
    }

    fun currentFilter(): FilterConfig {
        val now = SystemClock.elapsedRealtime()
        filterCache?.let { cached ->
            if (now - filterAt < 30_000) return cached
        }
        val pm = app.packageManager
        val launchers = runCatching {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                .mapNotNull { it.activityInfo?.packageName }
                .toSet()
        }.getOrDefault(emptySet())
        val imes = runCatching {
            app.getSystemService(InputMethodManager::class.java)
                ?.enabledInputMethodList
                ?.map { it.packageName }
                ?.toSet()
                ?: emptySet()
        }.getOrDefault(emptySet())
        return FilterConfig(app.packageName, launchers, imes).also {
            filterCache = it
            filterAt = now
        }
    }

    fun toast(message: String) {
        if (!::app.isInitialized) return
        val a11y = accessibility
        if (a11y != null) {
            bubble.show(a11y, message, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
            return
        }
        if (Settings.canDrawOverlays(app)) {
            bubble.show(app, message, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            return
        }
        android.widget.Toast.makeText(app, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun schedulePoll() {
        val wait = coordinator.millisUntilPoll(SystemClock.uptimeMillis()) ?: return
        pollJob?.cancel()
        pollJob = scope.launch {
            delay(wait.coerceAtLeast(1L))
            val settings = repo.state.value ?: return@launch
            val audio = app.getSystemService(AudioManager::class.java) ?: return@launch
            val outcome = coordinator.poll(
                now = SystemClock.uptimeMillis(),
                currentVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC),
                saved = settings.appVolumes.associate { it.packageName to it.volume },
                restoreOnLeave = settings.restoreVolumeOnLeave,
            )
            applyVolume(outcome.setVolume)
        }
    }

    private fun reapplyVolume(settings: SettingsSpec) {
        val packageName = coordinator.confirmedPackage ?: return
        if (coordinator.memory.managedPackage == packageName) return
        val saved = settings.appVolumes.associate { it.packageName to it.volume }
        if (packageName !in saved) return
        val audio = app.getSystemService(AudioManager::class.java) ?: return
        val change = applyForeground(
            memory = coordinator.memory,
            packageName = packageName,
            currentVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC),
            saved = saved,
            restoreOnLeave = settings.restoreVolumeOnLeave,
        )
        coordinator.memory = change.memory
        applyVolume(change.setVolume)
    }

    private fun applyVolume(volume: Int?) {
        if (volume == null) return
        val audio = app.getSystemService(AudioManager::class.java) ?: return
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = volume.coerceIn(0, max)
        if (audio.getStreamVolume(AudioManager.STREAM_MUSIC) == target) return
        try {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        } catch (_: Exception) {
        }
    }

    private fun activeOverlay(): FloatingOverlay? = a11yOverlay ?: serviceOverlay
}
