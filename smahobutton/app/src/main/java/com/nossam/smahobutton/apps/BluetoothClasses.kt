package com.nossam.smahobutton.apps

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object BluetoothClasses {
    /** Device class for a saved `mac:` key, or null when the radio or permission is unavailable. */
    fun deviceClass(context: Context, mac: String?): Int? {
        val address = mac?.trim()?.uppercase().orEmpty()
        if (address.isEmpty()) return null
        if (
            Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        return runCatching {
            val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return null
            adapter.getRemoteDevice(address).bluetoothClass?.deviceClass
        }.getOrNull()
    }

    fun macOf(key: String): String? =
        key.removePrefix("mac:").takeIf { key.startsWith("mac:") && it.isNotBlank() }
}
