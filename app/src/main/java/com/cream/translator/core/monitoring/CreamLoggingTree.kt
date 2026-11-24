package com.cream.translator.core.monitoring

import timber.log.Timber

/**
 * Custom Timber tree that sanitizes messages, dispatches events to registered [LogSink] instances,
 * and maintains debug output to Android Logcat when in debug mode.
 */
class CreamLoggingTree(
    private val isDebug: Boolean = false,
    private val sinks: List<LogSink> = emptyList(),
) : Timber.DebugTree() {
    override fun log(
        priority: Int,
        tag: String?,
        message: String,
        t: Throwable?,
    ) {
        val cleanTag = tag ?: "CreamTranslator"
        val cleanMessage = LogSanitizer.sanitize(message)
        val entry =
            LogEntry(
                priority = priority,
                tag = cleanTag,
                message = cleanMessage,
                throwable = t,
            )

        for (sink in sinks) {
            sink.onLog(entry)
        }

        if (isDebug) {
            super.log(priority, cleanTag, cleanMessage, t)
        }
    }
}
