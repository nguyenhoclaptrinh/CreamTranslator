package com.cream.translator.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TranslatorControllerTest {
    @Before
    fun setup() {
        TranslatorController.updateServiceState(false)
    }

    @Test
    fun initialState_isFalse() {
        assertFalse(TranslatorController.isServiceRunning.value)
    }

    @Test
    fun updateServiceState_updatesStateFlow() {
        TranslatorController.updateServiceState(true)
        assertTrue(TranslatorController.isServiceRunning.value)

        TranslatorController.updateServiceState(false)
        assertFalse(TranslatorController.isServiceRunning.value)
    }
}
