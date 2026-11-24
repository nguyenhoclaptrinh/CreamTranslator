package com.cream.translator.service.coordinator

import android.graphics.Bitmap
import android.graphics.Rect
import com.cream.translator.core.engine.filter.TextFilter
import com.cream.translator.core.model.LanguagePair
import com.cream.translator.core.model.TextRegion
import com.cream.translator.core.model.TranslationState
import com.cream.translator.core.monitoring.metrics.PipelineTracer
import com.cream.translator.testutil.BaseRobolectricTest
import com.cream.translator.testutil.FakeOcrEngine
import com.cream.translator.testutil.FakeTranslationEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TranslationPipelineCoordinatorTest : BaseRobolectricTest() {
    private lateinit var fakeOcrEngine: FakeOcrEngine
    private lateinit var fakeTranslationEngine: FakeTranslationEngine
    private lateinit var testTracer: PipelineTracer
    private val languagePair = LanguagePair("en", "vi")

    private class SimpleTextFilter(private val filterResult: Boolean) : TextFilter {
        override fun shouldFilter(text: String): Boolean = filterResult

        override fun shouldFilter(
            text: String,
            languagePair: LanguagePair?,
        ): Boolean = filterResult
    }

    @Before
    override fun setUp() {
        super.setUp()
        fakeOcrEngine = FakeOcrEngine()
        fakeTranslationEngine = FakeTranslationEngine(translationPrefix = "[VI] ")
        testTracer = PipelineTracer()
    }

    @Test
    fun executePipeline_zeroRegions_returnsNullAndSetsErrorState() =
        runTest(testDispatcher) {
            val coordinator =
                TranslationPipelineCoordinator(
                    ocrEngine = fakeOcrEngine,
                    translationEngine = fakeTranslationEngine,
                    textFilter = SimpleTextFilter(filterResult = false),
                    defaultDispatcher = StandardTestDispatcher(testScheduler),
                )

            fakeOcrEngine.simulatedRegions = emptyList()
            val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
            val session = testTracer.startSession()

            val result = coordinator.executePipeline(bitmap, languagePair, session)

            assertNull(result)
            assertTrue(coordinator.state.value is TranslationState.Error)
            assertTrue(bitmap.isRecycled)
        }

    @Test
    fun executePipeline_allFiltered_returnsNullAndSetsIdleState() =
        runTest(testDispatcher) {
            val coordinator =
                TranslationPipelineCoordinator(
                    ocrEngine = fakeOcrEngine,
                    translationEngine = fakeTranslationEngine,
                    textFilter = SimpleTextFilter(filterResult = true), // Lọc bỏ tất cả
                    defaultDispatcher = StandardTestDispatcher(testScheduler),
                )

            val region =
                TextRegion(
                    id = "reg_1",
                    originalText = "Cancel",
                    boundingBox = Rect(10, 10, 50, 30),
                )
            fakeOcrEngine.simulatedRegions = listOf(region)
            val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
            val session = testTracer.startSession()

            val result = coordinator.executePipeline(bitmap, languagePair, session)

            assertNull(result)
            assertEquals(TranslationState.Idle, coordinator.state.value)
            assertTrue(bitmap.isRecycled)
        }

    @Test
    fun executePipeline_success_returnsTranslationResultsAndRecyclesBitmap() =
        runTest(testDispatcher) {
            val coordinator =
                TranslationPipelineCoordinator(
                    ocrEngine = fakeOcrEngine,
                    translationEngine = fakeTranslationEngine,
                    textFilter = SimpleTextFilter(filterResult = false),
                    defaultDispatcher = StandardTestDispatcher(testScheduler),
                )

            val region =
                TextRegion(
                    id = "reg_1",
                    originalText = "Start Game",
                    boundingBox = Rect(10, 10, 80, 40),
                )
            fakeOcrEngine.simulatedRegions = listOf(region)
            val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
            val session = testTracer.startSession()

            val result = coordinator.executePipeline(bitmap, languagePair, session)

            assertNotNull(result)
            assertEquals(1, result!!.size)
            assertEquals("[VI] Start Game", result.first().translatedText)
            assertTrue(bitmap.isRecycled)

            coordinator.close()
            assertTrue(fakeOcrEngine.isClosed)
            assertTrue(fakeTranslationEngine.isClosed)
        }
}
