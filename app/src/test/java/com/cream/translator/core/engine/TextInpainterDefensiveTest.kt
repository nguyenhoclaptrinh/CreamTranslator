package com.cream.translator.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextInpainterDefensiveTest {
    @Test
    fun calculateOptimalFontSize_withNegativeOrZeroDimensions_returnsFallback() {
        assertEquals(14.0f, TextInpainter.calculateOptimalFontSize(-10, 100, 20), 0.01f)
        assertEquals(14.0f, TextInpainter.calculateOptimalFontSize(100, -10, 20), 0.01f)
        assertEquals(14.0f, TextInpainter.calculateOptimalFontSize(100, 100, 0), 0.01f)
        assertEquals(14.0f, TextInpainter.calculateOptimalFontSize(100, 100, -5), 0.01f)
        assertEquals(14.0f, TextInpainter.calculateOptimalFontSize(100, 100, 20, density = 0f), 0.01f)
        assertEquals(14.0f, TextInpainter.calculateOptimalFontSize(100, 100, 20, density = -1f), 0.01f)
    }

    @Test
    fun calculateOptimalFontSize_clampedBetweenLimits() {
        // Enormous box with small text -> clamped to 28f
        val large = TextInpainter.calculateOptimalFontSize(2000, 2000, 5, density = 1.0f)
        assertEquals(28.0f, large, 0.01f)

        // Tiny box with large text -> clamped to 10f
        val small = TextInpainter.calculateOptimalFontSize(10, 10, 500, density = 2.0f)
        assertEquals(10.0f, small, 0.01f)
    }

    @Test
    fun calculateContrastingTextColor_handlesPureBlackAndPureWhite() {
        val pureBlack = (0xFF shl 24) or 0
        val pureWhite = (0xFF shl 24) or (0xFF shl 16) or (0xFF shl 8) or 0xFF

        // Contrasting color for black background should be white
        val onBlack = TextInpainter.calculateContrastingTextColor(pureBlack)
        // Contrasting color for white background should be black
        val onWhite = TextInpainter.calculateContrastingTextColor(pureWhite)

        assertTrue(onBlack != onWhite)
    }
}
