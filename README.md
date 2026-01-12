#  BLE KMP App - Kotlin Multiplatform BLE Connection

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-blue.svg)](https://kotlinlang.org)
[![KMP](https://img.shields.io/badge/Kotlin%20Multiplatform-1.9.22-purple.svg)](https://kotlinlang.org/docs/multiplatform.html)
[![Android](https://img.shields.io/badge/Android-API%2024+-green.svg)](https://developer.android.com)
[![iOS](https://img.shields.io/badge/iOS-14.0+-black.svg)](https://developer.apple.com/ios)

A production-ready Kotlin Multiplatform application for Bluetooth Low Energy (BLE) device management with shared business logic across Android and iOS platforms.

---

## 📋 Features

### ✅ Core Functionality
- **BLE Device Scanning**: Discover nearby Bluetooth Low Energy devices
- **Persistent Connection**: Maintain continuous connection with auto-reconnect
- **Battery Monitoring**: Read battery level from Battery Service (UUID: 0x180F)
- **Heart Rate Monitoring**: Read heart rate data from Heart Rate Service (UUID: 0x180D)
- **Real-time Updates**: Live data streaming via Kotlin StateFlow
- **Cross-platform UI**: Modern Material Design 3 (Android) and SwiftUI (iOS)

### Bonus Features
- Smart Bluetooth enable prompts with native dialogs
- Dynamic connection status indicators
- Signal strength (RSSI) display with color coding
- Auto-detection of Bluetooth state changes
- Animated heart rate visualization
- Visual battery status with progress indicators

---

## 🏗Architecture

### Project Structure
```
BleKmpApp/
├── shared/                          # Shared KMP module
│   ├── commonMain/                  # Platform-agnostic code
│   │   ├── models/                  # Data models
│   │   │   ├── BleDevice.kt        # Device model
│   │   │   ├── ConnectionState.kt   # Connection states
│   │   │   └── DeviceInfo.kt       # Device information
│   │   ├── repository/              # Business logic
│   │   │   └── BleRepository.kt    # BLE operations wrapper
│   │   └── expect/                  # Platform interfaces
│   │       └── BleManager.kt       # expect class
│   ├── androidMain/                 # Android implementation
│   │   └── actual/
│   │       ├── BleManager.kt       # BluetoothGatt implementation
│   │       └── Platform.android.kt
│   └── iosMain/                     # iOS implementation
│       └── actual/
│           ├── BleManager.kt       # CoreBluetooth implementation
│           └── Platform.ios.kt
├── androidApp/                      # Android app module
│   └── MainActivity.kt              # Jetpack Compose UI
└── iosApp/                          # iOS app module
    └── ContentView.swift            # SwiftUI interface
```

### Shared Module Components

#### 1. **Models** (`commonMain/models/`)
Platform-agnostic data classes shared across Android and iOS:

```kotlin
// BLE device representation
data class BleDevice(
    val id: String,              // MAC (Android) / UUID (iOS)
    val name: String?,           // Device name
    val rssi: Int                // Signal strength
)

// Connection state machine
sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    data class Connected(val device: BleDevice) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

// Real-time device information
data class DeviceInfo(
    val device: BleDevice,
    val batteryLevel: BatteryLevel?,
    val heartRate: HeartRate?
)
```

#### 2. **BleManager Interface** (`expect/actual`)
Platform-specific implementations with unified API:

```kotlin
// Shared interface (expect)
expect class BleManager {
    val scannedDevices: StateFlow<List<BleDevice>>
    val connectionState: StateFlow<ConnectionState>
    val deviceInfo: StateFlow<DeviceInfo?>
    
    fun startScan()
    fun stopScan()
    suspend fun connect(device: BleDevice)
    fun disconnect()
}
```

**Android Implementation** (`androidMain`):
- Uses `BluetoothLeScanner` for device discovery
- `BluetoothGatt` for GATT operations
- GATT callbacks for characteristic notifications
- Supports Battery Service (0x180F) and Heart Rate Service (0x180D)

**iOS Implementation** (`iosMain`):
- Uses `CBCentralManager` for scanning and connection
- `CBPeripheral` for GATT operations
- Delegate pattern for characteristic updates
- Same GATT service support (Battery, Heart Rate)

#### 3. **Repository Pattern** (`BleRepository`)
Abstraction layer over BleManager for cleaner API:

```kotlin
class BleRepository(private val bleManager: BleManager) {
    val scannedDevices = bleManager.scannedDevices
    val connectionState = bleManager.connectionState
    val deviceInfo = bleManager.deviceInfo
    
    fun startScanning() = bleManager.startScan()
    fun stopScanning() = bleManager.stopScan()
    suspend fun connectToDevice(device: BleDevice) = bleManager.connect(device)
    fun disconnectDevice() = bleManager.disconnect()
}
```

---

## Technical Implementation

### Android Platform (`androidMain`)

**Key Technologies:**
- **BluetoothLeScanner**: Device discovery with scan filters
- **BluetoothGatt**: GATT client operations
- **GATT Services**:
    - Battery Service: UUID `0000180f-0000-1000-8000-00805f9b34fb`
    - Battery Level Characteristic: UUID `00002a19-0000-1000-8000-00805f9b34fb`
    - Heart Rate Service: UUID `0000180d-0000-1000-8000-00805f9b34fb`
    - Heart Rate Measurement: UUID `00002a37-0000-1000-8000-00805f9b34fb`

**Connection Flow:**
```
startScan() → ScanCallback → BleDevice discovered
    ↓
connect(device) → BluetoothGatt.connect()
    ↓
onConnectionStateChange → GATT_SUCCESS
    ↓
discoverServices() → onServicesDiscovered
    ↓
readCharacteristic() → onCharacteristicRead
    ↓
setCharacteristicNotification() → Real-time updates
```

**Auto-Reconnect Logic:**
```kotlin
private fun attemptReconnect() {
    scope.launch {
        delay(RECONNECT_DELAY)
        lastConnectedDevice?.let { device ->
            connect(device)
        }
    }
}
```

### iOS Platform (`iosMain`)

**Key Technologies:**
- **CBCentralManager**: Central role for BLE operations
- **CBPeripheral**: Peripheral device management
- **Same GATT Services**: Battery (0x180F), Heart Rate (0x180D)

**Connection Flow:**
```
startScan() → centralManager.scanForPeripherals()
    ↓
didDiscover peripheral → BleDevice created
    ↓
connect(device) → centralManager.connect(peripheral)
    ↓
didConnect → peripheral.discoverServices()
    ↓
didDiscoverServices → peripheral.discoverCharacteristics()
    ↓
didDiscoverCharacteristics → peripheral.readValue()
    ↓
didUpdateValueFor → Parse and emit data
```

**Background Mode Support:**
Requires `Info.plist` configuration:
```xml
<key>UIBackgroundModes</key>
<array>
    <string>bluetooth-central</string>
</array>
```

---

## Getting Started

### Prerequisites
- **Android Studio**: Arctic Fox or later
- **Xcode**: 14.0
- **Kotlin**: 1.9.22
- **Gradle**: 8.0+

### Clone & Build

```bash
# Clone repository
git clone https://github.com/ashutoshgithubs/Mobicloud-Technologies-Ble-Kmp-App.git
cd Mobicloud-Technologies-Ble-Kmp-App

# Build Android
./gradlew :androidApp:assembleDebug

# Build iOS (on macOS)
cd iosApp
xcodebuild -project iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator build
```

### Run Android

```bash
# Connect device or start emulator
./gradlew :androidApp:installDebug

# Or open in Android Studio and run
```

### Run iOS

```bash
# Open Xcode
cd iosApp
open iosApp.xcodeproj

# Select target device/simulator and press Run (Cmd+R)
```

---

[//]: # ()
[//]: # (## Screenshots)

[//]: # ()
[//]: # (### Android)

[//]: # (- **Scan Screen**: Modern Material Design 3 UI with device list)

[//]: # (- **Device Info**: Real-time battery and heart rate monitoring)

[//]: # (- **Connection Status**: Visual indicators for Bluetooth state)

[//]: # ()
[//]: # (### iOS)

[//]: # (- **SwiftUI Interface**: Native iOS design language)

[//]: # (- **Device Cards**: Smooth animations and transitions)

[//]: # (- **Status Indicators**: Dynamic icons for connection states)

[//]: # ()
[//]: # (---)

## Key Features Explained

### 1. **Bluetooth State Management**
Auto-detects Bluetooth status and prompts user to enable:
- **Android**: Shows AlertDialog with direct intent to enable Bluetooth
- **iOS**: Shows native alert with link to Settings app

### 2. **Connection State Machine**
Robust state management using sealed classes:
- `Disconnected`: Initial state, ready to scan
- `Connecting`: Connection in progress
- `Connected`: Active connection with device info
- `Error`: Connection failed with error message

### 3. **Real-time Data Streaming**
Uses Kotlin `StateFlow` for reactive updates:
```kotlin
val deviceInfo: StateFlow<DeviceInfo?> = _deviceInfo.asStateFlow()
```
UI automatically updates when data changes.

### 4. **GATT Characteristic Parsing**

**Battery Level (0x2A19):**
```kotlin
val batteryPercentage = characteristic.value?.get(0)?.toInt() ?: 0
```

**Heart Rate (0x2A37):**
```kotlin
val flags = characteristic.value?.get(0)?.toInt() ?: 0
val heartRate = if (flags and 0x01 == 0) {
    characteristic.value?.get(1)?.toInt() ?: 0  // UINT8
} else {
    // UINT16 format
    val byte1 = characteristic.value?.get(1)?.toInt() ?: 0
    val byte2 = characteristic.value?.get(2)?.toInt() ?: 0
    (byte2 shl 8) or byte1
}
```

---

## API Documentation

### BleManager Interface

#### `startScan()`
Starts BLE device scanning. Discovered devices are emitted to `scannedDevices` StateFlow.

**Android**: Uses `BluetoothLeScanner.startScan()`  
**iOS**: Uses `CBCentralManager.scanForPeripherals()`

#### `stopScan()`
Stops active BLE scanning.

#### `suspend fun connect(device: BleDevice)`
Initiates connection to specified BLE device. Updates `connectionState` flow.

**Throws**: `BluetoothException` if connection fails

#### `disconnect()`
Disconnects from currently connected device.

### State Flows

#### `scannedDevices: StateFlow<List<BleDevice>>`
Real-time list of discovered BLE devices during scanning.

#### `connectionState: StateFlow<ConnectionState>`
Current connection state (Disconnected, Connecting, Connected, Error).

#### `deviceInfo: StateFlow<DeviceInfo?>`
Real-time device information including battery level and heart rate (when connected).

---

## Testing

### Manual Testing Steps

1. **Scan Test**:
    - Open app
    - Tap "Start Scanning"
    - Verify devices appear in list
    - Check RSSI values update

2. **Connection Test**:
    - Select device from list
    - Tap "Connect"
    - Verify connection status changes
    - Check device info appears

3. **Battery Test**:
    - Connect to device with Battery Service
    - Verify battery percentage displays
    - Check visual progress bar

4. **Heart Rate Test**:
    - Connect to heart rate monitor
    - Verify BPM updates in real-time
    - Check animated heart icon

5. **Reconnection Test**:
    - Turn off Bluetooth on phone
    - Turn Bluetooth back on
    - Verify app reconnects automatically

---

## Troubleshooting

### Android

**Issue**: "No devices found"
- **Solution**: Check location permissions are granted
- Enable location services in device settings

**Issue**: "Connection failed"
- **Solution**: Move closer to BLE device
- Ensure device is not already connected to another app

### iOS

**Issue**: App cannot scan
- **Solution**: Check Bluetooth permissions in Settings
- Verify Info.plist has required keys

**Issue**: Connection drops in background
- **Solution**: Add `bluetooth-central` to UIBackgroundModes in Info.plist

---

##  Dependencies

### Shared Module
```kotlin
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}
```

### Android
```kotlin
dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.compose.material3:material3:1.1.2")
}
```

### iOS
- CoreBluetooth framework (built-in)
- SwiftUI (built-in)

---

##  Interview Task Completion

###  Completed Requirements

| Requirement | Status | Notes |
|-------------|--------|-------|
| BLE Scanning | ✅ | Both platforms |
| Device Connection | ✅ | With auto-reconnect |
| Battery Service | ✅ | UUID 0x180F/0x2A19 |
| Heart Rate Service | ✅ | UUID 0x180D/0x2A37 |
| KMP Architecture | ✅ | Proper expect/actual |
| Shared Models | ✅ | BleDevice, ConnectionState |
| StateFlow Updates | ✅ | Real-time data |
| Modern UI | ✅ | Compose + SwiftUI |

[//]: # ()
[//]: # (### 🎁 Bonus Features)

[//]: # (- Bluetooth enable dialogs)

[//]: # (- Dynamic status indicators)

[//]: # (- Signal strength display)

[//]: # (- Animated visualizations)

[//]: # (- Auto Bluetooth detection)

---

[//]: # ()
[//]: # (## 📄 License)

[//]: # ()
[//]: # (This project is created for interview demonstration purposes.)

---

##  Author

**Ashutosh**  
Mobile Application Developer  
Evatoz Solutions

ashutoshg780@outlook.com


---

## Acknowledgments

- Kotlin Multiplatform documentation
- Android BLE guide
- Apple CoreBluetooth framework
- Material Design 3 guidelines
- SwiftUI best practices

---

**Built with using Kotlin Multiplatform**