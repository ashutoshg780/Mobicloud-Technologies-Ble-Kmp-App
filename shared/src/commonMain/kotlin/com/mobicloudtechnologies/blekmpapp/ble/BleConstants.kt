package com.mobicloudtechnologies.blekmpapp.ble

object BleConstants {
    // Standard GATT Services
    const val BATTERY_SERVICE_UUID = "0000180f-0000-1000-8000-00805f9b34fb"
    const val BATTERY_LEVEL_CHARACTERISTIC_UUID = "00002a19-0000-1000-8000-00805f9b34fb"

    // Heart Rate Service (optional)
    const val HEART_RATE_SERVICE_UUID = "0000180d-0000-1000-8000-00805f9b34fb"
    const val HEART_RATE_MEASUREMENT_UUID = "00002a37-0000-1000-8000-00805f9b34fb"

    // Client Characteristic Configuration Descriptor
    const val CLIENT_CHARACTERISTIC_CONFIG_UUID = "00002902-0000-1000-8000-00805f9b34fb"

    // Scan settings
    const val SCAN_DURATION_MS = 10000L
    const val RECONNECT_DELAY_MS = 3000L
}