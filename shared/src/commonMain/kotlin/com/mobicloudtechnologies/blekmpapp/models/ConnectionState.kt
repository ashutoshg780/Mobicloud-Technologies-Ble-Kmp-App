// ConnectionState.kt - REPLACE THIS FILE
package com.mobicloudtechnologies.blekmpapp.models

sealed class ConnectionState {
    class Disconnected : ConnectionState()
    data class Connecting(val device: BleDevice) : ConnectionState()
    data class Connected(val device: BleDevice) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}