package com.walhalla.bluetoothhiddevice

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.walhalla.bluetoothhiddevice.presets.PresetActionCodec
import com.walhalla.bluetoothhiddevice.presets.PresetCategoryEntity
import com.walhalla.bluetoothhiddevice.presets.PresetEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HidScreen(
    viewModel: HidViewModel,
    onEnableBluetooth: () -> Unit,
    onMakeDiscoverable: () -> Unit,
    onImportPresets: () -> Unit,
    onExportPresets: (includeSensitive: Boolean) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    var selectedTab by remember { mutableStateOf(0) }
    var showPresetEditor by remember { mutableStateOf(false) }
    var showCategoryEditor by remember { mutableStateOf(false) }
    var showDeleteCategoryDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Bluetooth HID Device") },
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
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatusCard(uiState.status, uiState.isConnected, uiState.isBluetoothOff)

            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Devices") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Presets") }
                )
            }

            if (selectedTab == 1) {
                PresetsTab(
                    uiState = uiState,
                    onSelectCategory = viewModel::selectPresetCategory,
                    onRunPreset = viewModel::requestRunPreset,
                    onAddPreset = { showPresetEditor = true },
                    onAddCategory = { showCategoryEditor = true },
                    onDeleteCategory = { showDeleteCategoryDialog = true },
                    onImportPresets = onImportPresets,
                    onExportPresets = { showExportDialog = true }
                )
                return@Column
            }

            DevicesTab(
                uiState = uiState,
                onEnableBluetooth = onEnableBluetooth,
                onMakeDiscoverable = onMakeDiscoverable,
                onTogglePersistence = viewModel::togglePersistence,
                onForceReset = viewModel::forceReset,
                onSendTestKey = { viewModel.sendText("A") },
                onOpenCalculator = viewModel::openCalculatorOnHost,
                onConnect = viewModel::connect,
                onDisconnect = viewModel::disconnect
            )
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

    uiState.pendingSensitivePreset?.let { preset ->
        SensitivePresetConfirmationDialog(
            preset = preset,
            onDismiss = viewModel::dismissPendingPreset,
            onConfirm = viewModel::confirmPendingPreset
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
fun DevicesTab(
    uiState: HidUiState,
    onEnableBluetooth: () -> Unit,
    onMakeDiscoverable: () -> Unit,
    onTogglePersistence: (Boolean) -> Unit,
    onForceReset: () -> Unit,
    onSendTestKey: () -> Unit,
    onOpenCalculator: () -> Unit,
    onConnect: (BluetoothDevice) -> Unit,
    onDisconnect: (BluetoothDevice) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Persistent Mode", fontWeight = FontWeight.Bold)
                Text("Keep connection alive in background", style = MaterialTheme.typography.bodySmall)
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

    InstructionsSection()

    Button(
        onClick = onMakeDiscoverable,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Make Discoverable")
    }

    Button(
        onClick = onForceReset,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Text("Reset HID Service")
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

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onSendTestKey,
            modifier = Modifier.weight(1f).height(56.dp),
            enabled = uiState.isConnected
        ) {
            Text("Send Test 'A' Key")
        }
        Button(
            onClick = onOpenCalculator,
            modifier = Modifier.weight(1f).height(56.dp),
            enabled = uiState.isConnected
        ) {
            Text("Win+R calc")
        }
    }

    Text(
        text = "Note: Buttons are only active when connected",
        style = MaterialTheme.typography.bodySmall,
        color = Color.Gray
    )
}

@Composable
fun StatusCard(status: String, isConnected: Boolean, isBluetoothOff: Boolean) {
    val isError = isBluetoothOff || status.contains("Unregistered") || status.contains("FAILED")

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isConnected -> Color(0xFFE8F5E9)
                isError -> Color(0xFFFFEBEE)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Current Status", style = MaterialTheme.typography.labelMedium)
            Text(
                text = status,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = when {
                    isConnected -> Color(0xFF2E7D32)
                    isError -> Color(0xFFC62828)
                    else -> Color.Unspecified
                }
            )
        }
    }
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
fun PresetsTab(
    uiState: HidUiState,
    onSelectCategory: (Long) -> Unit,
    onRunPreset: (PresetEntity) -> Unit,
    onAddPreset: () -> Unit,
    onAddCategory: () -> Unit,
    onDeleteCategory: () -> Unit,
    onImportPresets: () -> Unit,
    onExportPresets: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onImportPresets,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Filled.FileUpload, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Import")
        }
        OutlinedButton(
            onClick = onExportPresets,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Filled.FileDownload, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Export")
        }
        Button(
            onClick = onAddPreset,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add")
        }
    }

    PresetCategoryChips(
        categories = uiState.presetCategories,
        selectedCategoryId = uiState.selectedPresetCategoryId,
        onSelectCategory = onSelectCategory
    )

    val selectedCategory = uiState.presetCategories.firstOrNull { it.id == uiState.selectedPresetCategoryId }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onAddCategory,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add group")
        }
        OutlinedButton(
            enabled = selectedCategory?.isBuiltIn == false,
            onClick = onDeleteCategory,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(Icons.Filled.Delete, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Delete group")
        }
    }

    if (uiState.presets.isEmpty()) {
        Text(
            text = "No presets in this category yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        uiState.presets.forEach { preset ->
            PresetCard(
                preset = preset,
                actionType = uiState.presetActionTypes[preset.id],
                enabled = uiState.isConnected,
                onRunPreset = { onRunPreset(preset) }
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categories.chunked(2).forEach { categoryColumn ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                categoryColumn.forEach { category ->
                    FilterChip(
                        selected = selectedCategoryId == category.id,
                        onClick = { onSelectCategory(category.id) },
                        label = {
                            Text(if (category.isBuiltIn) "${category.title} *" else category.title)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PresetCard(
    preset: PresetEntity,
    actionType: String?,
    enabled: Boolean,
    onRunPreset: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (preset.isSensitive) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
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
                    tint = if (preset.isSensitive) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(preset.title, fontWeight = FontWeight.Bold)
                    if (preset.description.isNotBlank()) {
                        Text(preset.description, style = MaterialTheme.typography.bodySmall)
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
                            "Sensitive: confirmation required",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
            Button(
                enabled = enabled,
                onClick = onRunPreset
            ) {
                Text("Run")
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
fun PresetEditorDialog(
    categories: List<PresetCategoryEntity>,
    selectedCategoryId: Long?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, Boolean) -> Unit
) {
    val defaultPresetName = remember { generateDefaultPresetName() }
    var title by remember { mutableStateOf(defaultPresetName) }
    var description by remember { mutableStateOf(defaultPresetName) }
    var value by remember { mutableStateOf("") }
    var isSensitive by remember { mutableStateOf(false) }
    var selectedActionType by remember { mutableStateOf(PresetActionCodec.TYPE_RUN_WINDOWS_COMMAND) }
    var menuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Preset") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = categories.firstOrNull { it.id == selectedCategoryId }?.title ?: "No category selected",
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
                                    isSensitive = actionType == PresetActionCodec.TYPE_TYPE_SENSITIVE_TEXT || isSensitive
                                    menuExpanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Value") },
                    minLines = 2
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isSensitive,
                        onCheckedChange = { isSensitive = it }
                    )
                    Text("Sensitive / require confirmation")
                }
            }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank() && value.isNotBlank() && selectedCategoryId != null,
                onClick = { onSave(title, description, selectedActionType, value, isSensitive) }
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
        title = { Text("Export Presets") },
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
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
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

private val editorActionTypes = listOf(
    PresetActionCodec.TYPE_RUN_WINDOWS_COMMAND,
    PresetActionCodec.TYPE_TYPE_TEXT,
    PresetActionCodec.TYPE_TYPE_SENSITIVE_TEXT
)

private fun actionTypeLabel(actionType: String): String {
    return when (actionType) {
        PresetActionCodec.TYPE_TYPE_TEXT -> "Type text"
        PresetActionCodec.TYPE_TYPE_SENSITIVE_TEXT -> "Type sensitive text"
        else -> "Run Windows command"
    }
}

private fun presetActionIcon(actionType: String?): ImageVector {
    return when (actionType) {
        PresetActionCodec.TYPE_TYPE_TEXT -> Icons.Filled.TextFields
        PresetActionCodec.TYPE_TYPE_SENSITIVE_TEXT -> Icons.Filled.Lock
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
