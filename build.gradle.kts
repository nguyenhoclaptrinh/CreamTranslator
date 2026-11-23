plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.spotless) apply false
}

subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "com.diffplug.spotless")

    configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        config.setFrom(files("$rootDir/detekt.yml"))
        buildUponDefaultConfig = true
        parallel = true
        source.setFrom("src/main/java")
    }

    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        if (rootProject.file(".git").isDirectory) {
            ratchetFrom("origin/main")
        }
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
