package com.cream.translator.core.monitoring.memory

import android.content.ComponentCallbacks2
import android.content.Context
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryPressureMonitorTest {
    @Test
    fun `getTrimLevelDescription returns readable strings`() {
        val criticalDesc =
            MemoryPressureMonitor.getTrimLevelDescription(
                ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            )
        val lowDesc =
            MemoryPressureMonitor.getTrimLevelDescription(
                ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            )
        val hiddenDesc =
            MemoryPressureMonitor.getTrimLevelDescription(
                ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN,
            )
        val completeDesc =
            MemoryPressureMonitor.getTrimLevelDescription(
                ComponentCallbacks2.TRIM_MEMORY_COMPLETE,
            )

        assertEquals("RUNNING_CRITICAL", criticalDesc)
        assertEquals("RUNNING_LOW", lowDesc)
        assertEquals("UI_HIDDEN", hiddenDesc)
        assertEquals("COMPLETE", completeDesc)
    }

    @Test
    fun `critical trim level triggers onCriticalMemoryPressure callback`() {
        val mockContext = mockk<Context>(relaxed = true)
        var callbackTriggered = false

        val monitor =
            MemoryPressureMonitor(mockContext) {
                callbackTriggered = true
            }

        monitor.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
        assertTrue(callbackTriggered)
    }

    @Test
    fun `onLowMemory triggers onCriticalMemoryPressure callback`() {
        val mockContext = mockk<Context>(relaxed = true)
        var callbackTriggered = false

        val monitor =
            MemoryPressureMonitor(mockContext) {
                callbackTriggered = true
            }

        monitor.onLowMemory()
        assertTrue(callbackTriggered)
    }
}
