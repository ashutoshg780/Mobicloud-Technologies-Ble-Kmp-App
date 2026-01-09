package com.mobicloudtechnologies.blekmpapp.models

/**
 * Represents a Bluetooth Low Energy device discovered during scanning.
 *
 * This data class encapsulates basic information about a BLE device that can be
 * used across both Android and iOS platforms.
 *
 * @property id Unique identifier for the device:
 *              - Android: MAC address (e.g., "00:11:22:33:44:55")
 *              - iOS: UUID string (e.g., "12345678-1234-1234-1234-123456789ABC")
 * @property name Human-readable device name from advertising data, null if not available
 * @property rssi Received Signal Strength Indicator in dBm (typically -100 to 0)
 *               Higher values indicate stronger signal
 *
 * @since 1.0
 */
data class BleDevice(
    val id: String,
    val name: String?,
    val rssi: Int = 0
)