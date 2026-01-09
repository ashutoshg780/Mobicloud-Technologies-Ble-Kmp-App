package com.mobicloudtechnologies.blekmpapp

import android.content.Context
import com.mobicloudtechnologies.blekmpapp.ble.BleManager
import com.mobicloudtechnologies.blekmpapp.ble.AndroidBleManager

class AndroidPlatform : Platform {
    override val name: String = "Android ${android.os.Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform = AndroidPlatform()

private var bleManagerInstance: AndroidBleManager? = null

fun initializeBleManager(context: Context) {
    bleManagerInstance = AndroidBleManager(context)
}

actual fun createBleManager(): BleManager {
    return bleManagerInstance ?: throw IllegalStateException("BleManager not initialized")
}