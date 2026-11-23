package com.cream.translator.core.model

data class LanguagePair(
    val sourceLanguage: String = "en",
    val targetLanguage: String = "vi"
) {
    val displayName: String
        get() = "${sourceLanguage.uppercase()} -> ${targetLanguage.uppercase()}"
}
