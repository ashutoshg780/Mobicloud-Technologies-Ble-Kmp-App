package com.mobicloudtechnologies.blekmpapp.ble

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.mobicloudtechnologies.blekmpapp.models.*
import com.mobicloudtechnologies.blekmpapp.utils.BleUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*

/**
 * Android-specific implementation of BleManager interface.
 *
 * This class handles all BLE operations on Android using the native BluetoothGatt API.
 * Features:
 * - BLE device scanning with automatic device list management
 * - GATT connection with auto-reconnect capability
 * - Battery Level characteristic reading (Service 0x180F, Char 0x2A19)
 * - Heart Rate characteristic reading (Service 0x180D, Char 0x2A37)
 * - Real-time characteristic notifications
 * - Background operation support via Foreground Service
 *
 * Required Permissions:
 * - Android 12+: BLUETOOTH_SCAN, BLUETOOTH_CONNECT, ACCESS_FINE_LOCATION
 * - Android < 12: ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION
 *
 * @param context Application context for Bluetooth operations
 *
 * @since 1.0
 * @author Mobicloud Technologies
 */
class AndroidBleManager(private val context: Context) : BleManager {

    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private var bluetoothGatt: BluetoothGatt? = null
    private var bleScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _scannedDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    override val scannedDevices: StateFlow<List<BleDevice>> = _scannedDevices.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _deviceInfo = MutableStateFlow<DeviceInfo?>(null)
    override val deviceInfo: StateFlow<DeviceInfo?> = _deviceInfo.asStateFlow()

    private var currentDevice: BleDevice? = null
    private var isScanning = false

    /**
     * Callback for BLE scan results.
     * Handles discovered devices and updates the scanned devices list.
     */
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (checkScanPermission()) {
                val device = BleDevice(
                    id = result.device.address,
                    name = result.device.name ?: "Unknown Device",
                    rssi = result.rssi
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
        }

        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            _connectionState.value = ConnectionState.Error("Scan failed: $errorCode")
        }
    }

    /**
     * Callback for GATT operations.
     * Handles connection state changes, service discovery, and characteristic updates.
     */
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (checkConnectPermission()) {
                        currentDevice?.let {
                            _connectionState.value = ConnectionState.Connected(it)
                        }
                        // Discover all available GATT services
                        gatt.discoverServices()
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
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
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Read and subscribe to Battery Level
                readBatteryLevel(gatt)
                enableBatteryNotifications(gatt)

                // Read and subscribe to Heart Rate
                readHeartRate(gatt)
                enableHeartRateNotifications(gatt)
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                handleCharacteristicUpdate(characteristic)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            handleCharacteristicUpdate(characteristic)
        }
    }

    override fun startScan() {
        if (!checkScanPermission()) {
            _connectionState.value = ConnectionState.Error("Missing Bluetooth permissions")
            return
        }

        if (isScanning) return

        _scannedDevices.value = emptyList()

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bleScanner?.startScan(null, scanSettings, scanCallback)
        isScanning = true

        // Auto-stop scan after duration
        scope.launch {
            delay(BleConstants.SCAN_DURATION_MS)
            stopScan()
        }
    }

    override fun stopScan() {
        if (!isScanning) return

        if (checkScanPermission()) {
            bleScanner?.stopScan(scanCallback)
        }
        isScanning = false
    }

    override suspend fun connect(device: BleDevice) {
        if (!checkConnectPermission()) {
            _connectionState.value = ConnectionState.Error("Missing Bluetooth connect permission")
            return
        }

        disconnect()

        currentDevice = device
        _connectionState.value = ConnectionState.Connecting

        withContext(Dispatchers.Main) {
            val bluetoothDevice = bluetoothAdapter?.getRemoteDevice(device.id)
            bluetoothGatt = bluetoothDevice?.connectGatt(
                context,
                true, // autoConnect
                gattCallback,
                BluetoothDevice.TRANSPORT_LE
            )
        }
    }

    override fun disconnect() {
        if (checkConnectPermission()) {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        }
        bluetoothGatt = null
        currentDevice = null
        _connectionState.value = ConnectionState.Disconnected
        _deviceInfo.value = null
    }

    override fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    override fun requestEnableBluetooth() {
        // Should be called from Activity context
    }

    /**
     * Read battery level from Battery Service.
     * Service UUID: 0x180F
     * Characteristic UUID: 0x2A19
     */
    private fun readBatteryLevel(gatt: BluetoothGatt) {
        val batteryService = gatt.getService(UUID.fromString(BleConstants.BATTERY_SERVICE_UUID))
        val batteryChar = batteryService?.getCharacteristic(
            UUID.fromString(BleConstants.BATTERY_LEVEL_CHARACTERISTIC_UUID)
        )

        if (checkConnectPermission() && batteryChar != null) {
            gatt.readCharacteristic(batteryChar)
        }
    }

    /**
     * Enable notifications for battery level changes.
     */
    private fun enableBatteryNotifications(gatt: BluetoothGatt) {
        val batteryService = gatt.getService(UUID.fromString(BleConstants.BATTERY_SERVICE_UUID))
        val batteryChar = batteryService?.getCharacteristic(
            UUID.fromString(BleConstants.BATTERY_LEVEL_CHARACTERISTIC_UUID)
        )

        if (checkConnectPermission() && batteryChar != null) {
            gatt.setCharacteristicNotification(batteryChar, true)

            val descriptor = batteryChar.getDescriptor(
                UUID.fromString(BleConstants.CLIENT_CHARACTERISTIC_CONFIG_UUID)
            )
            descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
        }
    }

    /**
     * Read heart rate from Heart Rate Service.
     * Service UUID: 0x180D
     * Characteristic UUID: 0x2A37
     */
    private fun readHeartRate(gatt: BluetoothGatt) {
        val heartRateService = gatt.getService(UUID.fromString(BleConstants.HEART_RATE_SERVICE_UUID))
        val heartRateChar = heartRateService?.getCharacteristic(
            UUID.fromString(BleConstants.HEART_RATE_MEASUREMENT_UUID)
        )

        if (checkConnectPermission() && heartRateChar != null) {
            gatt.readCharacteristic(heartRateChar)
        }
    }

    /**
     * Enable notifications for heart rate changes.
     */
    private fun enableHeartRateNotifications(gatt: BluetoothGatt) {
        val heartRateService = gatt.getService(UUID.fromString(BleConstants.HEART_RATE_SERVICE_UUID))
        val heartRateChar = heartRateService?.getCharacteristic(
            UUID.fromString(BleConstants.HEART_RATE_MEASUREMENT_UUID)
        )

        if (checkConnectPermission() && heartRateChar != null) {
            gatt.setCharacteristicNotification(heartRateChar, true)

            val descriptor = heartRateChar.getDescriptor(
                UUID.fromString(BleConstants.CLIENT_CHARACTERISTIC_CONFIG_UUID)
            )
            descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
        }
    }

    /**
     * Handle characteristic value updates.
     * Parses battery level and heart rate data and updates device info.
     */
    private fun handleCharacteristicUpdate(characteristic: BluetoothGattCharacteristic) {
        when (characteristic.uuid.toString()) {
            BleConstants.BATTERY_LEVEL_CHARACTERISTIC_UUID -> {
                val batteryLevel = BleUtils.parseBatteryLevel(characteristic.value)
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
            BleConstants.HEART_RATE_MEASUREMENT_UUID -> {
                val heartRate = BleUtils.parseHeartRate(characteristic.value)
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

    /**
     * Check if app has required scan permission.
     *
     * @return true if permission granted, false otherwise
     */
    private fun checkScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Check if app has required connect permission.
     *
     * @return true if permission granted, false otherwise
     */
    private fun checkConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}