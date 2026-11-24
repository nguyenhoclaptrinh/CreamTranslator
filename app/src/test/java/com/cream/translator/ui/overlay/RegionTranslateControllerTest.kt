package com.cream.translator.ui.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import com.cream.translator.core.engine.filter.TextFilter
import com.cream.translator.core.model.LanguagePair
import com.cream.translator.core.model.TextRegion
import com.cream.translator.core.model.TranslationResult
import com.cream.translator.core.model.TranslationState
import com.cream.translator.core.monitoring.metrics.PipelineTracer
import com.cream.translator.service.coordinator.TranslationPipelineCoordinator
import com.cream.translator.testutil.BaseRobolectricTest
import com.cream.translator.testutil.FakeOcrEngine
import com.cream.translator.testutil.FakeTranslationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RegionTranslateControllerTest : BaseRobolectricTest() {
    private lateinit var fakeOcrEngine: FakeOcrEngine
    private lateinit var fakeTranslationEngine: FakeTranslationEngine
    private lateinit var pipelineTracer: PipelineTracer

    private val passThroughFilter =
        object : TextFilter {
            override fun shouldFilter(text: String): Boolean = false

            override fun shouldFilter(
                text: String,
                languagePair: LanguagePair?,
            ): Boolean = false
        }

    private class FakeOverlayWindowManager : IOverlayWindowManager {
        override var isOverlayShowing: Boolean = false
        var lastOnRegionSelected: ((Rect) -> Unit)? = null
        var lastOnCancel: (() -> Unit)? = null
        var lastOverlayResults: List<TranslationResult>? = null

        override fun showTranslationOverlay(
            results: List<TranslationResult>,
            onDismiss: () -> Unit,
        ): Boolean {
            lastOverlayResults = results
            isOverlayShowing = true
            return true
        }

        override fun dismissTranslationOverlay(): Boolean {
            isOverlayShowing = false
            return true
        }

        override fun showRegionSelector(
            scope: CoroutineScope,
            onRegionSelected: (Rect) -> Unit,
            onCancel: () -> Unit,
        ): Boolean {
            lastOnRegionSelected = onRegionSelected
            lastOnCancel = onCancel
            return true
        }

        override fun dismissRegionSelector() {
            lastOnRegionSelected = null
            lastOnCancel = null
        }

        override fun getScreenWidth(): Int = 1080

        override fun getScreenHeight(): Int = 1920

        override fun safeAddView(
            view: View,
            params: ViewGroup.LayoutParams,
        ): Boolean = true

        override fun safeRemoveView(view: View?): Boolean = true

        override fun safeUpdateViewLayout(
            view: View,
            params: ViewGroup.LayoutParams,
        ): Boolean = true
    }

    @Before
    override fun setUp() {
        super.setUp()
        fakeOcrEngine = FakeOcrEngine()
        fakeTranslationEngine = FakeTranslationEngine(translationPrefix = "[VI] ")
        pipelineTracer = PipelineTracer()
    }

    @Test
    fun activateRegionSelectMode_andCancel_restoresIdleState() =
        runTest(testDispatcher) {
            val fakeWindowManager = FakeOverlayWindowManager()
            val coordinator =
                TranslationPipelineCoordinator(
                    ocrEngine = fakeOcrEngine,
                    translationEngine = fakeTranslationEngine,
                    textFilter = passThroughFilter,
                    defaultDispatcher = StandardTestDispatcher(testScheduler),
                )
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val triggerController = FloatingTriggerController(context, OverlayWindowManager(context, wm), this)
            val controller =
                RegionTranslateController(
                    context = context,
                    overlayWindowManager = fakeWindowManager,
                    pipelineCoordinator = coordinator,
                    floatingTriggerController = triggerController,
                    serviceScope = this,
                    hooks =
                        RegionTranslateHooks(
                            languagePairProvider = { LanguagePair("en", "vi") },
                            pipelineTracer = pipelineTracer,
                        ),
                )

            controller.activateRegionSelectMode()
            assertEquals(TranslationState.RegionSelecting, coordinator.state.value)

            fakeWindowManager.lastOnCancel?.invoke()
            assertEquals(TranslationState.Idle, coordinator.state.value)
        }

    @Test
    fun activateRegionSelectMode_andSelectRegion_executesPipelineWithOffset() =
        runTest(testDispatcher) {
            val fakeWindowManager = FakeOverlayWindowManager()
            val coordinator =
                TranslationPipelineCoordinator(
                    ocrEngine = fakeOcrEngine,
                    translationEngine = fakeTranslationEngine,
                    textFilter = passThroughFilter,
                    defaultDispatcher = StandardTestDispatcher(testScheduler),
                )
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val triggerController = FloatingTriggerController(context, OverlayWindowManager(context, wm), this)

            val croppedBitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
            croppedBitmap.eraseColor(Color.BLACK)
            fakeOcrEngine.simulatedRegions =
                listOf(
                    TextRegion(
                        id = "reg_1",
                        originalText = "Inventory",
                        boundingBox = Rect(10, 20, 120, 50),
                    ),
                )

            val controller =
                RegionTranslateController(
                    context = context,
                    overlayWindowManager = fakeWindowManager,
                    pipelineCoordinator = coordinator,
                    floatingTriggerController = triggerController,
                    serviceScope = this,
                    hooks =
                        RegionTranslateHooks(
                            languagePairProvider = { LanguagePair("en", "vi") },
                            pipelineTracer = pipelineTracer,
                            isCaptureServiceReady = { true },
                            captureRegionProvider = { croppedBitmap },
                            onToast = {},
                        ),
                )

            controller.activateRegionSelectMode()
            fakeWindowManager.lastOnRegionSelected?.invoke(Rect(100, 300, 300, 400))

            advanceUntilIdle()

            assertTrue(fakeWindowManager.isOverlayShowing)
            val rendered = fakeWindowManager.lastOverlayResults!!
            assertEquals(1, rendered.size)
            assertEquals("[VI] Inventory", rendered.first().translatedText)
            assertEquals(Rect(110, 320, 220, 350), rendered.first().region.boundingBox)
            assertTrue(croppedBitmap.isRecycled)
        }
}
