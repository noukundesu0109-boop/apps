package com.nossam.smahobutton.logic

const val DEVICE_BLUETOOTH_A2DP = 8
const val DEVICE_BLE_HEADSET = 26
const val DEVICE_BLE_SPEAKER = 27

fun isCarDeviceType(types: Collection<Int>): Boolean =
    types.any { it == DEVICE_BLUETOOTH_A2DP || it == DEVICE_BLE_HEADSET || it == DEVICE_BLE_SPEAKER }
