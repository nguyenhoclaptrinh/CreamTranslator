package com.cream.translator.core.engine

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextInpainterTest {
    @Test
    fun calculateContrastingTextColor_withDarkBackground_returnsWhite() {
        val darkColor = (0xFF shl 24) or (20 shl 16) or (20 shl 8) or 20
        val textColor = TextInpainter.calculateContrastingTextColor(darkColor)
        assertEquals(Color.WHITE, textColor)
    }

    @Test
    fun calculateContrastingTextColor_withBrightBackground_returnsBlack() {
        val brightColor = (0xFF shl 24) or (240 shl 16) or (240 shl 8) or 240
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

    @Test
    fun calculateOptimalFontSize_withNarrowSingleLine_clampsToReadableBounds() {
        val fontSize =
            TextInpainter.calculateOptimalFontSize(
                boxWidth = 300,
                boxHeight = 30,
                textLength = 20,
                density = 2.0f,
            )
        assertTrue(fontSize in 10.0f..28.0f)
    }

    @Test
    fun computePlateBounds_whenTextExceedsOriginalBox_expandsSymmetrically() {
        val bounds =
            TextInpainter.computePlateBounds(
                box = TextInpainter.BoxCoordinates(100f, 200f, 200f, 250f),
                textSize = Pair(150f, 80f),
                padding = Pair(10f, 10f),
            )

        // Original center: X = 150f, Y = 225f
        assertEquals(150f, bounds.centerX, 0.01f)
        assertEquals(225f, bounds.centerY, 0.01f)

        // Must expand beyond original box
        assertTrue(bounds.left < 100f)
        assertTrue(bounds.right > 200f)
        assertTrue(bounds.top < 200f)
        assertTrue(bounds.bottom > 250f)

        // Must accommodate text + padding (150 + 20 = 170, 80 + 20 = 100)
        assertEquals(170f, bounds.width, 0.01f)
        assertEquals(100f, bounds.height, 0.01f)
    }

    @Test
    fun computePlateBounds_nearScreenBoundaries_clampsToScreen() {
        val bounds =
            TextInpainter.computePlateBounds(
                box = TextInpainter.BoxCoordinates(5f, 5f, 50f, 50f),
                textSize = Pair(80f, 80f),
                padding = Pair(10f, 10f),
                screenSize = Pair(1080f, 1920f),
            )

        assertTrue(bounds.left >= 0f)
        assertTrue(bounds.top >= 0f)
    }

    @Test
    fun calculateCollisionFreeExpansion_withAdjacentNeighbor_restrictsExpansion() {
        val targetBox = TextInpainter.BoxCoordinates(100f, 100f, 200f, 140f)
        val neighborRight = TextInpainter.BoxCoordinates(220f, 100f, 320f, 140f) // 20px gap

        val constraints =
            TextInpainter.calculateCollisionFreeExpansion(
                targetBox = targetBox,
                otherBoxes = listOf(neighborRight),
                safetyMargin = 2f,
            )

        // Gap = 20px, safeHalf = 10px, - 2px safetyMargin = 8px
        assertEquals(8f, constraints.maxExpandRight, 0.01f)
        assertEquals(Float.MAX_VALUE, constraints.maxExpandLeft, 0.01f)
    }

    @Test
    fun computePlateBounds_withCollisionConstraint_doesNotExceedConstraint() {
        val targetBox = TextInpainter.BoxCoordinates(100f, 100f, 200f, 140f)
        val constraints = TextInpainter.ExpansionConstraints(maxExpandRight = 8f)

        val bounds =
            TextInpainter.computePlateBounds(
                box = targetBox,
                textSize = Pair(250f, 40f), // Wants 250px + 20px = 270px width (expand 35px each side)
                padding = Pair(10f, 10f),
                constraints = constraints,
            )

        // Right bound must not expand beyond targetBox.right + 8f (208f)
        assertTrue(bounds.right <= 208.01f)
    }

    @Test
    fun boxCoordinates_offset_shiftsCoordinatesAccurately() {
        val originalBox = TextInpainter.BoxCoordinates(100f, 200f, 300f, 250f)
        val shifted = originalBox.offset(-88f, -24f)

        assertEquals(12f, shifted.left, 0.001f)
        assertEquals(176f, shifted.top, 0.001f)
        assertEquals(212f, shifted.right, 0.001f)
        assertEquals(226f, shifted.bottom, 0.001f)
        assertEquals(originalBox.width, shifted.width, 0.001f)
        assertEquals(originalBox.height, shifted.height, 0.001f)
    }

    @Test
    fun computePlateBounds_withLandscapeCutoutOffset_preservesScreenPosition() {
        val physicalBox = TextInpainter.BoxCoordinates(500f, 100f, 700f, 140f)
        val cutoutOffsetX = 88f
        val cutoutOffsetY = 0f

        val localBox = physicalBox.offset(-cutoutOffsetX, -cutoutOffsetY)
        assertEquals(412f, localBox.left, 0.001f)
        assertEquals(612f, localBox.right, 0.001f)

        val localBounds =
            TextInpainter.computePlateBounds(
                box = localBox,
                textSize = Pair(180f, 30f),
                padding = Pair(10f, 5f),
                screenSize = Pair(2400f - cutoutOffsetX, 1080f),
            )

        // Mapping local plate back to physical coordinates produces exact alignment
        val physicalPlateLeft = localBounds.left + cutoutOffsetX
        val physicalPlateRight = localBounds.right + cutoutOffsetX
        val physicalPlateCenterX = (physicalPlateLeft + physicalPlateRight) / 2f

        assertEquals(physicalBox.centerX, physicalPlateCenterX, 0.001f)
    }
}
