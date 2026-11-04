package com.nossam.smahobutton.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.viewinterop.AndroidView
import com.nossam.smahobutton.logic.ACTION_CHOICES
import com.nossam.smahobutton.logic.ActionChoice
import com.nossam.smahobutton.logic.ActionSpec
import com.nossam.smahobutton.logic.ButtonSpec
import com.nossam.smahobutton.logic.ICONS
import com.nossam.smahobutton.logic.PALETTE
import com.nossam.smahobutton.logic.SHAPE_CHOICES
import com.nossam.smahobutton.logic.SettingsSpec
import com.nossam.smahobutton.logic.TYPE_OPEN_APP
import com.nossam.smahobutton.logic.actionTitle
import com.nossam.smahobutton.logic.sizeDp
import com.nossam.smahobutton.overlay.RoundButtonView
import com.nossam.smahobutton.ui.theme.argbColor

private data class SheetRequest(val buttonId: String, val slot: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ButtonScreen(
    settings: SettingsSpec,
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onPickApp: () -> Unit,
) {
    var sheet by remember { mutableStateOf<SheetRequest?>(null) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("ボタンの設定") },
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
            itemsIndexed(settings.buttons, key = { _, button -> button.id }) { index, button ->
                ButtonCard(
                    index = index,
                    button = button,
                    canDelete = settings.buttons.size > 1,
                    onChange = { transform -> viewModel.updateButton(button.id, transform) },
                    onDelete = { confirmDelete = button.id },
                    onPick = { slot -> sheet = SheetRequest(button.id, slot) },
                    onRemoveMenu = { menuIndex -> viewModel.removeMenuAction(button.id, menuIndex) },
                )
            }
            item {
                if (settings.buttons.size < 5) {
                    Button(
                        onClick = viewModel::addButton,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp),
                    ) { Text("ボタンを追加") }
                } else {
                    Text(
                        "ボタンは5個までです。",
                        modifier = Modifier.padding(bottom = 28.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    val request = sheet
    if (request != null) {
        ActionSheet(
            includeNone = request.slot != "menu",
            onDismiss = { sheet = null },
            onPick = { choice ->
                sheet = null
                if (choice.type == TYPE_OPEN_APP) {
                    viewModel.pendingAppPick = PendingAppPick(request.buttonId, request.slot)
                    onPickApp()
                } else if (request.slot == "menu") {
                    viewModel.addMenuAction(request.buttonId, ActionSpec(choice.type))
                } else {
                    viewModel.setGesture(request.buttonId, request.slot, ActionSpec(choice.type))
                }
            },
        )
    }
    val deleting = confirmDelete
    if (deleting != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("このボタンを消しますか？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeButton(deleting)
                    confirmDelete = null
                }) { Text("消す") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("やめる") }
            },
        )
    }
}

@Composable
private fun ButtonCard(
    index: Int,
    button: ButtonSpec,
    canDelete: Boolean,
    onChange: ((ButtonSpec) -> ButtonSpec) -> Unit,
    onDelete: () -> Unit,
    onPick: (String) -> Unit,
    onRemoveMenu: (Int) -> Unit,
) {
    var alpha by remember(button.id) { mutableFloatStateOf(button.alpha) }
    var label by remember(button.id) { mutableStateOf(button.label) }
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ボタン ${index + 1}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (canDelete) TextButton(onClick = onDelete) { Text("削除") }
            }
            AndroidView(
                modifier = Modifier.size(112.dp).align(Alignment.CenterHorizontally),
                factory = { RoundButtonView(it) },
                update = {
                    it.spec = button.copy(label = label, alpha = 1f, size = "l")
                    it.alpha = 1f
                    it.isClickable = false
                },
            )
            Text("かたち", style = MaterialTheme.typography.titleSmall)
            SHAPE_CHOICES.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { choice ->
                        val selected = button.shape == choice.key
                        Column(
                            Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier
                                    .size(56.dp)
                                    .border(
                                        width = if (selected) 2.dp else 0.dp,
                                        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp),
                                    )
                                    .clickable { onChange { it.copy(shape = choice.key) } },
                                contentAlignment = Alignment.Center,
                            ) {
                                AndroidView(
                                    modifier = Modifier.size(44.dp),
                                    factory = { RoundButtonView(it) },
                                    update = {
                                        it.spec = button.copy(shape = choice.key, label = "", alpha = 1f)
                                        it.alpha = 1f
                                        it.isClickable = false
                                    },
                                )
                            }
                            Text(choice.name, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            Text("色", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PALETTE.forEach { color ->
                    val selected = button.color == color.key
                    Box(
                        Modifier
                            .size(42.dp)
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                                shape = CircleShape,
                            )
                            .padding(5.dp)
                            .clip(CircleShape)
                            .background(argbColor(color.argb))
                            .clickable { onChange { it.copy(color = color.key) } },
                    )
                }
            }
            Text("大きさ")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("s" to "小", "m" to "中", "l" to "大").forEach { (key, name) ->
                    FilterChip(
                        selected = button.size == key,
                        onClick = { onChange { it.copy(size = key) } },
                        label = { Text(name) },
                    )
                }
            }
            Text("透明度")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("うすい", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = alpha,
                    onValueChange = { alpha = it },
                    onValueChangeFinished = { onChange { it.copy(alpha = alpha) } },
                    valueRange = 0.35f..1f,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Text("濃い", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("マーク")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ICONS.forEach { icon ->
                    FilterChip(
                        selected = button.icon == icon.key && label.isBlank(),
                        onClick = {
                            label = ""
                            onChange { it.copy(icon = icon.key, label = "") }
                        },
                        label = { Text(icon.name) },
                    )
                }
            }
            OutlinedTextField(
                value = label,
                onValueChange = { next ->
                    label = next.take(4)
                    onChange { it.copy(label = next.take(4)) }
                },
                label = { Text("文字（空ならマーク）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (button.useMenu) {
                Text("タップすると、メニューが開きます。")
            } else {
                ActionRow("タップ", actionTitle(button.tap)) { onPick("tap") }
            }
            ActionRow("ダブルタップ", actionTitle(button.doubleTap)) { onPick("double") }
            ActionRow("長押し", actionTitle(button.longPress)) { onPick("long") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("タップでメニュー")
                    Text(
                        "いくつかの動きを、小さなメニューに入れます。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Switch(checked = button.useMenu, onCheckedChange = { on -> onChange { it.copy(useMenu = on) } })
            }
            if (button.useMenu) {
                button.menu.forEachIndexed { menuIndex, action ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(actionTitle(action), modifier = Modifier.weight(1f))
                        TextButton(onClick = { onRemoveMenu(menuIndex) }) { Text("削除") }
                    }
                }
                if (button.menu.size < 6) {
                    TextButton(onClick = { onPick("menu") }) { Text("動きを追加") }
                } else {
                    Text("6個までです。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ActionRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f))
        Text(value, color = MaterialTheme.colorScheme.primary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionSheet(
    includeNone: Boolean,
    onDismiss: () -> Unit,
    onPick: (ActionChoice) -> Unit,
) {
    val choices = if (includeNone) ACTION_CHOICES else ACTION_CHOICES.filter { it.type != "none" }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Text(
                "動きを選ぶ",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            choices.forEach { choice ->
                TextButton(
                    onClick = { onPick(choice) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                ) {
                    Text(choice.title, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}
