package com.cream.translator.service

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.cream.translator.R
import com.cream.translator.core.engine.MlKitOcrEngine
import com.cream.translator.core.engine.MlKitTranslationEngine
import com.cream.translator.core.engine.TextInpainter
import com.cream.translator.core.engine.filter.DefaultTextFilter
import com.cream.translator.core.model.LanguagePair
import com.cream.translator.core.model.TranslationResult
import com.cream.translator.core.model.TranslationState
import com.cream.translator.core.model.error.CreamError
import com.cream.translator.core.util.createNamedExceptionHandler
import com.cream.translator.data.repository.FilterPreferencesRepository
import com.cream.translator.ui.overlay.InPlaceTranslationOverlay
import com.cream.translator.ui.overlay.OverlayConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private var snapAnimator: ValueAnimator? = null

    private var inPlaceOverlay: InPlaceTranslationOverlay? = null
    private var isOverlayShowing = false

    private val ocrEngine = MlKitOcrEngine()
    private val translationEngine = MlKitTranslationEngine()
    private val filterPreferencesRepository by lazy { FilterPreferencesRepository.getInstance(this) }
    private val textFilter by lazy { DefaultTextFilter { filterPreferencesRepository.getConfig() } }
    private val _state = MutableStateFlow<TranslationState>(TranslationState.Idle)
    val state: StateFlow<TranslationState> = _state.asStateFlow()
    private var currentLanguagePair = LanguagePair("en", "vi")

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
                        dismissTranslationOverlay()
                    }
                }
            }
        }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager

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
        } catch (e: Throwable) {
            Timber.w(e, "Không thể đăng ký systemEventsReceiver")
        }

        setupFloatingTrigger()
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

    private fun getScreenWidth(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager?.currentWindowMetrics
            metrics?.bounds?.width() ?: resources.displayMetrics.widthPixels
        } else {
            resources.displayMetrics.widthPixels
        }

    private fun getScreenHeight(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager?.currentWindowMetrics
            metrics?.bounds?.height() ?: resources.displayMetrics.heightPixels
        } else {
            resources.displayMetrics.heightPixels
        }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFloatingTrigger() {
        if (!Settings.canDrawOverlays(this)) {
            Timber.w("Quyền vẽ đè (Overlay) chưa được cấp hoặc đã bị thu hồi")
            stopSelf()
            return
        }

        val wm = windowManager ?: return

        val layoutType =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

        val size = (OverlayConfig.TRIGGER_SIZE_DP * resources.displayMetrics.density).toInt()

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
                x = OverlayConfig.TRIGGER_DEFAULT_X
                y = OverlayConfig.TRIGGER_DEFAULT_Y
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
            Toast.makeText(this, getString(R.string.overlay_trigger_enabled), Toast.LENGTH_SHORT).show()
        } else {
            Timber.e("Failed to attach floating overlay view")
        }
    }

    private fun animateSnapToEdge(
        view: View,
        params: WindowManager.LayoutParams,
    ) {
        val screenWidth = getScreenWidth()
        val screenHeight = getScreenHeight()
        val size = (52 * resources.displayMetrics.density).toInt()
        val margin = (12 * resources.displayMetrics.density).toInt()

        val safeMaxY = (screenHeight - size - margin).coerceAtLeast(margin)
        params.y = params.y.coerceIn(margin, safeMaxY)

        val isLeft = (params.x + size / 2) < (screenWidth / 2)
        val targetX = if (isLeft) margin else (screenWidth - size - margin)

        snapAnimator?.cancel()
        snapAnimator =
            ValueAnimator.ofInt(params.x, targetX).apply {
                duration = 250
                interpolator = DecelerateInterpolator()
                addUpdateListener { animation ->
                    if (floatingTriggerView != null && (view.isAttachedToWindow || view.windowToken != null)) {
                        params.x = animation.animatedValue as Int
                        windowManager?.safeUpdateViewLayout(view, params)
                    }
                }
                start()
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
        var longPressJob: Job? = null

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    snapAnimator?.cancel()
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isClick = true

                    longPressJob =
                        serviceScope.launch {
                            delay(800)
                            if (isClick) {
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                Toast.makeText(
                                    this@OverlayService,
                                    "CreamTranslator: Đã tắt dịch",
                                    Toast.LENGTH_SHORT,
                                ).show()
                                TranslatorController.stop(this@OverlayService)
                            }
                        }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > OverlayConfig.TRIGGER_DRAG_THRESHOLD ||
                        Math.abs(dy) > OverlayConfig.TRIGGER_DRAG_THRESHOLD
                    ) {
                        isClick = false
                        longPressJob?.cancel()
                    }
                    val screenWidth = getScreenWidth()
                    val screenHeight = getScreenHeight()
                    val size = (52 * resources.displayMetrics.density).toInt()
                    val margin = (12 * resources.displayMetrics.density).toInt()

                    params.x = (initialX + dx).coerceIn(-margin, screenWidth - size + margin)
                    val safeMaxY = (screenHeight - size - margin).coerceAtLeast(margin)
                    params.y = (initialY + dy).coerceIn(margin, safeMaxY)
                    windowManager?.safeUpdateViewLayout(view, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    longPressJob?.cancel()
                    if (isClick) {
                        handleTriggerClick()
                    } else {
                        animateSnapToEdge(view, params)
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    longPressJob?.cancel()
                    if (!isClick) {
                        animateSnapToEdge(view, params)
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
            Toast.makeText(this, getString(R.string.overlay_processing_wait), Toast.LENGTH_SHORT).show()
            return
        }

        _state.value = TranslationState.Capturing
        serviceScope.launch {
            try {
                // 1. Ẩn nút nổi tạm thời để không bị dính vào ảnh chụp màn hình
                floatingTriggerView?.visibility = View.INVISIBLE
                delay(OverlayConfig.TRIGGER_HIDE_DELAY_MS)

                // 2. Chụp khung hình màn hình hiện tại
                val captureService = ScreenCaptureService.instance
                if (captureService == null) {
                    val err = CreamError.CaptureError.PermissionDenied()
                    _state.value = TranslationState.Error(err)
                    Toast.makeText(
                        this@OverlayService,
                        getString(R.string.overlay_capture_not_ready),
                        Toast.LENGTH_SHORT,
                    ).show()
                    floatingTriggerView?.visibility = View.VISIBLE
                    return@launch
                }

                val bitmap = captureService.captureCurrentScreen()
                floatingTriggerView?.visibility = View.VISIBLE

                if (bitmap == null) {
                    val err = CreamError.CaptureError.FrameAcquireTimeout()
                    _state.value = TranslationState.Error(err)
                    Toast.makeText(
                        this@OverlayService,
                        getString(R.string.overlay_capture_failed),
                        Toast.LENGTH_SHORT,
                    ).show()
                    return@launch
                }

                val results = processScreenCapture(bitmap) ?: return@launch

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
                Toast.makeText(
                    this@OverlayService,
                    getString(R.string.overlay_error_prefix, e.message ?: ""),
                    Toast.LENGTH_SHORT,
                ).show()
            } finally {
                floatingTriggerView?.visibility = View.VISIBLE
                if (_state.value is TranslationState.Capturing || _state.value is TranslationState.Processing) {
                    _state.value = TranslationState.Idle
                }
            }
        }
    }

    private suspend fun processScreenCapture(bitmap: Bitmap): List<TranslationResult>? {
        return try {
            _state.value = TranslationState.Processing("OCR")
            Toast.makeText(
                this@OverlayService,
                getString(R.string.overlay_ocr_recognizing),
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
                Toast.makeText(
                    this@OverlayService,
                    getString(R.string.overlay_ocr_no_text),
                    Toast.LENGTH_SHORT,
                ).show()
                return null
            }

            // Lọc bỏ các khối chữ thuộc danh sách từ phổ biến/ký hiệu không cần dịch
            val eligibleRegions = textRegions.filterNot { textFilter.shouldFilter(it.originalText) }

            if (eligibleRegions.isEmpty()) {
                Timber.d("Toàn bộ các khối chữ nhận diện được đã được lọc bỏ (từ phổ biến/ký hiệu)")
                Toast.makeText(
                    this@OverlayService,
                    getString(R.string.overlay_no_new_content_filtered),
                    Toast.LENGTH_SHORT,
                ).show()
                _state.value = TranslationState.Idle
                return null
            }

            _state.value = TranslationState.Processing("Translate")
            Toast.makeText(
                this@OverlayService,
                getString(R.string.overlay_translating_blocks, eligibleRegions.size),
                Toast.LENGTH_SHORT,
            ).show()

            // 4. Dịch on-device và lấy mẫu màu nền thông minh (Inpainting)
            withContext(Dispatchers.Default) {
                val startTime = System.currentTimeMillis()
                val textsToTranslate = eligibleRegions.map { it.originalText }
                val translatedTexts =
                    translationEngine.translateBatch(
                        textsToTranslate,
                        currentLanguagePair,
                    )
                val duration = System.currentTimeMillis() - startTime

                eligibleRegions.mapIndexed { index, region ->
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
                        executionTimeMs = duration,
                    )
                }
            }
        } finally {
            // Prevent native graphic memory leaks by immediately recycling full-frame capture
            bitmap.recycle()
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
            Toast.makeText(this, getString(R.string.overlay_tap_hint), Toast.LENGTH_LONG).show()
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
        try {
            unregisterReceiver(systemEventsReceiver)
        } catch (e: Throwable) {
            Timber.w(e, "Lỗi unregister systemEventsReceiver")
        }
        snapAnimator?.cancel()
        snapAnimator = null

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

    companion object {
        const val ACTION_STOP = "com.cream.translator.action.STOP_OVERLAY"
        const val EXTRA_SOURCE_LANG = "extra_source_lang"
        const val EXTRA_TARGET_LANG = "extra_target_lang"
    }
}
