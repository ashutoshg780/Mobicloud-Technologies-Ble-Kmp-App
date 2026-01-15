package com.mobicloudtechnologies.blekmpapp.ble

import com.mobicloudtechnologies.blekmpapp.models.BleDevice
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Auto-Reconnect Manager
 * Handles automatic reconnection with exponential backoff
 */
class AutoReconnectManager {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _reconnectState = MutableStateFlow<ReconnectState>(ReconnectState.Idle)
    val reconnectState: StateFlow<ReconnectState> = _reconnectState.asStateFlow()

    private var reconnectJob: Job? = null
    private var lastConnectedDevice: BleDevice? = null
    private var retryCount = 0

    // Exponential backoff delays: 1s, 2s, 4s, 8s, 15s (max)
    private val retryDelays = listOf(1000L, 2000L, 4000L, 8000L, 15000L)
    private val maxRetries = 5

    /**
     * Save last connected device for auto-reconnect
     */
    fun saveLastConnectedDevice(device: BleDevice) {
        lastConnectedDevice = device
        println("✅ AutoReconnect: Saved device - ${device.name} (${device.id})")
    }

    /**
     * Clear saved device (called on manual disconnect)
     */
    fun clearSavedDevice() {
        lastConnectedDevice = null
        retryCount = 0
        cancelReconnect()
        _reconnectState.value = ReconnectState.Idle
        println("❌ AutoReconnect: Cleared saved device")
    }

    /**
     * Start auto-reconnect attempts
     */
//    fun startAutoReconnect(
//        onReconnect: suspend (BleDevice) -> Boolean
//    ) {
//        val device = lastConnectedDevice ?: run {
//            println("⚠️ AutoReconnect: No saved device to reconnect")
//            return
//        }
//
//        // Cancel any existing reconnect job
//        cancelReconnect()
//
//        reconnectJob = scope.launch {
//            retryCount = 0
//
//            while (retryCount < maxRetries) {
//                val delay = retryDelays.getOrElse(retryCount) { retryDelays.last() }
//
//                _reconnectState.value = ReconnectState.Reconnecting(
//                    device = device,
//                    attempt = retryCount + 1,
//                    maxAttempts = maxRetries
//                )
//
//                println("🔄 AutoReconnect: Attempt ${retryCount + 1}/$maxRetries for ${device.name}")
//
//                try {
//                    val success = onReconnect(device)
//
//                    if (success) {
//                        println("✅ AutoReconnect: Successfully reconnected!")
//                        _reconnectState.value = ReconnectState.Success(device)
//                        return@launch
//                    } else {
//                        println("❌ AutoReconnect: Attempt ${retryCount + 1} failed")
//                        retryCount++
//
//                        if (retryCount < maxRetries) {
//                            println("⏱️ AutoReconnect: Waiting ${delay}ms before retry")
//                            delay(delay)
//                        }
//                    }
//                } catch (e: Exception) {
//                    println("❌ AutoReconnect: Error - ${e.message}")
//                    retryCount++
//
//                    if (retryCount < maxRetries) {
//                        delay(delay)
//                    }
//                }
//            }
//
//            // All retries exhausted
//            println("❌ AutoReconnect: All retries exhausted")
//            _reconnectState.value = ReconnectState.Failed(device, "Connection failed after $maxRetries attempts")
//        }
//    }

    fun startAutoReconnect(
        onReconnect: suspend (BleDevice) -> Boolean
    ) {
        val device = lastConnectedDevice ?: return

        cancelReconnect()

        reconnectJob = scope.launch {
            retryCount = 0

            while (retryCount < maxRetries && isActive) {
                val delayMs = retryDelays.getOrElse(retryCount) { retryDelays.last() }

                _reconnectState.value = ReconnectState.Reconnecting(
                    device = device,
                    attempt = retryCount + 1,
                    maxAttempts = maxRetries
                )

                println("🔄 AutoReconnect: Attempt ${retryCount + 1}/$maxRetries for ${device.id}")

                val success = try {
                    onReconnect(device)
                } catch (e: Exception) {
                    false
                }

                if (success) {
                    println("✅ AutoReconnect: Reconnected successfully")
                    _reconnectState.value = ReconnectState.Success(device)
                    return@launch
                }

                retryCount++
                delay(delayMs)
            }

            _reconnectState.value = ReconnectState.Failed(device, "Failed after $maxRetries attempts")
        }
    }


    /**
     * Cancel ongoing reconnection attempts
     */
    fun cancelReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        retryCount = 0
    }

    /**
     * Get last connected device
     */
    fun getLastConnectedDevice(): BleDevice? = lastConnectedDevice

    /**
     * Check if auto-reconnect is in progress
     */
    fun isReconnecting(): Boolean = reconnectState.value is ReconnectState.Reconnecting
}

/**
 * Reconnection States
 */
sealed class ReconnectState {
    object Idle : ReconnectState()

    data class Reconnecting(
        val device: BleDevice,
        val attempt: Int,
        val maxAttempts: Int
    ) : ReconnectState()

    data class Success(val device: BleDevice) : ReconnectState()

    data class Failed(
        val device: BleDevice,
        val reason: String
    ) : ReconnectState()
}