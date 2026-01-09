package com.mobicloudtechnologies.blekmpapp.utils

import com.mobicloudtechnologies.blekmpapp.models.BleDevice

object BleUtils {
    /**
     * Filter devices by name pattern
     */
    fun filterDevicesByName(devices: List<BleDevice>, pattern: String): List<BleDevice> {
        if (pattern.isBlank()) return devices
        return devices.filter {
            it.name?.contains(pattern, ignoreCase = true) == true
        }
    }

    /**
     * Filter devices by minimum RSSI (signal strength)
     */
    fun filterDevicesByRssi(devices: List<BleDevice>, minRssi: Int): List<BleDevice> {
        return devices.filter { it.rssi >= minRssi }
    }

    /**
     * Sort devices by RSSI (strongest signal first)
     */
    fun sortDevicesBySignalStrength(devices: List<BleDevice>): List<BleDevice> {
        return devices.sortedByDescending { it.rssi }
    }

    /**
     * Parse battery level from GATT characteristic bytes
     */
    fun parseBatteryLevel(data: ByteArray?): Int? {
        if (data == null || data.isEmpty()) return null
        // Battery level is a single unsigned byte (0-100)
        return data[0].toInt() and 0xFF
    }

    /**
     * Parse heart rate from GATT characteristic bytes
     */
    fun parseHeartRate(data: ByteArray?): Int? {
        if (data == null || data.size < 2) return null
        val flag = data[0].toInt()
        // Check if heart rate is in UINT8 or UINT16 format
        return if ((flag and 0x01) == 0) {
            // UINT8 format
            data[1].toInt() and 0xFF
        } else {
            // UINT16 format
            ((data[2].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
        }
    }
}