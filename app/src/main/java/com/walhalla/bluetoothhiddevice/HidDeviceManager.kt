package com.walhalla.bluetoothhiddevice

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.util.concurrent.Executors

import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat

class HidDeviceManager(private val context: Context) {

    private val TAG = "HidDeviceManager"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val typingExecutor = Executors.newSingleThreadExecutor()
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
                connectedDevice = null
                updateStatus("HID Service Lost")
            }
        }
    }

    private var onStatusChanged: ((String, BluetoothDevice?) -> Unit)? = null
    private var onConnectionChanged: ((String, Boolean) -> Unit)? = null

    fun setStatusListener(listener: (String, BluetoothDevice?) -> Unit) {
        onStatusChanged = listener
        // Force refresh when listener attaches (e.g., on app resume)
        refreshConnectionState()
    }

    fun setConnectionListener(listener: (String, Boolean) -> Unit) {
        onConnectionChanged = listener
        refreshConnectionState()
    }

    fun isConnected(): Boolean = connectedDevice != null

    @SuppressLint("MissingPermission")
    fun refreshConnectionState() {
        if (!hasBluetoothConnectPermission()) {
            updateStatus("Bluetooth permission required")
            return
        }

        if (bluetoothHidDevice == null) {
            updateStatus("Initializing HID Service...")
            adapter?.getProfileProxy(context, profileServiceListener, BluetoothProfile.HID_DEVICE)
            return
        }

        if (!isRegistered) {
            registerApp()
            return
        }

        val connectedDevices = try {
            bluetoothHidDevice?.getConnectedDevices() ?: emptyList()
        } catch (e: SecurityException) {
            Log.w(TAG, "BLUETOOTH_CONNECT missing", e)
            updateStatus("Bluetooth permission required")
            return
        }
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
            onStatusChanged?.invoke(status, connectedDevice)
            onConnectionChanged?.invoke(status, connectedDevice != null)
        }
    }

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            isRegistered = registered
            if (!registered) {
                connectedDevice = null
                clearHeldState()
            }
            val status = if (registered) "App Registered (Ready)" else "App Unregistered"
            Log.d(TAG, "onAppStatusChanged: registered=$registered")
            updateStatus(status)
            if (registered) refreshConnectionState()
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            if (state == BluetoothProfile.STATE_CONNECTED) {
                connectedDevice = device
                releaseAll() // start every connection from a clean (all keys up) state
            } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                connectedDevice = null
                clearHeldState()
            }

            val stateStr = when (state) {
                BluetoothProfile.STATE_CONNECTED -> "Connected to ${device?.name ?: "Unknown"}"
                BluetoothProfile.STATE_CONNECTING -> "Connecting..."
                BluetoothProfile.STATE_DISCONNECTING -> "Disconnecting..."
                BluetoothProfile.STATE_DISCONNECTED -> "Disconnected"
                else -> "State: $state"
            }
            Log.d(TAG, "onConnectionStateChanged: $stateStr")
            updateStatus(stateStr)
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onSetReport(device: BluetoothDevice?, type: Byte, id: Byte, data: ByteArray?) {
            Log.d(TAG, "onSetReport from ${device?.name}: type=$type id=$id data=${data?.contentToString()}")
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onGetReport(device: BluetoothDevice?, type: Byte, id: Byte, bufferSize: Int) {
            Log.d(TAG, "onGetReport from ${device?.name}: type=$type id=$id")
        }
    }

    init {
        if (hasBluetoothConnectPermission()) {
            adapter?.getProfileProxy(context, profileServiceListener, BluetoothProfile.HID_DEVICE)
        }
    }

    @SuppressLint("MissingPermission")
    fun requestDiscoverable(activity: android.app.Activity) {
        if (!hasBluetoothConnectPermission()) {
            updateStatus("Bluetooth permission required")
            return
        }
        val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
        }
        try {
            activity.startActivity(discoverableIntent)
        } catch (e: SecurityException) {
            Log.w(TAG, "BLUETOOTH_CONNECT missing", e)
            updateStatus("Bluetooth permission required")
        }
    }

    @SuppressLint("MissingPermission")
    private fun registerApp() {
        if (!hasBluetoothConnectPermission()) {
            updateStatus("Bluetooth permission required")
            return
        }
        val sdp = BluetoothHidDeviceAppSdpSettings(
            "HID Keyboard",
            "Android HID Keyboard emulated by Walhalla",
            "Walhalla",
            BluetoothHidDevice.SUBCLASS1_KEYBOARD,
            HID_REPORT_DESCRIPTOR
        )
        // Composite SDP experiment, kept commented for quick restore if needed:
        // val sdp = BluetoothHidDeviceAppSdpSettings(
        //     "Android HID Controller",
        //     "Android composite HID keyboard/mouse emulated by Walhalla",
        //     "Walhalla",
        //     BluetoothHidDevice.SUBCLASS1_COMBO,
        //     HID_REPORT_DESCRIPTOR
        // )
        try {
            bluetoothHidDevice?.registerApp(sdp, null, null, Executors.newSingleThreadExecutor(), callback)
        } catch (e: SecurityException) {
            Log.w(TAG, "BLUETOOTH_CONNECT missing", e)
            updateStatus("Bluetooth permission required")
        }
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
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
    fun disconnect(device: BluetoothDevice) {
        if (bluetoothHidDevice == null || connectedDevice?.address != device.address) {
            updateStatus("Error: Device is not connected")
            refreshConnectionState()
            return
        }
        Log.d(TAG, "Disconnecting from ${device.name}")
        bluetoothHidDevice?.disconnect(device)
    }

    @SuppressLint("MissingPermission")
    fun disconnectCurrent(): Boolean {
        val device = connectedDevice ?: return false
        disconnect(device)
        return true
    }

    @SuppressLint("MissingPermission")
    fun sendString(text: String) {
        val device = connectedDevice ?: return
        Log.d(TAG, "Typing string: $text")
        typingExecutor.execute {
            sendTextReport(device, text)
        }
    }

    @SuppressLint("MissingPermission")
    fun sendBackspaces(count: Int) {
        if (count <= 0) return
        val device = connectedDevice ?: return
        typingExecutor.execute {
            repeat(count) {
                sendKey(device, 0x2A.toByte(), 0.toByte())
                Thread.sleep(20)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun sendOpenCalculatorShortcut() {
        sendWindowsRunCommand("calc")
    }

    @SuppressLint("MissingPermission")
    fun sendWindowsRunCommand(command: String) {
        val device = connectedDevice ?: return
        Log.d(TAG, "Running Windows command via Win+R: $command")

        Thread {
            sendWindowsRunCommandReport(device, command)
        }.start()
    }

    @SuppressLint("MissingPermission")
    fun sendTextBlocking(text: String): Boolean {
        val device = connectedDevice ?: return false
        sendTextReport(device, text)
        return true
    }

    // ---- Held keys (real key down / key up), keyboard-only descriptor, up to 6 regular keys ----
    // sendKey() stays a tap (press + release) for presets and text. pressKey()/releaseKey() keep
    // the key in the report until released, so the host does its own auto-repeat / Mouse Keys acceleration.
    private val heldLock = Any()
    private val heldKeys = LinkedHashSet<Byte>()
    private var heldModifier: Byte = 0
    private val holdWatchdog = Runnable { releaseAll() }

    /** Re-arms the safety timer. Call periodically while a finger is still down. */
    fun holdKeepAlive() {
        if (synchronized(heldLock) { heldKeys.isEmpty() && heldModifier == 0.toByte() }) return
        mainHandler.removeCallbacks(holdWatchdog)
        mainHandler.postDelayed(holdWatchdog, HOLD_TIMEOUT_MS)
    }

    /** Key goes down and stays down until releaseKey/releaseAll or the safety timeout. */
    @SuppressLint("MissingPermission")
    fun pressKey(keyName: String, modifierName: String? = null) {
        val device = connectedDevice ?: return
        val keyCode = keyNameToUsageId(keyName) ?: return
        val modifier = if (modifierName.isNullOrBlank()) 0.toByte() else (modifierNameToByte(modifierName) ?: return)
        typingExecutor.execute {
            synchronized(heldLock) {
                if (keyCode !in heldKeys && heldKeys.size >= MAX_HELD_KEYS) return@execute
                heldKeys.add(keyCode)
                heldModifier = (heldModifier.toInt() or modifier.toInt()).toByte()
                sendHeldReport(device)
            }
            mainHandler.post { holdKeepAlive() }
        }
    }

    /** Releases one held key; the other held keys stay down. Empty set means an all-zero report. */
    @SuppressLint("MissingPermission")
    fun releaseKey(keyName: String) {
        val keyCode = keyNameToUsageId(keyName) ?: return
        val device = connectedDevice
        typingExecutor.execute {
            synchronized(heldLock) {
                heldKeys.remove(keyCode)
                if (heldKeys.isEmpty()) heldModifier = 0
                if (device != null) sendHeldReport(device)
            }
            if (synchronized(heldLock) { heldKeys.isEmpty() }) {
                mainHandler.post { mainHandler.removeCallbacks(holdWatchdog) }
            }
        }
    }

    /** One all-zero report releases every key and modifier at once. Sent twice in case one packet is lost. */
    @SuppressLint("MissingPermission")
    fun releaseAll() {
        mainHandler.removeCallbacks(holdWatchdog)
        val device = connectedDevice
        typingExecutor.execute {
            synchronized(heldLock) {
                heldKeys.clear()
                heldModifier = 0
                if (device != null) {
                    sendHeldReport(device)
                    Thread.sleep(20)
                    sendHeldReport(device)
                }
            }
        }
    }

    /** The host can no longer be reached (disconnect): just forget the held state. */
    private fun clearHeldState() {
        mainHandler.removeCallbacks(holdWatchdog)
        synchronized(heldLock) {
            heldKeys.clear()
            heldModifier = 0
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendHeldReport(device: BluetoothDevice) {
        val report = ByteArray(8)
        report[0] = heldModifier
        var slot = 2
        for (code in heldKeys) {
            if (slot > 7) break
            report[slot++] = code
        }
        bluetoothHidDevice?.sendReport(device, 1, report)
    }
    /** Non-blocking key press with optional modifiers (for example "ALT+SHIFT"), sent on the typing queue. */
    @SuppressLint("MissingPermission")
    fun sendKeyAsync(keyName: String, modifierName: String? = null) {
        val device = connectedDevice ?: return
        val keyCode = keyNameToUsageId(keyName) ?: return
        val modifier = if (modifierName.isNullOrBlank()) 0.toByte() else (modifierNameToByte(modifierName) ?: return)
        typingExecutor.execute {
            sendKey(device, keyCode, modifier)
        }
    }

    @SuppressLint("MissingPermission")
    fun sendKeyPressBlocking(keyName: String): Boolean {
        val device = connectedDevice ?: return false
        val keyCode = keyNameToUsageId(keyName) ?: return false
        sendKey(device, keyCode, 0.toByte())
        return true
    }

    @SuppressLint("MissingPermission")
    fun sendKeyComboBlocking(modifierName: String, keyName: String): Boolean {
        val device = connectedDevice ?: return false
        val modifier = modifierNameToByte(modifierName) ?: return false
        val keyCode = keyNameToUsageId(keyName) ?: return false
        sendKey(device, keyCode, modifier)
        return true
    }

    @SuppressLint("MissingPermission")
    fun sendWindowsRunCommandBlocking(command: String): Boolean {
        val device = connectedDevice ?: return false
        sendWindowsRunCommandReport(device, command)
        return true
    }

    @SuppressLint("MissingPermission")
    private fun sendKey(device: BluetoothDevice, keyCode: Byte, shift: Boolean) {
        sendKey(device, keyCode, if (shift) MOD_LEFT_SHIFT else 0.toByte())
    }

    @SuppressLint("MissingPermission")
    private fun sendKey(device: BluetoothDevice, keyCode: Byte, modifier: Byte) {
        val report = ByteArray(8)
        report[0] = modifier
        report[2] = keyCode

        bluetoothHidDevice?.sendReport(device, 1, report) // Press

        report[0] = 0
        report[2] = 0
        bluetoothHidDevice?.sendReport(device, 1, report) // Release
    }

    private fun sendTextReport(device: BluetoothDevice, text: String) {
        for (char in text) {
            val (keyCode, shift) = charToKeyCode(char)
            if (keyCode != 0.toByte()) {
                sendKey(device, keyCode, shift)
                Thread.sleep(20)
            }
        }
    }

    private fun sendWindowsRunCommandReport(device: BluetoothDevice, command: String) {
        sendKey(device, KEY_R, MOD_LEFT_GUI)
        Thread.sleep(400)
        sendTextReport(device, "$command\n")
    }

    private fun modifierNameToByte(name: String): Byte? {
        var result = 0
        for (part in name.split('+').map { it.trim().uppercase() }.filter { it.isNotEmpty() }) {
            val modifier = when (part) {
                "SHIFT" -> MOD_LEFT_SHIFT
                "CTRL", "CONTROL" -> MOD_LEFT_CTRL
                "ALT" -> MOD_LEFT_ALT
                "WIN", "GUI", "META" -> MOD_LEFT_GUI
                "RCTRL", "RCONTROL" -> MOD_RIGHT_CTRL
                "RSHIFT" -> MOD_RIGHT_SHIFT
                "RALT", "ALTGR" -> MOD_RIGHT_ALT
                "RWIN", "RGUI", "RMETA" -> MOD_RIGHT_GUI
                else -> return null
            }
            result = result or (modifier.toInt() and 0xFF)
        }
        return if (result == 0) null else result.toByte()
    }

    private fun keyNameToUsageId(name: String): Byte? {
        val upper = name.uppercase().filterNot { it.isWhitespace() || it == '_' }.ifEmpty { name.uppercase() }
        if (upper.length == 1) {
            val char = upper.first()
            if (char in 'A'..'Z' || char in '0'..'9') {
                return charToKeyCode(char.lowercaseChar()).first
            }
        }
        return when (upper) {
            "ENTER", "RETURN" -> 0x28.toByte()
            "ESC", "ESCAPE" -> 0x29.toByte()
            "BACKSPACE" -> 0x2A.toByte()
            "TAB" -> 0x2B.toByte()
            "SPACE" -> 0x2C.toByte()
            "-" -> 0x2D.toByte()
            "=" -> 0x2E.toByte()
            "[" -> 0x2F.toByte()
            "]" -> 0x30.toByte()
            "\\" -> 0x31.toByte()
            ";" -> 0x33.toByte()
            "'" -> 0x34.toByte()
            "`", "GRAVE", "BACKTICK" -> 0x35.toByte()
            "," -> 0x36.toByte()
            "." -> 0x37.toByte()
            "/" -> 0x38.toByte()
            "CAPSLOCK" -> 0x39.toByte()
            "PRINTSCREEN", "PRTSC", "PRTSCN" -> 0x46.toByte()
            "SCROLLLOCK" -> 0x47.toByte()
            "PAUSE", "BREAK" -> 0x48.toByte()
            "INSERT", "INS" -> 0x49.toByte()
            "HOME" -> 0x4A.toByte()
            "PAGEUP", "PGUP" -> 0x4B.toByte()
            "DELETE", "DEL" -> 0x4C.toByte()
            "END" -> 0x4D.toByte()
            "PAGEDOWN", "PGDN" -> 0x4E.toByte()
            "RIGHT" -> 0x4F.toByte()
            "LEFT" -> 0x50.toByte()
            "DOWN" -> 0x51.toByte()
            "UP" -> 0x52.toByte()
            "NUMLOCK" -> 0x53.toByte()
            "NUMDIVIDE" -> 0x54.toByte()
            "NUMMULTIPLY" -> 0x55.toByte()
            "NUMMINUS" -> 0x56.toByte()
            "NUMPLUS", "PLUS", "+" -> 0x57.toByte()
            "NUMENTER" -> 0x58.toByte()
            "NUM1" -> 0x59.toByte()
            "NUM2" -> 0x5A.toByte()
            "NUM3" -> 0x5B.toByte()
            "NUM4" -> 0x5C.toByte()
            "NUM5" -> 0x5D.toByte()
            "NUM6" -> 0x5E.toByte()
            "NUM7" -> 0x5F.toByte()
            "NUM8" -> 0x60.toByte()
            "NUM9" -> 0x61.toByte()
            "NUM0" -> 0x62.toByte()
            "NUMDECIMAL" -> 0x63.toByte()
            "APPLICATION", "MENU", "APPS" -> 0x65.toByte()
            else -> {
                if (upper.length in 2..3 && upper.startsWith("F")) {
                    upper.drop(1).toIntOrNull()?.takeIf { it in 1..12 }?.let {
                        (0x39 + it).toByte()
                    }
                } else {
                    null
                }
            }
        }
    }

    private fun charToKeyCode(char: Char): Pair<Byte, Boolean> {
        cyrillicToKeyCode(char)?.let { return it }
        return when (char) {
            in 'a'..'z' -> (0x04 + (char - 'a')).toByte() to false
            in 'A'..'Z' -> (0x04 + (char - 'A')).toByte() to true
            in '1'..'9' -> (0x1E + (char - '1')).toByte() to false
            '0' -> 0x27.toByte() to false
            ' ' -> 0x2C.toByte() to false
            '\n' -> 0x28.toByte() to false
            '!' -> 0x1E.toByte() to true
            '@' -> 0x1F.toByte() to true
            '#' -> 0x20.toByte() to true
            '$' -> 0x21.toByte() to true
            '%' -> 0x22.toByte() to true
            '^' -> 0x23.toByte() to true
            '&' -> 0x24.toByte() to true
            '*' -> 0x25.toByte() to true
            '(' -> 0x26.toByte() to true
            ')' -> 0x27.toByte() to true
            '-' -> 0x2D.toByte() to false
            '_' -> 0x2D.toByte() to true
            '=' -> 0x2E.toByte() to false
            '+' -> 0x2E.toByte() to true
            '[' -> 0x2F.toByte() to false
            '{' -> 0x2F.toByte() to true
            ']' -> 0x30.toByte() to false
            '}' -> 0x30.toByte() to true
            '\\' -> 0x31.toByte() to false
            '|' -> 0x31.toByte() to true
            ';' -> 0x33.toByte() to false
            ':' -> 0x33.toByte() to true
            '\'' -> 0x34.toByte() to false
            '"' -> 0x34.toByte() to true
            '`' -> 0x35.toByte() to false
            '~' -> 0x35.toByte() to true
            ',' -> 0x36.toByte() to false
            '<' -> 0x36.toByte() to true
            '.' -> 0x37.toByte() to false
            '>' -> 0x37.toByte() to true
            '/' -> 0x38.toByte() to false
            '?' -> 0x38.toByte() to true
            else -> 0.toByte() to false
        }
    }

    /** Russian ЙЦУКЕН letter → the US key in the same position. Host must use a Russian layout. */
    private fun cyrillicToKeyCode(char: Char): Pair<Byte, Boolean>? {
        val index = CYRILLIC_JCUKEN.indexOf(char.lowercaseChar())
        if (index < 0) return null
        return CYRILLIC_HID_KEYS[index] to char.isUpperCase()
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
        /** Safety net: if no release / keep-alive arrives within this time, all held keys are released. */
        private const val HOLD_TIMEOUT_MS = 3000L
        /** Boot-protocol keyboard report has 6 regular key slots (6KRO). */
        private const val MAX_HELD_KEYS = 6

        private const val MOD_LEFT_CTRL: Byte = 0x01
        private const val MOD_LEFT_SHIFT: Byte = 0x02
        private const val MOD_LEFT_ALT: Byte = 0x04
        private const val MOD_LEFT_GUI: Byte = 0x08
        private const val MOD_RIGHT_CTRL: Byte = 0x10
        private const val MOD_RIGHT_SHIFT: Byte = 0x20
        private const val MOD_RIGHT_ALT: Byte = 0x40
        private const val MOD_RIGHT_GUI: Byte = 0x80.toByte()
        private const val KEY_R: Byte = 0x15

        private const val CYRILLIC_JCUKEN = "ёйцукенгшщзхъфывапролджэячсмитьбю"
        private val CYRILLIC_HID_KEYS = byteArrayOf(
            0x35, 0x14, 0x1A, 0x08, 0x15, 0x17, 0x1C, 0x18, 0x0C, 0x12, 0x13, 0x2F, 0x30,
            0x04, 0x16, 0x07, 0x09, 0x0A, 0x0B, 0x0D, 0x0E, 0x0F, 0x33, 0x34,
            0x1D, 0x1B, 0x06, 0x19, 0x05, 0x11, 0x10, 0x36, 0x37
        )

        /** US QWERTY printable set. No Enter, so a password field is not submitted. */
        const val PRINTABLE_SYMBOL_TEST =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 " +
                "`-=[]\\;',./~!@#\$%^&*()_+{}|:\"<>?"

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

        /*
         * Composite descriptor experiment, kept commented so it can be restored if needed.
         * Keyboard was Report ID 1 and mouse was Report ID 2. No runtime mouse reports were sent.
         *
         * To restore it, add 0x85, 0x01 after the keyboard Collection item, append the mouse
         * collection bytes, and use BluetoothHidDevice.SUBCLASS1_COMBO in registerApp().
         */
    }
}
