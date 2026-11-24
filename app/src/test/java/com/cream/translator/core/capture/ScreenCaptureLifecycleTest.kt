package com.cream.translator.core.capture

import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import com.cream.translator.testutil.BaseRobolectricTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenCaptureLifecycleTest : BaseRobolectricTest() {
    @Test
    fun `ScreenCaptureManager registers callback before creating virtual display`() {
        val mockProjection = mockk<MediaProjection>(relaxed = true)
        val mockVirtualDisplay = mockk<VirtualDisplay>(relaxed = true)
        val callbackSlot = slot<MediaProjection.Callback>()

        every {
            mockProjection.registerCallback(capture(callbackSlot), any())
        } answers { nothing }

        every {
            mockProjection.createVirtualDisplay(any(), any(), any(), any(), any(), any(), any(), any())
        } returns mockVirtualDisplay

        val manager = ScreenCaptureManager(context, mockProjection)

        assertTrue(callbackSlot.isCaptured)
        assertTrue(manager.lifecycleState is CaptureLifecycleState.Active)
        assertEquals(true, manager.isProjectionActive)

        manager.release()
        assertEquals(CaptureLifecycleState.Stopped, manager.lifecycleState)
        verify { mockProjection.unregisterCallback(callbackSlot.captured) }
        verify { mockVirtualDisplay.release() }
    }

    @Test
    fun `when callback onStop fires lifecycle state transitions to Stopped and invokes onStopCallback`() {
        val mockProjection = mockk<MediaProjection>(relaxed = true)
        val mockVirtualDisplay = mockk<VirtualDisplay>(relaxed = true)
        val callbackSlot = slot<MediaProjection.Callback>()

        every {
            mockProjection.registerCallback(capture(callbackSlot), any())
        } answers { nothing }

        every {
            mockProjection.createVirtualDisplay(any(), any(), any(), any(), any(), any(), any(), any())
        } returns mockVirtualDisplay

        val manager = ScreenCaptureManager(context, mockProjection)
        var onStopInvoked = false
        manager.onStopCallback = { onStopInvoked = true }

        // Simulate Android OS stopping MediaProjection
        callbackSlot.captured.onStop()

        assertEquals(false, manager.isProjectionActive)
        assertEquals(CaptureLifecycleState.Stopped, manager.lifecycleState)
        assertTrue(onStopInvoked)
    }
}
