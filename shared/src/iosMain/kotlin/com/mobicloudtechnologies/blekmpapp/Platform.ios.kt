package com.mobicloudtechnologies.blekmpapp

import com.mobicloudtechnologies.blekmpapp.ble.BleManager
import platform.UIKit.UIDevice

class IOSPlatform: Platform {
    override val name: String =
        UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
}

actual fun getPlatform(): Platform = IOSPlatform()

actual fun createBleManager(): BleManager = BleManager()
