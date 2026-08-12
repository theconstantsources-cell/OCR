package com.scanhid.ocr.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * A selection rectangle expressed as fractions (0f..1f) of the PreviewView's displayed bounds,
 * e.g. left=0.1f means 10% in from the view's left edge.
 */
data class CropRegion(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    companion object {
        /** A wide, short default band centered on the frame - fits a typical single-line label. */
        val Default = CropRegion(left = 0.12f, top = 0.32f, right = 0.88f, bottom = 0.6f)
    }
}

/**
 * Crops [bitmap] down to the sub-rectangle that was visible under [region] of a PreviewView
 * measuring [previewWidth] x [previewHeight] px. Assumes the PreviewView's default FILL_CENTER
 * scaling: the camera frame is scaled up uniformly until it fully covers the view, then centered,
 * cropping off whatever overflows on one axis - so the frame pixels actually visible on screen are
 * a centered sub-rectangle of the full captured bitmap, not the whole thing.
 */
fun cropToPreviewRegion(bitmap: Bitmap, previewWidth: Int, previewHeight: Int, region: CropRegion): Bitmap {
    if (previewWidth <= 0 || previewHeight <= 0) return bitmap
    val imgW = bitmap.width.toFloat()
    val imgH = bitmap.height.toFloat()
    val scale = maxOf(previewWidth / imgW, previewHeight / imgH)
    val visibleImgW = previewWidth / scale
    val visibleImgH = previewHeight / scale
    val offsetX = (imgW - visibleImgW) / 2f
    val offsetY = (imgH - visibleImgH) / 2f

    val left = (offsetX + region.left.coerceIn(0f, 1f) * visibleImgW)
        .toInt().coerceIn(0, bitmap.width - 1)
    val top = (offsetY + region.top.coerceIn(0f, 1f) * visibleImgH)
        .toInt().coerceIn(0, bitmap.height - 1)
    val right = (offsetX + region.right.coerceIn(0f, 1f) * visibleImgW)
        .toInt().coerceIn(left + 1, bitmap.width)
    val bottom = (offsetY + region.bottom.coerceIn(0f, 1f) * visibleImgH)
        .toInt().coerceIn(top + 1, bitmap.height)

    return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
}

/** Wraps CameraX setup + single-photo capture so the UI layer only deals with a Bitmap. */
class CameraController(private val context: Context) {

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var imageCapture: ImageCapture? = null

    fun bindToLifecycle(previewView: PreviewView, lifecycleOwner: LifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val cameraProvider = providerFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()
            imageCapture = capture

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                capture,
            )
        }, ContextCompat.getMainExecutor(context))
    }

    suspend fun capturePhoto(): Bitmap = suspendCoroutine { continuation ->
        val capture = imageCapture
        if (capture == null) {
            continuation.resumeWithException(IllegalStateException("Camera not ready"))
            return@suspendCoroutine
        }

        val photoFile = File.createTempFile("scan_", ".jpg", context.cacheDir)
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        capture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val bitmap = decodeAndOrient(photoFile)
                    photoFile.delete()
                    continuation.resume(bitmap)
                }

                override fun onError(exception: ImageCaptureException) {
                    photoFile.delete()
                    continuation.resumeWithException(exception)
                }
            },
        )
    }

    private fun decodeAndOrient(file: File): Bitmap {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        val exif = ExifInterface(file.absolutePath)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
        val rotationDegrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
        if (rotationDegrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun shutdown() {
        cameraExecutor.shutdown()
    }
}
