package com.nossam.smahobutton.logic

const val DEVICE_BLUETOOTH_A2DP = 8
const val DEVICE_BLE_HEADSET = 26
const val DEVICE_BLE_SPEAKER = 27
const val DEVICE_BLE_BROADCAST = 30

const val LEGACY_BLUETOOTH_KEY = "legacy-bluetooth"
const val LEGACY_BLUETOOTH_NAME = "Bluetooth（前の設定）"
const val GENERIC_BLUETOOTH_KEY = "bluetooth"
const val GENERIC_BLUETOOTH_NAME = "Bluetooth"

data class OutputSlot(
    val key: String,
    val name: String,
    val bluetooth: Boolean,
)

val PHONE_SLOT = OutputSlot(key = "", name = "スマホ", bluetooth = false)

data class RawAudioDevice(
    val type: Int,
    val address: String? = null,
    val productName: String? = null,
)

fun isBluetoothMedia(type: Int): Boolean =
    type == DEVICE_BLUETOOTH_A2DP ||
        type == DEVICE_BLE_HEADSET ||
        type == DEVICE_BLE_SPEAKER ||
        type == DEVICE_BLE_BROADCAST

/** Phone speaker and wired outputs stay on the スマホ slot. Bluetooth media gets its own slot. */
fun resolveOutput(devices: List<RawAudioDevice>): OutputSlot {
    val bluetooth = devices.firstOrNull { isBluetoothMedia(it.type) } ?: return PHONE_SLOT
    val mac = usableMac(bluetooth.address)
    val product = bluetooth.productName?.trim()?.takeIf { it.isNotEmpty() && !it.equals(GENERIC_BLUETOOTH_NAME, true) }
    val key = when {
        mac != null -> "mac:$mac"
        product != null -> "name:$product"
        else -> GENERIC_BLUETOOTH_KEY
    }
    val name = product ?: GENERIC_BLUETOOTH_NAME
    return OutputSlot(key = key, name = name, bluetooth = true)
}

private fun usableMac(address: String?): String? {
    val mac = address?.trim()?.uppercase().orEmpty()
    if (mac.isEmpty()) return null
    if (mac == "02:00:00:00:00:00" || mac == "00:00:00:00:00:00") return null
    return mac
}
