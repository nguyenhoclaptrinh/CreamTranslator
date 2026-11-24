package com.cream.translator.core.monitoring.storage

import com.cream.translator.core.monitoring.LogEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CircularLogBufferTest {
    @Test
    fun `buffer stores entries up to max size and evicts oldest`() {
        val buffer = CircularLogBuffer(maxEntries = 3)

        buffer.onLog(LogEntry(priority = 3, tag = "Tag1", message = "Message 1"))
        buffer.onLog(LogEntry(priority = 4, tag = "Tag2", message = "Message 2"))
        buffer.onLog(LogEntry(priority = 5, tag = "Tag3", message = "Message 3"))

        assertEquals(3, buffer.size)
        assertEquals("Message 1", buffer.snapshot()[0].message)

        // Adding 4th entry should evict "Message 1"
        buffer.onLog(LogEntry(priority = 6, tag = "Tag4", message = "Message 4"))

        assertEquals(3, buffer.size)
        val snapshot = buffer.snapshot()
        assertEquals("Message 2", snapshot[0].message)
        assertEquals("Message 3", snapshot[1].message)
        assertEquals("Message 4", snapshot[2].message)
    }

    @Test
    fun `getRecent filters by priority correctly`() {
        val buffer = CircularLogBuffer(maxEntries = 10)

        buffer.onLog(LogEntry(priority = 3, tag = "DEBUG", message = "Debug log"))
        buffer.onLog(LogEntry(priority = 4, tag = "INFO", message = "Info log"))
        buffer.onLog(LogEntry(priority = 5, tag = "WARN", message = "Warn log"))
        buffer.onLog(LogEntry(priority = 6, tag = "ERROR", message = "Error log"))

        val allLogs = buffer.getRecent(limit = 10, minPriority = 0)
        assertEquals(4, allLogs.size)

        val warningsAndAbove = buffer.getRecent(limit = 10, minPriority = 5)
        assertEquals(2, warningsAndAbove.size)
        assertEquals("Warn log", warningsAndAbove[0].message)
        assertEquals("Error log", warningsAndAbove[1].message)

        val errorsOnly = buffer.getRecent(limit = 10, minPriority = 6)
        assertEquals(1, errorsOnly.size)
        assertEquals("Error log", errorsOnly[0].message)
    }

    @Test
    fun `clear empties the buffer`() {
        val buffer = CircularLogBuffer(maxEntries = 5)
        buffer.onLog(LogEntry(priority = 4, tag = "TAG", message = "Test"))
        assertEquals(1, buffer.size)

        buffer.clear()
        assertEquals(0, buffer.size)
        assertTrue(buffer.snapshot().isEmpty())
    }
}
