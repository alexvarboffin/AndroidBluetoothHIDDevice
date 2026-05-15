# MasterPrompt: Android Bluetooth HID Device

## Project Goal
Create an Android application that allows a smartphone to act as a Bluetooth HID (Human Interface Device) peripheral, emulating a keyboard and mouse for other host devices (PC, Tablet, etc.).

## Technical Stack
- **Language:** Kotlin
- **UI:** Jetpack Compose (Material 3)
- **Min API:** 28 (Android 9.0 Pie) - Required for `BluetoothHidDevice`.
- **Target API:** 34+

## Core Implementation Details

### 1. Bluetooth HID Profile
- Use `BluetoothProfile.HID_DEVICE` (API 28+).
- **Service Registration:** Requires `BluetoothHidDeviceAppSdpSettings`.
- **Identity:** Ensure `name` and `description` in SDP settings clearly indicate a keyboard. The `subclass` must be `SUBCLASS1_KEYBOARD`.
- **Report Descriptors:** Standard HID keyboard/mouse descriptors must be provided during registration.

### 2. Connection Logic
- **Discoverability:** The device MUST be discoverable (`ACTION_REQUEST_DISCOVERABLE`) for the host to see it during pairing.
- **Manual Connect:** Sometimes the host pairs but doesn't auto-connect the HID profile. Use `BluetoothHidDevice.connect(device)` to force the profile connection after bonding.

### 2. Permissions (Android 12+ / API 31+)
- Mandatory: `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE`.
- Legacy (API <= 30): `BLUETOOTH`, `BLUETOOTH_ADMIN`.
- Runtime requests are mandatory before initializing the Bluetooth adapter.

### 3. Execution Logic (AI Agent Workflow)
1. **Verify Environment:** Check if Bluetooth is supported and enabled. Use `ACTION_REQUEST_ENABLE` if off.
2. **Permissions First:** Do not initialize `HidDeviceManager` until permissions are granted.
3. **HID Registration:** Get proxy for `HID_DEVICE` profile and call `registerApp`.
4. **State Management:** Listen to `onConnectionStateChanged`. Only send reports when state is `STATE_CONNECTED`.
5. **Report Transmission:** `sendReport` requires a byte array conforming to the registered HID Descriptor.
6. **Intent Integration:** The app handles `ACTION_SEND` (text/plain). Shared text is processed through a character-to-keycode mapper and sent sequentially to the host.

### Technical Research (Off-topic: Layout Independence)
- **HID vs Data Channels:** Detailed comparison of Scan Codes vs Unicode sockets can be found in `Documentation/ARCHITECTURE/bluetooth-transmission-logic.md`.
- **Key Takeaway:** HID is layout-dependent by design (sends physical scan codes). For layout-independent transfer, a custom receiver agent on the host would be required (using SPP/GATT mode).

## Character-to-HID Mapping
- Maps standard ASCII (A-Z, 0-9, space, enter, punctuation) to HID Usage IDs.
- **Modifiers:** Uses the first byte of the HID report (0x02 for Left Shift) to handle uppercase and symbols.
- **Timing:** Sequential typing requires small delays (e.g., 20ms) between reports to prevent host-side buffer overflow.

## Lessons Learned & Optimizations
- **API Limitation:** Many emulators do not support the HID profile; testing must be done on physical devices.
- **Async Initialization:** `getProfileProxy` is asynchronous. Use a `ServiceListener` and only interact with the proxy after `onServiceConnected`.
- **Threading:** Bluetooth callbacks occur on background threads. UI updates MUST be dispatched to the Main Thread (e.g., using `Handler(Looper.getMainLooper())`).
- **State Persistence:** UI state must be synchronized with the `BluetoothHidDevice` proxy state. Use `getConnectedDevices()` to refresh the UI when the Activity is restored or when a new listener is attached.
- **User Feedback:** The UI must clearly distinguish between "App Registered" (service ready) and "Device Connected" (host is linked).

## Versioning & Persistence
- This project uses Git for version control.
- All custom skills and tools developed should be documented here and committed.
