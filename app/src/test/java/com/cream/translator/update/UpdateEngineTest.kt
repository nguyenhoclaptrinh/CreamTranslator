package com.cream.translator.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UpdateEngineTest {
    private lateinit var engine: UpdateEngine

    @Before
    fun setUp() {
        engine = UpdateEngine()
    }

    @Test
    fun `isNewerVersion returns true when remote patch version is higher`() {
        assertTrue(engine.isNewerVersion("1.0.1", "1.0.0"))
    }

    @Test
    fun `isNewerVersion returns true when remote minor version is higher`() {
        assertTrue(engine.isNewerVersion("1.1.0", "1.0.9"))
    }

    @Test
    fun `isNewerVersion returns true when remote major version is higher`() {
        assertTrue(engine.isNewerVersion("2.0.0", "1.9.9"))
    }

    @Test
    fun `isNewerVersion returns false when versions are identical`() {
        assertFalse(engine.isNewerVersion("1.0.0", "1.0.0"))
    }

    @Test
    fun `isNewerVersion returns false when remote version is older`() {
        assertFalse(engine.isNewerVersion("1.0.0", "1.0.1"))
        assertFalse(engine.isNewerVersion("1.2.0", "1.3.0"))
    }

    @Test
    fun `isNewerVersion handles double digit semantic versioning correctly`() {
        assertTrue(engine.isNewerVersion("1.10.0", "1.9.0"))
        assertFalse(engine.isNewerVersion("1.9.0", "1.10.0"))
    }

    @Test
    fun `isNewerVersion handles differing part lengths gracefully`() {
        assertTrue(engine.isNewerVersion("1.0.0.1", "1.0.0"))
        assertFalse(engine.isNewerVersion("1.0.0", "1.0.0.1"))
    }

    @Test
    fun `isNewerVersion handles pre-release tags and build metadata correctly`() {
        assertTrue(engine.isNewerVersion("1.0.1-beta", "1.0.0"))
        assertTrue(engine.isNewerVersion("1.1.0-rc.1", "1.0.9"))
        assertTrue(engine.isNewerVersion("1.0.1+build20", "1.0.0"))
        assertFalse(engine.isNewerVersion("1.0.0-beta", "1.0.0"))
        assertFalse(engine.isNewerVersion("1.0.0-rc1", "1.0.1"))
    }
}
