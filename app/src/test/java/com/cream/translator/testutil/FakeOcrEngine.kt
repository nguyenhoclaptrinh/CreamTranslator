package com.cream.translator.testutil

import android.graphics.Bitmap
import com.cream.translator.core.engine.OcrEngine
import com.cream.translator.core.model.TextRegion

/**
 * Fake implementation của [OcrEngine] phục vụ kiểm thử nhanh trên JVM mà không cần native ML Kit.
 */
class FakeOcrEngine(
    var simulatedRegions: List<TextRegion> = emptyList(),
    var shouldFail: Boolean = false,
) : OcrEngine {
    var isClosed: Boolean = false
        private set

    override suspend fun recognizeText(bitmap: Bitmap): List<TextRegion> {
        check(!isClosed) { "OcrEngine đã bị đóng" }
        if (shouldFail) {
            throw IllegalStateException("Fake OCR Recognition Failed")
        }
        return simulatedRegions
    }

    override fun close() {
        isClosed = true
    }
}
