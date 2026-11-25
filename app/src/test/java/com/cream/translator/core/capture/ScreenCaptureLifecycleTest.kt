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

    @Test
    fun `onConfigurationChanged resizes existing VirtualDisplay without calling createVirtualDisplay again`() {
        val mockProjection = mockk<MediaProjection>(relaxed = true)
        val mockVirtualDisplay = mockk<VirtualDisplay>(relaxed = true)
        val callbackSlot = slot<MediaProjection.Callback>()

        every { mockProjection.registerCallback(capture(callbackSlot), any()) } answers { nothing }
        every {
            mockProjection.createVirtualDisplay(any(), any(), any(), any(), any(), any(), any(), any())
        } returns mockVirtualDisplay

        val manager = ScreenCaptureManager(context, mockProjection)
        manager.onConfigurationChanged()

        verify(exactly = 1) {
            mockProjection.createVirtualDisplay(any(), any(), any(), any(), any(), any(), any(), any())
        }
        verify(exactly = 1) { mockVirtualDisplay.resize(any(), any(), any()) }
        manager.release()
    }

    @Test
    fun `onConfigurationChanged after onStop does not invoke createVirtualDisplay or resize`() {
        val mockProjection = mockk<MediaProjection>(relaxed = true)
        val mockVirtualDisplay = mockk<VirtualDisplay>(relaxed = true)
        val callbackSlot = slot<MediaProjection.Callback>()

        every { mockProjection.registerCallback(capture(callbackSlot), any()) } answers { nothing }
        every {
            mockProjection.createVirtualDisplay(any(), any(), any(), any(), any(), any(), any(), any())
        } returns mockVirtualDisplay

        val manager = ScreenCaptureManager(context, mockProjection)
        callbackSlot.captured.onStop()
        manager.onConfigurationChanged()

        verify(exactly = 1) {
            mockProjection.createVirtualDisplay(any(), any(), any(), any(), any(), any(), any(), any())
        }
        verify(exactly = 0) { mockVirtualDisplay.resize(any(), any(), any()) }
    }

    @Test
    fun `when createVirtualDisplay throws SecurityException lifecycle stops MediaProjection`() {
        val mockProjection = mockk<MediaProjection>(relaxed = true)

        every {
            mockProjection.createVirtualDisplay(any(), any(), any(), any(), any(), any(), any(), any())
        } throws SecurityException("Projection token revoked")

        val manager = ScreenCaptureManager(context, mockProjection)

        assertEquals(false, manager.isProjectionActive)
        assertEquals(CaptureLifecycleState.Stopped, manager.lifecycleState)
        verify(exactly = 1) { mockProjection.stop() }
    }

    @Test
    fun `when resize throws IllegalStateException resources release and projection stops`() {
        val mockProjection = mockk<MediaProjection>(relaxed = true)
        val mockVirtualDisplay = mockk<VirtualDisplay>(relaxed = true)

        every {
            mockProjection.createVirtualDisplay(any(), any(), any(), any(), any(), any(), any(), any())
        } returns mockVirtualDisplay
        every { mockVirtualDisplay.resize(any(), any(), any()) } throws IllegalStateException("Dead display")

        val manager = ScreenCaptureManager(context, mockProjection)
        manager.onConfigurationChanged()

        assertEquals(false, manager.isProjectionActive)
        assertEquals(CaptureLifecycleState.Stopped, manager.lifecycleState)
        verify(exactly = 1) { mockVirtualDisplay.release() }
        verify(exactly = 1) { mockProjection.stop() }
    }

    @Test
    fun `release stops MediaProjection even if VirtualDisplay release throws IllegalStateException`() {
        val mockProjection = mockk<MediaProjection>(relaxed = true)
        val mockVirtualDisplay = mockk<VirtualDisplay>(relaxed = true)

        every {
            mockProjection.createVirtualDisplay(any(), any(), any(), any(), any(), any(), any(), any())
        } returns mockVirtualDisplay
        every { mockVirtualDisplay.release() } throws IllegalStateException("Already released")

        val manager = ScreenCaptureManager(context, mockProjection)
        manager.release()

        assertEquals(false, manager.isProjectionActive)
        assertEquals(CaptureLifecycleState.Stopped, manager.lifecycleState)
        verify(exactly = 1) { mockProjection.stop() }
    }
}
