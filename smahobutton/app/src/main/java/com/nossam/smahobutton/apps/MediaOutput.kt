package com.nossam.smahobutton.apps

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.nossam.smahobutton.logic.OutputSlot
import com.nossam.smahobutton.logic.PHONE_SLOT
import com.nossam.smahobutton.logic.RawAudioDevice
import com.nossam.smahobutton.logic.isBluetoothMedia
import com.nossam.smahobutton.logic.resolveOutput

object MediaOutput {
    /**
     * Media route used for the saved Bluetooth slot.
     * [knownKeys] and [knownNames] pick a saved EarFun/car when several outputs are connected.
     * If the media-route API returns nothing yet (common just before playback), fall back to
     * the outputs that are actually connected.
     */
    fun current(
        context: Context,
        knownKeys: Set<String> = emptySet(),
        knownNames: Set<String> = emptySet(),
    ): OutputSlot {
        val audio = context.getSystemService(AudioManager::class.java) ?: return PHONE_SLOT
        val routed = if (Build.VERSION.SDK_INT >= 33) {
            runCatching {
                val media = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()
                audio.getAudioDevicesForAttributes(media).map { it.toRaw() }
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        val connected = runCatching {
            audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.toRaw() }
        }.getOrDefault(emptyList())
        val source: String
        val chosen: List<RawAudioDevice>
        when {
            routed.any { isBluetoothMedia(it.type) } -> {
                source = "routed"
                chosen = routed
            }
            routed.isEmpty() -> {
                source = "connected"
                chosen = connected
            }
            else -> {
                source = "routed-speaker"
                chosen = routed
            }
        }
        val slot = resolveOutput(chosen, knownKeys, knownNames)
        Log.i(
            TAG,
            "output key=${slot.key} name=${slot.name} bt=${slot.bluetooth} source=$source " +
                "routed=${summarize(routed)} connected=${summarize(connected)}",
        )
        return slot
    }

    private fun AudioDeviceInfo.toRaw(): RawAudioDevice {
        val address = runCatching { address }.getOrNull()
        val product = if (Build.VERSION.SDK_INT >= 31) {
            runCatching { productName?.toString() }.getOrNull()
        } else {
            null
        }
        return RawAudioDevice(type = type, address = address, productName = product)
    }

    private fun summarize(devices: List<RawAudioDevice>): String {
        if (devices.isEmpty()) return "-"
        return devices.joinToString(",") { device ->
            val named = device.productName?.trim().orEmpty().ifEmpty { "?" }
            val hasMac = !device.address.isNullOrBlank()
            "${device.type}:$named:mac=$hasMac"
        }
    }

    private const val TAG = "SmahoButton"
}
