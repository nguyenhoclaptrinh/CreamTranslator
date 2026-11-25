package com.cream.translator.ui.overlay

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.text.StaticLayout
import com.cream.translator.core.model.TextRegion
import com.cream.translator.core.model.TranslationResult
import com.cream.translator.testutil.BaseRobolectricTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InPlaceTranslationOverlayTest : BaseRobolectricTest() {
    @Test
    fun multiplePlates_keepIndependentTextSizeAndColorInStaticLayouts() {
        val overlay = InPlaceTranslationOverlay(context)
        val largeBoxResult =
            TranslationResult(
                region =
                    TextRegion(
                        id = "large",
                        originalText = "Hello",
                        boundingBox = Rect(20, 20, 420, 220),
                        backgroundColor = Color.BLACK,
                        textColor = Color.WHITE,
                    ),
                translatedText = "Hi",
                executionTimeMs = 1L,
            )
        val smallBoxResult =
            TranslationResult(
                region =
                    TextRegion(
                        id = "small",
                        originalText = "Small text label that wraps",
                        boundingBox = Rect(20, 300, 120, 332),
                        backgroundColor = Color.WHITE,
                        textColor = Color.BLACK,
                    ),
                translatedText = "Very long translated label that must shrink to minimum font size",
                executionTimeMs = 1L,
            )

        overlay.setResults(listOf(largeBoxResult, smallBoxResult))
        overlay.layout(0, 0, 1080, 1920)
        overlay.draw(Canvas())
        overlay.draw(Canvas())

        val preparedField = InPlaceTranslationOverlay::class.java.getDeclaredField("preparedPlates")
        preparedField.isAccessible = true
        val plates = preparedField.get(overlay) as List<*>
        assertEquals(2, plates.size)

        val layoutField = plates[0]!!.javaClass.getDeclaredField("staticLayout")
        layoutField.isAccessible = true
        val largeLayout = layoutField.get(plates[0]) as StaticLayout
        val smallLayout = layoutField.get(plates[1]) as StaticLayout

        assertTrue(
            "Expected larger plate font size (${largeLayout.paint.textSize}) > (${smallLayout.paint.textSize})",
            largeLayout.paint.textSize > smallLayout.paint.textSize,
        )
        assertEquals(Color.WHITE, largeLayout.paint.color)
        assertEquals(Color.BLACK, smallLayout.paint.color)
    }
}
