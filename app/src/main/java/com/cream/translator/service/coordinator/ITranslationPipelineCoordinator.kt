package com.cream.translator.service.coordinator

import android.graphics.Bitmap
import com.cream.translator.core.model.LanguagePair
import com.cream.translator.core.model.PixelOffset
import com.cream.translator.core.model.TranslationResult
import com.cream.translator.core.model.TranslationState
import com.cream.translator.core.monitoring.metrics.PipelineTracer
import kotlinx.coroutines.flow.StateFlow

/**
 * Hợp đồng điều phối luồng xử lý nhận dạng và dịch thuật màn hình (Pipeline).
 */
interface ITranslationPipelineCoordinator {
    val state: StateFlow<TranslationState>

    /**
     * Thực thi pipeline OCR → Filter → Translate → Inpaint trên [bitmap] đã cho.
     *
     * @param bitmap Bitmap nguồn (toàn màn hình hoặc đã crop theo vùng chọn).
     *               Bitmap này sẽ được recycle() bên trong pipeline.
     * @param languagePair Cặp ngôn ngữ nguồn/đích.
     * @param tracerSession Phiên đo đạc độ trễ pipeline.
     * @param regionOffset Độ lệch tọa độ từ góc trên-trái của vùng crop về gốc màn hình tuyệt đối.
     *                     Dùng [PixelOffset.NONE] khi bitmap là toàn màn hình (mặc định).
     */
    suspend fun executePipeline(
        bitmap: Bitmap,
        languagePair: LanguagePair,
        tracerSession: PipelineTracer.Session,
        regionOffset: PixelOffset = PixelOffset.NONE,
    ): List<TranslationResult>?

    fun updateState(newState: TranslationState)

    fun close()
}
