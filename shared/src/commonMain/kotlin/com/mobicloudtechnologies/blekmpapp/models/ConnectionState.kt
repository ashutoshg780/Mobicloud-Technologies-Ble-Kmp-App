package com.mobicloudtechnologies.blekmpapp.models

/**
 * Sealed class representing all possible BLE connection states.
 *
 * This hierarchy provides type-safe handling of connection state changes
 * and associated data.
 *
 * @since 1.0
 */
sealed class ConnectionState {
    /**
     * Device is not connected.
     * Initial state before any connection attempt.
     */
    object Disconnected : ConnectionState()

    /**
     * Connection attempt in progress.
     * Transitional state between Disconnected and Connected/Error.
     */
    object Connecting : ConnectionState()

    /**
     * Successfully connected to a BLE device.
     *
     * @property device The connected BLE device information
     */
    data class Connected(val device: BleDevice) : ConnectionState()

    /**
     * Connection failed or error occurred.
     *
     * @property message Human-readable error description
     */
    data class Error(val message: String) : ConnectionState()
}