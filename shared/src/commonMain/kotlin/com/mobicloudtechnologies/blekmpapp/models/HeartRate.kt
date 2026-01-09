package com.mobicloudtechnologies.blekmpapp.models

/**
 * Represents heart rate measurement from a BLE device.
 *
 * Data is read from the standard Heart Rate Service (UUID 0x180D)
 * using Heart Rate Measurement characteristic (UUID 0x2A37).
 *
 * @property beatsPerMinute Heart rate in beats per minute (BPM)
 * @property timestamp Unix timestamp in milliseconds when measurement was taken
 * @property sensorContact Whether sensor has proper skin contact (optional)
 *
 * @since 1.0
 */
data class HeartRate(
    val beatsPerMinute: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val sensorContact: Boolean? = null
)