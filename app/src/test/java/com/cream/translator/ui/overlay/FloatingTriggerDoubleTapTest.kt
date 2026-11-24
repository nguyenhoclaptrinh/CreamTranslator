package com.cream.translator.ui.overlay

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.WindowManager
import com.cream.translator.testutil.BaseRobolectricTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
class FloatingTriggerDoubleTapTest : BaseRobolectricTest() {
    private lateinit var windowManager: OverlayWindowManager

    @Before
    override fun setUp() {
        super.setUp()
        ShadowSettings.setCanDrawOverlays(true)
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = OverlayWindowManager(context, wm)
    }

    private fun tap(
        view: android.view.View,
        x: Float = 10f,
        y: Float = 10f,
    ) {
        val t = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0)
        view.dispatchTouchEvent(down)
        down.recycle()

        val up = MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_UP, x, y, 0)
        view.dispatchTouchEvent(up)
        up.recycle()
    }

    @Test
    fun singleTap_invokesOnClickAfterDoubleTapWindow() =
        runTest(testDispatcher) {
            val controller = FloatingTriggerController(context, windowManager, this)
            var clickCount = 0
            var doubleTapCount = 0
            var longPressCount = 0

            controller.setupFloatingTrigger(
                onClick = { clickCount++ },
                onDoubleTap = { doubleTapCount++ },
                onLongPress = { longPressCount++ },
            )
            val view = controller.floatingTriggerView!!

            tap(view)
            runCurrent()
            assertEquals(0, clickCount)

            advanceTimeBy(350L)
            runCurrent()
            assertEquals(1, clickCount)
            assertEquals(0, doubleTapCount)
            assertEquals(0, longPressCount)
            controller.destroy()
        }

    @Test
    fun doubleTap_within300ms_invokesOnDoubleTap_andNotOnClick() =
        runTest(testDispatcher) {
            val controller = FloatingTriggerController(context, windowManager, this)
            var clickCount = 0
            var doubleTapCount = 0

            controller.setupFloatingTrigger(
                onClick = { clickCount++ },
                onDoubleTap = { doubleTapCount++ },
                onLongPress = {},
            )
            val view = controller.floatingTriggerView!!

            tap(view)
            ShadowSystemClock.advanceBy(Duration.ofMillis(150))
            advanceTimeBy(150L)
            tap(view)

            advanceTimeBy(400L)
            runCurrent()

            assertEquals(0, clickCount)
            assertEquals(1, doubleTapCount)
            controller.destroy()
        }

    @Test
    fun twoSlowTaps_outside300ms_invokeOnClickTwice_andNotDoubleTap() =
        runTest(testDispatcher) {
            val controller = FloatingTriggerController(context, windowManager, this)
            var clickCount = 0
            var doubleTapCount = 0

            controller.setupFloatingTrigger(
                onClick = { clickCount++ },
                onDoubleTap = { doubleTapCount++ },
                onLongPress = {},
            )
            val view = controller.floatingTriggerView!!

            tap(view)
            ShadowSystemClock.advanceBy(Duration.ofMillis(400))
            advanceTimeBy(400L)
            runCurrent()

            tap(view)
            advanceTimeBy(400L)
            runCurrent()

            assertEquals(2, clickCount)
            assertEquals(0, doubleTapCount)
            controller.destroy()
        }
}
