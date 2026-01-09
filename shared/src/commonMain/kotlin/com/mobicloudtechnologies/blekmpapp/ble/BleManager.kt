package com.mobicloudtechnologies.blekmpapp.ble

import com.mobicloudtechnologies.blekmpapp.models.*
import kotlinx.coroutines.flow.StateFlow

/**
 * Platform-agnostic interface for Bluetooth Low Energy (BLE) operations.
 *
 * This interface provides a unified API for BLE operations across Android and iOS platforms.
 * Platform-specific implementations are located in:
 * - androidMain: AndroidBleManager (uses Android BluetoothGatt API)
 * - iosMain: IosBleManager (uses iOS CoreBluetooth framework)
 *
 * @since 1.0
 * @author Mobicloud Technologies
 */
interface BleManager {

    /**
     * Flow of scanned BLE devices.
     *
     * Emits a list of BLE devices found during scanning. The list is updated in real-time
     * as new devices are discovered. Duplicate devices (same MAC/UUID) are automatically
     * merged with their latest RSSI values.
     *
     * @return StateFlow containing list of discovered BleDevice objects
     */
    val scannedDevices: StateFlow<List<BleDevice>>

    /**
     * Flow of current connection state.
     *
     * Emits connection state changes including:
     * - Disconnected: No active connection
     * - Connecting: Connection attempt in progress
     * - Connected: Successfully connected to a device
     * - Error: Connection failed with error message
     *
     * @return StateFlow containing current ConnectionState
     */
    val connectionState: StateFlow<ConnectionState>

    /**
     * Flow of device information including GATT characteristics.
     *
     * Emits real-time updates of device information including:
     * - Battery level (from Battery Service 0x180F)
     * - Heart rate (from Heart Rate Service 0x180D)
     * - Custom GATT service data
     *
     * Returns null when no device is connected.
     *
     * @return StateFlow containing DeviceInfo or null
     */
    val deviceInfo: StateFlow<DeviceInfo?>

    /**
     * Start scanning for nearby BLE devices.
     *
     * Initiates a BLE scan that will continue for SCAN_DURATION_MS (default 10 seconds)
     * or until stopScan() is called. Requires Bluetooth and location permissions.
     *
     * Platform-specific behavior:
     * - Android: Uses BluetoothLeScanner with LOW_LATENCY scan mode
     * - iOS: Uses CBCentralManager.scanForPeripherals
     *
     * @throws SecurityException if required permissions are not granted
     */
    fun startScan()

    /**
     * Stop scanning for BLE devices.
     *
     * Stops an ongoing BLE scan. If no scan is active, this method has no effect.
     */
    fun stopScan()

    /**
     * Connect to a specific BLE device.
     *
     * Initiates a connection to the specified BLE device. On successful connection:
     * 1. Updates connectionState to Connected
     * 2. Discovers available GATT services
     * 3. Subscribes to Battery and Heart Rate characteristics
     * 4. Enables auto-reconnect on disconnection
     *
     * Platform-specific behavior:
     * - Android: Uses BluetoothGatt with autoConnect enabled
     * - iOS: Uses CBCentralManager.connect with automatic reconnection
     *
     * @param device The BLE device to connect to
     * @throws IllegalStateException if BleManager is not initialized (Android only)
     * @throws SecurityException if BLUETOOTH_CONNECT permission is not granted (Android 12+)
     */
    suspend fun connect(device: BleDevice)

    /**
     * Disconnect from the currently connected device.
     *
     * Terminates the active BLE connection and cleans up resources:
     * - Closes GATT connection
     * - Clears device information
     * - Updates connectionState to Disconnected
     * - Disables auto-reconnect
     */
    fun disconnect()

    /**
     * Check if Bluetooth is enabled on the device.
     *
     * @return true if Bluetooth is enabled, false otherwise
     */
    fun isBluetoothEnabled(): Boolean

    /**
     * Request to enable Bluetooth.
     *
     * Platform-specific behavior:
     * - Android: Should be called from Activity context to show enable dialog
     * - iOS: Handled automatically by the system with permission prompts
     */
    fun requestEnableBluetooth()
}