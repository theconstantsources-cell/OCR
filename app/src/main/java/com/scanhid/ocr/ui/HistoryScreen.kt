package com.scanhid.ocr.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.scanhid.ocr.MainViewModel
import com.scanhid.ocr.history.ScanHistoryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HistoryScreen(viewModel: MainViewModel) {
    val items by viewModel.historyItems.collectAsState()
    val isLoading by viewModel.isLoadingHistory.collectAsState()
    val visibleMonth by viewModel.historyVisibleMonth.collectAsState()
    val selectedDate by viewModel.historySelectedDate.collectAsState()

    val itemsByDate = remember(items) {
        items.groupBy {
            Instant.ofEpochMilli(it.timestampMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        }
    }
    val datesWithScans = itemsByDate.keys
    val visibleItems = if (selectedDate != null) itemsByDate[selectedDate].orEmpty() else items
    var selectedItem by remember { mutableStateOf<ScanHistoryItem?>(null) }
    // Collapsed by default so the scan list (the thing people scroll through most) gets the
    // screen space - the calendar is for jumping to a specific day, not something to keep open.
    var calendarExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            ScanHidTopBar(
                subtitle = "Scan history",
                trailing = {
                    OutlinedButton(onClick = viewModel::goToCaptureScreen) { Text("Done") }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            MonthCalendar(
                visibleMonth = visibleMonth,
                selectedDate = selectedDate,
                datesWithScans = datesWithScans,
                expanded = calendarExpanded,
                onToggleExpanded = { calendarExpanded = !calendarExpanded },
                onPrevMonth = { viewModel.changeHistoryMonth(-1) },
                onNextMonth = { viewModel.changeHistoryMonth(1) },
                onSelectDate = viewModel::selectHistoryDate,
            )

            if (selectedDate != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${visibleItems.size} scan(s) on ${selectedDate!!.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = { viewModel.selectHistoryDate(null) }) {
                        Text("Show all")
                    }
                }
            }

            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                visibleItems.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (selectedDate != null) "No scans on this day" else "No scans saved yet",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    items(visibleItems, key = { it.imageUri.toString() }) { item ->
                        HistoryRow(
                            item,
                            onClick = { selectedItem = item },
                            onDelete = { viewModel.deleteScan(item) },
                        )
                    }
                }
            }
        }
    }

    selectedItem?.let { item ->
        ScanDetailDialog(item, onDismiss = { selectedItem = null })
    }
}

@Composable
private fun MonthCalendar(
    visibleMonth: YearMonth,
    selectedDate: LocalDate?,
    datesWithScans: Set<LocalDate>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleExpanded).padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                (if (expanded) visibleMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " ${visibleMonth.year}" else "Calendar"),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (expanded) {
                    IconButton(onClick = onPrevMonth) {
                        Text("‹", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onNextMonth) {
                        Text("›", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    if (expanded) "▴ Hide" else "▾ Show",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }

        if (!expanded) return@Column

        // Sunday-first, to match the day-grid column order below.
        val weekdayHeaders = listOf(
            DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY,
        )
        Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            for (day in weekdayHeaders) {
                Text(
                    day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    modifier = Modifier.weight(1f),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val firstOfMonth = visibleMonth.atDay(1)
        // Sunday-first grid: DayOfWeek.SUNDAY.value == 7, so this wraps Sunday to column 0.
        val leadingBlanks = firstOfMonth.dayOfWeek.value % 7
        val totalDays = visibleMonth.lengthOfMonth()
        val cells = leadingBlanks + totalDays
        val rows = (cells + 6) / 7

        var dayCounter = 1
        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val cellIndex = row * 7 + col
                    if (cellIndex < leadingBlanks || dayCounter > totalDays) {
                        Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val date = visibleMonth.atDay(dayCounter)
                        DayCell(
                            date = date,
                            isSelected = date == selectedDate,
                            hasScans = date in datesWithScans,
                            onClick = { onSelectDate(date) },
                        )
                        dayCounter++
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.DayCell(
    date: LocalDate,
    isSelected: Boolean,
    hasScans: Boolean,
    onClick: () -> Unit,
) {
    val today = LocalDate.now()
    Box(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .background(
                when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    date == today -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    else -> androidx.compose.ui.graphics.Color.Transparent
                },
            )
            .clickable(enabled = hasScans, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                date.dayOfMonth.toString(),
                fontSize = 12.sp,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                else if (hasScans) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                fontWeight = if (hasScans) FontWeight.Bold else FontWeight.Normal,
            )
            if (hasScans && !isSelected) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

@Composable
private fun HistoryRow(item: ScanHistoryItem, onClick: () -> Unit, onDelete: () -> Unit) {
    val thumbnail = rememberScanImage(item, sampleSize = 4)
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        tonalElevation = 1.dp,
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (thumbnail != null) {
                    Image(bitmap = thumbnail, contentDescription = null, modifier = Modifier.fillMaxSize())
                }
            }
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(
                    buildString {
                        if (item.scanId != null) append("Scan #${item.scanId}  •  ")
                        append(formatTimestamp(item.timestampMillis))
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    item.text.ifBlank { "(no text)" },
                    fontSize = 13.sp,
                    maxLines = 2,
                )
            }
            IconButton(onClick = { showDeleteConfirm = true }) {
                Text("🗑", fontSize = 18.sp) // trash bin emoji
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this scan?") },
            text = { Text("The photo and its extracted text will be permanently deleted from this device. This can't be undone.") },
            confirmButton = {
                Button(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) { Text("Delete") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

/** Full-screen view opened by tapping a scan: the photo at a higher resolution, with its full extracted text laid out below it. */
@Composable
private fun ScanDetailDialog(item: ScanHistoryItem, onDismiss: () -> Unit) {
    val image = rememberScanImage(item, sampleSize = 1)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        buildString {
                            if (item.scanId != null) append("Scan #${item.scanId}  •  ")
                            append(formatTimestamp(item.timestampMillis))
                        },
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    OutlinedButton(onClick = onDismiss) { Text("Close") }
                }
                HorizontalDivider()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (image != null) {
                            Image(bitmap = image, contentDescription = null, modifier = Modifier.fillMaxWidth())
                        } else {
                            Box(modifier = Modifier.fillMaxWidth().height(200.dp))
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "EXTRACTED TEXT",
                        fontSize = 11.sp,
                        letterSpacing = 0.06.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    SelectionContainer {
                        Text(item.text.ifBlank { "(no text)" }, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberScanImage(item: ScanHistoryItem, sampleSize: Int): ImageBitmap? {
    val context = LocalContext.current
    var bitmap by remember(item.imageUri, sampleSize) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(item.imageUri, sampleSize) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(item.imageUri)?.use { input ->
                    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                    BitmapFactory.decodeStream(input, null, options)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    return bitmap
}

private fun formatTimestamp(millis: Long): String {
    val dateTime = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    return dateTime.format(DateTimeFormatter.ofPattern("MMM d, yyyy - h:mm a"))
}
