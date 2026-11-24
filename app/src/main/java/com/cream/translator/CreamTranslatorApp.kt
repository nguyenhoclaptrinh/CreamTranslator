package com.cream.translator

import android.app.Application
import com.cream.translator.core.monitoring.CreamLoggingTree
import com.cream.translator.core.monitoring.crash.CrashDiagnosticCollector
import com.cream.translator.core.monitoring.memory.MemoryPressureMonitor
import com.cream.translator.core.monitoring.metrics.PipelineTracer
import com.cream.translator.core.monitoring.storage.CircularLogBuffer
import com.cream.translator.core.monitoring.storage.RotatingFileLogSink
import timber.log.Timber
import java.io.File

class CreamTranslatorApp : Application() {
    lateinit var circularLogBuffer: CircularLogBuffer
        private set

    lateinit var fileLogSink: RotatingFileLogSink
        private set

    lateinit var pipelineTracer: PipelineTracer
        private set

    lateinit var memoryPressureMonitor: MemoryPressureMonitor
        private set

    lateinit var crashDiagnosticCollector: CrashDiagnosticCollector
        private set

    val diagnosticDirectory: File
        get() = File(noBackupFilesDir, "diagnostics")

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 1. Initialize diagnostic storage and in-memory log buffer
        circularLogBuffer = CircularLogBuffer()
        fileLogSink = RotatingFileLogSink(diagnosticDirectory)
        pipelineTracer = PipelineTracer()

        // 2. Plant customized, privacy-preserving Timber tree
        val sinks = listOf(circularLogBuffer, fileLogSink)
        Timber.plant(CreamLoggingTree(isDebug = BuildConfig.DEBUG, sinks = sinks))

        // 3. Register memory pressure monitor
        memoryPressureMonitor = MemoryPressureMonitor(this)
        registerComponentCallbacks(memoryPressureMonitor)

        // 4. Install local crash reporter
        crashDiagnosticCollector = CrashDiagnosticCollector.install(this, diagnosticDirectory)

        Timber.i("CreamTranslatorApp initialized: version=%s, debug=%s", BuildConfig.VERSION_NAME, BuildConfig.DEBUG)
    }

    companion object {
        lateinit var instance: CreamTranslatorApp
            private set
    }
}
