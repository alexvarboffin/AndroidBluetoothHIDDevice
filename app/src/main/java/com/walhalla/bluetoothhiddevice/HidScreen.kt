package com.walhalla.bluetoothhiddevice

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HidScreen(
    viewModel: HidViewModel,
    onEnableBluetooth: () -> Unit,
    onMakeDiscoverable: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(title = { Text("Bluetooth HID Device") })
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
                onClick = { viewModel.forceReset() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
            ) {
                Text("Reset HID Service")
            }

            // Bonded Devices List
            if (uiState.bondedDevices.isNotEmpty()) {
                Text(
                    "Paired Devices (Bonded):",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.Start)
                )
                uiState.bondedDevices.forEach { device ->
                    BondedDeviceRow(device) { viewModel.connect(device) }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { viewModel.sendText("A") },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = uiState.isConnected
            ) {
                Text("Send Test 'A' Key")
            }

            Text(
                text = "Note: Button is only active when connected",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
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

@SuppressLint("MissingPermission")
@Composable
fun BondedDeviceRow(device: android.bluetooth.BluetoothDevice, onConnect: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                @SuppressLint("MissingPermission")
                Text(device.name ?: "Unknown Device", fontWeight = FontWeight.Bold)
                Text(device.address, style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = onConnect) {
                Text("Connect")
            }
        }
    }
}
