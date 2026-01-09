package com.mobicloudtechnologies.blekmpapp.repository

import com.mobicloudtechnologies.blekmpapp.ble.BleManager
import com.mobicloudtechnologies.blekmpapp.models.*
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository layer for BLE operations
 * Provides a clean API for the UI layer
 */
class BleRepository(private val bleManager: BleManager) {

    val scannedDevices: StateFlow<List<BleDevice>> = bleManager.scannedDevices
    val connectionState: StateFlow<ConnectionState> = bleManager.connectionState
    val deviceInfo: StateFlow<DeviceInfo?> = bleManager.deviceInfo

    fun startScanning() {
        bleManager.startScan()
    }

    fun stopScanning() {
        bleManager.stopScan()
    }

    suspend fun connectToDevice(device: BleDevice) {
        bleManager.connect(device)
    }

    fun disconnectDevice() {
        bleManager.disconnect()
    }

    fun isBluetoothEnabled(): Boolean {
        return bleManager.isBluetoothEnabled()
    }

    fun enableBluetooth() {
        bleManager.requestEnableBluetooth()
    }
}