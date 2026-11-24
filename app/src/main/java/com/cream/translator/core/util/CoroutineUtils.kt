package com.cream.translator.core.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import timber.log.Timber

/**
 * Executes [block] and catches any non-cancellation exception, wrapping the result in [Result].
 * If [block] throws a [CancellationException], it is immediately rethrown to preserve
 * structured concurrency in Kotlin coroutines.
 */
@Suppress("TooGenericExceptionCaught")
inline fun <T> runSuspendCatching(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (e: Throwable) {
        Result.failure(e)
    }

/**
 * Creates a named [CoroutineExceptionHandler] that logs the uncaught exception via Timber
 * and forwards it to an optional [onError] lambda.
 */
fun createNamedExceptionHandler(
    tag: String,
    onError: ((Throwable) -> Unit)? = null,
): CoroutineExceptionHandler =
    CoroutineExceptionHandler { _, throwable ->
        if (throwable !is CancellationException) {
            Timber.tag(tag).e(throwable, "Uncaught coroutine exception in %s", tag)
            onError?.invoke(throwable)
        }
    }
