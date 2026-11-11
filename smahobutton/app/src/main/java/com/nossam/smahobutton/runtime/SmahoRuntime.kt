package com.nossam.smahobutton.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.util.Log
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.Manifest
import com.nossam.smahobutton.PermitActivity
import com.nossam.smahobutton.SmahoApp
import com.nossam.smahobutton.action.ActionExecutor
import com.nossam.smahobutton.action.Kikitori
import com.nossam.smahobutton.action.TalkRecorder
import com.nossam.smahobutton.a11y.SmahoAccessibilityService
import com.nossam.smahobutton.data.SettingsRepository
import com.nossam.smahobutton.logic.ActionSpec
import com.nossam.smahobutton.logic.ButtonSpec
import com.nossam.smahobutton.apps.MediaOutput
import com.nossam.smahobutton.logic.FilterConfig
import com.nossam.smahobutton.logic.LEGACY_BLUETOOTH_KEY
import com.nossam.smahobutton.logic.OutputSlot
import com.nossam.smahobutton.logic.VOLUME_REINFORCE_AT_MS
import com.nossam.smahobutton.logic.VOLUME_STEP_GAP_MS
import com.nossam.smahobutton.logic.VolumeApplyNote
import com.nossam.smahobutton.logic.displayKind
import com.nossam.smahobutton.logic.savedDeviceFor
import com.nossam.smahobutton.logic.useGradualVolume
import com.nossam.smahobutton.logic.applyForeground
import com.nossam.smahobutton.logic.canonicalVolumePackage
import com.nossam.smahobutton.logic.isLauncherPackage
import com.nossam.smahobutton.logic.volumeForDevice
import kotlinx.coroutines.flow.MutableStateFlow
import com.nossam.smahobutton.logic.ForegroundVolumeCoordinator
import com.nossam.smahobutton.logic.GESTURE_DOUBLE
import com.nossam.smahobutton.logic.GESTURE_LONG
import com.nossam.smahobutton.logic.GESTURE_TAP
import android.provider.MediaStore
import com.nossam.smahobutton.logic.ShutterSpot
import com.nossam.smahobutton.logic.isCameraPackage
import com.nossam.smahobutton.logic.orientationKey
import com.nossam.smahobutton.logic.SettingsSpec
import com.nossam.smahobutton.logic.TALK_LOG_TAG
import com.nossam.smahobutton.logic.shouldAskForMic
import com.nossam.smahobutton.overlay.RegionCapture
import com.nossam.smahobutton.overlay.ShutterAim
import com.nossam.smahobutton.overlay.BubbleToast
import com.nossam.smahobutton.overlay.FloatingOverlay
import com.nossam.smahobutton.service.OverlayService
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object SmahoRuntime {
    private const val TAG = "SmahoButton"
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
    private var talkGeneration = 0
    private var micPromptOpen = false
    private var talkUiRequested = false
    private var recordingFromActivity = false
    private val talkHandler = Handler(Looper.getMainLooper())
    private val insertDelaysMs = intArrayOf(250, 500, 800)

    var accessibility: SmahoAccessibilityService? = null
    var a11yOverlay: FloatingOverlay? = null
    var serviceOverlay: FloatingOverlay? = null

    @Volatile
    var holdHidden: Boolean = false

    val confirmedPackage: String? get() = coordinator.confirmedPackage

    /** Context that already has a window on screen, so a new activity can open from the background. */
    fun overlayContext(): android.content.Context =
        a11yOverlay?.hostContext() ?: accessibility ?: serviceOverlay?.hostContext() ?: app

    private var audioHooked = false
    private var settleJob: Job? = null
    private var stepJob: Job? = null
    private var applyingVolume = false
    private var applyGeneration = 0
    private var lastWritten: Int? = null
    private var userPaused = false
    private var pausePackage: String? = null
    private var pauseKey: String? = null
    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            if (applyingVolume) return
            val stream = intent?.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1) ?: return
            if (stream != AudioManager.STREAM_MUSIC) return
            val value = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", -1)
            if (value < 0 || value == lastWritten) return
            userPaused = true
            settleJob?.cancel()
            stepJob?.cancel()
            Log.i(TAG, "manual volume $value, stop reapply")
        }
    }
    val lastApply = MutableStateFlow<VolumeApplyNote?>(null)
    private val audioCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = onOutputChanged()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = onOutputChanged()
    }
    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
            val media = configs.any { config ->
                runCatching { config.audioAttributes.usage == AudioAttributes.USAGE_MEDIA }.getOrDefault(false)
            }
            if (media) onPlaybackStarted()
        }
    }

    fun install(application: SmahoApp, repository: SettingsRepository) {
        app = application
        repo = repository
        if (!audioHooked) {
            val audio = app.getSystemService(AudioManager::class.java)
            val handler = Handler(Looper.getMainLooper())
            audio?.registerAudioDeviceCallback(audioCallback, handler)
            audio?.registerAudioPlaybackCallback(playbackCallback, handler)
            runCatching {
                ContextCompat.registerReceiver(
                    app,
                    volumeReceiver,
                    IntentFilter("android.media.VOLUME_CHANGED_ACTION"),
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
            }
            audioHooked = true
        }
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

        override fun onMoved(id: String, edge: String, xFraction: Float, yFraction: Float) {
            scope.launch {
                repo.update { settings ->
                    settings.copy(
                        buttons = settings.buttons.map { button ->
                            if (button.id == id) {
                                button.copy(edge = edge, xFraction = xFraction, yFraction = yFraction)
                            } else {
                                button
                            }
                        },
                    )
                }
            }
        }

        override fun onMenuAction(action: ActionSpec) {
            executor.perform(action)
        }

        override fun onTalkDown(): Boolean = talkDown()

        override fun onTalkUp() = talkUp()

        override fun onTalkCancel() = talkCancel()

        override fun keepTalkOnCancel(): Boolean = talkUiRequested
    }

    /** Press-and-hold. False means recording did not start, so the button must not stay held. */
    fun talkDown(): Boolean {
        val host = talkContext() ?: return false
        talkGeneration += 1
        Kikitori.warm(host)
        if (!micGranted(host)) {
            requestMic(host)
            return false
        }
        talkUiRequested = true
        if (!com.nossam.smahobutton.TalkListenActivity.open(host)) {
            talkUiRequested = false
            return false
        }
        return true
    }

    /** Called while [com.nossam.smahobutton.TalkListenActivity] is resumed. */
    fun onTalkActivityVisible() {
        if (!talkUiRequested) {
            com.nossam.smahobutton.TalkListenActivity.close()
            return
        }
        if (!OverlayService.promoteWhileVisible()) {
            Log.i(TALK_LOG_TAG, "microphone promote rejected while activity visible")
            abortTalkUi()
            return
        }
        if (recordingFromActivity) return
        val host = talkContext() ?: return
        if (!TalkRecorder.start(host)) {
            Log.i(TALK_LOG_TAG, "recording failed after visible promote")
            abortTalkUi()
            return
        }
        recordingFromActivity = true
        OverlayService.showListening()
        Log.i(TALK_LOG_TAG, "microphone promoted while activity visible")
    }

    fun onTalkActivityDestroyed() {
        if (!talkUiRequested) return
        abortTalkUi()
    }

    fun talkUp() {
        val host = talkContext() ?: return
        talkUiRequested = false
        recordingFromActivity = false
        val file = TalkRecorder.stop()
        if (file == null) {
            OverlayService.hideListening()
            com.nossam.smahobutton.TalkListenActivity.close()
            Log.i(TALK_LOG_TAG, "no audio, receiver not sent")
            return
        }
        Kikitori.recognize(host, file) { code, text ->
            if (code != 0 || text.isNullOrEmpty()) return@recognize
            insertWithRetry(text, 0)
        }
        OverlayService.hideListening()
        com.nossam.smahobutton.TalkListenActivity.close()
    }

    private fun insertWithRetry(text: String, attempt: Int) {
        when (accessibility?.insertTextAtCursor(text)) {
            true -> {
                Log.i(TALK_LOG_TAG, "inserted textLength=${text.length}")
                return
            }
            false -> {
                Log.i(TALK_LOG_TAG, "text arrived but the field rejected the insert")
                return
            }
            null -> Unit
        }
        val delay = insertDelaysMs.getOrNull(attempt)
        if (delay == null) {
            Log.i(TALK_LOG_TAG, "text arrived but no focused field")
            return
        }
        talkHandler.postDelayed({ insertWithRetry(text, attempt + 1) }, delay.toLong())
    }

    /** Finger moved into a drag, or the press was rejected. Drop the audio and do not insert it. */
    fun talkCancel() {
        talkGeneration += 1
        talkUiRequested = false
        recordingFromActivity = false
        val file = TalkRecorder.stop()
        OverlayService.hideListening()
        file?.delete()
        com.nossam.smahobutton.TalkListenActivity.close()
    }

    private fun abortTalkUi() {
        talkUiRequested = false
        recordingFromActivity = false
        TalkRecorder.stop()?.delete()
        OverlayService.hideListening()
        a11yOverlay?.clearTalkHold()
        serviceOverlay?.clearTalkHold()
        com.nossam.smahobutton.TalkListenActivity.close()
    }

    fun micPromptClosed() {
        micPromptOpen = false
    }

    private fun micGranted(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun requestMic(context: Context) {
        if (!shouldAskForMic(granted = false, promptAlreadyOpen = micPromptOpen)) return
        val intent = Intent(context, PermitActivity::class.java).apply {
            action = PermitActivity.ACTION_MIC
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        micPromptOpen = true
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            micPromptOpen = false
        }
    }

    private fun talkContext(): Context? {
        accessibility?.let { return it }
        if (::app.isInitialized) return app
        return null
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

    /** Package to save when the user presses 「音量を覚える」. Asks the screen again. */
    fun foregroundForRemember(): String? {
        val service = accessibility
        if (service != null) return runCatching { service.probeForeground(null) }.getOrNull()
        return confirmedPackage
    }

    fun appLabel(packageName: String): String? {
        if (!::app.isInitialized) return null
        return runCatching {
            val info = app.packageManager.getApplicationInfo(packageName, 0)
            app.packageManager.getApplicationLabel(info).toString()
        }.getOrNull()
    }

    fun onWindowPackage(packageName: String?) {
        if (!::app.isInitialized || !::repo.isInitialized) return
        val settings = repo.state.value ?: return
        val audio = app.getSystemService(AudioManager::class.java) ?: return
        val savedIds = settings.appVolumes.map { it.packageName }
        val label = packageName?.let { appLabel(it) }
        val tracked = packageName?.let { canonicalVolumePackage(it, savedIds, label) }
        if (!packageName.isNullOrBlank() && tracked != packageName) {
            Log.i(TAG, "foreground $packageName ($label) -> $tracked")
        }
        val now = SystemClock.uptimeMillis()
        val output = currentOutput(settings)
        val outcome = coordinator.onWindow(
            packageName = tracked,
            now = now,
            currentVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC),
            saved = resolvedVolumes(settings, output),
            restoreOnLeave = settings.restoreVolumeOnLeave,
            filter = currentFilter(),
            outputKey = output.key,
        )
        deliver(outcome, output, "window")
        schedulePoll()
        armSettle()
        if (pendingShutter && isCameraPackage(packageName)) {
            pendingShutter = false
            scope.launch {
                delay(500)
                shoot(fromButton = false)
            }
        }
    }

    private var pendingShutter = false
    private var aim: ShutterAim? = null
    private var region: RegionCapture? = null

    fun beginRegionCapture() {
        val service = accessibility
        if (service == null) {
            toast("ユーザー補助をオンにしてください")
            return
        }
        if (android.os.Build.VERSION.SDK_INT < 30) {
            toast("この囲みは、新しいスマホで使えます")
            return
        }
        region?.dismiss()
        holdHidden = true
        refreshButtons()
        scope.launch {
            delay(220)
            val current = accessibility
            if (current == null) {
                holdHidden = false
                refreshButtons()
                toast("ユーザー補助をオンにしてください")
                return@launch
            }
            current.captureScreen { bitmap ->
                if (bitmap == null) {
                    holdHidden = false
                    refreshButtons()
                    toast("画面を取れませんでした。ユーザー補助を入れ直してください")
                    return@captureScreen
                }
                val overlay = RegionCapture(
                    context = current,
                    windowType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    bitmap = bitmap,
                    textIn = { current.textInside(it) },
                    onDismiss = {
                        region = null
                        holdHidden = false
                        refreshButtons()
                    },
                    onMessage = { message -> toast(message) },
                )
                region = overlay
                overlay.show()
            }
        }
    }

    fun cameraShutter() {
        val foreground = confirmedPackage
        if (isCameraPackage(foreground)) {
            scope.launch {
                delay(150)
                shoot(fromButton = true)
            }
            return
        }
        if (!openStillCamera()) {
            toast("カメラを開けませんでした")
            return
        }
        if (repo.state.value?.shutterOnOpen == true) pendingShutter = true
    }

    fun beginShutterAim() {
        openStillCamera()
        toast("シャッターの位置をタップしてください")
        scope.launch {
            delay(700)
            val service = accessibility
            val type = if (service != null) {
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            } else if (Settings.canDrawOverlays(app)) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                toast("上に表示の許可が必要です")
                return@launch
            }
            aim?.dismiss()
            val host = service ?: app
            aim = ShutterAim(host, type) { x, y -> saveShutterSpot(x, y) }.also { it.show() }
        }
    }

    private fun shoot(fromButton: Boolean) {
        val service = accessibility
        if (service == null) {
            toast("ユーザー補助をオンにしてください")
            return
        }
        holdHidden = true
        refreshButtons()
        scope.launch {
            delay(80)
            var finished = false
            try {
                service.pressShutter { ok ->
                    if (finished) return@pressShutter
                    finished = true
                    scope.launch {
                        holdHidden = false
                        refreshButtons()
                        if (!ok) toast("シャッターが見つかりませんでした。場所を覚えさせてください")
                    }
                }
            } catch (_: Exception) {
                holdHidden = false
                refreshButtons()
                if (fromButton) toast("シャッターが見つかりませんでした。場所を覚えさせてください")
            }
        }
    }

    private fun saveShutterSpot(x: Float, y: Float) {
        val portrait = app.resources.configuration.orientation != android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val pkg = confirmedPackage?.takeIf { isCameraPackage(it) } ?: "com.google.android.GoogleCamera"
        val orient = orientationKey(portrait)
        scope.launch {
            repo.update { settings ->
                val kept = settings.shutterSpots.filter { it.packageName != pkg || it.orientation != orient }
                settings.copy(shutterSpots = kept + ShutterSpot(pkg, orient, x, y))
            }
            toast("ここをシャッターにしました")
        }
    }

    private fun openStillCamera(): Boolean = try {
        val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        app.startActivity(intent)
        true
    } catch (_: Exception) {
        val fallback = app.packageManager.getLaunchIntentForPackage("com.google.android.GoogleCamera")
        if (fallback == null) {
            false
        } else {
            fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { app.startActivity(fallback) }.isSuccess
        }
    }

    fun currentFilter(): FilterConfig {
        val now = SystemClock.elapsedRealtime()
        filterCache?.let { cached ->
            if (now - filterAt < 30_000) return cached
        }
        val pm = app.packageManager
        val launchers = runCatching {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo
                ?.packageName
                ?.takeIf { it.isNotBlank() && it != "android" && it != app.packageName }
                ?.let { setOf(it) }
                ?: emptySet()
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
            val output = currentOutput(settings)
            val outcome = coordinator.poll(
                now = SystemClock.uptimeMillis(),
                currentVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC),
                saved = resolvedVolumes(settings, output),
                restoreOnLeave = settings.restoreVolumeOnLeave,
                outputKey = output.key,
            )
            deliver(outcome, output, "settle")
        }
    }

    /** Re-reads the real window a few times, then puts the saved level back if playback changed it. */
    private fun armSettle() {
        if (settleJob?.isActive == true) return
        settleJob = scope.launch {
            var elapsed = 0L
            for (mark in VOLUME_REINFORCE_AT_MS) {
                delay(mark - elapsed)
                elapsed = mark
                val probed = accessibility?.let { runCatching { it.probeForeground(null) }.getOrNull() }
                if (!probed.isNullOrBlank()) onWindowPackage(probed)
                reinforceNow("settle")
            }
        }
    }

    private fun onPlaybackStarted() {
        if (applyingVolume || !::app.isInitialized || !::repo.isInitialized) return
        scope.launch {
            val probed = accessibility?.let { runCatching { it.probeForeground(null) }.getOrNull() }
            if (!probed.isNullOrBlank()) onWindowPackage(probed)
            reinforceNow("playback")
        }
    }

    private fun reapplyVolume(settings: SettingsSpec) {
        val packageName = coordinator.confirmedPackage ?: return
        if (coordinator.memory.managedPackage == packageName) return
        val output = currentOutput(settings)
        val saved = resolvedVolumes(settings, output)
        if (packageName !in saved) return
        val audio = app.getSystemService(AudioManager::class.java) ?: return
        val change = applyForeground(
            memory = coordinator.memory,
            packageName = packageName,
            currentVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC),
            saved = saved,
            restoreOnLeave = settings.restoreVolumeOnLeave,
            outputKey = output.key,
            launcherPackages = currentFilter().launcherPackages,
        )
        coordinator.memory = change.memory
        val label = settings.appVolumes.find { it.packageName == packageName }?.label ?: packageName
        if (change.setVolume != null && change.memory.managedPackage == packageName) {
            recordAndSet(change.setVolume, label, output, packageName, "settings")
        } else {
            applyVolume(change.setVolume)
        }
    }

    private fun onOutputChanged() {
        if (!::app.isInitialized || !::repo.isInitialized) return
        reinforceNow("route")
    }

    private fun reinforceNow(reason: String) {
        if (!::app.isInitialized || !::repo.isInitialized) return
        val settings = repo.state.value ?: return
        val packageName = coordinator.confirmedPackage ?: return
        if (isLauncherPackage(packageName, currentFilter().launcherPackages)) return
        val output = currentOutput(settings)
        val saved = resolvedVolumes(settings, output)
        val level = saved[packageName] ?: return
        val audio = app.getSystemService(AudioManager::class.java) ?: return
        noteSession(packageName, output.key)
        if (userPaused) {
            Log.i(TAG, "skip $reason, user changed the volume")
            return
        }
        Log.i(TAG, "reinforce $reason pkg=$packageName key=${output.key} name=${output.name} level=$level class=${output.deviceClass}")
        val change = applyForeground(
            memory = coordinator.memory,
            packageName = packageName,
            currentVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC),
            saved = saved,
            restoreOnLeave = settings.restoreVolumeOnLeave,
            outputKey = output.key,
            launcherPackages = currentFilter().launcherPackages,
            force = true,
        )
        coordinator.memory = change.memory
        val label = settings.appVolumes.find { it.packageName == packageName }?.label ?: packageName
        recordAndSet(change.setVolume ?: level, label, output, packageName, reason)
    }

    private fun deliver(outcome: com.nossam.smahobutton.logic.VolumeOutcome, output: OutputSlot, reason: String) {
        val pkg = coordinator.memory.managedPackage
        val entry = repo.state.value?.appVolumes?.find { it.packageName == pkg }
        if (outcome.setVolume != null && pkg != null && entry != null && outcome.changedPackage == pkg) {
            recordAndSet(outcome.setVolume, entry.label, output, pkg, reason)
        } else {
            applyVolume(outcome.setVolume)
        }
    }

    private fun currentOutput(settings: SettingsSpec): OutputSlot {
        val keys = linkedSetOf<String>()
        val names = linkedSetOf<String>()
        for (appVolume in settings.appVolumes) {
            for (device in appVolume.devices) {
                if (device.key.isBlank() || device.key == LEGACY_BLUETOOTH_KEY) continue
                keys += device.key
                if (device.name.isNotBlank()) names += device.name
            }
        }
        return MediaOutput.current(app, keys, names)
    }

    private fun resolvedVolumes(settings: SettingsSpec, output: OutputSlot): Map<String, Int> =
        settings.appVolumes.associate {
            it.packageName to volumeForDevice(it.volume, it.devices, output)
        }

    private fun noteSession(packageName: String, outputKey: String) {
        if (pausePackage != packageName || pauseKey != outputKey) {
            userPaused = false
            pausePackage = packageName
            pauseKey = outputKey
        }
    }

    private fun recordAndSet(
        volume: Int?,
        appName: String,
        output: OutputSlot,
        packageName: String,
        reason: String,
    ) {
        if (volume == null) return
        noteSession(packageName, output.key)
        if (userPaused) return
        val audio = app.getSystemService(AudioManager::class.java) ?: return
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = volume.coerceIn(0, max)
        val settings = repo.state.value
        val savedDevices = settings?.appVolumes?.find { it.packageName == packageName }?.devices.orEmpty()
        val gradual = useGradualVolume(savedDevices, output)
        val kind = displayKind(output, savedDeviceFor(savedDevices, output))
        val clock = java.text.SimpleDateFormat("H:mm:ss", java.util.Locale.JAPAN).format(java.util.Date())
        lastApply.value = VolumeApplyNote(
            appName = appName.ifBlank { packageName },
            deviceName = output.name,
            volume = target,
            clock = clock,
            gradual = gradual,
            kind = kind,
        )
        Log.i(TAG, "apply $reason $packageName ${output.name} $kind gradual=$gradual key=${output.key} -> $target")
        val generation = ++applyGeneration
        applyingVolume = true
        stepJob?.cancel()
        stepJob = scope.launch {
            try {
                if (gradual) stepVolume(audio, target) else jumpVolume(audio, target)
                lastWritten = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            } finally {
                if (generation == applyGeneration) applyingVolume = false
            }
        }
    }

    private suspend fun stepVolume(audio: AudioManager, target: Int) {
        var guard = 0
        while (guard < 50) {
            val now = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (now == target) return
            val up = now < target
            val key = if (up) KeyEvent.KEYCODE_VOLUME_UP else KeyEvent.KEYCODE_VOLUME_DOWN
            val time = SystemClock.uptimeMillis()
            runCatching {
                audio.dispatchMediaKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_DOWN, key, 0))
                audio.dispatchMediaKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_UP, key, 0))
            }
            delay(VOLUME_STEP_GAP_MS)
            if (audio.getStreamVolume(AudioManager.STREAM_MUSIC) == now) {
                val direction = if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
                runCatching {
                    audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
                }
                delay(VOLUME_STEP_GAP_MS)
                if (audio.getStreamVolume(AudioManager.STREAM_MUSIC) == now) {
                    Log.w(TAG, "volume step stuck at $now toward $target")
                    return
                }
            }
            lastWritten = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            guard++
        }
    }

    private fun jumpVolume(audio: AudioManager, target: Int) {
        if (audio.getStreamVolume(AudioManager.STREAM_MUSIC) == target) return
        try {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        } catch (error: Exception) {
            Log.w(TAG, "setStreamVolume failed", error)
        }
    }

    private fun applyVolume(volume: Int?) {
        if (volume == null) return
        val audio = app.getSystemService(AudioManager::class.java) ?: return
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        jumpVolume(audio, volume.coerceIn(0, max))
    }

    private fun activeOverlay(): FloatingOverlay? = a11yOverlay ?: serviceOverlay
}
