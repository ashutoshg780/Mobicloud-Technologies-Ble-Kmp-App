package com.mobicloudtechnologies.blekmpapp.models

import kotlin.time.Clock

// Represents heart rate measurement from a BLE device.

data class HeartRate(
    val beatsPerMinute: Int,
    val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    val sensorContact: Boolean? = null
)