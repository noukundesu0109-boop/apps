package com.nossam.smahobutton.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nossam.smahobutton.SmahoApp
import com.nossam.smahobutton.data.SettingsRepository
import com.nossam.smahobutton.logic.ActionSpec
import com.nossam.smahobutton.logic.AppVolumeSpec
import com.nossam.smahobutton.logic.ButtonSpec
import com.nossam.smahobutton.logic.TYPE_OPEN_APP
import com.nossam.smahobutton.logic.defaultButton
import com.nossam.smahobutton.runtime.SmahoRuntime
import kotlinx.coroutines.launch

data class PendingAppPick(val buttonId: String, val slot: String)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo: SettingsRepository = (app as SmahoApp).repository
    val settings = repo.state
    var pendingAppPick: PendingAppPick? = null

    fun setEnabled(on: Boolean) = edit { it.copy(enabled = on) }

    fun setShutterOnOpen(on: Boolean) = edit { it.copy(shutterOnOpen = on) }

    fun replaceAll(spec: com.nossam.smahobutton.logic.SettingsSpec) {
        viewModelScope.launch {
            repo.replace(spec)
            repo.state.value?.let { SmahoRuntime.refreshButtons(it) }
        }
    }

    fun addButton() = edit { settings ->
        if (settings.buttons.size >= 5) settings
        else settings.copy(buttons = settings.buttons + defaultButton(settings.buttons.size))
    }

    fun removeButton(id: String) = edit { settings ->
        if (settings.buttons.size <= 1) settings
        else settings.copy(buttons = settings.buttons.filter { it.id != id })
    }

    fun updateButton(id: String, transform: (ButtonSpec) -> ButtonSpec) = edit { settings ->
        settings.copy(buttons = settings.buttons.map { if (it.id == id) transform(it) else it })
    }

    fun setGesture(id: String, slot: String, action: ActionSpec) = updateButton(id) { button ->
        when (slot) {
            "double" -> button.copy(doubleTap = action)
            "long" -> button.copy(longPress = action)
            else -> button.copy(tap = action)
        }
    }

    fun addMenuAction(id: String, action: ActionSpec) {
        if (action.type == "none") return
        updateButton(id) { button ->
            if (button.menu.size >= 6) button else button.copy(menu = button.menu + action)
        }
    }

    fun removeMenuAction(id: String, index: Int) = updateButton(id) { button ->
        button.copy(menu = button.menu.filterIndexed { item, _ -> item != index })
    }

    fun setRestore(on: Boolean) = edit { it.copy(restoreVolumeOnLeave = on) }

    fun addAppVolume(packageName: String, label: String, volume: Int) = edit { settings ->
        val kept = settings.appVolumes.filter { it.packageName != packageName }
        settings.copy(appVolumes = kept + AppVolumeSpec(packageName, label, volume))
    }

    fun setAppVolume(packageName: String, volume: Int) = edit { settings ->
        settings.copy(
            appVolumes = settings.appVolumes.map {
                if (it.packageName == packageName) it.copy(volume = volume) else it
            },
        )
    }

    fun setDeviceVolume(packageName: String, key: String, volume: Int) = edit { settings ->
        settings.copy(
            appVolumes = settings.appVolumes.map { entry ->
                if (entry.packageName != packageName) entry
                else entry.copy(devices = entry.devices.map { if (it.key == key) it.copy(volume = volume) else it })
            },
        )
    }

    fun removeDeviceVolume(packageName: String, key: String) = edit { settings ->
        settings.copy(
            appVolumes = settings.appVolumes.map { entry ->
                if (entry.packageName != packageName) entry
                else entry.copy(devices = entry.devices.filter { it.key != key })
            },
        )
    }

    fun removeAppVolume(packageName: String) = edit { settings ->
        settings.copy(appVolumes = settings.appVolumes.filter { it.packageName != packageName })
    }

    fun onAppPicked(mode: String, packageName: String, label: String, volume: Int) {
        if (mode == "volume") {
            addAppVolume(packageName, label, volume)
            return
        }
        val pending = pendingAppPick ?: return
        pendingAppPick = null
        val action = ActionSpec(TYPE_OPEN_APP, packageName, label)
        if (pending.slot == "menu") addMenuAction(pending.buttonId, action)
        else setGesture(pending.buttonId, pending.slot, action)
    }

    private fun edit(transform: (com.nossam.smahobutton.logic.SettingsSpec) -> com.nossam.smahobutton.logic.SettingsSpec) {
        viewModelScope.launch { repo.update(transform) }
    }
}
