package com.cream.translator.core.monitoring.crash

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.cream.translator.BuildConfig
import com.cream.translator.core.monitoring.storage.CircularLogBuffer
import com.cream.translator.core.monitoring.storage.RotatingFileLogSink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Packages system info, crash reports, and diagnostic logs into an exportable file,
 * and shares it via the standard Android Sharesheet.
 */
class DiagnosticExportHelper(
    private val context: Context,
    private val diagnosticDir: File,
    private val circularBuffer: CircularLogBuffer? = null,
) {
    private val exportDir = File(context.cacheDir, "diagnostics")

    init {
        if (!exportDir.exists()) {
            exportDir.mkdirs()
        }
    }

    /**
     * Aggregates all diagnostic artifacts into a single clean text report.
     */
    suspend fun createDiagnosticReport(): File =
        withContext(Dispatchers.IO) {
            val exportFile = File(exportDir, "cream_diagnostic_report.txt")
            if (exportFile.exists()) {
                exportFile.delete()
            }

            PrintWriter(FileWriter(exportFile, false)).use { writer ->
                writeHeader(writer)
                writeCrashReport(writer)
                writeLogs(writer)
            }

            exportFile
        }

    private fun writeHeader(writer: PrintWriter) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        writer.println("=================================================================")
        writer.println("CREAM TRANSLATOR - DIAGNOSTIC REPORT")
        writer.println("Generated: ${dateFormat.format(Date())}")
        writer.println("=================================================================")
        writer.println("App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        writer.println("Build Type: ${BuildConfig.BUILD_TYPE}")
        writer.println("Android SDK: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")
        writer.println("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.PRODUCT})")

        val runtime = Runtime.getRuntime()
        val freeMb = runtime.freeMemory() / (1024 * 1024)
        val totalMb = runtime.totalMemory() / (1024 * 1024)
        val maxMb = runtime.maxMemory() / (1024 * 1024)
        writer.println("JVM Heap: Free=${freeMb}MB, Total=${totalMb}MB, Max=${maxMb}MB")
        writer.println("=================================================================\n")
    }

    private fun writeCrashReport(writer: PrintWriter) {
        val crashFile = File(diagnosticDir, CrashDiagnosticCollector.CRASH_REPORT_FILENAME)
        if (crashFile.exists() && crashFile.length() > 0) {
            writer.println(">>> RECENT CRASH REPORT DETECTED <<<")
            writer.println(crashFile.readText())
            writer.println(">>> END OF CRASH REPORT <<<\n")
        } else {
            writer.println("Crash Status: No recent unhandled exceptions detected.\n")
        }
    }

    private fun writeLogs(writer: PrintWriter) {
        writer.println("========================= SYSTEM LOGS ===========================")

        // 1. First append disk-backed rotated previous log if available
        val previousFile = File(diagnosticDir, RotatingFileLogSink.PREVIOUS_LOG_FILENAME)
        val hasPreviousLogs = previousFile.exists() && previousFile.length() > 0L
        if (hasPreviousLogs) {
            writer.println("--- Previous Session Disk Log ---")
            previousFile.bufferedReader().use { reader -> reader.copyTo(writer) }
            writer.println()
        }

        // 2. Append active session disk log if available
        val activeFile = File(diagnosticDir, RotatingFileLogSink.ACTIVE_LOG_FILENAME)
        val hasActiveLogs = activeFile.exists() && activeFile.length() > 0L
        if (hasActiveLogs) {
            writer.println("--- Active Session Disk Log ---")
            activeFile.bufferedReader().use { reader -> reader.copyTo(writer) }
            writer.println()
        }

        // 3. Fallback or complement with in-memory buffer if files were empty
        if (!hasPreviousLogs && !hasActiveLogs && circularBuffer != null) {
            writer.println("--- In-Memory Log Buffer Snapshot ---")
            circularBuffer.snapshot().forEach { entry ->
                writer.println(entry.toFormattedString())
            }
        }

        writer.println("====================== END OF SYSTEM LOGS =======================")
    }

    /**
     * Creates an Intent to share the diagnostic report via Android Sharesheet.
     */
    suspend fun createShareIntent(): Intent =
        withContext(Dispatchers.IO) {
            val reportFile = createDiagnosticReport()
            val authority = "${context.packageName}.fileprovider"
            val contentUri = FileProvider.getUriForFile(context, authority, reportFile)

            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, "CreamTranslator Diagnostic Report")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
}
