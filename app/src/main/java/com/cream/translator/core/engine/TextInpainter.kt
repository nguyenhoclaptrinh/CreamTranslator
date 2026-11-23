package com.cream.translator.core.engine

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object TextInpainter {
    /**
     * Samples pixels around the bounding box perimeter to find the dominant background color.
     * Guaranteed crash-safe against invalid bounding boxes and recycled bitmaps.
     */
    fun sampleBackgroundColor(
        bitmap: Bitmap,
        box: Rect,
    ): Int {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
            return Color.BLACK
        }

        val width = bitmap.width
        val height = bitmap.height

        val left = (box.left - 3).coerceIn(0, width - 1)
        val right = (box.right + 3).coerceIn(0, width - 1)
        val top = (box.top - 3).coerceIn(0, height - 1)
        val bottom = (box.bottom + 3).coerceIn(0, height - 1)

        if (left > right || top > bottom) {
            return Color.BLACK
        }

        var redSum = 0L
        var greenSum = 0L
        var blueSum = 0L
        var count = 0

        try {
            // Sample top and bottom edge borders
            for (x in left..right step 2) {
                val topPixel = bitmap.getPixel(x, top)
                redSum += Color.red(topPixel)
                greenSum += Color.green(topPixel)
                blueSum += Color.blue(topPixel)
                count++

                val bottomPixel = bitmap.getPixel(x, bottom)
                redSum += Color.red(bottomPixel)
                greenSum += Color.green(bottomPixel)
                blueSum += Color.blue(bottomPixel)
                count++
            }

            // Sample left and right borders
            for (y in top..bottom step 2) {
                val leftPixel = bitmap.getPixel(left, y)
                redSum += Color.red(leftPixel)
                greenSum += Color.green(leftPixel)
                blueSum += Color.blue(leftPixel)
                count++

                val rightPixel = bitmap.getPixel(right, y)
                redSum += Color.red(rightPixel)
                greenSum += Color.green(rightPixel)
                blueSum += Color.blue(rightPixel)
                count++
            }
        } catch (_: Throwable) {
            // Concurrently recycled bitmap or native pixel access failure fallback
            return Color.BLACK
        }

        if (count == 0) return Color.BLACK

        val avgRed = (redSum / count).toInt()
        val avgGreen = (greenSum / count).toInt()
        val avgBlue = (blueSum / count).toInt()

        return Color.rgb(avgRed, avgGreen, avgBlue)
    }

    /**
     * Determines optimal contrasting text color (White or Black) based on background luminance.
     */
    fun calculateContrastingTextColor(backgroundColor: Int): Int {
        val red = (backgroundColor shr 16) and 0xFF
        val green = (backgroundColor shr 8) and 0xFF
        val blue = backgroundColor and 0xFF

        // Relative luminance calculation
        val luminance = (0.299 * red + 0.587 * green + 0.114 * blue) / 255.0
        return if (luminance > 0.5) Color.BLACK else Color.WHITE
    }

    /**
     * Calculates optimal dynamic font size (in SP) to fit inside the bounding box.
     */
    fun calculateOptimalFontSize(
        boxWidth: Int,
        boxHeight: Int,
        textLength: Int,
        density: Float = 2.0f,
    ): Float {
        if (textLength <= 0 || boxWidth <= 0 || boxHeight <= 0 || density <= 0f) return 14f

        val safeDensity = if (density <= 0f) 1.0f else density
        val boxHeightDp = boxHeight / safeDensity
        val area = boxWidth.toFloat() * boxHeight.toFloat()

        // Approximate character cell dimension
        val targetCharArea = area / (textLength * 1.15f)
        val sizeFromArea = sqrt(targetCharArea) / safeDensity

        // For single-line or small height boxes, prevent font height from exceeding box height
        val estimatedLines = max(1, (textLength * sizeFromArea * safeDensity * 0.55f / boxWidth).toInt())
        val maxFontPerLine = (boxHeightDp / estimatedLines) * 0.85f

        val bestSize = min(sizeFromArea, maxFontPerLine)
        return bestSize.coerceIn(10f, 28f)
    }

    data class BoxCoordinates(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    ) {
        val width: Float get() = max(0f, right - left)
        val height: Float get() = max(0f, bottom - top)
        val centerX: Float get() = left + width / 2f
        val centerY: Float get() = top + height / 2f
    }

    data class ExpansionConstraints(
        val maxExpandLeft: Float = Float.MAX_VALUE,
        val maxExpandRight: Float = Float.MAX_VALUE,
        val maxExpandTop: Float = Float.MAX_VALUE,
        val maxExpandBottom: Float = Float.MAX_VALUE,
    )

    /**
     * Calculates the maximum safe expansion distances in all four directions
     * to prevent colliding with neighboring bounding boxes.
     */
    fun calculateCollisionFreeExpansion(
        targetBox: BoxCoordinates,
        otherBoxes: List<BoxCoordinates>,
        safetyMargin: Float = 4f,
    ): ExpansionConstraints {
        var maxExpandLeft = Float.MAX_VALUE
        var maxExpandRight = Float.MAX_VALUE
        var maxExpandTop = Float.MAX_VALUE
        var maxExpandBottom = Float.MAX_VALUE

        for (other in otherBoxes) {
            // Check vertical overlap for horizontal neighbors
            val hasVerticalOverlap =
                max(targetBox.top, other.top) < min(targetBox.bottom, other.bottom) + 8f
            if (hasVerticalOverlap) {
                if (other.left >= targetBox.right) {
                    val gap = max(0f, other.left - targetBox.right)
                    val safeHalf = max(0f, gap / 2f - safetyMargin)
                    maxExpandRight = min(maxExpandRight, safeHalf)
                } else if (other.right <= targetBox.left) {
                    val gap = max(0f, targetBox.left - other.right)
                    val safeHalf = max(0f, gap / 2f - safetyMargin)
                    maxExpandLeft = min(maxExpandLeft, safeHalf)
                }
            }

            // Check horizontal overlap for vertical neighbors
            val hasHorizontalOverlap =
                max(targetBox.left, other.left) < min(targetBox.right, other.right) + 8f
            if (hasHorizontalOverlap) {
                if (other.top >= targetBox.bottom) {
                    val gap = max(0f, other.top - targetBox.bottom)
                    val safeHalf = max(0f, gap / 2f - safetyMargin)
                    maxExpandBottom = min(maxExpandBottom, safeHalf)
                } else if (other.bottom <= targetBox.top) {
                    val gap = max(0f, targetBox.top - other.bottom)
                    val safeHalf = max(0f, gap / 2f - safetyMargin)
                    maxExpandTop = min(maxExpandTop, safeHalf)
                }
            }
        }

        return ExpansionConstraints(maxExpandLeft, maxExpandRight, maxExpandTop, maxExpandBottom)
    }

    /**
     * Calculates adaptive plate bounds that symmetrically expand if rendered text
     * exceeds the original bounding box, keeping text centered and within screen boundaries.
     */
    fun computePlateBounds(
        box: BoxCoordinates,
        textSize: Pair<Float, Float>,
        padding: Pair<Float, Float>,
        screenSize: Pair<Float, Float> = Pair(Float.MAX_VALUE, Float.MAX_VALUE),
        constraints: ExpansionConstraints = ExpansionConstraints(),
    ): BoxCoordinates {
        val textWidth = textSize.first
        val textHeight = textSize.second
        val horizontalPadding = padding.first
        val verticalPadding = padding.second

        val requiredWidth = textWidth + horizontalPadding * 2f
        val requiredHeight = textHeight + verticalPadding * 2f

        val desiredExpandX = max(0f, (requiredWidth - box.width) / 2f)
        val desiredExpandY = max(0f, (requiredHeight - box.height) / 2f)

        val expandLeft = min(desiredExpandX, constraints.maxExpandLeft)
        val expandRight = min(desiredExpandX, constraints.maxExpandRight)
        val expandTop = min(desiredExpandY, constraints.maxExpandTop)
        val expandBottom = min(desiredExpandY, constraints.maxExpandBottom)

        var left = box.left - expandLeft
        var right = box.right + expandRight
        var top = box.top - expandTop
        var bottom = box.bottom + expandBottom

        val screenWidth = screenSize.first
        val screenHeight = screenSize.second

        // Clamp to screen boundaries if boundaries are specified
        if (left < 0f) {
            val shift = -left
            left = 0f
            right = min(screenWidth, right + shift)
        }
        if (right > screenWidth) {
            val shift = right - screenWidth
            right = screenWidth
            left = max(0f, left - shift)
        }
        if (top < 0f) {
            val shift = -top
            top = 0f
            bottom = min(screenHeight, bottom + shift)
        }
        if (bottom > screenHeight) {
            val shift = bottom - screenHeight
            bottom = screenHeight
            top = max(0f, top - shift)
        }

        return BoxCoordinates(left, top, right, bottom)
    }
}
