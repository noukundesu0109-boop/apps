package dev.nossam.zangyo

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class PermissionActivity : AppCompatActivity() {
    private val requestNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prepareEdgeToEdge()
        setContentView(R.layout.activity_permissions)
        applySafeSystemBarPadding()
        findViewById<Button>(R.id.permNotifButton).setOnClickListener { enableNotifications() }
        findViewById<Button>(R.id.permFullButton).setOnClickListener { openFullScreenSettings() }
        findViewById<Button>(R.id.permBatteryButton).setOnClickListener { openBatterySettings() }
        findViewById<Button>(R.id.permExactButton).setOnClickListener { openExactSettings() }
        findViewById<Button>(R.id.permOpen).setOnClickListener { finish() }
        findViewById<Button>(R.id.permLater).setOnClickListener {
            PermissionGate.suppressForHours(this, 12)
            finish()
        }
    }

    private var askedNotifications = false

    override fun onResume() {
        super.onResume()
        refresh()
        if (!askedNotifications && !PermissionGate.notificationsOk(this)) {
            askedNotifications = true
            enableNotifications()
        }
    }

    private fun refresh() {
        val notifications = PermissionGate.notificationsOk(this)
        val fullScreen = PermissionGate.fullScreenOk(this)
        val battery = PermissionGate.batteryOk(this)
        val exact = PermissionGate.exactOk(this)
        showStatus(R.id.permNotifStatus, R.id.permNotifButton, notifications, "通知をオンにする")
        showStatus(R.id.permFullStatus, R.id.permFullButton, fullScreen, "ロック画面への表示をオンにする")
        showStatus(R.id.permBatteryStatus, R.id.permBatteryButton, battery, "バッテリーの制限を外す")
        val exactBlock = findViewById<View>(R.id.permExactBlock)
        if (exact) {
            exactBlock.visibility = View.GONE
        } else {
            exactBlock.visibility = View.VISIBLE
            showStatus(R.id.permExactStatus, R.id.permExactButton, false, "時刻の許可をオンにする")
        }
        UiBridge.permissionSink?.invoke(PermissionGate.statusJson(this))
    }

    private fun showStatus(statusId: Int, buttonId: Int, ok: Boolean, enableLabel: String) {
        val status = findViewById<TextView>(statusId)
        val button = findViewById<Button>(buttonId)
        if (ok) {
            status.text = "できています"
            status.setTextColor(getColor(R.color.forest))
            button.text = "オンになっています"
            button.isEnabled = false
        } else {
            status.text = "まだです"
            status.setTextColor(getColor(R.color.over))
            button.text = enableLabel
            button.isEnabled = true
        }
    }

    private fun enableNotifications() {
        if (Build.VERSION.SDK_INT < 33 || PermissionGate.notificationsOk(this)) return
        requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun openFullScreenSettings() {
        val intent = if (Build.VERSION.SDK_INT >= 34) {
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri())
        } else {
            appDetailsIntent()
        }
        startOrDetails(intent)
    }

    private fun openBatterySettings() {
        val request = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri())
        try {
            startActivity(request)
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) {
                startOrDetails(appDetailsIntent())
            }
        }
    }

    private fun openExactSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri())
        } else {
            appDetailsIntent()
        }
        startOrDetails(intent)
    }

    private fun startOrDetails(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: Exception) {
            try {
                startActivity(appDetailsIntent())
            } catch (_: Exception) {
            }
        }
    }

    private fun packageUri(): Uri = Uri.parse("package:$packageName")

    private fun appDetailsIntent(): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri())
    }
}
