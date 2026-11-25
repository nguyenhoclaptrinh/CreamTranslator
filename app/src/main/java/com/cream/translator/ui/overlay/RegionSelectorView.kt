package com.cream.translator.ui.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.max
import kotlin.math.min

/**
 * View toàn màn hình cho phép người dùng kéo chọn vùng màn hình cần dịch.
 *
 * Tuân thủ bất biến kiến trúc:
 * - `isClickable = true` để tiêu thụ ACTION_DOWN (android-invariants.md §2.2).
 * - Hủy coroutine auto-dismiss tại `onDetachedFromWindow()` (architecture-invariants.md §2.2).
 * - [autoDismissScope] được truyền từ caller — View không tự tạo scope riêng.
 */
class RegionSelectorView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : View(context, attrs, defStyleAttr) {
        companion object {
            private const val MIN_SELECTION_PX = 20
            private const val AUTO_DISMISS_DELAY_MS = 10_000L
        }

        private var startX = 0f
        private var startY = 0f
        private var currentX = 0f
        private var currentY = 0f
        private var isSelecting = false

        private var onRegionSelectedListener: ((Rect) -> Unit)? = null
        var onCancelListener: (() -> Unit)? = null

        private var autoDismissJob: Job? = null

        private val selectionPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 4f
                color = Color.parseColor("#00E5FF")
            }

        private val fillPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.parseColor("#3300E5FF")
            }

        private val dimPaint =
            Paint().apply {
                style = Paint.Style.FILL
                color = Color.parseColor("#66000000")
            }

        init {
            // Bắt buộc theo android-invariants.md §2.2: View overlay phải isClickable = true
            isClickable = true
        }

        fun setOnRegionSelectedListener(listener: (Rect) -> Unit) {
            onRegionSelectedListener = listener
        }

        fun clearListeners() {
            onRegionSelectedListener = null
            onCancelListener = null
        }

        /**
         * Khởi động bộ đếm tự động hủy sau [AUTO_DISMISS_DELAY_MS] ms.
         * Scope được truyền vào từ caller — không tự tạo scope (architecture-invariants.md §2.2).
         */
        fun startAutoDismissTimer(scope: CoroutineScope) {
            autoDismissJob?.cancel()
            autoDismissJob =
                scope.launch {
                    delay(AUTO_DISMISS_DELAY_MS)
                    Timber.d("RegionSelectorView: auto-dismiss timeout kích hoạt")
                    onCancelListener?.invoke()
                }
        }

        fun cancelAutoDismiss() {
            autoDismissJob?.cancel()
            autoDismissJob = null
        }

        override fun onDetachedFromWindow() {
            // Bắt buộc theo architecture-invariants.md §2.2: hủy coroutine khi View bị tháo
            cancelAutoDismiss()
            super.onDetachedFromWindow()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            // Vẽ lớp làm tối toàn màn hình để người dùng dễ nhận ra chế độ chọn vùng
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)

            if (isSelecting) {
                val left = min(startX, currentX)
                val top = min(startY, currentY)
                val right = max(startX, currentX)
                val bottom = max(startY, currentY)

                canvas.drawRect(left, top, right, bottom, fillPaint)
                canvas.drawRect(left, top, right, bottom, selectionPaint)
            }
        }

        private fun clampX(x: Float): Float = if (width > 0) x.coerceIn(0f, width.toFloat()) else x.coerceAtLeast(0f)

        private fun clampY(y: Float): Float = if (height > 0) y.coerceIn(0f, height.toFloat()) else y.coerceAtLeast(0f)

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    cancelAutoDismiss()
                    startX = clampX(event.x)
                    startY = clampY(event.y)
                    currentX = startX
                    currentY = startY
                    isSelecting = true
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    currentX = clampX(event.x)
                    currentY = clampY(event.y)
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    isSelecting = false
                    val rect =
                        Rect(
                            min(startX, currentX).toInt(),
                            min(startY, currentY).toInt(),
                            max(startX, currentX).toInt(),
                            max(startY, currentY).toInt(),
                        )
                    if (rect.width() > MIN_SELECTION_PX && rect.height() > MIN_SELECTION_PX) {
                        cancelAutoDismiss()
                        onRegionSelectedListener?.invoke(rect)
                    } else {
                        // Kéo quá nhỏ hoặc chỉ tap → hủy chế độ chọn vùng
                        Timber.d("RegionSelectorView: vùng chọn quá nhỏ, hủy")
                        cancelAutoDismiss()
                        onCancelListener?.invoke()
                    }
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    isSelecting = false
                    cancelAutoDismiss()
                    onCancelListener?.invoke()
                    invalidate()
                    return true
                }
            }
            return super.onTouchEvent(event)
        }
    }
