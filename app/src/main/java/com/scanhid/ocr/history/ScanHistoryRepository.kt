package com.scanhid.ocr.history

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "ScanHidHistory"

data class ScanHistoryItem(
    val imageUri: Uri,
    val text: String,
    val timestampMillis: Long,
    val scanId: Int?,
    val baseName: String,
)

// Anchored to the full base name (no extension) so it only matches the current three-part
// naming scheme "AIScan_<id>_<yyyyMMdd>_<HHmmss>" - older two-part files saved before scan
// IDs existed ("AIScan_<yyyyMMdd>_<HHmmss>") correctly fail to match instead of having their
// date digits misread as an ID.
private val SCAN_ID_PATTERN = Regex("""AIScan_(\d+)_\d{8}_\d{6}""")

/**
 * Reads back everything GallerySaver has written to the "Pictures/AI Scan" album - this is
 * the same on-device data the History screen shows, not a separate store. Each photo's text
 * is read straight from that photo's own EXIF ImageDescription first (no extra lookup needed,
 * since we already have that exact image's Uri), falling back to the matching ".txt" sidecar
 * only if the EXIF field ever comes back empty.
 */
object ScanHistoryRepository {

    private const val ALBUM_RELATIVE_PATH = "Pictures/AI Scan"
    private const val ALBUM_NAME = "AI Scan"

    suspend fun loadAll(context: Context): List<ScanHistoryItem> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED,
            )
            val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
            } else null
            val selectionArgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                arrayOf("$ALBUM_RELATIVE_PATH%")
            } else null

            val items = mutableListOf<ScanHistoryItem>()
            resolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Images.Media.DATE_ADDED} DESC",
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameCol) ?: continue
                    if (!name.startsWith("AIScan_")) continue
                    val id = cursor.getLong(idCol)
                    val dateAddedSeconds = cursor.getLong(dateCol)
                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    val baseName = name.substringBeforeLast('.')
                    items += ScanHistoryItem(
                        imageUri = uri,
                        text = readScanText(context, uri, baseName),
                        timestampMillis = dateAddedSeconds * 1000L,
                        scanId = SCAN_ID_PATTERN.find(baseName)?.groupValues?.get(1)?.toIntOrNull(),
                        baseName = baseName,
                    )
                }
            }
            items
        }.getOrDefault(emptyList())
    }

    /** Deletes the photo and its matching .txt sidecar (if any). */
    suspend fun delete(context: Context, item: ScanHistoryItem): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.delete(item.imageUri, null, null)
            deleteSidecarText(context, item.baseName)
            true
        }.getOrDefault(false)
    }

    private fun deleteSidecarText(context: Context, baseName: String) {
        runCatching {
            val resolver = context.contentResolver
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val projection = arrayOf(MediaStore.Files.FileColumns._ID)
                val selection = "${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ? AND " +
                    "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ?"
                val args = arrayOf("$ALBUM_RELATIVE_PATH%", "$baseName.txt")
                resolver.query(MediaStore.Files.getContentUri("external"), projection, selection, args, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
                        val uri = ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), id)
                        resolver.delete(uri, null, null)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val albumDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ALBUM_NAME)
                File(albumDir, "$baseName.txt").delete()
            }
        }.onFailure { e ->
            Log.e(TAG, "deleteSidecarText threw for $baseName", e)
        }
    }

    private fun readScanText(context: Context, imageUri: Uri, baseName: String): String {
        val exifText = readExifText(context, imageUri)
        if (exifText.isNotBlank()) return exifText
        Log.d(TAG, "readScanText: EXIF empty for $baseName, falling back to .txt sidecar")
        return readSidecarText(context, baseName)
    }

    private fun readExifText(context: Context, imageUri: Uri): String =
        runCatching {
            context.contentResolver.openInputStream(imageUri)?.use { input ->
                val exif = ExifInterface(input)
                exif.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION)
            }
        }.onFailure { e ->
            Log.e(TAG, "readExifText threw for $imageUri", e)
        }.getOrNull().orEmpty()

    private fun readSidecarText(context: Context, baseName: String): String =
        runCatching {
            val resolver = context.contentResolver
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val projection = arrayOf(MediaStore.Files.FileColumns._ID)
                val selection = "${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ? AND " +
                    "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ?"
                val args = arrayOf("$ALBUM_RELATIVE_PATH%", "$baseName.txt")
                resolver.query(MediaStore.Files.getContentUri("external"), projection, selection, args, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
                        val uri = ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), id)
                        resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                    } else null
                }
            } else {
                @Suppress("DEPRECATION")
                val albumDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ALBUM_NAME)
                val file = File(albumDir, "$baseName.txt")
                if (file.exists()) file.readText() else null
            }
        }.getOrNull().orEmpty()
}
