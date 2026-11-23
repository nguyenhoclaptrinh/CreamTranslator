package com.cream.translator.core.engine

import android.graphics.Bitmap
import com.cream.translator.core.model.TextRegion

interface OcrEngine {
    suspend fun recognizeText(bitmap: Bitmap): List<TextRegion>
    fun close()
}
