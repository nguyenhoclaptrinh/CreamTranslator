package com.cream.translator.core.engine

import android.graphics.Bitmap
import android.graphics.Rect
import com.cream.translator.core.model.TextRegion
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MlKitOcrEngine : OcrEngine {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun recognizeText(bitmap: Bitmap): List<TextRegion> {
        if (bitmap.isRecycled) {
            Timber.w("Cannot recognize text on recycled bitmap")
            return emptyList()
        }

        return suspendCancellableCoroutine { continuation ->
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    if (!continuation.isActive) return@addOnSuccessListener
                    val regions = mutableListOf<TextRegion>()

                    // Iterate over text blocks and lines
                    for (block in visionText.textBlocks) {
                        for (line in block.lines) {
                            val box = line.boundingBox ?: Rect(0, 0, 0, 0)
                            if (line.text.isNotBlank() && box.width() > 5 && box.height() > 5) {
                                regions.add(
                                    TextRegion(
                                        id = UUID.randomUUID().toString(),
                                        originalText = line.text.trim(),
                                        boundingBox = box,
                                        confidence = 1.0f,
                                    ),
                                )
                            }
                        }
                    }
                    Timber.d("OCR detected %d text regions", regions.size)
                    continuation.resume(regions)
                }
                .addOnFailureListener { exception ->
                    if (!continuation.isActive) return@addOnFailureListener
                    Timber.e(exception, "OCR processing failed")
                    continuation.resumeWithException(exception)
                }
        }
    }

    override fun close() {
        try {
            recognizer.close()
        } catch (e: Exception) {
            Timber.e(e, "Error closing ML Kit recognizer")
        }
    }
}
