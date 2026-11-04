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

fun sameDeviceName(a: String, b: String): Boolean {
    val left = a.trim().lowercase().replace(" ", "")
    val right = b.trim().lowercase().replace(" ", "")
    return left.isNotEmpty() && left == right
}

/**
 * Phone speaker and wired outputs stay on the スマホ slot. Bluetooth media gets its own slot.
 * When several Bluetooth outputs are connected, a key or name the user already saved wins.
 * A2DP is preferred over LE Audio when nothing saved matches.
 */
fun resolveOutput(
    devices: List<RawAudioDevice>,
    knownKeys: Set<String> = emptySet(),
    knownNames: Set<String> = emptySet(),
): OutputSlot {
    val bluetooth = devices.filter { isBluetoothMedia(it.type) }.sortedBy { bluetoothRank(it.type) }
    if (bluetooth.isEmpty()) return PHONE_SLOT
    val slots = bluetooth.map { slotFor(it) }
    slots.firstOrNull { it.key in knownKeys }?.let { return it }
    slots.firstOrNull { slot -> knownNames.any { sameDeviceName(it, slot.name) } }?.let { return it }
    return slots.first()
}

private fun bluetoothRank(type: Int): Int = when (type) {
    DEVICE_BLUETOOTH_A2DP -> 0
    DEVICE_BLE_HEADSET -> 1
    DEVICE_BLE_SPEAKER -> 2
    DEVICE_BLE_BROADCAST -> 3
    else -> 9
}

private fun slotFor(device: RawAudioDevice): OutputSlot {
    val mac = usableMac(device.address)
    val product = device.productName?.trim()?.takeIf { it.isNotEmpty() && !it.equals(GENERIC_BLUETOOTH_NAME, true) }
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
