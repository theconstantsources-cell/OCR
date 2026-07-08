package com.scanhid.ocr.ui

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scanhid.ocr.MainViewModel
import com.scanhid.ocr.R
import com.scanhid.ocr.bluetooth.PairedDeviceInfo
import com.scanhid.ocr.bluetooth.PcConnectionState
import com.scanhid.ocr.ui.theme.SuzukiRed

@Composable
fun ConnectionScreen(viewModel: MainViewModel) {
    val connectionState by viewModel.pcConnectionState.collectAsState()
    val connectedDeviceName by viewModel.connectedDeviceName.collectAsState()
    val connectionError by viewModel.connectionError.collectAsState()
    val bondedDevices by viewModel.bondedDevices.collectAsState()

    LaunchedEffect(Unit) { viewModel.refreshBondedDevices() }

    val discoverableLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { viewModel.refreshBondedDevices() }

    Scaffold(topBar = { ScanHidTopBar(subtitle = "PC connection") }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.suzuki_logo),
                contentDescription = "Suzuki",
                modifier = Modifier.height(36.dp).padding(bottom = 16.dp),
            )

            Text("Connect to a PC", style = MaterialTheme.typography.titleLarge)

            Text(
                modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
                text = when (connectionState) {
                    PcConnectionState.DISCONNECTED -> "Not connected to a PC."
                    PcConnectionState.CONNECTING -> "Connecting..."
                    PcConnectionState.CONNECTED -> "Connected to: ${connectedDeviceName ?: "PC"}"
                },
            )

            if (connectionError != null) {
                Text(
                    connectionError.orEmpty(),
                    color = SuzukiRed,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            Button(
                onClick = {
                    val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                        putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
                    }
                    discoverableLauncher.launch(intent)
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text("Make discoverable to pair (5 min)")
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "PAIRED DEVICES",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = viewModel::refreshBondedDevices) {
                    Text("Refresh", fontSize = 12.sp)
                }
            }

            if (bondedDevices.isEmpty()) {
                Text(
                    "No paired devices yet. Tap \"Make discoverable\" above, then add this " +
                        "device from the PC's Bluetooth settings first.",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    bondedDevices.forEach { device ->
                        PairedDeviceRow(
                            device = device,
                            isConnected = connectionState == PcConnectionState.CONNECTED &&
                                connectedDeviceName == device.name,
                            isConnecting = connectionState == PcConnectionState.CONNECTING,
                            onConnect = { viewModel.connectToDevice(device.address) },
                        )
                    }
                }
            }

            OutlinedButton(
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 4.dp),
                onClick = viewModel::goToCaptureScreen,
            ) {
                Text("Back to scanning")
            }
        }
    }
}

@Composable
private fun PairedDeviceRow(
    device: PairedDeviceInfo,
    isConnected: Boolean,
    isConnecting: Boolean,
    onConnect: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(device.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    device.address,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                isConnected -> Text(
                    "Connected",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                )
                isConnecting -> CircularProgressIndicator(
                    modifier = Modifier.height(20.dp).width(20.dp),
                    strokeWidth = 2.dp,
                )
                else -> TextButton(onClick = onConnect) { Text("Connect") }
            }
        }
    }
}
