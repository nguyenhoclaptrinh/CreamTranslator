package com.cream.translator.update

import android.app.Application
import org.junit.Assert.assertNotNull
import org.junit.Test

class UpdateViewModelTest {
    @Test
    fun verifySingleApplicationConstructorExistsForViewModelFactory() {
        val constructor = UpdateViewModel::class.java.getConstructor(Application::class.java)
        assertNotNull(constructor)
    }
}
