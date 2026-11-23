package com.cream.translator.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import com.cream.translator.R
import com.cream.translator.core.engine.MlKitOcrEngine
import com.cream.translator.core.engine.MlKitTranslationEngine
import com.cream.translator.core.engine.TextInpainter
import com.cream.translator.core.model.LanguagePair
import com.cream.translator.core.model.TranslationResult
import com.cream.translator.core.model.TranslationState
import com.cream.translator.core.model.error.CreamError
import com.cream.translator.core.util.createNamedExceptionHandler
import com.cream.translator.ui.overlay.InPlaceTranslationOverlay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class OverlayService : Service() {
    private val exceptionHandler =
        createNamedExceptionHandler("OverlayService") { throwable ->
            _state.value =
                TranslationState.Error(
                    error = CreamError.OverlayError.WindowAttachmentFailed(throwable),
                )
            floatingTriggerView?.visibility = View.VISIBLE
        }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob() + exceptionHandler)
    private var windowManager: WindowManager? = null

    private var floatingTriggerView: View? = null
    private var triggerParams: WindowManager.LayoutParams? = null

    private var inPlaceOverlay: InPlaceTranslationOverlay? = null
    private var isOverlayShowing = false

    private val ocrEngine = MlKitOcrEngine()
    private val translationEngine = MlKitTranslationEngine()

    private val _state = MutableStateFlow<TranslationState>(TranslationState.Idle)
    val state: StateFlow<TranslationState> = _state.asStateFlow()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        setupFloatingTrigger()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFloatingTrigger() {
        val wm = windowManager ?: return

        val layoutType =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

        val size = (52 * resources.displayMetrics.density).toInt()

        triggerParams =
            WindowManager.LayoutParams(
                size,
                size,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 24
                y = 400
            }

        // Stylish circular floating trigger button with brand vector icon
        val triggerButton =
            ImageView(this).apply {
                setImageResource(R.drawable.ic_notification_capture)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                val pad = (11 * resources.displayMetrics.density).toInt()
                setPadding(pad, pad, pad, pad)

                val bg =
                    GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.parseColor("#E11D48"))
                        setStroke((2 * resources.displayMetrics.density).toInt(), Color.WHITE)
                    }
                background = bg
                elevation = 14f
            }

        attachDragAndClickListener(triggerButton, triggerParams!!)

        val attached = wm.safeAddView(triggerButton, triggerParams!!)
        if (attached) {
            floatingTriggerView = triggerButton
            Timber.d("Floating trigger attached successfully")
            Toast.makeText(this, "CreamTranslator: Đã kích hoạt nút dịch nổi", Toast.LENGTH_SHORT).show()
        } else {
            Timber.e("Failed to attach floating overlay view")
        }
    }

    private fun attachDragAndClickListener(
        view: View,
        params: WindowManager.LayoutParams,
    ) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isClick = true

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isClick = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 12 || Math.abs(dy) > 12) {
                        isClick = false
                    }
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager?.safeUpdateViewLayout(view, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isClick) {
                        handleTriggerClick()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun handleTriggerClick() {
        if (isOverlayShowing) {
            dismissTranslationOverlay()
            return
        }

        val currentState = _state.value
        if (currentState is TranslationState.Capturing || currentState is TranslationState.Processing) {
            Toast.makeText(this, "Đang xử lý, vui lòng chờ...", Toast.LENGTH_SHORT).show()
            return
        }

        _state.value = TranslationState.Capturing
        serviceScope.launch {
            try {
                // 1. Ẩn nút nổi tạm thời để không bị dính vào ảnh chụp màn hình
                floatingTriggerView?.visibility = View.INVISIBLE
                delay(60)

                // 2. Chụp khung hình màn hình hiện tại
                val captureService = ScreenCaptureService.instance
                if (captureService == null) {
                    val err = CreamError.CaptureError.PermissionDenied()
                    _state.value = TranslationState.Error(err)
                    Toast.makeText(this@OverlayService, err.userFriendlyMessage, Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val bitmap = captureService.captureCurrentScreen()
                floatingTriggerView?.visibility = View.VISIBLE

                if (bitmap == null) {
                    val err = CreamError.CaptureError.FrameAcquireTimeout()
                    _state.value = TranslationState.Error(err)
                    Toast.makeText(this@OverlayService, err.userFriendlyMessage, Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val results =
                    try {
                        _state.value = TranslationState.Processing("OCR")
                        Toast.makeText(
                            this@OverlayService,
                            "Đang nhận diện văn bản...",
                            Toast.LENGTH_SHORT,
                        ).show()

                        // 3. Chạy OCR On-Device bằng ML Kit
                        val textRegions =
                            withContext(Dispatchers.Default) {
                                ocrEngine.recognizeText(bitmap)
                            }

                        if (textRegions.isEmpty()) {
                            val err = CreamError.OcrError.ZeroRegionsDetected()
                            _state.value = TranslationState.Error(err)
                            Toast.makeText(this@OverlayService, err.userFriendlyMessage, Toast.LENGTH_SHORT).show()
                            return@launch
                        }

                        _state.value = TranslationState.Processing("Translate")
                        Toast.makeText(
                            this@OverlayService,
                            "Đang dịch ${textRegions.size} khối chữ...",
                            Toast.LENGTH_SHORT,
                        ).show()

                        // 4. Dịch on-device và lấy mẫu màu nền thông minh (Inpainting)
                        withContext(Dispatchers.Default) {
                            val textsToTranslate = textRegions.map { it.originalText }
                            val translatedTexts =
                                translationEngine.translateBatch(
                                    textsToTranslate,
                                    LanguagePair("en", "vi"),
                                )

                            textRegions.mapIndexed { index, region ->
                                val sampledBg = TextInpainter.sampleBackgroundColor(bitmap, region.boundingBox)
                                val contrastingText = TextInpainter.calculateContrastingTextColor(sampledBg)
                                val enrichedRegion =
                                    region.copy(
                                        backgroundColor = sampledBg,
                                        textColor = contrastingText,
                                    )
                                TranslationResult(
                                    region = enrichedRegion,
                                    translatedText = translatedTexts.getOrElse(index) { region.originalText },
                                )
                            }
                        }
                    } finally {
                        // Prevent native graphic memory leaks by immediately recycling full-frame capture
                        bitmap.recycle()
                    }

                // 5. Hiển thị lớp phủ dịch đè trực tiếp (In-Place Translation Overlay)
                _state.value = TranslationState.Success(results)
                displayTranslationOverlay(results)
            } catch (cancellation: CancellationException) {
                _state.value = TranslationState.Idle
                throw cancellation
            } catch (e: Throwable) {
                Timber.e(e, "Lỗi trong quá trình dịch màn hình")
                val err = CreamError.TranslationError.InferenceFailed(e)
                _state.value = TranslationState.Error(err)
                Toast.makeText(this@OverlayService, "Lỗi xử lý: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                floatingTriggerView?.visibility = View.VISIBLE
                if (_state.value is TranslationState.Capturing || _state.value is TranslationState.Processing) {
                    _state.value = TranslationState.Idle
                }
            }
        }
    }

    private fun displayTranslationOverlay(results: List<TranslationResult>) {
        if (isOverlayShowing) {
            dismissTranslationOverlay()
        }

        val wm = windowManager ?: return

        val layoutType =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

        val overlayParams =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 0
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }

        val overlayView =
            InPlaceTranslationOverlay(this).apply {
                setResults(results)
                onDismissListener = {
                    dismissTranslationOverlay()
                }
            }

        val added = wm.safeAddView(overlayView, overlayParams)
        if (added) {
            inPlaceOverlay = overlayView
            isOverlayShowing = true
            Toast.makeText(
                this,
                "Chạm vào chữ để đổi bản gốc/dịch. Chạm ra ngoài để đóng.",
                Toast.LENGTH_LONG,
            ).show()
        } else {
            Timber.e("Không thể gắn lớp phủ dịch đè lên màn hình")
            _state.value = TranslationState.Error(CreamError.OverlayError.WindowAttachmentFailed())
        }
    }

    private fun dismissTranslationOverlay() {
        if (isOverlayShowing && inPlaceOverlay != null) {
            windowManager?.safeRemoveView(inPlaceOverlay)
            inPlaceOverlay = null
            isOverlayShowing = false
            _state.value = TranslationState.Idle
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (isOverlayShowing) {
            dismissTranslationOverlay()
        }
        floatingTriggerView?.let { trigger ->
            triggerParams?.let { params ->
                val displayMetrics = resources.displayMetrics
                val maxX = displayMetrics.widthPixels - params.width
                val maxY = displayMetrics.heightPixels - params.height
                params.x = params.x.coerceIn(0, maxOf(0, maxX))
                params.y = params.y.coerceIn(0, maxOf(0, maxY))
                try {
                    windowManager?.updateViewLayout(trigger, params)
                } catch (e: Exception) {
                    Timber.w(e, "Failed to update trigger layout on configuration change")
                }
            }
        }
        Timber.d("OverlayService configuration changed and views adjusted")
    }

    override fun onDestroy() {
        dismissTranslationOverlay()
        if (floatingTriggerView != null) {
            windowManager?.safeRemoveView(floatingTriggerView)
            floatingTriggerView = null
        }
        ocrEngine.close()
        translationEngine.close()
        serviceScope.cancel()
        super.onDestroy()
        Timber.d("OverlayService destroyed")
    }

    private fun WindowManager.safeAddView(
        view: View,
        params: ViewGroup.LayoutParams,
    ): Boolean =
        try {
            addView(view, params)
            true
        } catch (e: Throwable) {
            Timber.e(e, "safeAddView failed")
            false
        }

    private fun WindowManager.safeRemoveView(view: View?): Boolean =
        try {
            if (view != null && (view.isAttachedToWindow || view.windowToken != null)) {
                removeView(view)
                true
            } else {
                false
            }
        } catch (e: Throwable) {
            Timber.e(e, "safeRemoveView failed")
            false
        }

    private fun WindowManager.safeUpdateViewLayout(
        view: View,
        params: ViewGroup.LayoutParams,
    ): Boolean =
        try {
            if (view.isAttachedToWindow || view.windowToken != null) {
                updateViewLayout(view, params)
                true
            } else {
                false
            }
        } catch (e: Throwable) {
            Timber.e(e, "safeUpdateViewLayout failed")
            false
        }
}
