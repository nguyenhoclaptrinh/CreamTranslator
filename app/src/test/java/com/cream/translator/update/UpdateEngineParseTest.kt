package com.cream.translator.update

import com.cream.translator.testutil.BaseRobolectricTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UpdateEngineParseTest : BaseRobolectricTest() {
    private lateinit var engine: UpdateEngine

    @Before
    override fun setUp() {
        super.setUp()
        engine = UpdateEngine()
    }

    @Test
    fun `parseReleaseResult returns failure on malformed JSON instead of throwing`() {
        val result = engine.parseReleaseResult("{ invalid json", "1.3.0")
        assertTrue(result.isFailure)
    }

    @Test
    fun `parseReleaseResult parses newer APK release correctly`() {
        val json =
            """
            {
              "tag_name": "v1.4.0",
              "body": "Lifecycle fixes",
              "assets": [
                {
                  "name": "CreamTranslator-v1.4.0.apk",
                  "browser_download_url": "https://github.com/example/app.apk",
                  "size": 1234567
                }
              ]
            }
            """.trimIndent()

        val result = engine.parseReleaseResult(json, "1.3.0")
        assertTrue(result.isSuccess)
        val release = result.getOrNull()
        assertNotNull(release)
        assertEquals("1.4.0", release?.versionName)
        assertEquals("v1.4.0", release?.tagName)
    }
}
