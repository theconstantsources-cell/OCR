package com.scanhid.ocr.storage

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "ScanHidGallery"

/**
 * Saves an approved scan into the device's Gallery, in its own "AI Scan" album. The final
 * OCR text is written two ways: embedded as EXIF metadata (ImageDescription + UserComment)
 * on the photo itself, and as a plain ".txt" sidecar file next to it in the same album
 * folder. The EXIF route depends on the gallery app choosing to surface those fields (many,
 * including Google Photos, don't show them anywhere in their UI even when present) - the
 * sidecar file is the reliable way to see the text, viewable from any file manager.
 */
object GallerySaver {

    private const val ALBUM_NAME = "AI Scan"

    suspend fun save(context: Context, bitmap: Bitmap, extractedText: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolver = context.contentResolver
                val baseName = "AIScan_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}"
                val fileName = "$baseName.jpg"

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

                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri == null) {
                    Log.e(TAG, "save: MediaStore.insert() returned null - nothing was saved")
                    return@runCatching false
                }
                Log.d(TAG, "save: inserted $uri")

                val written = resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                } ?: false
                if (!written) {
                    Log.e(TAG, "save: writing/compressing the bitmap failed for $uri")
                    return@runCatching false
                }
                Log.d(TAG, "save: image bytes written to $uri")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val pendingDone = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                    resolver.update(uri, pendingDone, null, null)
                }

                val exifWritten = writeExif(context, uri, extractedText)
                Log.d(TAG, "save: EXIF description write ${if (exifWritten) "succeeded" else "FAILED"} for $uri")

                val sidecarWritten = writeSidecarText(context, baseName, extractedText)
                Log.d(TAG, "save: sidecar .txt write ${if (sidecarWritten) "succeeded" else "FAILED"} for $baseName")

                true
            }.getOrDefault(false)
        }

    /**
     * Writes the text as its own ".txt" file alongside the photo in the same album folder -
     * this is what actually guarantees the text is visible to the operator, since it doesn't
     * depend on a gallery app choosing to display EXIF description fields.
     */
    private fun writeSidecarText(context: Context, baseName: String, text: String): Boolean =
        runCatching {
            val resolver = context.contentResolver
            val fileName = "$baseName.txt"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Files.FileColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.Files.FileColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.Files.FileColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM_NAME")
                    put(MediaStore.Files.FileColumns.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Files.getContentUri("external"), values) ?: return@runCatching false
                val written = resolver.openOutputStream(uri)?.use { out -> out.write(text.toByteArray()) } != null
                val pendingDone = ContentValues().apply { put(MediaStore.Files.FileColumns.IS_PENDING, 0) }
                resolver.update(uri, pendingDone, null, null)
                written
            } else {
                @Suppress("DEPRECATION")
                val albumDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ALBUM_NAME)
                if (!albumDir.exists()) albumDir.mkdirs()
                File(albumDir, fileName).writeText(text)
                true
            }
        }.onFailure { e ->
            Log.e(TAG, "writeSidecarText threw for $baseName", e)
        }.getOrDefault(false)

    /** Returns whether the EXIF text was actually written - a failure here does not undo the image save above. */
    private fun writeExif(context: Context, uri: Uri, text: String): Boolean =
        runCatching {
            context.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                val exif = ExifInterface(pfd.fileDescriptor)
                exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, text)
                exif.setAttribute(ExifInterface.TAG_USER_COMMENT, text)
                exif.saveAttributes()
            } != null
        }.onFailure { e ->
            Log.e(TAG, "writeExif threw for $uri", e)
        }.getOrDefault(false)
}
