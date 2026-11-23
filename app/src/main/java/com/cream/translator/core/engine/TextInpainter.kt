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
     */
    fun sampleBackgroundColor(
        bitmap: Bitmap,
        box: Rect,
    ): Int {
        val width = bitmap.width
        val height = bitmap.height

        val left = max(0, box.left - 3)
        val right = min(width - 1, box.right + 3)
        val top = max(0, box.top - 3)
        val bottom = min(height - 1, box.bottom + 3)

        var redSum = 0L
        var greenSum = 0L
        var blueSum = 0L
        var count = 0

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
        val red = Color.red(backgroundColor)
        val green = Color.green(backgroundColor)
        val blue = Color.blue(backgroundColor)

        // Relative luminance calculation
        val luminance = (0.299 * red + 0.587 * green + 0.114 * blue) / 255.0
        return if (luminance > 0.5) Color.BLACK else Color.WHITE
    }

    /**
     * Calculates optimal dynamic font size (in SP / pixels) to fit inside the bounding box.
     */
    fun calculateOptimalFontSize(
        boxWidth: Int,
        boxHeight: Int,
        textLength: Int,
        density: Float = 2.0f,
    ): Float {
        if (textLength <= 0 || boxWidth <= 0 || boxHeight <= 0) return 14f

        val area = boxWidth.toFloat() * boxHeight.toFloat()
        // Approximate character cell dimension
        val targetCharArea = area / (textLength * 1.2f)
        val calculatedSize = sqrt(targetCharArea) / density

        // Clamp font size between 10sp and 28sp for readability in mobile games
        return calculatedSize.coerceIn(10f, 28f)
    }
}
