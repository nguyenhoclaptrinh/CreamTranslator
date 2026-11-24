package com.cream.translator.ui.overlay

import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import com.cream.translator.testutil.BaseRobolectricTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RegionSelectorViewCancelTest : BaseRobolectricTest() {
    private fun dispatchTouch(
        view: RegionSelectorView,
        action: Int,
        x: Float,
        y: Float,
    ) {
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, action, x, y, 0)
        view.onTouchEvent(event)
        event.recycle()
    }

    @Test
    fun smallSwipe_invokesCancelListener_andNotRegionSelected() {
        val view = RegionSelectorView(context)
        var selectedRect: Rect? = null
        var cancelled = false

        view.setOnRegionSelectedListener { selectedRect = it }
        view.onCancelListener = { cancelled = true }

        dispatchTouch(view, MotionEvent.ACTION_DOWN, 100f, 100f)
        dispatchTouch(view, MotionEvent.ACTION_MOVE, 110f, 110f)
        dispatchTouch(view, MotionEvent.ACTION_UP, 110f, 110f)

        assertNull(selectedRect)
        assertTrue(cancelled)
    }

    @Test
    fun validSwipe_invokesRegionSelected_andNotCancel() {
        val view = RegionSelectorView(context)
        var selectedRect: Rect? = null
        var cancelled = false

        view.setOnRegionSelectedListener { selectedRect = it }
        view.onCancelListener = { cancelled = true }

        dispatchTouch(view, MotionEvent.ACTION_DOWN, 50f, 60f)
        dispatchTouch(view, MotionEvent.ACTION_MOVE, 250f, 300f)
        dispatchTouch(view, MotionEvent.ACTION_UP, 250f, 300f)

        assertEquals(Rect(50, 60, 250, 300), selectedRect)
        assertFalse(cancelled)
    }

    @Test
    fun autoDismissTimer_invokesCancelAfterTimeout_whenNoTouchOccurs() =
        runTest(testDispatcher) {
            val view = RegionSelectorView(context)
            var cancelled = false
            view.onCancelListener = { cancelled = true }

            view.startAutoDismissTimer(this)
            advanceTimeBy(9_999L)
            runCurrent()
            assertFalse(cancelled)

            advanceTimeBy(2L)
            runCurrent()
            assertTrue(cancelled)
        }
}
