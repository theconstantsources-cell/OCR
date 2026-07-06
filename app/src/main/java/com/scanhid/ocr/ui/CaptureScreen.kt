package com.scanhid.ocr.ui

import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.scanhid.ocr.MainViewModel
import com.scanhid.ocr.bluetooth.HidConnectionState
import kotlinx.coroutines.launch

@Composable
fun CaptureScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val connectionState by viewModel.hidConnectionState.collectAsState()

    val cameraController = remember {
        com.scanhid.ocr.camera.CameraController(context)
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ConnectionStatusChip(
                state = connectionState,
                onClick = viewModel::goToConnectionScreen,
            )

            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).also { previewView ->
                            cameraController.bindToLifecycle(previewView, lifecycleOwner)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Button(
                onClick = {
                    scope.launch {
                        val bitmap = cameraController.capturePhoto()
                        viewModel.onPhotoCaptured(bitmap)
                    }
                },
                modifier = Modifier
                    .padding(24.dp)
                    .size(72.dp),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Text("Scan")
            }
        }
    }
}

@Composable
fun ConnectionStatusChip(state: HidConnectionState, onClick: () -> Unit) {
    val label = when (state) {
        HidConnectionState.UNREGISTERED -> "Bluetooth: starting..."
        HidConnectionState.REGISTERED_WAITING_FOR_PC -> "Bluetooth: not paired with PC"
        HidConnectionState.CONNECTED -> "Bluetooth: connected to PC"
    }
    SuggestionChip(
        onClick = onClick,
        label = { Text(label) },
        modifier = Modifier.padding(8.dp),
        colors = SuggestionChipDefaults.suggestionChipColors(),
    )
}
