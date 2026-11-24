package com.cream.translator.core.monitoring.crash

import android.content.Context
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CrashDiagnosticCollectorTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `uncaughtException creates crash report and invokes previous handler`() {
        val mockContext = mockk<Context>(relaxed = true)
        val mockPreviousHandler = mockk<Thread.UncaughtExceptionHandler>(relaxed = true)
        val diagDir = tempFolder.newFolder("crash_diag")

        val collector =
            CrashDiagnosticCollector(
                context = mockContext,
                diagnosticDir = diagDir,
                previousHandler = mockPreviousHandler,
            )

        val testThread = Thread.currentThread()
        val testException = IllegalStateException("Test crash for diagnostics")

        collector.uncaughtException(testThread, testException)

        verify(exactly = 1) {
            mockPreviousHandler.uncaughtException(testThread, testException)
        }

        assertTrue(collector.hasCrashReport())
        val report = collector.readCrashReport()
        assertNotNull(report)
        assertTrue(report!!.contains("Test crash for diagnostics"))
        assertTrue(report.contains("IllegalStateException"))

        collector.clearCrashReport()
        assertFalse(collector.hasCrashReport())
    }
}
