package com.cream.translator.core.monitoring

/**
 * Receiver interface for processed and sanitized log events.
 */
interface LogSink {
    fun onLog(entry: LogEntry)
}
