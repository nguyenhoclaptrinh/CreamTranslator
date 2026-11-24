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
        private val renderedPlates = mutableMapOf<String, RectF>()
        private val windowLocationOnScreen = IntArray(2)
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
                strokeWidth = OverlayConfig.OVERLAY_BORDER_WIDTH
                color = Color.parseColor(OverlayConfig.OVERLAY_BORDER_COLOR)
            }

        init {
            isClickable = true
            fitsSystemWindows = false
            setPadding(0, 0, 0, 0)
        }

        fun setResults(newResults: List<TranslationResult>) {
            results.clear()
            results.addAll(newResults)
            toggledRegions.clear()
            renderedPlates.clear()
            invalidate()
        }

        fun clear() {
            results.clear()
            toggledRegions.clear()
            renderedPlates.clear()
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            getLocationOnScreen(windowLocationOnScreen)
            val windowOffsetX = windowLocationOnScreen[0].toFloat()
            val windowOffsetY = windowLocationOnScreen[1].toFloat()
            for (item in results) {
                drawTranslationItem(canvas, item, windowOffsetX, windowOffsetY)
            }
        }

        private fun drawTranslationItem(
            canvas: Canvas,
            item: TranslationResult,
            windowOffsetX: Float,
            windowOffsetY: Float,
        ) {
            val rect = item.region.boundingBox
            val density = resources.displayMetrics.density
            val padX = 6f * density
            val padY = 4f * density
            val cornerRadius = 6f * density

            val isShowingOriginal = toggledRegions.contains(item.region.id)
            val displayText = if (isShowingOriginal) item.region.originalText else item.translatedText

            val bgColor = item.region.backgroundColor ?: Color.parseColor(OverlayConfig.OVERLAY_BG_COLOR)
            bgPaint.color = bgColor

            val textColor =
                item.region.textColor
                    ?: TextInpainter.calculateContrastingTextColor(bgColor)
            textPaint.color = textColor

            // Map physical screen coordinates to local view canvas space
            val targetBox =
                TextInpainter.BoxCoordinates(
                    rect.left.toFloat(),
                    rect.top.toFloat(),
                    rect.right.toFloat(),
                    rect.bottom.toFloat(),
                ).offset(-windowOffsetX, -windowOffsetY)

            // Gather other bounding boxes mapped to local view coordinates
            val otherBoxes =
                results.mapNotNull { other ->
                    if (other.region.id != item.region.id) {
                        val ob = other.region.boundingBox
                        TextInpainter.BoxCoordinates(
                            ob.left.toFloat(),
                            ob.top.toFloat(),
                            ob.right.toFloat(),
                            ob.bottom.toFloat(),
                        ).offset(-windowOffsetX, -windowOffsetY)
                    } else {
                        null
                    }
                }
            val constraints =
                TextInpainter.calculateCollisionFreeExpansion(
                    targetBox = targetBox,
                    otherBoxes = otherBoxes,
                    safetyMargin = 3f * density,
                )

            // Calculate starting font size
            var currentSp =
                TextInpainter.calculateOptimalFontSize(
                    rect.width(),
                    rect.height(),
                    displayText.length,
                    density,
                )
            textPaint.textSize = currentSp * density

            // For single-line labels or buttons, allow safe horizontal expansion if room permits
            val isSingleLine = rect.height() < 42f * density
            val baseContentWidth = maxOf(20f, rect.width() - 2f * padX)
            val targetContentWidth =
                if (isSingleLine) {
                    val naturalTextWidth = textPaint.measureText(displayText)
                    val maxSafeWidth = baseContentWidth + constraints.maxExpandLeft + constraints.maxExpandRight
                    naturalTextWidth.coerceIn(baseContentWidth, maxSafeWidth).toInt()
                } else {
                    baseContentWidth.toInt()
                }

            var staticLayout = createStaticLayout(displayText, targetContentWidth)

            // Downscale font size if rendered text exceeds bounding box height
            val targetHeight = rect.height() - 2f * padY
            while (staticLayout.height > targetHeight && currentSp > 10f) {
                currentSp = maxOf(10f, currentSp - 1f)
                textPaint.textSize = currentSp * density
                staticLayout = createStaticLayout(displayText, targetContentWidth)
            }

            // Compute adaptive plate bounds that respect neighboring box boundaries
            val plateBounds =
                TextInpainter.computePlateBounds(
                    box = targetBox,
                    textSize = Pair(staticLayout.width.toFloat(), staticLayout.height.toFloat()),
                    padding = Pair(padX, padY),
                    screenSize =
                        Pair(
                            width.toFloat().takeIf { it > 0 } ?: Float.MAX_VALUE,
                            height.toFloat().takeIf { it > 0 } ?: Float.MAX_VALUE,
                        ),
                    constraints = constraints,
                )
            val plateRectF = RectF(plateBounds.left, plateBounds.top, plateBounds.right, plateBounds.bottom)
            renderedPlates[item.region.id] = plateRectF

            // Draw rounded inpainting background plate
            canvas.drawRoundRect(plateRectF, cornerRadius, cornerRadius, bgPaint)
            canvas.drawRoundRect(plateRectF, cornerRadius, cornerRadius, borderPaint)

            // Center static layout vertically and horizontally inside the adaptive plate
            canvas.save()
            val textOffsetX = plateRectF.centerX() - staticLayout.width / 2f
            val textOffsetY = plateRectF.centerY() - staticLayout.height / 2f
            canvas.translate(textOffsetX, textOffsetY)
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
                        val plate = renderedPlates[item.region.id]
                        val bb = item.region.boundingBox
                        val offsetX = windowLocationOnScreen[0]
                        val offsetY = windowLocationOnScreen[1]
                        val isHit =
                            plate?.contains(event.x, event.y)
                                ?: (
                                    x in (bb.left - offsetX)..(bb.right - offsetX) &&
                                        y in (bb.top - offsetY)..(bb.bottom - offsetY)
                                )
                        if (isHit) {
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
