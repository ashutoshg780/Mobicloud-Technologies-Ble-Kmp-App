package com.mobicloudtechnologies.blekmpapp.ble

import com.mobicloudtechnologies.blekmpapp.models.*
import com.mobicloudtechnologies.blekmpapp.utils.BleUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.CoreBluetooth.*
import platform.Foundation.*
import platform.darwin.NSObject
import kotlinx.cinterop.*

@OptIn(ExperimentalForeignApi::class)
class IosBleManager : NSObject(), CBCentralManagerDelegateProtocol, CBPeripheralDelegateProtocol {

    private var centralManager: CBCentralManager? = null
    private var connectedPeripheral: CBPeripheral? = null

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _scannedDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    val scannedDevices: StateFlow<List<BleDevice>> = _scannedDevices.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _deviceInfo = MutableStateFlow<DeviceInfo?>(null)
    val deviceInfo: StateFlow<DeviceInfo?> = _deviceInfo.asStateFlow()

    private var currentDevice: BleDevice? = null
    private var isScanning = false

    init {
        centralManager = CBCentralManager(this, null)
    }

    // MARK: - BleManager Implementation

    fun startScan() {
        if (centralManager?.state != CBManagerStatePoweredOn) {
            _connectionState.value = ConnectionState.Error("Bluetooth is not powered on")
            return
        }

        if (isScanning) return

        _scannedDevices.value = emptyList()

        centralManager?.scanForPeripheralsWithServices(
            serviceUUIDs = null,
            options = null
        )

        isScanning = true

        // Auto-stop scan after duration
        scope.launch {
            delay(BleConstants.SCAN_DURATION_MS)
            stopScan()
        }
    }

    fun stopScan() {
        if (!isScanning) return
        centralManager?.stopScan()
        isScanning = false
    }

    suspend fun connect(device: BleDevice) {
        disconnect()

        currentDevice = device
        _connectionState.value = ConnectionState.Connecting

        // Find peripheral by identifier
        val peripherals = centralManager?.retrievePeripheralsWithIdentifiers(
            listOf(NSUUID(device.id))
        ) as? List<*>

        val peripheral = peripherals?.firstOrNull() as? CBPeripheral

        if (peripheral != null) {
            connectedPeripheral = peripheral
            peripheral.delegate = this
            centralManager?.connectPeripheral(peripheral, null)
        } else {
            _connectionState.value = ConnectionState.Error("Peripheral not found")
        }
    }

    fun disconnect() {
        connectedPeripheral?.let {
            centralManager?.cancelPeripheralConnection(it)
        }
        connectedPeripheral = null
        currentDevice = null
        _connectionState.value = ConnectionState.Disconnected
        _deviceInfo.value = null
    }

    fun isBluetoothEnabled(): Boolean {
        return centralManager?.state == CBManagerStatePoweredOn
    }

    fun requestEnableBluetooth() {
        // iOS handles this automatically with system prompts
    }

    // MARK: - CBCentralManagerDelegate

    override fun centralManagerDidUpdateState(central: CBCentralManager) {
        when (central.state) {
            CBManagerStatePoweredOn -> {
                // Bluetooth is ready
            }
            CBManagerStatePoweredOff -> {
                _connectionState.value = ConnectionState.Error("Bluetooth is powered off")
            }
            CBManagerStateUnauthorized -> {
                _connectionState.value = ConnectionState.Error("Bluetooth permission denied")
            }
            else -> {
                // Handle other states
            }
        }
    }

    override fun centralManager(
        central: CBCentralManager,
        didDiscoverPeripheral: CBPeripheral,
        advertisementData: Map<Any?, *>,
        RSSI: NSNumber
    ) {
        val device = BleDevice(
            id = didDiscoverPeripheral.identifier.UUIDString,
            name = didDiscoverPeripheral.name ?: "Unknown Device",
            rssi = RSSI.intValue
        )

        scope.launch {
            val currentList = _scannedDevices.value.toMutableList()
            val existingIndex = currentList.indexOfFirst { it.id == device.id }

            if (existingIndex != -1) {
                currentList[existingIndex] = device
            } else {
                currentList.add(device)
            }

            _scannedDevices.value = currentList
        }
    }

    override fun centralManager(
        central: CBCentralManager,
        didConnectPeripheral: CBPeripheral
    ) {
        currentDevice?.let {
            _connectionState.value = ConnectionState.Connected(it)
        }

        // Discover services
        didConnectPeripheral.discoverServices(null)
    }

    @ObjCSignatureOverride
    override fun centralManager(
        central: CBCentralManager,
        didDisconnectPeripheral: CBPeripheral,
        error: NSError?
    ) {
        _connectionState.value = ConnectionState.Disconnected
        _deviceInfo.value = null

        // Auto-reconnect logic
        currentDevice?.let { device ->
            scope.launch {
                delay(BleConstants.RECONNECT_DELAY_MS)
                if (_connectionState.value is ConnectionState.Disconnected) {
                    connect(device)
                }
            }
        }
    }

    @ObjCSignatureOverride
    override fun centralManager(
        central: CBCentralManager,
        didFailToConnectPeripheral: CBPeripheral,
        error: NSError?
    ) {
        _connectionState.value = ConnectionState.Error(
            error?.localizedDescription ?: "Connection failed"
        )
    }

    // MARK: - CBPeripheralDelegate

    override fun peripheral(
        peripheral: CBPeripheral,
        didDiscoverServices: NSError?
    ) {
        peripheral.services?.forEach { service ->
            val cbService = service as CBService

            // Discover characteristics for Battery Service
            if (cbService.UUID.UUIDString.equals(
                    BleConstants.BATTERY_SERVICE_UUID,
                    ignoreCase = true
                )) {
                peripheral.discoverCharacteristics(null, cbService)
            }

            // Discover characteristics for Heart Rate Service
            if (cbService.UUID.UUIDString.equals(
                    BleConstants.HEART_RATE_SERVICE_UUID,
                    ignoreCase = true
                )) {
                peripheral.discoverCharacteristics(null, cbService)
            }
        }
    }

    override fun peripheral(
        peripheral: CBPeripheral,
        didDiscoverCharacteristicsForService: CBService,
        error: NSError?
    ) {
        didDiscoverCharacteristicsForService.characteristics?.forEach { characteristic ->
            val cbChar = characteristic as CBCharacteristic

            // Read battery level
            if (cbChar.UUID.UUIDString.equals(
                    BleConstants.BATTERY_LEVEL_CHARACTERISTIC_UUID,
                    ignoreCase = true
                )) {
                peripheral.readValueForCharacteristic(cbChar)

                // Enable notifications
                if (cbChar.properties and CBCharacteristicPropertyNotify.toULong() != 0uL) {
                    peripheral.setNotifyValue(true, cbChar)
                }
            }

            // Read heart rate
            if (cbChar.UUID.UUIDString.equals(
                    BleConstants.HEART_RATE_MEASUREMENT_UUID,
                    ignoreCase = true
                )) {
                peripheral.readValueForCharacteristic(cbChar)

                // Enable notifications
                if (cbChar.properties and CBCharacteristicPropertyNotify.toULong() != 0uL) {
                    peripheral.setNotifyValue(true, cbChar)
                }
            }
        }
    }

    override fun peripheral(
        peripheral: CBPeripheral,
        didUpdateValueForCharacteristic: CBCharacteristic,
        error: NSError?
    ) {
        if (error != null) return

        when (didUpdateValueForCharacteristic.UUID.UUIDString.lowercase()) {
            BleConstants.BATTERY_LEVEL_CHARACTERISTIC_UUID.lowercase() -> {
                val data = didUpdateValueForCharacteristic.value
                data?.let { nsData ->
                    val bytes = ByteArray(nsData.length.toInt())
                    memScoped {
                        nsData.getBytes(bytes.usePinned { it.addressOf(0) }, nsData.length)
                    }

                    val batteryLevel = BleUtils.parseBatteryLevel(bytes)
                    batteryLevel?.let { level ->
                        currentDevice?.let { device ->
                            _deviceInfo.value = _deviceInfo.value?.copy(
                                batteryLevel = BatteryLevel(level)
                            ) ?: DeviceInfo(
                                device = device,
                                batteryLevel = BatteryLevel(level)
                            )
                        }
                    }
                }
            }

            BleConstants.HEART_RATE_MEASUREMENT_UUID.lowercase() -> {
                val data = didUpdateValueForCharacteristic.value
                data?.let { nsData ->
                    val bytes = ByteArray(nsData.length.toInt())
                    memScoped {
                        nsData.getBytes(bytes.usePinned { it.addressOf(0) }, nsData.length)
                    }

                    val heartRate = BleUtils.parseHeartRate(bytes)
                    heartRate?.let { bpm ->
                        currentDevice?.let { device ->
                            _deviceInfo.value = _deviceInfo.value?.copy(
                                heartRate = HeartRate(bpm)
                            ) ?: DeviceInfo(
                                device = device,
                                heartRate = HeartRate(bpm)
                            )
                        }
                    }
                }
            }
        }
    }
}