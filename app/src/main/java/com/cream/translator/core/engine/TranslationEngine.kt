package com.cream.translator.core.engine

import com.cream.translator.core.model.LanguagePair

interface TranslationEngine {
    suspend fun translate(text: String, languagePair: LanguagePair): String
    suspend fun translateBatch(texts: List<String>, languagePair: LanguagePair): List<String>
    suspend fun ensureModelDownloaded(languagePair: LanguagePair): Boolean
    fun close()
}
