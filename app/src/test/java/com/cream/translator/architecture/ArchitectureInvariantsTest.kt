package com.cream.translator.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Test

class ArchitectureInvariantsTest {
    @Test
    fun `classes with Service suffix should reside in service package`() {
        Konsist
            .scopeFromProduction()
            .classes()
            .withNameEndingWith("Service")
            .assertTrue { it.resideInPackage("..service..") }
    }

    @Test
    fun `classes with Repository suffix should reside in data or repository package`() {
        Konsist
            .scopeFromProduction()
            .classes()
            .withNameEndingWith("Repository")
            .assertTrue { it.resideInPackage("..data..") || it.resideInPackage("..repository..") }
    }

    @Test
    fun `classes with ViewModel suffix should reside in ui or update package`() {
        Konsist
            .scopeFromProduction()
            .classes()
            .withNameEndingWith("ViewModel")
            .assertTrue { it.resideInPackage("..ui..") || it.resideInPackage("..update..") }
    }

    @Test
    fun `no production file should reference GlobalScope`() {
        Konsist
            .scopeFromProduction()
            .files
            .assertTrue { file ->
                !file.text.contains("GlobalScope.")
            }
    }

    @Test
    fun `services must reside in service package`() {
        Konsist
            .scopeFromProduction()
            .classes()
            .withNameEndingWith("Service")
            .assertTrue { it.resideInPackage("..service..") }
    }
}
