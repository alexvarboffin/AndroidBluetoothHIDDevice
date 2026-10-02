package com.walhalla.bluetoothhiddevice

import android.R
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.walhalla.bluetoothhiddevice.presets.PresetActionCodec
import com.walhalla.bluetoothhiddevice.presets.PresetCategoryEntity
import com.walhalla.bluetoothhiddevice.presets.PresetEntity
import com.walhalla.bluetoothhiddevice.presets.PresetShortcutDraft
import com.walhalla.bluetoothhiddevice.presets.ShortcutDraft
import com.walhalla.bluetoothhiddevice.presets.ShortcutKeyGroup
import com.walhalla.bluetoothhiddevice.presets.ShortcutKeys
import org.json.JSONObject
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HidScreen(
    viewModel: HidViewModel,
    onEnableBluetooth: () -> Unit,
    onMakeDiscoverable: () -> Unit,
    onRequestBluetoothPermission: () -> Unit,
    onImportPresets: () -> Unit,
    onExportPresets: (includeSensitive: Boolean) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var showPresetEditor by remember { mutableStateOf(false) }
    var showCategoryEditor by remember { mutableStateOf(false) }
    var showDeleteCategoryDialog by remember { mutableStateOf(false) }
    var presetPendingDelete by remember { mutableStateOf<PresetEntity?>(null) }
    var presetPendingReset by remember { mutableStateOf<PresetEntity?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showGroupColorDialog by remember { mutableStateOf(false) }
    var viewLayoutName by rememberSaveable { mutableStateOf(PresetViewLayout.LIST.name) }
    val viewLayout = remember(viewLayoutName) { PresetViewLayout.valueOf(viewLayoutName) }
    val onViewLayoutSelected: (PresetViewLayout) -> Unit = { viewLayoutName = it.name }
    val selectedPresetCategory = uiState.presetCategories
        .firstOrNull { it.id == uiState.selectedPresetCategoryId }
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    BackHandler(enabled = selectedTab != 0) {
        selectedTab = 0
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (!isLandscape) {
            CenterAlignedTopAppBar(
                title = {
                    StatusTopBarTitle(
                        status = uiState.status,
                        isConnected = uiState.isConnected,
                        isBluetoothOff = uiState.isBluetoothOff
                    )
                },
                actions = {
                    HostCommandPresetMenu(
                        enabled = uiState.isConnected,
                        categories = uiState.presetCategories,
                        presets = uiState.allPresets,
                        actionTypes = uiState.presetActionTypes,
                        onRunPreset = viewModel::requestRunPreset
                    )
                }
            )
            }
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .imePadding()
        ) {
        if (isLandscape) {
            LandscapeIconRail(
                selectedTab = selectedTab,
                onSelectTab = { selectedTab = it },
                status = uiState.status,
                isConnected = uiState.isConnected,
                isBluetoothOff = uiState.isBluetoothOff,
                commandMenu = {
                    HostCommandPresetMenu(
                        enabled = uiState.isConnected,
                        categories = uiState.presetCategories,
                        presets = uiState.allPresets,
                        actionTypes = uiState.presetActionTypes,
                        onRunPreset = viewModel::requestRunPreset
                    )
                },
                presetsMenu = {
                    PresetLayoutMenu(
                        selectedLayout = viewLayout,
                        onLayoutSelected = onViewLayoutSelected,
                        onImportPresets = onImportPresets,
                        onExportPresets = { showExportDialog = true },
                        selectedCategory = selectedPresetCategory,
                        onPickGroupColor = { showGroupColorDialog = true },
                        onAddPreset = { showPresetEditor = true },
                        onDeleteCategory = { showDeleteCategoryDialog = true }
                    )
                }
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            if (!isLandscape) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.Bluetooth, contentDescription = null)
                            Text("Devices")
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.Bookmarks, contentDescription = null)
                            Text("Presets")
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.Keyboard, contentDescription = null)
                            Text("Type")
                        }
                    }
                )
            }
            }

            if (selectedTab == 1) {
                PresetsTab(
                    uiState = uiState,
                    onSelectCategory = viewModel::selectPresetCategory,
                    onRunPreset = viewModel::requestRunPreset,
                    onEditPreset = viewModel::requestEditPreset,
                    onDuplicatePreset = viewModel::duplicatePreset,
                    onDeletePreset = { presetPendingDelete = it },
                    onResetPreset = { presetPendingReset = it },
                    onAddPreset = { showPresetEditor = true },
                    onCategoryColorChange = viewModel::setSelectedPresetCategoryColor,
                    onAddCategory = { showCategoryEditor = true },
                    onDeleteCategory = { showDeleteCategoryDialog = true },
                    onImportPresets = onImportPresets,
                    onExportPresets = { showExportDialog = true },
                    onPickGroupColor = { showGroupColorDialog = true },
                    viewLayout = viewLayout,
                    onViewLayoutSelected = onViewLayoutSelected,
                    showLayoutFab = !isLandscape,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
            if (selectedTab == 2) {
                TypeTab(
                    enabled = uiState.isConnected,
                    onSendClipboard = viewModel::sendClipboard,
                    onTypingChange = viewModel::sendTypingChange,
                    onMouseKey = viewModel::sendMouseKey,
                    onToggleMouseKeys = viewModel::toggleWindowsMouseKeys,
                    onOpenMouseKeysSettings = viewModel::openMouseKeysSettingsOnHost,
                    onHoldPress = viewModel::pressHeldKey,
                    onHoldRelease = viewModel::releaseHeldKey,
                    onHoldKeepAlive = viewModel::keepHeldKeysAlive,
                    onReleaseAllHeld = viewModel::releaseAllHeldKeys
                )
            } else {
            DevicesTab(
                uiState = uiState,
                onEnableBluetooth = onEnableBluetooth,
                onMakeDiscoverable = onMakeDiscoverable,
                onRequestBluetoothPermission = onRequestBluetoothPermission,
                onTogglePersistence = viewModel::togglePersistence,
                onForceReset = viewModel::forceReset,
                onSendTestKey = { viewModel.sendText("A") },
                onOpenCalculator = viewModel::openCalculatorOnHost,
                onSendSymbolTest = viewModel::sendHidSymbolTest,
                onConnect = viewModel::connect,
                onDisconnect = viewModel::disconnect
            )
            }
            }
            }
        }
        }
    }

    if (showPresetEditor) {
        PresetEditorDialog(
            categories = uiState.presetCategories,
            selectedCategoryId = uiState.selectedPresetCategoryId,
            onDismiss = { showPresetEditor = false },
            onSave = { title, description, actionType, value, isSensitive ->
                viewModel.addPreset(title, description, actionType, value, isSensitive)
                showPresetEditor = false
            }
        )
    }

    uiState.editingPreset?.let { editDraft ->
        PresetEditorDialog(
            categories = uiState.presetCategories,
            selectedCategoryId = editDraft.preset.categoryId,
            titleText = "Edit Preset",
            confirmText = "Save",
            initialTitle = editDraft.preset.title,
            initialDescription = editDraft.preset.description,
            initialActionType = editDraft.actionType,
            initialValue = editDraft.value,
            initialLogin = editDraft.login,
            initialPassword = editDraft.password,
            initialSensitive = editDraft.preset.isSensitive,
            onDismiss = viewModel::dismissEditPreset,
            onSave = viewModel::updateEditingPreset
        )
    }

    if (showCategoryEditor) {
        CategoryEditorDialog(
            onDismiss = { showCategoryEditor = false },
            onSave = { title ->
                viewModel.addPresetCategory(title)
                showCategoryEditor = false
            }
        )
    }

    if (showDeleteCategoryDialog) {
        DeleteCategoryDialog(
            category = uiState.presetCategories.firstOrNull { it.id == uiState.selectedPresetCategoryId },
            onDismiss = { showDeleteCategoryDialog = false },
            onConfirm = {
                viewModel.deleteSelectedPresetCategory()
                showDeleteCategoryDialog = false
            }
        )
    }

    presetPendingDelete?.let { preset ->
        DeletePresetDialog(
            preset = preset,
            onDismiss = { presetPendingDelete = null },
            onConfirm = {
                viewModel.deletePreset(preset)
                presetPendingDelete = null
            }
        )
    }

    presetPendingReset?.let { preset ->
        ResetPresetDialog(
            preset = preset,
            onDismiss = { presetPendingReset = null },
            onConfirm = {
                viewModel.resetPresetToDefault(preset)
                presetPendingReset = null
            }
        )
    }

    uiState.pendingSensitivePreset?.let { preset ->
        SensitivePresetConfirmationDialog(
            preset = preset,
            onDismiss = viewModel::dismissPendingPreset,
            onConfirm = viewModel::confirmPendingPreset
        )
    }

    if (showGroupColorDialog && selectedPresetCategory != null) {
        GroupColorDialog(
            selectedArgb = selectedPresetCategory.colorArgb,
            onDismiss = { showGroupColorDialog = false },
            onSelect = { colorArgb ->
                showGroupColorDialog = false
                viewModel.setSelectedPresetCategoryColor(colorArgb)
            }
        )
    }

    if (showExportDialog) {
        ExportPresetsDialog(
            onDismiss = { showExportDialog = false },
            onExportSafeOnly = {
                showExportDialog = false
                onExportPresets(false)
            },
            onExportIncludingSensitive = {
                showExportDialog = false
                onExportPresets(true)
            }
        )
    }
}

@Composable
private fun LandscapeIconRail(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    status: String,
    isConnected: Boolean,
    isBluetoothOff: Boolean,
    commandMenu: @Composable () -> Unit,
    presetsMenu: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .width(48.dp)
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        StatusLamp(
            status = status,
            isConnected = isConnected,
            isBluetoothOff = isBluetoothOff,
            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
        )
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ToolbarTabAction(
                selected = selectedTab == 0,
                icon = Icons.Filled.Bluetooth,
                contentDescription = "Devices",
                onClick = { onSelectTab(0) }
            )
            ToolbarTabAction(
                selected = selectedTab == 1,
                icon = Icons.Filled.Bookmarks,
                contentDescription = "Presets",
                onClick = { onSelectTab(1) }
            )
            ToolbarTabAction(
                selected = selectedTab == 2,
                icon = Icons.Filled.Keyboard,
                contentDescription = "Type",
                onClick = { onSelectTab(2) }
            )
            commandMenu()
        }
        presetsMenu()
    }
}

@Composable
private fun StatusLamp(
    status: String,
    isConnected: Boolean,
    isBluetoothOff: Boolean,
    modifier: Modifier = Modifier
) {
    val container = when (statusTone(status, isConnected, isBluetoothOff)) {
        StatusTone.Connected -> MaterialTheme.colorScheme.primaryContainer
        StatusTone.Ready -> MaterialTheme.colorScheme.tertiaryContainer
        StatusTone.Busy -> MaterialTheme.colorScheme.secondaryContainer
        StatusTone.Idle -> MaterialTheme.colorScheme.surfaceVariant
        StatusTone.Error -> MaterialTheme.colorScheme.errorContainer
    }
    val glyph = when (statusTone(status, isConnected, isBluetoothOff)) {
        StatusTone.Connected -> MaterialTheme.colorScheme.onPrimaryContainer
        StatusTone.Ready -> MaterialTheme.colorScheme.onTertiaryContainer
        StatusTone.Busy -> MaterialTheme.colorScheme.onSecondaryContainer
        StatusTone.Idle -> MaterialTheme.colorScheme.outline
        StatusTone.Error -> MaterialTheme.colorScheme.onErrorContainer
    }
    Box(
        modifier = modifier
            .size(18.dp)
            .background(container, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = statusIcon(status, isConnected, isBluetoothOff),
            contentDescription = status,
            modifier = Modifier.size(12.dp),
            tint = glyph
        )
    }
}

@Composable
private fun ToolbarTabAction(
    selected: Boolean,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
fun StatusTopBarTitle(status: String, isConnected: Boolean, isBluetoothOff: Boolean) {
    val statusTint = statusColor(status, isConnected, isBluetoothOff)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Bluetooth HID Device",
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = statusIcon(status, isConnected, isBluetoothOff),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = statusTint
            )
            Text(
                text = status,
                style = MaterialTheme.typography.labelSmall,
                color = statusTint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun TypeTab(
    enabled: Boolean,
    onSendClipboard: (String) -> Unit,
    onTypingChange: (String, String) -> Unit,
    onMouseKey: (String) -> Unit = {},
    onToggleMouseKeys: () -> Unit = {},
    onOpenMouseKeysSettings: () -> Unit = {},
    onHoldPress: ((String) -> Unit)? = null,
    onHoldRelease: ((String) -> Unit)? = null,
    onHoldKeepAlive: () -> Unit = {},
    onReleaseAllHeld: () -> Unit = {}
) {
    // Never leave a key held on the host when this tab leaves the screen.
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { onReleaseAllHeld() }
    }
    val clipboard = LocalClipboardManager.current
    var draft by rememberSaveable { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Clipboard",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Button(
                onClick = { onSendClipboard(clipboard.getText()?.text.orEmpty()) },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled
            ) {
                Text("Send clipboard")
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Keyboard",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            OutlinedTextField(
                value = draft,
                onValueChange = { updated ->
                    if (enabled) onTypingChange(draft, updated)
                    draft = updated
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp),
                minLines = 4,
                enabled = enabled,
                label = { Text("Type to host") }
            )
            Text(
                text = "EN needs a US host layout. Russian letters need a Russian host layout. Other characters are skipped.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Mouse keys (Windows)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Button(
                onClick = onToggleMouseKeys,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled
            ) {
                Text("Toggle Mouse Keys (Alt+Shift+NumLock)")
            }
            OutlinedButton(
                onClick = onOpenMouseKeysSettings,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled
            ) {
                Text("Open Mouse Keys settings (speed)")
            }
            val rows = listOf(
                listOf("\u2196" to "NUM7", "\u2191" to "NUM8", "\u2197" to "NUM9"),
                listOf("\u2190" to "NUM4", "Click" to "NUM5", "\u2192" to "NUM6"),
                listOf("\u2199" to "NUM1", "\u2193" to "NUM2", "\u2198" to "NUM3")
            )
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { (label, key) ->
                        MouseKeyButton(
                            label = label,
                            keyName = key,
                            enabled = enabled,
                            repeatWhileHeld = key != "NUM5",
                            onMouseKey = onMouseKey,
                            onHoldPress = onHoldPress,
                            onHoldRelease = onHoldRelease,
                            onHoldKeepAlive = onHoldKeepAlive,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MouseKeyButton("Left btn", "NUMDIVIDE", enabled, false, onMouseKey, Modifier.weight(1f))
                MouseKeyButton("Right btn", "NUMMINUS", enabled, false, onMouseKey, Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MouseKeyButton("Hold", "NUM0", enabled, false, onMouseKey, Modifier.weight(1f))
                MouseKeyButton("Release", "NUMDECIMAL", enabled, false, onMouseKey, Modifier.weight(1f))
                MouseKeyButton("NumLock", "NUMLOCK", enabled, false, onMouseKey, Modifier.weight(1f))
            }
            Text(
                text = "Uses the Windows Mouse Keys numpad shortcuts. Press the toggle once (confirm the Windows prompt), keep Num Lock on, then hold an arrow to keep moving.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MouseKeyButton(
    label: String,
    keyName: String,
    enabled: Boolean,
    repeatWhileHeld: Boolean,
    onMouseKey: (String) -> Unit,
    modifier: Modifier = Modifier,
    onHoldPress: ((String) -> Unit)? = null,
    onHoldRelease: ((String) -> Unit)? = null,
    onHoldKeepAlive: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val currentOnMouseKey by rememberUpdatedState(onMouseKey)
    val currentHoldPress by rememberUpdatedState(onHoldPress)
    val currentHoldRelease by rememberUpdatedState(onHoldRelease)
    val currentHoldKeepAlive by rememberUpdatedState(onHoldKeepAlive)
    val container = if (enabled) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val content = if (enabled) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val gestureModifier = if (enabled) {
        Modifier.pointerInput(keyName, repeatWhileHeld) {
            detectTapGestures(onPress = {
                val holdPress = currentHoldPress
                if (repeatWhileHeld && holdPress != null) {
                    // Real hold: one report with the key down, one all-up report on release.
                    // The host does the auto-repeat and Mouse Keys acceleration itself.
                    holdPress(keyName)
                    val keepAlive = scope.launch {
                        while (true) {
                            delay(1000)
                            currentHoldKeepAlive()
                        }
                    }
                    try {
                        tryAwaitRelease()
                    } finally {
                        keepAlive.cancel()
                        currentHoldRelease?.invoke(keyName)
                    }
                    return@detectTapGestures
                }
                currentOnMouseKey(keyName)
                val job = if (repeatWhileHeld) {
                    scope.launch {
                        delay(350)
                        while (true) {
                            currentOnMouseKey(keyName)
                            delay(60)
                        }
                    }
                } else {
                    null
                }
                tryAwaitRelease()
                job?.cancel()
            })
        }
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .height(48.dp)
            .background(container, RoundedCornerShape(12.dp))
            .then(gestureModifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = content,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun DevicesTab(
    uiState: HidUiState,
    onEnableBluetooth: () -> Unit,
    onMakeDiscoverable: () -> Unit,
    onRequestBluetoothPermission: () -> Unit,
    onTogglePersistence: (Boolean) -> Unit,
    onForceReset: () -> Unit,
    onSendTestKey: () -> Unit,
    onOpenCalculator: () -> Unit,
    onSendSymbolTest: () -> Unit,
    onConnect: (BluetoothDevice) -> Unit,
    onDisconnect: (BluetoothDevice) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Persistent Mode", fontWeight = FontWeight.Bold)
                Text(
                    "Keep connection alive in background",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(
                checked = uiState.isPersistentMode,
                onCheckedChange = onTogglePersistence
            )
        }
    }

    if (uiState.isBluetoothOff) {
        Button(
            onClick = onEnableBluetooth,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Enable Bluetooth")
        }
    }

    if (!uiState.bluetoothConnectGranted) {
        Button(
            onClick = onRequestBluetoothPermission,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (uiState.bluetoothPermissionPermanentlyDenied) {
                    "Open settings"
                } else {
                    "Allow Bluetooth"
                }
            )
        }
        Text(
            text = if (uiState.bluetoothPermissionPermanentlyDenied) {
                "Bluetooth permission is blocked. Enable it in system settings, then return to the app."
            } else {
                "Bluetooth permission is required to pair and send keys."
            },
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
    }

    InstructionsSection()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(onClick = onMakeDiscoverable) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.BluetoothSearching,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text("Make Discoverable")
        }
        Button(
            onClick = onForceReset,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        ) {
            Icon(
                imageVector = Icons.Filled.RestartAlt,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text("Reset HID Service")
        }
    }

    if (uiState.bondedDevices.isNotEmpty()) {
        Text(
            "Paired Devices (Bonded):",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth()
        )
        uiState.bondedDevices.forEach { device ->
            val isConnectedDevice = uiState.connectedDeviceAddress == device.address
            BondedDeviceRow(
                device = device,
                isConnected = isConnectedDevice,
                onConnect = { onConnect(device) },
                onDisconnect = { onDisconnect(device) }
            )
        }
    }

    if(BuildConfig.DEBUG){
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onSendTestKey,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                enabled = uiState.isConnected
            ) {
                Text("Send Test 'A' Key")
            }
            Button(
                onClick = onOpenCalculator,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                enabled = uiState.isConnected
            ) {
                Text("Win+R calc")
            }
        }

        Button(
            onClick = onSendSymbolTest,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = uiState.isConnected
        ) {
            Text("Send HID symbols")
        }
        Text(
            text = HidDeviceManager.PRINTABLE_SYMBOL_TEST,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )

        Text(
            text = "Note: Buttons are only active when connected",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
    }
}

@Composable
fun StatusCard(status: String, isConnected: Boolean, isBluetoothOff: Boolean) {
    val tone = statusTone(status, isConnected, isBluetoothOff)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (tone) {
                StatusTone.Connected -> MaterialTheme.colorScheme.primaryContainer
                StatusTone.Ready -> MaterialTheme.colorScheme.tertiaryContainer
                StatusTone.Busy -> MaterialTheme.colorScheme.secondaryContainer
                StatusTone.Idle -> MaterialTheme.colorScheme.surfaceVariant
                StatusTone.Error -> MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Current Status", style = MaterialTheme.typography.labelMedium)
            Text(
                text = status,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = statusColor(status, isConnected, isBluetoothOff)
            )
        }
    }
}

private enum class StatusTone {
    Connected,
    Ready,
    Busy,
    Idle,
    Error
}

private fun statusTone(status: String, isConnected: Boolean, isBluetoothOff: Boolean): StatusTone {
    if (isBluetoothOff || isStatusError(status, isBluetoothOff)) return StatusTone.Error
    if (isConnected || status.startsWith("Connected")) return StatusTone.Connected
    if (status.contains("Ready")) return StatusTone.Ready
    if (
        status.startsWith("Initializing") ||
        status.startsWith("Connecting") ||
        status.startsWith("Disconnecting")
    ) {
        return StatusTone.Busy
    }
    return StatusTone.Idle
}

@Composable
private fun statusColor(status: String, isConnected: Boolean, isBluetoothOff: Boolean): Color {
    return when (statusTone(status, isConnected, isBluetoothOff)) {
        StatusTone.Connected -> MaterialTheme.colorScheme.primary
        StatusTone.Ready -> MaterialTheme.colorScheme.tertiary
        StatusTone.Busy -> MaterialTheme.colorScheme.secondary
        StatusTone.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
        StatusTone.Error -> MaterialTheme.colorScheme.error
    }
}

@Composable
private fun statusIcon(status: String, isConnected: Boolean, isBluetoothOff: Boolean): ImageVector {
    return when {
        isBluetoothOff -> Icons.Filled.BluetoothDisabled
        isConnected -> Icons.Filled.BluetoothConnected
        isStatusError(status, isBluetoothOff) -> Icons.Filled.Error
        else -> Icons.Filled.Bluetooth
    }
}

private fun isStatusError(status: String, isBluetoothOff: Boolean): Boolean {
    if (isBluetoothOff) return true
    val normalized = status.lowercase()
    return normalized.contains("unregistered") ||
        normalized.contains("failed") ||
        normalized.contains("error") ||
        normalized.contains("lost") ||
        normalized.contains("permission") ||
        normalized.contains("service disconnected")
}

@Composable
fun InstructionsSection() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("How to connect:", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("1. Open Bluetooth settings on the Target device (PC/Tablet).")
            Text("2. Look for this phone in the list of available devices.")
            Text("3. Pair with this phone.")
            Text("4. Once paired, the status above should change to 'Connected'.")
        }
    }
}

@Composable
fun HostCommandPresetMenu(
    enabled: Boolean,
    categories: List<PresetCategoryEntity>,
    presets: List<PresetEntity>,
    actionTypes: Map<Long, String>,
    onRunPreset: (PresetEntity) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    IconButton(
        enabled = enabled,
        onClick = { expanded = true }
    ) {
        Icon(
            imageVector = Icons.Filled.MoreVert,
            contentDescription = "Command presets"
        )
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        Text(
            text = "Command Presets",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        HorizontalDivider()

        categories.forEachIndexed { categoryIndex, category ->
            val categoryPresets = presets.filter { it.categoryId == category.id }
            if (categoryPresets.isEmpty()) return@forEachIndexed
            if (categoryIndex > 0) {
                HorizontalDivider()
            }
            Text(
                text = category.title,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 4.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            categoryPresets.forEach { preset ->
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(
                            imageVector = presetActionIcon(actionTypes[preset.id]),
                            contentDescription = null
                        )
                    },
                    text = {
                        Column {
                            Text(preset.title)
                            Text(
                                text = preset.description.ifBlank {
                                    if (preset.isSensitive) "Sensitive preset" else "Preset"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onRunPreset(preset)
                    }
                )
            }
        }
    }
}

@Composable
private fun PresetGroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
private fun PresetsTab(
    uiState: HidUiState,
    onSelectCategory: (Long) -> Unit,
    onRunPreset: (PresetEntity) -> Unit,
    onEditPreset: (PresetEntity) -> Unit,
    onDuplicatePreset: (PresetEntity) -> Unit,
    onDeletePreset: (PresetEntity) -> Unit,
    onResetPreset: (PresetEntity) -> Unit,
    onAddPreset: () -> Unit,
    onCategoryColorChange: (Int) -> Unit,
    onAddCategory: () -> Unit,
    onDeleteCategory: () -> Unit,
    onImportPresets: () -> Unit,
    onExportPresets: () -> Unit,
    onPickGroupColor: () -> Unit,
    viewLayout: PresetViewLayout,
    onViewLayoutSelected: (PresetViewLayout) -> Unit,
    showLayoutFab: Boolean,
    modifier: Modifier = Modifier
) {
    val selectedCategory =
        uiState.presetCategories.firstOrNull { it.id == uiState.selectedPresetCategoryId }
    val groupColorArgb = selectedCategory?.colorArgb ?: 0
    val columns = when (viewLayout) {
        PresetViewLayout.LIST -> GridCells.Fixed(1)
        PresetViewLayout.GRID_2 -> GridCells.Adaptive(160.dp)
        PresetViewLayout.GRID_3 -> GridCells.Adaptive(104.dp)
        PresetViewLayout.GRID_4 -> GridCells.Adaptive(76.dp)
    }

    Box(modifier) {
    LazyVerticalGrid(
        columns = columns,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = if (showLayoutFab) 88.dp else 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
    //Spacer(modifier = Modifier.width(8.dp))
    //Spacer(modifier = Modifier.width(8.dp))

    item(span = { GridItemSpan(maxLineSpan) }) {
    PresetGroupCard {
        FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.Center,
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onAddCategory,
            modifier = Modifier.size(36.dp),
            shape = CircleShape,
            contentPadding = PaddingValues(0.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add Group")
        }

        PresetCategoryChips(
            categories = uiState.presetCategories,
            selectedCategoryId = uiState.selectedPresetCategoryId,
            onSelectCategory = onSelectCategory
        )
    }
    }
    }

    if (uiState.presets.isEmpty()) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = "No presets in this category yet.",
                modifier = Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else if (viewLayout == PresetViewLayout.LIST) {
        items(uiState.presets, key = { it.id }) { preset ->
            PresetListCard(
                preset = preset,
                actionType = uiState.presetActionTypes[preset.id],
                groupColorArgb = groupColorArgb,
                enabled = uiState.isConnected,
                isModified = preset.id in uiState.modifiedPresetIds,
                onRunPreset = { onRunPreset(preset) },
                onEditPreset = { onEditPreset(preset) },
                onDuplicatePreset = { onDuplicatePreset(preset) },
                onDeletePreset = { onDeletePreset(preset) },
                onResetPreset = { onResetPreset(preset) }
            )
        }
    } else {
        items(uiState.presets, key = { it.id }) { preset ->
            PresetGridCard(
                modifier = Modifier.fillMaxWidth(),
                preset = preset,
                actionType = uiState.presetActionTypes[preset.id],
                groupColorArgb = groupColorArgb,
                enabled = uiState.isConnected,
                isModified = preset.id in uiState.modifiedPresetIds,
                onRunPreset = { onRunPreset(preset) },
                onEditPreset = { onEditPreset(preset) },
                onDuplicatePreset = { onDuplicatePreset(preset) },
                onDeletePreset = { onDeletePreset(preset) },
                onResetPreset = { onResetPreset(preset) }
            )
        }
    }
    }

    if (showLayoutFab) {
    PresetLayoutMenu(
        selectedLayout = viewLayout,
        onLayoutSelected = onViewLayoutSelected,
        onImportPresets = onImportPresets,
        onExportPresets = onExportPresets,
        selectedCategory = selectedCategory,
        onPickGroupColor = onPickGroupColor,
        onAddPreset = onAddPreset,
        onDeleteCategory = onDeleteCategory,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(16.dp)
    )
    }
    }
}

@Composable
fun PresetCategoryChips(
    categories: List<PresetCategoryEntity>,
    selectedCategoryId: Long?,
    onSelectCategory: (Long) -> Unit
) {
    categories.chunked(2).forEach { categoryColumn ->
        //Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            categoryColumn.forEach { category ->
                FilterChip(
                    selected = selectedCategoryId == category.id,
                    onClick = { onSelectCategory(category.id) },
                    label = {
                        Text(if (category.isBuiltIn) "${category.title} *" else category.title)
                    }
                )
            }
        //}
    }
}

@Composable
private fun PresetLayoutMenu(
    selectedLayout: PresetViewLayout,
    onLayoutSelected: (PresetViewLayout) -> Unit,
    onImportPresets: () -> Unit,
    onExportPresets: () -> Unit,
    selectedCategory: PresetCategoryEntity?,
    onPickGroupColor: () -> Unit,
    onAddPreset: () -> Unit,
    onDeleteCategory: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        SmallFloatingActionButton(onClick = { expanded = true }) {
            Icon(
                imageVector = presetLayoutIcon(selectedLayout),
                contentDescription = "Presets menu"
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (selectedCategory != null) {
                    Text(
                        text = if (selectedCategory.isBuiltIn) {
                            "${selectedCategory.title} *"
                        } else {
                            selectedCategory.title
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalIconButton(
                            onClick = {
                                expanded = false
                                onPickGroupColor()
                            },
                            colors = groupColorButtonColors(selectedCategory.colorArgb)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Palette,
                                contentDescription = "Group color"
                            )
                        }
                        FilledTonalIconButton(
                            onClick = {
                                expanded = false
                                onAddPreset()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Add preset"
                            )
                        }
                        FilledTonalIconButton(
                            onClick = {
                                expanded = false
                                onDeleteCategory()
                            },
                            enabled = !selectedCategory.isBuiltIn,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Delete group"
                            )
                        }
                    }
                }
                Text(
                    text = "View",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PresetViewLayout.entries.forEach { layout ->
                        FilterChip(
                            selected = layout == selectedLayout,
                            onClick = {
                                expanded = false
                                onLayoutSelected(layout)
                            },
                            label = { Text(presetLayoutLabel(layout)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = presetLayoutIcon(layout),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }
                }
                Text(
                    text = "All presets",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = {
                            expanded = false
                            onImportPresets()
                        },
                        label = { Text("Import") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.FileDownload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                    AssistChip(
                        onClick = {
                            expanded = false
                            onExportPresets()
                        },
                        label = { Text("Export") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.FileUpload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }
        }
    }
}

private fun presetLayoutLabel(layout: PresetViewLayout): String {
    return when (layout) {
        PresetViewLayout.LIST -> "List"
        PresetViewLayout.GRID_2 -> "×2"
        PresetViewLayout.GRID_3 -> "×3"
        PresetViewLayout.GRID_4 -> "×4"
    }
}

private fun presetLayoutIcon(layout: PresetViewLayout): ImageVector {
    return when (layout) {
        PresetViewLayout.LIST -> Icons.Filled.ViewList
        PresetViewLayout.GRID_2 -> Icons.Filled.ViewModule
        PresetViewLayout.GRID_3 -> Icons.Filled.Apps
        PresetViewLayout.GRID_4 -> Icons.Filled.GridView
    }
}

@Composable
fun PresetGridCard(
    preset: PresetEntity,
    actionType: String?,
    groupColorArgb: Int,
    enabled: Boolean,
    isModified: Boolean,
    onRunPreset: () -> Unit,
    onEditPreset: () -> Unit,
    onDuplicatePreset: () -> Unit,
    onDeletePreset: () -> Unit,
    onResetPreset: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val contentColor = if (preset.isSensitive) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = modifier
            .aspectRatio(1f)
            .clickable(enabled = enabled, onClick = onRunPreset),
        colors = CardDefaults.cardColors(
            containerColor = presetItemContainer(preset.isSensitive, groupColorArgb)
        )
    ) {
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)) {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "Preset actions",
                    modifier = Modifier.size(18.dp),
                    tint = contentColor
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Run") },
                    enabled = enabled,
                    onClick = {
                        menuExpanded = false
                        onRunPreset()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Edit") },
                    // enabled = !preset.isBuiltIn,
                    onClick = {
                        menuExpanded = false
                        onEditPreset()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Copy") },
                    onClick = {
                        menuExpanded = false
                        onDuplicatePreset()
                    }
                )
                if (isModified) {
                    DropdownMenuItem(
                        text = { Text("Reset to default") },
                        onClick = {
                            menuExpanded = false
                            onResetPreset()
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Delete") },
                    enabled = !preset.isBuiltIn,
                    onClick = {
                        menuExpanded = false
                        onDeletePreset()
                    }
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = presetActionIcon(actionType),
                    contentDescription = presetActionLabel(actionType),
                    modifier = Modifier.size(28.dp),
                    tint = presetItemIconTint(preset.isSensitive, groupColorArgb)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = preset.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = contentColor
                )
                if (preset.isSensitive) {
                    Text(
                        text = "Sensitive",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                if (isModified) {
                    PresetModifiedLabel()
                }
            }
        }
    }
}

@Composable
private fun PresetModifiedLabel() {
    Text(
        text = "Modified",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.tertiary
    )
}

@Composable
fun PresetListCard(
    preset: PresetEntity,
    actionType: String?,
    groupColorArgb: Int,
    enabled: Boolean,
    isModified: Boolean,
    onRunPreset: () -> Unit,
    onEditPreset: () -> Unit,
    onDuplicatePreset: () -> Unit,
    onDeletePreset: () -> Unit,
    onResetPreset: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = presetItemContainer(preset.isSensitive, groupColorArgb)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = presetActionIcon(actionType),
                    contentDescription = presetActionLabel(actionType),
                    modifier = Modifier.size(20.dp),
                    tint = presetItemIconTint(preset.isSensitive, groupColorArgb)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = preset.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (preset.description.isNotBlank()) {
                        Text(
                            text = preset.description,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        presetActionLabel(actionType),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (preset.isSensitive) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    if (preset.isSensitive) {
                        Text(
                            "Sensitive",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    if (isModified) {
                        PresetModifiedLabel()
                    }
                }
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                FilledTonalButton(
                    enabled = enabled,
                    onClick = onRunPreset,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Text("Run")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (isModified) {
                        IconButton(
                            onClick = onResetPreset,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.RestartAlt,
                                contentDescription = "Reset to default",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    IconButton(
                        // enabled = !preset.isBuiltIn,
                        onClick = onEditPreset,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Edit preset",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onDuplicatePreset,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "Copy preset",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        enabled = !preset.isBuiltIn,
                        onClick = onDeletePreset,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete preset",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryEditorDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val defaultGroupName = remember { generateDefaultGroupName() }
    var title by remember { mutableStateOf(defaultGroupName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Group") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Group title") },
                singleLine = true
            )
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank(),
                onClick = { onSave(title) }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DeleteCategoryDialog(
    category: PresetCategoryEntity?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val canDelete = category != null && !category.isBuiltIn

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Group?") },
        text = {
            Text(
                if (canDelete) {
                    "Delete `${category?.title}` and all presets inside it?"
                } else {
                    "Built-in groups cannot be deleted."
                }
            )
        },
        confirmButton = {
            Button(
                enabled = canDelete,
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ResetPresetDialog(
    preset: PresetEntity,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reset to default?") },
        text = {
            Text("`${preset.title}` gets back its built-in title, description and action. Your changes are lost.")
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Reset")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DeletePresetDialog(
    preset: PresetEntity,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Preset?") },
        text = {
            Text("Delete `${preset.title}` and all actions inside it?")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ShortcutPicker(
    draft: ShortcutDraft,
    onDraftChange: (ShortcutDraft) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedKeyGroup by remember { mutableStateOf(draft.key?.group ?: ShortcutKeyGroup.COMMON) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Modifiers", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = draft.ctrl,
                onClick = { onDraftChange(draft.copy(ctrl = !draft.ctrl)) },
                label = { Text("Ctrl") }
            )
            FilterChip(
                selected = draft.shift,
                onClick = { onDraftChange(draft.copy(shift = !draft.shift)) },
                label = { Text("Shift") }
            )
            FilterChip(
                selected = draft.alt,
                onClick = { onDraftChange(draft.copy(alt = !draft.alt)) },
                label = { Text("Alt") }
            )
            FilterChip(
                selected = draft.win,
                onClick = { onDraftChange(draft.copy(win = !draft.win)) },
                label = { Text("Win") }
            )
            FilterChip(
                selected = draft.rctrl,
                onClick = { onDraftChange(draft.copy(rctrl = !draft.rctrl)) },
                label = { Text("RCtrl") }
            )
            FilterChip(
                selected = draft.rshift,
                onClick = { onDraftChange(draft.copy(rshift = !draft.rshift)) },
                label = { Text("RShift") }
            )
            FilterChip(
                selected = draft.ralt,
                onClick = { onDraftChange(draft.copy(ralt = !draft.ralt)) },
                label = { Text("AltGr") }
            )
            FilterChip(
                selected = draft.rwin,
                onClick = { onDraftChange(draft.copy(rwin = !draft.rwin)) },
                label = { Text("RWin") }
            )
        }
        Text(
            text = draft.displayLabel(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text("Key", style = MaterialTheme.typography.labelMedium)
        PrimaryScrollableTabRow(
            selectedTabIndex = ShortcutKeyGroup.entries.indexOf(selectedKeyGroup),
            edgePadding = 0.dp
        ) {
            ShortcutKeyGroup.entries.forEach { group ->
                Tab(
                    selected = selectedKeyGroup == group,
                    onClick = { selectedKeyGroup = group },
                    text = { Text(group.title) }
                )
            }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ShortcutKeys.forGroup(selectedKeyGroup).forEach { keyOption ->
                FilterChip(
                    selected = draft.key == keyOption,
                    onClick = { onDraftChange(draft.copy(key = keyOption)) },
                    label = { Text(keyOption.label) }
                )
            }
        }
    }
}

@Composable
fun PresetEditorDialog(
    categories: List<PresetCategoryEntity>,
    selectedCategoryId: Long?,
    titleText: String = "Add Preset",
    confirmText: String = "Save",
    initialTitle: String? = null,
    initialDescription: String? = null,
    initialActionType: String = PresetActionCodec.TYPE_RUN_WINDOWS_COMMAND,
    initialValue: String = "",
    initialLogin: String = "",
    initialPassword: String = "",
    initialSensitive: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, Boolean) -> Unit
) {
    val defaultPresetName = remember { generateDefaultPresetName() }
    var title by remember(initialTitle) { mutableStateOf(initialTitle ?: defaultPresetName) }
    var description by remember(initialDescription) {
        mutableStateOf(
            initialDescription ?: defaultPresetName
        )
    }
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    var login by remember(initialLogin) { mutableStateOf(initialLogin) }
    var password by remember(initialPassword) { mutableStateOf(initialPassword) }
    var isSensitive by remember(initialSensitive) { mutableStateOf(initialSensitive) }
    var selectedActionType by remember(initialActionType) { mutableStateOf(initialActionType) }
    var menuExpanded by remember { mutableStateOf(false) }
    val isCredential = selectedActionType == PresetActionCodec.TYPE_CREDENTIAL
    val isKeyboardShortcut = selectedActionType == PresetActionCodec.TYPE_KEYBOARD_SHORTCUT
    var shortcutDraft by remember(initialActionType, initialValue) {
        mutableStateOf(
            if (initialActionType == PresetActionCodec.TYPE_KEYBOARD_SHORTCUT ||
                initialActionType == PresetActionCodec.TYPE_KEY_COMBO ||
                initialActionType == PresetActionCodec.TYPE_KEY_PRESS
            ) {
                PresetShortcutDraft.fromShortcut(initialValue)
            } else {
                ShortcutDraft()
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titleText) },
        text = {
            val scrollState = rememberScrollState()
            val maxDialogHeight = (LocalConfiguration.current.screenHeightDp * 0.55f).dp
            Column(
                modifier = Modifier
                    .heightIn(max = maxDialogHeight)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = categories.firstOrNull { it.id == selectedCategoryId }?.title
                        ?: "No category selected",
                    style = MaterialTheme.typography.labelMedium
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    singleLine = true
                )
                Box {
                    OutlinedButton(onClick = { menuExpanded = true }) {
                        Text(actionTypeLabel(selectedActionType))
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        editorActionTypes.forEach { actionType ->
                            DropdownMenuItem(
                                text = { Text(actionTypeLabel(actionType)) },
                                onClick = {
                                    selectedActionType = actionType
                                    if (actionType == PresetActionCodec.TYPE_KEYBOARD_SHORTCUT &&
                                        selectedActionType != PresetActionCodec.TYPE_KEYBOARD_SHORTCUT
                                    ) {
                                        shortcutDraft = ShortcutDraft()
                                    }
                                    isSensitive =
                                        actionType == PresetActionCodec.TYPE_TYPE_SENSITIVE_TEXT ||
                                                actionType == PresetActionCodec.TYPE_CREDENTIAL ||
                                                isSensitive
                                    menuExpanded = false
                                }
                            )
                        }
                    }
                }
                if (isCredential) {
                    OutlinedTextField(
                        value = login,
                        onValueChange = { login = it },
                        label = { Text("Login") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true
                    )
                } else if (isKeyboardShortcut) {
                    ShortcutPicker(
                        draft = shortcutDraft,
                        onDraftChange = { shortcutDraft = it }
                    )
                } else {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        label = { Text("Value") },
                        minLines = 2
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isSensitive || isCredential,
                        onCheckedChange = { isSensitive = it },
                        enabled = !isCredential
                    )
                    Text("Sensitive / require confirmation")
                }
            }
        },
        confirmButton = {
            val payload = when {
                isCredential -> JSONObject()
                    .put("login", login)
                    .put("password", password)
                    .toString()

                isKeyboardShortcut -> shortcutDraft.toShortcutString()
                else -> value
            }
            Button(
                enabled = title.isNotBlank() &&
                        selectedCategoryId != null &&
                        when {
                            isCredential -> login.isNotBlank() && password.isNotBlank()
                            isKeyboardShortcut -> shortcutDraft.isValid
                            else -> value.isNotBlank()
                        },
                onClick = {
                    onSave(
                        title,
                        description,
                        selectedActionType,
                        payload,
                        isSensitive || isCredential
                    )
                }
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SensitivePresetConfirmationDialog(
    preset: PresetEntity,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Run Sensitive Preset?") },
        text = {
            Text("`${preset.title}` may type sensitive data stored in RoomDB as plain text.")
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Run")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ExportPresetsDialog(
    onDismiss: () -> Unit,
    onExportSafeOnly: () -> Unit,
    onExportIncludingSensitive: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export all presets") },
        text = {
            Text("Sensitive presets may contain plaintext secrets. Export them only if you trust the destination file.")
        },
        confirmButton = {
            Button(onClick = onExportSafeOnly) {
                Text("Safe only")
            }
        },
        dismissButton = {
            TextButton(onClick = onExportIncludingSensitive) {
                Text("Include sensitive")
            }
        }
    )
}

@SuppressLint("MissingPermission")
@Composable
fun BondedDeviceRow(
    modifier: Modifier = Modifier,
    device: BluetoothDevice,
    isConnected: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(device.name ?: "Unknown Device", fontWeight = FontWeight.Bold)
                Text(device.address, style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = if (isConnected) onDisconnect else onConnect,
                colors = if (isConnected) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                Icon(
                    imageVector = if (isConnected) Icons.Filled.BluetoothDisabled else Icons.Filled.BluetoothConnected,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isConnected) "Disconnect" else "Connect")
            }
        }
    }
}

private val groupColorSwatches = intArrayOf(
    0xFFE53935.toInt(),
    0xFFFB8C00.toInt(),
    0xFFFDD835.toInt(),
    0xFF43A047.toInt(),
    0xFF00ACC1.toInt(),
    0xFF1E88E5.toInt(),
    0xFF5E35B1.toInt(),
    0xFF8E24AA.toInt(),
    0xFFD81B60.toInt(),
    0xFF6D4C41.toInt()
)

@Composable
private fun groupColorButtonColors(colorArgb: Int): IconButtonColors {
    if (colorArgb == 0) return IconButtonDefaults.filledTonalIconButtonColors()
    val tint = Color(colorArgb)
    return IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = tint,
        contentColor = contentOn(tint)
    )
}

@Composable
private fun presetItemContainer(isSensitive: Boolean, groupColorArgb: Int): Color {
    val base = if (isSensitive) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    if (groupColorArgb == 0) return base
    return lerp(base, Color(groupColorArgb), 0.42f)
}

@Composable
private fun presetItemIconTint(isSensitive: Boolean, groupColorArgb: Int): Color {
    if (isSensitive) return MaterialTheme.colorScheme.onErrorContainer
    if (groupColorArgb == 0) return MaterialTheme.colorScheme.primary
    return Color(groupColorArgb)
}

private fun contentOn(color: Color): Color {
    val luminance = color.red * 0.299f + color.green * 0.587f + color.blue * 0.114f
    return if (luminance > 0.6f) Color.Black else Color.White
}

@Composable
private fun GroupColorDialog(
    selectedArgb: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Group color") },
        text = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GroupColorSwatch(
                    colorArgb = 0,
                    selected = selectedArgb == 0,
                    onClick = { onSelect(0) }
                )
                groupColorSwatches.forEach { colorArgb ->
                    GroupColorSwatch(
                        colorArgb = colorArgb,
                        selected = selectedArgb == colorArgb,
                        onClick = { onSelect(colorArgb) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun GroupColorSwatch(
    colorArgb: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    val color = if (colorArgb == 0) MaterialTheme.colorScheme.surfaceContainerHigh else Color(colorArgb)
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(color, CircleShape)
            .clickable(onClick = onClick)
            .then(
                if (selected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                } else {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (colorArgb == 0) {
            Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private enum class PresetViewLayout {
    LIST,
    GRID_2,
    GRID_3,
    GRID_4
}

private val editorActionTypes = listOf(
    PresetActionCodec.TYPE_RUN_WINDOWS_COMMAND,
    PresetActionCodec.TYPE_KEYBOARD_SHORTCUT,
    PresetActionCodec.TYPE_TYPE_TEXT,
    PresetActionCodec.TYPE_TYPE_SENSITIVE_TEXT,
    PresetActionCodec.TYPE_CREDENTIAL
)

private fun actionTypeLabel(actionType: String): String {
    return when (actionType) {
        PresetActionCodec.TYPE_KEYBOARD_SHORTCUT -> "Keyboard shortcut"
        PresetActionCodec.TYPE_TYPE_TEXT -> "Type text"
        PresetActionCodec.TYPE_TYPE_SENSITIVE_TEXT -> "Type sensitive text"
        PresetActionCodec.TYPE_CREDENTIAL -> "Credential"
        else -> "Run Windows command"
    }
}

private fun presetActionIcon(actionType: String?): ImageVector {
    return when (actionType) {
        PresetActionCodec.TYPE_TYPE_TEXT -> Icons.Filled.TextFields
        PresetActionCodec.TYPE_TYPE_SENSITIVE_TEXT -> Icons.Filled.Lock
        PresetActionCodec.TYPE_CREDENTIAL -> Icons.Filled.Lock
        PresetActionCodec.TYPE_KEY_COMBO,
        PresetActionCodec.TYPE_KEY_PRESS -> Icons.Filled.Keyboard

        PresetActionCodec.TYPE_DELAY -> Icons.Filled.Schedule
        else -> Icons.Filled.Apps
    }
}

private fun presetActionLabel(actionType: String?): String {
    return when (actionType) {
        PresetActionCodec.TYPE_TYPE_TEXT -> "Text input"
        PresetActionCodec.TYPE_TYPE_SENSITIVE_TEXT -> "Sensitive text"
        PresetActionCodec.TYPE_CREDENTIAL -> "Credential"
        PresetActionCodec.TYPE_KEY_COMBO -> "Key combo"
        PresetActionCodec.TYPE_KEY_PRESS -> "Key press"
        PresetActionCodec.TYPE_DELAY -> "Delay"
        else -> "Windows command"
    }
}

private fun generateDefaultPresetName(): String {
    return "Preset-${(System.currentTimeMillis() % 1000).toString().padStart(3, '0')}"
}

private fun generateDefaultGroupName(): String {
    return "Group-${(System.currentTimeMillis() % 1000).toString().padStart(3, '0')}"
}
