package com.cream.translator.ui.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.widget.Toast
import com.cream.translator.CreamTranslatorApp
import com.cream.translator.R
import com.cream.translator.core.model.LanguagePair
import com.cream.translator.core.model.PixelOffset
import com.cream.translator.core.model.TranslationState
import com.cream.translator.core.model.error.CreamError
import com.cream.translator.core.monitoring.metrics.PipelineTracer
import com.cream.translator.service.ScreenCaptureService
import com.cream.translator.service.coordinator.ITranslationPipelineCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Các hook mở rộng phục vụ kiểm thử và tùy biến nguồn chụp/hiển thị thông báo
 * cho [RegionTranslateController].
 */
data class RegionTranslateHooks(
    val languagePairProvider: () -> LanguagePair = { LanguagePair("en", "vi") },
    val pipelineTracer: PipelineTracer? = null,
    val isCaptureServiceReady: () -> Boolean = { ScreenCaptureService.instance != null },
    val captureRegionProvider: suspend (Rect) -> Bitmap? = { rect ->
        ScreenCaptureService.instance?.captureRegion(rect)
    },
    val onToast: ((String) -> Unit)? = null,
)

/**
 * Điều phối luồng Chọn vùng → Chụp crop → Dịch → Hiển thị Overlay.
 * Tuân thủ bất biến Thin Host: [com.cream.translator.service.OverlayService] không chứa logic nghiệp vụ.
 */
class RegionTranslateController(
    private val context: Context,
    private val overlayWindowManager: IOverlayWindowManager,
    private val pipelineCoordinator: ITranslationPipelineCoordinator,
    private val floatingTriggerController: FloatingTriggerController,
    private val serviceScope: CoroutineScope,
    private val hooks: RegionTranslateHooks = RegionTranslateHooks(),
) {
    private val pipelineTracer: PipelineTracer
        get() =
            hooks.pipelineTracer
                ?: (context.applicationContext as? CreamTranslatorApp)?.pipelineTracer
                ?: PipelineTracer()

    private fun showToast(message: String) {
        val customHandler = hooks.onToast
        if (customHandler != null) {
            customHandler(message)
        } else {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Kích hoạt chế độ chọn vùng trên màn hình.
     * Bỏ qua nếu pipeline đang bận hoặc đang ở trạng thái chọn vùng.
     */
    fun activateRegionSelectMode() {
        val currentState = pipelineCoordinator.state.value
        if (currentState is TranslationState.Capturing ||
            currentState is TranslationState.Processing ||
            currentState is TranslationState.RegionSelecting
        ) {
            return
        }

        if (overlayWindowManager.isOverlayShowing) {
            overlayWindowManager.dismissTranslationOverlay()
        }

        pipelineCoordinator.updateState(TranslationState.RegionSelecting)
        floatingTriggerController.setVisibility(false)

        val shown =
            overlayWindowManager.showRegionSelector(
                scope = serviceScope,
                onRegionSelected = { rect -> executeRegionPipeline(rect) },
                onCancel = {
                    floatingTriggerController.setVisibility(true)
                    pipelineCoordinator.updateState(TranslationState.Idle)
                },
            )
        if (!shown) {
            floatingTriggerController.setVisibility(true)
            pipelineCoordinator.updateState(
                TranslationState.Error(CreamError.OverlayError.WindowAttachmentFailed()),
            )
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun executeRegionPipeline(region: Rect) {
        val session = pipelineTracer.startSession()
        pipelineCoordinator.updateState(TranslationState.Capturing)

        serviceScope.launch {
            try {
                floatingTriggerController.setVisibility(false)
                delay(OverlayConfig.TRIGGER_HIDE_DELAY_MS)

                if (!hooks.isCaptureServiceReady()) {
                    val err = CreamError.CaptureError.PermissionDenied()
                    pipelineCoordinator.updateState(TranslationState.Error(err))
                    showToast(context.getString(R.string.overlay_capture_not_ready))
                    session.finish(isSuccess = false, errorMessage = "CaptureService is null")
                    return@launch
                }

                val croppedBitmap =
                    session.measureCapture {
                        hooks.captureRegionProvider(region)
                    }

                if (croppedBitmap == null) {
                    val err = CreamError.CaptureError.FrameAcquireTimeout()
                    pipelineCoordinator.updateState(TranslationState.Error(err))
                    showToast(context.getString(R.string.overlay_capture_failed))
                    session.finish(isSuccess = false, errorMessage = "Region crop returned null")
                    return@launch
                }

                val offset = PixelOffset(region.left, region.top)
                val results =
                    pipelineCoordinator.executePipeline(
                        croppedBitmap,
                        hooks.languagePairProvider(),
                        session,
                        offset,
                    )

                if (results == null) {
                    session.finish(isSuccess = false, errorMessage = "No eligible text in region")
                    return@launch
                }

                pipelineCoordinator.updateState(TranslationState.Success(results))
                session.measureRender {
                    val shown =
                        overlayWindowManager.showTranslationOverlay(results) {
                            pipelineCoordinator.updateState(TranslationState.Idle)
                        }
                    if (shown) {
                        showToast(context.getString(R.string.overlay_tap_hint))
                    } else {
                        pipelineCoordinator.updateState(
                            TranslationState.Error(CreamError.OverlayError.WindowAttachmentFailed()),
                        )
                    }
                }
                session.finish(isSuccess = true)
            } catch (cancellation: CancellationException) {
                pipelineCoordinator.updateState(TranslationState.Idle)
                session.finish(isSuccess = false, errorMessage = "Job cancelled")
                throw cancellation
            } catch (e: Throwable) {
                Timber.e(e, "Lỗi trong pipeline dịch theo vùng")
                val err = CreamError.TranslationError.InferenceFailed(e)
                pipelineCoordinator.updateState(TranslationState.Error(err))
                session.finish(isSuccess = false, errorMessage = e.message)
                showToast(context.getString(R.string.overlay_error_prefix, e.message ?: ""))
            } finally {
                floatingTriggerController.setVisibility(true)
                val finalState = pipelineCoordinator.state.value
                if (finalState is TranslationState.Capturing || finalState is TranslationState.Processing) {
                    pipelineCoordinator.updateState(TranslationState.Idle)
                }
            }
        }
    }
}
