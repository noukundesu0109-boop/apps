package dev.nossam.task

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.webkit.WebViewAssetLoader
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import dev.nossam.task.drive.DriveMessages
import dev.nossam.task.drive.DriveNotice
import dev.nossam.task.drive.DrivePlan
import dev.nossam.task.voice.VoiceCommand
import dev.nossam.task.voice.VoiceCommandParser
import dev.nossam.task.voice.VoiceListen
import dev.nossam.task.voice.VoiceMemoParser
import org.json.JSONObject
import java.io.File
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity(), TaskJsBridge.Ui {
    companion object {
        const val EXTRA_VOICE = "dev.nossam.task.extra.VOICE"
        private const val DRIVE_TAG = "TaskDrive"
    }
    private lateinit var webView: WebView
    private var pageReady = false
    private var pendingVoice = false
    private var micForCommand = false
    private var afterMic: (() -> Unit)? = null
    private val voiceHandler = Handler(Looper.getMainLooper())
    private var commandToken = 0
    private var activeCommandToken = 0
    private var commandRetries = 0
    private var silenceRestarts = 0
    private var silenceEpoch = 0
    private var commandWatch: Runnable? = null
    private var speech: SpeechRecognizer? = null
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var guideShown = false
    private var driveName: String? = null
    private var driveContent: String? = null
    private val driveBusy = AtomicBoolean(false)
    private val drivePrefix = Regex("^tasks-backup-")
    private var driveMode = "save"
    private var driveRestoreId: String? = null

    private val driveConsent = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        onDriveConsent(result.resultCode, result.data)
    }

    private val voiceLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        onVoiceResult(result.resultCode, result.data)
    }

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val next = afterMic
        afterMic = null
        if (granted) {
            next?.invoke()
        } else if (micForCommand) {
            deliverCommandMessage(getString(R.string.voice_denied))
        } else {
            deliverVoiceError(getString(R.string.voice_denied))
        }
    }

    private val fileChooser = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val callback = fileCallback
        fileCallback = null
        val uri = result.data?.data
        if (result.resultCode == RESULT_OK && uri != null) {
            callback?.onReceiveValue(arrayOf(uri))
        } else {
            callback?.onReceiveValue(null)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prepareEdgeToEdge()
        webView = WebView(this)
        setContentView(webView)
        applySafeSystemBarPadding()
        webView.setBackgroundColor(Color.parseColor("#F3EFE6"))
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request.url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                pageReady = true
                deliver("window.__taskPermStatus", PermissionGate.statusJson(this@MainActivity))
                UiBridge.lastSchedule?.let { deliver("window.__taskShowSchedule", it) }
                flushDone()
                webView.postDelayed({ flushDone() }, 400)
                maybeStartVoice()
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?,
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = filePathCallback
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(
                        Intent.EXTRA_MIME_TYPES,
                        arrayOf("application/json", "text/plain", "application/octet-stream"),
                    )
                }
                return try {
                    fileChooser.launch(intent)
                    true
                } catch (_: ActivityNotFoundException) {
                    fileCallback = null
                    filePathCallback?.onReceiveValue(null)
                    false
                }
            }

            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                if (result == null) return false
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(message)
                    .setPositiveButton("上書きする") { _, _ -> result.confirm() }
                    .setNegativeButton("やめる") { _, _ -> result.cancel() }
                    .setOnCancelListener { result.cancel() }
                    .show()
                return true
            }

            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                if (result == null) return false
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(message)
                    .setPositiveButton("OK") { _, _ -> result.confirm() }
                    .setOnCancelListener { result.confirm() }
                    .show()
                return true
            }
        }
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_NO_CACHE
            allowFileAccess = false
            builtInZoomControls = false
            displayZoomControls = false
        }
        val debuggable = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        WebView.setWebContentsDebuggingEnabled(debuggable)
        webView.addJavascriptInterface(TaskJsBridge(applicationContext, this), "TaskNative")
        UiBridge.scheduleSink = scheduleSink
        UiBridge.permissionSink = permissionSink
        UiBridge.doneSink = doneSink
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    webView.evaluateJavascript(
                        "(function(){try{return !!(window.__taskBack&&window.__taskBack())}catch(e){return false}})()",
                    ) { value ->
                        if (value != "true") moveTaskToBack(true)
                    }
                }
            },
        )
        webView.loadUrl("https://appassets.androidplatform.net/assets/index.html")
        noteVoiceIntent(intent, savedInstanceState == null)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        noteVoiceIntent(intent, openedFresh = true)
        maybeStartVoice()
    }

    private fun noteVoiceIntent(intent: Intent?, openedFresh: Boolean) {
        if (wantsVoice(intent)) {
            pendingVoice = true
            return
        }
        val launcher = intent?.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_LAUNCHER)
        if (openedFresh && launcher && VoiceSettings.listenOnOpen(this)) pendingVoice = true
    }

    private fun wantsVoice(intent: Intent?): Boolean {
        if (intent == null) return false
        if (intent.getBooleanExtra(EXTRA_VOICE, false)) return true
        return intent.getStringExtra(EXTRA_VOICE) == "true"
    }

    private fun maybeStartVoice() {
        if (!pageReady || !pendingVoice) return
        pendingVoice = false
        startVoice()
    }

    override fun startVoice() {
        stopVoice()
        ensureMic(forCommand = false) { launchRecognizer() }
    }

    override fun listenForCommand() {
        ensureMic(forCommand = true) {
            commandToken += 1
            commandRetries = 1
            silenceRestarts = 0
            beginCommandListen(commandToken, armWatch = true)
        }
    }

    override fun stopVoice() {
        commandToken += 1
        commandRetries = 0
        silenceRestarts = 0
        silenceEpoch += 1
        voiceHandler.removeCallbacksAndMessages(null)
        cancelRecognizer()
        deliverListen(false)
    }

    private fun ensureMic(forCommand: Boolean, next: () -> Unit) {
        micForCommand = forCommand
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            next()
            return
        }
        afterMic = next
        micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun beginCommandListen(token: Int, armWatch: Boolean) {
        if (token != commandToken) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            deliverCommandMessage(getString(R.string.voice_unavailable))
            return
        }
        val recognizer = speechRecognizer()
        if (recognizer == null) {
            deliverCommandMessage(getString(R.string.voice_unavailable))
            return
        }
        activeCommandToken = token
        try {
            recognizer.cancel()
        } catch (_: Throwable) {
        }
        deliverListen(true)
        if (armWatch) {
            silenceRestarts = 0
            watchCommand(token)
        }
        try {
            recognizer.startListening(commandIntent())
        } catch (_: Throwable) {
            onCommandMiss(token)
        }
    }

    private fun watchCommand(token: Int) {
        clearCommandWatch()
        val watch = Runnable {
            if (token != commandToken) return@Runnable
            silenceEpoch += 1
            cancelRecognizer()
            onCommandMiss(token)
        }
        commandWatch = watch
        voiceHandler.postDelayed(watch, VoiceListen.COMMAND_WATCH_MILLIS)
    }

    private fun clearCommandWatch() {
        commandWatch?.let { voiceHandler.removeCallbacks(it) }
        commandWatch = null
    }

    private fun Intent.putUnhurriedListen() {
        putExtra(
            RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
            VoiceListen.COMPLETE_SILENCE_MILLIS.toLong(),
        )
        putExtra(
            RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
            VoiceListen.POSSIBLY_COMPLETE_SILENCE_MILLIS.toLong(),
        )
        putExtra(
            RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
            VoiceListen.MINIMUM_LENGTH_MILLIS.toLong(),
        )
    }

    private fun commandIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ja-JP")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ja-JP")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.voice_command_prompt))
            putUnhurriedListen()
        }
    }

    private fun speechRecognizer(): SpeechRecognizer? {
        speech?.let { return it }
        return try {
            SpeechRecognizer.createSpeechRecognizer(this).also {
                it.setRecognitionListener(commandListener)
                speech = it
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun cancelRecognizer() {
        try {
            speech?.cancel()
        } catch (_: Throwable) {
        }
    }

    private val commandListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onPartialResults(partialResults: Bundle?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onResults(results: Bundle?) {
            val token = activeCommandToken
            if (token != commandToken) return
            clearCommandWatch()
            val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            val command = VoiceCommandParser.parseFirst(heard, ZonedDateTime.now(VoiceMemoParser.ZONE))
            if (command is VoiceCommand.Unrecognized) {
                onCommandMiss(token)
                return
            }
            commandRetries = 0
            deliverListen(false)
            deliver("window.__taskVoiceCommand", commandJson(command).toString())
        }

        override fun onError(error: Int) {
            val token = activeCommandToken
            if (token != commandToken) return
            if (error == SpeechRecognizer.ERROR_CLIENT) return
            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                clearCommandWatch()
                deliverCommandMessage(getString(R.string.voice_denied))
                return
            }
            if (VoiceListen.pauseEnded(error) && silenceRestarts < VoiceListen.MAX_SILENCE_RESTARTS) {
                val epoch = silenceEpoch
                silenceRestarts += 1
                voiceHandler.postDelayed(
                    {
                        if (token == commandToken && epoch == silenceEpoch) {
                            beginCommandListen(token, armWatch = false)
                        }
                    },
                    VoiceListen.SILENCE_RESTART_DELAY_MILLIS,
                )
                return
            }
            onCommandMiss(token)
        }
    }

    private fun onCommandMiss(token: Int) {
        if (token != commandToken) return
        silenceEpoch += 1
        clearCommandWatch()
        if (commandRetries > 0) {
            commandRetries -= 1
            voiceHandler.postDelayed({ beginCommandListen(token, armWatch = true) }, 350)
        } else {
            deliverListen(false)
        }
    }

    private fun commandJson(command: VoiceCommand): JSONObject {
        val json = JSONObject().put("ok", true)
        return when (command) {
            VoiceCommand.Save -> json.put("command", "save")
            VoiceCommand.Retry -> json.put("command", "retry")
            VoiceCommand.Cancel -> json.put("command", "cancel")
            is VoiceCommand.SetTime -> json.put("command", "time").put("time", command.time?.let { "%02d:%02d".format(it.hour, it.minute) } ?: JSONObject.NULL)
            is VoiceCommand.SetDate -> json.put("command", "date").put("date", command.date?.toString() ?: JSONObject.NULL)
            is VoiceCommand.SetContent -> json.put("command", "content").put("text", command.text)
            VoiceCommand.Unrecognized -> JSONObject().put("ok", false)
        }
    }

    private fun deliverListen(on: Boolean) {
        deliver("window.__taskVoiceCommand", JSONObject().put("listening", on).toString())
    }

    private fun deliverCommandMessage(message: String) {
        deliverListen(false)
        val json = JSONObject().put("ok", false).put("message", message).toString()
        deliver("window.__taskVoiceCommand", json)
    }

    private fun launchRecognizer() {
        val recognize = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ja-JP")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ja-JP")
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.voice_prompt))
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putUnhurriedListen()
        }
        if (recognize.resolveActivity(packageManager) == null) {
            deliverVoiceError(getString(R.string.voice_unavailable))
            return
        }
        try {
            voiceLauncher.launch(recognize)
        } catch (_: ActivityNotFoundException) {
            deliverVoiceError(getString(R.string.voice_unavailable))
        }
    }

    private fun onVoiceResult(resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_CANCELED) return
        val spoken = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull { it.isNotBlank() }
        if (resultCode != RESULT_OK || spoken.isNullOrBlank()) {
            deliverVoiceError(getString(R.string.voice_nomatch))
            return
        }
        val parsed = VoiceMemoParser.parse(spoken, ZonedDateTime.now(VoiceMemoParser.ZONE))
        val json = JSONObject()
            .put("ok", true)
            .put("spoken", spoken)
            .put("text", parsed.content)
            .put("date", parsed.date?.toString() ?: JSONObject.NULL)
            .put("time", parsed.time?.let { "%02d:%02d".format(it.hour, it.minute) } ?: JSONObject.NULL)
            .toString()
        deliver("window.__taskVoiceHeard", json)
    }

    private fun deliverVoiceError(message: String) {
        if (!pageReady) Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        val json = JSONObject().put("ok", false).put("message", message).toString()
        deliver("window.__taskVoiceHeard", json)
    }

    override fun onResume() {
        super.onResume()
        deliver("window.__taskPermStatus", PermissionGate.statusJson(this))
        if (!guideShown && PermissionGate.needsPrompt(this)) {
            guideShown = true
            startActivity(Intent(this, PermissionActivity::class.java))
        }
    }

    private val scheduleSink: (String) -> Unit = { json ->
        runOnUiThread { deliver("window.__taskShowSchedule", json) }
    }
    private val permissionSink: (String) -> Unit = { json ->
        runOnUiThread { deliver("window.__taskPermStatus", json) }
    }
    private val doneSink: (String) -> Unit = { json ->
        runOnUiThread { deliver("window.__taskMarkDone", json) }
    }

    private fun flushDone() {
        if (!pageReady) return
        val pending = AlarmStore(applicationContext).readPendingDone()
        for (item in pending) {
            val json = org.json.JSONObject()
                .put("kind", item.kind)
                .put("id", item.id)
                .put("tabId", item.tabId)
                .toString()
            deliver("window.__taskMarkDone", json)
        }
    }

    override fun onDestroy() {
        commandToken += 1
        voiceHandler.removeCallbacksAndMessages(null)
        try {
            speech?.destroy()
        } catch (_: Throwable) {
        }
        speech = null
        if (UiBridge.scheduleSink === scheduleSink) UiBridge.scheduleSink = null
        if (UiBridge.permissionSink === permissionSink) UiBridge.permissionSink = null
        if (UiBridge.doneSink === doneSink) UiBridge.doneSink = null
        webView.destroy()
        super.onDestroy()
    }

    override fun shareBackup(name: String, content: String) {
        val safe = name.replace(Regex("""[^0-9A-Za-z._\-]"""), "_").take(80).ifBlank { "tasks-backup.json" }
        val dir = File(cacheDir, "backup").apply { mkdirs() }
        val file = File(dir, safe)
        file.writeText(content)
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, safe)
            clipData = android.content.ClipData.newRawUri(safe, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(send, "バックアップの保存先"))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "保存先を開けるアプリがありません", Toast.LENGTH_LONG).show()
        }
    }

    override fun openPermissions() {
        startActivity(Intent(this, PermissionActivity::class.java))
    }

    override fun saveToDrive(name: String, content: String) {
        try {
            driveMode = "save"
            if (name.isBlank() || name.length > 200 || content.length > 8_000_000) {
                finishDrive(DriveNotice("error", DriveMessages.GENERIC))
                return
            }
            if (!driveOnline()) {
                finishDrive(DriveNotice("offline", DriveMessages.OFFLINE))
                return
            }
            if (!driveBusy.compareAndSet(false, true)) return
            driveName = name
            driveContent = content
            startDriveAuth()
        } catch (error: Throwable) {
            android.util.Log.e(DRIVE_TAG, "drive save crashed", error)
            finishDrive(DriveNotice("error", DriveMessages.GENERIC))
        }
    }

    override fun restoreFromDrive(phase: String) {
        try {
            driveMode = if (phase == "download") "download" else "preview"
            if (!driveOnline()) {
                finishRestore(DriveNotice("offline", DriveMessages.RESTORE_OFFLINE))
                return
            }
            if (driveMode == "download" && driveRestoreId.isNullOrBlank()) {
                finishRestore(DriveNotice("none", DriveMessages.NONE))
                return
            }
            if (!driveBusy.compareAndSet(false, true)) {
                deliver(
                    "window.__taskDriveRestore",
                    JSONObject().put("ok", false).put("reason", "busy").put("message", DriveMessages.RESTORE_GENERIC).toString(),
                )
                return
            }
            startDriveAuth()
        } catch (error: Throwable) {
            android.util.Log.e(DRIVE_TAG, "drive restore crashed", error)
            finishRestore(DriveNotice("error", DriveMessages.RESTORE_GENERIC))
        }
    }

    private fun startDriveAuth() {
        try {
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(DriveMessages.SCOPE)))
                .build()
            Identity.getAuthorizationClient(this)
                .authorize(request)
                .addOnSuccessListener(this) { auth ->
                    try {
                        if (auth.hasResolution()) {
                            val pending = auth.pendingIntent
                            if (pending == null) {
                                finishAuth(-1)
                            } else {
                                driveConsent.launch(IntentSenderRequest.Builder(pending).build())
                            }
                        } else {
                            val token = auth.accessToken
                            if (token.isNullOrBlank()) finishAuth(-1)
                            else continueDrive(token)
                        }
                    } catch (error: Throwable) {
                        android.util.Log.e(DRIVE_TAG, "drive consent could not start", error)
                        finishAuth(-1)
                    }
                }
                .addOnFailureListener(this) { error ->
                    try {
                        val code = (error as? ApiException)?.statusCode ?: -1
                        finishAuth(code)
                    } catch (failure: Throwable) {
                        android.util.Log.e(DRIVE_TAG, "drive failure could not be shown", failure)
                        driveBusy.set(false)
                    }
                }
        } catch (error: Throwable) {
            android.util.Log.e(DRIVE_TAG, "drive sign-in crashed", error)
            finishAuth(-1)
        }
    }

    private fun onDriveConsent(resultCode: Int, data: Intent?) {
        try {
            if (resultCode != RESULT_OK || data == null) {
                finishAuthNotice(DriveNotice("cancel", DriveMessages.CANCEL))
                return
            }
            val auth = Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(data)
            val token = auth.accessToken
            if (token.isNullOrBlank()) finishAuth(-1)
            else continueDrive(token)
        } catch (error: ApiException) {
            finishAuth(error.statusCode)
        } catch (error: Throwable) {
            android.util.Log.e(DRIVE_TAG, "drive consent failed", error)
            finishAuth(-1)
        }
    }

    private fun continueDrive(token: String) {
        when (driveMode) {
            "preview" -> previewDrive(token)
            "download" -> downloadDrive(token)
            else -> uploadDrive(token)
        }
    }

    private fun authNotice(code: Int): DriveNotice {
        return if (driveMode == "save") DriveMessages.noticeForStatus(code) else DriveMessages.noticeForRestore(code)
    }

    private fun finishAuth(code: Int) = finishAuthNotice(authNotice(code))

    private fun finishAuthNotice(notice: DriveNotice) {
        if (driveMode == "save") finishDrive(notice) else finishRestore(notice)
    }

    private fun uploadDrive(token: String) {
        val name = driveName
        val content = driveContent
        if (name == null || content == null) {
            finishDrive(DriveMessages.noticeForStatus(-1))
            return
        }
        Thread {
            val json = try {
                DriveClient.save(token, name, content, drivePrefix).toString()
            } catch (error: DriveFailure) {
                JSONObject().put("ok", false).put("reason", error.notice.reason).put("message", error.notice.message).toString()
            } catch (error: Throwable) {
                android.util.Log.e(DRIVE_TAG, "drive upload failed", error)
                JSONObject().put("ok", false).put("reason", "error").put("message", DriveMessages.GENERIC).toString()
            }
            runOnUiThread {
                try {
                    driveBusy.set(false)
                    deliver("window.__taskDriveDone", json)
                } catch (error: Throwable) {
                    android.util.Log.e(DRIVE_TAG, "drive result could not be shown", error)
                    driveBusy.set(false)
                }
            }
        }.start()
    }

    private fun previewDrive(token: String) {
        Thread {
            val json = try {
                val file = DriveClient.newestBackup(token, drivePrefix)
                if (file == null) {
                    JSONObject().put("ok", false).put("reason", "none").put("message", DriveMessages.NONE).toString()
                } else {
                    driveRestoreId = file.id
                    JSONObject()
                        .put("ok", true)
                        .put("phase", "preview")
                        .put("when", DrivePlan.savedLabel(file.modifiedTime, file.name))
                        .toString()
                }
            } catch (error: DriveFailure) {
                restoreFailure(error).toString()
            } catch (error: Throwable) {
                android.util.Log.e(DRIVE_TAG, "drive lookup failed", error)
                JSONObject().put("ok", false).put("reason", "error").put("message", DriveMessages.RESTORE_GENERIC).toString()
            }
            runOnUiThread {
                driveBusy.set(false)
                deliver("window.__taskDriveRestore", json)
            }
        }.start()
    }

    private fun downloadDrive(token: String) {
        val id = driveRestoreId
        if (id.isNullOrBlank()) {
            finishRestore(DriveNotice("none", DriveMessages.NONE))
            return
        }
        Thread {
            val json = try {
                val text = DriveClient.download(token, id)
                if (!DrivePlan.looksLikeBackup(text)) {
                    JSONObject().put("ok", false).put("reason", "bad").put("message", DriveMessages.RESTORE_BAD).toString()
                } else {
                    JSONObject().put("ok", true).put("phase", "content").put("content", text).toString()
                }
            } catch (error: DriveFailure) {
                restoreFailure(error).toString()
            } catch (error: Throwable) {
                android.util.Log.e(DRIVE_TAG, "drive download failed", error)
                JSONObject().put("ok", false).put("reason", "error").put("message", DriveMessages.RESTORE_GENERIC).toString()
            }
            runOnUiThread {
                driveBusy.set(false)
                deliver("window.__taskDriveRestore", json)
            }
        }.start()
    }

    private fun restoreFailure(error: DriveFailure): JSONObject {
        val notice = if (error.notice.reason == "drive-api" || error.notice.reason == "bad") {
            error.notice
        } else {
            DriveMessages.noticeForRestore(-1)
        }
        return JSONObject().put("ok", false).put("reason", notice.reason).put("message", notice.message)
    }

    private fun finishRestore(notice: DriveNotice) {
        driveBusy.set(false)
        try {
            val json = JSONObject()
                .put("ok", false)
                .put("reason", notice.reason)
                .put("message", notice.message)
                .toString()
            deliver("window.__taskDriveRestore", json)
        } catch (error: Throwable) {
            android.util.Log.e(DRIVE_TAG, "drive restore message could not be shown", error)
        }
    }

    private fun finishDrive(notice: DriveNotice) {
        driveBusy.set(false)
        try {
            val json = JSONObject()
                .put("ok", false)
                .put("reason", notice.reason)
                .put("message", notice.message)
                .toString()
            deliver("window.__taskDriveDone", json)
        } catch (error: Throwable) {
            android.util.Log.e(DRIVE_TAG, "drive message could not be shown", error)
        }
    }

    private fun driveOnline(): Boolean {
        return try {
            val manager = getSystemService(ConnectivityManager::class.java) ?: return true
            val network = manager.activeNetwork ?: return false
            val caps = manager.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (error: Throwable) {
            android.util.Log.w(DRIVE_TAG, "could not read the network state", error)
            true
        }
    }

    private fun deliver(functionName: String, json: String) {
        if (!::webView.isInitialized) return
        val quoted = JSONObject.quote(json)
        webView.evaluateJavascript("$functionName&&$functionName($quoted)", null)
    }
}
