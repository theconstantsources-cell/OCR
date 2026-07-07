package com.scanhid.ocr.ui

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scanhid.ocr.MainViewModel
import com.scanhid.ocr.bluetooth.HidConnectionState

@Composable
fun ConnectionScreen(viewModel: MainViewModel) {
    val connectionState by viewModel.hidConnectionState.collectAsState()
    val connectedDeviceName by viewModel.connectedDeviceName.collectAsState()

    val discoverableLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { /* result ignored: we react to the HID connection callback instead */ }

    Scaffold(topBar = { ScanHidTopBar(subtitle = "PC connection") }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Pair with a PC", style = MaterialTheme.typography.titleLarge)

            Text(
                modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
                text = when (connectionState) {
                    HidConnectionState.UNREGISTERED ->
                        "Starting Bluetooth keyboard mode..."
                    HidConnectionState.REGISTERED_WAITING_FOR_PC ->
                        "Ready to pair. On the PC, open Bluetooth settings, " +
                            "add a device, and select this phone the same way you'd pair a wireless keyboard."
                    HidConnectionState.CONNECTED ->
                        "Connected to: ${connectedDeviceName ?: "PC"}"
                },
            )

            if (connectionState != HidConnectionState.CONNECTED) {
                Button(onClick = {
                    val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                        putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
                    }
                    discoverableLauncher.launch(intent)
                }) {
                    Text("Make discoverable (5 min)")
                }
            }

            OutlinedButton(
                modifier = Modifier.padding(top = 16.dp),
                onClick = viewModel::goToCaptureScreen,
            ) {
                Text("Back to scanning")
            }
        }
    }
}
