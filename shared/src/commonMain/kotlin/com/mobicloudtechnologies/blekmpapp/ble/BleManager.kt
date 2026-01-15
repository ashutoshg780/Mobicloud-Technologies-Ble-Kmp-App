// BleManager.kt (commonMain) - REPLACE THIS FILE
package com.mobicloudtechnologies.blekmpapp.ble

import com.mobicloudtechnologies.blekmpapp.models.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

expect class BleManager {
    val scannedDevices: StateFlow<List<BleDevice>>
    val connectionState: StateFlow<ConnectionState>
    val deviceInfo: StateFlow<DeviceInfo?>
    val bluetoothState: StateFlow<BluetoothState>

    fun startScan()
    fun stopScan()
    suspend fun connect(device: BleDevice)
    fun disconnect()
    fun enableAutoReconnect(enabled: Boolean)
}

sealed class BluetoothState {
    object Unknown : BluetoothState()
    object PoweredOff : BluetoothState()
    object PoweredOn : BluetoothState()
    object Unauthorized : BluetoothState()
}

abstract class BaseBleManager {

    protected val _scannedDevices = MutableStateFlow(emptyList<BleDevice>())
    open val scannedDevices: StateFlow<List<BleDevice>> = _scannedDevices.asStateFlow()

    protected val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected())
    open val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    protected val _deviceInfo = MutableStateFlow<DeviceInfo?>(null)
    open val deviceInfo: StateFlow<DeviceInfo?> = _deviceInfo.asStateFlow()

    protected val _bluetoothState = MutableStateFlow<BluetoothState>(BluetoothState.Unknown)
    open val bluetoothState: StateFlow<BluetoothState> = _bluetoothState.asStateFlow()

    protected val autoReconnectManager = AutoReconnectManager()
    val reconnectState: StateFlow<ReconnectState> = autoReconnectManager.reconnectState

    protected var isAutoReconnectEnabled = true

    open fun enableAutoReconnect(enabled: Boolean) {
        isAutoReconnectEnabled = enabled
        if (!enabled) {
            autoReconnectManager.cancelReconnect()
        }
        println("🔄 Auto-reconnect ${if (enabled) "enabled" else "disabled"}")
    }

    protected fun onConnectionSuccess(device: BleDevice) {
        _connectionState.value = ConnectionState.Connected(device)

        autoReconnectManager.cancelReconnect()

        if (isAutoReconnectEnabled) {
            autoReconnectManager.saveLastConnectedDevice(device)
        }
    }


    protected fun onDisconnected(wasManual: Boolean = false) {
        _connectionState.value = ConnectionState.Disconnected()
        _deviceInfo.value = null

        if (wasManual) {
            autoReconnectManager.clearSavedDevice()
        } else if (isAutoReconnectEnabled && _bluetoothState.value is BluetoothState.PoweredOn) {
            triggerAutoReconnect()
        }
    }

    protected fun onBluetoothStateChanged(newState: BluetoothState) {
        val oldState = _bluetoothState.value
        _bluetoothState.value = newState

        println("📡 Bluetooth state: $oldState → $newState")

        when (newState) {
            is BluetoothState.PoweredOn -> {
                if (isAutoReconnectEnabled && oldState is BluetoothState.PoweredOff) {
                    triggerAutoReconnect()
                }
            }
            is BluetoothState.PoweredOff -> {
                if (_connectionState.value is ConnectionState.Connected) {
                    onDisconnected(wasManual = false)
                }
                autoReconnectManager.cancelReconnect()
            }
            else -> {}
        }
    }

    protected abstract fun triggerAutoReconnect()
}