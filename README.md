# Android Bluetooth HID Device

Android app that turns a phone into a Bluetooth HID keyboard for a paired host device. The current stable mode is keyboard-only because it gives faster and more reliable preset typing than the experimental composite keyboard/mouse descriptor.

## Features

- Bluetooth HID Device registration through Android's `BluetoothHidDevice` API.
- Pair/connect/disconnect UI for bonded Bluetooth hosts.
- Test key sender and Windows `Win+R -> calc -> Enter` shortcut.
- RoomDB-backed command presets with categories.
- Preset import/export as JSON.
- Preset item actions: add, edit, copy, delete.
- Built-in seed presets for common Windows commands such as Calculator, Notepad, Task Manager, Firefox profile manager, Android Studio, and VS Code.
- Sensitive preset support with confirmation before execution.
- Credential preset type: type login, press `Tab`, type password, press `Enter`.
- Optional foreground service mode for keeping the HID session alive in the background.

## Requirements

- Android 9.0+ (`minSdk 28`), because `BluetoothHidDevice` was added in API 28.
- A phone/ROM that exposes the Bluetooth HID Device profile. Some vendors disable or restrict this profile.
- A host device that accepts Bluetooth HID keyboards, for example a Windows PC.
- Bluetooth permissions granted at runtime.

## Current HID Mode

The active HID descriptor is keyboard-only:

- SDP name: `HID Keyboard`
- subclass: `SUBCLASS1_KEYBOARD`
- keyboard report: `Report ID 1`, 8-byte keyboard report

A composite keyboard/mouse descriptor was tested and rolled back because preset typing was faster in keyboard-only mode. The composite experiment is kept as comments in `HidDeviceManager.kt` for future reference, but it is not enabled by default.

## Build

```powershell
.\gradlew.bat :app:assembleDebug
```

For a Kotlin compile check:

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

The project currently uses:

- Kotlin
- Jetpack Compose + Material 3
- AndroidX Lifecycle ViewModel
- RoomDB + KSP
- Gradle Version Catalog

## Usage

1. Install and open the app on an Android device with Bluetooth enabled.
2. Grant Bluetooth permissions.
3. Make the phone discoverable from the app.
4. Pair the phone from the host device Bluetooth settings.
5. Connect to the paired host in the app.
6. Use `Send Test 'A' Key`, `Win+R calc`, or the `Presets` tab.

If the host keeps stale HID behavior after descriptor experiments, remove the Bluetooth device on the host and pair it again.

## Presets

Presets are stored locally in RoomDB and can be exported/imported as JSON. Categories can be added and deleted, but built-in categories are protected. Built-in seed presets can be run and copied; copied presets become editable user presets.

Sensitive and credential presets are stored as plaintext in the local RoomDB in the current implementation. They are marked sensitive, require confirmation before execution, and the export flow warns before including sensitive values.

## Project Documentation

- `MasterPrompt.md` contains compact project context and durable implementation decisions.
- `Documentation/Roadmap.md` is the canonical roadmap and verification checklist.
- `app/src/main/java/com/walhalla/bluetoothhiddevice/HidDeviceManager.kt` contains the Bluetooth HID registration and report-sending logic.

## Known Limitations

- Stable mode is keyboard-only. Mouse/touchpad/gamepad support would require a carefully tested composite HID descriptor and report throttling.
- Windows command presets depend on host OS behavior, installed applications, and PATH/App Paths registration.
- HID descriptor changes may require resetting the HID service, reconnecting, or re-pairing the host.
- `BluetoothAdapter.getDefaultAdapter()` currently produces a deprecation warning.

## License

Add a license before publishing if you want others to reuse or redistribute the project.
