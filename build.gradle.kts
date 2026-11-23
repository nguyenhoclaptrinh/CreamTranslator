plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.spotless) apply false
}

subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "com.diffplug.spotless")

    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        kotlin {
            target("**/*.kt")
            targetExclude("**/build/**")
            ktlint("1.2.1")
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint("1.2.1")
        }
    }
}
