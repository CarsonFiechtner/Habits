plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

// Release versioning comes from CI: VERSION_NAME is the git tag without the leading "v" (e.g. 0.1.0).
// versionCode is derived from it (major * 10000 + minor * 100 + patch) so it always increases.
val appVersionName: String = providers.environmentVariable("VERSION_NAME").getOrElse("0.0.0")
val appVersionCode: Int = appVersionName.split("-")[0].split(".").let { parts ->
    val (major, minor, patch) = (parts + listOf("0", "0", "0")).take(3).map { it.toIntOrNull() ?: 0 }
    (major * 10000 + minor * 100 + patch).coerceAtLeast(1)
}

// Signing secrets are only present on CI release builds (see .github/workflows/release.yml).
val releaseKeystorePath: String? = providers.environmentVariable("KEEPUP_KEYSTORE_PATH").orNull
val hasReleaseSigning = releaseKeystorePath != null && file(releaseKeystorePath).exists()

android {
    namespace = "io.github.carsonfiechtner.keepup"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "io.github.carsonfiechtner.keepup"
        minSdk = 34
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = providers.environmentVariable("KEEPUP_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("KEEPUP_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("KEEPUP_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        debug {
            // Lets a debug build live next to the release install on the same phone.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        // Lint's Kotlin analysis crashes on the Hilt test module; app sources are still fully checked.
        ignoreTestSources = true
    }

    sourceSets {
        // Exported Room schemas, for migration tests on Robolectric (which only sees app assets).
        // Debug builds only; release APKs don't include them.
        getByName("debug").assets.directories.add("$projectDir/schemas")
    }

    testOptions {
        unitTests {
            // Robolectric UI tests need merged resources and the manifest.
            isIncludeAndroidResources = true
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

room {
    // Exported schemas are committed so every migration can be tested (design doc §9.2).
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":core:domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.health.connect)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)
}
