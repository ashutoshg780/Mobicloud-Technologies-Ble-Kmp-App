package com.mobicloudtechnologies.blekmpapp.models

import kotlin.time.Clock

//Represents battery level information from a BLE device.

data class BatteryLevel(
    val percentage: Int,
    val timestamp: Long = Clock.System.now().toEpochMilliseconds()
)