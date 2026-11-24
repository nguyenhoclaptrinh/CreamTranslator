package com.cream.translator.service.coordinator

import android.graphics.Bitmap
import com.cream.translator.core.model.LanguagePair
import com.cream.translator.core.model.TranslationResult
import com.cream.translator.core.model.TranslationState
import com.cream.translator.core.monitoring.metrics.PipelineTracer
import kotlinx.coroutines.flow.StateFlow

/**
 * Hợp đồng điều phối luồng xử lý nhận dạng và dịch thuật màn hình (Pipeline).
 */
interface ITranslationPipelineCoordinator {
    val state: StateFlow<TranslationState>

    suspend fun executePipeline(
        bitmap: Bitmap,
        languagePair: LanguagePair,
        tracerSession: PipelineTracer.Session,
    ): List<TranslationResult>?

    fun updateState(newState: TranslationState)

    fun close()
}
