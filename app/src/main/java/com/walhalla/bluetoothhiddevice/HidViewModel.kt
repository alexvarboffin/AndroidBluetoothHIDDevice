package com.walhalla.bluetoothhiddevice

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HidViewModel(application: Application) : AndroidViewModel(application) {

    private val hidManager = HidDeviceManager(application)
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    private val _uiState = MutableStateFlow(HidUiState())
    val uiState: StateFlow<HidUiState> = _uiState.asStateFlow()

    init {
        hidManager.setStatusListener { status ->
            _uiState.value = _uiState.value.copy(
                status = status,
                isConnected = status.contains("Connected"),
                isBluetoothOff = status.contains("OFF")
            )
        }
        refreshBondedDevices()
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

    fun checkBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }
}

data class HidUiState(
    val status: String = "Initializing...",
    val isConnected: Boolean = false,
    val isBluetoothOff: Boolean = false,
    val bondedDevices: List<BluetoothDevice> = emptyList()
)
