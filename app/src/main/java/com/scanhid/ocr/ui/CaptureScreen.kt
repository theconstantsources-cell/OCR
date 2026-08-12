package com.scanhid.ocr.ui

import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.scanhid.ocr.MainViewModel
import com.scanhid.ocr.bluetooth.PcConnectionState
import com.scanhid.ocr.camera.CropRegion
import com.scanhid.ocr.camera.cropToPreviewRegion
import kotlin.math.roundToInt
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

    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    var cropRegion by remember { mutableStateOf(CropRegion.Default) }

    Scaffold(
        topBar = {
            ScanHidTopBar(
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = viewModel::goToHistoryScreen) {
                            Text("History")
                        }
                        ConnectionStatusChip(
                            state = connectionState,
                            onClick = viewModel::goToConnectionScreen,
                        )
                    }
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
                                val cropped = cropToPreviewRegion(
                                    bitmap,
                                    previewSize.width,
                                    previewSize.height,
                                    cropRegion,
                                )
                                viewModel.onPhotoCaptured(cropped)
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
                        "Drag the box to fit the text, then tap to capture",
                        modifier = Modifier.padding(top = 8.dp, start = 24.dp, end = 24.dp),
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
                .background(Color.Black)
                .onGloballyPositioned { previewSize = IntSize(it.size.width, it.size.height) },
        ) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).also { previewView ->
                        // COMPATIBLE mode composites via TextureView instead of SurfaceView,
                        // so the preview respects normal view z-order/clipping instead of
                        // punching through and bleeding over sibling UI (the bug this fixes).
                        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        // FILL_CENTER is the default, but set explicitly since cropToPreviewRegion's
                        // math (center-crop to cover the view) assumes exactly this scale type.
                        previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
                        cameraController.bindToLifecycle(previewView, lifecycleOwner)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            CropOverlay(
                region = cropRegion,
                containerSize = previewSize,
                onRegionChange = { cropRegion = it },
            )
        }
    }
}

/**
 * A draggable, resizable rectangle over the camera preview marking the region that will actually
 * be sent to OCR - the rest of the captured photo is discarded. Users drag inside the rectangle to
 * move it, or drag a corner handle to resize it.
 */
@Composable
private fun CropOverlay(
    region: CropRegion,
    containerSize: IntSize,
    onRegionChange: (CropRegion) -> Unit,
) {
    val minSize = 0.1f
    val density = LocalDensity.current

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val rectPx = Rect(
                left = region.left * size.width,
                top = region.top * size.height,
                right = region.right * size.width,
                bottom = region.bottom * size.height,
            )
            // Dim everything outside the selection so the active region reads clearly.
            val scrimPath = Path().apply {
                addRect(Rect(Offset.Zero, size))
                addRect(rectPx)
                fillType = PathFillType.EvenOdd
            }
            drawPath(scrimPath, color = Color.Black.copy(alpha = 0.55f))
            drawRect(
                color = Color.White,
                topLeft = rectPx.topLeft,
                size = rectPx.size,
                style = Stroke(width = 2.dp.toPx()),
            )
        }

        if (containerSize.width <= 0 || containerSize.height <= 0) return@Box

        val leftPx = region.left * containerSize.width
        val topPx = region.top * containerSize.height
        val rightPx = region.right * containerSize.width
        val bottomPx = region.bottom * containerSize.height

        // Drag inside the rectangle to move the whole selection.
        Box(
            modifier = Modifier
                .offset { IntOffset(leftPx.roundToInt(), topPx.roundToInt()) }
                .size(
                    with(density) { (rightPx - leftPx).toDp() },
                    with(density) { (bottomPx - topPx).toDp() },
                )
                .pointerInput(containerSize) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val width = region.right - region.left
                        val height = region.bottom - region.top
                        val newLeft = (region.left + dragAmount.x / containerSize.width)
                            .coerceIn(0f, 1f - width)
                        val newTop = (region.top + dragAmount.y / containerSize.height)
                            .coerceIn(0f, 1f - height)
                        onRegionChange(
                            region.copy(
                                left = newLeft,
                                top = newTop,
                                right = newLeft + width,
                                bottom = newTop + height,
                            ),
                        )
                    }
                },
        )

        CropHandle(xPx = leftPx, yPx = topPx, containerSize = containerSize) { dxFrac, dyFrac ->
            onRegionChange(
                region.copy(
                    left = (region.left + dxFrac).coerceIn(0f, region.right - minSize),
                    top = (region.top + dyFrac).coerceIn(0f, region.bottom - minSize),
                ),
            )
        }
        CropHandle(xPx = rightPx, yPx = topPx, containerSize = containerSize) { dxFrac, dyFrac ->
            onRegionChange(
                region.copy(
                    right = (region.right + dxFrac).coerceIn(region.left + minSize, 1f),
                    top = (region.top + dyFrac).coerceIn(0f, region.bottom - minSize),
                ),
            )
        }
        CropHandle(xPx = leftPx, yPx = bottomPx, containerSize = containerSize) { dxFrac, dyFrac ->
            onRegionChange(
                region.copy(
                    left = (region.left + dxFrac).coerceIn(0f, region.right - minSize),
                    bottom = (region.bottom + dyFrac).coerceIn(region.top + minSize, 1f),
                ),
            )
        }
        CropHandle(xPx = rightPx, yPx = bottomPx, containerSize = containerSize) { dxFrac, dyFrac ->
            onRegionChange(
                region.copy(
                    right = (region.right + dxFrac).coerceIn(region.left + minSize, 1f),
                    bottom = (region.bottom + dyFrac).coerceIn(region.top + minSize, 1f),
                ),
            )
        }
    }
}

/** A round drag handle centered at ([xPx], [yPx]) in the overlay's pixel space. */
@Composable
private fun CropHandle(
    xPx: Float,
    yPx: Float,
    containerSize: IntSize,
    onDragFraction: (dxFrac: Float, dyFrac: Float) -> Unit,
) {
    val handleSize = 28.dp
    val density = LocalDensity.current
    val handleSizePx = with(density) { handleSize.toPx() }
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (xPx - handleSizePx / 2f).roundToInt(),
                    (yPx - handleSizePx / 2f).roundToInt(),
                )
            }
            .size(handleSize)
            .background(Color.White, CircleShape)
            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
            .pointerInput(containerSize) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDragFraction(dragAmount.x / containerSize.width, dragAmount.y / containerSize.height)
                }
            },
    )
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
