package com.cream.translator.service.coordinator

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import com.cream.translator.core.engine.filter.TextFilter
import com.cream.translator.core.model.LanguagePair
import com.cream.translator.core.model.PixelOffset
import com.cream.translator.core.model.TextRegion
import com.cream.translator.core.monitoring.metrics.PipelineTracer
import com.cream.translator.testutil.BaseRobolectricTest
import com.cream.translator.testutil.FakeOcrEngine
import com.cream.translator.testutil.FakeTranslationEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RegionTranslatePipelineOffsetTest : BaseRobolectricTest() {
    private lateinit var fakeOcrEngine: FakeOcrEngine
    private lateinit var fakeTranslationEngine: FakeTranslationEngine
    private lateinit var testTracer: PipelineTracer
    private val languagePair = LanguagePair("en", "vi")

    private val passThroughFilter =
        object : TextFilter {
            override fun shouldFilter(text: String): Boolean = false

            override fun shouldFilter(
                text: String,
                languagePair: LanguagePair?,
            ): Boolean = false
        }

    @Before
    override fun setUp() {
        super.setUp()
        fakeOcrEngine = FakeOcrEngine()
        fakeTranslationEngine = FakeTranslationEngine(translationPrefix = "[VI] ")
        testTracer = PipelineTracer()
    }

    @Test
    fun executePipeline_withRegionOffset_shiftsBoundingBoxesAfterColorSampling() =
        runTest(testDispatcher) {
            val coordinator =
                TranslationPipelineCoordinator(
                    ocrEngine = fakeOcrEngine,
                    translationEngine = fakeTranslationEngine,
                    textFilter = passThroughFilter,
                    defaultDispatcher = StandardTestDispatcher(testScheduler),
                )

            // Tạo bitmap crop kích thước nhỏ 60x60 phủ màu xanh dương
            val croppedBitmap = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888)
            croppedBitmap.eraseColor(Color.BLUE)

            // Tọa độ OCR trả về là tương đối trong bitmap crop (10,10,50,30)
            fakeOcrEngine.simulatedRegions =
                listOf(
                    TextRegion(
                        id = "reg_crop_1",
                        originalText = "Quest",
                        boundingBox = Rect(10, 10, 50, 30),
                    ),
                )

            val offset = PixelOffset(x = 100, y = 200)
            val session = testTracer.startSession()

            val results = coordinator.executePipeline(croppedBitmap, languagePair, session, offset)

            assertNotNull(results)
            assertEquals(1, results!!.size)
            val result = results.first()
            // Bounding box phải được dịch chuyển theo offset để render đúng trên màn hình
            assertEquals(Rect(110, 210, 150, 230), result.region.boundingBox)
            // Màu nền được sample từ bitmap crop (BLUE) trước khi dịch chuyển offset
            assertEquals(Color.BLUE, result.region.backgroundColor)
            assertTrue(croppedBitmap.isRecycled)
        }

    @Test
    fun executePipeline_zeroOffset_keepsOriginalBoundingBoxAndRecyclesBitmap() =
        runTest(testDispatcher) {
            val coordinator =
                TranslationPipelineCoordinator(
                    ocrEngine = fakeOcrEngine,
                    translationEngine = fakeTranslationEngine,
                    textFilter = passThroughFilter,
                    defaultDispatcher = StandardTestDispatcher(testScheduler),
                )

            val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
            val originalBox = Rect(15, 25, 75, 55)
            fakeOcrEngine.simulatedRegions =
                listOf(
                    TextRegion(
                        id = "reg_full_1",
                        originalText = "Attack",
                        boundingBox = originalBox,
                    ),
                )

            val session = testTracer.startSession()
            val results = coordinator.executePipeline(bitmap, languagePair, session, PixelOffset.NONE)

            assertNotNull(results)
            assertEquals(originalBox, results!!.first().region.boundingBox)
            assertTrue(bitmap.isRecycled)
        }
}
