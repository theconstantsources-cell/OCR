package com.scanhid.ocr.ui

import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.scanhid.ocr.MainViewModel
import com.scanhid.ocr.bluetooth.PcConnectionState
import kotlinx.coroutines.launch

@Composable
fun CaptureScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val connectionState by viewModel.pcConnectionState.collectAsState()

    val cameraController = remember {
        com.scanhid.ocr.camera.CameraController(context)
    }

    Scaffold(
        topBar = {
            ScanHidTopBar(
                trailing = {
                    ConnectionStatusChip(
                        state = connectionState,
                        onClick = viewModel::goToConnectionScreen,
                    )
                },
            )
        },
        // Solid, opaque action bar - deliberately its own Surface so the camera's
        // hardware-composited preview layer can never visually bleed into the button.
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 4.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                val bitmap = cameraController.capturePhoto()
                                viewModel.onPhotoCaptured(bitmap)
                            }
                        },
                        modifier = Modifier.size(76.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text("SCAN", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Text(
                        "Tap to capture a document",
                        modifier = Modifier.padding(top = 8.dp),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black),
        ) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).also { previewView ->
                        // COMPATIBLE mode composites via TextureView instead of SurfaceView,
                        // so the preview respects normal view z-order/clipping instead of
                        // punching through and bleeding over sibling UI (the bug this fixes).
                        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        cameraController.bindToLifecycle(previewView, lifecycleOwner)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun ConnectionStatusChip(state: PcConnectionState, onClick: () -> Unit) {
    val label = when (state) {
        PcConnectionState.DISCONNECTED -> "Not connected"
        PcConnectionState.CONNECTING -> "Connecting..."
        PcConnectionState.CONNECTED -> "Connected"
    }
    SuggestionChip(
        onClick = onClick,
        label = { Text(label) },
        colors = SuggestionChipDefaults.suggestionChipColors(),
    )
}
