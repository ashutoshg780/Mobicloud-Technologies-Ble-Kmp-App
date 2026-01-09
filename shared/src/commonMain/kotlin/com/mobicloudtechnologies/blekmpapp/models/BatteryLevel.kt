package com.mobicloudtechnologies.blekmpapp.models

/**
 * Represents battery level information from a BLE device.
 *
 * Data is read from the standard Battery Service (UUID 0x180F)
 * using Battery Level characteristic (UUID 0x2A19).
 *
 * @property percentage Battery level as percentage (0-100)
 * @property timestamp Unix timestamp in milliseconds when reading was taken
 *
 * @since 1.0
 */
data class BatteryLevel(
    val percentage: Int,
    val timestamp: Long = System.currentTimeMillis()
)