package com.cream.translator.ui.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

class RegionSelectorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var startX = 0f
    private var startY = 0f
    private var currentX = 0f
    private var currentY = 0f
    private var isSelecting = false
    private var onRegionSelectedListener: ((Rect) -> Unit)? = null

    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.parseColor("#00E5FF")
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#3300E5FF")
    }

    fun setOnRegionSelectedListener(listener: (Rect) -> Unit) {
        onRegionSelectedListener = listener
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (isSelecting) {
            val left = min(startX, currentX)
            val top = min(startY, currentY)
            val right = max(startX, currentX)
            val bottom = max(startY, currentY)

            canvas.drawRect(left, top, right, bottom, fillPaint)
            canvas.drawRect(left, top, right, bottom, selectionPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                currentX = startX
                currentY = startY
                isSelecting = true
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                currentX = event.x
                currentY = event.y
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                isSelecting = false
                val rect = Rect(
                    min(startX, currentX).toInt(),
                    min(startY, currentY).toInt(),
                    max(startX, currentX).toInt(),
                    max(startY, currentY).toInt()
                )
                if (rect.width() > 20 && rect.height() > 20) {
                    onRegionSelectedListener?.invoke(rect)
                }
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
