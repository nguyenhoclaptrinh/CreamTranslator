package com.cream.translator.core.monitoring

import java.util.regex.Pattern

/**
 * Sanitizes log messages to enforce the Zero Remote Exfiltration and Privacy Invariant.
 * Redacts Personally Identifiable Information (PII), credential tokens, and raw OCR text content.
 */
object LogSanitizer {
    private val EMAIL_PATTERN =
        Pattern.compile(
            "[a-zA-Z0-9+._%\\-]{1,256}@[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}(\\.[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25})+",
            Pattern.CASE_INSENSITIVE,
        )

    private val SENSITIVE_KEY_PATTERN =
        Pattern.compile(
            "(?i)\\b(password|secret|token|api[_-]?key|authorization|bearer)\\b\\s*[:=]\\s*['\"]?([^\\s,'\"]+)",
        )

    private val CREDIT_CARD_PATTERN =
        Pattern.compile(
            "\\b(?:\\d[ -]*?){13,16}\\b",
        )

    private val OCR_PAYLOAD_PATTERN =
        Pattern.compile(
            "(?i)(rawText|ocrText|translatedText|recognizedText)\\s*[:=]\\s*(['\"])(.*?)\\2",
        )

    /**
     * Replaces raw user text with a privacy-safe length descriptor.
     */
    fun maskText(text: String): String {
        return "[REDACTED_LEN_${text.length}]"
    }

    /**
     * Sanitizes any log message by removing sensitive user credentials, PII, and raw OCR text.
     */
    fun sanitize(message: String): String {
        if (message.isEmpty()) return message

        var sanitized = message

        // 1. Redact OCR payload contents
        sanitized =
            OCR_PAYLOAD_PATTERN.matcher(sanitized).replaceAll { matchResult ->
                val key = matchResult.group(1)
                val quote = matchResult.group(2)
                val content = matchResult.group(3) ?: ""
                "$key=$quote${maskText(content)}$quote"
            }

        // 2. Redact sensitive credentials/keys
        sanitized =
            SENSITIVE_KEY_PATTERN.matcher(sanitized).replaceAll { matchResult ->
                val key = matchResult.group(1)
                "$key=[REDACTED_KEY]"
            }

        // 3. Redact emails
        sanitized = EMAIL_PATTERN.matcher(sanitized).replaceAll("[REDACTED_EMAIL]")

        // 4. Redact credit card / long numeric patterns (13-16 consecutive digits)
        sanitized = CREDIT_CARD_PATTERN.matcher(sanitized).replaceAll("[REDACTED_NUMERIC]")

        return sanitized
    }
}
