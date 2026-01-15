import SwiftUI
import Shared
import CoreBluetooth

// MARK: - Main Content View
struct ContentView: View {
    @StateObject private var viewModel = BleViewModel()
    @StateObject private var bluetoothManager = BluetoothStatusManager()
    @State private var selectedTab = 0
    @State private var showBluetoothAlert = false

    var body: some View {
        TabView(selection: $selectedTab) {
            ScanView(
                viewModel: viewModel,
                isBluetoothEnabled: bluetoothManager.isBluetoothEnabled,
                onBluetoothAlert: { showBluetoothAlert = true }
            )
            .tabItem {
                Label("Scan", systemImage: "antenna.radiowaves.left.and.right")
            }
            .tag(0)

            DeviceInfoView(viewModel: viewModel)
                .tabItem {
                    // ✅ FIX: Add badge when connected
                    if viewModel.connectionState is ConnectionState.Connected {
                        Label("Device", systemImage: "device.laptop")
                            .badge(1)
                    } else {
                        Label("Device", systemImage: "device.laptop")
                    }
                }
                .tag(1)
        }
        .accentColor(Color(hex: "6366F1"))
        .alert("Enable Bluetooth", isPresented: $showBluetoothAlert) {
            Button("Open Settings", role: .none) {
                openBluetoothSettings()
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Bluetooth is required to scan and connect to BLE devices. Please enable it in Settings.")
        }
    }

    private func openBluetoothSettings() {
        if let url = URL(string: "App-Prefs:root=Bluetooth") {
            if UIApplication.shared.canOpenURL(url) {
                UIApplication.shared.open(url)
            } else if let settingsUrl = URL(string: UIApplication.openSettingsURLString) {
                UIApplication.shared.open(settingsUrl)
            }
        }
    }
}

// MARK: - Bluetooth Status Manager
class BluetoothStatusManager: NSObject, ObservableObject, CBCentralManagerDelegate {
    @Published var isBluetoothEnabled = false
    private var centralManager: CBCentralManager?

    override init() {
        super.init()
        centralManager = CBCentralManager(delegate: self, queue: nil)
    }

    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        DispatchQueue.main.async {
            self.isBluetoothEnabled = central.state == .poweredOn
        }
    }
}

struct ScanView: View {
    @ObservedObject var viewModel: BleViewModel
    let isBluetoothEnabled: Bool
    let onBluetoothAlert: () -> Void

    var body: some View {
        NavigationView {
            ZStack {
                Color(hex: "F8FAFC")
                    .ignoresSafeArea()

                ScrollView {
                    VStack(spacing: 16) {
                        HStack {
                            Text("BLE Manager")
                                .font(.system(size: 34, weight: .bold))
                            Spacer()
                            BluetoothStatusIcon(
                                connectionState: viewModel.connectionState,
                                hasDevices: !viewModel.scannedDevices.isEmpty,
                                isBluetoothEnabled: isBluetoothEnabled,
                                onBluetoothClick: onBluetoothAlert
                            )
                        }
                        .padding(.horizontal)
                        .padding(.top, 8)

                        if viewModel.connectionState is ConnectionState.Connected {
                            if let connectedState = viewModel.connectionState as? ConnectionState.Connected {
                                ConnectedBanner(device: connectedState.device)
                                    .padding(.horizontal)
                            }
                        }

                        ScanButton(
                            isScanning: viewModel.isScanning,
                            isBluetoothEnabled: isBluetoothEnabled,
                            onBluetoothAlert: onBluetoothAlert
                        ) {
                            if isBluetoothEnabled {
                                if viewModel.isScanning {
                                    viewModel.stopScan()
                                } else {
                                    viewModel.startScan()
                                }
                            } else {
                                onBluetoothAlert()
                            }
                        }
                        .padding(.horizontal)

                        DeviceStatsCard(deviceCount: viewModel.scannedDevices.count)
                            .padding(.horizontal)

                        if !isBluetoothEnabled {
                            BluetoothDisabledWarning(onClick: onBluetoothAlert)
                                .padding(.horizontal)
                        } else if viewModel.scannedDevices.isEmpty {
                            EmptyDeviceList(isScanning: viewModel.isScanning)
                                .padding(.top, 32)
                        } else {
                            LazyVStack(spacing: 12) {
                                ForEach(viewModel.scannedDevices, id: \.id) { device in
                                    let connectingDeviceId = (viewModel.connectionState as? ConnectionState.Connecting)?.device.id

                                    ModernDeviceCard(
                                        device: device,
                                        isConnected: viewModel.connectionState is ConnectionState.Connected &&
                                                    (viewModel.connectionState as? ConnectionState.Connected)?.device.id == device.id,
                                        isConnecting: viewModel.connectionState is ConnectionState.Connecting &&
                                                     connectingDeviceId == device.id
                                    ) {
                                        viewModel.connect(to: device)
                                    }
                                }
                            }
                            .padding(.horizontal)
                        }
                    }
                    .padding(.vertical)
                }
            }
            .navigationBarHidden(true)
        }
    }
}

// MARK: - Scan View
// struct ScanView: View {
//     @ObservedObject var viewModel: BleViewModel
//     let isBluetoothEnabled: Bool
//     let onBluetoothAlert: () -> Void
//
//     var body: some View {
//         NavigationView {
//             ZStack {
//                 Color(hex: "F8FAFC")
//                     .ignoresSafeArea()
//
//                 ScrollView {
//                     VStack(spacing: 16) {
//                         HStack {
//                             Text("BLE Manager")
//                                 .font(.system(size: 34, weight: .bold))
//                             Spacer()
//                             // ✅ FIX: Connection state indicator
//                             BluetoothStatusIcon(
//                                 connectionState: viewModel.connectionState,
//                                 hasDevices: !viewModel.scannedDevices.isEmpty,
//                                 isBluetoothEnabled: isBluetoothEnabled,
//                                 onBluetoothClick: onBluetoothAlert
//                             )
//                         }
//                         .padding(.horizontal)
//                         .padding(.top, 8)
//
//                         // ✅ FIX: Show connected banner
//                         if viewModel.connectionState is ConnectionState.Connected {
//                             if let connectedState = viewModel.connectionState as? ConnectionState.Connected {
//                                 ConnectedBanner(device: connectedState.device)
//                                     .padding(.horizontal)
//                             }
//                         }
//
//                         ScanButton(
//                             isScanning: viewModel.isScanning,
//                             isBluetoothEnabled: isBluetoothEnabled,
//                             onBluetoothAlert: onBluetoothAlert
//                         ) {
//                             if isBluetoothEnabled {
//                                 if viewModel.isScanning {
//                                     viewModel.stopScan()
//                                 } else {
//                                     viewModel.startScan()
//                                 }
//                             } else {
//                                 onBluetoothAlert()
//                             }
//                         }
//                         .padding(.horizontal)
//
//                         DeviceStatsCard(deviceCount: viewModel.scannedDevices.count)
//                             .padding(.horizontal)
//
//                         if !isBluetoothEnabled {
//                             BluetoothDisabledWarning(onClick: onBluetoothAlert)
//                                 .padding(.horizontal)
//                         } else if viewModel.scannedDevices.isEmpty {
//                             EmptyDeviceList(isScanning: viewModel.isScanning)
//                                 .padding(.top, 32)
//                         } else {
//                             LazyVStack(spacing: 12) {
//                                 ForEach(viewModel.scannedDevices, id: \.id) { device in
//                                     // ✅ FIX: Pass connection state
//                                     ModernDeviceCard(
//                                         device: device,
//                                         isConnected: viewModel.connectionState is ConnectionState.Connected &&
//                                             (viewModel.connectionState as? ConnectionState.Connected)?.device.id == device.id,
//                                         isConnecting: viewModel.connectionState is ConnectionState.Connecting &&
//                                             (viewModel.connectionState as? ConnectionState.Connecting)?.device.id == device.id
//                                     ) {
//                                         viewModel.connect(to: device)
//                                     }
//
//                                 }
//                             }
//                             .padding(.horizontal)
//                         }
//                     }
//                     .padding(.vertical)
//                 }
//             }
//             .navigationBarHidden(true)
//         }
//     }
// }

// ✅ NEW: Connected Banner for iOS
struct ConnectedBanner: View {
    let device: BleDevice

    var body: some View {
        HStack(spacing: 16) {
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 24))
                .foregroundColor(Color(hex: "10B981"))

            VStack(alignment: .leading, spacing: 4) {
                Text("Connected")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(Color(hex: "10B981"))

                Text(device.name ?? "Unknown Device")
                    .font(.system(size: 15, weight: .medium))
                    .foregroundColor(Color(hex: "1E293B"))
            }

            Spacer()

            Image(systemName: "bluetooth.connected")
                .font(.system(size: 20))
                .foregroundColor(Color(hex: "10B981"))
        }
        .padding(16)
        .background(Color(hex: "10B981").opacity(0.1))
        .cornerRadius(16)
    }
}

struct BluetoothStatusIcon: View {
    let connectionState: ConnectionState
    let hasDevices: Bool
    let isBluetoothEnabled: Bool
    let onBluetoothClick: () -> Void

    @State private var isAnimating = false

    var iconName: String {
        switch connectionState {
        case is ConnectionState.Connected:
            return "bluetooth.connected"
        case is ConnectionState.Connecting:
            return "dot.radiowaves.left.and.right"
        default:
            if !isBluetoothEnabled {
                return "bluetooth.slash"
            }
            return hasDevices ? "bluetooth" : "bluetooth.slash"
        }
    }

    var iconColor: Color {
        switch connectionState {
        case is ConnectionState.Connected:
            return Color(hex: "10B981")
        case is ConnectionState.Connecting:
            return Color(hex: "6366F1")
        default:
            if !isBluetoothEnabled {
                return Color(hex: "6B7280")
            }
            return hasDevices ? Color(hex: "6366F1") : Color(hex: "6B7280")
        }
    }

    var body: some View {
        ZStack {
            Circle()
                .fill(iconColor.opacity(0.2))
                .frame(width: 40, height: 40)

            Image(systemName: iconName)
                .font(.system(size: 20, weight: .semibold))
                .foregroundColor(iconColor)
                .scaleEffect(isAnimating ? 1.2 : 1.0)
                .animation(
                    connectionState is ConnectionState.Connecting ?
                        Animation.easeInOut(duration: 1.0).repeatForever(autoreverses: true) : .default,
                    value: isAnimating
                )
        }
        .onAppear {
            if connectionState is ConnectionState.Connecting {
                isAnimating = true
            }
        }
        .onChange(of: connectionState) { newState in
            isAnimating = newState is ConnectionState.Connecting
        }
        .onTapGesture {
            if !isBluetoothEnabled {
                onBluetoothClick()
            }
        }
    }
}

struct ScanButton: View {
    let isScanning: Bool
    let isBluetoothEnabled: Bool
    let onBluetoothAlert: () -> Void
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: buttonIcon)
                    .font(.system(size: 20, weight: .semibold))
                Text(buttonText)
                    .font(.system(size: 17, weight: .semibold))
            }
            .frame(maxWidth: .infinity)
            .frame(height: 56)
            .background(buttonGradient)
            .foregroundColor(.white)
            .cornerRadius(16)
            .shadow(color: buttonShadowColor, radius: 8, x: 0, y: 4)
        }
    }

    private var buttonIcon: String {
        if !isBluetoothEnabled {
            return "bluetooth.slash"
        }
        return isScanning ? "stop.fill" : "play.fill"
    }

    private var buttonText: String {
        if !isBluetoothEnabled {
            return "Enable Bluetooth"
        }
        return isScanning ? "Stop Scanning" : "Start Scanning"
    }

    private var buttonGradient: LinearGradient {
        if !isBluetoothEnabled {
            return LinearGradient(
                colors: [Color(hex: "6B7280"), Color(hex: "4B5563")],
                startPoint: .leading,
                endPoint: .trailing
            )
        }
        if isScanning {
            return LinearGradient(
                colors: [Color(hex: "EF4444"), Color(hex: "DC2626")],
                startPoint: .leading,
                endPoint: .trailing
            )
        }
        return LinearGradient(
            colors: [Color(hex: "6366F1"), Color(hex: "8B5CF6")],
            startPoint: .leading,
            endPoint: .trailing
        )
    }

    private var buttonShadowColor: Color {
        if !isBluetoothEnabled {
            return Color(hex: "6B7280").opacity(0.3)
        }
        return (isScanning ? Color(hex: "EF4444") : Color(hex: "6366F1")).opacity(0.3)
    }
}

struct BluetoothDisabledWarning: View {
    let onClick: () -> Void

    var body: some View {
        Button(action: onClick) {
            HStack(spacing: 16) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .font(.system(size: 28))
                    .foregroundColor(Color(hex: "F59E0B"))

                VStack(alignment: .leading, spacing: 4) {
                    Text("Bluetooth is Disabled")
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundColor(Color(hex: "92400E"))

                    Text("Tap to enable Bluetooth and start scanning")
                        .font(.system(size: 14))
                        .foregroundColor(Color(hex: "92400E").opacity(0.7))
                }

                Spacer()

                Image(systemName: "chevron.right")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(Color(hex: "92400E"))
            }
            .padding(20)
            .background(Color(hex: "FEF3C7"))
            .cornerRadius(16)
        }
        .buttonStyle(PlainButtonStyle())
    }
}

struct DeviceStatsCard: View {
    let deviceCount: Int

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text("Devices Found")
                    .font(.system(size: 15))
                    .foregroundColor(Color(hex: "64748B"))
                Text("\(deviceCount)")
                    .font(.system(size: 36, weight: .bold))
                    .foregroundColor(Color(hex: "6366F1"))
            }

            Spacer()

            ZStack {
                Circle()
                    .fill(
                        LinearGradient(
                            colors: [Color(hex: "6366F1"), Color(hex: "8B5CF6")],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .frame(width: 60, height: 60)

                Image(systemName: "wave.3.right")
                    .font(.system(size: 28))
                    .foregroundColor(.white)
            }
        }
        .padding(20)
        .background(Color.white)
        .cornerRadius(20)
        .shadow(color: Color.black.opacity(0.05), radius: 8, x: 0, y: 2)
    }
}

struct EmptyDeviceList: View {
    let isScanning: Bool

    var body: some View {
        VStack(spacing: 16) {
            if isScanning {
                ProgressView()
                    .scaleEffect(1.5)
                    .tint(Color(hex: "6366F1"))
                    .padding(.bottom, 8)
                Text("Scanning for devices...")
                    .font(.system(size: 17, weight: .medium))
                    .foregroundColor(Color(hex: "64748B"))
            } else {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 56))
                    .foregroundColor(Color(hex: "CBD5E1"))
                    .padding(.bottom, 8)
                Text("No devices found")
                    .font(.system(size: 19, weight: .semibold))
                    .foregroundColor(Color(hex: "64748B"))
                Text("Start scanning to discover BLE devices")
                    .font(.system(size: 15))
                    .foregroundColor(Color(hex: "94A3B8"))
            }
        }
        .padding(32)
    }
}

// ✅ UPDATED: Device Card with connection state
struct ModernDeviceCard: View {
    let device: BleDevice
    let isConnected: Bool
    let isConnecting: Bool
    let onConnect: () -> Void

    var signalColor: Color {
        if device.rssi >= -50 {
            return Color(hex: "10B981")
        } else if device.rssi >= -70 {
            return Color(hex: "F59E0B")
        } else {
            return Color(hex: "EF4444")
        }
    }

    var body: some View {
        HStack(spacing: 16) {
            ZStack {
                RoundedRectangle(cornerRadius: 16)
                    .fill(
                        isConnected ?
                        LinearGradient(
                            colors: [Color(hex: "10B981"), Color(hex: "059669")],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        ) :
                        LinearGradient(
                            colors: [Color(hex: "E0E7FF"), Color(hex: "DDD6FE")],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .frame(width: 56, height: 56)

                Image(systemName: isConnected ? "bluetooth.connected" : "bluetooth")
                    .font(.system(size: 26))
                    .foregroundColor(isConnected ? .white : Color(hex: "6366F1"))
            }

            VStack(alignment: .leading, spacing: 4) {
                Text(device.name ?? "Unknown Device")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundColor(Color(hex: "1E293B"))

                HStack(spacing: 4) {
                    Image(systemName: "number")
                        .font(.system(size: 11))
                        .foregroundColor(Color(hex: "64748B"))
                    Text(String(device.id.prefix(17)))
                        .font(.system(size: 13))
                        .foregroundColor(Color(hex: "64748B"))
                }

                HStack(spacing: 4) {
                    Image(systemName: "antenna.radiowaves.left.and.right")
                        .font(.system(size: 11))
                        .foregroundColor(signalColor)
                    Text("\(device.rssi) dBm")
                        .font(.system(size: 13))
                        .foregroundColor(Color(hex: "64748B"))
                }
            }

            Spacer()

            DeviceStatusButton(
                isConnected: isConnected,
                isConnecting: isConnecting,
                onConnect: onConnect
            )


            // ✅ FIX: Show different states
//             if isConnected {
//                 HStack(spacing: 6) {
//                     Image(systemName: "checkmark.circle.fill")
//                         .font(.system(size: 14, weight: .medium))
//                         .foregroundColor(Color(hex: "10B981"))
//                     Text("Connected")
//                         .font(.system(size: 14, weight: .medium))
//                         .foregroundColor(Color(hex: "10B981"))
//                 }
//                 .padding(.horizontal, 16)
//                 .padding(.vertical, 10)
//                 .background(Color(hex: "10B981").opacity(0.15))
//                 .cornerRadius(12)
//             } else if isConnecting {
//                 HStack(spacing: 6) {
//                     ProgressView()
//                         .scaleEffect(0.8)
//                         .tint(Color(hex: "6366F1"))
//                     Text("Connecting")
//                         .font(.system(size: 14, weight: .medium))
//                         .foregroundColor(Color(hex: "6366F1"))
//                 }
//                 .padding(.horizontal, 16)
//                 .padding(.vertical, 10)
//                 .background(Color(hex: "6366F1").opacity(0.15))
//                 .cornerRadius(12)
//             } else {
//                 Button(action: onConnect) {
//                     HStack(spacing: 6) {
//                         Image(systemName: "link")
//                             .font(.system(size: 14, weight: .medium))
//                         Text("Connect")
//                             .font(.system(size: 14, weight: .medium))
//                     }
//                     .padding(.horizontal, 16)
//                     .padding(.vertical, 10)
//                     .background(Color(hex: "6366F1"))
//                     .foregroundColor(.white)
//                     .cornerRadius(12)
//                 }
//             }
        }
        .padding(20)
        .background(isConnected ? Color(hex: "10B981").opacity(0.05) : Color.white)
        .cornerRadius(20)
        .shadow(color: Color.black.opacity(0.05), radius: 8, x: 0, y: 2)
    }
}

struct DeviceStatusButton: View {
    let isConnected: Bool
    let isConnecting: Bool
    let onConnect: () -> Void

    var bgColor: Color {
        if isConnected { return Color(hex: "10B981") }
        if isConnecting { return Color(hex: "6366F1").opacity(0.25) }
        return Color(hex: "6366F1")
    }

    var fgColor: Color {
        if isConnecting { return Color(hex: "6366F1") }
        return .white
    }

    var body: some View {
        HStack(spacing: 6) {
            if isConnected {
                Image(systemName: "checkmark")
                Text("Connected")
            } else if isConnecting {
                ProgressView().scaleEffect(0.7)
                Text("Connecting")
            } else {
                Image(systemName: "link")
                Text("Connect")
            }
        }
        .font(.system(size: 14, weight: .medium))
        .foregroundColor(fgColor)
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(bgColor)
        .cornerRadius(12)
        .onTapGesture {
            if !isConnected && !isConnecting {
                onConnect()
            }
        }
    }
}


struct DeviceInfoView: View {
    @ObservedObject var viewModel: BleViewModel

    var body: some View {
        NavigationView {
            ZStack {
                Color(hex: "F8FAFC")
                    .ignoresSafeArea()

                ScrollView {
                    VStack(spacing: 16) {
                        ConnectionStatusCard(connectionState: viewModel.connectionState)
                            .padding(.horizontal)
                            .padding(.top, 8)

                        if let deviceInfo = viewModel.deviceInfo {
                            DeviceInformationCard(deviceInfo: deviceInfo)
                                .padding(.horizontal)
                        }

                        Spacer(minLength: 100)
                    }
                    .padding(.vertical)
                }

                if viewModel.connectionState is ConnectionState.Connected {
                    VStack {
                        Spacer()
                        DisconnectButton {
                            viewModel.disconnect()
                        }
                        .padding(.horizontal)
                        .padding(.bottom, 24)
                    }
                }
            }
            .navigationTitle("Device Info")
            .navigationBarTitleDisplayMode(.large)
        }
    }
}

struct ConnectionStatusCard: View {
    let connectionState: ConnectionState

    var statusColor: Color {
        switch connectionState {
        case is ConnectionState.Connected: return Color(hex: "10B981")
        case is ConnectionState.Connecting: return Color(hex: "6366F1")
        case is ConnectionState.Error: return Color(hex: "EF4444")
        default: return Color(hex: "6B7280")
        }
    }

    var statusIcon: String {
        switch connectionState {
        case is ConnectionState.Connected: return "checkmark.circle.fill"
        case is ConnectionState.Connecting: return "clock.fill"
        case is ConnectionState.Error: return "exclamationmark.triangle.fill"
        default: return "xmark.circle.fill"
        }
    }

    var statusTitle: String {
        switch connectionState {
        case is ConnectionState.Connected: return "Connected"
        case is ConnectionState.Connecting: return "Connecting..."
        case is ConnectionState.Error: return "Connection Error"
        default: return "Disconnected"
        }
    }

    var statusMessage: String {
        switch connectionState {
        case let state as ConnectionState.Connected:
            return "Device: \(state.device.name ?? "Unknown")"
        case is ConnectionState.Connecting:
            return "Establishing connection..."
        case let state as ConnectionState.Error:
            return state.message
        default:
            return "No device connected"
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 12) {
                Image(systemName: "cable.connector")
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundColor(Color(hex: "6366F1"))
                Text("Connection Status")
                    .font(.system(size: 19, weight: .semibold))
                    .foregroundColor(Color(hex: "1E293B"))
                Spacer()
            }
            .padding(20)

            HStack(spacing: 16) {
                Image(systemName: statusIcon)
                    .font(.system(size: 28))
                    .foregroundColor(statusColor)
                    .frame(width: 40, height: 40)

                VStack(alignment: .leading, spacing: 4) {
                    Text(statusTitle)
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundColor(statusColor)
                    Text(statusMessage)
                        .font(.system(size: 15))
                        .foregroundColor(Color(hex: "64748B"))
                }

                Spacer()
            }
            .padding(16)
            .background(statusColor.opacity(0.1))
            .cornerRadius(16)
            .padding(.horizontal, 20)
            .padding(.bottom, 20)
        }
        .background(Color.white)
        .cornerRadius(20)
        .shadow(color: Color.black.opacity(0.05), radius: 8, x: 0, y: 2)
    }
}

struct DeviceInformationCard: View {
    let deviceInfo: DeviceInfo

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 12) {
                Image(systemName: "info.circle")
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundColor(Color(hex: "8B5CF6"))
                Text("Device Information")
                    .font(.system(size: 19, weight: .semibold))
                    .foregroundColor(Color(hex: "1E293B"))
                Spacer()
            }
            .padding(20)

            VStack(spacing: 16) {
                ModernInfoRow(
                    icon: "device.laptop",
                    label: "Device Name",
                    value: deviceInfo.device.name ?? "Unknown"
                )

                ModernInfoRow(
                    icon: "number",
                    label: "Device ID",
                    value: deviceInfo.device.id
                )

                if let battery = deviceInfo.batteryLevel {
                    Divider()
                        .padding(.vertical, 4)

                    BatteryLevelView(batteryLevel: battery)
                }

                if let heartRate = deviceInfo.heartRate {
                    Divider()
                        .padding(.vertical, 4)

                    HeartRateView(heartRate: heartRate)
                }
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 20)
        }
        .background(Color.white)
        .cornerRadius(20)
        .shadow(color: Color.black.opacity(0.05), radius: 8, x: 0, y: 2)
    }
}

struct ModernInfoRow: View {
    let icon: String
    let label: String
    let value: String

    var body: some View {
        HStack {
            HStack(spacing: 8) {
                Image(systemName: icon)
                    .font(.system(size: 16))
                    .foregroundColor(Color(hex: "64748B"))
                    .frame(width: 20)
                Text(label)
                    .font(.system(size: 15))
                    .foregroundColor(Color(hex: "64748B"))
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            Text(value)
                .font(.system(size: 15, weight: .medium))
                .foregroundColor(Color(hex: "1E293B"))
                .lineLimit(1)
                .frame(maxWidth: .infinity, alignment: .trailing)
        }
    }
}

struct BatteryLevelView: View {
    let batteryLevel: BatteryLevel

    var batteryColor: Color {
        if batteryLevel.percentage >= 60 {
            return Color(hex: "10B981")
        } else if batteryLevel.percentage >= 30 {
            return Color(hex: "F59E0B")
        } else {
            return Color(hex: "EF4444")
        }
    }

    var batteryStatus: String {
        if batteryLevel.percentage >= 60 {
            return "Good"
        } else if batteryLevel.percentage >= 30 {
            return "Medium"
        } else {
            return "Low"
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 8) {
                Image(systemName: "battery.100")
                    .font(.system(size: 16, weight: .medium))
                    .foregroundColor(Color(hex: "EC4899"))
                Text("Battery Level")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundColor(Color(hex: "1E293B"))
            }

            HStack {
                Text("\(batteryLevel.percentage)%")
                    .font(.system(size: 32, weight: .bold))
                    .foregroundColor(Color(hex: "6366F1"))

                Spacer()

                Text(batteryStatus)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundColor(batteryColor)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(batteryColor.opacity(0.15))
                    .cornerRadius(8)
            }

            GeometryReader { geometry in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 6)
                        .fill(Color(hex: "F1F5F9"))
                        .frame(height: 12)

                    RoundedRectangle(cornerRadius: 6)
                        .fill(batteryColor)
                        .frame(width: geometry.size.width * CGFloat(batteryLevel.percentage) / 100, height: 12)
                }
            }
            .frame(height: 12)
        }
    }
}

struct HeartRateView: View {
    let heartRate: HeartRate

    var heartRateColor: Color {
        if heartRate.beatsPerMinute < 60 {
            return Color(hex: "3B82F6")
        } else if heartRate.beatsPerMinute <= 100 {
            return Color(hex: "10B981")
        } else {
            return Color(hex: "EF4444")
        }
    }

    var heartRateStatus: String {
        if heartRate.beatsPerMinute < 60 {
            return "Low"
        } else if heartRate.beatsPerMinute <= 100 {
            return "Normal"
        } else {
            return "High"
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 8) {
                Image(systemName: "heart.fill")
                    .font(.system(size: 16, weight: .medium))
                    .foregroundColor(Color(hex: "EF4444"))
                Text("Heart Rate")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundColor(Color(hex: "1E293B"))
            }

            HStack(spacing: 20) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("\(heartRate.beatsPerMinute)")
                        .font(.system(size: 48, weight: .bold))
                        .foregroundColor(Color(hex: "EF4444"))
                    Text("BPM")
                        .font(.system(size: 15))
                        .foregroundColor(Color(hex: "64748B"))
                }

                Spacer()

                HeartBeatAnimationView()
            }
            .padding(20)
            .background(Color(hex: "EF4444").opacity(0.1))
            .cornerRadius(16)

            HStack {
                HStack(spacing: 4) {
                    Image(systemName: "info.circle")
                        .font(.system(size: 14))
                        .foregroundColor(Color(hex: "64748B"))
                    Text("Status")
                        .font(.system(size: 13))
                        .foregroundColor(Color(hex: "64748B"))
                }

                Spacer()

                Text(heartRateStatus)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundColor(heartRateColor)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(heartRateColor.opacity(0.15))
                    .cornerRadius(8)
            }

            if let contact = heartRate.sensorContact?.boolValue {
                HStack(spacing: 6) {
                    Image(systemName: contact ? "checkmark.circle" : "exclamationmark.triangle")
                        .font(.system(size: 12))
                        .foregroundColor(contact ? Color(hex: "10B981") : Color(hex: "F59E0B"))
                    Text(contact ? "Sensor Contact: Good" : "Sensor Contact: Poor")
                        .font(.system(size: 13))
                        .foregroundColor(Color(hex: "64748B"))
                }
            }
        }
    }
}

struct HeartBeatAnimationView: View {
    @State private var isAnimating = false

    var body: some View {
        Image(systemName: "heart.fill")
            .font(.system(size: 40))
            .foregroundColor(Color(hex: "EF4444"))
            .scaleEffect(isAnimating ? 1.3 : 1.0)
            .animation(
                Animation.easeInOut(duration: 0.4)
                    .repeatForever(autoreverses: true),
                value: isAnimating
            )
            .onAppear {
                isAnimating = true
            }
    }
}

struct DisconnectButton: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: "link.badge.minus")
                    .font(.system(size: 20, weight: .semibold))
                Text("Disconnect Device")
                    .font(.system(size: 17, weight: .semibold))
            }
            .frame(maxWidth: .infinity)
            .frame(height: 56)
            .background(
                LinearGradient(
                    colors: [Color(hex: "EF4444"), Color(hex: "DC2626")],
                    startPoint: .leading,
                    endPoint: .trailing
                )
            )
            .foregroundColor(.white)
            .cornerRadius(16)
            .shadow(color: Color(hex: "EF4444").opacity(0.3), radius: 8, x: 0, y: 4)
        }
    }
}

class BleViewModel: ObservableObject {
    private let bleManager: BleManager

    @Published var scannedDevices: [BleDevice] = []
    @Published var connectionState: ConnectionState = ConnectionState.Disconnected()
    @Published var deviceInfo: DeviceInfo? = nil
    @Published var isScanning: Bool = false

    init() {
        self.bleManager = PlatformKt.createBleManager()
        setupObservers()
    }

    private func setupObservers() {
        Timer.scheduledTimer(withTimeInterval: 0.1, repeats: true) { [weak self] _ in
            guard let self = self else { return }

            DispatchQueue.main.async {
                let devices = self.bleManager.scannedDevices.value as? [BleDevice] ?? []
                self.scannedDevices = devices

                if let state = self.bleManager.connectionState.value as? ConnectionState {
                    self.connectionState = state
                }

                self.deviceInfo = self.bleManager.deviceInfo.value as? DeviceInfo
            }
        }
    }

    func startScan() {
        isScanning = true
        bleManager.startScan()
    }

    func stopScan() {
        isScanning = false
        bleManager.stopScan()
    }

    func connect(to device: BleDevice) {
        Task {
            do {
                try await bleManager.connect(device: device)
            } catch {
                print("Connection failed: \(error.localizedDescription)")
            }
        }
    }

    func disconnect() {
        bleManager.disconnect()
    }
}

extension Color {
    init(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)
        let a, r, g, b: UInt64
        switch hex.count {
        case 3:
            (a, r, g, b) = (255, (int >> 8) * 17, (int >> 4 & 0xF) * 17, (int & 0xF) * 17)
        case 6:
            (a, r, g, b) = (255, int >> 16, int >> 8 & 0xFF, int & 0xFF)
        case 8:
            (a, r, g, b) = (int >> 24, int >> 16 & 0xFF, int >> 8 & 0xFF, int & 0xFF)
        default:
            (a, r, g, b) = (1, 1, 1, 0)
        }

        self.init(
            .sRGB,
            red: Double(r) / 255,
            green: Double(g) / 255,
            blue:  Double(b) / 255,
            opacity: Double(a) / 255
        )
    }
}

#Preview {
    ContentView()
}