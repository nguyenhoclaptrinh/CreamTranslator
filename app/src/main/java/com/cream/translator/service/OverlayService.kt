package com.cream.translator.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.cream.translator.CreamTranslatorApp
import com.cream.translator.R
import com.cream.translator.core.engine.MlKitOcrEngine
import com.cream.translator.core.engine.MlKitTranslationEngine
import com.cream.translator.core.engine.filter.DefaultTextFilter
import com.cream.translator.core.model.LanguagePair
import com.cream.translator.core.model.TranslationState
import com.cream.translator.core.model.error.CreamError
import com.cream.translator.core.monitoring.metrics.PipelineTracer
import com.cream.translator.core.util.createNamedExceptionHandler
import com.cream.translator.data.repository.FilterPreferencesRepository
import com.cream.translator.service.coordinator.ITranslationPipelineCoordinator
import com.cream.translator.service.coordinator.TranslationPipelineCoordinator
import com.cream.translator.ui.overlay.FloatingTriggerController
import com.cream.translator.ui.overlay.IOverlayWindowManager
import com.cream.translator.ui.overlay.OverlayConfig
import com.cream.translator.ui.overlay.OverlayWindowManager
import com.cream.translator.ui.overlay.RegionTranslateController
import com.cream.translator.ui.overlay.RegionTranslateHooks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Service lớp phủ dịch thuật (Thin Lifecycle Host).
 * Đóng vai trò cầu nối vòng đời Android Service và ủy quyền toàn bộ trách nhiệm:
 * - Điều phối luồng nhận dạng/dịch thuật cho [ITranslationPipelineCoordinator].
 * - Quản lý hiển thị toàn màn hình cho [IOverlayWindowManager].
 * - Quản lý nút nổi và cử chỉ chạm kéo cho [FloatingTriggerController].
 */
class OverlayService : Service() {
    private val exceptionHandler =
        createNamedExceptionHandler("OverlayService") { throwable ->
            overlayWindowManager.dismissTranslationOverlay()
            overlayWindowManager.dismissRegionSelector()
            pipelineCoordinator.updateState(
                TranslationState.Error(
                    error = CreamError.OverlayError.WindowAttachmentFailed(throwable),
                ),
            )
            floatingTriggerController.setVisibility(true)
        }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob() + exceptionHandler)

    private val overlayWindowManager: IOverlayWindowManager by lazy {
        OverlayWindowManager(this)
    }

    private val floatingTriggerController: FloatingTriggerController by lazy {
        FloatingTriggerController(this, overlayWindowManager, serviceScope)
    }

    private val regionTranslateController: RegionTranslateController by lazy {
        RegionTranslateController(
            context = this,
            overlayWindowManager = overlayWindowManager,
            pipelineCoordinator = pipelineCoordinator,
            floatingTriggerController = floatingTriggerController,
            serviceScope = serviceScope,
            hooks =
                RegionTranslateHooks(
                    languagePairProvider = { currentLanguagePair },
                ),
        )
    }

    private val filterPreferencesRepository by lazy { FilterPreferencesRepository.getInstance(this) }
    private val textFilter by lazy { DefaultTextFilter { filterPreferencesRepository.getConfig() } }

    private val pipelineCoordinatorDelegate =
        lazy<ITranslationPipelineCoordinator> {
            TranslationPipelineCoordinator(
                ocrEngine = MlKitOcrEngine(),
                translationEngine = MlKitTranslationEngine(),
                textFilter = textFilter,
            )
        }

    private val pipelineCoordinator: ITranslationPipelineCoordinator by pipelineCoordinatorDelegate

    val state: StateFlow<TranslationState>
        get() = pipelineCoordinator.state

    private var currentLanguagePair = LanguagePair("en", "vi")

    private val pipelineTracer: PipelineTracer
        get() = (applicationContext as? CreamTranslatorApp)?.pipelineTracer ?: PipelineTracer()

    private val systemEventsReceiver =
        object : BroadcastReceiver() {
            @Suppress("DEPRECATION")
            override fun onReceive(
                context: Context?,
                intent: Intent?,
            ) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF,
                    Intent.ACTION_CLOSE_SYSTEM_DIALOGS,
                    -> {
                        overlayWindowManager.dismissTranslationOverlay()
                        overlayWindowManager.dismissRegionSelector()
                        if (pipelineCoordinator.state.value is TranslationState.RegionSelecting) {
                            floatingTriggerController.setVisibility(true)
                            pipelineCoordinator.updateState(TranslationState.Idle)
                        }
                    }
                }
            }
        }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                @Suppress("DEPRECATION")
                addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
            }
        try {
            ContextCompat.registerReceiver(
                this,
                systemEventsReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED,
            )
        } catch (e: SecurityException) {
            Timber.w(e, "Không thể đăng ký systemEventsReceiver do thiếu quyền")
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "Tham số không hợp lệ khi đăng ký systemEventsReceiver")
        }

        val attached =
            floatingTriggerController.setupFloatingTrigger(
                onClick = { handleTriggerClick() },
                onDoubleTap = { regionTranslateController.activateRegionSelectMode() },
                onLongPress = { handleTriggerLongPress() },
            )
        if (attached) {
            isRunning = true
            Toast.makeText(this, getString(R.string.overlay_trigger_enabled), Toast.LENGTH_SHORT).show()
        } else {
            isRunning = false
            TranslatorController.stop(this)
            stopSelf()
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == ACTION_STOP) {
            Timber.d("OverlayService nhận lệnh ACTION_STOP")
            stopSelf()
            return START_NOT_STICKY
        }
        val source = intent?.getStringExtra(EXTRA_SOURCE_LANG) ?: "en"
        val target = intent?.getStringExtra(EXTRA_TARGET_LANG) ?: "vi"
        currentLanguagePair = LanguagePair(source, target)
        Timber.d("OverlayService started with language pair: %s", currentLanguagePair.displayName)
        return START_NOT_STICKY
    }

    private fun handleTriggerLongPress() {
        Toast.makeText(
            this@OverlayService,
            getString(R.string.toast_service_stopped),
            Toast.LENGTH_SHORT,
        ).show()
        TranslatorController.stop(this@OverlayService)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun handleTriggerClick() {
        if (overlayWindowManager.isOverlayShowing) {
            overlayWindowManager.dismissTranslationOverlay()
            pipelineCoordinator.updateState(TranslationState.Idle)
            return
        }

        val currentState = pipelineCoordinator.state.value
        if (currentState is TranslationState.Capturing ||
            currentState is TranslationState.Processing ||
            currentState is TranslationState.RegionSelecting
        ) {
            Toast.makeText(this, getString(R.string.overlay_processing_wait), Toast.LENGTH_SHORT).show()
            return
        }

        pipelineCoordinator.updateState(TranslationState.Capturing)
        val session = pipelineTracer.startSession()

        serviceScope.launch {
            try {
                // 1. Ẩn nút nổi tạm thời để không bị dính vào ảnh chụp màn hình
                floatingTriggerController.setVisibility(false)
                delay(OverlayConfig.TRIGGER_HIDE_DELAY_MS)

                // 2. Chụp khung hình màn hình hiện tại
                val captureService = ScreenCaptureService.instance
                if (captureService == null) {
                    val err = CreamError.CaptureError.PermissionDenied()
                    pipelineCoordinator.updateState(TranslationState.Error(err))
                    Toast.makeText(
                        this@OverlayService,
                        getString(R.string.overlay_capture_not_ready),
                        Toast.LENGTH_SHORT,
                    ).show()
                    session.finish(isSuccess = false, errorMessage = "CaptureService is null")
                    return@launch
                }

                val bitmap =
                    session.measureCapture {
                        captureService.captureCurrentScreen()
                    }

                if (bitmap == null) {
                    val err = CreamError.CaptureError.FrameAcquireTimeout()
                    pipelineCoordinator.updateState(TranslationState.Error(err))
                    Toast.makeText(
                        this@OverlayService,
                        getString(R.string.overlay_capture_failed),
                        Toast.LENGTH_SHORT,
                    ).show()
                    session.finish(isSuccess = false, errorMessage = "Frame acquire timeout")
                    return@launch
                }

                val results = pipelineCoordinator.executePipeline(bitmap, currentLanguagePair, session)
                if (results == null) {
                    session.finish(isSuccess = false, errorMessage = "No eligible text or OCR failed")
                    return@launch
                }

                // 3. Hiển thị lớp phủ dịch đè trực tiếp (In-Place Translation Overlay)
                pipelineCoordinator.updateState(TranslationState.Success(results))
                session.measureRender {
                    val shown =
                        overlayWindowManager.showTranslationOverlay(results) {
                            pipelineCoordinator.updateState(TranslationState.Idle)
                        }
                    if (shown) {
                        Toast.makeText(
                            this@OverlayService,
                            getString(R.string.overlay_tap_hint),
                            Toast.LENGTH_LONG,
                        ).show()
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
                Timber.e(e, "Lỗi trong quá trình dịch màn hình")
                val err = CreamError.TranslationError.InferenceFailed(e)
                pipelineCoordinator.updateState(TranslationState.Error(err))
                session.finish(isSuccess = false, errorMessage = e.message)
                Toast.makeText(
                    this@OverlayService,
                    getString(R.string.overlay_error_prefix, e.message ?: ""),
                    Toast.LENGTH_SHORT,
                ).show()
            } finally {
                floatingTriggerController.setVisibility(true)
                val finalState = pipelineCoordinator.state.value
                if (finalState is TranslationState.Capturing || finalState is TranslationState.Processing) {
                    pipelineCoordinator.updateState(TranslationState.Idle)
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (overlayWindowManager.isOverlayShowing) {
            overlayWindowManager.dismissTranslationOverlay()
            pipelineCoordinator.updateState(TranslationState.Idle)
        }
        if (pipelineCoordinator.state.value is TranslationState.RegionSelecting) {
            overlayWindowManager.dismissRegionSelector()
            floatingTriggerController.setVisibility(true)
            pipelineCoordinator.updateState(TranslationState.Idle)
        }
        floatingTriggerController.handleConfigurationChange()
        Timber.d("OverlayService configuration changed and views adjusted")
    }

    override fun onDestroy() {
        isRunning = false
        try {
            unregisterReceiver(systemEventsReceiver)
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "Lỗi unregister systemEventsReceiver: receiver chưa từng được đăng ký")
        }

        overlayWindowManager.dismissTranslationOverlay()
        overlayWindowManager.dismissRegionSelector()
        floatingTriggerController.destroy()
        if (pipelineCoordinatorDelegate.isInitialized()) {
            pipelineCoordinator.close()
        }
        serviceScope.cancel()
        TranslatorController.updateServiceState(false)
        if (ScreenCaptureService.instance != null) {
            try {
                stopService(Intent(this, ScreenCaptureService::class.java))
            } catch (e: SecurityException) {
                Timber.w(e, "SecurityException khi dừng ScreenCaptureService từ OverlayService.onDestroy")
            } catch (e: IllegalStateException) {
                Timber.w(e, "IllegalStateException khi dừng ScreenCaptureService từ OverlayService.onDestroy")
            }
        }
        super.onDestroy()
        Timber.d("OverlayService destroyed")
    }

    companion object {
        const val ACTION_STOP = "com.cream.translator.action.STOP_OVERLAY"
        const val EXTRA_SOURCE_LANG = "extra_source_lang"
        const val EXTRA_TARGET_LANG = "extra_target_lang"

        @Volatile
        var isRunning: Boolean = false
            private set
    }
}
