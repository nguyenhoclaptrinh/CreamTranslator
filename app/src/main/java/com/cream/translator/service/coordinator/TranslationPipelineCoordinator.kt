package com.cream.translator.service.coordinator

import android.graphics.Bitmap
import android.graphics.Rect
import com.cream.translator.core.engine.OcrEngine
import com.cream.translator.core.engine.TextInpainter
import com.cream.translator.core.engine.TranslationEngine
import com.cream.translator.core.engine.filter.TextFilter
import com.cream.translator.core.model.LanguagePair
import com.cream.translator.core.model.PixelOffset
import com.cream.translator.core.model.TranslationResult
import com.cream.translator.core.model.TranslationState
import com.cream.translator.core.model.error.CreamError
import com.cream.translator.core.monitoring.metrics.PipelineTracer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Hiện thực chuẩn của [ITranslationPipelineCoordinator].
 * Điều phối tuần tự chu trình OCR -> TextFilter -> Translate -> TextInpainter.
 */
class TranslationPipelineCoordinator(
    private val ocrEngine: OcrEngine,
    private val translationEngine: TranslationEngine,
    private val textFilter: TextFilter,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ITranslationPipelineCoordinator {
    private val _state = MutableStateFlow<TranslationState>(TranslationState.Idle)
    override val state: StateFlow<TranslationState> = _state.asStateFlow()

    override fun updateState(newState: TranslationState) {
        _state.value = newState
    }

    override suspend fun executePipeline(
        bitmap: Bitmap,
        languagePair: LanguagePair,
        tracerSession: PipelineTracer.Session,
        regionOffset: PixelOffset,
    ): List<TranslationResult>? {
        return try {
            _state.value = TranslationState.Processing("OCR")

            // 1. Chạy OCR On-Device trên Bitmap đã cho (toàn màn hình hoặc đã crop)
            val textRegions =
                tracerSession.measureOcr {
                    withContext(defaultDispatcher) {
                        ocrEngine.recognizeText(bitmap)
                    }
                }
            tracerSession.recognizedBlocks = textRegions.size

            if (textRegions.isEmpty()) {
                val err = CreamError.OcrError.ZeroRegionsDetected()
                _state.value = TranslationState.Error(err)
                return null
            }

            // 2. Lọc bỏ các khối chữ thuộc danh sách từ phổ biến/ký hiệu hoặc không tương thích
            val eligibleRegions =
                tracerSession.measureFilter {
                    textRegions.filterNot {
                        textFilter.shouldFilter(it.originalText, languagePair)
                    }
                }

            if (eligibleRegions.isEmpty()) {
                Timber.d("Toàn bộ các khối chữ nhận diện được đã được lọc bỏ (từ phổ biến/ký hiệu)")
                _state.value = TranslationState.Idle
                return null
            }

            _state.value = TranslationState.Processing("Translate")

            // 3. Dịch on-device, lấy mẫu màu nền và áp offset tọa độ (nếu có)
            val results =
                withContext(defaultDispatcher) {
                    val textsToTranslate = eligibleRegions.map { it.originalText }
                    val translatedTexts =
                        tracerSession.measureTranslate {
                            translationEngine.translateBatch(
                                textsToTranslate,
                                languagePair,
                            )
                        }

                    tracerSession.measureInpaint {
                        eligibleRegions.mapIndexed { index, region ->
                            // Luôn sample màu từ tọa độ gốc của Bitmap (trước khi áp offset).
                            // Bitmap ở đây là Bitmap đã crop → tọa độ region.boundingBox là tọa độ
                            // tương đối so với crop, đúng để sample pixel.
                            val sampledBg = TextInpainter.sampleBackgroundColor(bitmap, region.boundingBox)
                            val contrastingText = TextInpainter.calculateContrastingTextColor(sampledBg)

                            // Áp offset tọa độ SAU khi sample màu để boundingBox khớp với tọa độ
                            // màn hình tuyệt đối cho InPlaceTranslationOverlay render đúng vị trí.
                            val screenBoundingBox =
                                if (regionOffset.isZero) {
                                    region.boundingBox
                                } else {
                                    Rect(
                                        region.boundingBox.left + regionOffset.x,
                                        region.boundingBox.top + regionOffset.y,
                                        region.boundingBox.right + regionOffset.x,
                                        region.boundingBox.bottom + regionOffset.y,
                                    )
                                }

                            val enrichedRegion =
                                region.copy(
                                    boundingBox = screenBoundingBox,
                                    backgroundColor = sampledBg,
                                    textColor = contrastingText,
                                )
                            TranslationResult(
                                region = enrichedRegion,
                                translatedText = translatedTexts.getOrElse(index) { region.originalText },
                                executionTimeMs = 0L,
                            )
                        }
                    }
                }
            tracerSession.translatedBlocks = results.size
            results
        } finally {
            // Giải phóng Bitmap triệt để tránh rò rỉ Native Graphic RAM
            bitmap.recycle()
        }
    }

    override fun close() {
        ocrEngine.close()
        translationEngine.close()
    }
}
