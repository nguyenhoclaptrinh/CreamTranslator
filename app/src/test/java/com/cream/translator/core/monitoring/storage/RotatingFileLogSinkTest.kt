package com.cream.translator.core.monitoring.storage

import com.cream.translator.core.monitoring.LogEntry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class RotatingFileLogSinkTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `sink creates active log file and writes formatted entry`() =
        runTest {
            val logDir = tempFolder.newFolder("logs")
            val sink =
                RotatingFileLogSink(
                    logDirectory = logDir,
                    maxFileSizeBytes = 1024,
                    scope = this,
                )

            sink.onLog(LogEntry(priority = 4, tag = "TEST", message = "Hello Rotating File"))
            advanceUntilIdle()

            val activeFile = File(logDir, RotatingFileLogSink.ACTIVE_LOG_FILENAME)
            assertTrue(activeFile.exists())
            val content = activeFile.readText()
            assertTrue(content.contains("Hello Rotating File"))
            assertTrue(content.contains("[I][TEST]"))
            sink.close()
        }

    @Test
    fun `sink rotates file when size threshold is reached`() =
        runTest {
            val logDir = tempFolder.newFolder("rotating_logs")
            // Small threshold so 2 lines trigger rotation
            val sink =
                RotatingFileLogSink(
                    logDirectory = logDir,
                    maxFileSizeBytes = 50,
                    scope = this,
                )

            sink.onLog(LogEntry(priority = 4, tag = "TEST", message = "Line 1 - Exceeding Threshold"))
            advanceUntilIdle()

            sink.onLog(LogEntry(priority = 4, tag = "TEST", message = "Line 2 - Trigger Rotation"))
            advanceUntilIdle()

            val activeFile = File(logDir, RotatingFileLogSink.ACTIVE_LOG_FILENAME)
            val previousFile = File(logDir, RotatingFileLogSink.PREVIOUS_LOG_FILENAME)

            assertTrue("Previous rotated file should exist", previousFile.exists())
            assertTrue("Active file should exist", activeFile.exists())
            assertEquals(2, sink.getLogFiles().size)
            sink.close()
        }

    @Test
    fun `clearLogs deletes both active and previous files`() =
        runTest {
            val logDir = tempFolder.newFolder("clear_logs")
            val sink =
                RotatingFileLogSink(
                    logDirectory = logDir,
                    maxFileSizeBytes = 1000,
                    scope = this,
                )

            sink.onLog(LogEntry(priority = 4, tag = "TEST", message = "To be deleted"))
            advanceUntilIdle()

            val activeFile = File(logDir, RotatingFileLogSink.ACTIVE_LOG_FILENAME)
            assertTrue(activeFile.exists())

            sink.clearLogs()
            assertTrue(sink.getLogFiles().isEmpty())
            sink.close()
        }
}
