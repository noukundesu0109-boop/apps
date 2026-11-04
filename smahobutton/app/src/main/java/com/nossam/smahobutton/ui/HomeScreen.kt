package com.nossam.smahobutton.ui

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nossam.smahobutton.MainActivity
import com.nossam.smahobutton.backup.readBackupText
import com.nossam.smahobutton.backup.shareBackup
import com.nossam.smahobutton.logic.BackupRead
import com.nossam.smahobutton.logic.SettingsSpec
import com.nossam.smahobutton.logic.backupDriveName
import com.nossam.smahobutton.logic.backupFileName
import com.nossam.smahobutton.logic.encodeBackup
import com.nossam.smahobutton.logic.readBackup
import com.nossam.smahobutton.system.PermissionSnapshot
import com.nossam.smahobutton.system.accessibilitySettingsIntent
import com.nossam.smahobutton.system.appDetailsIntent
import com.nossam.smahobutton.system.batterySettingsIntent
import com.nossam.smahobutton.system.dndSettingsIntent
import com.nossam.smahobutton.system.overlaySettingsIntent
import com.nossam.smahobutton.system.readPermissions
import com.nossam.smahobutton.system.tryStart
import java.time.LocalDate
import java.time.LocalDateTime

private const val RESTRICTED_HELP =
    "「アプリはアクセスを拒否されました」と出たり、グレーで押せないときは、アプリ情報 → 右上の ︙ → 「制限付き設定を許可」。"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    settings: SettingsSpec,
    onToggle: (Boolean) -> Unit,
    onButtons: () -> Unit,
    onVolumes: () -> Unit,
    onRestore: (SettingsSpec) -> Unit,
    onShutterOnOpen: (Boolean) -> Unit,
    onCalibrateShutter: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var revision by remember { mutableIntStateOf(0) }
    var showChecks by remember { mutableStateOf(false) }
    var showBackup by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<SettingsSpec?>(null) }
    var backupError by remember { mutableStateOf<String?>(null) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { revision++ }
    val bluetoothPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { revision++ }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = context.readBackupText(uri)
        when (val result = readBackup(text)) {
            is BackupRead.Ok -> pendingRestore = result.settings
            is BackupRead.Error -> backupError = result.message
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) revision++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val permissions = remember(revision) { readPermissions(context) }
    val ready = permissions.allGranted
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("スマホボタン", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "画面のうえに、丸いボタンを出せます。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(colors = CardDefaults.cardColors()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Text("ボタンを表示", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "オンにすると、ほかのアプリのうえに出ます。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        checkedThumbColor = Color.White,
                    ),
                )
            }
        }
        if (settings.enabled && !permissions.overlay && !permissions.accessibility) {
            Text(
                "ボタンを出すには、「上に表示」か「ユーザー補助」をオンにしてください。",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (ready && !showChecks) {
            Card(
                onClick = { showChecks = true },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Text(
                    "✓ 準備はぜんぶ済んでいます",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        } else {
            Text("はじめにやること", style = MaterialTheme.typography.titleMedium)
            CheckCard(
                title = "他のアプリの上に表示",
                body = "ボタンを、ほかのアプリのうえに出す許可です。",
                done = permissions.overlay,
                action = "設定を開く",
                onAction = { context.tryStart(overlaySettingsIntent(context)) },
                showRestrictedHelp = !permissions.overlay,
                onAppInfo = { context.tryStart(appDetailsIntent(context)) },
            )
            CheckCard(
                title = "ユーザー補助",
                body = "戻る、ホーム、スクリーンショットに使います。",
                done = permissions.accessibility,
                action = "設定を開く",
                onAction = { context.tryStart(accessibilitySettingsIntent()) },
                showRestrictedHelp = !permissions.accessibility,
                onAppInfo = { context.tryStart(appDetailsIntent(context)) },
            )
            CheckCard(
                title = "通知",
                body = "ボタンを動かし続けるために、通知を出します。",
                done = permissions.notifications,
                action = "許可する",
                onAction = {
                    if (Build.VERSION.SDK_INT >= 33) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            )
            if (Build.VERSION.SDK_INT >= 31) {
                CheckCard(
                    title = "Bluetoothの名前",
                    body = "つないでいる機器の名前を見分けるため。",
                    done = permissions.bluetooth,
                    action = "許可する",
                    onAction = {
                        bluetoothPermission.launch(android.Manifest.permission.BLUETOOTH_CONNECT)
                    },
                )
            }
            CheckCard(
                title = "おやすみモード",
                body = "マナーモードに使います。使わないなら、そのままで大丈夫です。",
                done = permissions.dnd,
                action = "設定を開く",
                onAction = { context.tryStart(dndSettingsIntent()) },
            )
            CheckCard(
                title = "バッテリー制限なし",
                body = "画面を消したあとも、ボタンが止まらないようにします。",
                done = permissions.battery,
                action = "設定を開く",
                onAction = { context.tryStart(batterySettingsIntent(context)) },
            )
            if (ready) {
                TextButton(onClick = { showChecks = false }) { Text("とじる") }
            }
        }
        Text("設定", style = MaterialTheme.typography.titleMedium)
        NavCard("ボタンの設定", onButtons)
        NavCard("アプリごとの音量", onVolumes)
        NavCard("控え（バックアップ）") { showBackup = true }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("開いたらすぐ撮る", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "カメラを開いた直後に、シャッターを押します。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = settings.shutterOnOpen,
                        onCheckedChange = onShutterOnOpen,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            checkedThumbColor = Color.White,
                        ),
                    )
                }
                TextButton(onClick = onCalibrateShutter) { Text("シャッターの場所を覚える") }
            }
        }
        Text(
            "オンのまま再起動すると、ボタンは戻ります。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
    if (showBackup) {
        ModalBottomSheet(onDismissRequest = { showBackup = false }) {
            Column(
                Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("控え（バックアップ）", style = MaterialTheme.typography.titleLarge)
                Text(
                    "ボタン、動き、位置、アプリごとの音量を、一つのファイルに残します。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        val activity = context as? MainActivity ?: return@Button
                        activity.saveToDrive(backupDriveName(LocalDate.now()), encodeBackup(settings))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("ドライブに保存") }
                OutlinedButton(
                    onClick = {
                        context.shareBackup(encodeBackup(settings), backupFileName(LocalDateTime.now()))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("バックアップを保存") }
                OutlinedButton(
                    onClick = {
                        openBackup.launch(arrayOf("application/json", "text/plain", "*/*"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("バックアップから復元") }
            }
        }
    }
    val restoring = pendingRestore
    if (restoring != null) {
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("今の設定を上書きしますか？") },
            text = { Text("ボタン、動き、音量の設定が、控えの内容に変わります。") },
            confirmButton = {
                TextButton(onClick = {
                    onRestore(restoring)
                    pendingRestore = null
                    showBackup = false
                    Toast.makeText(context, "もどしました", Toast.LENGTH_SHORT).show()
                }) { Text("上書きする") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) { Text("やめる") }
            },
        )
    }
    val activity = context as? MainActivity
    val driveNote = activity?.driveNotice
    if (activity != null && driveNote != null) {
        AlertDialog(
            onDismissRequest = { activity.driveNotice = null },
            text = { Text(driveNote) },
            confirmButton = {
                TextButton(onClick = { activity.driveNotice = null }) { Text("閉じる") }
            },
        )
    }
    val error = backupError
    if (error != null) {
        AlertDialog(
            onDismissRequest = { backupError = null },
            title = { Text("このファイルは使えません") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { backupError = null }) { Text("閉じる") }
            },
        )
    }
}

private val PermissionSnapshot.allGranted: Boolean
    get() = overlay && accessibility && notifications && bluetooth && dnd && battery

@Composable
private fun CheckCard(
    title: String,
    body: String,
    done: Boolean,
    action: String,
    onAction: () -> Unit,
    showRestrictedHelp: Boolean = false,
    onAppInfo: (() -> Unit)? = null,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    if (done) "✓" else "",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (showRestrictedHelp) {
                Text(
                    RESTRICTED_HELP,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!done || showRestrictedHelp) {
                Row {
                    if (!done) TextButton(onClick = onAction) { Text(action) }
                    if (showRestrictedHelp && onAppInfo != null) {
                        TextButton(onClick = onAppInfo) { Text("アプリ情報を開く") }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NavCard(title: String, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
