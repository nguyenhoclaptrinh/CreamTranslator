package com.cream.translator.core.model

data class TranslationResult(
    val region: TextRegion,
    val translatedText: String,
    val executionTimeMs: Long = 0L
)
