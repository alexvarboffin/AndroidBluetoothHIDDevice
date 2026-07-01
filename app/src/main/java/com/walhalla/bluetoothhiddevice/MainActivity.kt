package com.walhalla.bluetoothhiddevice

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.text.Layout
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
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


                //12
//                val radiusX = 100f
//                val radiusY = 100f
//
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//                    val blurEffect: RenderEffect = android.graphics.RenderEffect
//                        .createBlurEffect(radiusX, radiusY, Shader.TileMode.MIRROR)
//                        .asComposeRenderEffect()
//                    Box(modifier = Modifier
//                        .background(Color.Red)
//                        .padding(100.dp)
//                        .graphicsLayer {
//                            renderEffect = blurEffect
//                        }) {
//                        Image(
//                            painter = painterResource(R.drawable.ic_launcher_background),
//                            contentDescription = null
//                        )
//                    }
//                } else {
//                    //TODO("VERSION.SDK_INT < S")
//                }
//
//
//                Box(
//                    modifier = Modifier
//                        .fillMaxSize()
//                        .padding(bottom = 24.dp),
//                    contentAlignment = Alignment.BottomCenter
//                ) {
//                    CustomBottomNavigation(modifier = Modifier.fillMaxWidth())
//                }


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

    override fun onStop() {
        super.onStop()
        viewModel.keepConnectionAliveInBackground()
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
            buildList {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }.toTypedArray()
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

@Composable
fun CustomBottomNavigation(modifier: Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .paint(
                painter = painterResource(R.drawable.bottom_navigation),
                contentScale = ContentScale.FillHeight
            )
            .padding(horizontal = 40.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(Icons.Filled.CalendarToday, Icons.Filled.Groups).map { image ->
            IconButton(onClick = {}) {
                Icon(imageVector = image, contentDescription = null, tint = Color.White)
            }
        }
    }
}
