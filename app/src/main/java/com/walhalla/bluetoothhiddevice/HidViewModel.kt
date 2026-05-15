package com.walhalla.bluetoothhiddevice

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HidViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "HidViewModel"
    private var hidManager: HidDeviceManager = HidDeviceManager(application)
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    private val _uiState = MutableStateFlow(HidUiState())
    val uiState: StateFlow<HidUiState> = _uiState.asStateFlow()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "Service Bound")
            val binder = service as HidForegroundService.LocalBinder
            val srv = binder.getService()
            hidManager = srv.hidManager
            setupStatusListener()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Service Unbound")
        }
    }

    init {
        setupStatusListener()
        refreshBondedDevices()
    }

    private fun setupStatusListener() {
        hidManager.setStatusListener { status ->
            _uiState.value = _uiState.value.copy(
                status = status,
                isConnected = status.contains("Connected"),
                isBluetoothOff = status.contains("OFF")
            )
        }
    }

    fun togglePersistence(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isPersistentMode = enabled)
        val intent = Intent(getApplication(), HidForegroundService::class.java)
        if (enabled) {
            getApplication<Application>().startForegroundService(intent)
            getApplication<Application>().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } else {
            try {
                getApplication<Application>().unbindService(serviceConnection)
            } catch (e: Exception) {
                Log.e(TAG, "Error unbinding", e)
            }
            getApplication<Application>().stopService(intent)
            // Re-init local manager if service is stopped
            hidManager = HidDeviceManager(getApplication())
            setupStatusListener()
        }
    }

    fun resume() {
        refreshBondedDevices()
        hidManager.refreshConnectionState()
    }

    @SuppressLint("MissingPermission")
    fun refreshBondedDevices() {
        val devices = bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
        _uiState.value = _uiState.value.copy(bondedDevices = devices)
    }

    fun makeDiscoverable(activity: android.app.Activity) {
        hidManager.requestDiscoverable(activity)
    }

    fun connect(device: BluetoothDevice) {
        hidManager.connect(device)
    }

    fun sendText(text: String) {
        hidManager.sendString(text)
    }

    fun forceReset() {
        hidManager.forceReset()
    }

    fun checkBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }
}

data class HidUiState(
    val status: String = "Initializing...",
    val isConnected: Boolean = false,
    val isBluetoothOff: Boolean = false,
    val isPersistentMode: Boolean = false,
    val bondedDevices: List<BluetoothDevice> = emptyList()
)
