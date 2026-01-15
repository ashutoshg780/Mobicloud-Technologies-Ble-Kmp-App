package com.mobicloudtechnologies.blekmpapp

import android.content.Context
import com.mobicloudtechnologies.blekmpapp.ble.BleManager

class AndroidPlatform : Platform {
    override val name: String = "Android ${android.os.Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform = AndroidPlatform()

private var bleManagerInstance: BleManager? = null

fun initializeBleManager(context: Context) {
    bleManagerInstance = BleManager(context)
}

actual fun createBleManager(): BleManager {
    return bleManagerInstance ?: throw IllegalStateException("BleManager not initialized. Call initializeBleManager(context) first")
}
