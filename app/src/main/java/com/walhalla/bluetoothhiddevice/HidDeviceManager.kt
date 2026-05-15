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

import android.os.Handler
import android.os.Looper

class HidDeviceManager(private val context: Context) {

    private val TAG = "HidDeviceManager"
    private val mainHandler = Handler(Looper.getMainLooper())
    private var bluetoothHidDevice: BluetoothHidDevice? = null
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var connectedDevice: BluetoothDevice? = null
    private var isRegistered = false

    private val profileServiceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                Log.d(TAG, "HID Service Connected")
                bluetoothHidDevice = proxy as BluetoothHidDevice
                registerApp()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                Log.d(TAG, "HID Service Disconnected")
                bluetoothHidDevice = null
                isRegistered = false
                updateStatus("HID Service Lost")
            }
        }
    }

    private var onStatusChanged: ((String) -> Unit)? = null

    fun setStatusListener(listener: (String) -> Unit) {
        onStatusChanged = listener
        // Force refresh when listener attaches (e.g., on app resume)
        refreshConnectionState()
    }

    @SuppressLint("MissingPermission")
    fun refreshConnectionState() {
        if (bluetoothHidDevice == null) {
            updateStatus("Initializing HID Service...")
            adapter?.getProfileProxy(context, profileServiceListener, BluetoothProfile.HID_DEVICE)
            return
        }

        if (!isRegistered) {
            registerApp()
            return
        }

        val connectedDevices = bluetoothHidDevice?.getConnectedDevices() ?: emptyList()
        if (connectedDevices.isNotEmpty()) {
            val device = connectedDevices.first()
            connectedDevice = device
            updateStatus("Connected to ${device.name ?: device.address}")
        } else {
            connectedDevice = null
            updateStatus("App Registered (Ready)")
        }
    }

    @SuppressLint("MissingPermission")
    fun forceReset() {
        Log.d(TAG, "Force resetting HID Service")
        if (isRegistered) {
            try {
                bluetoothHidDevice?.unregisterApp()
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering", e)
            }
        }
        isRegistered = false
        connectedDevice = null
        bluetoothHidDevice = null
        adapter?.getProfileProxy(context, profileServiceListener, BluetoothProfile.HID_DEVICE)
    }

    private fun updateStatus(status: String) {
        mainHandler.post {
            onStatusChanged?.invoke(status)
        }
    }

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            isRegistered = registered
            val status = if (registered) "App Registered (Ready)" else "App Unregistered"
            Log.d(TAG, "onAppStatusChanged: registered=$registered")
            updateStatus(status)
            if (registered) refreshConnectionState()
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
            updateStatus(stateStr)
            
            if (state == BluetoothProfile.STATE_CONNECTED) {
                connectedDevice = device
            } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                connectedDevice = null
            }
        }

        override fun onSetReport(device: BluetoothDevice?, type: Byte, id: Byte, data: ByteArray?) {
            Log.d(TAG, "onSetReport from ${device?.name}: type=$type id=$id data=${data?.contentToString()}")
        }

        override fun onGetReport(device: BluetoothDevice?, type: Byte, id: Byte, bufferSize: Int) {
            Log.d(TAG, "onGetReport from ${device?.name}: type=$type id=$id")
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
        if (bluetoothHidDevice == null || !isRegistered) {
            updateStatus("Error: Service not ready")
            refreshConnectionState()
            return
        }
        Log.d(TAG, "Manually connecting to ${device.name}")
        bluetoothHidDevice?.connect(device)
    }

    @SuppressLint("MissingPermission")
    fun sendString(text: String) {
        val device = connectedDevice ?: return
        Log.d(TAG, "Typing string: $text")
        
        Thread {
            for (char in text) {
                val (keyCode, shift) = charToKeyCode(char)
                if (keyCode != 0.toByte()) {
                    sendKey(device, keyCode, shift)
                    Thread.sleep(20) // Small delay between keys
                }
            }
        }.start()
    }

    @SuppressLint("MissingPermission")
    private fun sendKey(device: BluetoothDevice, keyCode: Byte, shift: Boolean) {
        val report = ByteArray(8)
        if (shift) report[0] = 0x02 // Left Shift modifier
        report[2] = keyCode
        
        bluetoothHidDevice?.sendReport(device, 1, report) // Press
        
        report[0] = 0
        report[2] = 0
        bluetoothHidDevice?.sendReport(device, 1, report) // Release
    }

    private fun charToKeyCode(char: Char): Pair<Byte, Boolean> {
        return when (char) {
            in 'a'..'z' -> (0x04 + (char - 'a')).toByte() to false
            in 'A'..'Z' -> (0x04 + (char - 'A')).toByte() to true
            in '1'..'9' -> (0x1E + (char - '1')).toByte() to false
            '0' -> 0x27.toByte() to false
            ' ' -> 0x2C.toByte() to false
            '\n' -> 0x28.toByte() to false
            '.' -> 0x37.toByte() to false
            ',' -> 0x36.toByte() to false
            '!' -> 0x1E.toByte() to true
            '?' -> 0x38.toByte() to true
            else -> 0.toByte() to false
        }
    }

    fun unregister() {
        if (isRegistered) {
            try {
                bluetoothHidDevice?.unregisterApp()
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering", e)
            }
        }
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
