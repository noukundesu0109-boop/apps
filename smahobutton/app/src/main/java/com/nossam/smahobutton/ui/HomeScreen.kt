package com.nossam.smahobutton.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nossam.smahobutton.logic.SettingsSpec
import com.nossam.smahobutton.system.accessibilitySettingsIntent
import com.nossam.smahobutton.system.appDetailsIntent
import com.nossam.smahobutton.system.batterySettingsIntent
import com.nossam.smahobutton.system.dndSettingsIntent
import com.nossam.smahobutton.system.overlaySettingsIntent
import com.nossam.smahobutton.system.readPermissions
import com.nossam.smahobutton.system.tryStart
import com.nossam.smahobutton.ui.theme.argbColor

@Composable
fun HomeScreen(
    settings: SettingsSpec,
    onToggle: (Boolean) -> Unit,
    onButtons: () -> Unit,
    onVolumes: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var revision by remember { mutableIntStateOf(0) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { revision++ }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) revision++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val permissions = remember(revision) { readPermissions(context) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("スマホボタン", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(
            "画面のうえに、丸いボタンを出せます。",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(argbColor(0xFF1E88E5).copy(alpha = 0.78f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(16.dp).clip(CircleShape).background(Color.White))
                }
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text("ボタンを表示", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "オンにすると、ほかのアプリのうえに出ます。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.enabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.scale(1.15f),
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        checkedThumbColor = Color.White,
                    ),
                )
            }
        }
        if (settings.enabled && !permissions.overlay && !permissions.accessibility) {
            Text(
                "ボタンを出すには、下の「上に表示」か「ユーザー補助」をオンにしてください。",
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text("はじめにやること", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
        CheckCard(
            title = "他のアプリの上に表示",
            body = "ボタンを、ほかのアプリのうえに出す許可です。",
            done = permissions.overlay,
            action = "設定を開く",
            onAction = { context.tryStart(overlaySettingsIntent(context)) },
        )
        CheckCard(
            title = "ユーザー補助（アクセシビリティ）",
            body = "戻る、ホーム、スクリーンショットに使います。",
            done = permissions.accessibility,
            action = "設定を開く",
            onAction = { context.tryStart(accessibilitySettingsIntent()) },
            extra = {
                Text(
                    "グレーで押せないときは、アプリ情報 → 右上の ︙ → 「制限付き設定を許可」。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { context.tryStart(appDetailsIntent(context)) }) {
                    Text("アプリ情報を開く")
                }
            },
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
        CheckCard(
            title = "おやすみモードへのアクセス",
            body = "マナーモードの切り替えに使います。使わないなら、そのままで大丈夫です。",
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
        Text("設定", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
        NavCard("ボタンの設定", "色や、タップしたときの動きを変えます。", onButtons)
        NavCard("アプリごとの音量", "アプリを開いたときの音の大きさを覚えます。", onVolumes)
        Text(
            "スイッチがオンのまま再起動すると、ボタンは自動で戻ります。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
        )
    }
}

@Composable
private fun CheckCard(
    title: String,
    body: String,
    done: Boolean,
    action: String,
    onAction: () -> Unit,
    extra: (@Composable () -> Unit)? = null,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                extra?.invoke()
                if (!done) {
                    TextButton(onClick = onAction) { Text(action) }
                }
            }
            Text(
                if (done) "✓" else "",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NavCard(title: String, body: String, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
