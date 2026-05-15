package com.walhalla.bluetoothhiddevice

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.walhalla.bluetoothhiddevice.ui.theme.BluetoothHIDDeviceTheme

class MainActivity : ComponentActivity() {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Bluetooth enabled
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            checkBluetoothState()
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkPermissions()
        enableEdgeToEdge()

        setContent {
            BluetoothHIDDeviceTheme {
                val context = LocalContext.current
                val hidManager = remember { HidDeviceManager(context) }
                var status by remember { 
                    mutableStateOf(if (bluetoothAdapter?.isEnabled == true) "Initializing..." else "Bluetooth is OFF") 
                }

                LaunchedEffect(hidManager) {
                    hidManager.setStatusListener { newStatus ->
                        status = newStatus
                    }
                }

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
                            .fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        StatusCard(status)

                        if (status == "Bluetooth is OFF") {
                            Button(
                                onClick = { checkBluetoothState() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Enable Bluetooth")
                            }
                        }

                        InstructionsSection()

                        Spacer(modifier = Modifier.weight(1f))

                        Button(
                            onClick = { hidManager.sendTestString("Hello") },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            enabled = status.contains("Connected")
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
        }
    }

    private fun checkBluetoothState() {
        if (bluetoothAdapter == null) return
        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
        }
    }

    @Composable
    fun StatusCard(status: String) {
        val isConnected = status.contains("Connected")
        val isError = status.contains("OFF") || status.contains("Unregistered")
        
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

    private fun checkPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            checkBluetoothState()
        }
    }
}
