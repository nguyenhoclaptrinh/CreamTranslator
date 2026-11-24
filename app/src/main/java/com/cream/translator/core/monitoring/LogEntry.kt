package com.cream.translator.core.monitoring

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Immutable representation of a single log event captured by the monitoring infrastructure.
 */
data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val priority: Int,
    val tag: String,
    val threadName: String = Thread.currentThread().name,
    val message: String,
    val throwable: Throwable? = null,
) {
    fun priorityLetter(): String =
        when (priority) {
            2 -> "V" // Log.VERBOSE
            3 -> "D" // Log.DEBUG
            4 -> "I" // Log.INFO
            5 -> "W" // Log.WARN
            6 -> "E" // Log.ERROR
            7 -> "A" // Log.ASSERT
            else -> "?"
        }

    fun formattedTimestamp(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        return formatter.format(Date(timestamp))
    }

    fun toFormattedString(): String {
        val stackTrace = throwable?.let { "\n" + it.stackTraceToString() } ?: ""
        return "[${formattedTimestamp()}][${priorityLetter()}][$tag][$threadName] $message$stackTrace"
    }
}
