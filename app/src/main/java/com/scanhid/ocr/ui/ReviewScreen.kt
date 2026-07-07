package com.scanhid.ocr.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scanhid.ocr.MainViewModel
import kotlin.math.roundToInt

@Composable
fun ReviewScreen(viewModel: MainViewModel) {
    val recognizedText by viewModel.recognizedText.collectAsState()
    val ocrConfidence by viewModel.ocrConfidence.collectAsState()
    val isProcessing by viewModel.isProcessingOcr.collectAsState()
    val lastSendSucceeded by viewModel.lastSendSucceeded.collectAsState()
    var showApproveDialog by remember { mutableStateOf(false) }

    Scaffold(topBar = { ScanHidTopBar(subtitle = "Review & approve") }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Extracted text", style = MaterialTheme.typography.titleMedium)
                if (!isProcessing) {
                    ConfidenceBadge(ocrConfidence)
                }
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
                        .padding(vertical = 8.dp),
                    label = { Text("Edit before sending") },
                )
            }

            if (lastSendSucceeded == false) {
                Text(
                    "Not connected to a PC - open the Bluetooth screen and pair first.",
                    color = Color.Red,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                OutlinedButton(onClick = viewModel::retakePhoto) {
                    Text("Retake")
                }
                Button(
                    onClick = { showApproveDialog = true },
                    enabled = !isProcessing && recognizedText.isNotBlank(),
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
 * Shows the OCR engine's own confidence for this scan, when the device actually reported one.
 * ML Kit's on-device recognizer is known to leave confidence unpopulated on some Play Services
 * versions - in that case [confidence] is null and we say so plainly rather than showing a
 * fabricated number.
 */
@Composable
private fun ConfidenceBadge(confidence: Float?) {
    val color = when {
        confidence == null -> MaterialTheme.colorScheme.onSurfaceVariant
        confidence >= 0.8f -> Color(0xFF2E7D4F)
        confidence >= 0.5f -> Color(0xFFB8792B)
        else -> com.scanhid.ocr.ui.theme.SuzukiRed
    }
    val label = if (confidence == null) "Confidence unavailable" else "Confidence ${(confidence * 100).roundToInt()}%"

    Surface(
        color = color.copy(alpha = 0.14f),
        contentColor = color,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
