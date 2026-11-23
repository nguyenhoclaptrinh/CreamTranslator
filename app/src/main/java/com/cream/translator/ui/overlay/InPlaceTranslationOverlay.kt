package com.cream.translator.ui.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.cream.translator.core.engine.TextInpainter
import com.cream.translator.core.model.TranslationResult

class InPlaceTranslationOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val results = mutableListOf<TranslationResult>()
    private val toggledRegions = mutableSetOf<String>()
    var onDismissListener: (() -> Unit)? = null

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isSubpixelText = true
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#80FFFFFF")
    }

    fun setResults(newResults: List<TranslationResult>) {
        results.clear()
        results.addAll(newResults)
        toggledRegions.clear()
        invalidate()
    }

    fun clear() {
        results.clear()
        toggledRegions.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (item in results) {
            val rect = item.region.boundingBox
            val rectF = RectF(
                rect.left.toFloat(),
                rect.top.toFloat(),
                rect.right.toFloat(),
                rect.bottom.toFloat()
            )

            val isShowingOriginal = toggledRegions.contains(item.region.id)
            val displayText = if (isShowingOriginal) item.region.originalText else item.translatedText

            val bgColor = item.region.backgroundColor ?: Color.parseColor("#EE1E1E1E")
            bgPaint.color = bgColor

            val textColor = item.region.textColor
                ?: TextInpainter.calculateContrastingTextColor(bgColor)
            textPaint.color = textColor

            val fontSize = TextInpainter.calculateOptimalFontSize(
                rect.width(),
                rect.height(),
                displayText.length,
                resources.displayMetrics.density
            )
            textPaint.textSize = fontSize * resources.displayMetrics.density

            // Draw rounded inpainting background plate
            canvas.drawRoundRect(rectF, 8f, 8f, bgPaint)
            canvas.drawRoundRect(rectF, 8f, 8f, borderPaint)

            // Center text inside the bounding box
            val fontMetrics = textPaint.fontMetrics
            val baseline = rectF.centerY() - (fontMetrics.ascent + fontMetrics.descent) / 2
            val textWidth = textPaint.measureText(displayText)
            val startX = (rectF.centerX() - textWidth / 2).coerceAtLeast(rectF.left + 4f)

            canvas.drawText(displayText, startX, baseline, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val x = event.x.toInt()
            val y = event.y.toInt()

            var hitText = false
            for (item in results) {
                if (item.region.boundingBox.contains(x, y)) {
                    hitText = true
                    // Toggle between original text and translated text
                    if (toggledRegions.contains(item.region.id)) {
                        toggledRegions.remove(item.region.id)
                    } else {
                        toggledRegions.add(item.region.id)
                    }
                    invalidate()
                    return true
                }
            }

            // Tapping empty space dismisses the translation overlay
            if (!hitText && results.isNotEmpty()) {
                onDismissListener?.invoke()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
