package com.cream.translator.testutil

import android.graphics.Bitmap
import android.graphics.Rect
import com.cream.translator.core.model.LanguagePair
import com.cream.translator.core.model.TextRegion
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BaseRobolectricSmokeTest : BaseRobolectricTest() {
    @Test
    fun verifyRobolectricContextInitialized() {
        assertNotNull(context)
        assertNotNull(application)
        assertEquals(context.packageName, application.packageName)
    }

    @Test
    fun verifyFakeEnginesWorkflow() =
        runTest(testDispatcher) {
            val fakeOcr = FakeOcrEngine()
            val fakeTranslation = FakeTranslationEngine()

            val sampleRegion =
                TextRegion(
                    id = "reg_1",
                    originalText = "Hello World",
                    boundingBox = Rect(0, 0, 100, 50),
                )
            fakeOcr.simulatedRegions = listOf(sampleRegion)

            val bitmap = Bitmap.createBitmap(100, 50, Bitmap.Config.ARGB_8888)
            val regions = fakeOcr.recognizeText(bitmap)
            assertEquals(1, regions.size)
            assertEquals("Hello World", regions.first().originalText)

            val translated =
                fakeTranslation.translate(
                    regions.first().originalText,
                    LanguagePair("en", "vi"),
                )
            assertEquals("[VI] Hello World", translated)

            fakeOcr.close()
            fakeTranslation.close()
            assertTrue(fakeOcr.isClosed)
            assertTrue(fakeTranslation.isClosed)
            bitmap.recycle()
        }
}
