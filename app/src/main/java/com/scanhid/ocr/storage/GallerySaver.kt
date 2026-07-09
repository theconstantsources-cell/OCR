package com.scanhid.ocr.storage

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saves an approved scan into the device's Gallery, in its own "AI Scan" album, with the
 * final OCR text embedded as EXIF metadata (ImageDescription + UserComment) so it shows
 * up alongside the photo in any EXIF-aware viewer's "details"/"info" panel. Whether that
 * panel actually surfaces it depends on the gallery app - the data is genuinely in the
 * file either way.
 */
object GallerySaver {

    private const val ALBUM_NAME = "AI Scan"

    suspend fun save(context: Context, bitmap: Bitmap, extractedText: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolver = context.contentResolver
                val fileName = "AIScan_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"

                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM_NAME")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    } else {
                        // Pre-scoped-storage (Android 9): MediaStore doesn't auto-create the
                        // album folder or accept a relative path, so point it at an absolute
                        // path we create ourselves.
                        val albumDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ALBUM_NAME)
                        if (!albumDir.exists()) albumDir.mkdirs()
                        @Suppress("DEPRECATION")
                        put(MediaStore.Images.Media.DATA, File(albumDir, fileName).absolutePath)
                    }
                }

                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@runCatching false

                val written = resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                } ?: false
                if (!written) return@runCatching false

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val pendingDone = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                    resolver.update(uri, pendingDone, null, null)
                }

                writeExif(context, uri, extractedText)
                true
            }.getOrDefault(false)
        }

    private fun writeExif(context: Context, uri: Uri, text: String) {
        // Best-effort: a failure here shouldn't undo the successful Gallery save above.
        runCatching {
            context.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                val exif = ExifInterface(pfd.fileDescriptor)
                exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, text)
                exif.setAttribute(ExifInterface.TAG_USER_COMMENT, text)
                exif.saveAttributes()
            }
        }
    }
}
