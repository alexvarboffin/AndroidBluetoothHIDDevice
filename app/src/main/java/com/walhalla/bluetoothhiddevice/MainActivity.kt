package com.walhalla.bluetoothhiddevice

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.walhalla.bluetoothhiddevice.ui.theme.BluetoothHIDDeviceTheme

class MainActivity : ComponentActivity() {

    private val viewModel: HidViewModel by viewModels()
    private var pendingPresetExportJson: String? = null

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            viewModel.refreshBondedDevices()
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

    private val importPresetsLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        contentResolver.openInputStream(uri)?.use { inputStream ->
            val json = inputStream.bufferedReader().use { it.readText() }
            viewModel.importPresetsJson(json)
        }
    }

    private val exportPresetsLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val json = pendingPresetExportJson ?: return@registerForActivityResult
        pendingPresetExportJson = null
        if (uri == null) return@registerForActivityResult
        contentResolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.write(json.toByteArray())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        checkPermissions()
        enableEdgeToEdge()

        setContent {
            BluetoothHIDDeviceTheme {
                HidScreen(
                    viewModel = viewModel,
                    onEnableBluetooth = { checkBluetoothState() },
                    onMakeDiscoverable = { viewModel.makeDiscoverable(this) },
                    onImportPresets = { importPresetsLauncher.launch(arrayOf("application/json")) },
                    onExportPresets = { includeSensitive ->
                        viewModel.exportPresetsJson(includeSensitive) { json ->
                            pendingPresetExportJson = json
                            exportPresetsLauncher.launch("hid-presets.json")
                        }
                    }
                )
            }
        }

        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.resume()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { sharedText ->
                if (viewModel.uiState.value.isConnected) {
                    viewModel.sendText(sharedText)
                }
            }
        }
    }

    private fun checkBluetoothState() {
        if (!viewModel.checkBluetoothEnabled()) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
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
