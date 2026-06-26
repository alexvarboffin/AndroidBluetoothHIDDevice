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
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.walhalla.bluetoothhiddevice.presets.PresetActionCodec
import com.walhalla.bluetoothhiddevice.presets.PresetCategoryEntity
import com.walhalla.bluetoothhiddevice.presets.PresetEntity
import com.walhalla.bluetoothhiddevice.presets.PresetExecutor
import com.walhalla.bluetoothhiddevice.presets.PresetRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

class HidViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "HidViewModel"
    private var hidManager: HidDeviceManager? = null
    private var hidService: HidForegroundService? = null
    private var isServiceBound = false
    private var presetExecutor: PresetExecutor? = null
    private val presetRepository = PresetRepository(application)
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var selectedCategoryJob: Job? = null
    private var selectedCategoryJobCategoryId: Long? = null

    private val _uiState = MutableStateFlow(HidUiState())
    val uiState: StateFlow<HidUiState> = _uiState.asStateFlow()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "Service Bound")
            val binder = service as HidForegroundService.LocalBinder
            val srv = binder.getService()
            hidService = srv
            hidManager = srv.hidManager
            presetExecutor = PresetExecutor(srv.hidManager)
            isServiceBound = true
            setupStatusListener()
            refreshPersistentModeState()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Service Unbound")
            isServiceBound = false
            hidService = null
            hidManager = null
            presetExecutor = null
            _uiState.value = _uiState.value.copy(
                status = "HID Service disconnected",
                isConnected = false,
                connectedDeviceAddress = null,
                isPersistentMode = false
            )
        }
    }

    init {
        bindHidService()
        refreshBondedDevices()
        observePresets()
        viewModelScope.launch {
            presetRepository.ensureSeedData()
        }
    }

    private fun setupStatusListener() {
        val manager = hidManager ?: return
        manager.setStatusListener { status, connectedDevice ->
            _uiState.value = _uiState.value.copy(
                status = status,
                isConnected = connectedDevice != null,
                isBluetoothOff = status.contains("OFF"),
                connectedDeviceAddress = connectedDevice?.address
            )
        }
    }

    fun togglePersistence(enabled: Boolean) {
        val intent = Intent(getApplication(), HidForegroundService::class.java)
        if (enabled) {
            ContextCompat.startForegroundService(getApplication(), intent)
            bindHidService()
            _uiState.value = _uiState.value.copy(isPersistentMode = true)
        } else {
            hidService?.stopForegroundMode()
            getApplication<Application>().stopService(intent)
            _uiState.value = _uiState.value.copy(isPersistentMode = false)
        }
    }

    fun resume() {
        bindHidService()
        refreshBondedDevices()
        hidManager?.refreshConnectionState()
        refreshPersistentModeState()
    }

    fun keepConnectionAliveInBackground() {
        if (!_uiState.value.isConnected) return
        val intent = Intent(getApplication(), HidForegroundService::class.java)
        ContextCompat.startForegroundService(getApplication(), intent)
        hidService?.startForegroundMode("Connected HID session is kept alive")
        _uiState.value = _uiState.value.copy(isPersistentMode = true)
    }

    @SuppressLint("MissingPermission")
    fun refreshBondedDevices() {
        val devices = bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
        _uiState.value = _uiState.value.copy(bondedDevices = devices)
    }

    fun makeDiscoverable(activity: android.app.Activity) {
        hidManager?.requestDiscoverable(activity)
    }

    fun connect(device: BluetoothDevice) {
        hidManager?.connect(device)
    }

    fun disconnect(device: BluetoothDevice) {
        hidManager?.disconnect(device)
    }

    fun sendText(text: String) {
        hidManager?.sendString(text)
    }

    fun openCalculatorOnHost() {
        hidManager?.sendOpenCalculatorShortcut()
    }

    fun runWindowsCommandPreset(command: String) {
        hidManager?.sendWindowsRunCommand(command)
    }

    fun selectPresetCategory(categoryId: Long) {
        _uiState.value = _uiState.value.copy(selectedPresetCategoryId = categoryId)
        observePresetsForCategory(categoryId)
    }

    fun addPresetCategory(title: String) {
        val categoryTitle = title.trim().ifBlank { generateDefaultGroupName() }
        viewModelScope.launch {
            presetRepository.addCategory(categoryTitle)
        }
    }

    fun deleteSelectedPresetCategory() {
        val category = _uiState.value.presetCategories
            .firstOrNull { it.id == _uiState.value.selectedPresetCategoryId }
            ?: return
        if (category.isBuiltIn) return

        viewModelScope.launch {
            presetRepository.deleteCustomCategory(category.id)
        }
    }

    fun addPreset(
        title: String,
        description: String,
        actionType: String,
        value: String,
        isSensitive: Boolean
    ) {
        val categoryId = _uiState.value.selectedPresetCategoryId ?: return
        if (value.isBlank()) return
        val fallbackName = generateDefaultPresetName()
        val presetTitle = title.trim().ifBlank { fallbackName }
        val presetDescription = description.trim().ifBlank { presetTitle }
        val sensitivePreset = isSensitive ||
            actionType == PresetActionCodec.TYPE_TYPE_SENSITIVE_TEXT ||
            actionType == PresetActionCodec.TYPE_CREDENTIAL

        viewModelScope.launch {
            presetRepository.addSingleActionPreset(
                categoryId = categoryId,
                title = presetTitle,
                description = presetDescription,
                value = value,
                sortOrder = _uiState.value.presets.size,
                actionType = actionType,
                isSensitive = sensitivePreset
            )
        }
    }

    fun requestRunPreset(preset: PresetEntity) {
        if (preset.isSensitive || preset.requiresConfirmation) {
            _uiState.value = _uiState.value.copy(pendingSensitivePreset = preset)
        } else {
            runPreset(preset.id)
        }
    }

    fun duplicatePreset(preset: PresetEntity) {
        viewModelScope.launch {
            presetRepository.duplicatePreset(preset.id)
        }
    }

    fun deletePreset(preset: PresetEntity) {
        if (preset.isBuiltIn) return
        viewModelScope.launch {
            presetRepository.deletePreset(preset.id)
        }
    }

    fun requestEditPreset(preset: PresetEntity) {
        if (preset.isBuiltIn) return
        viewModelScope.launch {
            val presetWithActions = presetRepository.getPresetWithActions(preset.id) ?: return@launch
            val firstAction = presetWithActions.actions.firstOrNull()
            val actionType = firstAction?.type ?: PresetActionCodec.TYPE_RUN_WINDOWS_COMMAND
            val payload = firstAction?.payloadJson?.let { JSONObject(it) } ?: JSONObject()
            val editDraft = PresetEditDraft(
                preset = presetWithActions.preset,
                actionType = actionType,
                value = when (actionType) {
                    PresetActionCodec.TYPE_TYPE_TEXT,
                    PresetActionCodec.TYPE_TYPE_SENSITIVE_TEXT -> payload.optString("text")
                    PresetActionCodec.TYPE_RUN_WINDOWS_COMMAND -> payload.optString("command")
                    else -> ""
                },
                login = payload.optString("login"),
                password = payload.optString("password")
            )
            _uiState.value = _uiState.value.copy(editingPreset = editDraft)
        }
    }

    fun updateEditingPreset(
        title: String,
        description: String,
        actionType: String,
        value: String,
        isSensitive: Boolean
    ) {
        val editingPreset = _uiState.value.editingPreset ?: return
        val presetTitle = title.trim().ifBlank { editingPreset.preset.title }
        val presetDescription = description.trim().ifBlank { presetTitle }
        val sensitivePreset = isSensitive ||
            actionType == PresetActionCodec.TYPE_TYPE_SENSITIVE_TEXT ||
            actionType == PresetActionCodec.TYPE_CREDENTIAL

        viewModelScope.launch {
            presetRepository.updateSingleActionPreset(
                presetId = editingPreset.preset.id,
                title = presetTitle,
                description = presetDescription,
                value = value,
                actionType = actionType,
                isSensitive = sensitivePreset
            )
            _uiState.value = _uiState.value.copy(editingPreset = null)
        }
    }

    fun dismissEditPreset() {
        _uiState.value = _uiState.value.copy(editingPreset = null)
    }

    fun confirmPendingPreset() {
        val preset = _uiState.value.pendingSensitivePreset ?: return
        _uiState.value = _uiState.value.copy(pendingSensitivePreset = null)
        runPreset(preset.id)
    }

    fun dismissPendingPreset() {
        _uiState.value = _uiState.value.copy(pendingSensitivePreset = null)
    }

    fun exportPresetsJson(includeSensitive: Boolean, onExported: (String) -> Unit) {
        viewModelScope.launch {
            onExported(presetRepository.exportToJson(includeSensitive))
        }
    }

    fun importPresetsJson(json: String) {
        viewModelScope.launch {
            runCatching {
                presetRepository.importFromJson(json)
            }.onFailure { error ->
                Log.e(TAG, "Preset import failed", error)
                _uiState.value = _uiState.value.copy(status = "Preset import failed")
            }
        }
    }

    fun forceReset() {
        hidManager?.forceReset()
    }

    fun checkBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    private fun observePresets() {
        viewModelScope.launch {
            presetRepository.categories.collect { categories ->
                val selectedId = _uiState.value.selectedPresetCategoryId
                    ?.takeIf { id -> categories.any { it.id == id } }
                    ?: categories.firstOrNull()?.id

                _uiState.value = _uiState.value.copy(
                    presetCategories = categories,
                    selectedPresetCategoryId = selectedId
                )

                if (selectedId != null && selectedId != selectedCategoryJobCategoryId) {
                    observePresetsForCategory(selectedId)
                }
            }
        }

        viewModelScope.launch {
            presetRepository.allPresets.collect { presets ->
                _uiState.value = _uiState.value.copy(allPresets = presets)
            }
        }

        viewModelScope.launch {
            presetRepository.allActions.collect { actions ->
                val actionTypes = actions
                    .groupBy { it.presetId }
                    .mapValues { (_, presetActions) ->
                        presetActions.minByOrNull { it.sortOrder }?.type.orEmpty()
                    }
                _uiState.value = _uiState.value.copy(presetActionTypes = actionTypes)
            }
        }
    }

    private fun observePresetsForCategory(categoryId: Long) {
        selectedCategoryJob?.cancel()
        selectedCategoryJobCategoryId = categoryId
        selectedCategoryJob = viewModelScope.launch {
            presetRepository.presetsForCategory(categoryId).collect { presets ->
                _uiState.value = _uiState.value.copy(presets = presets)
            }
        }
    }

    private fun runPreset(presetId: Long) {
        viewModelScope.launch {
            val preset = presetRepository.getPresetWithActions(presetId) ?: return@launch
            val actions = preset.actions.map { PresetActionCodec.fromEntity(it) }
            val success = presetExecutor?.execute(actions) == true
            if (!success) {
                _uiState.value = _uiState.value.copy(status = "Preset failed: no HID connection or unsupported key")
            }
        }
    }

    override fun onCleared() {
        if (isServiceBound) {
            runCatching {
                getApplication<Application>().unbindService(serviceConnection)
            }.onFailure { error ->
                Log.e(TAG, "Error unbinding HID service", error)
            }
            isServiceBound = false
        }
        super.onCleared()
    }

    private fun bindHidService() {
        if (isServiceBound) {
            refreshPersistentModeState()
            return
        }
        val intent = Intent(getApplication(), HidForegroundService::class.java)
        getApplication<Application>().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun refreshPersistentModeState() {
        val isActive = hidService?.isForegroundModeActive() == true
        if (_uiState.value.isPersistentMode != isActive) {
            _uiState.value = _uiState.value.copy(isPersistentMode = isActive)
        }
    }

    private fun generateDefaultPresetName(): String {
        return "Preset-${(System.currentTimeMillis() % 1000).toString().padStart(3, '0')}"
    }

    private fun generateDefaultGroupName(): String {
        return "Group-${(System.currentTimeMillis() % 1000).toString().padStart(3, '0')}"
    }
}

data class HidUiState(
    val status: String = "Initializing...",
    val isConnected: Boolean = false,
    val isBluetoothOff: Boolean = false,
    val isPersistentMode: Boolean = false,
    val connectedDeviceAddress: String? = null,
    val bondedDevices: List<BluetoothDevice> = emptyList(),
    val presetCategories: List<PresetCategoryEntity> = emptyList(),
    val selectedPresetCategoryId: Long? = null,
    val presets: List<PresetEntity> = emptyList(),
    val allPresets: List<PresetEntity> = emptyList(),
    val presetActionTypes: Map<Long, String> = emptyMap(),
    val pendingSensitivePreset: PresetEntity? = null,
    val editingPreset: PresetEditDraft? = null
)

data class PresetEditDraft(
    val preset: PresetEntity,
    val actionType: String,
    val value: String,
    val login: String,
    val password: String
)
