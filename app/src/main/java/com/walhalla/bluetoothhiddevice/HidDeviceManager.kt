package com.walhalla.bluetoothhiddevice

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.concurrent.Executors

class HidDeviceManager(private val context: Context) {

    private val TAG = "HidDeviceManager"
    private var bluetoothHidDevice: BluetoothHidDevice? = null
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var connectedDevice: BluetoothDevice? = null

    private val profileServiceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                bluetoothHidDevice = proxy as BluetoothHidDevice
                registerApp()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                bluetoothHidDevice = null
            }
        }
    }

    private var onStatusChanged: ((String) -> Unit)? = null

    fun setStatusListener(listener: (String) -> Unit) {
        onStatusChanged = listener
    }

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            val status = if (registered) "App Registered" else "App Unregistered"
            Log.d(TAG, "onAppStatusChanged: $status")
            onStatusChanged?.invoke(status)
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            val stateStr = when (state) {
                BluetoothProfile.STATE_CONNECTED -> "Connected to ${device?.name ?: "Unknown"}"
                BluetoothProfile.STATE_CONNECTING -> "Connecting..."
                BluetoothProfile.STATE_DISCONNECTING -> "Disconnecting..."
                BluetoothProfile.STATE_DISCONNECTED -> "Disconnected"
                else -> "State: $state"
            }
            Log.d(TAG, "onConnectionStateChanged: $stateStr")
            onStatusChanged?.invoke(stateStr)
            
            if (state == BluetoothProfile.STATE_CONNECTED) {
                connectedDevice = device
            } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                connectedDevice = null
            }
        }
    }

    init {
        adapter?.getProfileProxy(context, profileServiceListener, BluetoothProfile.HID_DEVICE)
    }

    @SuppressLint("MissingPermission")
    fun requestDiscoverable(activity: android.app.Activity) {
        val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
        }
        activity.startActivity(discoverableIntent)
    }

    @SuppressLint("MissingPermission")
    private fun registerApp() {
        val sdp = BluetoothHidDeviceAppSdpSettings(
            "HID Keyboard",
            "Android HID Keyboard emulated by Walhalla",
            "Walhalla",
            BluetoothHidDevice.SUBCLASS1_KEYBOARD,
            HID_REPORT_DESCRIPTOR
        )
        bluetoothHidDevice?.registerApp(sdp, null, null, Executors.newSingleThreadExecutor(), callback)
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        bluetoothHidDevice?.connect(device)
    }

    @SuppressLint("MissingPermission")
    fun sendTestString(text: String) {
        val device = connectedDevice ?: return
        // This is a simplified implementation. 
        // Real HID keyboard reports require converting characters to usage IDs.
        // For now, we'll just log and send a simple 'Enter' key or similar if possible,
        // but typically we need a proper report generator.
        Log.d(TAG, "Sending text: $text to $device")
        
        // Example: Sending a "Key A" press and release
        val report = ByteArray(8)
        report[2] = 0x04 // Key A
        bluetoothHidDevice?.sendReport(device, 1, report) // Press
        
        report[2] = 0x00
        bluetoothHidDevice?.sendReport(device, 1, report) // Release
    }

    fun unregister() {
        // Implementation for unregistering
    }

    companion object {
        // Minimal Keyboard Report Descriptor
        private val HID_REPORT_DESCRIPTOR = byteArrayOf(
            0x05.toByte(), 0x01.toByte(), // Usage Page (Generic Desktop)
            0x09.toByte(), 0x06.toByte(), // Usage (Keyboard)
            0xA1.toByte(), 0x01.toByte(), // Collection (Application)
            0x05.toByte(), 0x07.toByte(), // Usage Page (Key Codes)
            0x19.toByte(), 0xE0.toByte(), // Usage Minimum (224)
            0x29.toByte(), 0xE7.toByte(), // Usage Maximum (231)
            0x15.toByte(), 0x00.toByte(), // Logical Minimum (0)
            0x25.toByte(), 0x01.toByte(), // Logical Maximum (1)
            0x75.toByte(), 0x01.toByte(), // Report Size (1)
            0x95.toByte(), 0x08.toByte(), // Report Count (8)
            0x81.toByte(), 0x02.toByte(), // Input (Data, Variable, Absolute) ; Modifier byte
            0x95.toByte(), 0x01.toByte(), // Report Count (1)
            0x75.toByte(), 0x08.toByte(), // Report Size (8)
            0x81.toByte(), 0x01.toByte(), // Input (Constant) ; Reserved byte
            0x95.toByte(), 0x05.toByte(), // Report Count (5)
            0x75.toByte(), 0x01.toByte(), // Report Size (1)
            0x05.toByte(), 0x08.toByte(), // Usage Page (LEDs)
            0x19.toByte(), 0x01.toByte(), // Usage Minimum (1)
            0x29.toByte(), 0x05.toByte(), // Usage Maximum (5)
            0x91.toByte(), 0x02.toByte(), // Output (Data, Variable, Absolute) ; LED report
            0x95.toByte(), 0x01.toByte(), // Report Count (1)
            0x75.toByte(), 0x03.toByte(), // Report Size (3)
            0x91.toByte(), 0x01.toByte(), // Output (Constant) ; LED report padding
            0x95.toByte(), 0x06.toByte(), // Report Count (6)
            0x75.toByte(), 0x08.toByte(), // Report Size (8)
            0x15.toByte(), 0x00.toByte(), // Logical Minimum (0)
            0x25.toByte(), 0x65.toByte(), // Logical Maximum (101)
            0x05.toByte(), 0x07.toByte(), // Usage Page (Key Codes)
            0x19.toByte(), 0x00.toByte(), // Usage Minimum (0)
            0x29.toByte(), 0x65.toByte(), // Usage Maximum (101)
            0x81.toByte(), 0x00.toByte(), // Input (Data, Array) ; Key codes
            0xC0.toByte()                // End Collection
        )
    }
}
