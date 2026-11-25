package com.cream.translator.ui.overlay

import android.content.Context
import android.graphics.Rect
import android.view.WindowManager
import com.cream.translator.core.model.TextRegion
import com.cream.translator.core.model.TranslationResult
import com.cream.translator.testutil.BaseRobolectricTest
import io.mockk.every
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OverlayWindowManagerTest : BaseRobolectricTest() {
    private lateinit var overlayWindowManager: OverlayWindowManager
    private lateinit var windowManager: WindowManager

    @Before
    override fun setUp() {
        super.setUp()
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        overlayWindowManager = OverlayWindowManager(context, windowManager)
    }

    @Test
    fun showAndDismissTranslationOverlay_updatesStateCorrectly() {
        assertFalse(overlayWindowManager.isOverlayShowing)

        val results =
            listOf(
                TranslationResult(
                    region =
                        TextRegion(
                            id = "reg_1",
                            originalText = "Hello",
                            boundingBox = Rect(0, 0, 100, 50),
                            backgroundColor = -1,
                            textColor = -16777216,
                        ),
                    translatedText = "Xin chào",
                    executionTimeMs = 50L,
                ),
            )

        var dismissed = false
        val shown =
            overlayWindowManager.showTranslationOverlay(results) {
                dismissed = true
            }

        assertTrue(shown)
        assertTrue(overlayWindowManager.isOverlayShowing)
        assertFalse(dismissed)

        val dismissResult = overlayWindowManager.dismissTranslationOverlay()
        assertTrue(dismissResult)
        assertFalse(overlayWindowManager.isOverlayShowing)
    }

    @Test
    fun getDimensions_returnsValidPositiveValues() {
        val width = overlayWindowManager.getScreenWidth()
        val height = overlayWindowManager.getScreenHeight()

        assertTrue(width > 0)
        assertTrue(height > 0)
    }

    @Test
    fun dismissOverlays_usesRemoveViewImmediateInsteadOfDeferredRemoveView() =
        runTest(testDispatcher) {
            val spyWm = spyk(windowManager)
            val manager = OverlayWindowManager(context, spyWm)

            val results =
                listOf(
                    TranslationResult(
                        region =
                            TextRegion(
                                id = "reg_1",
                                originalText = "Hello",
                                boundingBox = Rect(0, 0, 100, 50),
                                backgroundColor = -1,
                                textColor = -16777216,
                            ),
                        translatedText = "Xin chào",
                        executionTimeMs = 10L,
                    ),
                )

            assertTrue(manager.showTranslationOverlay(results) {})
            assertTrue(manager.dismissTranslationOverlay())

            assertTrue(manager.showRegionSelector(scope = this, onRegionSelected = {}, onCancel = {}))
            manager.dismissRegionSelector()

            verify(exactly = 2) { spyWm.removeViewImmediate(any()) }
            verify(exactly = 0) { spyWm.removeView(any()) }
        }

    @Test
    fun showRegionSelector_whenAttachFails_doesNotFireAutoDismissOnCancel() =
        runTest(testDispatcher) {
            val spyWm = spyk(windowManager)
            every { spyWm.addView(any(), any()) } throws WindowManager.BadTokenException("Missing token")
            val manager = OverlayWindowManager(context, spyWm)

            var cancelCount = 0
            val shown =
                manager.showRegionSelector(
                    scope = this,
                    onRegionSelected = {},
                    onCancel = { cancelCount++ },
                )

            assertFalse(shown)
            advanceTimeBy(11_000L)
            runCurrent()
            assertFalse(cancelCount > 0)
        }
}
