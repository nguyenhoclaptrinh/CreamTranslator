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
        private data class PreparedPlate(
            val regionId: String,
            val plateRect: RectF,
            val bgColor: Int,
            val staticLayout: StaticLayout,
        )

        private val results = mutableListOf<TranslationResult>()
        private val toggledRegions = mutableSetOf<String>()
        private val renderedPlates = mutableMapOf<String, RectF>()
        private val preparedPlates = mutableListOf<PreparedPlate>()
        private val windowLocationOnScreen = IntArray(2)
        private var cachedWidth = -1
        private var cachedHeight = -1
        private var cachedWindowOffsetX = Int.MIN_VALUE
        private var cachedWindowOffsetY = Int.MIN_VALUE
        private var layoutDirty = true
        var onDismissListener: (() -> Unit)? = null

        private val defaultBgColor = Color.parseColor(OverlayConfig.OVERLAY_BG_COLOR)

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
            preparedPlates.clear()
            layoutDirty = true
            invalidate()
        }

        fun clear() {
            results.clear()
            toggledRegions.clear()
            renderedPlates.clear()
            preparedPlates.clear()
            layoutDirty = true
            invalidate()
        }

        override fun onSizeChanged(
            w: Int,
            h: Int,
            oldw: Int,
            oldh: Int,
        ) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (w != oldw || h != oldh) {
                layoutDirty = true
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (results.isEmpty()) return

            getLocationOnScreen(windowLocationOnScreen)
            val windowOffsetX = windowLocationOnScreen[0]
            val windowOffsetY = windowLocationOnScreen[1]

            if (needsPlateRebuild(windowOffsetX, windowOffsetY)) {
                rebuildPreparedPlates(windowOffsetX.toFloat(), windowOffsetY.toFloat())
                cachedWidth = width
                cachedHeight = height
                cachedWindowOffsetX = windowOffsetX
                cachedWindowOffsetY = windowOffsetY
                layoutDirty = false
            }

            val density = resources.displayMetrics.density
            val cornerRadius = 6f * density
            for (plate in preparedPlates) {
                bgPaint.color = plate.bgColor
                canvas.drawRoundRect(plate.plateRect, cornerRadius, cornerRadius, bgPaint)
                canvas.drawRoundRect(plate.plateRect, cornerRadius, cornerRadius, borderPaint)

                canvas.save()
                val textOffsetX = plate.plateRect.centerX() - plate.staticLayout.width / 2f
                val textOffsetY = plate.plateRect.centerY() - plate.staticLayout.height / 2f
                canvas.translate(textOffsetX, textOffsetY)
                plate.staticLayout.draw(canvas)
                canvas.restore()
            }
        }

        private fun needsPlateRebuild(
            windowOffsetX: Int,
            windowOffsetY: Int,
        ): Boolean {
            val sizeChanged = width != cachedWidth || height != cachedHeight
            val offsetChanged = windowOffsetX != cachedWindowOffsetX || windowOffsetY != cachedWindowOffsetY
            return layoutDirty || sizeChanged || offsetChanged
        }

        private fun rebuildPreparedPlates(
            windowOffsetX: Float,
            windowOffsetY: Float,
        ) {
            preparedPlates.clear()
            renderedPlates.clear()

            val localBoxes =
                results.map { item ->
                    val rect = item.region.boundingBox
                    TextInpainter.BoxCoordinates(
                        rect.left.toFloat() - windowOffsetX,
                        rect.top.toFloat() - windowOffsetY,
                        rect.right.toFloat() - windowOffsetX,
                        rect.bottom.toFloat() - windowOffsetY,
                    )
                }

            for (index in results.indices) {
                val prepared =
                    buildPreparedPlate(
                        item = results[index],
                        targetIndex = index,
                        localBoxes = localBoxes,
                    )
                preparedPlates.add(prepared)
                renderedPlates[prepared.regionId] = prepared.plateRect
            }
        }

        private fun buildPreparedPlate(
            item: TranslationResult,
            targetIndex: Int,
            localBoxes: List<TextInpainter.BoxCoordinates>,
        ): PreparedPlate {
            val rect = item.region.boundingBox
            val density = resources.displayMetrics.density
            val padX = 6f * density
            val padY = 4f * density

            val isShowingOriginal = toggledRegions.contains(item.region.id)
            val displayText = if (isShowingOriginal) item.region.originalText else item.translatedText

            val bgColor = item.region.backgroundColor ?: defaultBgColor
            val textColor =
                item.region.textColor
                    ?: TextInpainter.calculateContrastingTextColor(bgColor)

            val targetBox = localBoxes[targetIndex]
            val otherBoxes = localBoxes.filterIndexed { idx, _ -> idx != targetIndex }
            val constraints =
                TextInpainter.calculateCollisionFreeExpansion(
                    targetBox = targetBox,
                    otherBoxes = otherBoxes,
                    safetyMargin = 3f * density,
                )

            var currentSp =
                TextInpainter.calculateOptimalFontSize(
                    rect.width(),
                    rect.height(),
                    displayText.length,
                    density,
                )
            val platePaint =
                TextPaint(textPaint).apply {
                    color = textColor
                    textSize = currentSp * density
                }

            val isSingleLine = rect.height() < 42f * density
            val baseContentWidth = maxOf(20f, rect.width() - 2f * padX)
            val targetContentWidth =
                if (isSingleLine) {
                    val naturalTextWidth = platePaint.measureText(displayText)
                    val maxSafeWidth = baseContentWidth + constraints.maxExpandLeft + constraints.maxExpandRight
                    naturalTextWidth.coerceIn(baseContentWidth, maxSafeWidth).toInt()
                } else {
                    baseContentWidth.toInt()
                }

            var staticLayout = createStaticLayout(displayText, targetContentWidth, platePaint)

            val targetHeight = rect.height() - 2f * padY
            while (staticLayout.height > targetHeight && currentSp > 10f) {
                currentSp = maxOf(10f, currentSp - 1f)
                platePaint.textSize = currentSp * density
                staticLayout = createStaticLayout(displayText, targetContentWidth, platePaint)
            }

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
            return PreparedPlate(
                regionId = item.region.id,
                plateRect = RectF(plateBounds.left, plateBounds.top, plateBounds.right, plateBounds.bottom),
                bgColor = bgColor,
                staticLayout = staticLayout,
            )
        }

        private fun createStaticLayout(
            text: String,
            width: Int,
            paint: TextPaint,
        ): StaticLayout =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setIncludePad(false)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                StaticLayout(
                    text,
                    paint,
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
                            layoutDirty = true
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
