package com.scanhid.ocr.history

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class ScanHistoryItem(
    val imageUri: Uri,
    val text: String,
    val timestampMillis: Long,
)

/**
 * Reads back everything GallerySaver has written to the "Pictures/AI Scan" album - this is
 * the same on-device data the History screen shows, not a separate store. Each photo's text
 * comes from its matching ".txt" sidecar rather than EXIF, since that's the copy guaranteed
 * to have been written successfully (EXIF write failures don't undo the image/sidecar save).
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
                        text = readSidecarText(context, baseName),
                        timestampMillis = dateAddedSeconds * 1000L,
                    )
                }
            }
            items
        }.getOrDefault(emptyList())
    }

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
