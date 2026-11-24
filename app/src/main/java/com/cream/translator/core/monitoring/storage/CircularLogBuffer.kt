package com.cream.translator.core.monitoring.storage

import com.cream.translator.core.monitoring.LogEntry
import com.cream.translator.core.monitoring.LogSink
import java.util.ArrayDeque

/**
 * In-memory circular buffer storing the most recent log entries in thread-safe fashion.
 * Prevents memory growth by evicting the oldest entries once [maxEntries] is reached.
 */
class CircularLogBuffer(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) : LogSink {
    private val lock = Any()
    private val buffer = ArrayDeque<LogEntry>(maxEntries)

    override fun onLog(entry: LogEntry) {
        synchronized(lock) {
            if (buffer.size >= maxEntries) {
                buffer.removeFirst()
            }
            buffer.addLast(entry)
        }
    }

    /**
     * Returns a snapshot of all currently stored log entries in chronological order.
     */
    fun snapshot(): List<LogEntry> {
        synchronized(lock) {
            return buffer.toList()
        }
    }

    /**
     * Returns the most recent entries, optionally filtered by minimum priority.
     */
    fun getRecent(
        limit: Int = 100,
        minPriority: Int = 0,
    ): List<LogEntry> {
        synchronized(lock) {
            return buffer
                .filter { it.priority >= minPriority }
                .takeLast(limit)
        }
    }

    /**
     * Clears all log entries currently held in memory.
     */
    fun clear() {
        synchronized(lock) {
            buffer.clear()
        }
    }

    val size: Int
        get() = synchronized(lock) { buffer.size }

    companion object {
        const val DEFAULT_MAX_ENTRIES = 1000
    }
}
