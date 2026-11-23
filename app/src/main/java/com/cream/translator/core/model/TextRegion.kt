package com.cream.translator.core.model

import android.graphics.Rect

data class TextRegion(
    val id: String,
    val originalText: String,
    val boundingBox: Rect,
    val confidence: Float = 1.0f,
    val backgroundColor: Int? = null,
    val textColor: Int? = null
)
