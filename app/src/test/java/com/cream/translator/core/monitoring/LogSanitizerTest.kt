package com.cream.translator.core.monitoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogSanitizerTest {
    @Test
    fun `sanitize preserves normal log messages`() {
        val normalMsg = "Service started successfully with code 0"
        val sanitized = LogSanitizer.sanitize(normalMsg)
        assertEquals(normalMsg, sanitized)
    }

    @Test
    fun `maskText produces correct length indicator`() {
        val masked = LogSanitizer.maskText("SecretPhrase")
        assertEquals("[REDACTED_LEN_12]", masked)
    }

    @Test
    fun `sanitize redacts OCR payloads correctly`() {
        val messageWithOcr = "OCR recognized text: rawText='Hello World' and recognizedText=\"Confidential Screen\""
        val sanitized = LogSanitizer.sanitize(messageWithOcr)

        assertFalse(sanitized.contains("Hello World"))
        assertFalse(sanitized.contains("Confidential Screen"))
        assertTrue(sanitized.contains("rawText='[REDACTED_LEN_11]'"))
        assertTrue(sanitized.contains("recognizedText=\"[REDACTED_LEN_19]\""))
    }

    @Test
    fun `sanitize redacts sensitive tokens and passwords`() {
        val message = "Failed request with token=abc12345xyz and password: 'SuperSecret123'"
        val sanitized = LogSanitizer.sanitize(message)

        assertFalse(sanitized.contains("abc12345xyz"))
        assertFalse(sanitized.contains("SuperSecret123"))
        assertTrue(sanitized.contains("token=[REDACTED_KEY]"))
        assertTrue(sanitized.contains("password=[REDACTED_KEY]"))
    }

    @Test
    fun `sanitize redacts email addresses`() {
        val message = "User email is user.name+tag@example.com logged in"
        val sanitized = LogSanitizer.sanitize(message)

        assertFalse(sanitized.contains("user.name+tag@example.com"))
        assertTrue(sanitized.contains("[REDACTED_EMAIL]"))
    }

    @Test
    fun `sanitize redacts credit card numbers`() {
        val message = "Card number 4111 2222 3333 4444 submitted"
        val sanitized = LogSanitizer.sanitize(message)

        assertFalse(sanitized.contains("4111 2222 3333 4444"))
        assertTrue(sanitized.contains("[REDACTED_NUMERIC]"))
    }
}
