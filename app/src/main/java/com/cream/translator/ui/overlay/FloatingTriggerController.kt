package com.cream.translator.ui.overlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import com.cream.translator.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Điều khiển giao diện bong bóng nổi, cử chỉ chạm/kéo và animation hít mép màn hình.
 */
class FloatingTriggerController(
    private val context: Context,
    private val windowManager: IOverlayWindowManager,
    private val coroutineScope: CoroutineScope,
) {
    var floatingTriggerView: View? = null
        private set

    var triggerParams: WindowManager.LayoutParams? = null
        private set

    private var snapAnimator: ValueAnimator? = null
    private var longPressJob: Job? = null
    private var pendingSingleTapJob: Job? = null

    val isVisible: Boolean
        get() = floatingTriggerView?.visibility == View.VISIBLE

    fun setVisibility(visible: Boolean) {
        floatingTriggerView?.visibility = if (visible) View.VISIBLE else View.INVISIBLE
    }

    @SuppressLint("ClickableViewAccessibility")
    fun setupFloatingTrigger(
        onClick: () -> Unit,
        onDoubleTap: () -> Unit,
        onLongPress: () -> Unit,
    ): Boolean {
        if (!Settings.canDrawOverlays(context)) {
            Timber.w("FloatingTriggerController: Quyền vẽ đè (Overlay) chưa được cấp")
            return false
        }

        if (floatingTriggerView != null) {
            destroy()
        }

        val layoutType =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

        val density = context.resources.displayMetrics.density
        val size = (OverlayConfig.TRIGGER_SIZE_DP * density).toInt()

        val params =
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
        triggerParams = params

        val triggerButton =
            ImageView(context).apply {
                setImageResource(R.drawable.ic_notification_capture)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                val pad = (11 * density).toInt()
                setPadding(pad, pad, pad, pad)

                val bg =
                    GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.parseColor("#E11D48"))
                        setStroke((2 * density).toInt(), Color.WHITE)
                    }
                background = bg
                elevation = 14f
            }

        attachDragAndClickListener(triggerButton, params, onClick, onDoubleTap, onLongPress)

        val attached = windowManager.safeAddView(triggerButton, params)
        return if (attached) {
            floatingTriggerView = triggerButton
            Timber.d("Floating trigger attached successfully")
            true
        } else {
            triggerButton.setOnTouchListener(null)
            triggerParams = null
            Timber.e("Failed to attach floating overlay view")
            false
        }
    }

    private fun animateSnapToEdge(
        view: View,
        params: WindowManager.LayoutParams,
    ) {
        val screenWidth = windowManager.getScreenWidth()
        val screenHeight = windowManager.getScreenHeight()
        val density = context.resources.displayMetrics.density
        val size = (OverlayConfig.TRIGGER_SIZE_DP * density).toInt()
        val margin = (12 * density).toInt()

        val safeMaxY = (screenHeight - size - margin).coerceAtLeast(margin)
        params.y = params.y.coerceIn(margin, safeMaxY)

        val isLeft = (params.x + size / 2) < (screenWidth / 2)
        val rightEdgeX = (screenWidth - size - margin).coerceAtLeast(margin)
        val targetX = if (isLeft) margin else rightEdgeX

        snapAnimator?.cancel()
        snapAnimator =
            ValueAnimator.ofInt(params.x, targetX).apply {
                duration = 250
                interpolator = DecelerateInterpolator()
                addUpdateListener { animation ->
                    if (floatingTriggerView != null && (view.isAttachedToWindow || view.windowToken != null)) {
                        params.x = animation.animatedValue as Int
                        windowManager.safeUpdateViewLayout(view, params)
                    }
                }
                start()
            }
    }

    private fun attachDragAndClickListener(
        view: View,
        params: WindowManager.LayoutParams,
        onClick: () -> Unit,
        onDoubleTap: () -> Unit,
        onLongPress: () -> Unit,
    ) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isClick = true
        var lastTapUpTimeMs = -1L
        val doubleTapWindowMs = 300L
        var gestureScreenWidth = windowManager.getScreenWidth()
        var gestureScreenHeight = windowManager.getScreenHeight()
        var gestureSize = params.width
        var gestureMargin = (12 * context.resources.displayMetrics.density).toInt()

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    snapAnimator?.cancel()
                    longPressJob?.cancel()
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isClick = true

                    val density = context.resources.displayMetrics.density
                    gestureScreenWidth = windowManager.getScreenWidth()
                    gestureScreenHeight = windowManager.getScreenHeight()
                    gestureSize = (OverlayConfig.TRIGGER_SIZE_DP * density).toInt()
                    gestureMargin = (12 * density).toInt()

                    longPressJob =
                        coroutineScope.launch {
                            delay(800)
                            if (isClick) {
                                isClick = false
                                pendingSingleTapJob?.cancel()
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                onLongPress()
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
                        lastTapUpTimeMs = -1L
                        longPressJob?.cancel()
                        pendingSingleTapJob?.cancel()
                    }
                    val safeMaxX = (gestureScreenWidth - gestureSize + gestureMargin).coerceAtLeast(-gestureMargin)
                    params.x = (initialX + dx).coerceIn(-gestureMargin, safeMaxX)
                    val safeMaxY = (gestureScreenHeight - gestureSize - gestureMargin).coerceAtLeast(gestureMargin)
                    params.y = (initialY + dy).coerceIn(gestureMargin, safeMaxY)
                    windowManager.safeUpdateViewLayout(view, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    longPressJob?.cancel()
                    if (isClick) {
                        val now = android.os.SystemClock.uptimeMillis()
                        if (lastTapUpTimeMs >= 0L && now - lastTapUpTimeMs <= doubleTapWindowMs) {
                            // Double-tap xác nhận: hủy single-tap đang chờ và gọi onDoubleTap
                            pendingSingleTapJob?.cancel()
                            lastTapUpTimeMs = -1L
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onDoubleTap()
                        } else {
                            // Tap đầu tiên: ghi lại thời điểm, chờ xem có double-tap không
                            val capturedTime = now
                            lastTapUpTimeMs = capturedTime
                            pendingSingleTapJob =
                                coroutineScope.launch {
                                    delay(doubleTapWindowMs + 20)
                                    // Nếu lastTapUpTimeMs vẫn là capturedTime → không có double-tap
                                    if (lastTapUpTimeMs == capturedTime) {
                                        lastTapUpTimeMs = -1L
                                        onClick()
                                    }
                                }
                        }
                    } else {
                        animateSnapToEdge(view, params)
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    longPressJob?.cancel()
                    pendingSingleTapJob?.cancel()
                    lastTapUpTimeMs = -1L
                    if (!isClick) {
                        animateSnapToEdge(view, params)
                    }
                    true
                }
                else -> false
            }
        }
    }

    fun handleConfigurationChange() {
        floatingTriggerView?.let { trigger ->
            triggerParams?.let { params ->
                val screenWidth = windowManager.getScreenWidth()
                val screenHeight = windowManager.getScreenHeight()
                val density = context.resources.displayMetrics.density
                val margin = (12 * density).toInt()
                val maxX = (screenWidth - params.width - margin).coerceAtLeast(margin)
                val maxY = (screenHeight - params.height - margin).coerceAtLeast(margin)
                params.x = params.x.coerceIn(margin, maxX)
                params.y = params.y.coerceIn(margin, maxY)
                windowManager.safeUpdateViewLayout(trigger, params)
            }
        }
    }

    fun destroy() {
        snapAnimator?.cancel()
        snapAnimator = null
        longPressJob?.cancel()
        longPressJob = null
        pendingSingleTapJob?.cancel()
        pendingSingleTapJob = null
        floatingTriggerView?.let { view ->
            view.setOnTouchListener(null)
            windowManager.safeRemoveView(view)
            floatingTriggerView = null
        }
        triggerParams = null
    }
}
