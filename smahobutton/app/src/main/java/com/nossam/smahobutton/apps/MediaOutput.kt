package com.nossam.smahobutton.apps

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import com.nossam.smahobutton.logic.OutputSlot
import com.nossam.smahobutton.logic.PHONE_SLOT
import com.nossam.smahobutton.logic.RawAudioDevice
import com.nossam.smahobutton.logic.resolveOutput

object MediaOutput {
    fun current(context: Context): OutputSlot {
        val audio = context.getSystemService(AudioManager::class.java) ?: return PHONE_SLOT
        val infos = if (Build.VERSION.SDK_INT >= 33) {
            val media = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()
            audio.getAudioDevicesForAttributes(media)
        } else {
            audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        }
        val raw = infos.map { info ->
            val address = runCatching { info.address }.getOrNull()
            val product = if (Build.VERSION.SDK_INT >= 31) {
                runCatching { info.productName?.toString() }.getOrNull()
            } else {
                null
            }
            RawAudioDevice(type = info.type, address = address, productName = product)
        }
        return resolveOutput(raw)
    }
}
