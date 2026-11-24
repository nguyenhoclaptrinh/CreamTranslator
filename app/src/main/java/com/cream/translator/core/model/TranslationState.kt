package com.cream.translator.core.model

import com.cream.translator.core.model.error.CreamError

sealed interface TranslationState {
    data object Idle : TranslationState

    data object Capturing : TranslationState

    /**
     * Người dùng đang kéo chọn vùng màn hình (RegionSelectorView đang hiển thị).
     * Guard state này ngăn toàn bộ pipeline song song khi chế độ chọn vùng đang hoạt động.
     */
    data object RegionSelecting : TranslationState

    data class Processing(val stage: String) : TranslationState

    data class Success(val results: List<TranslationResult>) : TranslationState

    data class Error(
        val error: CreamError,
        val message: String = error.userFriendlyMessage,
        val isRecoverable: Boolean = error.isRecoverable,
    ) : TranslationState
}
