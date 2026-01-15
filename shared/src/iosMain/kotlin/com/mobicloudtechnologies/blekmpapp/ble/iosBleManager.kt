// iosBleManager.kt - FINAL FIX
package com.mobicloudtechnologies.blekmpapp.ble

import com.mobicloudtechnologies.blekmpapp.models.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import platform.CoreBluetooth.*
import platform.Foundation.*
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
actual class BleManager : BaseBleManager() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var centralManager: CBCentralManager? = null
    private var peripheral: CBPeripheral? = null
    private var isManualDisconnect = false

    private val centralDelegate = object : NSObject(), CBCentralManagerDelegateProtocol {

        override fun centralManagerDidUpdateState(central: CBCentralManager) {
            val newState = when (central.state) {
                CBManagerStatePoweredOn -> BluetoothState.PoweredOn
                CBManagerStatePoweredOff -> BluetoothState.PoweredOff
                CBManagerStateUnauthorized -> BluetoothState.Unauthorized
                else -> BluetoothState.Unknown
            }

            onBluetoothStateChanged(newState)
        }

        override fun centralManager(
            central: CBCentralManager,
            didDiscoverPeripheral: CBPeripheral,
            advertisementData: Map<Any?, *>,
            RSSI: NSNumber
        ) {
            val device = BleDevice(
                id = didDiscoverPeripheral.identifier.UUIDString,
                name = didDiscoverPeripheral.name,
                rssi = RSSI.intValue
            )

            val currentList = _scannedDevices.value.toMutableList()
            val existingIndex = currentList.indexOfFirst { it.id == device.id }

            if (existingIndex >= 0) {
                currentList[existingIndex] = device
            } else {
                currentList.add(device)
            }

            _scannedDevices.value = currentList
        }

        override fun centralManager(
            central: CBCentralManager,
            didConnectPeripheral: CBPeripheral
        ) {
            println("✅ iOS: Connected to peripheral")

            val device = BleDevice(
                id = didConnectPeripheral.identifier.UUIDString,
                name = didConnectPeripheral.name,
                rssi = 0
            )

            onConnectionSuccess(device)

            peripheral = didConnectPeripheral
            didConnectPeripheral.delegate = peripheralDelegate
            didConnectPeripheral.discoverServices(null)
        }

        @ObjCSignatureOverride
        override fun centralManager(
            central: CBCentralManager,
            didDisconnectPeripheral: CBPeripheral,
            error: NSError?
        ) {
            println("❌ iOS: Disconnected from peripheral")

            scope.launch {
                onDisconnected(isManualDisconnect)
            }
        }

        @ObjCSignatureOverride
        override fun centralManager(
            central: CBCentralManager,
            didFailToConnectPeripheral: CBPeripheral,
            error: NSError?
        ) {
            println("❌ iOS: Failed to connect - ${error?.localizedDescription}")
            _connectionState.value = ConnectionState.Error(
                error?.localizedDescription ?: "Connection failed"
            )
        }
    }

    private val peripheralDelegate = object : NSObject(), CBPeripheralDelegateProtocol {

        override fun peripheral(
            peripheral: CBPeripheral,
            didDiscoverServices: NSError?
        ) {
            peripheral.services?.forEach { service ->
                val serviceUUID = (service as CBService).UUID.UUIDString.lowercase()

                when {
                    serviceUUID.contains("180f") -> {
                        peripheral.discoverCharacteristics(null, service)
                    }
                    serviceUUID.contains("180d") -> {
                        peripheral.discoverCharacteristics(null, service)
                    }
                }
            }
        }

        override fun peripheral(
            peripheral: CBPeripheral,
            didDiscoverCharacteristicsForService: CBService,
            error: NSError?
        ) {
            didDiscoverCharacteristicsForService.characteristics?.forEach { char ->
                val characteristic = char as CBCharacteristic
                val uuid = characteristic.UUID.UUIDString.lowercase()

                when {
                    uuid.contains("2a19") -> {
                        peripheral.readValueForCharacteristic(characteristic)
                    }
                    uuid.contains("2a37") -> {
                        peripheral.setNotifyValue(true, characteristic)
                    }
                }
            }
        }

        override fun peripheral(
            peripheral: CBPeripheral,
            didUpdateValueForCharacteristic: CBCharacteristic,
            error: NSError?
        ) {
            val uuid = didUpdateValueForCharacteristic.UUID.UUIDString.lowercase()
            val value = didUpdateValueForCharacteristic.value

            when {
                uuid.contains("2a19") -> {
                    value?.let {
                        val bytes = it.bytes?.reinterpret<ByteVar>()
                        val batteryLevel = bytes?.get(0)?.toInt() ?: 0
                        updateDeviceInfo(batteryLevel = BatteryLevel(batteryLevel))
                    }
                }
                uuid.contains("2a37") -> {
                    value?.let {
                        val bytes = it.bytes?.reinterpret<ByteVar>()
                        val flags = bytes?.get(0)?.toInt() ?: 0
                        val heartRate = if (flags and 0x01 != 0) {
                            bytes?.get(2)?.toInt() ?: 0
                        } else {
                            bytes?.get(1)?.toInt() ?: 0
                        }
                        val sensorContact = flags and 0x06 != 0

                        updateDeviceInfo(
                            heartRate = HeartRate(
                                beatsPerMinute = heartRate,
                                sensorContact = sensorContact
                            )
                        )
                    }
                }
            }
        }
    }

    init {
        centralManager = CBCentralManager(centralDelegate, null)
    }

    actual override val scannedDevices: StateFlow<List<BleDevice>> = _scannedDevices
    actual override val connectionState: StateFlow<ConnectionState> = _connectionState
    actual override val deviceInfo: StateFlow<DeviceInfo?> = _deviceInfo
    actual override val bluetoothState: StateFlow<BluetoothState> = _bluetoothState

    actual fun startScan() {
        _scannedDevices.value = emptyList()
        centralManager?.scanForPeripheralsWithServices(null, null)
    }

    actual fun stopScan() {
        centralManager?.stopScan()
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

            val peripherals = centralManager?.retrievePeripheralsWithIdentifiers(
                listOf(NSUUID(device.id))
            )

            val targetPeripheral = peripherals?.firstOrNull() as? CBPeripheral

            if (targetPeripheral != null) {
                centralManager?.connectPeripheral(targetPeripheral, null)
            } else {
                _connectionState.value = ConnectionState.Error("Device not found")
            }
        }
    }

    actual fun disconnect() {
        isManualDisconnect = true
        _connectionState.value = ConnectionState.Disconnected()

        peripheral?.let {
            centralManager?.cancelPeripheralConnection(it)
        }
        peripheral = null
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
//                    println("❌ iOS Reconnect failed: ${e.message}")
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
}