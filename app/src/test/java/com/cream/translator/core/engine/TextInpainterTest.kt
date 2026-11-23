package com.cream.translator.core.engine

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextInpainterTest {
    @Test
    fun calculateContrastingTextColor_withDarkBackground_returnsWhite() {
        val darkColor = Color.rgb(20, 20, 20)
        val textColor = TextInpainter.calculateContrastingTextColor(darkColor)
        assertEquals(Color.WHITE, textColor)
    }

    @Test
    fun calculateContrastingTextColor_withBrightBackground_returnsBlack() {
        val brightColor = Color.rgb(240, 240, 240)
        val textColor = TextInpainter.calculateContrastingTextColor(brightColor)
        assertEquals(Color.BLACK, textColor)
    }

    @Test
    fun calculateOptimalFontSize_withinExpectedBounds() {
        // Standard game dialogue box dimensions (e.g. 400x100)
        val fontSize =
            TextInpainter.calculateOptimalFontSize(
                boxWidth = 400,
                boxHeight = 100,
                textLength = 40,
                density = 2.0f,
            )

        // Must be clamped within mobile game readability threshold (10sp to 28sp)
        assertTrue(fontSize in 10.0f..28.0f)
    }

    @Test
    fun calculateOptimalFontSize_withZeroDimensions_returnsDefault() {
        val fontSize = TextInpainter.calculateOptimalFontSize(0, 0, 0)
        assertEquals(14.0f, fontSize, 0.01f)
    }
}
