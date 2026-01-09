package com.mobicloudtechnologies.blekmpapp.models

/**
 * Comprehensive device information including all GATT characteristics.
 *
 * This data class aggregates all information retrieved from a connected BLE device:
 * - Basic device info (name, ID, RSSI)
 * - Battery level from Battery Service
 * - Heart rate from Heart Rate Service
 * - Custom GATT service data
 *
 * @property device Basic BLE device information
 * @property batteryLevel Current battery level, null if not available or not supported
 * @property heartRate Current heart rate, null if not available or not supported
 * @property customData Additional custom GATT service data as key-value pairs
 *                      Key: Service UUID or custom identifier
 *                      Value: Parsed data as string
 *
 * @since 1.0
 */
data class DeviceInfo(
    val device: BleDevice,
    val batteryLevel: BatteryLevel? = null,
    val heartRate: HeartRate? = null,
    val customData: Map<String, String> = emptyMap()
)