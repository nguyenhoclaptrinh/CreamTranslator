package com.cream.translator.testutil

import com.cream.translator.core.engine.TranslationEngine
import com.cream.translator.core.model.LanguagePair

/**
 * Fake implementation của [TranslationEngine] phục vụ kiểm thử nhanh trên JVM mà không cần native ML Kit.
 */
class FakeTranslationEngine(
    var translationPrefix: String = "[VI] ",
    var shouldFail: Boolean = false,
    var shouldDownloadFail: Boolean = false,
) : TranslationEngine {
    var isClosed: Boolean = false
        private set

    override suspend fun translate(
        text: String,
        languagePair: LanguagePair,
    ): String {
        check(!isClosed) { "TranslationEngine đã bị đóng" }
        if (shouldFail) {
            throw IllegalStateException("Fake Translation Failed")
        }
        return "$translationPrefix$text"
    }

    override suspend fun translateBatch(
        texts: List<String>,
        languagePair: LanguagePair,
    ): List<String> {
        check(!isClosed) { "TranslationEngine đã bị đóng" }
        if (shouldFail) {
            throw IllegalStateException("Fake Translation Batch Failed")
        }
        return texts.map { "$translationPrefix$it" }
    }

    override suspend fun ensureModelDownloaded(languagePair: LanguagePair): Boolean {
        check(!isClosed) { "TranslationEngine đã bị đóng" }
        return !shouldDownloadFail
    }

    override fun close() {
        isClosed = true
    }
}
