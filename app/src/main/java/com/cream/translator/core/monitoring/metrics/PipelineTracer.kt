package com.cream.translator.core.monitoring.metrics

import timber.log.Timber
import java.util.ArrayDeque
import java.util.UUID

/**
 * High-precision tracer for the end-to-end screen capture and translation pipeline.
 * Measures latency across discrete pipeline stages without introducing performance overhead.
 */
class PipelineTracer(
    private val maxHistory: Int = DEFAULT_MAX_HISTORY,
) {
    private val lock = Any()
    private val history = ArrayDeque<PipelineMetricRecord>(maxHistory)

    class Session(
        val traceId: String = UUID.randomUUID().toString().take(8),
        private val onFinish: (PipelineMetricRecord) -> Unit,
    ) {
        val startTimeNs: Long = System.nanoTime()
        var captureDurationMs: Long = 0L
        var ocrDurationMs: Long = 0L
        var filterDurationMs: Long = 0L
        var translateDurationMs: Long = 0L
        var inpaintDurationMs: Long = 0L
        var renderDurationMs: Long = 0L
        var recognizedBlocks: Int = 0
        var translatedBlocks: Int = 0

        inline fun <T> measureCapture(block: () -> T): T {
            val start = System.nanoTime()
            try {
                return block()
            } finally {
                captureDurationMs = (System.nanoTime() - start) / 1_000_000L
            }
        }

        inline fun <T> measureOcr(block: () -> T): T {
            val start = System.nanoTime()
            try {
                return block()
            } finally {
                ocrDurationMs = (System.nanoTime() - start) / 1_000_000L
            }
        }

        inline fun <T> measureFilter(block: () -> T): T {
            val start = System.nanoTime()
            try {
                return block()
            } finally {
                filterDurationMs = (System.nanoTime() - start) / 1_000_000L
            }
        }

        inline fun <T> measureTranslate(block: () -> T): T {
            val start = System.nanoTime()
            try {
                return block()
            } finally {
                translateDurationMs = (System.nanoTime() - start) / 1_000_000L
            }
        }

        inline fun <T> measureInpaint(block: () -> T): T {
            val start = System.nanoTime()
            try {
                return block()
            } finally {
                inpaintDurationMs = (System.nanoTime() - start) / 1_000_000L
            }
        }

        inline fun <T> measureRender(block: () -> T): T {
            val start = System.nanoTime()
            try {
                return block()
            } finally {
                renderDurationMs = (System.nanoTime() - start) / 1_000_000L
            }
        }

        fun finish(
            isSuccess: Boolean = true,
            errorMessage: String? = null,
        ): PipelineMetricRecord {
            val totalDurationMs = (System.nanoTime() - startTimeNs) / 1_000_000L
            val record =
                PipelineMetricRecord(
                    traceId = traceId,
                    captureDurationMs = captureDurationMs,
                    ocrDurationMs = ocrDurationMs,
                    filterDurationMs = filterDurationMs,
                    translateDurationMs = translateDurationMs,
                    inpaintDurationMs = inpaintDurationMs,
                    renderDurationMs = renderDurationMs,
                    totalDurationMs = totalDurationMs,
                    recognizedBlocks = recognizedBlocks,
                    translatedBlocks = translatedBlocks,
                    isSuccess = isSuccess,
                    errorMessage = errorMessage,
                )
            onFinish(record)
            return record
        }
    }

    fun startSession(): Session {
        return Session { record ->
            synchronized(lock) {
                if (history.size >= maxHistory) {
                    history.removeFirst()
                }
                history.addLast(record)
            }
            Timber.i("PipelineTrace: %s", record.toSummaryString())
        }
    }

    fun getRecentMetrics(): List<PipelineMetricRecord> {
        synchronized(lock) {
            return history.toList()
        }
    }

    fun calculateAverageLatencyMs(): Long {
        synchronized(lock) {
            if (history.isEmpty()) return 0L
            val successful = history.filter { it.isSuccess }
            if (successful.isEmpty()) return 0L
            return successful.map { it.totalDurationMs }.average().toLong()
        }
    }

    companion object {
        const val DEFAULT_MAX_HISTORY = 50
    }
}
