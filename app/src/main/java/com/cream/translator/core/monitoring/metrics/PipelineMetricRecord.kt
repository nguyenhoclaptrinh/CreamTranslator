package com.cream.translator.core.monitoring.metrics

/**
 * Metric snapshot for a single translation pipeline execution cycle.
 */
data class PipelineMetricRecord(
    val traceId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val captureDurationMs: Long = 0L,
    val ocrDurationMs: Long = 0L,
    val filterDurationMs: Long = 0L,
    val translateDurationMs: Long = 0L,
    val inpaintDurationMs: Long = 0L,
    val renderDurationMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val recognizedBlocks: Int = 0,
    val translatedBlocks: Int = 0,
    val isSuccess: Boolean = true,
    val errorMessage: String? = null,
) {
    fun toSummaryString(): String {
        return "traceId=$traceId, total=${totalDurationMs}ms " +
            "(capture=${captureDurationMs}ms, ocr=${ocrDurationMs}ms, filter=${filterDurationMs}ms, " +
            "trans=${translateDurationMs}ms, inpaint=${inpaintDurationMs}ms, render=${renderDurationMs}ms), " +
            "blocks=$recognizedBlocks/$translatedBlocks, success=$isSuccess"
    }
}
