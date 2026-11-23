package com.cream.translator.core.model

sealed interface TranslationState {
    data object Idle : TranslationState

    data object Capturing : TranslationState

    data class Processing(val stage: String) : TranslationState

    data class Success(val results: List<TranslationResult>) : TranslationState

    data class Error(val message: String, val isRecoverable: Boolean = true) : TranslationState
}
