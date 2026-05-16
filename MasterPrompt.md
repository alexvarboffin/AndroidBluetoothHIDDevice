# MasterPrompt: Android Bluetooth HID Device

## Project Goal
Create an Android application that allows a smartphone to act as a Bluetooth HID (Human Interface Device) peripheral, emulating a keyboard and mouse for other host devices (PC, Tablet, etc.).

## Technical Stack
- **Language:** Kotlin
- **UI:** Jetpack Compose (Material 3)
- **Architecture:** MVVM (ViewModel + StateFlow)
- **Min API:** 28 (Android 9.0 Pie) - Required for `BluetoothHidDevice`.
- **Target API:** 34+

## Development History: Problem & Solution Log

### 0. AI Handoff & Knowledge Preservation Protocol
- **Problem:** User corrections, failed attempts, and project-specific decisions can be lost between AI coding sessions.
- **Solution:** Keep this `MasterPrompt.md` as the compact source of truth for future AI agents.
- **Rule:** If the user's clarification changes the task, contradicts this MP, or reveals a better solution after a failed attempt, update MP before finishing the work.
- **Format:** Record only durable lessons as `Problem -> Solution -> Tools/Files`, optimized for reuse by another AI programmer.
- **Do not bloat MP:** Put long implementation plans in `Documentation/Roadmap.md`, technical trade-off analysis in `Documentation/ARCHITECTURE/`, UI conventions in `Documentation/guidelines/`, and user-facing release notes in `Whatsnew.md` or `Documentation/Whatsnew.md`.
- **Optional acceleration:** Create Cursor skills/tools only for repeatable workflows where they prevent likely future mistakes.

### 1. Initial Setup & HID Support
- **Problem:** Need to emulate a hardware keyboard without custom drivers on the host.
- **Solution:** Used `BluetoothProfile.HID_DEVICE` (API 28+). 
- **Tools:** Implemented `HidDeviceManager` to handle `BluetoothProfile.ServiceListener` and `BluetoothHidDevice.Callback`.

### 2. Permissions (Modern Android)
- **Problem:** Bluetooth permissions changed significantly in Android 12 (API 31).
- **Solution:** Implemented dual-logic permission checks. 
    - API >= 31: `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE`.
    - API < 31: `BLUETOOTH`, `BLUETOOTH_ADMIN`.
- **Logic:** Used `ActivityResultContracts.RequestMultiplePermissions` in `MainActivity`.

### 3. Bluetooth State Management
- **Problem:** App fails or stays in "Initializing" if Bluetooth is disabled on the phone.
- **Solution:** Added `BluetoothAdapter.isEnabled` check. If off, triggers `ACTION_REQUEST_ENABLE` system dialog. UI shows "Bluetooth is OFF" with an enable button.

### 4. Host OS Identification (SDP Settings)
- **Problem:** PC sees the device but doesn't recognize it as a keyboard.
- **Solution:** Refined `BluetoothHidDeviceAppSdpSettings`. 
    - Set name to "HID Keyboard".
    - Explicitly used `SUBCLASS1_KEYBOARD`.
    - Provided a standard 8-byte Keyboard HID Report Descriptor.

### 5. UI Threading & State Sync
- **Problem:** Bluetooth callbacks (onConnectionStateChanged) happen on background threads, causing Compose UI to not update or crash.
- **Solution:** Implemented `updateStatus()` helper using `Handler(Looper.getMainLooper())` to force all UI-bound updates onto the Main Thread.

### 6. Background Persistence & App Resume
- **Problem:** Swiping away/backgrounding the app breaks the UI state or drops the HID proxy.
- **Solution:** 
    - Refactored to **ViewModel** architecture (`HidViewModel`) to survive Activity recreation.
    - Implemented `resume()` in ViewModel (called from `MainActivity.onResume`) that triggers `getConnectedDevices()` to re-sync the app's internal "Connected" state with the actual system Bluetooth state.

### 7. Connection "Ghosting" & Recovery
- **Problem:** Device shows as paired on PC but "Disconnected" in app, and the `connect` button seems unresponsive.
- **Solution:**
    - Added an `isRegistered` flag to track HID App registration independently of the service proxy.
    - Implemented `forceReset()`: unregisters the app, clears the proxy, and re-initializes the entire HID flow.
    - Added a **"Reset HID Service"** (Emergency Button) in the UI for hard-recovery.

### 8. Text Input & Share Intent
- **Problem:** Need to send more than just one key and integrate with other apps.
- **Solution:** 
    - Added `intent-filter` for `ACTION_SEND` (text/plain).
    - Created `charToKeyCode` mapper to translate ASCII characters into HID Scan Codes (including Shift modifiers).
    - Implemented `sendString()` which types text sequentially with a 20ms delay.

### 9. Paired Device Row Connect/Disconnect State
- **Problem:** The paired-device row had only a `Connect` action and a global `isConnected` flag, so UI could not know which bonded device was connected or offer a safe disconnect action for that row.
- **Solution:** Track the connected device address in `HidUiState`, pass row-level `isConnected` into `BondedDeviceRow`, and switch the button between `Connect` and `Disconnect`.
- **Tools/Files:** `HidDeviceManager.disconnect()`, `HidViewModel.connectedDeviceAddress`, `BondedDeviceRow`, and `material-icons-extended` for Bluetooth action icons.

### 10. Connection Drop on Background (Battery Optimization)
- **Problem:** OS suspends Bluetooth stack/proxy when Activity is hidden, breaking the HID link.
- **Solution (Planned):** Implement a `Foreground Service` to hold the HID proxy.
- **User Control:** Add a toggle in the UI/Settings to enable/disable "Persistent Mode". If enabled, the app runs a Foreground Service with a notification to maintain high system priority.

## Core Implementation Details
...
### 4. Persistence Modes
- **Standard Mode:** HID proxy is tied to the ViewModel/Activity lifecycle. Connection may drop when backgrounded.
- **Persistent Mode:** HID proxy lives in a `Foreground Service`. Connection remains active even if the UI is closed. Requires `FOREGROUND_SERVICE` and `POST_NOTIFICATIONS` permissions.

### HID Profile Details
- **Identity:** SDP name "HID Keyboard", subclass `0x40` (Keyboard).
- **Report Transmission:** `sendReport` (ID 1, 8-byte array). 
    - `report[0]`: Modifiers (Shift=0x02).
    - `report[2]`: Usage ID (A=0x04, etc.).

### Lifecycle Guidelines
- **UI Sync:** Always refresh state in `onResume` via `BluetoothHidDevice.getConnectedDevices()`.
- **Reset Logic:** When in doubt, `unregisterApp` and re-init the proxy.

## Versioning & Persistence
- Project managed via Git. 
- Canonical roadmap lives in `Documentation/Roadmap.md`; do not keep a duplicate roadmap in the project root.
- Technical Research (HID vs SPP) documented in `Documentation/ARCHITECTURE/bluetooth-transmission-logic.md`.
