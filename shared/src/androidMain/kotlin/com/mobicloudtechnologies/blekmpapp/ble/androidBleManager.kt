// androidBleManager.kt - FINAL FIX
package com.mobicloudtechnologies.blekmpapp.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.mobicloudtechnologies.blekmpapp.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import java.util.*

@SuppressLint("MissingPermission")
actual class BleManager(private val context: Context) : BaseBleManager() {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter

    private var bluetoothGatt: BluetoothGatt? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var isManualDisconnect = false

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(
                        BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR
                    )

                    val newState = when (state) {
                        BluetoothAdapter.STATE_ON -> BluetoothState.PoweredOn
                        BluetoothAdapter.STATE_OFF -> BluetoothState.PoweredOff
                        else -> BluetoothState.Unknown
                    }

                    onBluetoothStateChanged(newState)
                }
            }
        }
    }

    init {
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        context.registerReceiver(bluetoothStateReceiver, filter)

        _bluetoothState.value = if (bluetoothAdapter?.isEnabled == true) {
            BluetoothState.PoweredOn
        } else {
            BluetoothState.PoweredOff
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { device ->
                val bleDevice = BleDevice(
                    id = device.address,
                    name = device.name,
                    rssi = result.rssi
                )

                val currentList = _scannedDevices.value.toMutableList()
                val existingIndex = currentList.indexOfFirst { it.id == bleDevice.id }

                if (existingIndex >= 0) {
                    currentList[existingIndex] = bleDevice
                } else {
                    currentList.add(bleDevice)
                }

                _scannedDevices.value = currentList
            }
        }

        override fun onScanFailed(errorCode: Int) {
            println("Scan failed: $errorCode")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    println("✅ Connected to GATT server")
                    gatt?.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    println("❌ Disconnected from GATT server")
                    scope.launch {
                        onDisconnected(isManualDisconnect)
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val device = gatt?.device?.let {
                    BleDevice(
                        id = it.address,
                        name = it.name,
                        rssi = 0
                    )
                } ?: return

                onConnectionSuccess(device)
                readDeviceInfo(gatt)
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic != null) {
                handleCharacteristicData(characteristic)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            characteristic?.let { handleCharacteristicData(it) }
        }
    }

    actual override val scannedDevices: StateFlow<List<BleDevice>> = _scannedDevices
    actual override val connectionState: StateFlow<ConnectionState> = _connectionState
    actual override val deviceInfo: StateFlow<DeviceInfo?> = _deviceInfo
    actual override val bluetoothState: StateFlow<BluetoothState> = _bluetoothState

    actual fun startScan() {
        _scannedDevices.value = emptyList()
        bluetoothAdapter?.bluetoothLeScanner?.startScan(scanCallback)
    }

    actual fun stopScan() {
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
    }

    actual suspend fun connect(device: BleDevice) {
        if (_connectionState.value is ConnectionState.Connecting ||
            _connectionState.value is ConnectionState.Connected) {
            println("⚠️ Already connecting/connected, ignoring")
            return
        }

        withContext(Dispatchers.Main) {
            isManualDisconnect = false
            _connectionState.value = ConnectionState.Connecting(device)

            val bluetoothDevice = bluetoothAdapter?.getRemoteDevice(device.id)
            bluetoothGatt?.close()
            bluetoothGatt = bluetoothDevice?.connectGatt(
                context,
                false,
                gattCallback,
                BluetoothDevice.TRANSPORT_LE
            )
        }
    }

    actual fun disconnect() {
        isManualDisconnect = true
        _connectionState.value = ConnectionState.Disconnected()
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        _deviceInfo.value = null
        autoReconnectManager.clearSavedDevice()
    }

    actual override fun enableAutoReconnect(enabled: Boolean) {
        super.enableAutoReconnect(enabled)
    }

//    override fun triggerAutoReconnect() {
//        scope.launch {
//            autoReconnectManager.startAutoReconnect { device ->
//                try {
//                    connect(device)
//                    delay(5000)
//                    _connectionState.value is ConnectionState.Connected
//                } catch (e: Exception) {
//                    println("❌ Reconnect failed: ${e.message}")
//                    false
//                }
//            }
//        }
//    }

    override fun triggerAutoReconnect() {
        if (_connectionState.value is ConnectionState.Connecting) return

        scope.launch {
            autoReconnectManager.startAutoReconnect { device ->
                connect(device)
                repeat(10) {
                    delay(500)
                    if (_connectionState.value is ConnectionState.Connected) return@startAutoReconnect true
                }
                false
            }
        }
    }


    private fun readDeviceInfo(gatt: BluetoothGatt?) {
        gatt?.services?.forEach { service ->
            when (service.uuid.toString().lowercase()) {
                BATTERY_SERVICE_UUID -> {
                    service.getCharacteristic(UUID.fromString(BATTERY_LEVEL_CHARACTERISTIC_UUID))?.let {
                        gatt.readCharacteristic(it)
                    }
                }
                HEART_RATE_SERVICE_UUID -> {
                    service.getCharacteristic(UUID.fromString(HEART_RATE_MEASUREMENT_UUID))?.let {
                        gatt.setCharacteristicNotification(it, true)
                        it.descriptors.firstOrNull()?.let { descriptor ->
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(descriptor)
                        }
                    }
                }
            }
        }
    }

    private fun handleCharacteristicData(characteristic: BluetoothGattCharacteristic) {
        when (characteristic.uuid.toString().lowercase()) {
            BATTERY_LEVEL_CHARACTERISTIC_UUID -> {
                val batteryLevel = characteristic.getIntValue(
                    BluetoothGattCharacteristic.FORMAT_UINT8, 0
                ) ?: 0
                updateDeviceInfo(batteryLevel = BatteryLevel(batteryLevel))
            }
            HEART_RATE_MEASUREMENT_UUID -> {
                val flag = characteristic.properties
                val format = if (flag and 0x01 != 0) {
                    BluetoothGattCharacteristic.FORMAT_UINT16
                } else {
                    BluetoothGattCharacteristic.FORMAT_UINT8
                }
                val heartRate = characteristic.getIntValue(format, 1) ?: 0
                val sensorContact = flag and 0x06 != 0
                updateDeviceInfo(
                    heartRate = HeartRate(
                        beatsPerMinute = heartRate,
                        sensorContact = sensorContact
                    )
                )
            }
        }
    }

    private fun updateDeviceInfo(
        batteryLevel: BatteryLevel? = null,
        heartRate: HeartRate? = null
    ) {
        val currentDevice = (_connectionState.value as? ConnectionState.Connected)?.device ?: return
        val current = _deviceInfo.value

        _deviceInfo.value = DeviceInfo(
            device = currentDevice,
            batteryLevel = batteryLevel ?: current?.batteryLevel,
            heartRate = heartRate ?: current?.heartRate
        )
    }

    fun cleanup() {
        context.unregisterReceiver(bluetoothStateReceiver)
        disconnect()
        scope.cancel()
    }

    companion object {
        private const val BATTERY_SERVICE_UUID = "0000180f-0000-1000-8000-00805f9b34fb"
        private const val BATTERY_LEVEL_CHARACTERISTIC_UUID = "00002a19-0000-1000-8000-00805f9b34fb"
        private const val HEART_RATE_SERVICE_UUID = "0000180d-0000-1000-8000-00805f9b34fb"
        private const val HEART_RATE_MEASUREMENT_UUID = "00002a37-0000-1000-8000-00805f9b34fb"
    }
}