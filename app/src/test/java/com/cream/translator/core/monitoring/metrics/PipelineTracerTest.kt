package com.cream.translator.core.monitoring.metrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PipelineTracerTest {
    @Test
    fun `session measures pipeline stages and creates metric record`() {
        val tracer = PipelineTracer(maxHistory = 10)
        val session = tracer.startSession()

        val captured =
            session.measureCapture {
                Thread.sleep(10)
                "BitmapCaptured"
            }
        assertEquals("BitmapCaptured", captured)

        val ocrResult =
            session.measureOcr {
                Thread.sleep(15)
                listOf("Line1", "Line2")
            }
        session.recognizedBlocks = ocrResult.size

        val translated =
            session.measureTranslate {
                listOf("Dong1", "Dong2")
            }
        session.translatedBlocks = translated.size

        val record = session.finish(isSuccess = true)

        assertNotNull(record.traceId)
        assertTrue(record.captureDurationMs >= 5L)
        assertTrue(record.ocrDurationMs >= 10L)
        assertEquals(2, record.recognizedBlocks)
        assertEquals(2, record.translatedBlocks)
        assertTrue(record.isSuccess)
        assertTrue(record.toSummaryString().contains(record.traceId))
    }

    @Test
    fun `tracer calculates average latency across successful sessions`() {
        val tracer = PipelineTracer(maxHistory = 5)

        val session1 = tracer.startSession()
        session1.measureOcr { Thread.sleep(20) }
        session1.finish(isSuccess = true)

        val session2 = tracer.startSession()
        session2.measureOcr { Thread.sleep(40) }
        session2.finish(isSuccess = true)

        // Failed session should be ignored in latency average
        val sessionFailed = tracer.startSession()
        sessionFailed.finish(isSuccess = false, errorMessage = "Timeout")

        val avg = tracer.calculateAverageLatencyMs()
        assertTrue("Average latency should be positive", avg > 0L)
        assertEquals(3, tracer.getRecentMetrics().size)
    }
}
