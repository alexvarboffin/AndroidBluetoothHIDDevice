НЕ УДАЛЯТЬ
is it possible to send key via bluetooth from android to android?


android phone like blooetooth keyboard
How to use your Android phone as a keyboard or mouse
https://play.google.com/store/apps/details?id=io.appground.blek&pli=1



Bluetooth HID Device


AndroidBluetoothHIDDevice
END_НЕ УДАЛЯТЬ

## Implementation Roadmap

### Phase 1: Setup and Permissions
- [ ] Configure `AndroidManifest.xml` with necessary Bluetooth permissions:
    - `BLUETOOTH`, `BLUETOOTH_ADMIN` (for legacy support)
    - `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE`, `BLUETOOTH_SCAN` (for Android 12+)
- [ ] Implement runtime permission requests in `MainActivity`.
- [ ] Check if the device supports Bluetooth HID Device profile (`BluetoothProfile.HID_DEVICE`).

### Phase 2: Bluetooth HID Service Core
- [ ] Create a `BluetoothHidService` to manage the HID connection.
- [ ] Get the `BluetoothAdapter` and `BluetoothHidDevice` proxy object.
- [ ] Define HID Report Descriptors (Keyboard, Mouse, Consumer Control).
- [ ] Register the app as an HID device using `registerApp`.

### Phase 3: Connection and State Management
- [ ] Handle `BluetoothHidDevice.Callback` events:
    - `onAppStatusChanged`
    - `onConnectionStateChanged`
    - `onSetReport`, `onGetReport`
- [ ] Implement logic to pair and connect to host devices.

### Phase 4: HID Report Transmission
- [ ] Implement helper methods to send Keyboard reports (Key codes, modifiers).
- [ ] Implement helper methods to send Mouse reports (X, Y movement, button clicks).
- [ ] Implement helper methods to send Consumer Control reports (Media keys).

### Phase 5: User Interface (Jetpack Compose)
- [ ] Create a Dashboard showing Bluetooth status and connection state.
- [ ] Implement a Virtual Keyboard UI.
- [ ] Implement a Virtual Touchpad/Mouse UI.
- [ ] Add a list of paired devices to initiate connections.

### Phase 6: Testing and Validation
- [ ] Test with different host OS (Windows, macOS, Linux, Android).
- [ ] Verify low-latency input.
- [ ] Ensure proper cleanup and battery efficiency.
