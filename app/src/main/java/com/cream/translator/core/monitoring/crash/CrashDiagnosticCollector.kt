package com.cream.translator.core.monitoring.crash

import android.content.Context
import com.cream.translator.BuildConfig
import com.cream.translator.core.monitoring.LogSanitizer
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Catches unhandled exceptions, packages diagnostic device info and stacktrace into a local JSON report,
 * and passes the event to the system default uncaught exception handler.
 */
class CrashDiagnosticCollector(
    private val context: Context,
    private val diagnosticDir: File,
    private val previousHandler: Thread.UncaughtExceptionHandler? = Thread.getDefaultUncaughtExceptionHandler(),
) : Thread.UncaughtExceptionHandler {
    private val reportFile = File(diagnosticDir, CRASH_REPORT_FILENAME)

    init {
        if (!diagnosticDir.exists()) {
            diagnosticDir.mkdirs()
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun uncaughtException(
        thread: Thread,
        throwable: Throwable,
    ) {
        try {
            saveCrashReport(thread, throwable)
        } catch (e: Exception) {
            Timber.e(e, "Failed to persist crash diagnostic report")
        } finally {
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun saveCrashReport(
        thread: Thread,
        throwable: Throwable,
    ) {
        val now = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        val formattedDate = dateFormat.format(Date(now))

        val sanitizedStackTrace = LogSanitizer.sanitize(throwable.stackTraceToString())
        val sanitizedMessage = LogSanitizer.sanitize(throwable.message ?: "No message")

        val runtime = Runtime.getRuntime()
        val freeMemoryBytes = runtime.freeMemory()
        val totalMemoryBytes = runtime.totalMemory()

        val jsonContent =
            buildString {
                append("{\n")
                append("""  "timestamp": $now,""").append("\n")
                append("""  "dateTime": "$formattedDate",""").append("\n")
                append("""  "thread": "${escapeJson(thread.name)}",""").append("\n")
                append("""  "exceptionClass": "${escapeJson(throwable.javaClass.name)}",""").append("\n")
                append("""  "message": "${escapeJson(sanitizedMessage)}",""").append("\n")
                append("""  "stackTrace": "${escapeJson(sanitizedStackTrace)}",""").append("\n")
                append("""  "appVersionName": "${BuildConfig.VERSION_NAME}",""").append("\n")
                append("""  "appVersionCode": ${BuildConfig.VERSION_CODE},""").append("\n")
                append("""  "buildType": "${BuildConfig.BUILD_TYPE}",""").append("\n")
                append("""  "freeMemoryMb": ${freeMemoryBytes / (1024 * 1024)},""").append("\n")
                append("""  "totalMemoryMb": ${totalMemoryBytes / (1024 * 1024)}""").append("\n")
                append("}")
            }

        FileWriter(reportFile, false).use { writer ->
            writer.write(jsonContent)
        }
    }

    private fun escapeJson(str: String): String {
        return str
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\b", "\\b")
            .replace("\u000C", "\\f")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    fun hasCrashReport(): Boolean {
        return reportFile.exists() && reportFile.length() > 0
    }

    fun getCrashReportFile(): File? {
        return if (hasCrashReport()) reportFile else null
    }

    fun readCrashReport(): String? {
        return if (hasCrashReport()) reportFile.readText() else null
    }

    fun clearCrashReport(): Boolean {
        return if (reportFile.exists()) reportFile.delete() else true
    }

    companion object {
        const val CRASH_REPORT_FILENAME = "crash_report.json"

        fun install(
            context: Context,
            diagnosticDir: File,
        ): CrashDiagnosticCollector {
            val collector = CrashDiagnosticCollector(context, diagnosticDir)
            Thread.setDefaultUncaughtExceptionHandler(collector)
            return collector
        }
    }
}
