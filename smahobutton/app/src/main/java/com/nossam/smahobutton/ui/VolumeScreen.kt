package com.nossam.smahobutton.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nossam.smahobutton.apps.InstalledApps
import com.nossam.smahobutton.logic.AppSuggestion
import com.nossam.smahobutton.logic.AppVolumeSpec
import com.nossam.smahobutton.logic.SettingsSpec
import com.nossam.smahobutton.logic.suggestApps
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolumeScreen(
    settings: SettingsSpec,
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onAdd: () -> Unit,
) {
    val context = LocalContext.current
    val maxVolume = remember { InstalledApps.maxMusicVolume(context) }
    var installed by remember { mutableStateOf(emptyList<com.nossam.smahobutton.logic.InstalledApp>()) }
    LaunchedEffect(Unit) {
        installed = withContext(Dispatchers.Default) { InstalledApps.load(context) }
    }
    val saved = settings.appVolumes.map { it.packageName }.toSet()
    val suggestions = remember(installed, saved) { suggestApps(installed, saved) }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("アプリごとの音量") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).padding(end = 12.dp)) {
                            Text("アプリを閉じたら元の音量にもどす")
                            Text(
                                "オンのとき、ホームや別のアプリに戻ると、前の音量に戻します。",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Switch(
                            checked = settings.restoreVolumeOnLeave,
                            onCheckedChange = viewModel::setRestore,
                        )
                    }
                }
            }
            if (suggestions.isNotEmpty()) {
                item { Text("おすすめ", style = MaterialTheme.typography.titleMedium) }
                items(suggestions, key = { "s-${it.packageName}" }) { suggestion ->
                    SuggestionRow(suggestion) {
                        viewModel.addAppVolume(
                            suggestion.packageName,
                            suggestion.label,
                            InstalledApps.musicVolume(context).coerceIn(0, maxVolume),
                        )
                    }
                }
            }
            item { Text("覚えているアプリ", style = MaterialTheme.typography.titleMedium) }
            if (settings.appVolumes.isEmpty()) {
                item {
                    Text(
                        "まだありません。下からアプリを追加できます。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(settings.appVolumes, key = { it.packageName }) { entry ->
                VolumeRow(
                    entry = entry,
                    maxVolume = maxVolume,
                    onPhone = { viewModel.setAppVolume(entry.packageName, it) },
                    onCar = { viewModel.setCarVolume(entry.packageName, it) },
                    onRemove = { viewModel.removeAppVolume(entry.packageName) },
                )
            }
            item {
                Button(
                    onClick = onAdd,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp),
                ) { Text("アプリを追加") }
            }
        }
    }
}

@Composable
private fun SuggestionRow(suggestion: AppSuggestion, onAdd: () -> Unit) {
    Card {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(suggestion.packageName)
            Text(suggestion.label, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
            TextButton(onClick = onAdd) { Text("追加") }
        }
    }
}

@Composable
private fun VolumeRow(
    entry: AppVolumeSpec,
    maxVolume: Int,
    onPhone: (Int) -> Unit,
    onCar: (Int?) -> Unit,
    onRemove: () -> Unit,
) {
    var phone by remember(entry.packageName, entry.volume) {
        mutableFloatStateOf(entry.volume.coerceIn(0, maxVolume).toFloat())
    }
    var car by remember(entry.packageName, entry.carVolume, entry.volume) {
        mutableFloatStateOf((entry.carVolume ?: entry.volume).coerceIn(0, maxVolume).toFloat())
    }
    val carText = entry.carVolume?.toString() ?: "未設定"
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(entry.packageName)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(entry.label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "スマホ ${entry.volume} / 車 $carText",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onRemove) { Text("削除") }
            }
            Text("スマホ", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = phone,
                onValueChange = { phone = it },
                onValueChangeFinished = { onPhone(phone.roundToInt()) },
                valueRange = 0f..maxVolume.toFloat(),
                steps = (maxVolume - 1).coerceAtLeast(0),
            )
            Text("車（Bluetooth）", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = car,
                onValueChange = { car = it },
                onValueChangeFinished = { onCar(car.roundToInt()) },
                valueRange = 0f..maxVolume.toFloat(),
                steps = (maxVolume - 1).coerceAtLeast(0),
            )
            if (entry.carVolume != null) {
                TextButton(onClick = { onCar(null) }) { Text("車はスマホと同じ") }
            } else {
                Text(
                    "車が未設定のときは、スマホの音量を使います。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
