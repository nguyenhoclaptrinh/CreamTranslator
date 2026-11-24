package com.cream.translator.core.engine.filter

import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class TextFilterPropertyTest {
    private val filter = DefaultTextFilter()

    @Test
    fun `numeric integers must always be filtered out`() {
        runBlocking {
            checkAll(iterations = 500, Arb.int()) { number ->
                val result = filter.shouldFilter(number.toString())
                assertTrue("Expected number $number to be filtered out", result)
            }
        }
    }

    @Test
    fun `whitespace strings must always be filtered out`() {
        runBlocking {
            val whitespaceCharArb = Arb.of(' ', '\t', '\n', '\r')
            val whitespaceStringArb = Arb.list(whitespaceCharArb, 0..20).map { it.joinToString("") }
            checkAll(iterations = 500, whitespaceStringArb) { whitespace ->
                val result = filter.shouldFilter(whitespace)
                assertTrue("Expected whitespace to be filtered out", result)
            }
        }
    }

    @Test
    fun `arbitrary unicode strings must never cause filter to crash`() {
        runBlocking {
            checkAll(iterations = 500, Arb.string(0..100)) { arbitraryString ->
                // Invariant: filter must never throw unhandled runtime exceptions regardless of unicode inputs
                filter.shouldFilter(arbitraryString)
            }
        }
    }
}
