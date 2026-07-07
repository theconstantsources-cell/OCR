package com.scanhid.ocr.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/** Recognized text plus a confidence estimate, when the device actually reports one. */
data class OcrResult(
    val text: String,
    /** Average per-word confidence in [0f, 1f], or null if the device didn't report any. */
    val confidence: Float?,
)

/** Thin wrapper around ML Kit's on-device (offline) text recognizer. */
class OcrProcessor {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(bitmap: Bitmap): OcrResult {
        val image = InputImage.fromBitmap(bitmap, 0)
        val result = recognizer.process(image).await()

        // ML Kit's on-device recognizer is known to leave confidence unpopulated on some
        // Play Services versions/devices (it returns 0f rather than a real score in that
        // case) - only report a confidence if at least one word actually came back with a
        // plausible non-zero value, so the UI can honestly show "unavailable" otherwise
        // instead of a fabricated number.
        val wordConfidences = result.textBlocks
            .flatMap { it.lines }
            .flatMap { it.elements }
            .mapNotNull { it.confidence }
            .filter { it > 0f }

        val averageConfidence = if (wordConfidences.isEmpty()) null else wordConfidences.average().toFloat()

        return OcrResult(text = result.text, confidence = averageConfidence)
    }
}
