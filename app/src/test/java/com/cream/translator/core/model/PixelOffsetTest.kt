package com.cream.translator.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelOffsetTest {
    @Test
    fun `NONE has zero coordinates and isZero is true`() {
        assertEquals(0, PixelOffset.NONE.x)
        assertEquals(0, PixelOffset.NONE.y)
        assertTrue(PixelOffset.NONE.isZero)
    }

    @Test
    fun `non-zero coordinates make isZero false`() {
        assertFalse(PixelOffset(10, 0).isZero)
        assertFalse(PixelOffset(0, 20).isZero)
        assertFalse(PixelOffset(100, 200).isZero)
    }
}
