package com.scanhid.ocr.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scanhid.ocr.MainViewModel
import com.scanhid.ocr.ui.theme.SuzukiRed
import kotlin.math.roundToInt

@Composable
fun ReviewScreen(viewModel: MainViewModel) {
    val recognizedText by viewModel.recognizedText.collectAsState()
    val ocrConfidence by viewModel.ocrConfidence.collectAsState()
    val isProcessing by viewModel.isProcessingOcr.collectAsState()
    val lastSendSucceeded by viewModel.lastSendSucceeded.collectAsState()
    val gallerySaved by viewModel.gallerySaved.collectAsState()
    var showApproveDialog by remember { mutableStateOf(false) }

    Scaffold(topBar = { ScanHidTopBar(subtitle = "Review & approve") }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text("Extracted text", style = MaterialTheme.typography.titleMedium)

            if (!isProcessing) {
                ConfidenceLevel(
                    confidence = ocrConfidence,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            if (isProcessing) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                    Text("Reading text from image...", modifier = Modifier.padding(top = 16.dp))
                }
            } else {
                OutlinedTextField(
                    value = recognizedText,
                    onValueChange = viewModel::onTextEdited,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = 14.dp),
                    label = { Text("Edit before sending") },
                    shape = MaterialTheme.shapes.medium,
                )
            }

            if (lastSendSucceeded == false) {
                Text(
                    "Not connected to a PC - open the Bluetooth screen and pair first.",
                    color = SuzukiRed,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            if (gallerySaved != null) {
                Text(
                    if (gallerySaved == true) "Saved to Gallery (AI Scan album, text saved as a .txt file alongside it)" else "Couldn't save to Gallery",
                    color = if (gallerySaved == true) Color(0xFF2E7D4F) else SuzukiRed,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = viewModel::retakePhoto) {
                    Text("Retake")
                }
                Button(
                    onClick = { showApproveDialog = true },
                    enabled = !isProcessing && recognizedText.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Approve & Send to PC")
                }
            }
        }
    }

    if (showApproveDialog) {
        AlertDialog(
            onDismissRequest = { showApproveDialog = false },
            title = { Text("Send to PC?") },
            text = { Text("This will type the text below wherever the cursor is currently placed on the connected PC.") },
            confirmButton = {
                Button(onClick = {
                    showApproveDialog = false
                    viewModel.approveAndSend()
                }) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showApproveDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

/**
 * Visual level bar for the OCR engine's own confidence in this scan, when the device actually
 * reported one. ML Kit's on-device recognizer is known to leave confidence unpopulated on some
 * Play Services versions - in that case [confidence] is null and the bar shows "Unavailable"
 * rather than a fabricated percentage.
 */
@Composable
private fun ConfidenceLevel(confidence: Float?, modifier: Modifier = Modifier) {
    val color = when {
        confidence == null -> MaterialTheme.colorScheme.onSurfaceVariant
        confidence >= 0.8f -> Color(0xFF2E7D4F)
        confidence >= 0.5f -> Color(0xFFB8792B)
        else -> SuzukiRed
    }
    val percentLabel = if (confidence == null) "Unavailable" else "${(confidence * 100).roundToInt()}%"

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "OCR CONFIDENCE",
                fontSize = 11.sp,
                letterSpacing = 0.06.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(percentLabel, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { confidence ?: 0f },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = color.copy(alpha = 0.16f),
        )
    }
}
