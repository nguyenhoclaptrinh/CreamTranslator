package com.cream.translator.ui.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.cream.translator.core.engine.TextInpainter
import com.cream.translator.core.model.TranslationResult

class InPlaceTranslationOverlay
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : View(context, attrs, defStyleAttr) {
        private val results = mutableListOf<TranslationResult>()
        private val toggledRegions = mutableSetOf<String>()
        var onDismissListener: (() -> Unit)? = null

        private val bgPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }

        private val textPaint =
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                isSubpixelText = true
            }

        private val borderPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f
                color = Color.parseColor("#80FFFFFF")
            }

        init {
            isClickable = true
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
                drawTranslationItem(canvas, item)
            }
        }

        private fun drawTranslationItem(
            canvas: Canvas,
            item: TranslationResult,
        ) {
            val rect = item.region.boundingBox
            val rectF =
                RectF(
                    rect.left.toFloat(),
                    rect.top.toFloat(),
                    rect.right.toFloat(),
                    rect.bottom.toFloat(),
                )

            val isShowingOriginal = toggledRegions.contains(item.region.id)
            val displayText = if (isShowingOriginal) item.region.originalText else item.translatedText

            val bgColor = item.region.backgroundColor ?: Color.parseColor("#EE1E1E1E")
            bgPaint.color = bgColor

            val textColor =
                item.region.textColor
                    ?: TextInpainter.calculateContrastingTextColor(bgColor)
            textPaint.color = textColor

            val fontSize =
                TextInpainter.calculateOptimalFontSize(
                    rect.width(),
                    rect.height(),
                    displayText.length,
                    resources.displayMetrics.density,
                )
            textPaint.textSize = fontSize * resources.displayMetrics.density

            // Draw rounded inpainting background plate
            canvas.drawRoundRect(rectF, 8f, 8f, bgPaint)
            canvas.drawRoundRect(rectF, 8f, 8f, borderPaint)

            val contentWidth = maxOf(1, rect.width() - 8)
            val staticLayout = createStaticLayout(displayText, contentWidth)

            // Center static layout vertically and horizontally inside bounding box
            canvas.save()
            val textHeight = staticLayout.height
            val offsetY = rectF.centerY() - textHeight / 2f
            canvas.translate(rectF.left + 4f, offsetY)
            staticLayout.draw(canvas)
            canvas.restore()
        }

        private fun createStaticLayout(
            text: String,
            width: Int,
        ): StaticLayout =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                StaticLayout.Builder.obtain(text, 0, text.length, textPaint, width)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setIncludePad(false)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                StaticLayout(
                    text,
                    textPaint,
                    width,
                    Layout.Alignment.ALIGN_CENTER,
                    1.0f,
                    0.0f,
                    false,
                )
            }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> return true
                MotionEvent.ACTION_UP -> {
                    val x = event.x.toInt()
                    val y = event.y.toInt()

                    for (item in results) {
                        if (item.region.boundingBox.contains(x, y)) {
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
                    if (results.isNotEmpty()) {
                        onDismissListener?.invoke()
                        return true
                    }
                }
            }
            return super.onTouchEvent(event)
        }
    }
