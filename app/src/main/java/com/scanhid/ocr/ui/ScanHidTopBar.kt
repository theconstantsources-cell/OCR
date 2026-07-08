package com.scanhid.ocr.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scanhid.ocr.R

/** Shared branded header used by every screen, so the app reads as one product. */
@Composable
fun ScanHidTopBar(subtitle: String? = null, trailing: @Composable () -> Unit = {}) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Full logo, uncropped - width scales automatically to preserve its aspect ratio.
                Image(
                    painter = painterResource(R.drawable.suzuki_logo),
                    contentDescription = "Suzuki",
                    modifier = Modifier.height(26.dp),
                )
                Column(modifier = Modifier.padding(start = 10.dp)) {
                    Text(
                        "AI SCANHID",
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        letterSpacing = 1.2.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            trailing()
        }
    }
}
