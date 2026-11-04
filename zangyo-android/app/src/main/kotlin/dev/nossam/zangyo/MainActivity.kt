package dev.nossam.zangyo

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
import dev.nossam.zangyo.drive.DriveMessages
import dev.nossam.zangyo.drive.DriveNotice
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity(), ZangyoJsBridge.Ui {
    private companion object {
        const val DRIVE_TAG = "ZangyoDrive"
    }
    private lateinit var webView: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var guideShown = false
    private var pageReady = false
    private var pendingAlarmTab = false
    private var askedNotifications = false
    private var driveName: String? = null
    private var driveContent: String? = null
    private val driveBusy = AtomicBoolean(false)
    private val drivePrefix = Regex("^zangyo-backup-")

    private val driveConsent = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        onDriveConsent(result.resultCode, result.data)
    }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        deliver("window.__zangyoPermStatus", PermissionGate.statusJson(this))
        AlarmScheduler.enqueue { AlarmScheduler.reschedule(this) }
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
                deliver("window.__zangyoPermStatus", PermissionGate.statusJson(this@MainActivity))
                UiBridge.lastSchedule?.let { deliver("window.__zangyoShowSchedule", it) }
                openAlarmTab()
                webView?.postDelayed({ openAlarmTab() }, 400)
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
        webView.addJavascriptInterface(ZangyoJsBridge(applicationContext, this), "ZangyoNative")
        UiBridge.scheduleSink = scheduleSink
        UiBridge.permissionSink = permissionSink
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    webView.evaluateJavascript(
                        "(function(){try{return !!(window.__zangyoBack&&window.__zangyoBack())}catch(e){return false}})()",
                    ) { value ->
                        if (value != "true") moveTaskToBack(true)
                    }
                }
            },
        )
        webView.loadUrl("https://appassets.androidplatform.net/assets/index.html")
        noteAlarmTab(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        noteAlarmTab(intent)
        openAlarmTab()
    }

    private fun noteAlarmTab(intent: Intent?) {
        if (intent?.getStringExtra(AlarmIntents.EXTRA_OPEN_TAB) == "alarms") pendingAlarmTab = true
    }

    private fun openAlarmTab() {
        if (!pageReady || !pendingAlarmTab || !::webView.isInitialized) return
        webView.evaluateJavascript(
            "(function(){try{if(window.__zangyoSetTab){window.__zangyoSetTab('alarms');return true}return false}catch(e){return false}})()",
        ) { value ->
            if (value == "true") pendingAlarmTab = false
        }
    }

    override fun requestNotifications() {
        if (Build.VERSION.SDK_INT < 33 || PermissionGate.notificationsOk(this)) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        askedNotifications = true
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onResume() {
        super.onResume()
        deliver("window.__zangyoPermStatus", PermissionGate.statusJson(this))
        if (!guideShown && PermissionGate.needsPrompt(this)) {
            guideShown = true
            startActivity(Intent(this, PermissionActivity::class.java))
        } else if (!askedNotifications && NextAlarmPrefs.enabled(this) && !PermissionGate.notificationsOk(this)) {
            requestNotifications()
        }
        AlarmScheduler.enqueue { AlarmScheduler.reschedule(this) }
    }

    private val scheduleSink: (String) -> Unit = { json ->
        runOnUiThread { deliver("window.__zangyoShowSchedule", json) }
    }
    private val permissionSink: (String) -> Unit = { json ->
        runOnUiThread { deliver("window.__zangyoPermStatus", json) }
    }

    override fun onDestroy() {
        if (UiBridge.scheduleSink === scheduleSink) UiBridge.scheduleSink = null
        if (UiBridge.permissionSink === permissionSink) UiBridge.permissionSink = null
        webView.destroy()
        super.onDestroy()
    }

    override fun shareBackup(name: String, content: String) {
        val safe = name.replace(Regex("""[^0-9A-Za-z._\-]"""), "_").take(80).ifBlank { "zangyo-backup.json" }
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
                                finishDrive(DriveMessages.noticeForStatus(-1))
                            } else {
                                driveConsent.launch(IntentSenderRequest.Builder(pending).build())
                            }
                        } else {
                            val token = auth.accessToken
                            if (token.isNullOrBlank()) finishDrive(DriveMessages.noticeForStatus(-1))
                            else uploadDrive(token)
                        }
                    } catch (error: Throwable) {
                        android.util.Log.e(DRIVE_TAG, "drive consent could not start", error)
                        finishDrive(DriveMessages.noticeForStatus(-1))
                    }
                }
                .addOnFailureListener(this) { error ->
                    try {
                        val code = (error as? ApiException)?.statusCode ?: -1
                        finishDrive(DriveMessages.noticeForStatus(code))
                    } catch (failure: Throwable) {
                        android.util.Log.e(DRIVE_TAG, "drive failure could not be shown", failure)
                        driveBusy.set(false)
                    }
                }
        } catch (error: Throwable) {
            android.util.Log.e(DRIVE_TAG, "drive save crashed", error)
            finishDrive(DriveNotice("error", DriveMessages.GENERIC))
        }
    }

    private fun onDriveConsent(resultCode: Int, data: Intent?) {
        try {
            if (resultCode != RESULT_OK || data == null) {
                finishDrive(DriveNotice("cancel", DriveMessages.CANCEL))
                return
            }
            val auth = Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(data)
            val token = auth.accessToken
            if (token.isNullOrBlank()) finishDrive(DriveMessages.noticeForStatus(-1))
            else uploadDrive(token)
        } catch (error: ApiException) {
            finishDrive(DriveMessages.noticeForStatus(error.statusCode))
        } catch (error: Throwable) {
            android.util.Log.e(DRIVE_TAG, "drive consent failed", error)
            finishDrive(DriveNotice("error", DriveMessages.GENERIC))
        }
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
                    deliver("window.__zangyoDriveDone", json)
                } catch (error: Throwable) {
                    android.util.Log.e(DRIVE_TAG, "drive result could not be shown", error)
                    driveBusy.set(false)
                }
            }
        }.start()
    }

    private fun finishDrive(notice: DriveNotice) {
        driveBusy.set(false)
        try {
            val json = JSONObject()
                .put("ok", false)
                .put("reason", notice.reason)
                .put("message", notice.message)
                .toString()
            deliver("window.__zangyoDriveDone", json)
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
