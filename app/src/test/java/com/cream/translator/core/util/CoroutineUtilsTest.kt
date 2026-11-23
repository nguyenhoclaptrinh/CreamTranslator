package com.cream.translator.core.util

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.coroutines.EmptyCoroutineContext

class CoroutineUtilsTest {
    @Test
    fun runSuspendCatching_onSuccess_returnsSuccess() {
        val result =
            runSuspendCatching {
                42
            }
        assertTrue(result.isSuccess)
        assertEquals(42, result.getOrNull())
    }

    @Test
    fun runSuspendCatching_onStandardException_returnsFailure() {
        val result =
            runSuspendCatching {
                throw IllegalStateException("Something went wrong")
            }
        assertTrue(result.isFailure)
        assertEquals("Something went wrong", result.exceptionOrNull()?.message)
    }

    @Test
    fun runSuspendCatching_onCancellationException_rethrowsImmediately() {
        try {
            runSuspendCatching {
                throw CancellationException("Coroutine was cancelled")
            }
            fail("Expected CancellationException to be rethrown")
        } catch (e: CancellationException) {
            assertEquals("Coroutine was cancelled", e.message)
        }
    }

    @Test
    fun createNamedExceptionHandler_invokesOnErrorCallback() {
        var callbackInvoked = false
        var capturedError: Throwable? = null
        val handler =
            createNamedExceptionHandler("TestTag") { error ->
                callbackInvoked = true
                capturedError = error
            }

        val testException = RuntimeException("Boom")
        handler.handleException(EmptyCoroutineContext, testException)

        assertTrue(callbackInvoked)
        assertEquals(testException, capturedError)
    }

    @Test
    fun createNamedExceptionHandler_ignoresCancellationException() {
        var callbackInvoked = false
        val handler =
            createNamedExceptionHandler("TestTag") {
                callbackInvoked = true
            }

        handler.handleException(EmptyCoroutineContext, CancellationException("Cancelled"))

        assertFalse(callbackInvoked)
    }
}
