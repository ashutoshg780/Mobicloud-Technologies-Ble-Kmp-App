package com.mobicloudtechnologies.blekmpapp

import com.mobicloudtechnologies.blekmpapp.ble.BleManager

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
expect fun createBleManager(): BleManager
