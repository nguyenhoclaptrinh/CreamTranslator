package com.cream.translator.core.monitoring.storage

import com.cream.translator.core.monitoring.LogEntry
import com.cream.translator.core.monitoring.LogSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter

/**
 * File-backed rotating log sink that asynchronously writes log entries to disk.
 * Uses two rotating files (active and previous) capped at [maxFileSizeBytes] to guarantee
 * deterministic storage bounds without unbounded disk accumulation.
 */
class RotatingFileLogSink(
    private val logDirectory: File,
    private val maxFileSizeBytes: Long = DEFAULT_MAX_FILE_SIZE,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) : LogSink, AutoCloseable {
    private val logChannel = Channel<LogEntry>(capacity = 500)
    private val activeFile: File get() = File(logDirectory, ACTIVE_LOG_FILENAME)
    private val previousFile: File get() = File(logDirectory, PREVIOUS_LOG_FILENAME)
    private var workerJob: Job? = null

    init {
        if (!logDirectory.exists()) {
            logDirectory.mkdirs()
        }
        startLogWorker()
    }

    private fun startLogWorker() {
        workerJob =
            scope.launch {
                for (entry in logChannel) {
                    writeEntry(entry)
                }
            }
    }

    override fun onLog(entry: LogEntry) {
        logChannel.trySend(entry)
    }

    override fun close() {
        logChannel.close()
        workerJob?.cancel()
    }

    private fun writeEntry(entry: LogEntry) {
        try {
            rotateIfExceeded()
            PrintWriter(FileWriter(activeFile, true)).use { writer ->
                writer.println(entry.toFormattedString())
            }
        } catch (_: Exception) {
            // Fail silently to prevent logging failures from crashing the host application
        }
    }

    private fun rotateIfExceeded() {
        if (activeFile.exists() && activeFile.length() >= maxFileSizeBytes) {
            if (previousFile.exists()) {
                previousFile.delete()
            }
            activeFile.renameTo(previousFile)
        }
    }

    /**
     * Returns all existing log files in chronological order (previous first, then active).
     */
    fun getLogFiles(): List<File> {
        val files = mutableListOf<File>()
        if (previousFile.exists() && previousFile.length() > 0) {
            files.add(previousFile)
        }
        if (activeFile.exists() && activeFile.length() > 0) {
            files.add(activeFile)
        }
        return files
    }

    /**
     * Clears all log files from disk.
     */
    suspend fun clearLogs() =
        withContext(Dispatchers.IO) {
            if (activeFile.exists()) activeFile.delete()
            if (previousFile.exists()) previousFile.delete()
        }

    companion object {
        const val ACTIVE_LOG_FILENAME = "active_session.log"
        const val PREVIOUS_LOG_FILENAME = "previous_session.log"
        const val DEFAULT_MAX_FILE_SIZE = 1024 * 1024L // 1 MB
    }
}
