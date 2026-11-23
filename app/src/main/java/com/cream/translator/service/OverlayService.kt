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
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import com.cream.translator.core.engine.MlKitOcrEngine
import com.cream.translator.core.engine.MlKitTranslationEngine
import com.cream.translator.core.engine.TextInpainter
import com.cream.translator.core.model.LanguagePair
import com.cream.translator.core.model.TranslationResult
import com.cream.translator.ui.overlay.InPlaceTranslationOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class OverlayService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var windowManager: WindowManager? = null

    private var floatingTriggerView: View? = null
    private var triggerParams: WindowManager.LayoutParams? = null

    private var inPlaceOverlay: InPlaceTranslationOverlay? = null
    private var isOverlayShowing = false

    private val ocrEngine = MlKitOcrEngine()
    private val translationEngine = MlKitTranslationEngine()
    private var isProcessing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        setupFloatingTrigger()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFloatingTrigger() {
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

        // Stylish circular floating trigger button with "CT" brand text
        val triggerButton =
            TextView(this).apply {
                text = "CT"
                textSize = 18f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER

                val bg =
                    GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.parseColor("#EE0084FF"))
                        setStroke((2 * resources.displayMetrics.density).toInt(), Color.WHITE)
                    }
                background = bg
                elevation = 12f
            }

        attachDragAndClickListener(triggerButton, triggerParams!!)

        try {
            windowManager?.addView(triggerButton, triggerParams)
            floatingTriggerView = triggerButton
            Timber.d("Floating trigger attached successfully")
            Toast.makeText(this, "CreamTranslator: Đã kích hoạt nút dịch nổi (CT)", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Timber.e(e, "Failed to attach floating overlay view")
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
                    windowManager?.updateViewLayout(view, params)
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

        if (isProcessing) {
            Toast.makeText(this, "Đang xử lý, vui lòng chờ...", Toast.LENGTH_SHORT).show()
            return
        }

        isProcessing = true
        serviceScope.launch {
            try {
                // 1. Ẩn nút nổi tạm thời để không bị dính vào ảnh chụp màn hình
                floatingTriggerView?.visibility = View.INVISIBLE
                delay(60)

                // 2. Chụp khung hình màn hình hiện tại
                val captureService = ScreenCaptureService.instance
                if (captureService == null) {
                    Toast.makeText(
                        this@OverlayService,
                        "Dịch vụ chụp màn hình chưa sẵn sàng",
                        Toast.LENGTH_SHORT,
                    ).show()
                    floatingTriggerView?.visibility = View.VISIBLE
                    isProcessing = false
                    return@launch
                }

                val bitmap = captureService.captureCurrentScreen()
                floatingTriggerView?.visibility = View.VISIBLE

                if (bitmap == null) {
                    Toast.makeText(
                        this@OverlayService,
                        "Không thể chụp màn hình (hoặc màn hình được bảo mật)",
                        Toast.LENGTH_SHORT,
                    ).show()
                    isProcessing = false
                    return@launch
                }

                val results =
                    try {
                        Toast.makeText(this@OverlayService, "Đang nhận diện văn bản...", Toast.LENGTH_SHORT).show()

                        // 3. Chạy OCR On-Device bằng ML Kit
                        val textRegions =
                            withContext(Dispatchers.Default) {
                                ocrEngine.recognizeText(bitmap)
                            }

                        if (textRegions.isEmpty()) {
                            Toast.makeText(this@OverlayService, "Không tìm thấy chữ trên màn hình", Toast.LENGTH_SHORT).show()
                            isProcessing = false
                            return@launch
                        }

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
                displayTranslationOverlay(results)
            } catch (e: Exception) {
                Timber.e(e, "Lỗi trong quá trình dịch màn hình")
                Toast.makeText(this@OverlayService, "Lỗi xử lý: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                floatingTriggerView?.visibility = View.VISIBLE
                isProcessing = false
            }
        }
    }

    private fun displayTranslationOverlay(results: List<TranslationResult>) {
        if (isOverlayShowing) {
            dismissTranslationOverlay()
        }

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
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            )

        val overlayView =
            InPlaceTranslationOverlay(this).apply {
                setResults(results)
                onDismissListener = {
                    dismissTranslationOverlay()
                }
            }

        try {
            windowManager?.addView(overlayView, overlayParams)
            inPlaceOverlay = overlayView
            isOverlayShowing = true
            Toast.makeText(this, "Chạm vào chữ để đổi bản gốc/dịch. Chạm ra ngoài để đóng.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Timber.e(e, "Không thể gắn lớp phủ dịch đè lên màn hình")
        }
    }

    private fun dismissTranslationOverlay() {
        if (isOverlayShowing && inPlaceOverlay != null) {
            try {
                windowManager?.removeView(inPlaceOverlay)
            } catch (e: Exception) {
                Timber.e(e, "Lỗi khi gỡ lớp phủ dịch")
            } finally {
                inPlaceOverlay = null
                isOverlayShowing = false
            }
        }
    }

    override fun onDestroy() {
        dismissTranslationOverlay()
        if (floatingTriggerView != null) {
            try {
                windowManager?.removeView(floatingTriggerView)
            } catch (e: Exception) {
                Timber.e(e, "Error removing floating view on destroy")
            }
            floatingTriggerView = null
        }
        ocrEngine.close()
        translationEngine.close()
        serviceScope.cancel()
        super.onDestroy()
        Timber.d("OverlayService destroyed")
    }
}
