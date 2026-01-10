package com.mobicloudtechnologies.blekmpapp

import com.mobicloudtechnologies.blekmpapp.ble.BleManager
import com.mobicloudtechnologies.blekmpapp.ble.IosBleManager
import com.mobicloudtechnologies.blekmpapp.models.BleDevice
import com.mobicloudtechnologies.blekmpapp.models.ConnectionState
import com.mobicloudtechnologies.blekmpapp.models.DeviceInfo
import kotlinx.coroutines.flow.StateFlow
import platform.UIKit.UIDevice

class IOSPlatform: Platform {
    override val name: String =
        UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
}

actual fun getPlatform(): Platform = IOSPlatform()

// Wrapper class to adapt IosBleManager to BleManager interface
private class IosBleManagerWrapper(private val iosManager: IosBleManager) : BleManager {
    override val scannedDevices: StateFlow<List<BleDevice>>
        get() = iosManager.scannedDevices

    override val connectionState: StateFlow<ConnectionState>
        get() = iosManager.connectionState

    override val deviceInfo: StateFlow<DeviceInfo?>
        get() = iosManager.deviceInfo

    override fun startScan() = iosManager.startScan()

    override fun stopScan() = iosManager.stopScan()

    override suspend fun connect(device: BleDevice) = iosManager.connect(device)

    override fun disconnect() = iosManager.disconnect()

    override fun isBluetoothEnabled(): Boolean = iosManager.isBluetoothEnabled()

    override fun requestEnableBluetooth() = iosManager.requestEnableBluetooth()
}

actual fun createBleManager(): BleManager = IosBleManagerWrapper(IosBleManager())