import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val VERSION = "1.1.0"

// Set by .github/workflows/android.yml from repo secrets. Without them the release APK is
// unsigned, and scripts/arise-install.sh refuses it. See the README to sign with your own key.
val keystorePath: String? = System.getenv("ARISE_KEYSTORE_PATH")

android {
    namespace = "com.minimaldesigner.arise"
    // 36 for Health Connect 1.1.0; the app still targets 35.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.minimaldesigner.arise"
        minSdk = 29
        targetSdk = 35
        // Every CI run is newer than the last, so `adb install -r` always upgrades in place.
        // +100: the public repo's run numbers restarted at 1 after the private builds reached 18.
        versionCode = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0) + 100
        // A v* tag builds the plain version; everything else carries its commit.
        versionName = VERSION + if (System.getenv("GITHUB_REF_TYPE") == "tag") "" else "+" + (System.getenv("GITHUB_SHA")?.take(7) ?: "local")
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("arise") {
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("ARISE_STORE_PASS")
                keyAlias = System.getenv("ARISE_KEY_ALIAS")
                keyPassword = System.getenv("ARISE_KEY_PASS")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (keystorePath != null) signingConfigs.getByName("arise") else null
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    lint {
        abortOnError = true
        checkReleaseBuilds = false
    }
    testOptions {
        // Robolectric: Room migration test runs on the JVM, no emulator.
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

tasks.withType<Test>().configureEach {
    // CI is the only place tests run, so list every result in the log.
    testLogging { events("passed", "skipped", "failed") }
}

ksp {
    // Committed schemas back the Room migration tests.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.health.connect)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
