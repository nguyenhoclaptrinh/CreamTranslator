plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val majorProp = providers.gradleProperty("appVersionMajor").getOrElse("1").toInt()
val minorProp = providers.gradleProperty("appVersionMinor").getOrElse("1").toInt()
val patchProp = providers.gradleProperty("appVersionPatch").getOrElse("0").toInt()

val tagRef = providers.environmentVariable("GITHUB_REF_NAME").orNull
    ?: providers.gradleProperty("appTag").orNull

val (major, minor, patch) = if (tagRef != null && tagRef.startsWith("v")) {
    val cleanTag = tagRef.removePrefix("v")
    val parts = cleanTag.split(".").mapNotNull { it.toIntOrNull() }
    Triple(
        parts.getOrElse(0) { majorProp },
        parts.getOrElse(1) { minorProp },
        parts.getOrElse(2) { patchProp },
    )
} else {
    Triple(majorProp, minorProp, patchProp)
}

val appVersionCode = (major * 10000) + (minor * 1000) + patch
val appVersionName = "$major.$minor.$patch"

android {
    namespace = "com.cream.translator"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.cream.translator"
        minSdk = 26
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        checkDependencies = false
        checkReleaseBuilds = false
        checkTestSources = false
        checkGeneratedSources = false
        abortOnError = true
        htmlReport = true
        xmlReport = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // ML Kit On-Device (Zero-Cost, Zero Cloud API dependency)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.translate)

    implementation(libs.timber)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.register("printAppVersion") {
    doLast {
        println("APP_VERSION_NAME=$appVersionName")
        println("APP_VERSION_CODE=$appVersionCode")
    }
}
