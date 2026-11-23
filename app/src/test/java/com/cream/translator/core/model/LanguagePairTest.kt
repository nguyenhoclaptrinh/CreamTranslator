package com.cream.translator.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LanguagePairTest {
    @Test
    fun displayName_formatsCorrectly() {
        val pair = LanguagePair("en", "vi")
        assertEquals("EN -> VI", pair.displayName)
    }

    @Test
    fun defaultConstructor_setsEnglishAndVietnamese() {
        val pair = LanguagePair()
        assertEquals("en", pair.sourceLanguage)
        assertEquals("vi", pair.targetLanguage)
    }
}
